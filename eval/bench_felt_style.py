"""Can the live background (SDXL + Lightning 8-step, ~4 s) look like the app's felt art? (10-03)

The app's buttons, room and bundled pictures are 치영's felt style (krea2 turbo, ~50 s a picture,
android/tools/otto_art.py STYLES["felt"]); the server's live backgrounds are cut paper
(backend/app/image/comfy.py BG_STYLE). Same model, same seed, same scene — only the style words
change — so the picture difference is the style, and the time is the model's.

  py eval/bench_felt_style.py [--comfy http://192.168.0.52:8188]   → eval/image_out/felt_style/*.png

Uses PC2's ComfyUI (the team server's). Nothing here goes to a child.
"""
import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402
from app.image import comfy  # noqa: E402

if "--comfy" in sys.argv:
    settings.comfy_url = sys.argv[sys.argv.index("--comfy") + 1]
elif "127.0.0.1" in settings.comfy_url:
    settings.comfy_url = "http://192.168.0.52:8188"

SCENES = {
    "sea": "an underwater world with colorful coral, seaweed and bubbles",
    "amusement": "a cheerful amusement park with a carousel, a ferris wheel and balloons",
    "grandma": "a cozy countryside house of a grandmother with a garden and a vegetable patch",
}
# app felt — android/tools/otto_art.py STYLES["felt"] with its background head (BG_HEAD)
FELT = (", wide landscape, no characters, no people, no animals, soft felt fabric and paper-craft 3D children's "
        "picture book illustration, cute, rounded shapes, warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
FELT_WOOL = (", wide landscape, no characters, no people, no animals, soft wool felt and fabric craft 3D children's picture "
             "book illustration, visible felt fibers and stitched edges, cute rounded shapes, warm pastel colors "
             "(cream, mustard yellow, coral, teal, sky blue), gentle soft lighting, cozy, no text, no letters, high quality")
NEG_NO_FELT = comfy.NEG.replace("felt, fabric, plush, clay, ", "")
STYLES = {
    "now_cutpaper": (comfy.BG_STYLE, comfy.NEG),
    "felt": (FELT, NEG_NO_FELT),
    "felt_wool": (FELT_WOOL, NEG_NO_FELT),
}
OUT = Path(__file__).parent / "image_out" / "felt_style"
OUT.mkdir(parents=True, exist_ok=True)


async def main() -> None:
    print(f"ComfyUI {settings.comfy_url}")
    times: dict[str, list[float]] = {k: [] for k in STYLES}
    for place, scene in SCENES.items():
        for style, (pos, neg) in STYLES.items():
            wf = comfy.workflow(scene, 20261003)
            wf["2"]["inputs"]["text"] = scene + pos
            wf["3"]["inputs"]["text"] = neg
            t = time.monotonic()
            png = await comfy.run(wf)
            dt = time.monotonic() - t
            times[style].append(dt)
            (OUT / f"{place}_{style}.png").write_bytes(png)
            print(f"  {place:<10}{style:<14}{dt:5.1f}s")
    for style, xs in times.items():
        print(f"{style:<14} mean {sum(xs) / len(xs):.1f}s")


if __name__ == "__main__":
    asyncio.run(main())
