# -*- coding: utf-8 -*-
"""생성된 주인공 그림(hero_*.png, 배경 제거됨)에서 눈(동자) 위치를 찾아
app/src/main/java/.../ui/HeroEyes.kt 를 만든다. 눈 스티커(반달 · 별)를 얹을 자리.

방법 (256px로 줄여서):
 1. 살색 픽셀로 얼굴 덩어리(위쪽 절반에서 가장 큰 살색 성분)를 찾고 그 상자를 얼굴로 본다.
 2. 얼굴 상자 안쪽(가로 15~85% · 세로 28~66%)에서 어두운 픽셀을 연결 성분으로 묶는다
    — 앞머리(위) · 입(아래) · 옆머리(바깥)는 이 범위 밖이라 걸리지 않는다.
 3. 같은 높이에 나란한 두 덩어리를 눈으로 본다. 실패하면 기본값.
"""
import os, sys, glob
from collections import deque
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
OUT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "example", "finalproject_demo", "ui", "HeroEyes.kt")
N = 256


def blobs(mask):
    h = len(mask); w = len(mask[0])
    seen = [[False] * w for _ in range(h)]
    out = []
    for y in range(h):
        for x in range(w):
            if mask[y][x] and not seen[y][x]:
                q = deque([(x, y)]); seen[y][x] = True; pts = []
                while q:
                    cx, cy = q.popleft(); pts.append((cx, cy))
                    for nx, ny in ((cx+1, cy), (cx-1, cy), (cx, cy+1), (cx, cy-1)):
                        if 0 <= nx < w and 0 <= ny < h and mask[ny][nx] and not seen[ny][nx]:
                            seen[ny][nx] = True; q.append((nx, ny))
                out.append(pts)
    return out


def skin(r, g, b):
    return r > 185 and g > 130 and b > 105 and r - b > 30 and r >= g >= b - 5


rows = []
for path in sorted(glob.glob(os.path.join(SRC, "hero_*.png"))):
    name = os.path.splitext(os.path.basename(path))[0]
    im = Image.open(path).convert("RGBA").resize((N, N), Image.LANCZOS)
    px = im.load()
    # 1. 얼굴 = 위쪽 60%에서 가장 큰 살색 덩어리
    sm = [[False] * N for _ in range(N)]
    for y in range(0, int(N * 0.6)):
        for x in range(N):
            r, g, b, a = px[x, y]
            if a > 200 and skin(r, g, b):
                sm[y][x] = True
    sb = blobs(sm)
    if not sb:
        print(f"{name}: 얼굴을 못 찾음 → 기본값"); continue
    face = max(sb, key=len)
    fx0 = min(p[0] for p in face); fx1 = max(p[0] for p in face)
    fy0 = min(p[1] for p in face); fy1 = max(p[1] for p in face)
    fw = fx1 - fx0; fh = fy1 - fy0
    # 2. 얼굴 안쪽에서 어두운 덩어리
    dm = [[False] * N for _ in range(N)]
    for y in range(int(fy0 + fh * 0.28), int(fy0 + fh * 0.66)):
        for x in range(int(fx0 + fw * 0.15), int(fx0 + fw * 0.85)):
            r, g, b, a = px[x, y]
            if a > 200 and r + g + b < 260:
                dm[y][x] = True
    bs = [b for b in blobs(dm) if 4 <= len(b) <= 300]
    best = None
    for i in range(len(bs)):
        for j in range(i + 1, len(bs)):
            a, b = bs[i], bs[j]
            ax = sum(p[0] for p in a) / len(a); ay = sum(p[1] for p in a) / len(a)
            bx = sum(p[0] for p in b) / len(b); by = sum(p[1] for p in b) / len(b)
            dx = abs(ax - bx); dy = abs(ay - by)
            ratio = min(len(a), len(b)) / max(len(a), len(b))
            if fw * 0.2 < dx < fw * 0.7 and dy < fh * 0.08 and ratio > 0.35:
                score = (len(a) + len(b)) * ratio - dy * 4
                if best is None or score > best[0]:
                    best = (score, (ax, ay), (bx, by), a, b)
    if best is None:
        print(f"{name}: 눈을 못 찾음 (얼굴 {fx0}-{fx1},{fy0}-{fy1}) → 기본값"); continue
    _, (ax, ay), (bx, by), a, b = best
    if ax > bx:
        (ax, ay, a), (bx, by, b) = (bx, by, b), (ax, ay, a)
    rad = (max(max(p[0] for p in a) - min(p[0] for p in a), 4) / 2 + 1.5) / N
    rows.append(f'    "{name}" to Eyes({ax / N:.4f}f, {ay / N:.4f}f, {bx / N:.4f}f, {by / N:.4f}f, {rad:.4f}f),')
    print(f"{name}: L({ax/N:.3f},{ay/N:.3f}) R({bx/N:.3f},{by/N:.3f}) r={rad:.3f}")

kt = "package com.example.finalproject_demo.ui\n\n/** 생성된 주인공 그림별 눈 위치 — tools/find_eyes.py가 채운다. 비어 있으면 DEFAULT_EYES */\nval HERO_EYES: Map<String, Eyes> = mapOf(\n" + "\n".join(rows) + "\n)\n"
open(OUT, "w", encoding="utf-8").write(kt)
print("wrote", OUT, len(rows), "entries")
