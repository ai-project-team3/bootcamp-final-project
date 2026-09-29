# -*- coding: utf-8 -*-
"""오또 **옆모습** 인형 부위 자르기 — 걸을 때 쓴다.

09-29 두 번째 판: 기존 마스코트(`mascot.png`)를 Kontext 로 옆으로 돌린 그림(`tools/gen_faces.py side_m`)에서 자른다.
첫 판은 새로 뽑은 A-포즈에서 돌린 그림이라 앱 곳곳의 오또와 얼굴 · 비율이 조금 달랐다.

부위(뒤 → 앞): 꼬리 · 뒷다리 · 앞다리 · 뒷팔 · 몸(머리 포함) · 앞팔
  - 앞팔 = 소매 + 앞발 **통째로**, 어깨에서 흔든다(실제 걷기처럼 팔 전체가 앞뒤로)
  - 뒷팔 = 가슴 앞으로 나온 먼 쪽 소매단 + 앞발, 망토 속 어깨에서 흔든다
  - 다리는 엉덩이 아래에서 둘로 — 뿌리는 몸 뒤에 이어 두고, 몸 아래 끝은 흐리게 끝나 다리 위에 겹친다
  - 앞팔이 비운 몸 자리는 망토 천(소매 자리) · 허벅지 털(앞발 자리)을 옆에서 옮겨 메운다
좌표는 1024 원본 기준(격자로 재었다). 앱에는 512 로 줄여 넣는다.

    python tools/otto_puppet_side.py SRC.png [DEBUG_OUT.png]
결과: res/drawable/otto_side_*.webp (512×512 같은 틀) · 축 좌표 출력 (Kotlin `OttoPuppet` 의 SIDE 에 옮긴다)
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")

PIV = {"tail": (392, 760), "leg_far": (610, 815), "leg_near": (478, 800), "arm_far": (700, 640), "arm_near": (572, 585)}
ORDER = ["tail", "leg_far", "leg_near", "arm_far", "body", "arm_near"]
ARM_NEAR = [(540, 552), (602, 558), (642, 640), (632, 690), (650, 755), (642, 792), (600, 804), (556, 798), (536, 778),
            (528, 742), (504, 740), (498, 690), (518, 620)]
ARM_FAR = [(688, 636), (762, 648), (792, 698), (798, 748), (772, 778), (720, 774), (690, 742), (680, 690)]
LEG_TOP = 836      # 이 아래가 다리
SPLIT = 560        # 앞다리 | 뒷다리 경계(x)
FADE = 30          # 몸 아래 끝을 흐리게 하는 폭


def main(src, debug=None):
    im = Image.open(src).convert("RGBA")
    W, H = im.size
    assert (W, H) == (1024, 1024), im.size
    a = im.getchannel("A").load()
    px = im.load()

    def solid(x, y):
        return a[x, y] > 8

    def green(x, y):
        r, g, b, _ = px[x, y]
        return g > r + 10

    poly = {}
    for k, pts in (("arm_near", ARM_NEAR), ("arm_far", ARM_FAR)):
        mk = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mk).polygon(pts, fill=255)
        poly[k] = mk.filter(ImageFilter.MaxFilter(5)).load()   # 흐린 테두리까지

    masks = {k: Image.new("L", (W, H), 0) for k in ORDER}
    m = {k: v.load() for k, v in masks.items()}
    for y in range(H):
        for x in range(W):
            if not solid(x, y):
                continue
            if poly["arm_near"][x, y]:
                m["arm_near"][x, y] = 255
            elif poly["arm_far"][x, y]:
                m["arm_far"][x, y] = 255
            elif x < 395 and 560 <= y <= 860 and not green(x, y):
                m["tail"][x, y] = 255
            elif y >= LEG_TOP:
                m["leg_near" if x < SPLIT else "leg_far"][x, y] = 255
            else:
                m["body"][x, y] = 255
    # 다리 · 꼬리 뿌리는 몸 뒤로 이어 둔다(몸이 덮는다) — 돌릴 때 위쪽이 비지 않게
    for y in range(760, LEG_TOP):
        for x in range(380, 700):
            if solid(x, y) and not poly["arm_near"][x, y]:
                m["leg_near" if x < SPLIT else "leg_far"][x, y] = 255
    for y in range(700, 850):
        for x in range(380, 430):
            if solid(x, y) and not green(x, y):
                m["tail"][x, y] = 255
    # 몸 아래 끝을 다리 위로 조금 늘여 흐리게 — 자른 가로줄이 다리가 돌 때 보이지 않게
    for y in range(LEG_TOP - 10, LEG_TOP + FADE):
        k = int(255 * (LEG_TOP + FADE - y) / (FADE + 10))
        for x in range(380, 700):
            if solid(x, y) and not poly["arm_near"][x, y]:
                m["body"][x, y] = max(m["body"][x, y], min(255, k))

    # 앞팔이 비운 몸 자리 — 소매 자리(망토 아랫단 위)는 망토 천을 왼쪽에서 되풀이해 옮기고,
    # 앞발 자리(아래)는 왼쪽 허벅지 털을 옮긴다. 몸 실루엣 밖(원래 앞발만 있던 오른쪽 바깥)은 비운다
    body = im.copy()
    bpx = body.load()
    bm = m["body"]
    near = poly["arm_near"]
    for y in range(540, 820):
        for x in range(480, 670):
            if not near[x, y]:
                continue
            cape = y < 752
            step = 90 if cape else 110
            sx = x - step
            while sx >= 0 and near[sx, y]:
                sx -= step
            inside = x < (690 if y < 700 else 672 - (y - 700) * 0.2)
            if inside and sx >= 0 and a[sx, y] > 200 and (green(sx, y) == cape or not cape):
                r, g, b, _ = px[sx, y]
                bpx[x, y] = (r, g, b, 255)
                bm[x, y] = 255

    os.makedirs(RES, exist_ok=True)
    for k in ORDER:
        soft = masks[k].filter(ImageFilter.GaussianBlur(0.8))
        srcim = body if k == "body" else im
        part = srcim.copy()
        part.putalpha(ImageChops.darker(soft, srcim.getchannel("A")))
        part = part.resize((512, 512), Image.LANCZOS)
        dest = os.path.join(RES, f"otto_side_{k}.webp")
        part.save(dest, "WEBP", quality=92, method=6)
        print(k, os.path.getsize(dest) // 1024, "KB")
    for k, (x, y) in PIV.items():
        print(f"pivot {k}: ({x / 1024:.4f}f, {y / 1024:.4f}f)")

    if debug:
        colors = {"body": (200, 200, 200), "arm_near": (230, 60, 60), "arm_far": (60, 90, 230), "leg_near": (240, 170, 30),
                  "leg_far": (40, 170, 90), "tail": (170, 60, 200)}
        dbg = Image.new("RGBA", (W, H), (255, 255, 255, 255))
        dbg.alpha_composite(body)
        over = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for k, mask in masks.items():
            over.paste(Image.new("RGBA", (W, H), colors[k] + (100,)), (0, 0), mask)
        dbg.alpha_composite(over)
        d = ImageDraw.Draw(dbg)
        for (x, y) in PIV.values():
            d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=(0, 0, 0, 255))
        dbg.convert("RGB").resize((700, 700)).save(debug)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
