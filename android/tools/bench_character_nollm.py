# -*- coding: utf-8 -*-
"""서버 캐릭터 그림 속도 측정 — **LLM 없이** (09-30).

OpenAI 크레딧이 없을 때도 서버와 **같은 코드**로 그림을 뽑아 잰다: 서버의 `comfy.character_workflow`
(SDXL + Lightning 8 · 회색 마네킹 img2img) → `comfy.run` → `character.cut_and_fit`(640 · 발 93%).
빠지는 것은 서버의 앞뒤 두 단계 — 아이 말 → 영어 묘사 · 몸 종류(LLM) · 결과 안전 검사(OpenAI). 영어 묘사 · 몸 종류는 여기서 넣는다.

    backend/.venv/Scripts/python.exe tools/bench_character_nollm.py OUT_DIR [--set basic|hard|all]
결과: OUT_DIR/<몸종류>__<번호>_<이름>.png · OUT_DIR/bench.tsv (그림 초 · 오려 내기 초)
"""
import asyncio
import os
import random
import sys
import time

sys.path.insert(0, r"C:\dev\bootcamp-final-project\backend")
from app.image import character, comfy  # noqa: E402  서버 코드 그대로

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_rig_corpus import HARD, SUBJECTS  # noqa: E402  같은 묘사 목록


async def main(out, which):
    sets = {"basic": [SUBJECTS], "hard": [HARD], "all": [SUBJECTS, HARD]}[which]
    os.makedirs(out, exist_ok=True)
    uploaded = {}
    rows = []
    n = 0
    for subjects in sets:
        for rig, subs in subjects.items():
            for subject in subs:
                tmpl = character.template(rig)
                if tmpl is not None and rig not in uploaded:
                    uploaded[rig] = await comfy.upload(tmpl, f"otto_mannequin_{rig}.png")
                t0 = time.monotonic()
                try:
                    raw = await comfy.run(comfy.character_workflow(subject, rig, random.randrange(2 ** 31), uploaded.get(rig)))
                except Exception as e:
                    rows.append((n, rig, subject, "", "", f"그림 실패 {e}"))
                    print(rows[-1], flush=True); n += 1
                    continue
                t1 = time.monotonic()
                try:
                    png = await asyncio.to_thread(character.cut_and_fit, raw)
                    note = "ok"
                except Exception as e:
                    png, note = None, f"오려 내기 실패 {e}"
                t2 = time.monotonic()
                if png:
                    slug = subject.split(" with ")[0].replace(" ", "_")[:24]
                    open(os.path.join(out, f"{rig}__{n:02d}_{slug}.png"), "wb").write(png)
                rows.append((n, rig, subject, f"{t1 - t0:.2f}", f"{t2 - t1:.2f}", note))
                print("\t".join(map(str, rows[-1])), flush=True)
                n += 1
    with open(os.path.join(out, "bench.tsv"), "w", encoding="utf-8") as f:
        f.write("번호\t몸 종류\t묘사\t그림 초\t오려 내기 초\t결과\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")
    d = sorted(float(r[3]) for r in rows if r[3]); c = sorted(float(r[4]) for r in rows if r[4])
    if d:
        print(f"그림 {len(d)}장 · 중앙 {d[len(d) // 2]:.2f}s · 최소 {d[0]:.2f}s · 최대 {d[-1]:.2f}s", flush=True)
        print(f"오려 내기 · 중앙 {c[len(c) // 2]:.2f}s · 최대 {c[-1]:.2f}s", flush=True)
    print("ALL DONE", flush=True)


if __name__ == "__main__":
    a = sys.argv[2:]
    asyncio.run(main(sys.argv[1], a[a.index("--set") + 1] if "--set" in a else "all"))
