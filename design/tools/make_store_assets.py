"""Cat Otto (10-01) → launcher icons, Play Store icon, feature graphic.

Sources (made by the team, kept in the repo):
  design/assets/icon_otto_cat.png   1254² opaque — the app icon
  design/assets/logo_otto_cat.png   1774×887 transparent — the 「오또」 cat logo

Outputs:
  android/app/src/main/res/mipmap-*/ic_launcher{,_round,_foreground}.png
  design/store/icon_512.png          Play: 512², 32-bit PNG
  design/store/feature_1024x500.png  Play: the right-hand words of the 09-28 graphic are kept,
                                     only the old felt logo on the left is replaced

Run: py design/tools/make_store_assets.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"
STORE = ROOT / "design/store"
ICON = Image.open(ROOT / "design/assets/icon_otto_cat.png").convert("RGBA")
LOGO = Image.open(ROOT / "design/assets/logo_otto_cat.png").convert("RGBA")

# legacy launcher sizes (48dp) and adaptive foreground sizes (108dp)
DENSITY = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# Adaptive icons show only the middle 66dp of 108 for sure (the mask). The icon fills its own
# square, so it goes in at 72/108 and the launcher background is the icon's own cream —
# whatever the mask shows past the picture is the same colour.
FOREGROUND_FILL = 72 / 108


def round_mask(size: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size - 1, size - 1), fill=255)
    return m


def main() -> None:
    bg = ICON.getpixel((4, 4))[:3]
    print("icon background", "#%02X%02X%02X" % bg)

    for name, k in DENSITY.items():
        d = RES / f"mipmap-{name}"
        n = round(48 * k)
        ICON.resize((n, n), Image.LANCZOS).save(d / "ic_launcher.png")
        r = ICON.resize((n, n), Image.LANCZOS)
        r.putalpha(round_mask(n))
        r.save(d / "ic_launcher_round.png")
        f = round(108 * k)
        inner = round(f * FOREGROUND_FILL)
        fg = Image.new("RGBA", (f, f), (0, 0, 0, 0))
        fg.alpha_composite(ICON.resize((inner, inner), Image.LANCZOS), ((f - inner) // 2, (f - inner) // 2))
        fg.save(d / "ic_launcher_foreground.png")

    ICON.resize((512, 512), Image.LANCZOS).save(STORE / "icon_512.png")

    # feature graphic: keep the words on the right, swap the logo on the left
    feat = Image.open(STORE / "feature_1024x500.png").convert("RGBA")
    cream = feat.getpixel((8, 8))
    ImageDraw.Draw(feat).rectangle((0, 0, 535, 499), fill=cream)
    box = LOGO.getchannel("A").getbbox()
    logo = LOGO.crop(box)
    scale = min(500 / logo.width, 300 / logo.height)
    logo = logo.resize((round(logo.width * scale), round(logo.height * scale)), Image.LANCZOS)
    feat.alpha_composite(logo, ((540 - logo.width) // 2, (500 - logo.height) // 2))
    feat.convert("RGB").save(STORE / "feature_1024x500.png")

    # phone screenshots: the app's own recorded screens (android/app/screens, Robolectric) are
    # 2.05:1 — over Play's 2:1 — so they sit in a 1920×1080 cream frame
    for old in STORE.glob("phone_*.png"):
        old.unlink()
    for i, (name, label) in enumerate(SHOTS, 1):
        im = Image.open(ROOT / "android/app/screens" / f"{name}.png").convert("RGB")
        im = im.resize((1920, round(im.height * 1920 / im.width)), Image.LANCZOS)
        frame = Image.new("RGB", (1920, 1080), cream[:3])
        frame.paste(im, (0, (1080 - im.height) // 2))
        frame.save(STORE / f"phone_{i:02d}_{label}.png")
    print("done")


# which recorded screens go to the store, in order (10-01)
SHOTS = [
    ("world_dino", "story"),                   # 동화 — 아이가 고른 세계
    ("touch_drag", "story_mission"),           # 책 속 미션 — 선물 건네기
    ("diary_board_otto_pick", "diary_redraw"), # 그림일기 — 내 그림 / 오또 그림 고르기
    ("diary_paper_drawing", "diary_page"),     # 그림일기 한 쪽
    ("diary_gift", "diary_shelf"),             # 책장에 꽂기
    ("coop_template_saved", "coop_parent"),    # 부모 — 같이 만들기
]


if __name__ == "__main__":
    main()
