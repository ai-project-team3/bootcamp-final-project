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
from PIL import Image

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
