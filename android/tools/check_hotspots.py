# -*- coding: utf-8 -*-
"""배경 속 누를 자리(HOTSPOTS) 좌표를 그림 위에 그려 눈으로 대조한다.

`Model.kt` 의 좌표는 손으로 찍는다. 앱을 띄우지 않고도 **자리가 맞는지** 보려면
그림 위에 그 원을 그려 보면 된다. 에뮬레이터를 켜지 않아도 되고 더 정확하다 (9/21).

좌표 규약은 `Hotspot` 주석과 같다 — 배경 그림(1344×768) 기준 비율 · r은 **그림 폭** 기준 반지름.

쓰는 법
  python check_hotspots.py              # 전부
  python check_hotspots.py bg_pool      # 하나만
"""
import io, os, re, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
MODEL = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "example",
                     "finalproject_demo", "demo", "Model.kt")
OUT = os.path.join(HERE, "..", "build", "hotspot_check")
os.makedirs(OUT, exist_ok=True)

src = io.open(MODEL, encoding="utf-8").read()
block = src[src.index("val HOTSPOTS"):src.index("/** 장소 한 곳 —")]

spots = {}
for m in re.finditer(r'"(bg_\w+)" to listOf\((.*?)\n    \),', block, re.S):
    bg, body = m.group(1), m.group(2)
    spots[bg] = [(k, n, float(cx), float(cy), float(r)) for k, n, cx, cy, r in
                 re.findall(r'h\("(\w+)", "([^"]+)", ([\d.]+)f, ([\d.]+)f, ([\d.]+)f,', body)]

only = set(sys.argv[1:])
for bg, items in sorted(spots.items()):
    if only and bg not in only:
        continue
    path = os.path.join(RES, bg + ".png")
    if not os.path.exists(path):
        print(f"[{bg}] 그림 없음 — 건너뜀")
        continue
    im = Image.open(path).convert("RGB")
    W, H = im.size
    d = ImageDraw.Draw(im, "RGBA")
    for key, name, cx, cy, r in items:
        # 앱과 같은 규약: 중심은 폭·높이 비율, 반지름은 **폭** 기준
        x, y, rr = cx * W, cy * H, r * W
        d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=(255, 80, 80, 255), width=5)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(255, 80, 80, 255))
        d.text((x - rr, y - rr - 18), name, fill=(255, 255, 255, 255))
    dest = os.path.join(OUT, bg + ".png")
    im.save(dest)
    print(f"[{bg}] {len(items)}자리 → {dest}")
