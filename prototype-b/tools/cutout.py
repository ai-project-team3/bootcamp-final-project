# -*- coding: utf-8 -*-
"""초안 B - 인형 그림에서 주인공만 남기기 (BiRefNet 없이)

왜 필요한가
  SDXL은 "흰 배경에 하나만"이라고 해도 주변에 색종이 조각을 흩뿌린다.
  게다가 배경이 순백이 아니라 **회색 그라데이션**이라 밝기만으로는 못 자른다.
  (hero_a 네 귀퉁이 값이 204~234였다)

어떻게 자르나
  1. 배경은 **채도가 낮고 밝다**. 인형은 색이 있거나 어둡다.  →  bg = (max-min < 20) and (min > 188)
  2. 덩어리를 모두 찾아 **테두리에 덜 닿는 것 중 가장 큰 것**을 남긴다
     (그냥 최대 덩어리로 하면 인형이 올려진 색종이 카드를 골라 버린다)
  3. 덩어리 안쪽 구멍(안경알 같은 흰 부분)은 메운다
  4. 테두리를 한 겹 부드럽게 하고, 여백을 잘라 RGBA로 덮어쓴다
  5. 덩어리가 너무 작으면(3% 미만) 건드리지 않고 원본을 둔다 - 흰 구름처럼 배경과 같은 색인 경우

  원본은 `assets_raw/`에 남아 있다.

  python tools/cutout.py           (assets 전부)
  python tools/cutout.py hero_a    (하나만)
"""
import os, sys
import numpy as np
from PIL import Image
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "assets"))
RAW = os.path.abspath(os.path.join(HERE, "..", "assets_raw"))

SAT_MAX = 20     # 이보다 채도가 낮고
VAL_MIN = 188    # 이보다 밝으면 배경으로 본다
MIN_AREA = 0.03  # 덩어리가 이보다 작으면 자르지 않는다


def components_largest(mask):
    """덩어리를 모두 찾아 **테두리에 덜 닿는 것 중 가장 큰 것**을 고른다.

    그냥 '가장 큰 덩어리'로 하면 인형이 색종이 카드 위에 놓였을 때 카드를 고른다.
    잘라낼 주인공은 보통 화면 가장자리에 닿지 않는다. 그걸 신호로 쓴다.
    (세 변 이상 닿으면 배경판으로 본다. 발이 바닥에 닿는 정도는 허용)
    """
    small = mask[::2, ::2]
    h, w = small.shape
    seen = np.zeros((h, w), dtype=bool)
    cands = []
    for sy in range(h):
        row = small[sy]
        for sx in range(w):
            if not row[sx] or seen[sy, sx]:
                continue
            q = deque([(sy, sx)]); seen[sy, sx] = True; px = []
            top = bot = lef = rig = False
            while q:
                y, x = q.popleft(); px.append((y, x))
                if y == 0: top = True
                if y == h - 1: bot = True
                if x == 0: lef = True
                if x == w - 1: rig = True
                for dy in (-1, 0, 1):
                    ny = y + dy
                    if ny < 0 or ny >= h: continue
                    for dx in (-1, 0, 1):
                        nx = x + dx
                        if nx < 0 or nx >= w: continue
                        if small[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True; q.append((ny, nx))
            cands.append((sum((top, bot, lef, rig)), len(px), px))
    if not cands:
        return np.zeros_like(mask)
    overall = max(cands, key=lambda c: c[1])
    inner = [c for c in cands if c[0] <= 1]
    best = max(inner, key=lambda c: c[1]) if inner else overall
    # 안쪽 덩어리가 너무 초라하면 그냥 제일 큰 것을 쓴다
    if best[1] < overall[1] * 0.25:
        best = overall
    keep_s = np.zeros((h, w), dtype=bool)
    ys, xs = zip(*best[2])
    keep_s[np.array(ys), np.array(xs)] = True
    keep = np.repeat(np.repeat(keep_s, 2, axis=0), 2, axis=1)[:mask.shape[0], :mask.shape[1]]
    return keep


def fill_holes(blob):
    """테두리에서 못 닿는 빈 곳 = 덩어리 안쪽 구멍 → 메운다"""
    h, w = blob.shape
    outside = np.zeros((h, w), dtype=bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if not blob[y, x] and not outside[y, x]:
                outside[y, x] = True; q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if not blob[y, x] and not outside[y, x]:
                outside[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not blob[ny, nx] and not outside[ny, nx]:
                outside[ny, nx] = True; q.append((ny, nx))
    return blob | ~outside


def feather(blob):
    """3×3 평균으로 테두리 한 겹만 부드럽게"""
    f = blob.astype(np.float32)
    acc = np.zeros_like(f)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            acc += np.roll(np.roll(f, dy, axis=0), dx, axis=1)
    return np.clip(acc / 9.0, 0, 1)


def cut(name):
    src = os.path.join(RAW, name + ".png")
    if not os.path.exists(src):
        src = os.path.join(OUT, name + ".png")
    probe = Image.open(src)
    if probe.mode == "RGBA":
        print(f"  [건너뜀] {name} - 이미 잘린 그림이다 (원본은 assets_raw 에)")
        return
    im = probe.convert("RGB")
    a = np.asarray(im).astype(np.int16)
    mx, mn = a.max(axis=2), a.min(axis=2)
    bg = ((mx - mn) < SAT_MAX) & (mn > VAL_MIN)
    fg = ~bg

    blob = components_largest(fg)
    if blob.mean() < MIN_AREA:
        print(f"  [그대로 둠] {name} - 덩어리가 너무 작다 ({100*blob.mean():.1f}%)")
        return
    blob = fill_holes(blob)

    alpha = (feather(blob) * 255).astype(np.uint8)
    rgba = np.dstack([np.asarray(im), alpha])
    out = Image.fromarray(rgba, "RGBA")

    ys, xs = np.where(alpha > 10)
    m = 10
    out = out.crop((max(0, xs.min() - m), max(0, ys.min() - m),
                    min(out.width, xs.max() + m), min(out.height, ys.max() + m)))
    out.save(os.path.join(OUT, name + ".png"))
    print(f"  {name} → {out.width}x{out.height} (덩어리 {100*blob.mean():.0f}%)")


if __name__ == "__main__":
    only = set(sys.argv[1:])
    names = sorted(f[:-4] for f in os.listdir(RAW if os.path.isdir(RAW) else OUT)
                   if f.endswith(".png") and not f.startswith("bg_"))
    for n in names:
        if only and n not in only:
            continue
        cut(n)
    print("끝.")
