"""Wool-felt props on wool-felt backgrounds — a first look at prop placement (10-03).

The lead liked the wool-felt background draft (eval/bench_felt_style.py). This makes a few
place-fitting props in the same style (SDXL + Lightning on PC2 — krea2 is not installed there),
cuts them out with the server's own cut-out, and places them with assets/tools/place_props.py
(horizon → flat, ground-coloured cells → one depth band each → smaller when far).

  py eval/bench_felt_props.py   → eval/image_out/felt_props/{place}_placed.png · {place}_{prop}.png
"""
import asyncio
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.config import settings  # noqa: E402
from app.image import character, comfy  # noqa: E402

settings.comfy_url = "http://192.168.0.52:8188"

WOOL = ("soft wool felt and fabric craft 3D children's picture book illustration, visible felt fibers and stitched "
        "edges, cute rounded shapes, warm pastel colors (cream, mustard yellow, coral, teal, sky blue), gentle soft "
        "lighting, cozy, no text, no letters, high quality")
# cfg 1.0 (Lightning) barely reads the negative — 10-03: "isolated on white" after the style drew a picture
# frame round every object; "die-cut sticker" drew sheets of cards. The character prompt's order works
# (backend CHAR_STYLE): the subject first, one object, then style, then the white page last.
NEG = comfy.NEG.replace("felt, fabric, plush, clay, ", "") + ", background scenery, multiple objects, frame, border"
PROPS = {
    "sea": {"shell": "a pink scallop shell", "starfish": "an orange starfish", "seaweed": "a tall green seaweed plant"},
    "amusement": {"balloons": "a bunch of three colorful balloons", "cotton": "a pink cotton candy on a stick",
                  "drum": "a small toy drum"},
    "grandma": {"can": "a little watering can", "pot": "a flower pot with red tulips", "basket": "a basket of red apples"},
}
SRC = Path(__file__).parent / "image_out" / "felt_style"
OUT = Path(__file__).parent / "image_out" / "felt_props"
OUT.mkdir(parents=True, exist_ok=True)


def cut_out_prop(png: bytes) -> bytes:
    """Props are made ahead of time, so the cut can be looser than the live one: 10-03 SDXL put each
    object on a coloured studio backdrop (beige · teal · pink), which the live rule (pale grey only)
    does not take. Background = reached from the border through colour close to that row's edge colour,
    whatever the colour. Largest blob kept, holes filled. Raises CutoutError."""
    import io
    from collections import deque
    import numpy as np
    from PIL import Image
    im = Image.open(io.BytesIO(png)).convert("RGB")
    small = np.asarray(im.resize((256, 256), Image.BILINEAR)).astype(int)
    h, w = small.shape[:2]
    edge = (small[:, :1, :] + small[:, -1:, :]) // 2
    near = np.abs(small - edge).max(axis=2) <= 30
    bg = np.zeros((h, w), bool); q = deque()
    for y in range(h):
        for x in (0, w - 1):
            if near[y, x]: bg[y, x] = True; q.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if near[y, x] and not bg[y, x]: bg[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and near[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True; q.append((ny, nx))
    fg = ~bg
    comps = character._components(fg)
    if not comps:
        raise character.CutoutError("nothing drawn")
    sides, size, blob = max(comps, key=lambda c: c[1])
    if size < 0.03 * fg.size or sides >= 3:
        raise character.CutoutError(f"no single object (size {size / fg.size:.2f}, sides {sides})")
    blob = character._fill_holes(blob)
    alpha = Image.fromarray((blob * 255).astype(np.uint8)).resize(im.size, Image.BILINEAR)
    rgba = im.copy(); rgba.putalpha(alpha)
    rgba = rgba.crop(alpha.point(lambda v: 255 if v > 127 else 0).getbbox())
    out = io.BytesIO(); rgba.save(out, "PNG")
    return out.getvalue()


async def make(thing: str, seed: int) -> bytes:
    wf = comfy.workflow("", seed)
    wf["2"]["inputs"]["text"] = (f"a cute {thing}, front view, full view, one single object, {WOOL}, "
                                 "isolated on plain pure white background, no shadow, no text")
    wf["3"]["inputs"]["text"] = NEG
    wf["4"]["inputs"].update({"width": 1024, "height": 1024})
    return await comfy.run(wf)


async def main() -> None:
    for place, props in PROPS.items():
        paths = []
        for i, (name, thing) in enumerate(props.items()):
            t = time.monotonic()
            cut = None
            for seed in (3000 + i, 4000 + i):              # two tries — keep the first that cuts out
                raw = await make(thing, seed)
                (OUT / f"{place}_{name}_{seed}_raw.png").write_bytes(raw)
                try:
                    cut = cut_out_prop(raw)
                    break
                except character.CutoutError as e:
                    print(f"  {place}/{name} seed {seed}: cut-out failed ({e})")
            if cut is None:
                continue
            p = OUT / f"{place}_{name}.png"
            p.write_bytes(cut)
            paths.append(str(p))
            print(f"  {place}/{name}: {time.monotonic() - t:.1f}s")
        bg = SRC / f"{place}_felt_wool.png"
        r = subprocess.run([sys.executable, str(ROOT / "assets/tools/place_props.py"), str(bg), *paths,
                            "-o", str(OUT / f"{place}_placed.png")], capture_output=True, text=True, encoding="utf-8")
        print(r.stdout.strip() or r.stderr.strip()[-400:])


if __name__ == "__main__":
    asyncio.run(main())
