# -*- coding: utf-8 -*-
"""오또 로고 · 마스코트 얼굴 그림을 만든다.

    python design/tools/make_logo.py
재료: assets/마스코트.png(원본) · 앱의 logo_otto.png(펠트 글자) · assets/felt_texture.png · 앱의 jua.ttf
결과: assets/logo_otto_felt.png · assets/mascot_face.png
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets")
APP_RES = r"C:\Android\FinalProject_OTTO\app\src\main\res"

TEAL, TEAL_DEEP, MUSTARD, MUSTARD_DEEP = (79, 175, 152), (52, 128, 110), (242, 178, 50), (201, 138, 18)


def felt_fill(size, color, texture, alpha=0.9):
    base = Image.new("RGBA", size, color + (255,))
    tex = texture.resize(size) if texture.size != size else texture
    t = tex.copy()
    t.putalpha(t.getchannel("A").point(lambda a: int(a * alpha)))
    base.alpha_composite(t)
    return base


def stitch(draw, pts, color=(255, 255, 255, 170), dash=18, gap=12, width=5):
    """점선 바느질 — pts 를 잇는 선을 점선으로."""
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        L = math.hypot(x2 - x1, y2 - y1)
        n = int(L // (dash + gap))
        for i in range(n + 1):
            a = i * (dash + gap) / L
            b = min((i * (dash + gap) + dash) / L, 1)
            if a >= 1:
                break
            draw.line([(x1 + (x2 - x1) * a, y1 + (y2 - y1) * a), (x1 + (x2 - x1) * b, y1 + (y2 - y1) * b)],
                      fill=color, width=width)


def star_pts(cx, cy, R, r):
    return [(cx + (R if i % 2 == 0 else r) * math.cos(-math.pi / 2 + i * math.pi / 5),
             cy + (R if i % 2 == 0 else r) * math.sin(-math.pi / 2 + i * math.pi / 5)) for i in range(10)]


def felt_star(canvas, cx, cy, R, texture):
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).polygon(star_pts(cx, cy, R, R * 0.5), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(1.2))
    fill = felt_fill(canvas.size, MUSTARD, texture)
    shadow = Image.new("RGBA", canvas.size, (58, 42, 32, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(8)).point(lambda a: int(a * 0.35)))
    canvas.alpha_composite(shadow, (4, 8))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(fill, (0, 0), mask)
    canvas.alpha_composite(layer)
    d = ImageDraw.Draw(canvas)
    p = star_pts(cx, cy, R * 0.72, R * 0.36)
    stitch(d, p + [p[0]], dash=8, gap=7, width=3)


def make_logo():
    tex = Image.open(os.path.join(ASSETS, "felt_texture.png")).convert("RGBA")
    letters = Image.open(os.path.join(APP_RES, "drawable", "logo_otto.png")).convert("RGBA")
    mascot = Image.open(os.path.join(ASSETS, "마스코트.png")).convert("RGBA")
    W, H = 1800, 1100
    cv = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # 1) 「또」 뒤에서 빼꼼 내민 오또 얼굴
    head = mascot.crop((150, 0, 1110, 640))
    hw = 460
    head = head.resize((hw, int(head.height * hw / head.width)), Image.LANCZOS)
    cv.alpha_composite(head, (1080, 60))

    # 2) 펠트 글자 (앱 로고 그대로) + 아래 그림자
    lw = 1320
    lt = letters.resize((lw, int(letters.height * lw / letters.width)), Image.LANCZOS)
    lx, ly = 240, 300
    sh = Image.new("RGBA", lt.size, (58, 42, 32, 0))
    sh.putalpha(lt.getchannel("A").filter(ImageFilter.GaussianBlur(18)).point(lambda a: int(a * 0.35)))
    cv.alpha_composite(sh, (lx + 8, ly + 22))
    cv.alpha_composite(lt, (lx, ly))

    # 3) 청록 펠트 리본 — 「말로 만드는 그림책」
    rx0, rx1, ry0, ry1 = 400, 1400, 900, 1030
    rmask = Image.new("L", (W, H), 0)
    rd = ImageDraw.Draw(rmask)
    rd.rounded_rectangle([rx0, ry0, rx1, ry1], 40, fill=255)
    tails = Image.new("L", (W, H), 0)
    td = ImageDraw.Draw(tails)
    td.polygon([(rx0 + 30, ry0 + 20), (rx0 - 130, ry0 + 20), (rx0 - 80, (ry0 + ry1) / 2 + 10),
                (rx0 - 130, ry1 + 20), (rx0 + 30, ry1 + 20)], fill=255)
    td.polygon([(rx1 - 30, ry0 + 20), (rx1 + 130, ry0 + 20), (rx1 + 80, (ry0 + ry1) / 2 + 10),
                (rx1 + 130, ry1 + 20), (rx1 - 30, ry1 + 20)], fill=255)
    for m, col in ((tails, TEAL_DEEP), (rmask, TEAL)):
        m2 = m.filter(ImageFilter.GaussianBlur(1.5))
        s = Image.new("RGBA", (W, H), (58, 42, 32, 0))
        s.putalpha(m2.filter(ImageFilter.GaussianBlur(10)).point(lambda a: int(a * 0.3)))
        cv.alpha_composite(s, (0, 10))
        lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        lay.paste(felt_fill((W, H), col, tex), (0, 0), m2)
        cv.alpha_composite(lay)
    d = ImageDraw.Draw(cv)
    i = 16
    stitch(d, [(rx0 + i + 20, ry0 + i), (rx1 - i - 20, ry0 + i)])
    stitch(d, [(rx0 + i + 20, ry1 - i), (rx1 - i - 20, ry1 - i)])
    font = ImageFont.truetype(os.path.join(APP_RES, "font", "jua.ttf"), 76)
    msg = "말로 만드는 그림책"
    tw = d.textlength(msg, font=font)
    d.text(((rx0 + rx1 - tw) / 2, ry0 + 22), msg, font=font, fill=(255, 255, 255, 255))

    # 4) 펠트 별 장식
    felt_star(cv, 190, 330, 70, tex)
    felt_star(cv, 1600, 560, 52, tex)
    felt_star(cv, 300, 180, 34, tex)

    cv = cv.crop(cv.getbbox())
    cv.save(os.path.join(ASSETS, "logo_otto_felt.png"))
    return cv.size


def make_face():
    mascot = Image.open(os.path.join(ASSETS, "마스코트.png")).convert("RGBA")
    face = mascot.crop((210, 60, 1050, 900)).resize((512, 512), Image.LANCZOS)
    face.save(os.path.join(ASSETS, "mascot_face.png"))


if __name__ == "__main__":
    print("logo", make_logo())
    make_face()
    print("face ok")
