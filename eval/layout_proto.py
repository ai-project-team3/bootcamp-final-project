"""Scene layout from pre-made felt pieces — prototype of the placement function (10-03).

The lead's call: no stage curtains, a thin ground, and everything in the picture (sun, stars, trees,
flowers) is a pre-made piece copied in, not one generated background. 10-03's first try scattered
pieces by hand and looked empty and random. This is the function, written so it can move to Kotlin:

  1. tags per piece  — role and real size (hero height = 1), set once when the piece is baked
  2. one perspective — depth d (0 horizon … 1 front) gives both the feet line and the scale
  3. a place frame   — sky · sky anchor · sky fill · far band · landmark · cover · foreground,
                       plus areas kept empty (actors, ↩ ↪ buttons)
  4. N candidates    — seeded, scored (overlap · keep-out · balance · empty columns), best wins;
                       the seed is stored, so undo/redo redraws the same scene

  py eval/layout_proto.py   → eval/image_out/layout/{place}_best.png · {place}_candidates.png
Pieces come from eval/image_out/felt_kit and felt_props (made by eval/bench_felt_props.py, 10-03).
"""
import math
import random
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
KIT = ROOT / "image_out" / "felt_kit"
PROPS = ROOT / "image_out" / "felt_props"
OUT = ROOT / "image_out" / "layout"
W, H = 1344, 768
HORIZON = 0.80          # ground is the bottom 20 %
HERO_H = 0.42           # hero height at d = 1, as a share of H


@dataclass(frozen=True)
class Piece:
    name: str
    path: Path
    role: str           # sky_anchor · sky_fill · far · landmark · cover · foreground
    size: float         # real height, hero = 1
    base: str = "feet"  # feet: stands on the ground · center: hangs in the sky
    flip: bool = True
    tilt: float = 0     # max random tilt in degrees (sky pieces)


@dataclass
class Placed:
    piece: Piece
    x: float            # centre x, px
    y: float            # feet y (base feet) or centre y (base center), px
    h: float            # height, px
    flip: bool = False
    tilt: float = 0
    fade: float = 0     # far-band haze, 0 … 1

    def box(self, aspect: float) -> tuple[float, float, float, float]:
        w = self.h * aspect
        top = self.y - self.h if self.piece.base == "feet" else self.y - self.h / 2
        return self.x - w / 2, top, self.x + w / 2, top + self.h


def scale(d: float) -> float:
    return 0.45 + 0.55 * d


def feet_y(d: float) -> float:
    return H * (HORIZON + (1 - HORIZON) * d)


def piece_h(p: Piece, d: float) -> float:
    return p.size * HERO_H * H * scale(d)


K = lambda n: KIT / f"{n}.png"           # noqa: E731
KITS = {
    "space": {
        "sky": ((20, 28, 68), (52, 56, 118)), "ground": ((200, 196, 222), (168, 164, 196)),
        "hills": [(92, 90, 150), (130, 126, 180)],
        "pieces": [
            Piece("moon", K("moon"), "sky_anchor", 0.75, "center", tilt=12),
            Piece("planet", PROPS / "space_planet.png", "sky_anchor", 0.35, "center"),
            Piece("star", K("star"), "sky_fill", 0.12, "center", tilt=25),
            Piece("rocket", PROPS / "space_rocket.png", "landmark", 1.3),
            Piece("star_ground", K("star"), "cover", 0.28, tilt=10),
        ],
        "fill": 16,
    },
    "grandma": {
        "sky": ((140, 200, 236), (208, 234, 246)), "ground": ((120, 178, 84), (92, 150, 66)),
        "hills": [(118, 170, 110), (150, 196, 128)],
        "pieces": [
            Piece("sun", K("sun"), "sky_anchor", 0.55, "center", tilt=8),
            Piece("tree_far", K("tree"), "far", 1.5),
            Piece("house", K("house"), "landmark", 1.6, flip=False),
            Piece("tree", K("tree"), "landmark", 1.7),
            Piece("bush", K("bush"), "cover", 0.38),
            Piece("tulip", K("tulip"), "cover", 0.34),
            Piece("tulip_big", K("tulip"), "foreground", 0.55),
        ],
        "fill": 0,
    },
}
# actors and the ↩ ↪ buttons (MainActivity CenterStart / CenterEnd) — pieces keep out
HERO_X, FRIEND_X = 0.42, 0.66
KEEP_OUT = [(0, 0.38, 0.07, 0.62), (0.93, 0.38, 1, 0.62)]


def actor_box(x: float) -> tuple[float, float, float, float]:
    h = HERO_H * H
    return W * x - h * 0.33, feet_y(1) - h, W * x + h * 0.33, feet_y(1)


# ---------------------------------------------------------------- layout


def poisson(rng: random.Random, n: int, box, min_d: float, avoid) -> list[tuple[float, float]]:
    """Up to n points in box, at least min_d apart and outside the avoid boxes (dart throwing)."""
    x0, y0, x1, y1 = box
    pts: list[tuple[float, float]] = []
    for _ in range(n * 40):
        if len(pts) >= n:
            break
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        if any(a <= x <= c and b <= y <= d for a, b, c, d in avoid):
            continue
        if all(math.hypot(x - px, y - py) >= min_d for px, py in pts):
            pts.append((x, y))
    return pts


def layout(place: str, seed: int, actors: int = 2) -> list[Placed]:
    kit = KITS[place]
    rng = random.Random(seed)
    by = lambda role: [p for p in kit["pieces"] if p.role == role]  # noqa: E731
    out: list[Placed] = []
    heroes = [actor_box(HERO_X)] + ([actor_box(FRIEND_X)] if actors > 1 else [])
    hero_side = -1 if HERO_X < 0.5 else 1

    # sky anchor: the big one in an upper corner; a second, smaller one across
    anchors = by("sky_anchor")
    corner = rng.choice([0.16, 0.84])
    taken = []
    for i, p in enumerate(anchors):
        cx = W * (corner if i == 0 else 1 - corner + rng.uniform(-0.06, 0.06))
        cy = H * (0.2 if i == 0 else rng.uniform(0.14, 0.3))
        h = piece_h(p, 1)
        out.append(Placed(p, cx, cy, h, tilt=rng.uniform(-p.tilt, p.tilt)))
        taken.append((cx - h * 0.7, cy - h * 0.7, cx + h * 0.7, cy + h * 0.7))

    # sky fill: spread, never in a clump, not over an anchor or a head
    for p in by("sky_fill"):
        heads = [(a, b - 60, c, b + 80) for a, b, c, _ in heroes]
        for x, y in poisson(rng, kit["fill"], (W * 0.08, H * 0.05, W * 0.92, H * (HORIZON - 0.14)),
                            W * 0.09, taken + heads):
            out.append(Placed(p, x, y, piece_h(p, 1) * rng.uniform(0.6, 1.3), rng.random() < 0.5,
                              rng.uniform(-p.tilt, p.tilt)))

    # far band: small hazy copies along the horizon — this is what stops the middle looking empty
    for p in by("far"):
        x = rng.uniform(0, 0.08) * W
        while x < W:
            d = rng.uniform(0.0, 0.08)
            out.append(Placed(p, x, feet_y(d) - 4, piece_h(p, d) * rng.uniform(0.45, 0.65),
                              rng.random() < 0.5, fade=0.45))
            x += W * rng.uniform(0.08, 0.16)

    # landmarks: on the third away from the hero, a little behind
    lms = by("landmark")
    for i, p in enumerate(lms):
        third = 0.5 - hero_side * 0.28 + (i * 0.2 * -hero_side if i else 0)
        if i == 1:
            third = 0.5 + hero_side * 0.38       # second landmark balances on the hero's side
        d = rng.uniform(0.12, 0.3)
        out.append(Placed(p, W * (third + rng.uniform(-0.04, 0.04)), feet_y(d), piece_h(p, d),
                          p.flip and rng.random() < 0.5))

    # cover: clumps of 2–3, not an even carpet
    cover = by("cover")
    for _ in range(rng.randint(3, 4)) if cover else []:
        p0 = rng.choice(cover)
        cx, d0 = W * rng.uniform(0.08, 0.92), rng.uniform(0.3, 0.85)
        for k in range(rng.randint(2, 3)):
            p = p0 if rng.random() < 0.7 else rng.choice(cover)
            d = min(1, max(0.25, d0 + rng.uniform(-0.12, 0.12)))
            out.append(Placed(p, cx + rng.uniform(-1, 1) * W * 0.035 * (k + 1), feet_y(d),
                              piece_h(p, d) * rng.uniform(0.85, 1.15), rng.random() < 0.5))

    # foreground: one big piece cut by a bottom corner — the cheapest depth there is
    for p in by("foreground"):
        side = rng.choice([0.03, 0.97])
        out.append(Placed(p, W * side, H * 1.04, piece_h(p, 1.15), side > 0.5))
    return out


# ---------------------------------------------------------------- scoring


def score(place: str, items: list[Placed], aspects: dict[str, float], actors: int = 2) -> float:
    """Lower is better. Weights picked by eye on 10-03 — a prototype, not a measurement."""
    boxes = [(it, it.box(aspects[it.piece.name])) for it in items if it.fade == 0]
    ground = [(it, b) for it, b in boxes if it.piece.base == "feet"]
    area = lambda b: max(0, b[2] - b[0]) * max(0, b[3] - b[1])  # noqa: E731
    inter = lambda a, b: area((max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])))  # noqa: E731
    s = 0.0
    # overlap between ground pieces of different kinds (a clump of the same kind may touch)
    for i, (a, ba) in enumerate(ground):
        for b, bb in ground[i + 1:]:
            if a.piece.name != b.piece.name:
                s += 3 * inter(ba, bb) / max(1, min(area(ba), area(bb)))
    # keep-out: actors and the buttons
    keep = [actor_box(HERO_X)] + ([actor_box(FRIEND_X)] if actors > 1 else [])
    keep += [(a * W, b * H, c * W, d * H) for a, b, c, d in KEEP_OUT]
    for it, b in boxes:
        if it.piece.role == "foreground":
            continue
        for k in keep:
            s += 4 * inter(b, k) / max(1, area(b))
    # balance: area-weighted centre of the ground pieces near the middle
    tot = sum(area(b) for _, b in ground) or 1
    s += 4 * abs(sum(area(b) * ((b[0] + b[2]) / 2 / W - 0.5) for _, b in ground) / tot)
    # empty columns on the ground (six columns, actors count)
    cols = [0] * 6
    for _, b in ground:
        for c in range(6):
            if b[0] < (c + 1) * W / 6 and b[2] > c * W / 6:
                cols[c] = 1
    for k in keep[:actors]:
        cols[min(5, int((k[0] + k[2]) / 2 / W * 6))] = 1
    s += 0.6 * cols.count(0)
    return s


# ---------------------------------------------------------------- render


def felt(size, top, bot, seed) -> Image.Image:
    w, h = size
    rng = np.random.default_rng(seed)
    t = np.linspace(0, 1, h)[:, None, None]
    base = np.array(top) * (1 - t) + np.array(bot) * t
    blot = np.asarray(Image.fromarray(rng.normal(0, 6, (h // 3, w // 3)).astype(np.float32))
                      .resize((w, h), Image.BILINEAR))[..., None]
    return Image.fromarray(np.clip(base + blot + rng.normal(0, 4, (h, w, 1)), 0, 255)
                           .astype(np.uint8)).convert("RGBA")


def wave(seed: float, y0: float, amp: float, freq: float) -> list[tuple[float, float]]:
    return [(x, y0 + amp * math.sin(x / freq + seed) + amp * 0.4 * math.sin(x / (freq * 0.37) + seed * 2))
            for x in range(0, W + 1, 6)]


def stitched(c: Image.Image, poly, color, seed, stitch=(255, 250, 238, 150)) -> None:
    layer = felt((W, H), color, tuple(max(0, v - 18) for v in color), seed)
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon(poly + [(W, H), (0, H)], fill=255)
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).line(poly, fill=(0, 0, 0, 60), width=8)
    c.alpha_composite(sh.filter(ImageFilter.GaussianBlur(5)))
    layer.putalpha(m)
    c.alpha_composite(layer)
    d = ImageDraw.Draw(c)
    for i in range(0, len(poly) - 3, 4):
        d.line([(poly[i][0], poly[i][1] + 9), (poly[i + 2][0], poly[i + 2][1] + 9)], fill=stitch, width=2)


def load(p: Path) -> Image.Image:
    from collections import deque
    a = np.asarray(Image.open(p).convert("RGBA")).copy()
    h, w = a.shape[:2]
    rgb = a[..., :3].astype(int)
    grey = (a[..., 3] > 0) & (rgb.max(2) - rgb.min(2) < 22) & (rgb.min(2) > 110)
    seen = np.zeros((h, w), bool)
    q = deque((y, x) for y in range(h - 3, h) for x in range(w) if grey[y, x])
    for y, x in q:
        seen[y, x] = True
    while q:                                # the grey floor shadow SDXL draws regardless
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if h * 0.6 < ny < h and 0 <= nx < w and grey[ny, nx] and not seen[ny, nx]:
                seen[ny, nx] = True
                q.append((ny, nx))
    a[seen, 3] = 0
    im = Image.fromarray(a)
    return im.crop(im.getbbox())


def render(place: str, items: list[Placed], imgs: dict[str, Image.Image], actors: list[Image.Image],
           seed: int) -> Image.Image:
    kit = KITS[place]
    c = felt((W, H), *kit["sky"], seed=seed)

    def draw(it: Placed) -> None:
        im = imgs[it.piece.name]
        im = im.resize((max(1, int(it.h * im.width / im.height)), max(1, int(it.h))), Image.LANCZOS)
        if it.flip:
            im = im.transpose(Image.FLIP_LEFT_RIGHT)
        if it.tilt:
            im = im.rotate(it.tilt, expand=True, resample=Image.BICUBIC)
        if it.fade:
            rgb = ImageEnhance.Color(im).enhance(1 - it.fade * 0.6)
            haze = Image.new("RGBA", im.size, kit["sky"][1] + (0,))
            haze.putalpha(im.getchannel("A").point(lambda v: int(v * it.fade * 0.55)))
            rgb.alpha_composite(haze)
            im = rgb
        if it.piece.base == "feet" and not it.fade:
            sh = Image.new("RGBA", c.size, (0, 0, 0, 0))
            w = im.width * 0.7
            ImageDraw.Draw(sh).ellipse((it.x - w / 2, it.y - w * 0.07, it.x + w / 2, it.y + w * 0.07),
                                       fill=(20, 24, 30, 90))
            c.alpha_composite(sh.filter(ImageFilter.GaussianBlur(max(2, w * 0.05))))
        top = it.y - im.height if it.piece.base == "feet" else it.y - im.height / 2
        c.alpha_composite(im, (int(it.x - im.width / 2), int(top)))

    sky = [it for it in items if it.piece.base == "center"]
    for it in sorted(sky, key=lambda it: it.h):
        draw(it)
    hy = H * HORIZON
    stitched(c, wave(seed, hy - H * 0.13, 16, 120), kit["hills"][0], seed + 1)
    stitched(c, wave(seed + 2, hy - H * 0.07, 12, 90), kit["hills"][1], seed + 2)
    for it in [it for it in items if it.fade]:
        draw(it)
    stitched(c, wave(seed + 4, hy, 5, 140), kit["ground"][0], seed + 3)
    actor_items = [Placed(Piece(f"actor{i}", Path(), "actor", 1), W * x, feet_y(1) - 6, HERO_H * H)
                   for i, x in enumerate([HERO_X, FRIEND_X][:len(actors)])]
    for i, a in enumerate(actors):
        imgs[f"actor{i}"] = a
    ground = [it for it in items if it.piece.base == "feet" and not it.fade] + actor_items
    for it in sorted(ground, key=lambda it: it.y):
        draw(it)
    return c


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    hero = Image.open(ROOT / "image_out" / "felt_kit" / "_hero.png").convert("RGBA")
    hero = hero.crop(hero.getbbox())
    friend = hero.transpose(Image.FLIP_LEFT_RIGHT)
    font = ImageFont.truetype("malgun.ttf", 26)
    for place, kit in KITS.items():
        imgs = {p.name: load(p.path) for p in kit["pieces"]}
        aspects = {n: im.width / im.height for n, im in imgs.items()}
        cands = []
        for seed in range(20):
            items = layout(place, seed)
            cands.append((score(place, items, aspects), seed, items))
        cands.sort(key=lambda c: c[0])
        s, seed, items = cands[0]
        render(place, items, dict(imgs), [hero, friend], seed).convert("RGB").save(OUT / f"{place}_best.png")
        # best two and worst two side by side, with their scores
        pick = cands[:2] + cands[-2:]
        sheet = Image.new("RGB", (W, H + 40), "white")
        for i, (sc, sd, it) in enumerate(pick):
            im = render(place, it, dict(imgs), [hero, friend], sd).convert("RGB").resize((W // 2, H // 2))
            x, y = (i % 2) * W // 2, (i // 2) * (H // 2 + 20)
            sheet.paste(im, (x, y + 20))
            ImageDraw.Draw(sheet).text((x + 8, y - 4), f"{'best' if i < 2 else 'worst'} seed {sd} · score {sc:.2f}",
                                       fill="black", font=font)
        sheet.save(OUT / f"{place}_candidates.png")
        print(f"{place}: best seed {seed} score {s:.2f} · worst {cands[-1][0]:.2f}")


if __name__ == "__main__":
    main()
