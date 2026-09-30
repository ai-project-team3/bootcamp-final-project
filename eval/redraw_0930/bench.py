"""/image redraw (#32): denoise 0.6~0.9 on three script-drawn pieces. Needs ComfyUI with comfy_nodes/otto_memory.py.
Output: ./redraw/*.png + sheet.png next to this file (results.md 09-30)."""
import asyncio
import base64
import io
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "backend"))
from app.image import character, comfy  # noqa: E402

OUT = Path(__file__).parent / "redraw"
OUT.mkdir(exist_ok=True)


def png(im):
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()


def house():
    im = Image.new("RGBA", (300, 260), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle((60, 110, 240, 240), outline=(200, 40, 40, 255), width=8)
    d.polygon([(40, 115), (150, 20), (260, 115)], outline=(40, 40, 200, 255), width=8)
    d.rectangle((130, 170, 170, 240), outline=(120, 70, 20, 255), width=6)
    return png(im), "small red house with a blue roof"


def sun():
    im = Image.new("RGBA", (300, 300), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse((90, 90, 210, 210), fill=(250, 200, 30, 255))
    for i in range(8):
        import math
        a = i * math.pi / 4
        d.line((150 + 75 * math.cos(a), 150 + 75 * math.sin(a), 150 + 130 * math.cos(a), 150 + 130 * math.sin(a)),
               fill=(250, 160, 20, 255), width=8)
    return png(im), "smiling yellow sun"


def cat():
    im = Image.new("RGBA", (320, 260), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse((60, 100, 260, 230), outline=(30, 30, 30, 255), width=7)       # body
    d.ellipse((190, 40, 290, 130), outline=(30, 30, 30, 255), width=7)       # head
    d.polygon([(200, 60), (210, 20), (230, 50)], outline=(30, 30, 30, 255), width=6)
    d.polygon([(250, 50), (270, 18), (280, 60)], outline=(30, 30, 30, 255), width=6)
    d.line((60, 160, 20, 100), fill=(30, 30, 30, 255), width=7)              # tail
    return png(im), "orange cat"


async def main():
    rows = []
    for make in (house, sun, cat):
        drawing, subject = make()
        prepared = character.prepare_drawing(drawing)
        b64 = base64.b64encode(prepared).decode()
        row = [Image.open(io.BytesIO(prepared)).convert("RGB").resize((256, 256))]
        for dn in (0.6, 0.7, 0.8, 0.9):
            t = time.monotonic()
            raw = await comfy.run(comfy.redraw_workflow(subject, 7, b64, dn))
            dt = time.monotonic() - t
            try:
                cut = character.cut_and_fit(raw, True)
                im = Image.open(io.BytesIO(cut)).convert("RGBA")
                bg = Image.new("RGBA", im.size, (200, 230, 200, 255)); bg.alpha_composite(im)
                shown = bg.convert("RGB").resize((256, 256))
                note = "ok"
            except character.CutoutError as e:
                shown = Image.open(io.BytesIO(raw)).convert("RGB").resize((256, 256)); note = f"cut: {e}"
            (OUT / f"{make.__name__}_{dn}.png").write_bytes(raw)
            print(f"{make.__name__} denoise {dn}: {dt:.2f}s {note}")
            row.append(shown)
        rows.append(row)
    sheet = Image.new("RGB", (256 * 5, 256 * len(rows)), "white")
    for y, row in enumerate(rows):
        for x, im in enumerate(row):
            sheet.paste(im, (x * 256, y * 256))
    sheet.save(OUT / "sheet.png")


asyncio.run(main())
