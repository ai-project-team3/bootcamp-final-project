# -*- coding: utf-8 -*-
"""Live doll in wool felt instead of cut paper — does it still cut out, and what does it cost? (10-05)

10-05 device: 「캐릭터 만들기 퀄리티 아직 너무 이상함. 양모 펠트 적용 안된거지?」 — the live doll is still cut paper
(backend/app/image/comfy.py CHAR_STYLE) while the app is felt. Same server path as /image kind=character after
the scene LLM (template mannequin img2img → cut_and_fit), only the style words change. Counts cut-out failures
and time; pictures go to eval/image_out/felt_character/ for the eye.

    py eval/bench_felt_character.py [--seeds 2]
"""
from __future__ import annotations

import argparse
import asyncio
import random
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.config import settings  # noqa: E402
from app.image import character, comfy  # noqa: E402

if "127.0.0.1" in settings.comfy_url:
    settings.comfy_url = "http://192.168.0.52:8188"

# what the scene LLM made of real answers (10-05 trace) and two everyday ones
SUBJECTS = [
    "child wearing a very funny tall hat, a big puffy balloon dress and sunglasses",
    "child with short curly hair, a yellow raincoat and red boots",
    "child with long braided hair, a blue dinosaur hoodie",
    "child with a crown, a sparkly purple cape",
]
FELT = (", soft wool felt and fabric craft 3D children's picture book character, visible felt fibers and stitched "
        "edges, cute rounded shapes, warm pastel colors, isolated on plain pure white background, no shadow, no text")
STYLES = {"cutpaper": comfy.CHAR_STYLE, "felt": FELT}
OUT = ROOT / "eval" / "image_out" / "felt_character"


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", type=int, default=2)
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    tmpl = character.template("human")
    uploaded = await comfy.upload(tmpl, "otto_mannequin_human.png") if tmpl is not None else None
    stats = {k: {"ok": 0, "fail": 0, "t": []} for k in STYLES}
    for si, subject in enumerate(SUBJECTS):
        for seed_i in range(a.seeds):
            seed = random.Random(si * 100 + seed_i).randrange(2 ** 31)
            for name, style in STYLES.items():
                comfy.CHAR_STYLE = style            # character_workflow reads the module constant
                t = time.monotonic()
                raw = await comfy.run(comfy.character_workflow(subject, "human", seed, uploaded), front=True)
                dt = time.monotonic() - t
                stats[name]["t"].append(dt)
                (OUT / f"{si}_{seed_i}_{name}_raw.png").write_bytes(raw)
                try:
                    cut = character.cut_and_fit(raw)
                    (OUT / f"{si}_{seed_i}_{name}.png").write_bytes(cut)
                    stats[name]["ok"] += 1
                    res = "ok"
                except character.CutoutError as e:
                    stats[name]["fail"] += 1
                    res = f"cut-out failed: {e}"
                print(f"  {si} seed{seed_i} {name:<9} {dt:4.1f}s  {res}")
    for name, s in stats.items():
        ts = sorted(s["t"])
        print(f"{name:<9} cut-out ok {s['ok']}/{s['ok'] + s['fail']} · p50 {ts[len(ts) // 2]:.1f}s")


if __name__ == "__main__":
    asyncio.run(main())
