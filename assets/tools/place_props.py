# -*- coding: utf-8 -*-
"""미리 만들어 둔 소품을 생성된 배경 **어디에** 얹을지 계산한다.

규칙 8은 *"만지는 것(바위·나무·집·별)은 미리 만들어 두고 얹는다 — 0초"*까지만 정하고
**어디에 얹을지는 안 정한다.** 배경은 매번 새로 생성되므로 좌표를 박아 둘 수 없다 —
박으면 바위가 하늘에 뜬다.

세 가지 길이 있었다.
  ① 배경 프롬프트로 자리를 비워 두게 시킨다 — 제일 싸지만 SDXL이 자주 무시한다.
     `render.py` 주석에 이미 *"full body·zoomed out을 넣어도 확대해 그린다"*가 있다.
  ② **만든 배경을 읽어 자리를 찾는다** ← 이것을 택했다.
  ③ 아래쪽에 항상 단색 땅 띠를 깔고 거기에만 얹는다 — 안전하지만 그림이 단조로워진다.

②를 택한 근거는 **같은 방식이 이미 통하고 있다**는 것이다. `find_eyes.py`가 생성된
주인공 그림에서 눈 좌표를 찾아 눈 스티커를 얹는다. 배경도 똑같이 읽으면 된다.
GPU를 안 쓰고 수십 ms다 — 그림 생성이 2.4초인 것에 비하면 공짜다.

어떻게 찾나
  1. **지평선** — 행마다 색 평균을 내고 위아래 차이가 가장 큰 곳. 하늘과 땅이 갈리는 줄이다.
  2. **빈 구역** — 지평선 아래에서 창을 굴리며 **표준편차가 낮은**(어수선하지 않은) 칸을 찾는다.
     화산이나 나무 위에 바위를 얹지 않으려는 것이다.
  3. **겹침 방지** — 고른 자리 주변을 지워 가며 다음을 고른다. 안 그러면 한곳에 쌓인다.
  4. **원근** — 지평선 가까울수록 작게, 아래로 올수록 크게. 같은 크기로 얹으면 멀리 있는
     바위가 집채만 해진다.

⚠️ **자리를 못 찾을 때가 있다.** 빈 구역 점수가 문턱을 못 넘으면 `None`을 돌려준다.
   그때 소품을 빼는지 · 아무 데나 얹는지 · 배경을 다시 만드는지는 **아직 안 정했다.**

    python place_props.py <배경.png> <소품.png>... -o out.png
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

GRID = 24          # 빈 구역을 재는 창 크기(px). 소품보다 작아야 지형을 본다
MAX_SD = 26.0      # 이 표준편차를 넘으면 어수선하다고 본다 — 문턱
SKY_KEEP = 0.12    # 지평선 바로 아래 이만큼은 안 쓴다. 딱 붙으면 떠 보인다
NEAR = 0.34        # 화면 세로 대비 소품 높이 — 맨 아래에 놓았을 때
FAR = 0.13         # 지평선 바로 아래에 놓았을 때
PAPER = (251, 250, 247)
CURTAIN = (123, 45, 59)
DONE = (63, 107, 78)


def horizon(a):
    """행 평균색의 위아래 차이가 가장 큰 줄. 하늘/땅 경계다.

    가장자리는 액자나 그림자라 후보에서 뺀다 — 안 빼면 맨 윗줄이 뽑힌다.
    """
    rows = a.reshape(a.shape[0], -1, a.shape[2]).mean(1)
    d = np.abs(np.diff(rows, axis=0)).sum(1)
    lo, hi = int(len(d) * 0.30), int(len(d) * 0.88)
    return lo + int(np.argmax(d[lo:hi]))


def flatness(a, y0):
    """지평선 아래를 격자로 나눠 칸마다 **어수선함**을 잰다. 낮을수록 얹기 좋다.

    ⚠️ 표준편차만 보면 안 된다. 처음엔 그것만 봤다가 **배경에 이미 그려진 공룡 몸통 위에
    바위를 얹었다.** 공룡 몸이 한 가지 색이라 「평평한 칸」으로 잡힌 것이다 —
    **평평한 것과 비어 있는 것은 다르다.**

    그래서 조건을 하나 더 건다: **그 칸 색이 땅 색과 비슷한가.** 땅 색은 지평선 아래
    전체의 중앙값으로 잡는다. 초록 들판 위의 황갈색 공룡은 이 조건에서 걸린다.
    """
    h, w = a.shape[:2]
    ground = np.median(a[y0:].reshape(-1, a.shape[2]), axis=0)
    gy, gx = (h - y0) // GRID, w // GRID
    sd = np.zeros((gy, gx))
    for j in range(gy):
        for i in range(gx):
            tile = a[y0 + j * GRID:y0 + (j + 1) * GRID, i * GRID:(i + 1) * GRID]
            flat = tile.reshape(-1, tile.shape[2])
            # 어수선함 + 땅 색에서 벗어난 정도. 둘 중 하나만 커도 후보에서 빠진다.
            sd[j, i] = flat.std(0).mean() + np.abs(flat.mean(0) - ground).mean()
    return sd


def pick(sd, n, y0, h, w):
    """소품마다 **깊이 띠를 하나씩** 맡겨 그 안에서 제일 평평한 칸을 고른다.

    ⚠️ 처음엔 그냥 평평한 순서로 골랐더니 **셋이 전부 맨 아랫줄에 한 줄로 섰다.**
    아래가 평평하니 당연한 결과인데, 같은 크기 소품이 일렬로 놓여 배경이 납작해 보인다.
    띠를 나눠 맡기면 먼 것은 작게 · 가까운 것은 크게 놓여 **깊이가 생긴다.**

    가로로도 겹치지 않게 이미 쓴 칸 둘레를 지운다.
    """
    gy, gx = sd.shape
    ok = np.where(sd > MAX_SD, np.inf, sd)
    ok[:max(1, int(gy * SKY_KEEP))] = np.inf          # 지평선 바로 아래는 비운다

    lo = max(1, int(gy * SKY_KEEP))
    edges = np.linspace(lo, gy, n + 1).astype(int)
    out = []
    for k in range(n):
        band = ok[edges[k]:max(edges[k] + 1, edges[k + 1])]
        if not np.isfinite(band).any():
            out.append(None)                          # ⚠️ 이 띠에는 자리가 없다
            continue
        j, i = np.unravel_index(np.argmin(band), band.shape)
        j += edges[k]
        cx = int((i + 0.5) * GRID)
        cy = int(y0 + (j + 1) * GRID)                 # 칸의 아랫변 = 소품이 서는 바닥
        t = (cy - y0) / max(1, h - y0)
        out.append((cx, cy, FAR + (NEAR - FAR) * t, float(sd[j, i])))
        r = 6
        ok[:, max(0, i - r):i + r + 1] = np.inf       # 같은 세로줄은 다시 안 쓴다
    return out


def main():
    args = [a for a in sys.argv[1:] if a != "-o"]
    out = args[-1]
    bg_path, props = args[0], args[1:-1]

    bg = Image.open(bg_path).convert("RGB")
    w, h = bg.size
    a = np.asarray(bg).astype(float)

    y0 = horizon(a)
    sd = flatness(a, y0)
    spots = pick(sd, len(props), y0, h, w)

    placed = bg.copy().convert("RGBA")
    # 먼 것부터 얹는다 — 가까운 소품이 위에 와야 겹침이 자연스럽다
    for path, spot in sorted(zip(props, spots), key=lambda t: t[1][1] if t[1] else 0):
        if spot is None:
            print("  %-12s 자리 못 찾음 — 뺀다" % path.split("/")[-1])
            continue
        cx, cy, frac, s = spot
        im = Image.open(path).convert("RGBA")
        im = im.crop(im.getbbox())
        ph = int(h * frac)
        im = im.resize((max(1, int(im.width * ph / im.height)), ph), Image.LANCZOS)
        # 가장자리 칸이 뽑히면 소품이 화면 밖으로 잘린다 — 별이 반쪽만 보였다.
        x = min(max(cx - im.width // 2, 0), w - im.width)
        # cy는 소품이 **서는 바닥**이다. 중심에 맞추면 땅에 반쯤 묻힌다.
        placed.alpha_composite(im, (x, cy - im.height))
        print("  %-12s (%4d,%4d)  높이 %3d px  거친 정도 %.1f"
              % (path.split("/")[-1], cx, cy, ph, s))

    # 왼쪽엔 찾은 것을 그려 보이고, 오른쪽엔 실제로 얹은 결과를 둔다
    shown = bg.copy().convert("RGBA")
    d = ImageDraw.Draw(shown, "RGBA")
    gy, gx = sd.shape
    for j in range(gy):
        for i in range(gx):
            if sd[j, i] <= MAX_SD:
                d.rectangle([i * GRID, y0 + j * GRID, (i + 1) * GRID - 1,
                             y0 + (j + 1) * GRID - 1], fill=DONE + (70,))
    d.line([(0, y0), (w, y0)], fill=CURTAIN + (255,), width=4)
    for spot in spots:
        if spot:
            cx, cy = spot[0], spot[1]
            d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=CURTAIN, width=4)

    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\malgun.ttf", 26)
    except OSError:
        f = None
    BAR = 42
    c = Image.new("RGB", (w * 2 + 26, h + BAR), PAPER)
    c.paste(shown.convert("RGB"), (0, BAR))
    c.paste(placed.convert("RGB"), (w + 26, BAR))
    dr = ImageDraw.Draw(c)
    dr.text((4, 6), "① 배경에서 찾은 것 — 빨간 줄이 지평선, 초록이 얹을 만한 칸",
            fill=(22, 24, 31), font=f)
    dr.text((w + 30, 6), "② 그 자리에 얹은 결과", fill=(22, 24, 31), font=f)
    c.save(out)
    print("\n지평선 y=%d · 얹을 만한 칸 %d/%d · %s"
          % (y0, int((sd <= MAX_SD).sum()), sd.size, out))


if __name__ == "__main__":
    main()
