"""Diary background redraw bench (10-06 · #168) — can Otto redraw a child's background piece in colored pencil?

A background piece is what DiaryBoard.kt calls PieceRole.BACKGROUND: flat lines across the board
(width >= 0.55, height <= 0.25 of the board) — ground, sky, sea. The app never asks to redraw one yet
(PictureDiary.kt: 「배경 다시 그리기는 ComfyUI 확인 뒤에」). This measures it before any app or API change.

Same PC2 ComfyUI and model as the product (SDXL base + Lightning 8-step, comfy.redraw_workflow).
Fixed subjects, no scene LLM, no safety check (eyes only, like eval/redraw_1005).

  0  today's path as is   — piece cropped to its strokes (pieceToPng), prepare_drawing, DIARY_STYLE, cut_out_all
  1  whole board · pencil background 0.85
  2  whole board · pencil background 0.9
  3  whole board + band wash · 0.85   (ground washed down to the next line, sky washed up to the top)
  4  whole board + band wash · 0.9

Run from backend/ with the backend venv:  COMFY_URL=http://<pc2>:8188 python ../eval/diary_bg_1006/bench.py OUT
  5  whole board + band wash · 0.88  (added 10-06, between 3 and 4)

  --sheets   only redraw the comparison sheets from OUT
  --only 5   run only these rows (comma-separated) into an OUT that has the others
"""
import asyncio
import base64
import io
import json
import math
import random
import sys
import time
from pathlib import Path

sys.path.insert(0, ".")
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from app.image import character, comfy  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
SEEDS = [7, 11, 23]

ASPECT = 1.6                       # the diary board, width / height
BOARD_W, BOARD_H = 1232, 768       # SDXL-sized, same ratio — the size a whole-board piece is sent at
PEN_W = 0.014
GREEN, BLUE, SKY, YELLOW, BROWN = (90, 170, 80), (60, 120, 220), (110, 180, 235), (245, 200, 50), (140, 90, 55)


def wave(y, amp, n_waves, x0=0.04, x1=0.96, n=40):
    return [(x0 + (x1 - x0) * i / n, y + amp * math.sin(2 * math.pi * n_waves * i / n)) for i in range(n + 1)]


def wob(pts, rnd, k=0.006):
    return [(x + rnd.uniform(-k, k), y + rnd.uniform(-k, k)) for x, y in pts]


def strokes_of(kind: str) -> list[tuple[tuple, list]]:
    """x as a fraction of the board width, y of its height — like the app's strokes."""
    rnd = random.Random(kind)
    if kind == "ground":
        return [(GREEN, wob(wave(0.80, 0.015, 3), rnd))]
    if kind == "sky_ground":
        return [(SKY, wob(wave(0.14, 0.02, 2), rnd)), (GREEN, wob(wave(0.82, 0.015, 3), rnd))]
    if kind == "sea":
        return [(BLUE, wob(wave(0.55, 0.03, 4), rnd)), (BLUE, wob(wave(0.66, 0.03, 4, 0.08, 0.92), rnd)),
                (YELLOW, wob(wave(0.86, 0.01, 1), rnd))]
    if kind == "hills":
        return [(GREEN, wob([(0.04 + 0.92 * i / 40, 0.72 - 0.16 * max(0, math.sin(math.pi * 2 * i / 40)) ** 1.2)
                             for i in range(41)], rnd))]
    raise KeyError(kind)


SUBJECTS = {"ground": "green grassy ground", "sky_ground": "blue sky above green grassy ground",
            "sea": "blue sea with waves and a sandy beach", "hills": "rolling green hills"}


def _draw(st, im: Image.Image, left: float, top: float, scale: float) -> None:
    d = ImageDraw.Draw(im)
    lw = max(2, round(PEN_W * ASPECT * scale))
    for color, pts in st:
        xy = [((x - left) * ASPECT * scale, (y - top) * scale) for x, y in pts]
        d.line(xy, fill=color + (255,), width=lw, joint="curve")
        for x, y in (xy[0], xy[-1]):
            d.ellipse([x - lw / 2, y - lw / 2, x + lw / 2, y + lw / 2], fill=color + (255,))


def piece_png(kind: str, side: int = 512) -> bytes:
    """DiaryRedraw.kt pieceToPng: transparent, cropped to the strokes + 2 %, long side [side]."""
    st = strokes_of(kind)
    xs = [x for _, p in st for x, _ in p]; ys = [y for _, p in st for _, y in p]
    pad = 0.02
    left, top = min(xs) - pad / ASPECT, min(ys) - pad
    right, bottom = max(xs) + pad / ASPECT, max(ys) + pad
    w_u, h_u = (right - left) * ASPECT, bottom - top
    scale = side / max(w_u, h_u)
    im = Image.new("RGBA", (int(w_u * scale), int(h_u * scale)), (0, 0, 0, 0))
    _draw(st, im, left, top, scale)
    buf = io.BytesIO(); im.save(buf, "PNG")
    return buf.getvalue()


def board_png(kind: str) -> Image.Image:
    """The piece where it was drawn on the whole board, on white paper — BOARD_W × BOARD_H."""
    im = Image.new("RGBA", (BOARD_W, BOARD_H), (255, 255, 255, 255))
    _draw(strokes_of(kind), im, 0.0, 0.0, BOARD_H)
    return im.convert("RGB")


def band_wash(kind: str, im: Image.Image) -> Image.Image:
    """A flat line encloses nothing, so fill_closed has nothing to fill. Wash the band each line stands for:
    a line in the top third is a sky edge (wash up to the top), any other line is a ground or water edge
    (wash down to the next line below, or the bottom). Same mix as fill_closed."""
    st = strokes_of(kind)
    arr = np.asarray(im).astype(float)
    out = arr.copy()
    W, H = im.size
    # each stroke's height at every column, in pixels
    def y_at(pts, xs):
        px = np.array([x * W for x, _ in pts]); py = np.array([y * H for _, y in pts])
        o = np.argsort(px)
        return np.interp(xs, px[o], py[o], left=np.nan, right=np.nan)
    cols = np.arange(W)
    lines = [(color, y_at(pts, cols)) for color, pts in st]
    paper = arr.min(axis=2) > 200
    for color, ys in lines:
        wash = np.array(color) * character.FILL_MIX + 255 * (1 - character.FILL_MIX)
        sky = np.nanmean(ys) < H / 3
        for x in cols:
            y = ys[x]
            if np.isnan(y):
                continue
            if sky:
                y0, y1 = 0, int(y)
            else:
                below = [l[x] for _, l in lines if not np.isnan(l[x]) and l[x] > y + 4]
                y0, y1 = int(y), int(min(below)) if below else H
            seg = paper[y0:y1, x]
            out[y0:y1, x][seg] = wash
    return Image.fromarray(out.clip(0, 255).astype(np.uint8))


PENCIL_BG = (", colored pencil drawing of a wide landscape, children's picture diary illustration, "
             "loose hand-drawn colored pencil strokes, light colored pencil shading on white paper, "
             "soft and gentle, simple, empty scene, no characters, no people, no animals, no text")
PENCIL_BG_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, cut paper, collage, felt, "
                 "blurry, ugly, scary, dark, horror, frame, border, people, person, child, animal, character, "
                 "nudity, blood, weapon, gore")

# name, whole board?, band wash?, denoise
VARIANTS = [
    ("0 지금 경로 (잘라 보냄 · 물건 색연필)", False, False, 0.85),
    ("1 판 전체 · 색연필 배경 0.85", True, False, 0.85),
    ("2 판 전체 · 색연필 배경 0.9", True, False, 0.9),
    ("3 판 전체 + 띠 채움 · 0.85", True, True, 0.85),
    ("4 판 전체 + 띠 채움 · 0.9", True, True, 0.9),
    ("5 판 전체 + 띠 채움 · 0.88", True, True, 0.88),      # between 3 and 4 (진웅 10-06)
]
# --only 5 → run just these rows; the sheets still show every row found in OUT
ONLY = {int(i) for i in sys.argv[sys.argv.index("--only") + 1].split(",")} if "--only" in sys.argv else None


def to_png(im: Image.Image) -> bytes:
    buf = io.BytesIO(); im.save(buf, "PNG")
    return buf.getvalue()


def graph(subject: str, seed: int, drawing_b64: str, denoise: float, board: bool) -> dict:
    wf = comfy.redraw_workflow(subject, seed, drawing_b64, denoise=denoise, mode="diary")
    if board:
        wf["2"]["inputs"]["text"] = f"{subject}{PENCIL_BG}"
        wf["3"]["inputs"]["text"] = PENCIL_BG_NEG
    return wf


async def main() -> None:
    log = []
    for kind, subject in SUBJECTS.items():
        (OUT / f"{kind}_src.png").write_bytes(to_png(board_png(kind)))
        for vi, (name, board, wash, dn) in enumerate(VARIANTS):
            if ONLY is not None and vi not in ONLY:
                continue
            if board:
                im = board_png(kind)
                if wash:
                    im = band_wash(kind, im)
                sent = to_png(im)
            else:
                sent = character.prepare_drawing(piece_png(kind))
            (OUT / f"{kind}_{vi}_sent.png").write_bytes(sent)
            drawing = base64.b64encode(sent).decode()
            for seed in SEEDS:
                t = time.monotonic()
                raw = await comfy.run(graph(subject, seed, drawing, dn, board))
                secs = time.monotonic() - t
                err = None
                if not board:
                    try:
                        raw = character.cut_out_all(raw)
                    except character.CutoutError as e:
                        err = str(e)
                (OUT / f"{kind}_{vi}_{seed}.png").write_bytes(raw)
                log.append({"kind": kind, "variant": name, "seed": seed, "secs": round(secs, 2),
                            "size": list(Image.open(io.BytesIO(raw)).size), "cutout_error": err})
                print(f"{kind}_{vi}_{seed} {secs:.1f}s {err or ''}", flush=True)
    (OUT / ("log.json" if ONLY is None else f"log_only_{'_'.join(map(str, sorted(ONLY)))}.json")).write_text(json.dumps(log, ensure_ascii=False, indent=1), encoding="utf-8")
    sheets()


def sheets() -> None:
    """One sheet per drawing: rows = variants, columns = what was sent + 3 seeds."""
    font = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 20)
    cw, ch, label = 320, 200, 330
    for kind in SUBJECTS:
        sheet = Image.new("RGB", (label + cw * (1 + len(SEEDS)), ch * len(VARIANTS)), (250, 246, 236))
        d = ImageDraw.Draw(sheet)
        for vi, v in enumerate(VARIANTS):
            y = vi * ch
            d.text((10, y + ch // 2 - 12), v[0], fill=(40, 30, 20), font=font)
            for ci, p in enumerate([OUT / f"{kind}_{vi}_sent.png"] + [OUT / f"{kind}_{vi}_{s}.png" for s in SEEDS]):
                x = label + cw * ci
                if p.exists():
                    im = Image.open(p).convert("RGBA"); im.thumbnail((cw - 8, ch - 8))
                    bg = Image.new("RGBA", im.size, (255, 255, 255, 255)); bg.alpha_composite(im)
                    sheet.paste(bg.convert("RGB"), (x + 4, y + 4))
                else:
                    d.text((x + 20, y + ch // 2), "실패", fill=(200, 40, 40), font=font)
            d.line([(0, y + ch - 1), (sheet.width, y + ch - 1)], fill=(220, 210, 190))
        sheet.save(OUT / f"sheet_{kind}.png")


if __name__ == "__main__":
    if "--sheets" in sys.argv:
        sheets()
    else:
        asyncio.run(main())
