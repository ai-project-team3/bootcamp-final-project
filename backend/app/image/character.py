"""A generated character → a picture the app can stand on the stage and rig.

The spec is 안치영's (docs/캐릭터_생성_규격.md §2): square 640, fully transparent background,
**feet 7% above the bottom** (the app's FEET_IN_ART = 0.93), body 55~75% of the width.
The cut-out is hers too (assets/tools/cutout.py): the background is bright and grey
(not pure white — SDXL paints a gradient), the character is coloured or dark; keep the
largest blob that does not run along the frame (confetti and a paper card around the
doll are dropped), fill holes (white of the eyes, glasses), soften the edge one pixel.

Pure numpy + Pillow, no model: nothing to install, runs in well under a second.
"""
from __future__ import annotations

import io
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

TEMPLATES = Path(__file__).parent / "templates"

SIZE = 640
FEET = 0.93          # feet line, fraction of the height (app: FEET_IN_ART)
MAX_W = 0.75         # widest the body may be (spec: 55~75%)
TOP = 0.04           # headroom

SAT_MAX = 20         # background = low saturation …
VAL_MIN = 188        # … and bright
MIN_AREA = 0.03      # a blob smaller than this is not a character


class CutoutError(Exception):
    pass


DRAWING = 1024      # SDXL's square
DRAWING_FILL = 0.8  # the drawing takes up this much of the square, white around it


def prepare_drawing(png: bytes) -> bytes:
    """The child's piece (transparent background, only what was drawn) → 1024² RGB on white.

    Transparent pixels become white so img2img sees paper, not black. Raises CutoutError on
    something that is not a picture. Runs in memory; the caller keeps no copy.
    """
    try:
        im = Image.open(io.BytesIO(png))
        im.load()
    except Exception as e:
        raise CutoutError(f"not a picture: {type(e).__name__}") from e
    im = im.convert("RGBA")
    box = im.getchannel("A").getbbox()
    if not box:
        raise CutoutError("empty drawing")
    im = im.crop(box)
    w, h = im.size
    scale = DRAWING * DRAWING_FILL / max(w, h)
    im = im.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    paper = Image.new("RGBA", (DRAWING, DRAWING), (255, 255, 255, 255))
    paper.alpha_composite(im, ((DRAWING - im.width) // 2, (DRAWING - im.height) // 2))
    out = io.BytesIO(); paper.convert("RGB").save(out, "PNG")
    return out.getvalue()


# A background piece is sent as the whole board (#168 · 10-06): ground · sky lines mean where they are, so it is
# neither cropped nor centred. SDXL-sized like the bench (eval/diary_bg_1006: 1232×768 for a 1.6 board).
BOARD_H = 768
BOARD_MAX_W = 1536


def prepare_board(png: bytes) -> bytes:
    """The whole board (transparent, lines where the child drew them) → RGB on white, 768 high, width a
    multiple of 8 from the board's own shape (at most 2:1). Raises CutoutError like prepare_drawing."""
    try:
        im = Image.open(io.BytesIO(png))
        im.load()
    except Exception as e:
        raise CutoutError(f"not a picture: {type(e).__name__}") from e
    im = im.convert("RGBA")
    if not im.getchannel("A").getbbox():
        raise CutoutError("empty drawing")
    w = min(BOARD_MAX_W, max(BOARD_H, round(BOARD_H * im.width / im.height / 8) * 8))
    paper = Image.new("RGBA", (w, BOARD_H), (255, 255, 255, 255))
    paper.alpha_composite(im.resize((w, BOARD_H), Image.LANCZOS))
    out = io.BytesIO(); paper.convert("RGB").save(out, "PNG")
    return out.getvalue()


def wash_bands(board: bytes) -> bytes:
    """A prepared board → each flat line's band washed with its colour (same mix as fill_closed).

    A flat line encloses nothing, so fill_closed has nothing to fill, and on white the redraw came back as
    a thin decorated line with paper all round (eval 10-06, row 1). A line in the top third is a sky edge:
    washed up to the top. Any other line is a ground or water edge: washed down to the next line, or the
    bottom. Lines are never painted over; dark lines keep their paper like fill_closed.
    """
    im = Image.open(io.BytesIO(board)).convert("RGB")
    W, H = im.size
    sw, sh = max(1, W // 4), max(1, H // 4)
    small = np.asarray(im.resize((sw, sh), Image.BILINEAR)).astype(int)
    ink = small.min(axis=2) <= 225
    lines = [blob for _, size, blob in _components(ink) if size >= 4]
    if not lines:
        return board
    rows = np.arange(sh)[:, None]
    tops = []                                     # each line's height in every column (nan where it is not)
    for blob in lines:
        cnt = blob.sum(axis=0)
        mean = np.where(cnt > 0, (blob * rows).sum(axis=0) / np.maximum(cnt, 1), np.nan)
        tops.append(mean)
    wash = np.zeros((sh, sw, 3)); painted = np.zeros((sh, sw), bool)
    for blob, ys in zip(lines, tops):
        colour = small[blob].mean(axis=0)
        if colour.max() < FILL_DARK:
            continue
        sky = np.nanmean(ys) < sh / 3
        for x in np.nonzero(~np.isnan(ys))[0]:
            y = int(ys[x])
            if sky:
                y0, y1 = 0, y
            else:
                below = [t[x] for t in tops if not np.isnan(t[x]) and t[x] > y + 1]
                y0, y1 = y, int(min(below)) if below else sh
            wash[y0:y1, x] = colour * FILL_MIX + 255 * (1 - FILL_MIX)
            painted[y0:y1, x] = True
    painted &= ~ink
    if not painted.any():
        return board
    big = np.asarray(im).astype(float)
    m = np.asarray(Image.fromarray((painted * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR)) / 255.0
    m = (m * (big.min(axis=2) > 200))[..., None]            # paper only — never over a line
    colour = np.asarray(Image.fromarray(wash.clip(0, 255).astype(np.uint8)).resize((W, H), Image.NEAREST)).astype(float)
    out = big * (1 - m) + colour * m
    buf = io.BytesIO(); Image.fromarray(out.clip(0, 255).astype(np.uint8)).save(buf, "PNG")
    return buf.getvalue()


# fill_closed — 10-05 진웅 (eval/redraw_1005 · docs/review/일기모드_1005_redraw): an outline on white
# comes back from the diary redraw (colored pencil 0.85) as the same outline, the inside left paper.
# Washing each closed shape with its outline colour gave the blue house its blue walls and the
# pink-triangle mom her pink dress; real drawings that were already coloured in, or drawn in
# black, came back unchanged. Same model, same time (4.2 s a picture).
FILL_MIX = 0.45     # share of the outline colour in the wash; the rest is white paper
FILL_DARK = 90      # an outline darker than this (black · dark brown) keeps its paper inside
FILL_MIN = 0.001    # a hole smaller than this share of the picture is the gap inside a line


def fill_closed(drawing: bytes) -> bytes:
    """A prepared drawing (RGB on white) → every closed paper region washed with its outline colour.

    Paper that can be reached from the frame stays paper. The colour is the commonest one on the
    outline around that region (a blue wall with a brown door is blue, not a mix). Lines are never
    painted over. Pure numpy + Pillow, in memory.
    """
    im = Image.open(io.BytesIO(drawing)).convert("RGB")
    n = 256
    small = np.asarray(im.resize((n, n), Image.BILINEAR)).astype(int)
    paper = small.min(axis=2) > 225
    # the outside in one C flood from the corner (prepare_drawing leaves paper all round), then
    # only the few enclosed pixels go through the slow labelling
    flood = Image.fromarray((paper * 255).astype(np.uint8)).copy()     # fromarray is read-only: floodfill would do nothing
    if flood.getpixel((0, 0)) != 255:              # not a prepared drawing: nothing to tell inside from out
        return drawing
    ImageDraw.floodfill(flood, (0, 0), 128)
    enclosed = np.asarray(flood) == 255
    holes = [blob for _, size, blob in _components(enclosed) if size >= FILL_MIN * n * n]
    if not holes:
        return drawing
    big = np.asarray(im).astype(float)
    on_paper = big.min(axis=2) > 200
    out = big.copy()
    washed = False
    for blob in holes:
        grown = np.asarray(Image.fromarray((blob * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7))) > 0
        ring = small[grown & ~blob & ~paper]
        if not len(ring):
            continue
        q = ring // 32
        key = q[:, 0] * 64 + q[:, 1] * 8 + q[:, 2]
        values, counts = np.unique(key, return_counts=True)
        colour = ring[key == values[counts.argmax()]].mean(axis=0)
        if colour.max() < FILL_DARK:
            continue
        wash = colour * FILL_MIX + 255 * (1 - FILL_MIX)
        m = np.asarray(Image.fromarray((blob * 255).astype(np.uint8)).resize(im.size, Image.BILINEAR)) / 255.0
        m = (m * on_paper)[..., None]
        out = out * (1 - m) + wash * m
        washed = True
    if not washed:                                  # only black outlines: the drawing as it came
        return drawing
    buf = io.BytesIO(); Image.fromarray(out.clip(0, 255).astype(np.uint8)).save(buf, "PNG")
    return buf.getvalue()


def template(rig: str) -> bytes | None:
    """The grey mannequin for this rig, or None (blob has no pose to keep)."""
    p = TEMPLATES / f"{rig}.png"
    return p.read_bytes() if p.exists() else None


BG_TOL = 16         # how far from that row's background colour still counts as background


def _foreground(rgb: np.ndarray) -> np.ndarray:
    """Background = pale grey **and connected to the frame** through pale grey.

    09-29 live: a princess in a pale pink dress came back with the dress cut away — the old rule
    (bright + low saturation = background, anywhere) ate every pale part of the body. Now only
    what can be reached from the border counts, measured against *that row's* background colour
    (SDXL paints a vertical grey gradient), so pale colour inside the outline survives.
    """
    img = rgb.astype(int)
    mx, mn = img.max(axis=2), img.min(axis=2)
    pale = (mx - mn < SAT_MAX) & (mn > VAL_MIN)
    # the background colour of each row, from its two edge pixels
    row_bg = (img[:, :1, :] + img[:, -1:, :]) // 2
    near = pale & (np.abs(img - row_bg).max(axis=2) <= BG_TOL)
    h, w = near.shape
    bg = np.zeros_like(near)
    q = deque()
    for y in range(h):
        for x in (0, w - 1):
            if near[y, x] and not bg[y, x]:
                bg[y, x] = True; q.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if near[y, x] and not bg[y, x]:
                bg[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and near[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True; q.append((ny, nx))
    return ~bg


def _components(mask: np.ndarray) -> list[tuple[int, int, np.ndarray]]:
    """(frame sides touched, size, pixel mask) for every 8-connected blob."""
    h, w = mask.shape
    seen = np.zeros_like(mask)
    out = []
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        q = deque([(y0, x0)]); seen[y0, x0] = True
        ys, xs = [], []
        while q:
            y, x = q.popleft(); ys.append(y); xs.append(x)
            for ny in (y - 1, y, y + 1):
                if 0 <= ny < h:
                    for nx in (x - 1, x, x + 1):
                        if 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True; q.append((ny, nx))
        ys_a, xs_a = np.array(ys), np.array(xs)
        sides = int(ys_a.min() == 0) + int(ys_a.max() == h - 1) + int(xs_a.min() == 0) + int(xs_a.max() == w - 1)
        blob = np.zeros_like(mask); blob[ys_a, xs_a] = True
        out.append((sides, len(ys), blob))
    return out


def _fill_holes(blob: np.ndarray) -> np.ndarray:
    h, w = blob.shape
    outside = np.zeros_like(blob)
    q = deque()
    for y in range(h):
        for x in (0, w - 1):
            if not blob[y, x] and not outside[y, x]:
                outside[y, x] = True; q.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if not blob[y, x] and not outside[y, x]:
                outside[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and not blob[ny, nx] and not outside[ny, nx]:
                outside[ny, nx] = True; q.append((ny, nx))
    return blob | ~outside


SPECK = 0.002       # redraw: a piece smaller than this share of the picture is a stray dot


def cut_out_all(png: bytes) -> bytes:
    """A redrawn piece → 640² RGBA, centred, **every** drawn part kept.

    The character cut keeps the largest blob (it drops confetti around a doll), but a child's
    sun has rays and a drawn person may have a head apart from the body — 진웅 10-01 #32 saw
    those pieces thrown away. Here only the paper (pale and reached from the frame) goes;
    specks under [SPECK] go too. Raises CutoutError.
    """
    im = Image.open(io.BytesIO(png)).convert("RGB")
    small = im.resize((256, 256), Image.BILINEAR)
    mask = _foreground(np.asarray(small))
    keep = np.zeros_like(mask)
    for sides, size, blob in _components(mask):
        if size >= SPECK * mask.size and sides <= 2:      # a card or frame touches 3+ sides
            keep |= blob
    if keep.sum() < MIN_AREA * mask.size:
        raise CutoutError("nothing drawn")
    alpha = Image.fromarray((keep * 255).astype(np.uint8)).resize(im.size, Image.BILINEAR)
    rgba = im.copy(); rgba.putalpha(alpha)
    box = alpha.point(lambda v: 255 if v > 127 else 0).getbbox()
    if not box:
        raise CutoutError("empty after cut")
    piece = rgba.crop(box)
    w, h = piece.size
    scale = min(SIZE * 0.9 / w, SIZE * 0.9 / h)
    piece = piece.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(piece, ((SIZE - piece.width) // 2, (SIZE - piece.height) // 2))
    out = io.BytesIO(); canvas.save(out, "PNG")
    return out.getvalue()


def cut_and_fit(png: bytes, centered: bool = False) -> bytes:
    """Generated PNG (white-ish background) → 640² RGBA, feet on the 93% line. Raises CutoutError.

    centered: a redrawn piece of a diary drawing (a house, a sun) has no feet and no rig —
    it goes in the middle instead, no wider or taller than 90%."""
    im = Image.open(io.BytesIO(png)).convert("RGB")
    small = im.resize((256, 256), Image.BILINEAR)           # find the blob small, apply it large
    mask = _foreground(np.asarray(small))
    comps = _components(mask)
    if not comps:
        raise CutoutError("nothing but background")
    overall = max(comps, key=lambda c: c[1])
    inner = [c for c in comps if c[0] <= 1]                  # feet may touch the bottom, a card touches 3+
    best = max(inner, key=lambda c: c[1]) if inner else overall
    if best[1] < overall[1] * 0.25:
        best = overall
    if best[1] < MIN_AREA * mask.size:
        raise CutoutError("character too small to be a character")
    blob = _fill_holes(best[2])

    # back to full size, one pixel of soft edge
    alpha = Image.fromarray((blob * 255).astype(np.uint8)).resize(im.size, Image.BILINEAR)
    rgba = im.copy(); rgba.putalpha(alpha)
    box = alpha.point(lambda v: 255 if v > 127 else 0).getbbox()
    if not box:
        raise CutoutError("empty after cut")
    doll = rgba.crop(box)

    # fit: feet on 93%, no taller than the headroom allows, no wider than 75%
    w, h = doll.size
    scale = (min(SIZE * 0.9 / h, SIZE * 0.9 / w) if centered
             else min(SIZE * (FEET - TOP) / h, SIZE * MAX_W / w))
    doll = doll.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    x = (SIZE - doll.width) // 2
    y = (SIZE - doll.height) // 2 if centered else round(SIZE * FEET) - doll.height
    canvas.alpha_composite(doll, (x, y))
    out = io.BytesIO(); canvas.save(out, "PNG")
    return out.getvalue()
