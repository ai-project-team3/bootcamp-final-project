# -*- coding: utf-8 -*-
"""오또 로고 v2 — 새 글꼴 · 폭신한 펠트 패치 글자. 후보 여러 개를 만든다.

    python design/tools/make_logo2.py          # 후보 A~D 모두 → assets/logo_candidates/
글꼴: assets/fonts/ (Google Fonts, OFL)
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets")
FONTS = os.path.join(ASSETS, "fonts")
OUT = os.path.join(ASSETS, "logo_candidates")

CORAL, MUSTARD, TEAL = (232, 96, 76), (242, 178, 50), (79, 175, 152)
CANDIDATES = {
    # (글꼴, 외곽선 글꼴이라 속을 채울지, 크기, 획을 두껍게 할 정도)
    "A_gugi": ("Gugi-Regular.ttf", False, 360, 0.035),
    "C_singleday": ("SingleDay-Regular.ttf", False, 380, 0.03),
    "D_blackhan": ("BlackHanSans-Regular.ttf", False, 360, 0.0),
}


def glyph_mask(ch, font_file, fill_holes, size, thicken=0.0):
    font = ImageFont.truetype(os.path.join(FONTS, font_file), size)
    W = int(size * 1.6)
    m = Image.new("L", (W, W), 0)
    ImageDraw.Draw(m).text((W * 0.12, W * 0.08), ch, font=font, fill=255)
    if fill_holes:   # 외곽선 글꼴 → 선을 굵혀 닫고, 바깥을 칠한 뒤 뒤집어 속을 채운다
        m = m.point(lambda v: 255 if v > 40 else 0).filter(ImageFilter.MaxFilter(9))
        outside = m.copy()
        ImageDraw.floodfill(outside, (1, 1), 128)
        m = outside.point(lambda v: 0 if v == 128 else 255)
    if thicken:
        m = m.filter(ImageFilter.MaxFilter(int(size * thicken) * 2 + 1))
    # 모서리를 둥글게 (펠트는 뾰족하지 않다)
    m = m.filter(ImageFilter.GaussianBlur(size * 0.02)).point(lambda v: 255 if v > 128 else 0)
    return m.crop(m.getbbox())


def dilate(m, r):
    return m.filter(ImageFilter.MaxFilter(r * 2 + 1)) if r > 0 else m


def erode(m, r):
    return m.filter(ImageFilter.MinFilter(r * 2 + 1))


def dashes(size, period=34, on=16):
    """45° 줄무늬 — 테두리 띠에 곱하면 바느질 점선처럼 보인다."""
    p = Image.new("L", size, 0)
    d = ImageDraw.Draw(p)
    for k in range(-size[1], size[0], period):
        d.line([(k, 0), (k + size[1], size[1])], fill=255, width=on)
    return p


def felt_letter(mask, color, tex):
    """단색 + 양모 결 + 볼록한 명암 + 안쪽 바느질."""
    w, h = mask.size
    base = Image.new("RGBA", (w, h), color + (255,))
    t = tex.resize((w, h)).copy()
    t.putalpha(t.getchannel("A").point(lambda v: int(v * 0.55)))
    base.alpha_composite(t)
    # 볼록함: 안쪽으로 갈수록 밝게, 가장자리는 어둡게
    inner = mask.filter(ImageFilter.GaussianBlur(max(w, h) * 0.06))
    light = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    light.putalpha(ImageChops.multiply(inner, Image.new("L", (w, h), 70)))
    edge = ImageChops.subtract(mask, erode(mask, 10)).filter(ImageFilter.GaussianBlur(6))
    dark = Image.new("RGBA", (w, h), (80, 40, 20, 0))
    dark.putalpha(edge.point(lambda v: int(v * 0.18)))
    base.alpha_composite(light)
    base.alpha_composite(dark)
    # 안쪽 바느질
    band = ImageChops.subtract(erode(mask, 16), erode(mask, 19))
    st = ImageChops.multiply(band, dashes((w, h)))
    thread = tuple(min(255, int(c + (255 - c) * 0.55)) for c in color)
    stitch = Image.new("RGBA", (w, h), thread + (0,))
    stitch.putalpha(st.point(lambda v: int(v * 0.9)))
    base.alpha_composite(stitch)
    base.putalpha(mask)
    return base


def patch(mask, color, tex, border=0):
    """글자 한 덩어리 + 그림자. 흰 테두리는 없앴다(2026-09-28). 바느질은 글자색보다 밝은 실."""
    pad = 60
    M = Image.new("L", (mask.width + pad * 2, mask.height + pad * 2), 0)
    M.paste(mask, (pad, pad))
    img = Image.new("RGBA", M.size, (0, 0, 0, 0))
    sh = Image.new("RGBA", M.size, (58, 42, 32, 0))
    sh.putalpha(M.filter(ImageFilter.GaussianBlur(14)).point(lambda v: int(v * 0.32)))
    img.alpha_composite(sh, (5, 14))
    img.alpha_composite(felt_letter(M, color, tex))
    return img


def make(key):
    font_file, holes, size, thick = CANDIDATES[key]
    tex = Image.open(os.path.join(ASSETS, "felt_texture.png")).convert("RGBA")
    o = patch(glyph_mask("오", font_file, holes, size, thick), CORAL, tex).rotate(-6, expand=True, resample=Image.BICUBIC)
    t = patch(glyph_mask("또", font_file, holes, size, thick), MUSTARD, tex).rotate(5, expand=True, resample=Image.BICUBIC)
    W, H = o.width + t.width + 520, max(o.height, t.height) + 380
    cv = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cv.alpha_composite(o, (40, 60))
    cv.alpha_composite(t, (40 + o.width - 110, 20))
    # 새 오또(인사 자세)가 「또」 옆에서 손 흔들기
    pose = os.path.join(ASSETS, "poses", "wave.png")
    if os.path.exists(pose):
        m = Image.open(pose).convert("RGBA")
        m = m.resize((460, 460), Image.LANCZOS)
        cv.alpha_composite(m, (40 + o.width + t.width - 180, max(o.height, t.height) - 330))
    # 부제 — 청록 알약 위에 같은 글꼴
    jua = os.path.join("C:/Android/FinalProject_OTTO", "app", "src", "main", "res", "font", "jua.ttf")
    sub_font = jua if holes else os.path.join(FONTS, font_file)
    font = ImageFont.truetype(sub_font, 92)
    msg = "말로 만드는 그림책"
    d = ImageDraw.Draw(cv)
    tw = d.textlength(msg, font=font)
    px, py = 60, max(o.height, t.height) + 70
    pill = Image.new("L", cv.size, 0)
    ImageDraw.Draw(pill).rounded_rectangle([px, py, px + tw + 120, py + 150], 75, fill=255)
    sh = Image.new("RGBA", cv.size, (58, 42, 32, 0))
    sh.putalpha(pill.filter(ImageFilter.GaussianBlur(12)).point(lambda v: int(v * 0.3)))
    cv.alpha_composite(sh, (0, 10))
    lay = Image.new("RGBA", cv.size, TEAL + (255,))
    tex = Image.open(os.path.join(ASSETS, "felt_texture.png")).convert("RGBA").resize(cv.size)
    lay.alpha_composite(tex)
    lay.putalpha(pill)
    cv.alpha_composite(lay)
    band = ImageChops.subtract(erode(pill, 12), erode(pill, 16))
    st = Image.new("RGBA", cv.size, (255, 255, 255, 0))
    st.putalpha(ImageChops.multiply(band, dashes(cv.size)).point(lambda v: int(v * 0.8)))
    cv.alpha_composite(st)
    d = ImageDraw.Draw(cv)
    d.text((px + 60, py + (150 - font.size) / 2 - 8), msg, font=font, fill=(255, 255, 255, 255))
    cv = cv.crop(cv.getbbox())
    os.makedirs(OUT, exist_ok=True)
    cv.save(os.path.join(OUT, f"logo_{key}.png"))
    return cv


if __name__ == "__main__":
    for k in CANDIDATES:
        print(k, make(k).size)


def make_mark(key="D_blackhan"):
    """작은 자리용 — 글자만 (보호자 화면 왼쪽 위 · 기능 소개)."""
    font_file, holes, size, thick = CANDIDATES[key]
    tex = Image.open(os.path.join(ASSETS, "felt_texture.png")).convert("RGBA")
    o = patch(glyph_mask("오", font_file, holes, size, thick), CORAL, tex).rotate(-6, expand=True, resample=Image.BICUBIC)
    t = patch(glyph_mask("또", font_file, holes, size, thick), MUSTARD, tex).rotate(5, expand=True, resample=Image.BICUBIC)
    cv = Image.new("RGBA", (o.width + t.width, max(o.height, t.height) + 60), (0, 0, 0, 0))
    cv.alpha_composite(o, (0, 40))
    cv.alpha_composite(t, (o.width - 110, 0))
    cv = cv.crop(cv.getbbox())
    cv.save(os.path.join(ASSETS, "logo_otto_v2_mark.png"))
    return cv.size


if __name__ == "__main__" and "mark" in os.sys.argv:
    print("mark", make_mark())
