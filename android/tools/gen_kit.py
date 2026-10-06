# -*- coding: utf-8 -*-
"""배경 조각 굽기 (#97 · docs/배경_조각_목록.md §3) — gen_coop.py 와 같은 파이프라인(krea2 turbo → BiRefNet).

    python tools/gen_kit.py OUT_DIR [묶음 ...] [--seeds N] [--only 조각,조각]

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
        "round_tree": "round green tree with a short brown trunk",   # 줄기가 없으면 덤불과 같아졌다
        "bush": "round green bush",
        "tulip": "red tulip flower",
        "daisy": "white daisy flower",
        "stone": "plain smooth round grey stone, no spots",       # 색 점이 박혀 알처럼 보였다
        "grass": "tuft of green grass",
        "butterfly": "colorful butterfly",
    },
    # 3-4 공원 · 놀이터
    "park": {
        "slide": "playground slide",
        "swing": "playground swing set",
        "seesaw": "seesaw",
        "sandbox": "low square wooden sandbox filled with sand and a small bucket, no roof",   # 지붕 달린 가판대가 나왔다
        "bench": "park bench",
        # 「old street lamp」는 등 머리만 크게 나와 공원 한가운데 거대한 등이 됐다(#109 · 10-05 조장) — 기둥 · 받침까지, 세로 판에
        "street_lamp": "old street lamp standing on a long thin straight pole with a small round base on the ground, "
                       "the whole lamp from base to small lamp head, the pole is much taller than the lamp head",
        "ball": "red and white ball",
        "balloons": "bunch of balloons",
        "kite": "diamond kite",
        "tree_row": "row of round trees",
    },
    # 3-3 공룡 나라 — 2순위 첫째 (#97 조장 10-05 순서: 공룡 → 우주 → 바닷속). 목록 §3-3 문구 그대로, 「먼띠 · 랜드마크」 큰 것은 세로 판
    "dino": {
        # 「small smoking volcano」는 연기 없는 전등갓 모양이 나왔다 — 산 모양과 연기를 적어 준다
        "volcano": "cone-shaped brown volcano mountain with a dark red crater and a small white puff of smoke rising from its top",
        "palm_tree": "palm tree",
        "waterfall": "little waterfall over rocks",
        "jungle": "clump of jungle trees",
        # 「green fern plant」는 화분에 심긴 채 나왔다
        "fern": "clump of green fern fronds growing straight out of the ground, no pot, no soil",
        "big_leaf": "big tropical leaf",
        "egg": "spotted dinosaur egg",
        "nest": "twig nest",
        # 「three-toed footprint」는 사람 발 인형이 나왔다 — 바닥에 납작한 공룡 발자국 모양으로
        "footprint": "flat brown dinosaur footprint shape with three big toes, top view, a flat cut-out shape lying on the ground",
        "hibiscus": "red hibiscus flower",
        "log": "fallen log",
        "rainbow": "rainbow arch",
    },
    # 3-1 우주 — 2순위 둘째 (#97). 목록 §3-1 문구 그대로
    "space": {
        "moon": "yellow crescent moon",
        "ring_planet": "orange planet with a ring",
        "small_planet": "small round blue planet",
        "star": "yellow five-pointed star",
        "shooting_star": "shooting star with a tail",
        "rocket": "red and white toy rocket",
        "dome": "little round dome house",
        "rock_hill": "lavender rocky hill",
        "moon_rock": "lavender moon rock",
        "crystal": "glowing purple crystal cluster",
        "crater": "shallow round crater",
        "flag": "little flag on a pole",
    },
    # 3-2 바닷속 — 2순위 셋째 (#97). 목록 §3-2 문구 그대로
    "sea": {
        "jellyfish": "pink jellyfish",
        # 「clear water bubble」는 색 조각 박힌 펠트 공이 나왔다 — 속이 비친 동그란 방울로
        "bubble": "single round transparent soap-bubble-like water bubble, pale see-through light blue with a small white shine highlight, hollow and empty inside",
        "yellow_fish": "small yellow fish",
        "clownfish": "orange striped clownfish",
        "pink_coral": "pink branching coral",
        "fan_coral": "orange fan coral",
        "seaweed": "tall green seaweed",
        "chest": "small wooden treasure chest",
        "clam": "open clam shell with a pearl",
        "scallop": "pink scallop shell",
        "starfish": "orange starfish",
        "sea_rock": "mossy blue-grey sea rock",
        "reef": "blue-grey rocky reef",
    },
    # 3-5 숲 · 시골 — 지금은 「살아 있는 배경」의 새만 (§6-3 · 공원 무대에도 들른다). 나머지 10종은 아직
    # 3-6 실내 — 3순위 (#97). 하늘 자리가 벽 — 창문 · 액자 · 시계는 벽에 걸린다
    "indoor": {
        "window": "window with curtains",
        "frame": "small picture frame with a flower painting",
        "clock": "round wall clock",
        "sofa": "small sofa",
        "bed": "small bed with a quilt",
        "bookshelf": "bookshelf with books",
        "table": "little wooden table",
        "toy_box": "toy box",
        "teddy": "teddy bear",
        "blocks": "stack of toy blocks",
        "potted_plant": "potted plant",
        "rug": "round striped rug",
    },
    # 3-7 눈 나라 — 5순위 (#97)
    "snow": {
        "snowflake": "white snowflake",
        "snowman": "snowman with a red scarf",
        "igloo": "igloo",
        "snow_pine": "snow-covered pine tree",
        "snow_mountain": "snowy mountain",
        "sled": "wooden sled",
        "snowballs": "pile of snowballs",
        "ice_rock": "light blue ice rock",
        "snow_bush": "snow-covered bush",
        "cabin": "snowy cabin with a chimney",
    },
    # 3-8 바닷가 — 5순위 (#97)
    "beach": {
        "umbrella": "striped beach umbrella",
        "sandcastle": "sandcastle",
        "bucket": "beach bucket and spade",
        "beach_ball": "striped beach ball",
        "swim_ring": "swim ring",
        "lighthouse": "red and white lighthouse",
        "sailboat": "little sailboat",
        # 「white seagull」은 앉은 갈매기 인형만 나왔다 — 하늘채움이라 나는 모습으로
        "seagull": "white seagull flying with both wings spread wide, side view, gliding in the air",
    },
    "forest": {
        # 3-5 숲 · 시골 — 4순위 (#97). 새(bird · bird_fly)는 10-05 살아 있는 배경 때 먼저 구웠다
        "cottage": "small cottage with a red roof",
        "pine": "green pine tree",
        "mountain": "rounded green mountain",
        "mushroom": "red spotted mushroom",
        "stump": "tree stump",
        "fence": "short wooden fence",
        "carrots": "carrots growing in soil",
        "apple_tree": "apple tree with red apples",
        "pond": "small round pond",
        "sunflower": "tall sunflower",
        # 목록 문구 「little blue bird」를 두 자세로 — 앉은 새와 나는 새. 옆모습 · 오른쪽을 보게(앱이 가는 쪽으로 뒤집는다)
        "bird": "little round blue bird sitting, side view facing right, wings folded, small orange beak and two tiny feet",
        "bird_fly": "little round blue bird flying, side view facing right, both wings spread wide open upward, small orange beak",
    },
}

# 세로로 긴 조각은 세로 판에 굽는다 — 정사각 판에서는 위아래가 잘렸다
SIZES = {
    "street_lamp": (768, 1344), "palm_tree": (768, 1344), "waterfall": (768, 1344), "rainbow": (1344, 768), "log": (1344, 768),
    "rocket": (768, 1344), "flag": (768, 1344), "rock_hill": (1344, 768), "crater": (1344, 768), "shooting_star": (1344, 768),
    "seaweed": (768, 1344), "reef": (1344, 768),
    "bookshelf": (768, 1344), "rug": (1344, 768), "bed": (1344, 768),
    "snow_pine": (768, 1344), "snow_mountain": (1344, 768), "sled": (1344, 768),
    "umbrella": (768, 1344), "lighthouse": (768, 1344),
    "pine": (768, 1344), "mountain": (1344, 768), "fence": (1344, 768), "sunflower": (768, 1344), "pond": (1344, 768),
}

if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    seeds = 3
    if "--seeds" in args:
        i = args.index("--seeds"); seeds = int(args[i + 1]); args = args[:i] + args[i + 2:]
    only = None
    if "--only" in args:
        i = args.index("--only"); only = set(args[i + 1].split(",")); args = args[:i] + args[i + 2:]
    get("/system_stats")
    os.makedirs(out, exist_ok=True)
    for kit in (args or list(KITS)):
        for key, phrase in KITS[kit].items():
            if only and key not in only:
                continue
            name = f"kit_{kit}_{key}"
            for k in range(seeds):
                path = os.path.join(out, f"{name}_{k}.png")
                if os.path.exists(path):
                    continue
                img = run(txt2img(f"a cute felt {phrase}, " + CUT, *SIZES.get(key, (1024, 1024)), name, random.randint(1, 2**31)), f"{name}#{k}")
                if not img:
                    continue
                cut = run(bgremove(upload(img), name), name + " cut")
                open(path, "wb").write(fetch(cut or img))
    print("ALL DONE", flush=True)
