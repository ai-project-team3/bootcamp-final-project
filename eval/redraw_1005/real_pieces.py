"""Real children's drawings → app-like pieces (transparent, cropped to what was drawn, long side 512).

Source: Meta's Amateur Drawings dataset (facebookresearch/AnimatedDrawings) — photos of children's
drawings on paper. The images are not in the repo; pass the folder you extracted them to.
Paper goes: each pixel is compared with the paper around it (a blurred copy), so shadows and the
light falling across the page do not count as drawing. Lines are thickened a little toward the
app's brush (PEN_W) so a pencil line is not a hair on 512 px.

    python real_pieces.py <dataset root> <list of files> <out dir>
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "backend"))
from app.image import character  # noqa: E402

SIDE = 512


def piece(path: Path, rim: bool = True) -> Image.Image:
    im = Image.open(path).convert("RGB")
    im.thumbnail((900, 900))
    k = max(3, round(max(im.size) * 0.004) | 1)
    thick = im.filter(ImageFilter.MinFilter(k))                      # spread the dark lines a bit
    rgb = np.asarray(thick).astype(int)
    gray = rgb.mean(axis=2)
    small = Image.fromarray(gray.astype(np.uint8)).resize((64, 64)).filter(ImageFilter.MaxFilter(5))
    paper = np.asarray(small.filter(ImageFilter.GaussianBlur(3)).resize(im.size, Image.BILINEAR)).astype(int)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    ink = ((paper - gray) > 30) | (((paper - gray) > 12) & (sat > 50))
    # what touches the photo's edge is the table, a shadow or the next page — not the drawing;
    # and a speck far from the drawing is dust. Keep the biggest part and what lies around it.
    n = 256
    m_small = np.asarray(Image.fromarray((ink * 255).astype(np.uint8)).resize((n, n), Image.NEAREST)) > 0
    m_small = np.asarray(Image.fromarray((m_small * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 0
    if rim:
        edge = round(n * 0.06)                    # the photo's rim: table, shadow, the page's edge
        m_small[:edge] = m_small[-edge:] = False
        m_small[:, :edge] = m_small[:, -edge:] = False

    def spans(blob):                             # a ruled line or the paper's edge runs across the photo
        ys, xs = np.nonzero(blob)
        return (xs.max() - xs.min()) > 0.85 * n or (ys.max() - ys.min()) > 0.85 * n
    parts = [(size, blob) for sides, size, blob in character._components(m_small)
             if size >= 0.0005 * n * n and not spans(blob) and (rim or sides == 0)]
    keep = np.zeros_like(m_small)
    if parts:
        big = max(parts, key=lambda p: p[0])[1]
        ys, xs = np.nonzero(big)
        my, mx = (ys.max() - ys.min()) * 0.3, (xs.max() - xs.min()) * 0.3
        y0, y1, x0, x1 = ys.min() - my, ys.max() + my, xs.min() - mx, xs.max() + mx
        for _, blob in parts:
            by, bx = np.nonzero(blob)
            if by.min() >= y0 and by.max() <= y1 and bx.min() >= x0 and bx.max() <= x1:
                keep |= blob
    keep_big = np.asarray(Image.fromarray((keep * 255).astype(np.uint8)).resize(im.size, Image.NEAREST)
                          .filter(ImageFilter.MaxFilter(5))) > 0
    alpha = (ink & keep_big).astype(np.uint8) * 255
    out = Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha]), "RGBA")
    box = Image.fromarray(alpha).getbbox()
    out = out.crop(box)
    out.thumbnail((SIDE, SIDE), Image.LANCZOS)
    return out


if __name__ == "__main__":
    root, listing, dest = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    dest.mkdir(parents=True, exist_ok=True)
    for line in listing.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        name, rel, *mode = line.split()           # a third word "norim": keep the photo's rim
        piece(root / rel, rim=mode != ["norim"]).save(dest / f"{name}.png")
        print(name)
