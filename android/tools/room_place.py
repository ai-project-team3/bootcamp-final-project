# -*- coding: utf-8 -*-
"""gen_room.py 로 만든 그림 중 고른 것을 앱에 넣는다 (09-29).

- 배경을 딴 물건(무대 등)은 **안쪽 구멍**을 원본으로 메운다 — BiRefNet 이 무대 안쪽 어두운 곳까지 배경으로 보고 뚫었다.
  테두리에서 이어진 투명한 곳만 진짜 바깥이고, 테두리와 이어지지 않은 투명한 곳은 물건 안쪽이다.
- 투명한 가장자리를 잘라 내고(여백이 있으면 방 좌표에 맞춘 자리보다 작아 보인다) 긴 변을 줄여 webp 로 저장한다.

    python tools/room_place.py SRC_DIR COMFY_OUTPUT_DIR
"""
import os
import sys
from collections import deque

from PIL import Image

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")

# 앱 이름 → (고른 파일, 원본 번호(구멍 메우기용, 없으면 None), 긴 변)
PICK = {
    "room_bg": ("room_bg_0.png", None, 1344),
    "room_window": ("room_window_1.png", "room_window_00002_.png", 640),
    "room_theater": ("room_theater_1.png", "room_theater_00002_.png", 760),
    "room_sofa": ("room_sofa_0.png", "room_sofa_00001_.png", 760),
    "room_shelf": ("room_shelf_0.png", "room_shelf_00001_.png", 760),
    "title_bg": ("title_bg_0.png", None, 1344),
    "night_bg": ("night_bg_0.png", None, 1344),
    "feat_talk": ("feat_talk_1.png", "feat_talk_00002_.png", 360),
    "feat_book": ("feat_book_0.png", "feat_book_00001_.png", 360),
    "feat_shelf": ("feat_shelf_0.png", "feat_shelf_00001_.png", 360),
}


def fill_holes(cut: Image.Image, raw: Image.Image) -> Image.Image:
    cut = cut.convert("RGBA")
    raw = raw.convert("RGB").resize(cut.size)
    w, h = cut.size
    a = cut.getchannel("A").load()
    outside = bytearray(w * h)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if a[x, y] < 128 and not outside[y * w + x]:
                outside[y * w + x] = 1; q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if a[x, y] < 128 and not outside[y * w + x]:
                outside[y * w + x] = 1; q.append((x, y))
    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not outside[ny * w + nx] and a[nx, ny] < 128:
                outside[ny * w + nx] = 1; q.append((nx, ny))
    px = cut.load()
    rp = raw.load()
    filled = 0
    for y in range(h):
        for x in range(w):
            if a[x, y] < 250 and not outside[y * w + x]:
                r, g, b = rp[x, y]
                px[x, y] = (r, g, b, 255)
                filled += 1
    print("  구멍 메움", filled, "px")
    return cut


def main(src, comfy):
    for name, (f, raw, side) in PICK.items():
        p = os.path.join(src, f)
        if not os.path.exists(p):
            print("없음", f); continue
        im = Image.open(p)
        if raw:
            im = fill_holes(im, Image.open(os.path.join(comfy, raw)))
            bbox = im.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
            if bbox:
                im = im.crop(bbox)
        im.thumbnail((side, side), Image.LANCZOS)
        dest = os.path.join(RES, name + ".webp")
        im.save(dest, "WEBP", quality=88, method=6)
        print(name, im.size, os.path.getsize(dest) // 1024, "KB")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
