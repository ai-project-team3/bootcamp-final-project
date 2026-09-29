# -*- coding: utf-8 -*-
"""자르지 않고 **휘게** 하는 뼈대 — 그물(메시) + 가중치 내보내기 (2026-09-28)

1차(`rig_export.py`)는 부위를 오려 축대로 돌렸다. 실물 폰에서 보니 **어깨가 잘린 자국**이 났다 — 오려 내는 한
어딘가에 이음매가 남는다(사용자: *"잘리는 거 없이 자연스럽게"*).

그래서 Spine · Live2D 가 하는 방식으로 바꾼다:
  그림 한 장을 통째로 쓴다 → 고운 삼각형 그물을 씌운다 → 그물의 점마다 **어느 뼈를 얼마나 따라갈지(가중치)** 를 준다
  → 앱이 뼈 각도대로 점을 옮기고, 그림을 그물에 입혀 그린다(`Canvas.drawVertices`)
어깨 근처 점은 팔을 **조금만**, 팔 쪽으로 갈수록 **많이** 따라가므로 소매와 어깨가 천처럼 늘어나며 휜다. 잘린 곳이 없다.

뼈
  사람형  root(몸) · armR_up(어깨) → armR_fore(팔꿈치) · armL_up → armL_fore
  네발형  root(몸) · leg0~3(엉덩이) · tail1(꼬리 뿌리) → tail2(꼬리 가운데)

내보내는 것  app/src/main/assets/rig/<이름>/
  full.png   캐릭터 한 장(오리지 않음 · 빈 가장자리만 잘라 냄)
  mesh.json  { kind, canvas, image: {file, x, y}, bones: [{name, parent, pivot}], vertices: [[x,y]...],
               weights: [[[뼈 번호, 가중치]...] 점마다], triangles: [[i,j,k]...](뒤 → 앞 순) }

쓰는 법  python tools/rig_mesh_export.py
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig_pose_check as rc

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets", "rig")
S = 0.5                 # 1024 → 512
STEP = 8                # 그물 한 칸 (512 기준 화소)
BLUR = 7                # 가중치 경계를 푸는 정도 — 작으면 휘는 자리에서 꺾이고, 크면 몸통까지 끌려간다


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def field(pixels, size, grow_r=0, blur=BLUR, extra=None):
    """화소 집합 → 0~1 흐린 지도 (512 기준)"""
    m = Image.new("L", size); mp = m.load()
    for x, y in pixels:
        mp[int(x * S), int(y * S)] = 255
    if extra: extra(ImageDraw.Draw(m))
    if grow_r: m = m.filter(ImageFilter.MaxFilter(grow_r * 2 + 1))
    return (m.filter(ImageFilter.GaussianBlur(blur)) if blur else m).load()


def grid(im512):
    """캐릭터를 덮는 격자 — 점 · 삼각형(빈 칸 빼고)"""
    a = im512.split()[3]
    cover = a.point(lambda v: 255 if v > 0 else 0).filter(ImageFilter.MaxFilter(2 * STEP + 1))
    x0, y0, x1, y1 = cover.getbbox()
    x0 -= x0 % STEP; y0 -= y0 % STEP
    cols = (x1 - x0) // STEP + 2; rows = (y1 - y0) // STEP + 2
    verts = [(x0 + c * STEP, y0 + r * STEP) for r in range(rows) for c in range(cols)]
    cp = cover.load(); W, H = cover.size
    inside = lambda x, y: 0 <= x < W and 0 <= y < H and cp[x, y] > 0
    tris = []
    for r in range(rows - 1):
        for c in range(cols - 1):
            i = r * cols + c
            cx, cy = x0 + c * STEP + STEP // 2, y0 + r * STEP + STEP // 2
            if inside(cx, cy) or any(inside(*verts[k]) for k in (i, i + 1, i + cols, i + cols + 1)):
                tris += [[i, i + 1, i + cols], [i + 1, i + cols + 1, i + cols]]
    return verts, tris


def human(im):
    """사람형 — 소매와 맨살 팔을 **다른 뼈**로 (09-28 3차).

    2차는 소매까지 팔 뼈 하나로 휘게 해서, 팔을 들면 소매 아랫부분이 반쯤만 따라가 **겨드랑이에 쐐기 같은 조각**이 늘어졌다.
    소매와 셔츠 몸통은 같은 천이다 — 그걸 쓴다:
      소매 뼈   팔 각도를 **100%** 따라간다(09-28 7차). 45 · 60 · 70% 로 덜 따라가게 했더니 소매 끝단에서
                팔이 **꺾이고 비틀렸다**. 앱이 각도를 섞어 돌리게 바꾼 뒤로는 어깨(몸통과 이어지는 천)만 휘면 된다. 어깨 쪽은 몸통과 같은 천이 늘어나는 것이라 자국이 안 난다. 몸 **앞**에 그린다
      팔 뼈     맨살 팔. 끝까지 올라간다. 몸 **뒤**에 그린다 — 소매 끝단이 팔과 만나는 곳을 덮는다
    소매와 맨살은 **색**으로 가른다 — 손 쪽 살색과 다르면 소매.
    """
    t = rc.TEMPLATES["human"]; m = rc.alpha_mask(im); ip = im.load()
    bones = [{"name": "root", "parent": -1, "pivot": None}]
    per_arm = []
    for side, box, hand, edge in (("R", t["arm_box_R"], t["hand_R"], t["arm_box_R"][0]),
                                  ("L", t["arm_box_L"], t["hand_L"], t["arm_box_L"][2] - 1)):
        lump = rc.grow(rc.reach(m, hand, box), im, box)
        # 뼈대를 **그 그림에서** 잡는다 (09-28 6차 — 사용자: *"팔의 뼈대가 이상하게 잡힌 것 같다"*).
        #   전: 손끝은 마네킹 틀의 좌표, 어깨 축은 팔 뿌리 높이의 한가운데, 팔꿈치는 어깨~틀 손의 절반
        #       → 생성된 팔이 틀보다 길어 팔꿈치가 **소매 끝단 바로 아래**에 잡혔다. 손을 흔들면 소매 끝에서 꺾였다
        #   지금: 손끝 = 팔 덩어리에서 가장 먼 곳 · 어깨 축 = 팔 뿌리의 **위쪽 40%** · 팔꿈치 = 소매 끝단과 손목의 한가운데
        cols = range(edge, edge + 10) if side == "R" else range(edge - 9, edge + 1)
        ys = [y for (x, y) in lump if x in cols]
        top_y, bot_y = min(ys), max(ys); h = bot_y - top_y + 1
        rr = max(20.0, h * 0.55)
        px = edge - rr * 0.25 if side == "R" else edge + rr * 0.25
        py = top_y + 0.4 * h
        d0 = math.dist((px, py), hand); u0 = ((hand[0] - px) / d0, (hand[1] - py) / d0)
        proj = [((x - px) * u0[0] + (y - py) * u0[1], x, y) for (x, y) in lump]
        pmax = max(q for q, _, _ in proj)
        far = [(x, y) for q, x, y in proj if q >= pmax - 12]
        tip_pt = (sum(x for x, _ in far) / len(far) * S, sum(y for _, y in far) / len(far) * S)
        piv = (px * S, py * S)
        L = math.dist(piv, tip_pt); u = ((tip_pt[0] - piv[0]) / L, (tip_pt[1] - piv[1]) / L)
        # ⚠️ 기본값으로 **이 팔의 값을 붙잡아 둔다** — 안 그러면 루프가 끝난 뒤 두 팔 모두 마지막(왼팔) 값을 쓴다
        tt_of = lambda x, y, piv=piv, u=u, L=L: ((x - piv[0]) * u[0] + (y - piv[1]) * u[1]) / L
        # 살색 기준 — 손 쪽(팔 끝 25%)의 평균 색
        tip = [ip[x, y] for (x, y) in lump if tt_of(x * S, y * S) > 0.75 and ip[x, y][3] > 200]
        ref = tuple(sum(c[k] for c in tip) / len(tip) for k in range(3))
        dist = lambda c: sum((c[k] - ref[k]) ** 2 for k in range(3)) ** 0.5
        # 소매 끝단(hem) — 팔 축을 따라가며 **살색 비율이 60% 를 넘는 첫 자리**. 색은 이것 하나에만 쓴다 (09-28 4차).
        # 화소마다 색으로 가르면 펠트 팔의 **그늘진 살**이 소매로 잡혀 45% 만 따라가다 **찢어졌다**.
        # 그래서 끝단을 찾은 뒤에는 **자리로** 가른다 — 끝단보다 어깨 쪽은 소매, 손 쪽은 맨살
        bins = {}
        for (x, y) in lump:
            if ip[x, y][3] < 128: continue
            tb = int(tt_of(x * S, y * S) * 50)
            n, k_ = bins.get(tb, (0, 0)); bins[tb] = (n + 1, k_ + (1 if dist(ip[x, y]) < 70 else 0))
        hem = next((tb / 50 for tb in sorted(bins) if 0 <= tb / 50 < 0.6 and bins[tb][1] / bins[tb][0] >= 0.6), 0.25)
        cls = {}                                   # 512 좌표 → 1 소매 / 2 맨살
        for (x, y) in lump:
            cls[(int(x * S), int(y * S))] = 2 if tt_of(x * S, y * S) >= hem else 1
        print(f"  {side} 팔 — 소매 끝단 t={hem:.2f} (팔 길이의 {hem * 100:.0f}%)")
        wrist = 0.80                              # 손 길이 ≈ 팔 전체의 20%
        t_el = (hem + wrist) / 2                  # 팔꿈치 = 드러난 팔(소매 끝단 ~ 손목)의 한가운데
        elbow = (piv[0] + u[0] * L * t_el, piv[1] + u[1] * L * t_el)
        print(f"     어깨 축 {tuple(round(c) for c in piv)} · 팔꿈치 t={t_el:.2f} {tuple(round(c) for c in elbow)} · 손끝 {tuple(round(c) for c in tip_pt)}")
        sl = len(bones); bones.append({"name": f"sleeve{side}", "parent": 0, "pivot": [round(piv[0], 1), round(piv[1], 1)],
                                       "follow": f"arm{side}_up", "ratio": 1.0})
        up = len(bones); bones.append({"name": f"arm{side}_up", "parent": 0, "pivot": [round(piv[0], 1), round(piv[1], 1)]})
        fo = len(bones); bones.append({"name": f"arm{side}_fore", "parent": up, "pivot": [round(elbow[0], 1), round(elbow[1], 1)]})
        per_arm.append((cls, piv, u, L, hem, sl, up, fo, tt_of, side, t_el))

    ap512 = im.resize((512, 512)).split()[3].load()

    def near_class(cls, v, piv, u, side):
        """점이 팔(소매 1 · 맨살 2)에 속하나 — 속하지 않으면 None (몸통)

        ⚠️ **셔츠 몸통 화소 위의 점은 무조건 몸통**이다 (09-28 5차). 한 칸 안에서 가장 가까운 팔 화소를 찾게만 했더니
           겨드랑이 옆 몸통 점까지 소매로 잡혀, 팔을 들면 **옆구리가 쐐기처럼 끌려 나왔다.**
           비어 있는(투명한) 점만 가까운 팔 화소를 따라간다 — 팔 옆 틈이 같이 움직여 가장자리가 안 뜯긴다
        """
        vx, vy = int(v[0]), int(v[1])
        if (vx, vy) in cls:
            return cls[(vx, vy)]
        if 0 <= vx < 512 and 0 <= vy < 512 and ap512[vx, vy] > 128:
            # 몸통 화소 — 단, 어깨 **윗면**(팔 축보다 머리 쪽)의 둘레는 소매와 같이 접힌다
            cross = (v[0] - piv[0]) * u[1] - (v[1] - piv[1]) * u[0]
            above = cross > 0 if side == "R" else cross < 0
            return 1 if (above and math.dist(v, piv) < 26) else None
        # 투명한 틈의 점 — **더 가까운 쪽**을 따라간다 (09-28 6차). 전에는 가까운 팔 화소가 있으면 무조건 팔을 따라가서,
        # 겨드랑이 틈의 점이 몸통 가장자리까지 끌고 가 **옅은 삼각형**이 생겼다. 몸통이 더 가까우면 그대로 둔다
        best, bd, torso_d = None, 1e9, 1e9
        for dy in range(-STEP, STEP + 1, 2):
            for dx in range(-STEP, STEP + 1, 2):
                qx, qy = vx + dx, vy + dy
                dd = dx * dx + dy * dy
                k = cls.get((qx, qy))
                if k:
                    if dd < bd: best, bd = k, dd
                elif 0 <= qx < 512 and 0 <= qy < 512 and ap512[qx, qy] > 128 and dd < torso_d:
                    torso_d = dd
        return best if bd <= torso_d else None

    def weights(v):
        out, rest = [], 1.0
        for cls, piv, u, L, hem, sl, up, fo, tt_of, side, t_el in per_arm:
            tt = tt_of(*v)
            if tt < -0.35: continue
            k = near_class(cls, v, piv, u, side)
            if k is None: continue
            s0 = smooth(-0.06, 0.10, tt)                 # 몸통에 붙는 곳 — 같은 천이 늘어난다
            if k == 1:
                ws, wa = s0, 0.0
            else:
                a = smooth(hem - 0.02, hem + 0.10, tt)    # 끝단 근처 살은 서서히 — 틈 대신 살이 조금 늘어난다
                wa, ws = a, (1 - a) * s0
            e = smooth(t_el - 0.07, t_el + 0.07, tt)   # 굽는 구간은 좁게 — 위팔 · 아래팔이 각각 단단하게
            out += [[sl, round(ws, 3)], [up, round(wa * (1 - e), 3)], [fo, round(wa * e, 3)]]
            rest -= ws + wa
        return [[0, round(max(0.0, rest), 3)]] + [w for w in out if w[1] > 0.001]
    return bones, weights


def quad(im, kind="quad2"):
    t = rc.TEMPLATES[kind]; m = rc.alpha_mask(im); mp = m.load(); N = rc.N
    bones = [{"name": "root", "parent": -1, "pivot": None}]
    top = 612
    runs, cur = [], None
    for x in range(N):
        if mp[x, 740]:
            cur = [x, x] if cur is None else [cur[0], x]
        elif cur is not None:
            if cur[1] - cur[0] >= 10: runs.append(cur)
            cur = None
    legs = []
    for i, (hx, hy) in enumerate(t["legs"]):
        r0, r1 = min(runs, key=lambda r: abs((r[0] + r[1]) / 2 - hx))
        cx, w0 = (r0 + r1) // 2, r1 - r0 + 1
        attach = top
        for y in range(740, top, -1):
            if not mp[cx, y]: continue
            a_, b_ = cx, cx
            while a_ > 0 and mp[a_ - 1, y]: a_ -= 1
            while b_ < N - 1 and mp[b_ + 1, y]: b_ += 1
            if b_ - a_ + 1 > 1.6 * w0:
                attach = y + 1; break
        lump = rc.grow(rc.reach(m, (cx, 740), (0, attach, N, N)), im, (0, attach, N, N))
        xs = [x for x, y in lump]
        lx0, lx1 = min(xs) * S, max(xs) * S; a512 = attach * S
        piv = (cx * S, a512 - 12)
        # 다리 둘레 — 몸에 붙는 줄 위 18화소까지 넣어 엉덩이가 같이 휘게
        mask = field(lump, (512, 512), grow_r=1, extra=lambda d: d.rectangle([lx0, a512 - 18, lx1, a512], fill=255))
        b = len(bones); bones.append({"name": f"leg{i}", "parent": 0, "pivot": [round(piv[0], 1), round(piv[1], 1)]})
        legs.append((mask, a512, b))
    # 꼬리 — 뿌리(몸 안쪽)에서 끝까지 두 뼈
    tp = t["tail_pivot"]; root_x = (tp[0] + 40) * S; ty = tp[1] * S
    tail_px = [(x, y) for y in range(t["tail_box"][1], 600) for x in range(0, tp[0] + 40) if mp[x, y]]
    tip_x = min(x for x, y in tail_px) * S
    mid_x = (root_x + tip_x) / 2
    tmask = field(tail_px, (512, 512), grow_r=1)
    t1 = len(bones); bones.append({"name": "tail1", "parent": 0, "pivot": [round(root_x, 1), round(ty, 1)]})
    t2 = len(bones); bones.append({"name": "tail2", "parent": t1, "pivot": [round(mid_x, 1), round(ty, 1)]})

    def weights(v):
        out, rest = [], 1.0
        x, y = min(511, max(0, int(v[0]))), min(511, max(0, int(v[1])))
        for mask, a512, b in legs:
            s = mask[x, y] / 255 * smooth(a512 - 22, a512 + 10, v[1])
            if s > 0.01:
                out.append([b, round(s, 3)]); rest -= s
        s = tmask[x, y] / 255 * smooth(root_x + 20, root_x - 30, v[0])
        if s > 0.01:
            e = smooth(mid_x + 18, mid_x - 18, v[0])
            out += [[t1, round(s * (1 - e), 3)], [t2, round(s * e, 3)]]; rest -= s
        return [[0, round(max(0.0, rest), 3)]] + [w for w in out if w[1] > 0.001]
    return bones, weights


def clean_halo(im):
    """배경을 지울 때 남은 **회색 반투명 그림자**를 지운다 (09-28 6차).
    겨드랑이 틈에 이게 남아 있으면 팔을 들 때 늘어나 **옅은 삼각형**이 된다. 색이 있는 가장자리(살 · 셔츠)는 그대로 둔다"""
    px = im.load(); n = 0
    for y in range(im.height):
        for x in range(im.width):
            r_, g, b, a = px[x, y]
            if 0 < a < 220 and max(r_, g, b) - min(r_, g, b) < 28:
                px[x, y] = (r_, g, b, 0); n += 1
    return n


PICK = {"kid": ("human_60_22", "human"), "dino": ("quad2_60_22", "quad2")}

for name, (tag, kind) in PICK.items():
    im = Image.open(os.path.join(rc.OUT, tag + "_cut.png")).convert("RGBA").resize((rc.N, rc.N))
    print(f"{name}: 회색 그림자 가장자리 {clean_halo(im)}화소 지움")
    bones, wfun = human(im) if kind == "human" else quad(im, kind)
    im512 = im.resize((512, 512), Image.LANCZOS)
    verts, tris = grid(im512)
    weights = [wfun(v) for v in verts]
    # 뒤 → 앞: 몸(root) 가중치가 작은 삼각형(팔 · 다리 · 꼬리)을 먼저 그려 몸 뒤로 가게
    # 뒤 → 앞: 팔 · 다리 · 꼬리(움직이는 뼈) → 몸(root) → 소매(앞에서 팔과 만나는 곳을 덮는다)
    kind_of = {i: ("sleeve" if b["name"].startswith("sleeve") else "root" if i == 0 else "limb") for i, b in enumerate(bones)}
    def order(tr):
        g = {"limb": 0.0, "root": 0.0, "sleeve": 0.0}
        for i in tr:
            for b, w in weights[i]: g[kind_of[b]] += w
        return -g["limb"] + 2 * g["sleeve"]
    tris.sort(key=order)
    # 그림은 **자르지 않고 512 캔버스 그대로** — 딱 맞게 자르면 그물 가장자리 삼각형이 그림 밖 좌표를 읽을 때
    # 가장자리 색이 늘어나 번진다(앱의 BitmapShader 가 CLAMP). 둘레가 투명하면 번질 것이 없다
    out = os.path.join(ASSETS, name); os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        os.remove(os.path.join(out, f))          # 1차(오려 돌리기) 조각은 지운다
    im512.save(os.path.join(out, "full.png"), optimize=True)
    json.dump({"kind": kind.rstrip("2"), "canvas": [512, 512], "image": {"file": "full.png", "x": 0, "y": 0},
               "bones": bones, "vertices": [[round(x, 1), round(y, 1)] for x, y in verts],
               "weights": weights, "triangles": tris},
              open(os.path.join(out, "mesh.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    kb = os.path.getsize(os.path.join(out, "mesh.json")) / 1024
    moving = sum(1 for w in weights if len(w) > 1)
    print(f"{name}: 점 {len(verts)} · 삼각형 {len(tris)} · 뼈 {[b['name'] for b in bones]} · 움직이는 점 {moving} · mesh.json {kb:.0f}KB")
