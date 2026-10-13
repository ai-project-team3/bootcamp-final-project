# -*- coding: utf-8 -*-
"""Crayon vs felt on the live picture paths — time and cut-out, before the app turns crayon on (10-13)

The crayon art style (10-07 종훈 · #223) is wired end to end — the app sends `style`, comfy.py has the crayon words —
but the app kept it off (ART_STYLES ready=false) until it was measured as felt was (bench_felt_style.py ·
bench_felt_character.py). Same server functions as /image after the scene LLM: comfy.background for a place,
character_workflow → cut_and_fit for a character. Only the style changes; the seed is shared per item.
Pictures go to eval/image_out/crayon_style/ for the eye.

    py eval/bench_crayon_style.py [--seeds 2] [--comfy http://192.168.0.52:8188]

Uses PC2's ComfyUI (the team server's). Nothing here goes to a child. The output safety check (check.is_safe)
is not run here — it needs the server's key and looks at content, not style.
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

# English scene words as the scene LLM writes them (eval/image_prompt.md · character_prompt.md)
PLACES = {
    "playground": "a sunny playground with a slide, swings and a sandbox",
    "sea": "an underwater world with colorful coral, seaweed and bubbles",
    "zoo": "a zoo with a lion enclosure, trees and a wooden fence",
    "space": "outer space with planets, stars and a small moon",
}
CHARACTERS = [
    ("rabbit wearing a red hat", "quad"),
    ("a blue dinosaur", "quad"),
    ("a father with short hair and glasses, a green sweater", "human"),
    ("child with short curly hair, a yellow raincoat and red boots", "human"),
]
STYLES = ("felt", "crayon")
OUT = ROOT / "eval" / "image_out" / "crayon_style"


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", type=int, default=2)
    ap.add_argument("--comfy")
    a = ap.parse_args()
    if a.comfy:
        settings.comfy_url = a.comfy
    elif "127.0.0.1" in settings.comfy_url:
        settings.comfy_url = "http://192.168.0.52:8188"
    print(f"ComfyUI {settings.comfy_url}")
    OUT.mkdir(parents=True, exist_ok=True)
    bg = {k: [] for k in STYLES}
    ch = {k: {"ok": 0, "fail": 0, "t": []} for k in STYLES}

    for place, scene in PLACES.items():
        for style in STYLES:
            t = time.monotonic()
            png = await comfy.background(scene, style, "story")
            dt = time.monotonic() - t
            bg[style].append(dt)
            (OUT / f"bg_{place}_{style}.png").write_bytes(png)
            print(f"  bg {place:<11}{style:<7}{dt:5.1f}s")

    uploaded: dict[str, str | None] = {}
    for rig in {r for _, r in CHARACTERS}:
        tmpl = character.template(rig)
        uploaded[rig] = await comfy.upload(tmpl, f"otto_mannequin_{rig}.png") if tmpl is not None else None
    for ci, (subject, rig) in enumerate(CHARACTERS):
        for si in range(a.seeds):
            seed = random.Random(ci * 100 + si).randrange(2 ** 31)
            for style in STYLES:
                t = time.monotonic()
                raw = await comfy.run(comfy.character_workflow(subject, rig, seed, uploaded[rig], style), front=True)
                try:
                    cut = character.cut_and_fit(raw)
                    ch[style]["ok"] += 1
                    (OUT / f"ch{ci}_{si}_{style}.png").write_bytes(cut)
                    verdict = "cut"
                except character.CutoutError as e:
                    ch[style]["fail"] += 1
                    verdict = f"FAIL {e}"
                dt = time.monotonic() - t
                ch[style]["t"].append(dt)
                (OUT / f"ch{ci}_{si}_{style}_raw.png").write_bytes(raw)
                print(f"  ch{ci}_{si} {style:<7}{dt:5.1f}s  {verdict}")

    print("\nbackground  mean / max")
    for style, xs in bg.items():
        print(f"  {style:<7}{sum(xs) / len(xs):5.1f}s {max(xs):5.1f}s")
    print("character   cut-out ok / fail · mean / max (cut-out included)")
    for style, v in ch.items():
        print(f"  {style:<7}{v['ok']} / {v['fail']} · {sum(v['t']) / len(v['t']):5.1f}s {max(v['t']):5.1f}s")


if __name__ == "__main__":
    asyncio.run(main())
