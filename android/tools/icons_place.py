# -*- coding: utf-8 -*-
"""gen_room.py 로 만든 아이콘 중 고른 것을 앱에 넣는다 (09-29 · 방 이름표 · 부모 영역).

투명 가장자리를 잘라 256px webp 로 저장한다.

    python tools/icons_place.py SRC_DIR COMFY_OUTPUT_DIR icon_diary=0 pi_lock=1 ...
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from room_place import RES  # noqa: E402

if __name__ == "__main__":
    src, comfy = sys.argv[1], sys.argv[2]
    for arg in sys.argv[3:]:
        name, k = arg.split("=")
        im = Image.open(os.path.join(src, f"{name}_{k}.png"))
        # 아이콘은 구멍을 메우지 않는다 — 자물쇠 고리 · 메달 끈 안쪽처럼 원래 비어 있는 곳이 흰색으로 막혔다
        im = im.convert("RGBA")
        bbox = im.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
        if bbox:
            im = im.crop(bbox)
        im.thumbnail((256, 256), Image.LANCZOS)
        dest = os.path.join(RES, name + ".webp")
        im.save(dest, "WEBP", quality=90, method=6)
        print(name, im.size, os.path.getsize(dest) // 1024, "KB")
