# -*- coding: utf-8 -*-
"""ComfyUI 가 만든 오또 자세 그림을 design/assets/poses/ 로 모으고, 나레이션 칸용 얼굴을 따낸다.

    python design/tools/collect_poses.py
각 자세는 가장 최근 번호의 파일을 쓴다(다시 뽑으면 새 번호가 생긴다).
"""
import glob
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "assets", "poses")
SRC = r"D:\ComfyUI\ComfyUI\output\otto_pose"
NAMES = ["wave", "phone", "point", "walk", "talk", "listen", "think", "yawn", "call"]
FACES = ["talk", "listen", "think"]          # 나레이션 칸 얼굴 = 말하기 · 귀 쫑긋 · 갸웃


# 여러 장 뽑았을 때 눈으로 보고 고른 것. 여기에 없으면 가장 최근 번호.
PICK = {"think": "think_00002_.png", "yawn": "yawn_00002_.png", "listen": "listen_00003_.png",
        "call": "call_00004_.png"}


def latest(name):
    if name in PICK and os.path.exists(os.path.join(SRC, PICK[name])):
        return os.path.join(SRC, PICK[name])
    files = sorted(glob.glob(os.path.join(SRC, f"{name}_*.png")))
    return files[-1] if files else None


def trim(im, pad=0.04):
    im = im.crop(im.getbbox())
    s = int(max(im.size) * (1 + pad * 2))
    c = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    c.alpha_composite(im, ((s - im.width) // 2, (s - im.height) // 2))
    return c.resize((640, 640), Image.LANCZOS)


def face(im):
    """머리 = 몸 위쪽. 위 45% 에서 가장 넓은 가로 범위를 머리로 보고 정사각형으로 자른다."""
    im = im.crop(im.getbbox())
    a = im.getchannel("A")
    top = a.crop((0, 0, im.width, int(im.height * 0.5)))
    bx0, _, bx1, _ = top.getbbox()
    side = int(min(im.height * 0.74, im.width, (bx1 - bx0) * 1.25))
    cx = (bx0 + bx1) // 2
    x0 = max(0, min(im.width - side, cx - side // 2))
    return im.crop((x0, 0, x0 + side, side)).resize((512, 512), Image.LANCZOS)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for n in NAMES:
        f = latest(n)
        if not f:
            print("없음:", n)
            continue
        im = Image.open(f).convert("RGBA")
        trim(im).save(os.path.join(OUT, f"{n}.png"))
        if n in FACES:
            face(im).save(os.path.join(OUT, f"face_{n}.png"))
        print(n, "←", os.path.basename(f))
