# -*- coding: utf-8 -*-
"""배경 조각 굽기 (#97 · docs/배경_조각_목록.md §3) — gen_coop.py 와 같은 파이프라인(krea2 turbo → BiRefNet).

    python tools/gen_kit.py OUT_DIR [묶음 ...] [--seeds N]

묶음: common · park (1순위). 결과는 OUT_DIR/kit_{묶음}_{조각}_{후보}.png — 눈으로 고른 뒤 넘긴다.
"""
import os, sys, random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_room import CUT, run, txt2img, bgremove, upload, fetch, get  # noqa: E402

KITS = {
    # 3-0 공용
    "common": {
        "sun": "smiling yellow sun",
        "cloud": "fluffy white cloud",
        "round_tree": "round green tree",
        "bush": "round green bush",
        "tulip": "red tulip flower",
        "daisy": "white daisy flower",
        "stone": "round grey stone",
        "grass": "tuft of green grass",
        "butterfly": "colorful butterfly",
    },
    # 3-4 공원 · 놀이터
    "park": {
        "slide": "playground slide",
        "swing": "playground swing set",
        "seesaw": "seesaw",
        "sandbox": "sandbox with a bucket",
        "bench": "park bench",
        "street_lamp": "old street lamp",
        "ball": "red and white ball",
        "balloons": "bunch of balloons",
        "kite": "diamond kite",
        "tree_row": "row of round trees",
    },
}

if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    seeds = 3
    if "--seeds" in args:
        i = args.index("--seeds"); seeds = int(args[i + 1]); args = args[:i] + args[i + 2:]
    get("/system_stats")
    os.makedirs(out, exist_ok=True)
    for kit in (args or list(KITS)):
        for key, phrase in KITS[kit].items():
            name = f"kit_{kit}_{key}"
            for k in range(seeds):
                path = os.path.join(out, f"{name}_{k}.png")
                if os.path.exists(path):
                    continue
                img = run(txt2img(f"a cute felt {phrase}, " + CUT, 1024, 1024, name, random.randint(1, 2**31)), f"{name}#{k}")
                if not img:
                    continue
                cut = run(bgremove(upload(img), name), name + " cut")
                open(path, "wb").write(fetch(cut or img))
    print("ALL DONE", flush=True)
