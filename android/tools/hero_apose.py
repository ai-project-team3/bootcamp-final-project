# -*- coding: utf-8 -*-
"""주인공 27장을 **팔 벌린 자세(A-포즈)** 로 다시 만든다 (2026-09-28)

왜 — 뼈대를 붙여 팔을 움직이면, 팔을 몸에 붙이고 선 그림은 겨드랑이 아래 팔이 셔츠에 묻혀 있어
들었을 때 소매만 뻗고 팔이 짧은 토막처럼 보였다(사용자: *"팔 이상해"* 여러 번). 마네킹 틀로 A-포즈를 뽑은
시연용 아이는 잘 움직였다(docs/캐릭터_생성_규격.md §8). 그래서 그림 자세를 바꾼다.

어떻게 — 얼굴 · 옷을 지키려고 **새로 뽑지 않는다**:
  1. 앱의 뼈대로 팔만 바깥으로 32° 벌린 그림을 만든다   (RigBuilderTest.A포즈_그림을_뽑는다 → build/rig_auto/apose/)
  2. ComfyUI Krea2 img2img 로 **약하게**(세기 0.3) 다듬어 소매 · 어깨 이음매를 없앤다
  3. 배경을 지운다(birefnet) → 640 으로 → build/rig_auto/apose_out/
앱의 그림(res/drawable)은 이 도구가 바꾸지 않는다 — 확인한 뒤 옮긴다.

쓰는 법  python tools/hero_apose.py [이름 ...]      (ComfyUI 가 켜져 있어야 한다)
"""
import io, os, sys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig_pose_gen as g

SRC = os.path.join(HERE, "..", "app", "build", "rig_auto", "apose")
OUT = os.path.join(HERE, "..", "app", "build", "rig_auto", "apose_out")
os.makedirs(OUT, exist_ok=True)
DENOISE = 0.3

SHIRT = {"red": "red", "blue": "blue", "yellow": "yellow"}
BOTTOM = {"pants": "long blue denim pants", "shorts": "blue denim shorts", "skirt": "blue denim skirt"}
HAIR = {"": "short black hair", "long": "long black hair", "tied": "black hair tied up"}


def prompt_of(name):
    p = name.split("_")                     # body_red_pants[_long]
    shirt, bottom = p[1], p[2]
    hair = p[3] if len(p) > 3 else ""
    return (f"a cute 5 year old child puppet doll standing, front view, full body, {HAIR[hair]}, "
            f"{SHIRT[shirt]} short sleeve t-shirt with a small green dinosaur, {BOTTOM[bottom]}, "
            "both arms held diagonally downward away from the body in an A-pose, clear empty gap between each arm and the body, "
            "isolated on plain pure white background, no shadow, " + g.STYLE)


def heal(name):
    im = Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")
    bg = Image.new("RGBA", im.size, (255, 255, 255, 255)); bg.alpha_composite(im)
    bg = bg.convert("RGB").resize((1024, 1024), Image.LANCZOS)
    buf = io.BytesIO(); bg.save(buf, "PNG")
    up = g.upload(buf.getvalue(), f"apose_{name}.png")
    img = g.run(g.img2img(prompt_of(name), up, 11, DENOISE, "apose_" + name), name)
    if not img: return False
    cut = g.run(g.bgremove(g.upload(g.fetch(img), f"apose_{name}_h.png"), "apose_" + name), name + ":cut")
    if not cut: return False
    Image.open(io.BytesIO(g.fetch(cut))).convert("RGBA").resize((640, 640), Image.LANCZOS).save(os.path.join(OUT, name + ".png"))
    return True


if __name__ == "__main__":
    names = sys.argv[1:] or sorted(f[:-4] for f in os.listdir(SRC) if f.endswith(".png"))
    for i, n in enumerate(names):
        if os.path.exists(os.path.join(OUT, n + ".png")) and "--again" not in sys.argv:
            continue
        print(f"[{i + 1}/{len(names)}]", n, "→", "ok" if heal(n) else "실패", flush=True)
