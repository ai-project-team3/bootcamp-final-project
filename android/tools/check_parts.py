# -*- coding: utf-8 -*-
"""부품을 겹쳐 보고 자리를 맞춘다 (몸 + 머리 + 안경).

`gen_hero_parts.py` 가 만든 조각들은 각자 1024 정사각 안에 들어 있지만,
머리와 안경이 얼굴 자리에 정확히 오지는 않는다. 앱에 넣기 전에 **여기서 겹쳐 보고**
`HeroImage.kt` 의 `HAIR_AT` / `GLASSES_AT` 값을 정한다.

에뮬레이터를 켜지 않아도 되고, 켜는 것보다 정확하다 (트러블슈팅 6-15와 같은 생각).

쓰는 법
  python check_parts.py                       # 기본 좌표로 겹쳐 본다
  python check_parts.py --hair 0.5 0.28 0.62  # 머리 중심 x, 중심 y, 폭 (0~1)
  python check_parts.py --gl 0.5 0.36 0.40    # 안경 중심 x, 중심 y, 폭
결과: build/parts_check.png
"""
import io, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
OUT = os.path.join(HERE, "..", "build")
os.makedirs(OUT, exist_ok=True)

# 기본 좌표 — 겹쳐 보고 고친다
HAIR_AT = [0.500, 0.255, 0.620]      # 중심 x, 중심 y, 폭 (몸 그림 기준 0~1)
GLASSES_AT = [0.500, 0.330, 0.400]

a = sys.argv[1:]
if "--hair" in a:
    i = a.index("--hair"); HAIR_AT = [float(x) for x in a[i + 1:i + 4]]
if "--gl" in a:
    i = a.index("--gl"); GLASSES_AT = [float(x) for x in a[i + 1:i + 4]]


def load(name):
    p = os.path.join(RES, name + ".png")
    return Image.open(p).convert("RGBA") if os.path.exists(p) else None


def place(base, part, at):
    """part 를 base 위 at(중심x, 중심y, 폭) 자리에 얹는다 — 가로세로 비율은 유지"""
    if part is None:
        return base
    W, H = base.size
    cx, cy, w = at
    # 투명 여백을 잘라내 실제 그림만 남긴다 — 그래야 "폭"이 뜻대로 맞는다
    bbox = part.getbbox()
    if bbox:
        part = part.crop(bbox)
    tw = int(W * w)
    th = max(1, round(tw * part.height / part.width))
    part = part.resize((tw, th), Image.LANCZOS)
    x = int(W * cx - tw / 2)
    y = int(H * cy - th / 2)
    out = base.copy()
    out.alpha_composite(part, (x, y))
    return out


rows = []
for hair in ["short", "long", "tied"]:
    row = []
    for gl in ["none", "round", "square"]:
        body = load("body_blue_pants")
        if body is None:
            print("몸 그림이 아직 없다 — 생성이 끝나면 다시 돌린다")
            sys.exit(0)
        im = place(body, load("hair_" + hair), HAIR_AT)
        if gl != "none":
            im = place(im, load("gl_" + gl), GLASSES_AT)
        bg = Image.new("RGBA", im.size, (255, 244, 225, 255))
        bg.alpha_composite(im)
        row.append(bg.convert("RGB").resize((330, 330)))
    rows.append(row)

sheet = Image.new("RGB", (330 * 3, 330 * len(rows)), (255, 244, 225))
for r, row in enumerate(rows):
    for c, im in enumerate(row):
        sheet.paste(im, (c * 330, r * 330))
dest = os.path.join(OUT, "parts_check.png")
sheet.save(dest)
print("머리 자리:", HAIR_AT)
print("안경 자리:", GLASSES_AT)
print("줄 = 짧아 / 길어 / 묶었어,  칸 = 안경 없음 / 동글 / 네모")
print("->", dest)
