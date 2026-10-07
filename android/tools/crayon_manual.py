# -*- coding: utf-8 -*-
"""크레용판을 손으로 굽는 18장 (10-07 · #248) — 처음 만든 기록(레시피 · gen_*.py 문장)이 없는 배경 10장 · 소품 8장.

`rebake_style.py crayon --dry-run` 맨 아래 「대상 문장을 못 찾은 세계 그림」이다. 펠트판 그림을 보고 같은 대상 · 같은 구도로
설명을 새로 썼다(그림체 말은 빼고 — `otto_art.py --style crayon` 이 붙인다). 후보는 `otto_art.py` 와 같이 art_out 에 쌓인다.

    python tools/crayon_manual.py            # 18장 모두 후보 2장씩 (이미 res/drawable 에 있는 것은 건너뜀)
    python tools/crayon_manual.py --only bg_  # 배경만
    python tools/otto_art.py pick <이름>_crayon <시드>   # 고른 한 장 → res/drawable
"""
import os, subprocess, sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
DRAWABLE = os.path.join(TOOLS, "..", "app", "src", "main", "res", "drawable")

# 배경 — 1344×768 가로 판 (펠트판과 같은 장소 · 같은 구도)
BG = {
    "bg_aquarium": "a big aquarium tank window seen from inside a dim aquarium hall, colourful fish, coral and seaweed behind the glass, a low wooden rail in front",
    "bg_baseball": "a sunny baseball field, brown dirt diamond with white bases and home plate, green grass, a small dugout at the back, a bat and ball on the grass, rolling hills",
    "bg_basketball": "an indoor school gym basketball court, wooden floor with white lines, a hoop with a backboard on one side, a ball on the floor, a bench by the wall, sunny windows",
    "bg_firestation": "the front of a cosy fire station with a big open garage door, a red fire engine parked inside, a round clock above the door, coiled hoses at the sides, small trees",
    "bg_kitchen": "a bright home kitchen with wooden cupboards, a stove with a steaming pot, pans hanging on the wall, a shelf with chef hats, baskets of fruit and vegetables, a window",
    "bg_police": "a friendly police station office, a desk with a computer and a chair, a notice board with papers on the wall, a big window showing a parked police car outside, a potted plant",
    "bg_school": "a sunny school classroom, small wooden desks and chairs in rows, a green chalkboard, a wall clock, a bookshelf, a globe, big windows with curtains",
    "bg_soccer": "a sunny soccer field with green grass and white lines, a white goal with a net in front, a ball on the grass, a small stand with spectators and floodlights behind, hills",
    "bg_taekwondo": "a taekwondo practice hall, a floor of red and blue foam mats, a wall mirror, belts hanging on wall hooks, a rack of padded kicking targets, big windows",
    "bg_themepark": "a cheerful theme park with a ferris wheel, a small roller coaster track, a carousel with horses, balloons and bunting flags, trees and bright sky",
}
# 오려 낸 소품 — 펠트판과 같은 대상 · 같은 방향
CUT = {
    "prop_candle": "a single pink and white striped birthday candle standing upright with a small yellow flame on top",
    "prop_candle_out": "a single short pink and white striped birthday candle standing upright, the flame blown out, no flame, a tiny wisp of smoke",
    "prop_car": "a cute small round toy car seen from the front, cream body, light blue windows, round headlights",
    "prop_dandelion": "a single dandelion seed head, a white fluffy round puffball on a thin green stem",
    "prop_faucet": "a cute toy water tap faucet, a curved spout turning down with a round handle on top",
    "prop_firetruck": "a cute red toy fire engine seen from the side, a white ladder on top, black wheels",
    "prop_lion": "a cute small friendly lion cub sitting, with a big round colourful mane and a tufted tail",
    "prop_puppy": "a cute small friendly puppy sitting, floppy ears, a little wagging tail",
}


def main():
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1].split(",")
    for kind, table, size in (("bg", BG, "1344x768"), ("cut", CUT, None)):
        for name, text in table.items():
            if only and not any(name.startswith(o) for o in only):
                continue
            if any(os.path.exists(os.path.join(DRAWABLE, f"{name}_crayon.{e}")) for e in ("png", "webp")):
                continue
            cmd = [sys.executable, os.path.join(TOOLS, "otto_art.py"), kind, f"{name}_crayon", text, "--count", "2", "--style", "crayon"]
            if size:
                cmd += ["--size", size]
            print(" ".join(cmd[2:5]), flush=True)
            subprocess.run(cmd, check=False)


if __name__ == "__main__":
    main()
