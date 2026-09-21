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
    """하늘이 **끝나는** 줄. 없으면 None — 그건 떠 있는 배경이다.

    두 번 틀리고 세 번째에 맞았다.

    ① "행 평균색이 가장 크게 바뀌는 줄"  → `bg_dino`에서 **y=547**. 하늘 경계가 아니라
       **언덕과 들판이 갈리는 줄**이었다. 부드러운 언덕보다 들판 경계가 또렷해 최댓값이 그리로 갔다.
    ② "위에서 내려오며 하늘색을 처음 벗어나는 줄" → **y=110**. 이번엔 **구름**에 걸렸다.
       구름도 하늘색이 아니다.
    ③ **아래에서 올라가며 땅이 끝나는 줄** ← 맞다. 땅은 한 번 시작하면 화면 끝까지 이어지지만
       구름은 위에 떠 있는 얼룩이라, 밑에서 올라오면 구름을 만나기 전에 멈춘다.

    우주나 바다처럼 **바닥이 없는 그림에서는 밑에서 바로 하늘색이 나온다.**
    그때 None을 돌려주고 부르는 쪽이 「떠 있는 배치」로 넘어간다.
    """
    rows = a.reshape(a.shape[0], -1, a.shape[2]).mean(1)
    top = rows[:max(4, int(len(rows) * 0.08))]
    sky, jitter = np.median(top, axis=0), top.std(0).mean()
    off = np.abs(rows - sky).mean(1) > max(14.0, jitter * 4)

    y, gap = len(rows) - 1, 0
    while y >= 0:
        if off[y]:
            gap = 0
        else:
            gap += 1
            if gap > 5:                   # 땅 안의 물웅덩이 같은 하늘색 얼룩은 봐준다
                break
        y -= 1
    y0 = y + gap + 1
    return None if y0 > len(rows) * 0.92 else y0   # 바닥이 거의 없다 = 떠 있는 배경


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


def pick(sd, n, y0, h, floating):
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
        # 바닥이 있으면 칸의 아랫변(소품이 서는 곳), 떠 있으면 칸 한가운데
        cy = int(y0 + (j + (0.5 if floating else 1)) * GRID)
        t = (cy - y0) / max(1, h - y0)
        out.append((cx, cy, FAR + (NEAR - FAR) * t, float(sd[j, i])))
        r = 6
        ok[:, max(0, i - r):i + r + 1] = np.inf       # 같은 세로줄은 다시 안 쓴다
    return out


def main():
    args = [a for a in sys.argv[1:] if a != "-o"]
    out, bg_path, props = args[-1], args[0], args[1:-1]

    bg = Image.open(bg_path).convert("RGB")
    w, h = bg.size
    a = np.asarray(bg).astype(float)

    # 바닥이 있는 배경과 떠 있는 배경(우주·바닷속)은 배치가 다르다.
    # 바닥이 있으면 소품이 **선다**(아랫변을 바닥에 맞춘다). 떠 있으면 **뜬다**(가운데를 맞춘다).
    y0 = horizon(a)
    floating = y0 is None
    if floating:
        y0 = 0
    sd = flatness(a, y0)
    spots = pick(sd, len(props), y0, h, floating)

    placed = bg.copy().convert("RGBA")
    # 먼 것부터 얹는다 — 가까운 소품이 위에 와야 겹침이 자연스럽다
    for path, spot in sorted(zip(props, spots), key=lambda t: t[1][1] if t[1] else 0):
        name = path.replace("\\", "/").split("/")[-1]
        if spot is None:
            print("  %-13s 자리 못 찾음 — 뺀다" % name)
            continue
        cx, cy, frac, s = spot
        im = Image.open(path).convert("RGBA")
        im = im.crop(im.getbbox())
        ph = max(1, int(h * frac))
        im = im.resize((max(1, int(im.width * ph / im.height)), ph), Image.LANCZOS)
        # 가장자리 칸이 뽑히면 소품이 화면 밖으로 잘린다 — 별이 반쪽만 보였다.
        x = min(max(cx - im.width // 2, 0), w - im.width)
        y = cy - im.height // 2 if floating else cy - im.height
        placed.alpha_composite(im, (x, max(0, min(y, h - im.height))))
        print("  %-13s (%4d,%4d)  높이 %3d px  거친 정도 %.1f" % (name, cx, cy, ph, s))

    shown = bg.copy().convert("RGBA")
    d = ImageDraw.Draw(shown, "RGBA")
    gy, gx = sd.shape
    for j in range(gy):
        for i in range(gx):
            if sd[j, i] <= MAX_SD:
                d.rectangle([i * GRID, y0 + j * GRID,
                             (i + 1) * GRID - 1, y0 + (j + 1) * GRID - 1], fill=DONE + (70,))
    if not floating:
        d.line([(0, y0), (w, y0)], fill=CURTAIN + (255,), width=4)
    for spot in spots:
        if spot:
            d.ellipse([spot[0] - 11, spot[1] - 11, spot[0] + 11, spot[1] + 11],
                      outline=CURTAIN, width=4)

    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\malgun.ttf", 26)
    except OSError:
        f = None
    BAR = 42
    c = Image.new("RGB", (w * 2 + 26, h + BAR), PAPER)
    c.paste(shown.convert("RGB"), (0, BAR))
    c.paste(placed.convert("RGB"), (w + 26, BAR))
    dr = ImageDraw.Draw(c)
    left = ("① 떠 있는 배경 — 바닥이 없어 소품이 뜬다" if floating
            else "① 찾은 것 — 빨간 줄이 지평선, 초록이 얹을 만한 칸")
    dr.text((4, 6), left, fill=(22, 24, 31), font=f)
    dr.text((w + 30, 6), "② 그 자리에 얹은 결과", fill=(22, 24, 31), font=f)
    c.save(out)
    print("\n%s · 얹을 만한 칸 %d/%d · %s"
          % ("바닥 없음(떠 있는 배치)" if floating else "지평선 y=%d" % y0,
             int((sd <= MAX_SD).sum()), sd.size, out))


if __name__ == "__main__":
    main()
