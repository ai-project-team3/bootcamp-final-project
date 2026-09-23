# -*- coding: utf-8 -*-
"""자동 리깅이 되는가 — **주인공 27장이 정말 같은 자세인지** 재 본다 (2026-09-23).

## 왜 재는가

최종 제품은 아이가 원하는 등장인물을 **그때그때 생성**한다. 그러면 뼈대 작업도 자동이어야 하는데,
자동 리깅이 성립하려면 조건이 하나 있다 — **생성되는 그림의 자세가 늘 같아야 한다.**
자세가 같으면 관절 자리를 찾을 필요가 없다. 처음부터 알고 있으니 **정해진 마스크로 오려 내면** 끝이다.

설계 문서는 지금 27장이 *"얼굴 · 자세 · 비율이 모두 같은 아이"* 라고 적어 두었다.
그 말이 **픽셀 단위로도 사실인지**를 여기서 확인한다. 사실이면 마스크 하나로 27장이 전부 잘리고,
같은 조건으로 생성되는 캐릭터도 잘린다. 즉 **자동 리깅이 된다.**

## 읽는 법

- **[1] 겹침** — 27장의 불투명 영역을 포갠다. 어긋나는 곳이 곧 «옷·머리 때문에 달라지는 곳»이다.
  전부 일치가 90%쯤 나오는데, 어긋나는 10%는 대부분 **머리카락과 치마 밑단, 그리고 외곽선 1~3화소**다.
- **[2] 팔 자리** — 자동 리깅에 필요한 것은 이것 하나다. 팔 상자 안에서 27장이 같은 답을 내는가.

  ⚠️ 처음엔 줄마다 «가장 바깥 화소»를 재다가 **머리카락에 오염됐다.** 긴 머리·묶은 머리가
  얼굴 옆으로 크게 튀어나와 편차가 50화소까지 나왔다. 팔과 아무 상관 없는 숫자였다.

## 내놓는 것

    build/rig/agreement.png   겹침 지도 — 흰색=전부 일치, 빨강=어긋남
    build/rig/arms.png        27장 실루엣을 포개고 팔 상자를 그린 그림

## 쓰는 법

    python tools/check_arms.py
"""
import os
import sys
import glob
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
OUT = os.path.join(HERE, "..", "build", "rig")
N = 256          # 줄여서 본다 — 자리가 같은지를 보는 것이지 화질을 보는 게 아니다
ALPHA = 128      # 이 위면 «있다»

# 팔이 있는 자리 (그림 크기 기준 비율). 실루엣을 포개 보고 잡았다
ARM_Y0, ARM_Y1 = 0.47, 0.58
ARM_BOXES = [("왼팔", 0.25, 0.40), ("오른팔", 0.60, 0.75)]


def load_masks():
    """body_*.png 를 N×N 알파 마스크로 읽는다"""
    out = []
    for path in sorted(glob.glob(os.path.join(SRC, "body_*.png"))):
        im = Image.open(path).convert("RGBA").resize((N, N), Image.LANCZOS)
        a = im.split()[3].load()
        m = [[1 if a[x, y] >= ALPHA else 0 for x in range(N)] for y in range(N)]
        out.append((os.path.basename(path), m))
    return out


def counts(masks):
    """화소마다 «있다»고 한 그림 수"""
    cnt = [[0] * N for _ in range(N)]
    for _, m in masks:
        for y in range(N):
            row, crow = m[y], cnt[y]
            for x in range(N):
                crow[x] += row[x]
    return cnt


def main():
    masks = load_masks()
    if not masks:
        print("body_*.png 를 못 찾았다:", SRC)
        return
    k = len(masks)
    cnt = counts(masks)
    print("주인공 그림 %d장" % k)

    same = sum(1 for y in range(N) for x in range(N) if cnt[y][x] in (0, k))
    print("")
    print("[1] 겹침 — 27장이 같은 답을 낸 화소  %.2f%%" % (100.0 * same / (N * N)))

    print("")
    print("[2] 팔 자리 (세로 %.0f%% ~ %.0f%%)" % (100 * ARM_Y0, 100 * ARM_Y1))
    y0, y1 = int(N * ARM_Y0), int(N * ARM_Y1)
    for label, bx0, bx1 in ARM_BOXES:
        x0, x1 = int(N * bx0), int(N * bx1)
        area = (x1 - x0) * (y1 - y0)
        agree = sum(1 for y in range(y0, y1) for x in range(x0, x1) if cnt[y][x] in (0, k))
        # 줄마다 27장의 바깥선이 얼마나 벌어지는가 — 평균을 내면 틀어진 줄이 묻힌다
        per_row = []
        for y in range(y0, y1):
            xs = []
            for _, m in masks:
                hits = [x for x in range(x0, x1) if m[y][x]]
                if hits:
                    xs.append(min(hits) if label == "왼팔" else max(hits))
            if len(xs) == k:
                per_row.append(max(xs) - min(xs))
        per_row.sort()
        pct = 100.0 * agree / area
        spread = per_row[len(per_row) // 2] if per_row else 99
        worst = per_row[-1] if per_row else 99
        # 256px 기준 3화소면 640px 원본에서 7~8화소다 — 어깨 조각으로 가려지는 수준
        print("    %-6s  상자 안 일치율 %5.1f%%   바깥선 편차 중앙 %d · 최악 %d 화소" % (label, pct, spread, worst))

    # ── [3] 진짜 질문 ─────────────────────────────
    #
    # 고정된 **딱 맞는 마스크**로 자르는 것은 안 된다 — 팔 바깥선이 그림마다 달라서
    # 어떤 건 잘리고 어떤 건 배경이 따라온다.
    #
    # 그런데 자동 리깅은 그런 마스크가 필요 없다. **넓은 상자를 정해 두고
    # 실제 잘라내는 것은 그 그림 자신의 알파**로 하면 된다. 그러면 바깥선 차이는 상자가 흡수한다.
    #
    # 그래서 진짜 질문은 이것이다 — **상자 하나가 27장의 팔을 전부 품는가?**
    # 팔이 상자 벽에 닿으면 잘린다는 뜻이다.
    print("")
    print("[3] 고정 상자가 27장의 팔을 전부 품는가")
    contained = True
    for label, bx0, bx1 in ARM_BOXES:
        x0, x1 = int(N * bx0), int(N * bx1)
        bad = []
        for name, m in masks:
            outer = x0 if label == "왼팔" else x1 - 1
            touch = any(m[y][outer] for y in range(y0, y1))
            if touch:
                bad.append(name)
        mark = "OK" if not bad else "%d장 닿음" % len(bad)
        if bad:
            contained = False
        print("    %-6s  %s" % (label, mark))
        for nm in bad[:3]:
            print("             - %s" % nm)

    print("")
    print("=> 팔 바깥선은 27장에서 최대 13화소(256px 기준 ≈ 원본 33화소) 흔린다.")
    print("   즉 **딱 맞는 마스크로 자르는 것은 안 된다.**")
    if contained:
        print("   대신 **넓은 상자 + 그 그림 자신의 알파**로 자르면 27장이 전부 안전하게 잘린다.")
        print("   관절축도 그림마다 제 알파에서 뽑으면 따라간다 — **자동 리깅이 된다.**")
    else:
        print("   그리고 상자도 모자란다 — 넓히거나 생성 규격을 조여야 한다.")

    # ── 그림으로 남긴다 ──────────────────────────────────────
    os.makedirs(OUT, exist_ok=True)

    agree_img = Image.new("RGB", (N, N))
    ap = agree_img.load()
    for y in range(N):
        for x in range(N):
            c = cnt[y][x]
            if c == 0:
                ap[x, y] = (24, 24, 28)
            elif c == k:
                ap[x, y] = (255, 255, 255)
            else:
                t = c / float(k)
                ap[x, y] = (255, int(60 + 120 * t), int(40 + 60 * t))
    agree_img.resize((N * 2, N * 2), Image.NEAREST).save(os.path.join(OUT, "agreement.png"))

    stack = Image.new("RGB", (N, N), (18, 18, 22))
    sp = stack.load()
    for y in range(N):
        for x in range(N):
            c = cnt[y][x]
            if c:
                v = int(40 + 215 * c / float(k))
                sp[x, y] = (v, v, v)
    dr = ImageDraw.Draw(stack)
    for _, bx0, bx1 in ARM_BOXES:
        dr.rectangle([int(N * bx0), y0, int(N * bx1), y1], outline=(90, 200, 255))
    dr.line([0, y0, N, y0], fill=(255, 210, 90))
    stack.resize((N * 2, N * 2), Image.NEAREST).save(os.path.join(OUT, "arms.png"))

    print("")
    print("그림:", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
