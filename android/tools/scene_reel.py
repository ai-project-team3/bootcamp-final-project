"""Join the frames of `SceneReelTest` into a GIF — the living background, to look at without a phone.

    OTTO_SCENE_REEL=/tmp/reel OTTO_SCENE_REEL_FPS=15 ./gradlew testDebugUnitTest --tests '*SceneReelTest'
    python tools/scene_reel.py /tmp/reel park.gif --fps 15 --width 960

Needs Pillow. The GIF is for looking, never a reference image — do not commit it.
"""
import argparse
import glob
import os

from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("frames", help="folder with frame_0000.png …")
    ap.add_argument("out", help="the GIF to write")
    ap.add_argument("--fps", type=int, default=12)
    ap.add_argument("--width", type=int, default=960)
    a = ap.parse_args()

    paths = sorted(glob.glob(os.path.join(a.frames, "frame_*.png")))
    if not paths:
        raise SystemExit(f"no frames in {a.frames}")
    frames = []
    for p in paths:
        im = Image.open(p).convert("RGB")
        h = round(im.height * a.width / im.width)
        frames.append(im.resize((a.width, h), Image.LANCZOS))
    # one palette for the whole reel, from the first frame — per-frame palettes make felt colours shimmer
    first = frames[0].quantize(colors=255, method=Image.MEDIANCUT, dither=Image.NONE)
    out = [first] + [f.quantize(palette=first, dither=Image.NONE) for f in frames[1:]]
    out[0].save(a.out, save_all=True, append_images=out[1:], duration=round(1000 / a.fps), loop=0, optimize=False)
    print(f"{len(out)} frames → {a.out} ({os.path.getsize(a.out) // 1024} KB)")


if __name__ == "__main__":
    main()
