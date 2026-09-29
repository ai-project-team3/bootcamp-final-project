# -*- coding: utf-8 -*-
"""그림 줄이기 — 만든 그림을 앱에 넣기 전에 한 번 돌린다.

ComfyUI는 1024×1024(잘라낸 것) · 1280~1344×768(배경)로 만든다. 그대로 넣으면 한 장에 1MB씩이라
`res/drawable` 이 금세 100MB를 넘는다 (v0.6에서 90MB → 18MB로 줄인 적이 있다).
화면에서 쓰는 크기는 그보다 훨씬 작으므로 **잘라낸 것 640 · 배경 1344×768** 로 맞춘다.

이미 그 크기인 파일은 건드리지 않는다. 되돌릴 수 없으니 ComfyUI 출력 원본은 `ComfyUI/output/puppet/` 에 남아 있다.

쓰는 법
  python shrink_assets.py            # 큰 것만
  python shrink_assets.py --dry      # 무엇이 줄어드는지만 본다
"""
import os, sys
from PIL import Image

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
CUT = 640           # 오려낸 그림 (주인공 · 친구 · 소품 · 아이콘)
BG = (1344, 768)    # 배경

dry = "--dry" in sys.argv
saved = 0
for name in sorted(os.listdir(RES)):
    if not name.endswith(".png"):
        continue
    path = os.path.join(RES, name)
    before = os.path.getsize(path)
    im = Image.open(path)
    w, h = im.size
    if name.startswith("bg_"):
        if (w, h) == BG:
            continue
        out = im.convert("RGB").resize(BG, Image.LANCZOS)
    else:
        if max(w, h) <= CUT:
            continue
        k = CUT / max(w, h)
        out = im.resize((round(w * k), round(h * k)), Image.LANCZOS)
    if dry:
        print(f"{name}: {w}x{h} -> {out.size}  ({before//1024}KB)")
        continue
    out.save(path, optimize=True)
    after = os.path.getsize(path)
    saved += before - after
    print(f"{name}: {w}x{h} -> {out.size}  {before//1024}KB -> {after//1024}KB", flush=True)

if not dry:
    print(f"\n== 줄인 용량: {saved // (1024 * 1024)}MB ==")
