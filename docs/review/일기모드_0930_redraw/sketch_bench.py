"""Diary redraw style bench — same SDXL + Lightning, only prompt/denoise differ. Run from backend/ with its venv."""
import asyncio
import base64
import io
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, ".")
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from app.image import character, comfy  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)

SKETCH_A = (", child's crayon drawing in a picture diary, simple hand-drawn crayon lines, soft crayon coloring, "
            "cute and simple, isolated on plain pure white paper background, no shadow, no text")
SKETCH_B = (", colored pencil sketch, loose hand-drawn pencil outlines, light colored pencil shading, "
            "gentle storybook sketch, isolated on plain pure white paper background, no shadow, no text")
SKETCH_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, cut paper, collage, felt, "
              "blurry, ugly, scary, dark, horror, background scenery, frame, border, card, colored background, "
              "multiple subjects, nudity, blood, weapon, gore")

VARIANTS = [
    ("지금(종이 0.9)", comfy.CHAR_STYLE, comfy.CHAR_NEG, 0.9),
    ("크레용 0.9", SKETCH_A, SKETCH_NEG, 0.9),
    ("크레용 0.75", SKETCH_A, SKETCH_NEG, 0.75),
    ("크레용 0.6", SKETCH_A, SKETCH_NEG, 0.6),
    ("색연필 0.75", SKETCH_B, SKETCH_NEG, 0.75),
]


def child_drawing(kind: str) -> bytes:
    im = Image.new("RGBA", (400, 400), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W = 10
    if kind == "house":
        d.rectangle([90, 190, 310, 350], outline=(63, 123, 217, 255), width=W)
        d.line([70, 200, 200, 80, 330, 200, 70, 200], fill=(232, 96, 76, 255), width=W)
        d.rectangle([175, 270, 225, 350], outline=(138, 90, 60, 255), width=W)
        d.rectangle([115, 220, 155, 255], outline=(243, 195, 60, 255), width=W - 3)
    elif kind == "sun":
        d.ellipse([130, 130, 270, 270], outline=(243, 195, 60, 255), width=W)
        for a in range(0, 360, 45):
            import math
            r1, r2 = 95, 150
            c = math.cos(math.radians(a)); s = math.sin(math.radians(a))
            d.line([200 + r1 * c, 200 + r1 * s, 200 + r2 * c, 200 + r2 * s], fill=(240, 138, 60, 255), width=W - 2)
        d.arc([165, 185, 235, 240], 20, 160, fill=(232, 96, 76, 255), width=W - 4)
    elif kind == "mom":
        d.ellipse([155, 50, 245, 140], outline=(58, 42, 30, 255), width=W - 2)
        d.line([160, 70, 140, 190], fill=(138, 90, 60, 255), width=W - 2)
        d.line([240, 70, 260, 190], fill=(138, 90, 60, 255), width=W - 2)
        d.polygon([(200, 140), (130, 300), (270, 300)], outline=(217, 107, 168, 255), width=W)
        d.line([160, 190, 100, 250], fill=(58, 42, 30, 255), width=W - 3)
        d.line([240, 190, 300, 250], fill=(58, 42, 30, 255), width=W - 3)
        d.line([175, 300, 170, 370], fill=(58, 42, 30, 255), width=W - 3)
        d.line([225, 300, 230, 370], fill=(58, 42, 30, 255), width=W - 3)
    elif kind == "dog":
        d.ellipse([110, 180, 290, 280], outline=(138, 90, 60, 255), width=W)
        d.ellipse([250, 120, 340, 200], outline=(138, 90, 60, 255), width=W)
        d.line([265, 125, 250, 90], fill=(138, 90, 60, 255), width=W - 2)
        for x in (135, 170, 230, 265):
            d.line([x, 270, x, 340], fill=(138, 90, 60, 255), width=W - 2)
        d.line([115, 215, 70, 170], fill=(138, 90, 60, 255), width=W - 2)
    buf = io.BytesIO(); im.save(buf, "PNG")
    return buf.getvalue()


SUBJECTS = {"house": "cozy little house with a red roof", "sun": "bright smiling sun",
            "mom": "kind mother with long hair in a pink dress", "dog": "cute brown puppy"}


async def main() -> None:
    results = {}
    for kind, subject in SUBJECTS.items():
        drawing = character.prepare_drawing(child_drawing(kind))
        b64 = base64.b64encode(drawing).decode()
        seed = 1234 + len(kind)
        for name, style, neg, dn in VARIANTS:
            wf = comfy.redraw_workflow(subject, seed, b64, denoise=dn)
            wf["2"]["inputs"]["text"] = f"a cute {subject}, full view{style}"
            wf["3"]["inputs"]["text"] = neg
            t = time.monotonic()
            try:
                raw = await comfy.run(wf)
                png = character.cut_and_fit(raw, True)
                ok = "ok"
            except Exception as e:  # noqa: BLE001
                png, ok = None, f"{type(e).__name__}: {e}"
            sec = round(time.monotonic() - t, 1)
            results[f"{kind}|{name}"] = {"sec": sec, "ok": ok}
            if png:
                (OUT / f"{kind}_{VARIANTS.index((name, style, neg, dn))}.png").write_bytes(png)
            print(kind, name, sec, ok, flush=True)
        (OUT / f"{kind}_in.png").write_bytes(drawing)
    (OUT / "times.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")


asyncio.run(main())
