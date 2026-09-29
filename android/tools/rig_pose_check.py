# -*- coding: utf-8 -*-
"""뽑힌 그림에 뼈대가 붙는가 — 재고, 실제로 움직여 본다 (2026-09-28)

`rig_pose_gen.py` 가 만든 `build/rig_pose/*_cut.png` 를 읽는다. 관절 자리는 **틀의 좌표를 그대로** 쓴다 —
그림을 보고 찾지 않는다. 그게 이 실험의 물음이다(정해진 자세로 뽑으면 관절 자리를 이미 안다).

재는 것
  공통    겹침 — 뽑힌 실루엣이 마네킹과 얼마나 겹치나 (IoU)
  사람형  겨드랑이 틈 — 몸통 바로 바깥 세로줄이 겨드랑이 아래에서 비었나 → 비면 팔이 몸과 떨어진 것 = 어깨가 있다
  네발형  다리 수 — 다리 높이의 가로줄마다 떨어진 덩어리가 몇 개인가 → 4 면 다리가 따로 있다

움직여 보는 것 (움직이는 부위는 몸 **뒤에** 그린다 — 관절 이음매를 몸통이 덮는다)
  사람형  오른팔을 어깨에서 들어 올려 손 흔들기
  네발형  다리 넷을 대각선끼리 번갈아 걷기 · 꼬리 흔들기

쓰는 법  python tools/rig_pose_check.py
결과     build/rig_pose/report.png · *_wave.gif · *_walk.gif · result.json
"""
import glob, json, math, os, sys
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rig_pose_gen import TEMPLATES, OUT, N  # 같은 틀 · 같은 좌표

FONT = ImageFont.truetype(r"C:\Windows\Fonts\malgunbd.ttf", 26)
SMALL = ImageFont.truetype(r"C:\Windows\Fonts\malgun.ttf", 22)


def alpha_mask(im, th=128):
    return im.split()[3].point(lambda v: 255 if v >= th else 0)


def template_mask(kind):
    t = Image.open(os.path.join(OUT, f"template_{kind}.png")).convert("RGB")
    return t.point(lambda v: 255 if v < 245 else 0).convert("L").point(lambda v: 255 if v > 0 else 0)


def iou(a, b):
    pa, pb = a.getdata(), b.getdata()
    inter = sum(1 for x, y in zip(pa, pb) if x and y)
    uni = sum(1 for x, y in zip(pa, pb) if x or y)
    return inter / uni if uni else 0.0


def column_fill(mask, x0, x1, y0, y1):
    """세로 띠에서 채워진 비율"""
    px = mask.load(); tot = fill = 0
    for x in range(x0, x1):
        for y in range(y0, y1):
            tot += 1; fill += 1 if px[x, y] else 0
    return fill / tot


def runs_in_row(mask, y, min_w=10):
    px = mask.load(); runs, cur = 0, 0
    for x in range(N):
        if px[x, y]:
            cur += 1
        else:
            if cur >= min_w: runs += 1
            cur = 0
    return runs + (1 if cur >= min_w else 0)


def reach(mask, seed, box, th=40):
    """상자 안에서 seed 와 이어진 불투명 화소 (알파 th 이상 · 네 방향)"""
    px = mask.load(); x0, y0, x1, y1 = box
    sx, sy = seed
    if not px[sx, sy]:  # 손 자리가 비었으면 가까운 불투명 화소에서 시작
        cand = [(abs(x - sx) + abs(y - sy), x, y) for x in range(max(x0, sx - 60), min(x1, sx + 60))
                for y in range(max(y0, sy - 60), min(y1, sy + 60)) if px[x, y]]
        if not cand: return set()
        _, sx, sy = min(cand)
    seen = {(sx, sy)}; stack = [(sx, sy)]
    while stack:
        x, y = stack.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if x0 <= nx < x1 and y0 <= ny < y1 and (nx, ny) not in seen and px[nx, ny]:
                seen.add((nx, ny)); stack.append((nx, ny))
    return seen


def grow(pix, im, box, r=3):
    """덩어리를 r 화소 넓혀 **거의 투명한 가장자리**까지 같이 가져간다.
    배경을 지운 그림은 가장자리에 알파 1~40 의 옅은 화소가 둘러 있다. 덩어리 기준(40)에서 빠진 이 화소가
    몸 쪽에 남으면, 팔을 돌렸을 때 원래 자리에 **옅은 윤곽선**이 보인다 (09-28)"""
    from PIL import ImageFilter
    x0, y0, x1, y1 = box
    m = Image.new("L", im.size); mp = m.load()
    for x, y in pix: mp[x, y] = 255
    m = m.filter(ImageFilter.MaxFilter(2 * r + 1)); mp = m.load(); ap = im.split()[3].load()
    return {(x, y) for x in range(x0, x1) for y in range(y0, y1) if mp[x, y] and ap[x, y]}


def shoulder_joint(im, lump, edge_x, side):
    """어깨를 **할핀 관절**로 — 축을 팔 뿌리 한가운데로 옮기고, 축을 중심으로 둥근 덮개를 만든다 (09-28 2차).

    1차는 마네킹 몸통 모서리를 축으로 두고 몸통 옆을 **세로로 곧게** 잘랐다. 실물 폰에서 보니 팔을 들면 소매가
    잘린 모서리째 따라 올라가고, 몸 쪽에도 직선이 남아 **어깨가 싹둑 잘린 것처럼** 보였다(사용자 지적).
    종이 인형은 둥근 관절로 이음매를 감춘다 — 그걸 그림에서 만든다:
      축     그 그림에서 팔이 몸에 붙는 뿌리(몸통 바로 바깥 세로줄)의 한가운데, 몸통 안쪽으로 반지름의 25%
      덮개   축을 중심으로 한 원 안의 **원래 화소**(소매 천). 몸 위에 얹어 움직이지 않는다. 가장자리 4화소는 옅게
      팔     덮개 원만큼 안쪽까지 겹쳐 잘라 돌 때 틈이 안 생기게 한다
    """
    ap = im.load()
    xs = range(edge_x, edge_x + 10) if side == "R" else range(edge_x - 9, edge_x + 1)
    ys = [y for (x, y) in lump if x in xs]
    if not ys:
        return None
    cy = (min(ys) + max(ys)) / 2; h = max(ys) - min(ys) + 1
    r = max(20.0, h * 0.55)
    px = edge_x - r * 0.25 if side == "R" else edge_x + r * 0.25   # 0.45 는 머리에 너무 가까워 올린 팔이 머리 뒤로 갔다
    arm = Image.new("RGBA", im.size); cap = Image.new("RGBA", im.size)
    armp, capp = arm.load(), cap.load()
    for (x, y) in lump:
        armp[x, y] = ap[x, y]
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(px - r) - 1, int(px + r) + 2):
            d = ((x - px) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r and ap[x, y][3]:
                armp[x, y] = ap[x, y]
                rr, g, b, a = ap[x, y]
                capp[x, y] = (rr, g, b, int(a * max(0.0, min(1.0, (r - d) / 4))))
    return arm, cap, (round(px), round(cy))


def split(im, pick):
    """pick(x, y) 가 True 인 불투명 화소는 부위로, 나머지는 몸으로 나눈다"""
    part = Image.new("RGBA", im.size); body = im.copy()
    pp, bp, ip = part.load(), body.load(), im.load()
    for y in range(N):
        for x in range(N):
            if ip[x, y][3] and pick(x, y):
                pp[x, y] = ip[x, y]; bp[x, y] = (0, 0, 0, 0)
    return part, body


def frame(body, parts, top=()):
    """parts = [(부위 그림, 각도, 중심)] — 부위를 먼저(뒤에), 몸을 나중에(앞에), top(관절 덮개)을 맨 앞에"""
    out = Image.new("RGBA", (N, N), (255, 255, 255, 255))
    for part, ang, c in parts:
        out.alpha_composite(part.rotate(ang, resample=Image.BICUBIC, center=c))
    out.alpha_composite(body)
    for t_ in top:
        out.alpha_composite(t_)
    return out.convert("RGB").resize((N // 2, N // 2), Image.LANCZOS)


def human(tag, im):
    t = TEMPLATES["human"]; m = alpha_mask(im)
    res = {"iou": round(iou(m, template_mask("human")), 3)}
    y0, y1 = t["gap_y"]
    res["gap_fill_L"] = round(column_fill(m, t["gap_x_L"] - 6, t["gap_x_L"] + 1, y0, y1), 3)
    res["gap_fill_R"] = round(column_fill(m, t["gap_x_R"], t["gap_x_R"] + 7, y0, y1), 3)
    bx = t["arm_box_R"]
    # 오른팔을 오려 들어 올린다 — 틀의 상자 안에서 **손과 이어진 덩어리만** (09-28)
    # 상자 안 화소를 전부 가져가면, 뽑힌 몸통이 틀보다 조금 넓을 때 **셔츠 옆구리 조각**이 딸려 가서
    # 팔을 돌리면 원래 팔 자리에 회색 조각이 남았다. 팔은 어깨로만 몸에 붙어 있으니 손에서 번지면 팔만 잡힌다
    arm_px = grow(reach(m, t["hand_R"], bx), im, bx)
    res["arm_R_pixels"] = len(arm_px)
    res["shoulder_ok"] = res["gap_fill_L"] < 0.08 and res["gap_fill_R"] < 0.08 and res["arm_R_pixels"] > 8000
    left_px = grow(reach(m, t["hand_L"], t["arm_box_L"]), im, t["arm_box_L"])
    _, body = split(im, lambda x, y: (x, y) in arm_px or (x, y) in left_px)
    jr = shoulder_joint(im, arm_px, bx[0], "R")
    jl = shoulder_joint(im, left_px, t["arm_box_L"][2] - 1, "L")
    arm, cap_R, sh = jr
    arm_L, cap_L, sh_L = jl
    res["pieces"] = [("arm_R", arm, sh, 0), ("arm_L", arm_L, sh_L, 0), ("body", body, None, 1),
                     ("cap_R", cap_R, None, 2), ("cap_L", cap_L, None, 2)]
    caps = (cap_R, cap_L)
    angles = [0, 20, 40, 60, 80, 100, 110, 95, 110, 95, 110, 95, 110, 80, 50, 20, 0]
    frames = [frame(body, [(arm, a, sh), (arm_L, -a * 0 , sh_L)], caps) for a in angles]
    frames[0].save(os.path.join(OUT, tag + "_wave.gif"), save_all=True, append_images=frames[1:], duration=90, loop=0)
    res["strip"] = [frame(body, [(arm, a, sh), (arm_L, 0, sh_L)], caps) for a in (0, 55, 110)]
    return res


def quad(tag, im, kind="quad"):
    t = TEMPLATES[kind]; m = alpha_mask(im)
    res = {"iou": round(iou(m, template_mask(kind)), 3)}
    y0, y1 = t["leg_band_y"]
    counts = sorted(runs_in_row(m, y) for y in range(y0, y1, 4))
    res["legs_median"] = counts[len(counts) // 2]
    res["legs_ok"] = res["legs_median"] == 4
    hips = t["legs"]; top = 612; below = 672
    # 다리 — 다리 높이(y 740)에서 **실제 다리 넷의 자리**를 찾고, 거기서 이어진 덩어리를 **배 아래(y ≥ 672)에서만** (09-28).
    #   1차: 가장 가까운 엉덩이에 몽땅 나눠 주기 → 다리 사이로 늘어진 배 조각이 딸려 갔다
    #   2차: 틀의 다리 폭(±38) 안에서만 → 뽑힌 다리가 틀보다 굵어 **발가락 · 다리 옆이 제자리에 남았다**
    #   3차(지금): 폭을 정하지 않고 그 그림의 실제 다리를 따라간다. 배 아래로만 번지므로 배는 안 딸려 온다
    ip = im.load(); mp = m.load()
    runs, cur = [], None
    for x in range(N):
        if mp[x, 740]:
            cur = [x, x] if cur is None else [cur[0], x]
        elif cur is not None:
            if cur[1] - cur[0] >= 10: runs.append(cur)
            cur = None
    region = (0, below, N, N)
    legs, pivots = [], []
    body = im.copy(); bp = body.load()
    for hx, hy in hips:
        if not runs:
            break
        r0, r1 = min(runs, key=lambda r: abs((r[0] + r[1]) / 2 - hx))   # 틀의 엉덩이에 가장 가까운 실제 다리
        cx, w0 = (r0 + r1) // 2, r1 - r0 + 1
        # 다리가 **몸에 붙는 줄** — 발에서 위로 올라가다 폭이 갑자기 넓어지는 곳 (4차 · 09-28).
        # 앞다리는 뒷다리보다 높은 곳에서 몸에 붙는다. 고정 높이(672)로 자르면 앞다리 윗부분이 **보이는 다리인데도**
        # 몸 쪽에 남아, 다리가 돌 때 제자리에 한 벌 더 보였다(가슴 옆에 튀어나온 짙은 조각)
        attach = top
        for y in range(740, top, -1):
            if not mp[cx, y]:
                continue
            a_, b_ = cx, cx
            while a_ > 0 and mp[a_ - 1, y]: a_ -= 1
            while b_ < N - 1 and mp[b_ + 1, y]: b_ += 1
            if b_ - a_ + 1 > 1.6 * w0:
                attach = y + 1; break
        region = (0, attach, N, N)
        pix = grow(reach(m, (cx, 740), region), im, region)
        xs = [x for x, y in pix]
        lx0, lx1 = min(xs), max(xs)
        leg = Image.new("RGBA", im.size); lp = leg.load()
        for x, y in pix:
            lp[x, y] = ip[x, y]; bp[x, y] = (0, 0, 0, 0)
        for y in range(max(top, attach - 40), attach):   # 몸에 붙는 줄 위 40 화소 — 몸 밑에 숨어 돌 때 이음매를 가린다
            for x in range(lx0, lx1 + 1):
                if ip[x, y][3]: lp[x, y] = ip[x, y]
        legs.append(leg); pivots.append((cx, max(top, attach - 20)))
    res["legs_cut"] = len(legs)
    # 꼬리 — 축을 **몸 안쪽 40 화소**로 옮겨 꺾이는 자리가 몸 밑에 숨게 하고, 몸 가장자리는 **60 화소에 걸쳐** 옅게 (09-28).
    # 축을 몸 가장자리에 두면 등 윤곽이 꺾이는 자리에서 끊겨 보였다
    px = t["tail_pivot"][0] + 40; py = t["tail_pivot"][1]
    ty0, ty1 = t["tail_box"][1], 600   # 600 아래는 뒷다리 윗부분이라 뺀다
    tail = Image.new("RGBA", im.size); tp = tail.load(); bi = body.copy().load()
    for y in range(ty0, ty1):
        for x in range(0, px + 40):
            if bi[x, y][3]:
                tp[x, y] = bi[x, y]
            if x < px - 50:
                bp[x, y] = (0, 0, 0, 0)
            elif x < px + 10 and bp[x, y][3]:
                r_, g, b_, al = bp[x, y]
                bp[x, y] = (r_, g, b_, int(al * (x - (px - 50)) / 60))
    res["pieces"] = ([("body", body, None, 1)] + [(f"leg{i}", legs[i], pivots[i], 0) for i in range(len(legs))]
                     + [("tail", tail, (px, py), 0)])
    frames = []
    for k in range(16):
        s = math.sin(2 * math.pi * k / 16)
        th = 14 * s
        parts = [(legs[i], th if i in (0, 3) else -th, pivots[i]) for i in range(len(legs))]
        parts.append((tail, 6 * math.sin(2 * math.pi * k / 8), (px, py)))
        frames.append(frame(body, parts))
    frames[0].save(os.path.join(OUT, tag + "_walk.gif"), save_all=True, append_images=frames[1:], duration=80, loop=0)
    res["strip"] = [frames[i] for i in (0, 4, 12)]
    return res


def main():
    rows = []
    for path in sorted(glob.glob(os.path.join(OUT, "*_cut.png"))):
        tag = os.path.basename(path)[:-8]
        kind = tag.split("_")[0]
        im = Image.open(path).convert("RGBA").resize((N, N))
        r = human(tag, im) if kind == "human" else quad(tag, im, kind)
        r["tag"] = tag; r["kind"] = kind
        rows.append(r)
        ok = r.get("shoulder_ok", r.get("legs_ok"))
        detail = (f"겨드랑이 채움 L{r['gap_fill_L']:.2f}/R{r['gap_fill_R']:.2f}" if kind == "human"
                  else f"다리 {r['legs_median']}개")
        print(f"{tag:16s} 겹침 {r['iou']:.2f} · {detail} · {'통과' if ok else '실패'}")

    # 보고서 — 한 줄에 한 장: 뽑힌 그림 · 움직인 세 장면
    W, H = 256, 256
    sheet = Image.new("RGB", (W * 5 + 40, (H + 44) * len(rows) + 10), (250, 246, 238))
    d = ImageDraw.Draw(sheet)
    for i, r in enumerate(rows):
        y = 10 + i * (H + 44)
        ok = r.get("shoulder_ok", r.get("legs_ok"))
        label = f"{r['tag']}  겹침 {r['iou']:.2f}  " + (
            f"겨드랑이 L{r['gap_fill_L']:.2f} R{r['gap_fill_R']:.2f}" if r["kind"] == "human" else f"다리 {r['legs_median']}개")
        d.text((10, y), label + ("  ✔ 통과" if ok else "  ✘ 실패"), font=SMALL, fill=(40, 120, 60) if ok else (200, 60, 50))
        gen = Image.open(os.path.join(OUT, r["tag"] + ".png")).convert("RGB").resize((W, H))
        sheet.paste(gen, (10, y + 34))
        for j, f in enumerate(r["strip"]):
            sheet.paste(f.resize((W, H)), (10 + (j + 1) * (W + 5) + 20, y + 34))
    sheet.save(os.path.join(OUT, "report.png"))
    json.dump([{k: v for k, v in r.items() if k not in ("strip", "pieces")} for r in rows],
              open(os.path.join(OUT, "result.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("report →", os.path.join(OUT, "report.png"))


if __name__ == "__main__":
    main()
