"""Cut a cutout character into puppet parts along the drawing's own ink lines.

09-21 measured that the model will NOT generate parts separately — asking for
"disassembled, not touching, lower jaw apart" returned a whole dinosaur in a
different pose, twice. So parts get cut after generation instead, which is
affordable because characters are presets: a finite set, cut once, reused
every session.

⚠️ The first version of this traced part outlines by hand and the result was
unusable: a polygon drawn by eye cuts straight through teeth, tongue and
belly, so the pieces came out ragged and the sockets looked like lasso
mistakes. The fix is to stop treating the polygon as the cut.

**The black outline in the artwork is already the cut line.** This style draws
every part with a thick closed contour, so the flat colour areas between the
lines are exactly the pieces we want. The polygon only has to say WHICH side
of the line each region is on — it never has to be accurate. Concretely:

  1. ink   = dark pixels (the contour)
  2. label the connected non-ink regions — each is one flat colour area
  3. keep a region when most of it falls inside the rough polygon
  4. dilate the union so the piece takes its own contour with it

A region is all-or-nothing, so every cut edge lands on a line the illustrator
drew. Sloppy input polygon, clean output. This is why the human's job is a
rough lasso, not tracing — and why it is per preset character, not per session.

    python split_parts.py char.png out.png
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

# Rough guides traced against assets/bench/char_paper.png after cutout.py, at
# 613x541. Accuracy does not matter — only which regions fall mostly inside.
# `pivot` does matter: it is the joint, NOT the centre of the piece. Rotating
# a jaw around its middle swings it off the face.
PARTS = [
    {
        "name": "아래턱",
        "poly": [(372, 158), (420, 174), (468, 192), (508, 198), (548, 212),
                 (536, 254), (474, 248), (424, 230), (386, 206), (360, 180)],
        "pivot": (386, 172),
        "shift": (168, -118),
        "note": "입을 벌렸다 다뭅니다",
    },
    {
        "name": "뒷다리",
        "poly": [(404, 336), (440, 330), (462, 382), (458, 432), (444, 460),
                 (472, 470), (520, 474), (535, 492), (528, 507), (382, 507),
                 (378, 468), (386, 418), (392, 368)],
        "pivot": (422, 348),
        "shift": (182, 58),
        "note": "걸음을 만듭니다",
    },
]

INK_LUMA = 118      # below this is contour, not fill
MIN_REGION = 55     # px; smaller blobs are anti-aliasing, not parts
KEEP_INSIDE = 0.55  # a region joins the part when this much of it is inside
GROW = 3            # px; how much contour the piece carries away with it

PAPER = (251, 250, 247, 255)
INK = (22, 24, 31, 255)
SLATE = (90, 96, 112, 255)
CURTAIN = (123, 45, 59, 255)
PAD = 40
LABEL = 215


def font(size):
    """Korean labels need a real face; PIL's default has no Hangul."""
    for path in (r"C:\Windows\Fonts\malgun.ttf", r"C:\Windows\Fonts\gulim.ttc"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def dashed(draw, a, b, fill, dash=7, gap=6):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0))
    if not n:
        return
    for i in range(int(n / (dash + gap)) + 1):
        t0 = i * (dash + gap) / n
        if t0 >= 1:
            break
        t1 = min(t0 + dash / n, 1.0)
        draw.line([(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0),
                   (x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1)], fill=fill, width=2)


def regions(im):
    """Label every flat colour area enclosed by the artwork's contour."""
    a = np.asarray(im)
    opaque = a[..., 3] > 40
    fill = (a[..., :3].mean(2) >= INK_LUMA) & opaque
    lab, n = ndimage.label(fill)
    return lab, n, opaque


def part_mask(lab, n, opaque, poly, size):
    """Union the regions lying mostly inside `poly`, then take the contour."""
    guide = Image.new("L", size, 0)
    ImageDraw.Draw(guide).polygon(poly, fill=1)
    inside = np.asarray(guide).astype(bool)

    keep = np.zeros(n + 1, bool)
    for idx, total in enumerate(np.bincount(lab.ravel(), minlength=n + 1)):
        if idx and total >= MIN_REGION:
            hit = np.count_nonzero(inside & (lab == idx))
            keep[idx] = hit / total >= KEEP_INSIDE
    mask = keep[lab]
    # Grow into the surrounding ink so the piece keeps its own black outline —
    # without this every part comes out looking like it was cut with scissors
    # through the line weight.
    mask = ndimage.binary_dilation(mask, iterations=GROW) & opaque
    return Image.fromarray((mask * 255).astype(np.uint8), "L")


def main():
    src, out = sys.argv[1], sys.argv[2]
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    lab, n, opaque = regions(im)

    dx = max(p["shift"][0] for p in PARTS)
    up = -min(p["shift"][1] for p in PARTS)
    dn = max(p["shift"][1] for p in PARTS)
    canvas = Image.new("RGBA", (w + dx + PAD * 2 + LABEL, h + up + dn + PAD * 2), PAPER)
    ox, oy = PAD, PAD + up

    masks = [part_mask(lab, n, opaque, p["poly"], im.size) for p in PARTS]

    gone = Image.new("L", im.size, 0)
    for m in masks:
        gone.paste(255, (0, 0), m)
    body = im.copy()
    body.putalpha(Image.composite(Image.new("L", im.size, 0), body.getchannel("A"), gone))

    # A ghost of the piece where it used to sit. An empty socket reads as a
    # rendering bug; the ghost reads as "this came from here".
    for m in masks:
        ghost = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ghost.paste(im, (0, 0), m)
        ghost.putalpha(ghost.getchannel("A").point(lambda v: v // 6))
        canvas.alpha_composite(ghost, (ox, oy))
    canvas.alpha_composite(body, (ox, oy))

    d = ImageDraw.Draw(canvas)
    f_name, f_note, f_key = font(26), font(18), font(16)

    for part, m in zip(PARTS, masks):
        piece = Image.new("RGBA", im.size, (0, 0, 0, 0))
        piece.paste(im, (0, 0), m)
        sx, sy = part["shift"]
        canvas.alpha_composite(piece, (ox + sx, oy + sy))

        px, py = part["pivot"]
        a, b = (ox + px, oy + py), (ox + px + sx, oy + py + sy)
        dashed(d, a, b, SLATE)
        for c in (a, b):
            d.ellipse([c[0] - 7, c[1] - 7, c[0] + 7, c[1] + 7], outline=CURTAIN, width=3)
            d.ellipse([c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2], fill=CURTAIN)

        # Anchor the label to the piece's own bounds, not to the pivot — the
        # pivot sits inside the art, so a label hung off it lands on the part.
        bx0, by0, bx1, by1 = piece.getbbox()
        tx, ty = ox + sx + bx1 + 22, oy + sy + (by0 + by1) // 2 - 24
        d.text((tx, ty), part["name"], font=f_name, fill=INK)
        d.text((tx, ty + 34), part["note"], font=f_note, fill=SLATE)

    y = canvas.height - PAD - 18
    d.ellipse([PAD, y, PAD + 14, y + 14], outline=CURTAIN, width=3)
    d.text((PAD + 24, y - 3), "회전축 — 앱이 이 점을 중심으로 돌립니다", font=f_key, fill=SLATE)

    canvas.convert("RGB").save(out, quality=95)
    print("%s  %dx%d" % (out, canvas.width, canvas.height))


if __name__ == "__main__":
    main()
