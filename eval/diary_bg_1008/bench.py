"""#264 measure: /image kind=background mode=diary (colored pencil) vs mode=story (felt).

Times each call, records preset/reason (preset = scene LLM refused, ComfyUI failed, or the safety
check blocked it), saves every picture, and builds before/after book-page mockups with a
synthetic child drawing laid over white vs over the generated background.
"""
import base64
import io
import json
import statistics
import sys
import time
from pathlib import Path

import httpx
from PIL import Image, ImageDraw, ImageFont

BASE = sys.argv[1]
OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "diary").mkdir(exist_ok=True)
(OUT / "felt").mkdir(exist_ok=True)
(OUT / "page").mkdir(exist_ok=True)

PLACES = ["바닷가", "놀이터", "공원", "할머니 집", "동물원", "수영장", "유치원", "산", "캠핑장", "눈썰매장", "놀이공원", "마트"]
FELT = ["바닷가", "놀이터", "할머니 집"]          # the old (felt) look, for the before/after sheet
ROUNDS = 2                                       # each diary place twice: warm-up spread + seed spread


def call(place: str, mode: str) -> dict:
    t = time.monotonic()
    try:
        r = httpx.post(f"{BASE}/image", json={"kind": "background", "place": place, "mode": mode, "style": "felt"}, timeout=40)
        j = r.json()
    except Exception as e:  # noqa: BLE001
        return {"place": place, "mode": mode, "s": round(time.monotonic() - t, 2), "preset": True, "reason": f"call failed: {e}"}
    s = round(time.monotonic() - t, 2)
    return {"place": place, "mode": mode, "s": s, "preset": bool(j.get("preset")), "reason": j.get("reason"),
            "scene": j.get("scene"), "png": j.get("png_base64")}


def child_drawing(w: int, h: int) -> Image.Image:
    """A synthetic stand-in for a child's board: a person, a sun, a bucket — thick crayon-ish lines on transparent."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    lw = max(6, w // 120)
    cx, gy = int(w * 0.42), int(h * 0.82)
    d.ellipse([cx - 45, gy - 260, cx + 45, gy - 170], outline=(230, 120, 40, 255), width=lw)        # head
    d.line([cx, gy - 170, cx, gy - 70], fill=(40, 90, 220, 255), width=lw)                           # body
    d.line([cx - 70, gy - 140, cx + 70, gy - 140], fill=(40, 90, 220, 255), width=lw)                # arms
    d.line([cx, gy - 70, cx - 50, gy], fill=(40, 90, 220, 255), width=lw)
    d.line([cx, gy - 70, cx + 50, gy], fill=(40, 90, 220, 255), width=lw)
    d.ellipse([int(w * 0.78), int(h * 0.08), int(w * 0.9), int(h * 0.08) + int(w * 0.12)], outline=(250, 200, 0, 255), width=lw)  # sun
    bx = int(w * 0.62)
    d.polygon([(bx, gy - 90), (bx + 90, gy - 90), (bx + 75, gy), (bx + 15, gy)], outline=(220, 40, 60, 255), width=lw)  # bucket
    return im


def page(bg: Image.Image | None, label: str, size=(1232, 768)) -> Image.Image:
    w, h = size
    base = Image.new("RGBA", size, (255, 255, 255, 255))
    if bg is not None:
        base = bg.convert("RGBA").resize(size)
    base.alpha_composite(child_drawing(w, h))
    out = Image.new("RGB", (w, h + 60), "white")
    out.paste(base.convert("RGB"), (0, 0))
    d = ImageDraw.Draw(out)
    try:
        f = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 30)
    except OSError:
        f = ImageFont.load_default()
    d.text((16, h + 12), label, fill="black", font=f)
    return out


def main() -> None:
    rows = []
    # first call warms ComfyUI / the scene LLM — logged, but kept out of the timing stats
    warm = call("바닷가", "diary"); warm["warmup"] = True; rows.append(warm)
    print("warmup", warm["s"], warm["reason"])
    for r in range(ROUNDS):
        for p in PLACES:
            x = call(p, "diary"); x["round"] = r + 1; rows.append(x)
            print("diary", r + 1, p, x["s"], "preset" if x["preset"] else "ok", x["reason"])
    for p in FELT:
        x = call(p, "story"); rows.append(x)
        print("felt", p, x["s"], "preset" if x["preset"] else "ok", x["reason"])

    pics = {}
    for i, x in enumerate(rows):
        png = x.pop("png", None)
        if png and not x.get("warmup"):
            name = f"{x['mode'] if x['mode'] == 'story' else 'diary'}_{x['place'].replace(' ', '_')}_{x.get('round', 1)}.png"
            folder = OUT / ("felt" if x["mode"] == "story" else "diary")
            (folder / name).write_bytes(base64.b64decode(png))
            x["file"] = str((folder / name).relative_to(OUT))
            pics.setdefault((x["mode"], x["place"]), Image.open(io.BytesIO(base64.b64decode(png))))

    for p in FELT:
        sheet = [page(None, f"지금 (배경 안 그림): 흰 바탕 — {p}")]
        if ("story", p) in pics:
            sheet.append(page(pics[("story", p)], f"참고: 펠트(mode=story) — {p}"))
        if ("diary", p) in pics:
            sheet.append(page(pics[("diary", p)], f"#264: 색연필 배경 자동(mode=diary) — {p}"))
        W = sheet[0].width; H = sum(s.height for s in sheet) + 10 * (len(sheet) - 1)
        out = Image.new("RGB", (W, H), (200, 200, 200)); y = 0
        for s in sheet:
            out.paste(s, (0, y)); y += s.height + 10
        out.save(OUT / "page" / f"before_after_{p.replace(' ', '_')}.png")

    timed = [x for x in rows if x["mode"] == "diary" and not x.get("warmup")]
    ok = [x for x in timed if not x["preset"]]
    ts = sorted(x["s"] for x in ok)
    summary = {
        "diary_calls": len(timed), "diary_generated": len(ok), "diary_preset": len(timed) - len(ok),
        "preset_reasons": [f"{x['place']}: {x['reason']}" for x in timed if x["preset"]],
        "seconds_p50": statistics.median(ts) if ts else None,
        "seconds_p90": ts[min(len(ts) - 1, int(len(ts) * 0.9))] if ts else None,
        "seconds_max": max(ts) if ts else None,
        "warmup_s": warm["s"],
        "felt_s": [x["s"] for x in rows if x["mode"] == "story"],
    }
    (OUT / "results.json").write_text(json.dumps({"summary": summary, "rows": rows}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
