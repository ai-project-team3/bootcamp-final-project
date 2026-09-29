# -*- coding: utf-8 -*-
"""머리 조각 안쪽의 **얼굴판을 비운다**.

`gen_hero_parts.py` 가 뽑은 머리(가발)는 안쪽에 살색 얼굴이 같이 딸려 온다.
그대로 몸 위에 얹으면 **얼굴을 덮어 버린다.** 가운데에서 시작해 바깥으로 번져 나가며
"머리카락이 아닌 밝은 색" 을 투명하게 만든다 — 머리카락(어두운 갈색)에서 멈춘다.

되돌릴 수 있게 원본은 `*_raw.png` 로 남긴다.

쓰는 법
  python cut_hair_face.py                 # hair_short / hair_long / hair_tied
  python cut_hair_face.py hair_long
"""
import os, sys
from collections import deque
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")

# 머리카락은 어둡다. 이보다 밝으면 "얼굴판" 으로 본다
HAIR_MAX = 120          # 0~255 밝기


def brightness(p):
    return (p[0] * 299 + p[1] * 587 + p[2] * 114) // 1000


def cut(name):
    path = os.path.join(RES, name + ".png")
    raw = os.path.join(RES, name + "_raw.png")
    if not os.path.exists(path):
        print(f"[{name}] 없음"); return
    if not os.path.exists(raw):                      # 원본을 한 번만 남긴다
        Image.open(path).save(raw)
    im = Image.open(raw).convert("RGBA")
    W, H = im.size
    px = im.load()

    # 가운데(얼굴이 있을 자리)에서 번져 나간다
    starts = [(W // 2, int(H * 0.45)), (W // 2, int(H * 0.55)), (W // 2, int(H * 0.5))]
    seen = bytearray(W * H)
    q = deque()
    for s in starts:
        x, y = s
        if not seen[y * W + x]:
            seen[y * W + x] = 1
            q.append(s)
    n = 0
    while q:
        x, y = q.popleft()
        r, g, b, a = px[x, y]
        if a > 0 and brightness((r, g, b)) <= HAIR_MAX:
            continue                                  # 머리카락에 닿으면 멈춘다
        px[x, y] = (r, g, b, 0)
        n += 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < W and 0 <= ny < H and not seen[ny * W + nx]:
                seen[ny * W + nx] = 1
                q.append((nx, ny))
    im.save(path)
    print(f"[{name}] 얼굴판 {n * 100 // (W * H)}% 비움 -> {path}")


for name in (sys.argv[1:] or ["hair_short", "hair_long", "hair_tied"]):
    cut(name)
