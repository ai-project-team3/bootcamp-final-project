"""Diary redraw quality bench (10-05) — the cheap ways first: A · B · C on the same drawings.

Same PC2 ComfyUI and model as the product (SDXL base + Lightning 8-step), same path as /image
redraw: prepare_drawing → redraw graph → cut_out_all. Only the input size, the subject words,
the style words and denoise change. No scene LLM, no safety check (fixed subjects, eyes only).

Drawings are drawn by script the way the app draws a piece (DiaryRedraw.kt pieceToPng: transparent,
cropped to the strokes, long side 512, round caps, PEN_W = 0.014 of the board width), with a wobble
so they look hand-drawn. With --real DIR, real children's drawings made by real_pieces.py instead.

Run from backend/ with the backend venv:  COMFY_URL=http://<pc2>:8188 python ../eval/redraw_1005/bench.py OUT [flags]
  (none) round 1 — now · A 1024 · B colour names · C felt 0.85 / 0.9 · B+C
  --r2   round 2 — F fill × felt / colored pencil
  --real DIR          round 3 — real drawings: now · C · F · F+B
  --r4 [--real DIR]   round 4 — now · F · S soft pencil · F+S   (results: eval/results.md 10-05)
  --sheets            only redraw the comparison sheets from OUT
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
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from app.image import character, comfy  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
SEEDS = [7, 11, 23]

# ── drawings, in board units (height 1, width ASPECT) like the app ────────────────
ASPECT = 1.6
PEN_W = 0.014
RED, ORANGE, YELLOW, GREEN = (232, 80, 70), (240, 140, 50), (245, 200, 50), (90, 170, 80)
BLUE, PURPLE, PINK, BROWN, BLACK = (60, 120, 220), (140, 90, 200), (235, 120, 170), (140, 90, 55), (50, 40, 35)


def wob(pts, rnd, k=0.006):
    return [(x + rnd.uniform(-k, k), y + rnd.uniform(-k, k)) for x, y in pts]


def ring(cx, cy, rx, ry, n=28):
    return [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n)) for i in range(n + 1)]


def strokes_of(kind: str) -> list[tuple[tuple, list]]:
    rnd = random.Random(kind)
    s = []
    if kind == "house":
        s += [(RED, wob([(0.30, 0.42), (0.45, 0.25), (0.60, 0.42), (0.30, 0.42)], rnd))]
        s += [(BLUE, wob([(0.33, 0.42), (0.33, 0.65), (0.57, 0.65), (0.57, 0.42)], rnd))]
        s += [(BROWN, wob([(0.42, 0.65), (0.42, 0.53), (0.48, 0.53), (0.48, 0.65)], rnd))]
        s += [(YELLOW, wob(ring(0.37, 0.49, 0.025, 0.03, 12), rnd, 0.003))]
    elif kind == "sun":
        s += [(YELLOW, wob(ring(0.45, 0.45, 0.10, 0.10), rnd))]
        for a in range(0, 360, 45):
            c, d = math.cos(math.radians(a)), math.sin(math.radians(a))
            s += [(ORANGE, wob([(0.45 + 0.13 * c, 0.45 + 0.13 * d), (0.45 + 0.19 * c, 0.45 + 0.19 * d)], rnd, 0.004))]
        s += [(RED, wob([(0.41, 0.47), (0.45, 0.50), (0.49, 0.47)], rnd, 0.003))]
    elif kind == "mom":
        s += [(BLACK, wob(ring(0.45, 0.22, 0.055, 0.06), rnd))]
        s += [(BROWN, wob([(0.40, 0.18), (0.38, 0.36)], rnd)), (BROWN, wob([(0.50, 0.18), (0.52, 0.36)], rnd))]
        s += [(PINK, wob([(0.45, 0.29), (0.36, 0.55), (0.54, 0.55), (0.45, 0.29)], rnd))]
        s += [(BLACK, wob([(0.41, 0.36), (0.33, 0.44)], rnd)), (BLACK, wob([(0.49, 0.36), (0.57, 0.44)], rnd))]
        s += [(BLACK, wob([(0.42, 0.55), (0.41, 0.68)], rnd)), (BLACK, wob([(0.48, 0.55), (0.49, 0.68)], rnd))]
    elif kind == "dog":
        s += [(BROWN, wob(ring(0.45, 0.50, 0.12, 0.07), rnd))]
        s += [(BROWN, wob(ring(0.60, 0.40, 0.055, 0.05), rnd))]
        s += [(RED, wob([(0.56, 0.45), (0.62, 0.46)], rnd, 0.003))]          # collar
        for x in (0.37, 0.42, 0.49, 0.54):
            s += [(BROWN, wob([(x, 0.56), (x, 0.66)], rnd))]
        s += [(BROWN, wob([(0.33, 0.48), (0.28, 0.42)], rnd))]
    return s


def piece_png(kind: str, side: int) -> bytes:
    """DiaryRedraw.kt pieceToPng, in Python: crop to strokes + 2 % pad, long side [side]."""
    st = strokes_of(kind)
    xs = [x for _, p in st for x, _ in p]; ys = [y for _, p in st for _, y in p]
    pad = 0.02
    left, top = min(xs) - pad / ASPECT, min(ys) - pad
    right, bottom = max(xs) + pad / ASPECT, max(ys) + pad
    w_u, h_u = (right - left) * ASPECT, bottom - top
    scale = side / max(w_u, h_u)
    im = Image.new("RGBA", (int(w_u * scale), int(h_u * scale)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    lw = max(2, round(PEN_W * ASPECT * scale))
    for color, pts in st:
        xy = [((x - left) * ASPECT * scale, (y - top) * scale) for x, y in pts]
        d.line(xy, fill=color + (255,), width=lw, joint="curve")
        for x, y in (xy[0], xy[-1]):                                        # round caps
            d.ellipse([x - lw / 2, y - lw / 2, x + lw / 2, y + lw / 2], fill=color + (255,))
    buf = io.BytesIO(); im.save(buf, "PNG")
    return buf.getvalue()


SUBJECTS = {"house": "small house with a pointed roof", "sun": "smiling sun",
            "mom": "mom with long hair", "dog": "little puppy"}

# ── B: the child's colors, read from the drawing on the server (no LLM sees it) ───────
NAMED = {"red": RED, "orange": ORANGE, "yellow": YELLOW, "green": GREEN, "blue": BLUE,
         "purple": PURPLE, "pink": PINK, "brown": BROWN, "black": BLACK}


def colors_of(png: bytes, top: int = 3, share: float = 0.08) -> list[str]:
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    count: dict[str, int] = {}
    for r, g, b, a in im.getdata():
        if a < 128:
            continue
        name = min(NAMED, key=lambda n: sum((c - k) ** 2 for c, k in zip((r, g, b), NAMED[n])))
        count[name] = count.get(name, 0) + 1
    total = sum(count.values()) or 1
    return [n for n, c in sorted(count.items(), key=lambda kv: -kv[1]) if c / total >= share][:top]


def with_colors(subject: str, cols: list[str]) -> str:
    if not cols:
        return subject
    return f"{subject} in {' and '.join(cols) if len(cols) < 3 else ', '.join(cols[:-1]) + ' and ' + cols[-1]}"


# ── C: the app's felt look (android/tools/otto_art.py "felt"), worded for SDXL ───────
FELT_STYLE = (", soft felt fabric craft toy, children's picture book illustration, cute rounded shapes, "
              "visible felt texture and stitched edges, warm pastel colors, isolated on plain pure white background, "
              "no shadow, no text")
FELT_NEG = ("text, letters, watermark, photo, photorealistic, pencil, sketch, line art, cut paper, collage, "
            "blurry, ugly, scary, dark, horror, background scenery, frame, border, card, colored background, "
            "multiple subjects, nudity, blood, weapon, gore")


def graph(subject: str, seed: int, drawing_b64: str, style: str, neg: str, denoise: float, felt: bool) -> dict:
    wf = comfy.redraw_workflow(subject, seed, drawing_b64, denoise=denoise, mode="diary")
    if felt:
        wf["2"]["inputs"]["text"] = f"a cute felt toy {subject}, full view{style}"
    else:
        wf["2"]["inputs"]["text"] = f"a cute {subject}, full view{style}"
    wf["3"]["inputs"]["text"] = neg
    return wf


# ── F: fill what the child closed (a roof, a face, a body) with a pale wash of its outline ─
# Round 1: an outline on white comes back as an outline at 0.85 (the inside stays paper), and at
# 0.9 the felt is lovely but the layout goes (the side-on dog sits facing front). Give the model
# mass to keep: every paper region not reached from the frame gets the colour of the line around it.
# The fill measured here is now app/image/character.py fill_closed (pixel-identical on all 13
# drawings) — the bench calls it so the two cannot drift apart.
fill_closed = character.fill_closed


# name, input side, colors?, style, neg, denoise, felt-subject[, fill]
VARIANTS_R1 = [
    ("0 지금 (512 · 색연필 0.85)", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False),
    ("A 1024 로 그려 보냄", 1024, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False),
    ("B 색 이름 넣기", 512, True, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False),
    ("C 펠트 0.85", 512, False, FELT_STYLE, FELT_NEG, 0.85, True),
    ("C 펠트 0.9", 512, False, FELT_STYLE, FELT_NEG, 0.9, True),
    ("B+C 펠트 0.85 + 색", 512, True, FELT_STYLE, FELT_NEG, 0.85, True),
]
# round 2 — the fill, against round 1's best of each
VARIANTS_R2 = [
    ("0 지금 (색연필 0.85)", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False, False),
    ("C 펠트 0.88", 512, False, FELT_STYLE, FELT_NEG, 0.88, True, False),
    ("F 채움 + 펠트 0.85", 512, False, FELT_STYLE, FELT_NEG, 0.85, True, True),
    ("F 채움 + 펠트 0.88", 512, False, FELT_STYLE, FELT_NEG, 0.88, True, True),
    ("F+B 채움 + 펠트 0.88 + 색", 512, True, FELT_STYLE, FELT_NEG, 0.88, True, True),
    ("F 채움 + 색연필 0.85", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False, True),
]
# round 3 — real children's drawings (real_pieces.py): subjects as the redraw LLM would word the names
VARIANTS_R3 = [
    ("0 지금 (색연필 0.85)", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False, False),
    ("C 펠트 0.88", 512, False, FELT_STYLE, FELT_NEG, 0.88, True, False),
    ("F 채움 + 펠트 0.88", 512, False, FELT_STYLE, FELT_NEG, 0.88, True, True),
    ("F+B 채움 + 펠트 0.88 + 색", 512, True, FELT_STYLE, FELT_NEG, 0.88, True, True),
]
REAL_SUBJECTS = {"boy": "little boy", "ghost": "cute little ghost", "girl": "little girl", "kitty": "cat",
                 "ladybug": "ladybug", "man": "dad", "monster": "cute little monster", "puppy": "little puppy",
                 "robot": "robot"}
# round 4 — the diary stays in colored pencil (진웅 10-05: felt does not suit a picture diary).
# Round 3 showed the pencil going photo-real on real drawings (a furry tabby, a ladybug with insect
# legs, a hairy monster), so: the same pencil, worded cute and simple, with and without the fill.
SOFT_STYLE = (", cute simple colored pencil drawing, children's picture book illustration, soft rounded shapes, "
              "loose hand-drawn colored pencil outlines, light colored pencil shading, gentle and friendly, "
              "isolated on plain pure white paper background, no shadow, no text")
SOFT_NEG = comfy.DIARY_NEG + ", realistic, hyperdetailed, detailed fur, fine hair, creepy, menacing, sharp teeth"
VARIANTS_R4 = [
    ("0 지금 (색연필 0.85)", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False, False),
    ("F 채움 + 색연필 0.85", 512, False, comfy.DIARY_STYLE, comfy.DIARY_NEG, 0.85, False, True),
    ("S 순한 색연필 0.85", 512, False, SOFT_STYLE, SOFT_NEG, 0.85, False, False),
    ("F+S 채움 + 순한 색연필 0.85", 512, False, SOFT_STYLE, SOFT_NEG, 0.85, False, True),
]
REAL = sys.argv[sys.argv.index("--real") + 1] if "--real" in sys.argv else None
if "--r4" in sys.argv:
    VARIANTS = VARIANTS_R4
    if REAL:
        SUBJECTS = REAL_SUBJECTS
elif REAL:
    VARIANTS, SUBJECTS = VARIANTS_R3, REAL_SUBJECTS
else:
    VARIANTS = VARIANTS_R2 if "--r2" in sys.argv else VARIANTS_R1


def source(kind: str, side: int) -> bytes:
    """The piece the app would send: a real drawing from --real, else the scripted one."""
    if REAL:
        return (Path(REAL) / f"{kind}.png").read_bytes()
    return piece_png(kind, side)


async def main() -> None:
    log = []
    for kind, subject in SUBJECTS.items():
        for vi, (name, side, use_col, style, neg, dn, felt, *fill) in enumerate(VARIANTS):
            src = source(kind, side)
            subj = with_colors(subject, colors_of(src)) if use_col else subject
            prepared = character.prepare_drawing(src)
            if fill and fill[0]:
                prepared = fill_closed(prepared)
                (OUT / f"{kind}_{vi}_filled.png").write_bytes(prepared)
            drawing = base64.b64encode(prepared).decode()
            for seed in SEEDS:
                t = time.monotonic()
                raw = await comfy.run(graph(subj, seed, drawing, style, neg, dn, felt))
                secs = time.monotonic() - t
                try:
                    cut, err = character.cut_out_all(raw), None
                except character.CutoutError as e:
                    cut, err = None, str(e)
                stem = f"{kind}_{vi}_{seed}"
                (OUT / f"{stem}_raw.png").write_bytes(raw)
                if cut:
                    (OUT / f"{stem}_cut.png").write_bytes(cut)
                log.append({"kind": kind, "variant": name, "seed": seed, "subject": subj,
                            "secs": round(secs, 2), "cutout_error": err})
                print(f"{stem} {secs:.1f}s {subj!r} {err or ''}", flush=True)
        (OUT / f"{kind}_src.png").write_bytes(source(kind, 512))
    (OUT / "log.json").write_text(json.dumps(log, ensure_ascii=False, indent=1), encoding="utf-8")
    sheets()


def sheets() -> None:
    """One sheet per drawing: rows = variants, columns = the input + 3 seeds (cut pieces on paper)."""
    font = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 22)
    cell, label = 256, 300
    for kind in SUBJECTS:
        sheet = Image.new("RGB", (label + cell * (1 + len(SEEDS)), cell * len(VARIANTS)), (250, 246, 236))
        d = ImageDraw.Draw(sheet)
        src = Image.open(OUT / f"{kind}_src.png").convert("RGBA"); src.thumbnail((cell - 16, cell - 16))
        for vi, v in enumerate(VARIANTS):
            y = vi * cell
            d.text((10, y + cell // 2 - 12), v[0], fill=(40, 30, 20), font=font)
            sheet.paste(src, (label + 8, y + 8), src)
            for si, seed in enumerate(SEEDS):
                p = OUT / f"{kind}_{vi}_{seed}_cut.png"
                x = label + cell * (1 + si)
                if p.exists():
                    im = Image.open(p).convert("RGBA"); im.thumbnail((cell - 8, cell - 8))
                    sheet.paste(im, (x + 4, y + 4), im)
                else:
                    d.text((x + 20, y + cell // 2), "오려 내기 실패", fill=(200, 40, 40), font=font)
            d.line([(0, y + cell - 1), (sheet.width, y + cell - 1)], fill=(220, 210, 190))
        sheet.save(OUT / f"sheet_{kind}.png")


if __name__ == "__main__":
    if "--sheets" in sys.argv:
        sheets()
    else:
        asyncio.run(main())
