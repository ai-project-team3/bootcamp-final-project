# -*- coding: utf-8 -*-
"""기존 마스코트 인형 미리보기 (09-29) — otto_m_*.webp 를 관절로 돌려 GIF 로. 앱의 mascotPose 와 같은 식.

    python tools/otto_mascot_preview.py OUT.gif [idle,wave,point,jump,spin,dance,giggle,hooray]
"""
import math
import os
import sys

from PIL import Image

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
S = 512
PIV = {"tail": (0.3359, 0.7773), "head": (0.5, 0.5859), "arm_l": (0.3359, 0.6211), "arm_r": (0.6875, 0.6211)}
ORDER = ["tail", "body", "head", "arm_l", "arm_r"]
parts = {k: Image.open(os.path.join(RES, f"otto_m_{k}.webp")).convert("RGBA") for k in ORDER}


def pose(motion, t):
    """(각도 dict · 몸 위아래 px · 몸 기울기 · 좌우 폭). 각도 양수 = 시계 방향.
    왼팔은 시계 방향이면 앞발이 올라가고, 오른팔은 반시계(음수)면 올라간다."""
    s = math.sin
    a = {"tail": 10 * s(t * 3), "head": 2.5 * s(t * 1.6), "arm_l": 3 * s(t * 2), "arm_r": -3 * s(t * 2)}
    bob, tilt, sx = 2 * s(t * 2), 0.0, 1.0
    if motion == "wave":
        a["arm_r"] = -18 + 22 * s(t * 9); a["head"] = -5; a["tail"] = 14 * s(t * 4)
    elif motion == "point":
        a["arm_r"] = -28 + 3 * s(t * 3); a["head"] = -6
    elif motion == "hooray":
        a["arm_l"] = 28 + 5 * s(t * 8); a["arm_r"] = -28 - 5 * s(t * 8); bob = -6 * abs(s(t * 4)); a["head"] = 3 * s(t * 8)
    elif motion == "jump":
        h = abs(s(t * 5)); bob = -44 * h
        a["arm_l"] = 30 * h; a["arm_r"] = -30 * h; a["tail"] = 20 * s(t * 10); a["head"] = -4 * h
    elif motion == "spin":
        sx = math.cos(t * 2 * math.pi / 1.1); bob = -8 * abs(s(t * 3))
    elif motion == "dance":
        k = s(t * 7)
        a["arm_l"] = 25 * max(k, 0) - 10 * max(-k, 0); a["arm_r"] = -25 * max(-k, 0) + 10 * max(k, 0)
        tilt = 8 * k; bob = -5 * abs(k); a["head"] = -6 * k; a["tail"] = 18 * k
    elif motion == "giggle":
        a["arm_l"] = -14; a["arm_r"] = 14
        tilt = 4 * s(t * 40); a["head"] = 8 + 3 * s(t * 30); bob = -2 * abs(s(t * 20)); a["tail"] = 25 * s(t * 12)
    elif motion == "nod":
        a["head"] = 0; bob = 0
    return a, bob, tilt, sx


def frame(motion, t):
    a, bob, tilt, sx = pose(motion, t)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for k in ORDER:
        im = parts[k]
        if a.get(k):
            im = im.rotate(-a[k], resample=Image.BICUBIC, center=(PIV[k][0] * S, PIV[k][1] * S))
        layer.alpha_composite(im)
    if abs(sx) < 0.999:
        w = max(1, int(S * abs(sx)))
        sq = layer.resize((w, S))
        if sx < 0:
            sq = sq.transpose(Image.FLIP_LEFT_RIGHT)
        layer = Image.new("RGBA", (S, S), (0, 0, 0, 0)); layer.alpha_composite(sq, ((S - w) // 2, 0))
    if tilt:
        layer = layer.rotate(-tilt, resample=Image.BICUBIC, center=(S / 2, S * 0.95))
    out = Image.new("RGBA", (S, S + 50), (244, 239, 230, 255))
    out.alpha_composite(layer, (0, int(46 + bob)))
    return out.convert("RGB")


if __name__ == "__main__":
    out = sys.argv[1]
    ms = sys.argv[2].split(",") if len(sys.argv) > 2 else ["idle", "wave", "point", "hooray", "jump", "spin", "dance", "giggle"]
    frames = [frame(m, i / 15.0).resize((256, 281)) for m in ms for i in range(30)]
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=60, loop=0)
    strip = Image.new("RGB", (256 * len(ms), 281), "white")
    for j, m in enumerate(ms):
        strip.paste(frame(m, 0.35).resize((256, 281)), (j * 256, 0))
    strip.save(out.replace(".gif", "_strip.png"))
    print("ok", len(frames))
