# -*- coding: utf-8 -*-
"""오또 **옆모습** 인형 부위 자르기 (09-29) — 걸을 때 쓴다. 오른쪽을 비스듬히 보는 전신 한 장(ComfyUI · Kontext `side`).

옆모습은 다리 · 팔이 겹쳐 있어 정면처럼 자를 수 없다. 걷기에 필요한 것만 나눈다:
  뒷팔(몸 앞쪽으로 나온 작은 앞발 · 망토 아래 어깨에서 흔든다) · 꼬리 · 뒷다리 · 앞다리 · 몸 ·
  앞팔(**소매째** 어깨에서 흔든다 — 09-29 사용자 「팔도 실제 걷는 것처럼」. 전에는 소매 끝 아래 앞발만 까딱였다)
앞팔이 흔들릴 때 드러나는 몸 자리는 망토 천 · 배 털 무늬를 옆에서 옮겨 메운다.

    python tools/otto_puppet_side.py SRC.png [DEBUG_OUT.png]
결과: res/drawable/otto_side_*.webp (512×512 같은 틀) · 축 좌표 출력 (Kotlin `OttoPuppet` 옆모습에 옮긴다)
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")

PIV = {
    "arm_far": (648, 628), "tail": (385, 770), "leg_far": (575, 790), "leg_near": (475, 792), "arm_near": (452, 548),
}
ORDER = ["arm_far", "tail", "leg_far", "leg_near", "body", "arm_near"]


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
        return g > r + 10 and g >= b - 10

    masks = {k: Image.new("L", (W, H), 0) for k in ORDER}
    m = {k: v.load() for k, v in masks.items()}
    # 앞팔 소매 — 어깨(위) · 소매 앞뒤 가장자리 · 소매 끝(파란 단)까지. 1024 원본에서 격자로 재었다
    sleeve = Image.new("L", (W, H), 0)
    ImageDraw.Draw(sleeve).polygon([(418, 512), (492, 512), (506, 560), (508, 640), (504, 690), (396, 692), (396, 610), (402, 548)], fill=255)
    # 어깨 둥근 뚜껑 — 돌려도 어깨 이음매가 안 보이게
    ImageDraw.Draw(sleeve).ellipse((452 - 40, 548 - 40, 452 + 40, 548 + 40), fill=255)
    # 앞발 — 소매 끝 아래 앞발 윤곽만. 네모로 자르면 뒤의 허벅지 털이 같이 딸려 와 팔과 함께 돌았다
    ImageDraw.Draw(sleeve).polygon([(398, 688), (502, 688), (500, 740), (490, 772), (470, 788), (425, 790), (405, 775), (400, 740)], fill=255)
    sl = sleeve.load()
    for y in range(H):
        for x in range(W):
            if not solid(x, y):
                continue
            if sl[x, y]:
                m["arm_near"][x, y] = 255
            elif x >= 636 and 640 <= y <= 752 and not green(x, y):
                m["arm_far"][x, y] = 255
            elif x < 392 and 600 <= y <= 860 and not green(x, y):
                m["tail"][x, y] = 255
            elif y >= 805:
                m["leg_near" if x < 545 else "leg_far"][x, y] = 255
            else:
                m["body"][x, y] = 255
    # 다리 뿌리는 몸 뒤로 이어 둔다(몸이 덮는다)
    for y in range(725, 805):
        for x in range(W):
            if solid(x, y) and 395 <= x < 640:
                m["leg_near" if x < 545 else "leg_far"][x, y] = 255
    # 꼬리 뿌리도 몸 뒤로
    for y in range(730, 830):
        for x in range(370, 420):
            if solid(x, y) and not green(x, y):
                m["tail"][x, y] = 255

    # 앞팔이 비운 몸 자리를 메운다 — 소매 자리(위)는 망토 천을 왼쪽에서 되풀이해 옮기고(없으면 오른쪽),
    # 앞발 자리(아래)는 오른쪽 배 · 허벅지 털 무늬를 옮긴다. 한 색으로 채우면 가로 줄무늬가 보였다
    body = im.copy()
    bpx = body.load()
    bm = m["body"]
    arm = m["arm_near"]
    for y in range(500, 805):
        for x in range(380, 520):
            if not arm[x, y]:
                continue
            got = None
            if y < 720:     # 소매 자리 + 앞발 뒤 망토 아랫단까지는 망토 천
                sx = x - 60
                while sx >= 0 and arm[sx, y]:
                    sx -= 60
                if sx >= 0 and bpx[sx, y][3] > 200:
                    got = bpx[sx, y]
                elif x + 90 < W and px[x + 90, y][3] > 200:
                    got = px[x + 90, y]
            else:
                sx = min(x + 108, W - 1)
                if px[sx, y][3] > 8:
                    got = px[sx, y]
            if got is not None:
                bpx[x, y] = (got[0], got[1], got[2], 255)
                bm[x, y] = 255

    # 몸 아래 끝(엉덩이)을 다리 위로 조금 늘여 **흐리게** 끝낸다 — 자른 가로줄이 다리가 돌 때 보였다
    for y in range(790, 822):
        k = int(255 * (822 - y) / 32)
        for x in range(395, 640):
            if solid(x, y):
                bm[x, y] = max(bm[x, y], k) if y < 805 else k

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
            over.paste(Image.new("RGBA", (W, H), colors[k] + (110,)), (0, 0), mask)
        dbg.alpha_composite(over)
        d = ImageDraw.Draw(dbg)
        for (x, y) in PIV.values():
            d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=(0, 0, 0, 255))
        dbg.convert("RGB").resize((700, 700)).save(debug)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
