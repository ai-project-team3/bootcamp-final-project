# -*- coding: utf-8 -*-
"""TypeCast 마스코트 목소리 고르기 — 거르기(screen) → 다듬기(tune) (2026-09-25)

09-25 1차 비교(`bench_tts_compare.py`)에서 마스코트는 TypeCast 가 이겼는데, 어린이 목소리
42개 중 **둘만** 들었다. 그리고 TypeCast 는 같은 목소리를 감정·강도·빠르기·음높이로 바꿀 수 있다.
그래서 두 단계로 고른다.

  screen   어린이 42개 전부 × 마스코트 문장 1개 × 감정 normal. **번호만 보이는 블라인드.**
           이름(「Pangpang」 등)이 선입견을 만들고, 언어 칸이 없어서 이름으로 한국어를
           추리면 추측이 된다 — 그래서 다 굽고 귀로 거른다. 약 1,900자(무료 월 3만 자).
  tune     거르기에서 남은 목소리 몇 개 × 설정 변형 × 마스코트 문장 3개. 역시 블라인드.

    python -m eval.bench_tts_voices screen
    python -m eval.bench_tts_voices tune tc_xxx tc_yyy tc_zzz
"""
from __future__ import annotations

import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from eval.bench_tts import LIVE, mp3_seconds
from eval.config import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "eval" / "tts_out"                  # gitignored
MODEL = "ssfm-v30"

SCREEN_LINE = LIVE[0]    # 감탄 + 되비추기 + 질문이 다 있는 줄 — 마스코트 성격이 가장 잘 드러난다

# tune variants: (label, prompt, output) — only knobs the TTS docs list for ssfm-v30
VARIANTS = [
    ("기본",          {"emotion_type": "preset", "emotion_preset": "normal", "emotion_intensity": 1.0}, {}),
    ("밝게",          {"emotion_type": "preset", "emotion_preset": "happy", "emotion_intensity": 1.0}, {}),
    ("밝게·약하게",    {"emotion_type": "preset", "emotion_preset": "happy", "emotion_intensity": 0.6}, {}),
    ("톤 업",         {"emotion_type": "preset", "emotion_preset": "toneup", "emotion_intensity": 1.0}, {}),
    ("문맥 따라(smart)", {"emotion_type": "smart"}, {}),
    ("밝게·조금 빠르게", {"emotion_type": "preset", "emotion_preset": "happy", "emotion_intensity": 1.0},
     {"audio_tempo": 1.1}),
]


def voices() -> list[dict]:
    req = urllib.request.Request("https://api.typecast.ai/v2/voices",
                                 headers={"X-API-KEY": os.environ["TYPECAST_API_KEY"]})
    return json.loads(urllib.request.urlopen(req, timeout=60).read())


def speak(voice_id: str, text: str, prompt: dict, output: dict,
          prev: str | None = None, nxt: str | None = None) -> tuple[float, bytes]:
    p = dict(prompt)
    if p.get("emotion_type") == "smart":
        # smart reads the neighbouring lines to pick the emotion; give it the real context
        if prev:
            p["previous_text"] = prev
        if nxt:
            p["next_text"] = nxt
    body = {"voice_id": voice_id, "text": text, "model": MODEL, "language": "kor",
            "prompt": p, "output": {"audio_format": "mp3", **output}}
    req = urllib.request.Request(
        "https://api.typecast.ai/v1/text-to-speech", json.dumps(body).encode(),
        {"X-API-KEY": os.environ["TYPECAST_API_KEY"], "Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        blob = r.read()
    return time.time() - t0, blob


def page(title: str, intro: str, rows_html: str, key_html: str) -> str:
    return f"""<!doctype html><meta charset="utf-8"><title>{title}</title>
<style>body{{font:15px/1.5 system-ui,sans-serif;margin:24px;max-width:1300px}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:10px}}
.c{{border:1px solid #ddd;border-radius:8px;padding:8px}}.n{{font-weight:700}}audio{{width:100%}}
table{{border-collapse:collapse}}td,th{{border:1px solid #ddd;padding:6px;vertical-align:top}}
.q{{background:#f6f3ee;padding:12px 16px;border-radius:8px;margin-bottom:16px}}</style>
<h1>{title}</h1><div class="q">{intro}</div>{rows_html}
<details style="margin-top:20px"><summary><b>정답 보기</b> (고른 뒤에)</summary>{key_html}</details>"""


def screen() -> None:
    kids = [v for v in voices() if v.get("age") == "child"
            and any(m.get("version") == MODEL for m in v.get("models", []))]
    rng = random.Random(925)
    rng.shuffle(kids)
    out = OUT / "screen"
    out.mkdir(parents=True, exist_ok=True)
    base = {"emotion_type": "preset", "emotion_preset": "normal", "emotion_intensity": 1.0}
    cells, key, fails = [], {}, []
    for n, v in enumerate(kids, 1):
        try:
            took, blob = speak(v["voice_id"], SCREEN_LINE, base, {})
        except urllib.error.HTTPError as e:
            fails.append((n, v["voice_name"], e.code))
            print(f"  {n:2d} ✗ HTTP {e.code}")
            continue
        (out / f"{n:02d}.mp3").write_bytes(blob)
        secs = mp3_seconds(str(out / f"{n:02d}.mp3"))
        key[n] = f"{v['voice_name']} · {v.get('gender')} · {v['voice_id']}"
        cells.append(f'<div class="c"><div class="n">{n}</div>'
                     f'<audio controls preload="none" src="{n:02d}.mp3"></audio></div>')
        print(f"  {n:2d}  {took:4.2f}s  {secs:4.1f}s")
    intro = (f"<b>어린이 목소리 {len(cells)}개 · 같은 문장 · 감정 normal.</b><br>"
             f"「{SCREEN_LINE}」<br>마스코트(여섯 살 또래 친구)로 쓸 만한 번호를 <b>3~5개</b> 골라 주세요. "
             "외국어 억양이 들리면 바로 빼도 됩니다.")
    key_html = "<ol>" + "".join(f"<li value='{n}'>{t}</li>" for n, t in key.items()) + "</ol>"
    (out / "listen.html").write_text(page("마스코트 목소리 거르기", intro,
                                          f'<div class="grid">{"".join(cells)}</div>', key_html),
                                     encoding="utf-8")
    (out / "_key.json").write_text(json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n{len(cells)}개 구움 · 실패 {len(fails)} · 듣기 {out / 'listen.html'}")


def tune(voice_ids: list[str]) -> None:
    names = {v["voice_id"]: v["voice_name"] for v in voices()}
    combos = [(vid, var) for vid in voice_ids for var in VARIANTS]
    rng = random.Random(926)
    rng.shuffle(combos)
    out = OUT / "tune"
    out.mkdir(parents=True, exist_ok=True)
    lines = LIVE                                   # the three mascot lines
    head = "".join(f"<th>문장 {i}<div style='font-weight:400;font-size:13px'>{t}</div></th>"
                   for i, t in enumerate(lines, 1))
    trs, key = [], {}
    for n, (vid, (label, prompt, output)) in enumerate(combos, 1):
        tds = []
        for i, text in enumerate(lines):
            prev = lines[i - 1] if i > 0 else None
            nxt = lines[i + 1] if i + 1 < len(lines) else None
            try:
                _, blob = speak(vid, text, prompt, output, prev, nxt)
                (out / f"{n:02d}_s{i + 1}.mp3").write_bytes(blob)
                tds.append(f'<td><audio controls preload="none" src="{n:02d}_s{i + 1}.mp3"></audio></td>')
            except urllib.error.HTTPError as e:
                tds.append(f"<td>✗ HTTP {e.code}</td>")
        key[n] = f"{names.get(vid, vid)} · {label}"
        trs.append(f"<tr><th>{n}</th>{''.join(tds)}</tr>")
        print(f"  {n:2d} done")
    intro = ("<b>남은 목소리 × 설정 변형 · 마스코트 문장 3개.</b> 번호마다 목소리·설정이 섞여 있습니다. "
             "가장 마스코트 같은 번호를 순서대로 골라 주세요.")
    key_html = "<ol>" + "".join(f"<li value='{n}'>{t}</li>" for n, t in key.items()) + "</ol>"
    (out / "listen.html").write_text(page("마스코트 목소리 다듬기", intro,
                                          f"<table><tr><th></th>{head}</tr>{''.join(trs)}</table>",
                                          key_html), encoding="utf-8")
    (out / "_key.json").write_text(json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n듣기 {out / 'listen.html'}")


if __name__ == "__main__":
    load_dotenv()
    if len(sys.argv) >= 2 and sys.argv[1] == "screen":
        screen()
    elif len(sys.argv) >= 3 and sys.argv[1] == "tune":
        tune(sys.argv[2:])
    else:
        sys.exit(__doc__)
