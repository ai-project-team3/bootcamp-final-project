# -*- coding: utf-8 -*-
"""같이 만들기 템플릿 그림 (09-29) — 장소 · 직업 · 스포츠 카드, 요소 15개, 고른 이유 3개, 직접 쓰기.

gen_room.py 와 같은 파이프라인 · 같은 화풍(krea2 turbo → BiRefNet 배경 제거)을 그대로 빌려 쓴다.
결과는 인자로 준 폴더에 저장하고, 눈으로 고른 뒤 res/drawable 로 옮긴다 (webp 로 줄여서).

    python tools/gen_coop.py OUT_DIR [이름 ...] [--seeds N]
"""
import os, sys, random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_room import CUT, run, txt2img, bgremove, upload, fetch, get  # noqa: E402

S = (1024, 1024)
JOBS = {
    # 템플릿 카드 셋
    "coop_kind_place": "a cute felt folded treasure map with a dotted path and a coral map pin, ",
    "coop_kind_job": "a cute felt red firefighter helmet next to a small white felt chef hat, ",
    "coop_kind_sport": "a cute felt soccer ball resting beside a small golden felt trophy cup, ",
    # 장소
    "coop_el_home": "a cute small felt house with a coral roof, a round window and a teal door, ",
    "coop_el_school": "a cute small felt school building with a clock on the front and a little flag on top, ",
    "coop_el_park": "a cute felt ferris wheel with colorful pastel cabins, ",
    "coop_el_aquarium": "a cute round felt fish bowl with a smiling orange fish and green seaweed inside, ",
    "coop_el_zoo": "a cute felt giraffe head and neck peeking over a small wooden fence, ",
    # 직업
    "coop_el_firefighter": "a cute felt red firefighter helmet with a golden badge, ",
    "coop_el_doctor": "a cute felt doctor stethoscope in teal with a little red cross badge, ",
    "coop_el_chef": "a cute tall white felt chef hat with a small wooden spoon, ",
    "coop_el_police": "a cute felt navy blue police cap with a golden star badge, ",
    "coop_el_astronaut": "a cute round white felt astronaut helmet with a glossy visor and a small star, ",
    # 스포츠
    "coop_el_soccer": "a cute felt soccer ball with black and white patches, ",
    "coop_el_basketball": "a cute orange felt basketball, ",
    "coop_el_baseball": "a cute white felt baseball with red stitches leaning on a small brown felt glove, ",
    "coop_el_swim": "a cute yellow felt swim ring floating on a small puddle of blue felt water, ",
    "coop_el_taekwondo": "a cute folded white felt taekwondo uniform with a black belt tied on top, ",
    # 직접 쓰기
    "coop_el_custom": "a cute chunky felt pencil in mustard yellow writing a little wavy line, ",
    # 고른 이유
    "coop_why_done": "a cute felt ticket stub in coral with a teal check mark stamp, ",
    "coop_why_soon": "a cute felt desk calendar with a heart circled on one day, ",
    "coop_why_dream": "a cute felt heart in coral with small golden sparkle stars around it, ",
}

if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    seeds = 1
    if "--seeds" in args:
        i = args.index("--seeds")
        seeds = int(args[i + 1])
        args = args[:i] + args[i + 2:]
    get("/system_stats")
    os.makedirs(out, exist_ok=True)
    for name in (args or list(JOBS)):
        for k in range(seeds):
            img = run(txt2img(JOBS[name] + CUT, *S, name, random.randint(1, 2**31)), f"{name}#{k}")
            if not img:
                continue
            cut = run(bgremove(upload(img), name), name + " cut")
            open(os.path.join(out, f"{name}_{k}.png"), "wb").write(fetch(cut or img))
    print("ALL DONE", flush=True)
