# -*- coding: utf-8 -*-
"""gen_faces.py 로 만든 표정 중 고른 것을 앱 얼굴 그림(otto_face_*.png, 512px)으로 넣는다 (09-29).

지금 얼굴(otto_face_talk)과 같은 틀 — 정사각 512px · 투명 바탕. 원형 틀 안에서 Crop 으로 그려진다.

    python tools/faces_place.py SRC_DIR happy=0 surprised=1 ...
"""
import os
import sys

from PIL import Image

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")

if __name__ == "__main__":
    src = sys.argv[1]
    for arg in sys.argv[2:]:
        name, k = arg.split("=")
        im = Image.open(os.path.join(src, f"{name}_{k}.png")).convert("RGBA")
        w, h = im.size
        side = min(w, h)
        im = im.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2)).resize((512, 512), Image.LANCZOS)
        dest = os.path.join(RES, f"otto_face_{name}.webp")
        im.save(dest, "WEBP", quality=90, method=6)
        print(name, os.path.getsize(dest) // 1024, "KB")
