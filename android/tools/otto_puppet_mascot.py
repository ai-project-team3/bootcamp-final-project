# -*- coding: utf-8 -*-
"""기존 마스코트(res/drawable/mascot.png · 512px) 인형 부위 자르기 (09-29).

사용자 — 「마스코트 기존 이미지에 뼈대를 붙여서 움직이게」. 앱 곳곳(나레이션 얼굴 · 온보딩 · 로고)과 같은 오또라
모습이 하나로 맞는다. 자동 뼈대(RigCore)는 이 그림에서 팔을 못 찾아서(털 손 · 소매가 몸에 붙음) 손으로 자른다.

부위(뒤 → 앞): 꼬리 · 몸(망토 · 배 · 다리) · 머리(후드 · 얼굴 · 귀) · 왼팔 · 오른팔
  - 머리는 목(후드 아랫단)에서 돈다. 아래 20px 은 흐리게 끝나 몸과 겹친다 — 기울여도 자른 줄이 안 보인다
  - 팔(앞발 + 파란 소매단)은 어깨에서 돈다. 팔이 비운 머리 · 몸 자리는 팔 경계 너머 무늬를 거울처럼 옮겨 메운다
  - 다리는 짧고 붙어 있어 몸에 둔다(몸 전체가 통통 튄다)
좌표는 512 원본 기준(격자로 재었다).

    python tools/otto_puppet_mascot.py [DEBUG_OUT.png]
결과: res/drawable/otto_m_*.webp (512×512 같은 틀) · 축 좌표 출력 (Kotlin `OttoPuppet` 에 옮긴다)
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
SRC = os.path.join(RES, "mascot.png")

PIV = {"tail": (172, 398), "head": (256, 300), "arm_l": (172, 318), "arm_r": (352, 318)}
ORDER = ["tail", "body", "head", "arm_l", "arm_r"]
NECK = 296          # 머리 / 몸 경계
FADE = 22           # 머리 아래 끝을 흐리게 하는 폭
ARM_L = [(92, 266), (128, 252), (160, 260), (174, 298), (178, 340), (150, 348), (116, 334), (98, 316)]
ARM_R = [(432, 270), (400, 252), (366, 260), (350, 298), (348, 338), (372, 348), (404, 334), (426, 314)]


def main(debug=None):
    im = Image.open(SRC).convert("RGBA")
    W, H = im.size
    assert (W, H) == (512, 512), im.size
    a = im.getchannel("A").load()
    px = im.load()

    def solid(x, y):
        return a[x, y] > 8

    def green(x, y):
        r, g, b, _ = px[x, y]
        return g > r + 8

    arm_l = Image.new("L", (W, H), 0); ImageDraw.Draw(arm_l).polygon(ARM_L, fill=255)
    arm_r = Image.new("L", (W, H), 0); ImageDraw.Draw(arm_r).polygon(ARM_R, fill=255)
    # 앞발 테두리(흐린 가장자리)까지 팔에 넣는다 — 안 넣으면 팔이 비킬 때 머리 쪽에 흰 테두리 조각이 남았다
    arm_l = arm_l.filter(ImageFilter.MaxFilter(9))
    arm_r = arm_r.filter(ImageFilter.MaxFilter(9))
    al, ar = arm_l.load(), arm_r.load()

    masks = {k: Image.new("L", (W, H), 0) for k in ORDER}
    m = {k: v.load() for k, v in masks.items()}
    for y in range(H):
        for x in range(W):
            if not solid(x, y):
                continue
            if al[x, y]:
                m["arm_l"][x, y] = 255
                continue
            if ar[x, y]:
                m["arm_r"][x, y] = 255
                continue
            # 꼬리 — 몸 왼쪽 아래로 나온 줄무늬 털(망토 · 다리 제외)
            if x < 176 and 296 <= y <= 440 and not green(x, y):
                m["tail"][x, y] = 255
                continue
            if y < NECK:
                m["head"][x, y] = 255
            if y >= NECK - FADE:
                m["body"][x, y] = 255
    # 꼬리 뿌리는 몸 뒤로 이어 둔다
    for y in range(360, 440):
        for x in range(170, 200):
            if solid(x, y) and not green(x, y):
                m["tail"][x, y] = 255
    # 머리 아래 끝을 흐리게
    for y in range(NECK - FADE, NECK):
        k = int(255 * (NECK - y) / FADE)
        for x in range(W):
            if m["head"][x, y]:
                m["head"][x, y] = k

    # 팔이 비운 자리 — 팔 경계 안쪽(몸 쪽) 무늬를 거울처럼 옮겨 메운다. 몸 바깥으로 삐져나간 자리는 비운다
    fill = im.copy()
    fp = fill.load()
    for arm, inward in ((al, 1), (ar, -1)):
        for y in range(240, 360):
            xs = [x for x in range(W) if arm[x, y]]
            if not xs:
                continue
            edge = max(xs) if inward > 0 else min(xs)   # 몸 쪽 경계
            for x in xs:
                sx = edge + (edge - x) + inward * 2
                if 0 <= sx < W and not al[sx, y] and not ar[sx, y] and a[sx, y] > 200:
                    fp[x, y] = px[sx, y][:3] + (255,)
                else:
                    fp[x, y] = (0, 0, 0, 0)
    # 채운 자리 중 몸 실루엣 밖(원래 팔만 있던 바깥)은 비운다 — 후드 · 망토 바깥 가장자리 추정
    for y in range(240, 360):
        for x in range(W):
            if not (al[x, y] or ar[x, y]):
                continue
            # 후드 바깥 가장자리 추정은 **안쪽으로 넉넉히** — 넓게 잡으면 팔이 비킬 때 후드 옆에 가짜 조각이 떠 보였다
            left_edge = 126 + max(0, y - 250) * 0.8 if y < 300 else 170
            right_edge = 398 - max(0, y - 250) * 0.8 if y < 300 else 346
            if x < left_edge or x > right_edge:
                fp[x, y] = (0, 0, 0, 0)
            else:
                if y < NECK:
                    m["head"][x, y] = 255
                if y >= NECK - FADE:
                    m["body"][x, y] = 255

    os.makedirs(RES, exist_ok=True)
    for k in ORDER:
        soft = masks[k].filter(ImageFilter.GaussianBlur(0.6))
        srcim = im if k in ("arm_l", "arm_r", "tail") else fill
        part = srcim.copy()
        part.putalpha(ImageChops.darker(soft, srcim.getchannel("A")))
        dest = os.path.join(RES, f"otto_m_{k}.webp")
        part.save(dest, "WEBP", quality=92, method=6)
        print(k, os.path.getsize(dest) // 1024, "KB")
    for k, (x, y) in PIV.items():
        print(f"pivot {k}: ({x / 512:.4f}f, {y / 512:.4f}f)")

    if debug:
        colors = {"body": (200, 200, 200), "head": (240, 200, 60), "arm_l": (230, 60, 60), "arm_r": (60, 90, 230), "tail": (170, 60, 200)}
        dbg = Image.new("RGBA", (W, H), (255, 255, 255, 255))
        dbg.alpha_composite(im)
        over = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for k, mask in masks.items():
            over.paste(Image.new("RGBA", (W, H), colors[k] + (90,)), (0, 0), mask)
        dbg.alpha_composite(over)
        d = ImageDraw.Draw(dbg)
        for (x, y) in PIV.values():
            d.ellipse((x - 5, y - 5, x + 5, y + 5), fill=(0, 0, 0, 255))
        dbg.convert("RGB").resize((700, 700)).save(debug)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
