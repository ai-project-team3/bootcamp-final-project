# -*- coding: utf-8 -*-
"""팔을 잘라 어깨를 축으로 돌려 본다 — **잘린 자국이 보이는가** (2026-09-23).

`check_arms.py` 가 「넓은 상자 + 그 그림 자신의 알파」로 자르면 27장이 전부 안전하게 잘린다는 것을
숫자로 보였다. 남은 관문은 하나다 — **돌렸을 때 뜯어져 보이는가.**

앱에 넣기 전에 여기서 그림으로 먼저 본다. 안 되는 걸 앱까지 들고 갈 이유가 없다.

## 하는 일

1. 몸 그림에서 **팔 상자**만큼 오려 낸다 (상자는 «어디가 팔인가»만 정하고, 실제 모양은 알파가 정한다)
2. 몸에서는 그 자리를 지운다
3. 어깨를 축으로 여러 각도로 돌려 **한 줄로 늘어놓는다**
4. 이음매를 가리는 **어깨 조각**이 있을 때와 없을 때를 나란히 낸다

## 내놓는 것

    build/rig/arm_swing.png    각도별로 늘어놓은 그림 (위: 어깨 조각 없음 · 아래: 있음)
    build/rig/arm_piece.png    잘라 낸 팔 한 장
    build/rig/body_cut.png     팔을 지운 몸

## 쓰는 법

    python tools/cut_arm.py [body_blue_pants]
"""
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DRAWABLE = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
OUT = os.path.join(HERE, "..", "build", "rig")

NAME = sys.argv[1] if len(sys.argv) > 1 else "body_blue_pants"

# check_arms.py 가 잰 자리. 상자는 넉넉해야 한다 — 27장의 팔이 전부 들어와야 하므로
# ⚠️ 첫 시도는 「어깨에서 팔 전체」를 상자로 잘랐다가 **옵과 몸통을 통째로 물었다.**
#    셀러보니 이유가 분명했다 — **위팔은 티셔츠 소매에 묻혀 있어 몸통과 구분이 안 된다.**
#    살색이 보이는 것은 세로 52% 아래(팔뚝·손)뿐이다.
#    그래서 지금 그림으로 할 수 있는 것은 **팔꿈치 관절 하나**다.
SKIN_BAND = (0.50, 0.72)          # 살색 팔뚝이 보이는 세로 구간
ARM_SIDE = "right"                 # 화면에서 오른쪽 팔
ANGLES = [40, 20, 0, -30, -60]


def is_skin(c):
    r, g, b, a = c
    return a >= 128 and r > 180 and g > 140 and b > 110 and r > b + 30


def find_forearm(im):
    """살색으로 팔뚝을 찾는다. (x0, y0, x1, y1)"""
    w, h = im.size
    px = im.load()
    y0, y1 = int(h * SKIN_BAND[0]), int(h * SKIN_BAND[1])
    xs, ys = [], []
    for y in range(y0, y1):
        for x in range(w // 2 if ARM_SIDE == "right" else 0,
                       w if ARM_SIDE == "right" else w // 2):
            if is_skin(px[x, y]):
                xs.append(x); ys.append(y)
    if not xs:
        return None
    # 손·팔뚝만 남기고 얼굴·목은 버린다 — 띄를 50% 아래로 잡았으므로 자동으로 빠진다
    return (min(xs) - 4, min(ys) - 4, max(xs) + 5, max(ys) + 5)


def arm_pivot(alpha, box, w, h):
    """어깨 관절 자리 — 팔 상자 안에서 **불투명이 시작되는 맨 위**, 몸통 쪽 모서리.

    그림마다 제 알파에서 뽑으므로 팔이 조금씩 달라도 축이 따라간다.
    """
    x0, y0, x1, y1 = box
    px = alpha.load()
    top = None
    for y in range(y0, y1):
        xs = [x for x in range(x0, x1) if px[x, y] >= 128]
        if xs:
            top = (y, min(xs), max(xs))
            break
    if top is None:
        return ((x0 + x1) // 2, y0)
    y, lo, hi = top
    # 오른팔은 몸통이 왼쪽에 있다 — 안쪽(왼쪽) 위 모서리가 어깨다
    return (lo + int((hi - lo) * 0.30), y + int((y1 - y0) * 0.06))


def main():
    path = os.path.join(DRAWABLE, NAME + ".png")
    if not os.path.exists(path):
        print("그림이 없다:", path)
        return
    body = Image.open(path).convert("RGBA")
    w, h = body.size
    box = find_forearm(body)
    if box is None:
        print("살색 팔뚝을 못 찾았다")
        return
    print("%s  %dx%d" % (NAME, w, h))
    print("팔뚝 상자 %s  (가로 %.0f%%~%.0f%% · 세로 %.0f%%~%.0f%%)" % (
        box, 100.0 * box[0] / w, 100.0 * box[2] / w, 100.0 * box[1] / h, 100.0 * box[3] / h))

    arm = body.crop(box)
    cut = body.copy()
    cut.paste(Image.new("RGBA", arm.size, (0, 0, 0, 0)), box)

    # 팔꿈치 = 팔뚝 상자의 위 가운데. 그림마다 알파에서 나오므로 따라간다
    pivot = ((box[0] + box[2]) // 2, box[1] + int((box[3] - box[1]) * 0.08))
    print("팔꿈치 축 %s" % (pivot,))

    os.makedirs(OUT, exist_ok=True)
    arm.save(os.path.join(OUT, "arm_piece.png"))
    cut.save(os.path.join(OUT, "body_cut.png"))

    cell = int(w * 0.55)
    strip = Image.new("RGBA", (cell * len(ANGLES), cell), (22, 22, 28, 255))
    ax, ay = pivot[0] - box[0], pivot[1] - box[1]
    for i, ang in enumerate(ANGLES):
        frame = cut.copy()
        big = Image.new("RGBA", (arm.width * 3, arm.height * 3), (0, 0, 0, 0))
        big.paste(arm, (arm.width, arm.height), arm)
        big = big.rotate(ang, resample=Image.BICUBIC, center=(arm.width + ax, arm.height + ay))
        frame.alpha_composite(big, (box[0] - arm.width, box[1] - arm.height))
        strip.alpha_composite(frame.resize((cell, cell), Image.LANCZOS), (i * cell, 0))
    strip.save(os.path.join(OUT, "arm_swing.png"))
    print("각도:", ANGLES)
    print("→", os.path.abspath(os.path.join(OUT, "arm_swing.png")))


if __name__ == "__main__":
    main()
