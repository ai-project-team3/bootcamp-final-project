# -*- coding: utf-8 -*-
"""TypeCast 마스코트 목소리 고르기 — 거르기(screen) → 다듬기(tune) (2026-09-25)

09-25 1차 비교(`bench_tts_compare.py`)에서 마스코트는 TypeCast 가 이겼는데, 어린이 목소리
42개 중 **둘만** 들었다. 그리고 TypeCast 는 같은 목소리를 감정·강도·빠르기·음높이로 바꿀 수 있다.
그래서 두 단계로 고른다.

  screen   어린이 42개 전부 × 마스코트 문장 1개 × 감정 normal. **번호만 보이는 블라인드.**
           이름(「Pangpang」 등)이 선입견을 만들고, 언어 칸이 없어서 이름으로 한국어를
           추리면 추측이 된다 — 그래서 다 굽고 귀로 거른다. 약 1,900자(무료 월 3만 자).
  tune     거르기에서 남은 목소리 몇 개 × 설정 변형 × 마스코트 문장 3개. 역시 블라인드.

  group    거르기 결과를 **페르소나 칸**으로 묶는다. 성별이 아니라 소리의 성질로 —
           마스코트는 캐릭터라 남녀가 기준이 아니다(09-25 조장). 같은 문장을 읽었으므로
           말한 시간 = 빠르기, 거기에 음높이 중앙값과 음높이 폭(반음)을 파형에서 잰다.
           ⚠️ 귀를 대신하지 않는다 — 칸마다 후보를 좁혀 줄 뿐이다.
           numpy·torchaudio 가 필요해서 .venv-diar 로 돌린다.

    python -m eval.bench_tts_voices screen
    .venv-diar\\Scripts\\python -m eval.bench_tts_voices group --drop 26
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


# Persona slots (09-25 draft, confirmed by the team lead). Each score is a weighted sum of
# z-scores across the screened voices: speed (shorter speech = faster), pitch height,
# and liveliness (pitch range in semitones).
PERSONAS = [
    ("①", "다정한 친구", "차분하고 따뜻하게, 천천히 — 말이 느린 3~4세 · 수줍은 아이",
     {"speed": -1.0, "lively": -1.0}),
    ("②", "씩씩한 친구", "밝고 힘차게 — 에너지 많은 아이",
     {"speed": 0.6, "lively": 1.0, "high": 0.3}),
    ("③", "장난꾸러기", "톤이 높고 빠르게 — 웃기는 걸 좋아하는 아이",
     {"speed": 1.0, "high": 1.0, "lively": 0.6}),
    ("④", "언니·형 같은 친구", "조금 더 차분하고 믿음직하게 — 6~7세",
     {"high": -1.0, "lively": -0.4}),
]
TOP_PER_SLOT = 8


def _traits(path: Path) -> dict:
    """Speech duration, median F0 and F0 spread from one mp3 — plain autocorrelation."""
    import numpy as np
    import torchaudio
    import torchaudio.functional as AF

    w, sr = torchaudio.load(str(path))
    x = AF.resample(w.mean(0), sr, 16000).numpy()
    sr = 16000
    n, h = int(0.04 * sr), int(0.01 * sr)
    lo, hi = sr // 600, sr // 150                      # 150-600 Hz covers child voices
    win = np.hanning(n)
    rms, f0 = [], []
    for i in range(0, len(x) - n, h):
        fr = x[i:i + n] * win
        rms.append(float(np.sqrt(np.mean(fr ** 2)) + 1e-9))
        ac = np.correlate(fr, fr, "full")[n - 1:]
        if ac[0] <= 0:
            f0.append(np.nan)
            continue
        k = int(np.argmax(ac[lo:hi])) + lo
        f0.append(sr / k if ac[k] / ac[0] > 0.45 else np.nan)
    rms_db = 20 * np.log10(np.array(rms))
    loud = rms_db > rms_db.max() - 35                  # speech frames, relative to the loudest
    idx = np.flatnonzero(loud)
    dur = (idx[-1] - idx[0]) * h / sr if len(idx) else float("nan")
    f = np.array(f0)[loud]
    f = f[~np.isnan(f)]
    med = float(np.median(f)) if len(f) else float("nan")
    st = 12 * np.log2(f / med) if len(f) else np.array([0.0])
    return {"dur": round(float(dur), 2), "f0": round(med, 1),
            "range_st": round(float(np.percentile(st, 90) - np.percentile(st, 10)), 2)}


def group(drop: set[int]) -> None:
    import statistics as st

    src = OUT / "screen"
    key = json.loads((src / "_key.json").read_text(encoding="utf-8"))
    nums = sorted(int(k) for k in key if int(k) not in drop)
    traits = {n: _traits(src / f"{n:02d}.mp3") for n in nums}

    def z(field, sign=1.0):
        vals = [traits[n][field] for n in nums]
        m, s = st.mean(vals), st.pstdev(vals) or 1.0
        return {n: sign * (traits[n][field] - m) / s for n in nums}

    zs = {"speed": z("dur", -1.0), "high": z("f0"), "lively": z("range_st")}

    def word(field, n, words):
        vals = sorted(traits[m][field] for m in nums)
        t1, t2 = vals[len(vals) // 3], vals[2 * len(vals) // 3]
        v = traits[n][field]
        return words[0] if v <= t1 else words[2] if v >= t2 else words[1]

    def desc(n):
        t = traits[n]
        return (f"{word('dur', n, ('빠름', '보통', '느림'))} · "
                f"{word('f0', n, ('낮음', '중간', '높음'))} · "
                f"{word('range_st', n, ('잔잔', '보통', '출렁'))}"
                f"<div style='color:#777;font-size:12px'>{t['dur']}초 · {t['f0']:.0f}Hz · {t['range_st']}반음</div>")

    sections, picked = [], {}
    for mark, name, who, weights in PERSONAS:
        score = {n: sum(wt * zs[f][n] for f, wt in weights.items()) for n in nums}
        top = sorted(nums, key=lambda n: -score[n])[:TOP_PER_SLOT]
        picked[f"{mark} {name}"] = top
        cards = "".join(
            f'<div class="c"><div class="n">{n}</div>'
            f'<audio controls preload="none" src="../screen/{n:02d}.mp3"></audio>{desc(n)}</div>'
            for n in top)
        sections.append(f"<h2>{mark} {name}</h2><p>{who}</p><div class='grid'>{cards}</div>")

    intro = ("<b>칸마다 후보 두 개씩</b> 골라 주세요. 번호는 거르기 페이지와 같습니다 — "
             "같은 번호가 두 칸에 나오면 같은 목소리입니다.<br>"
             "카드 아래 글자는 <b>소리에서 잰 값</b>(빠르기 · 음높이 · 억양 폭)이고, "
             "칸에 넣은 근거일 뿐 판정이 아닙니다. 판정은 귀로 합니다.")
    key_html = "<ol>" + "".join(f"<li value='{n}'>{key[str(n)]}</li>" for n in nums) + "</ol>"
    out = OUT / "group"
    out.mkdir(parents=True, exist_ok=True)
    (out / "listen.html").write_text(page("마스코트 페르소나 칸", intro, "".join(sections), key_html),
                                     encoding="utf-8")
    (out / "traits.json").write_text(json.dumps({"traits": traits, "slots": picked},
                                                ensure_ascii=False, indent=2), encoding="utf-8")
    for slot, top in picked.items():
        print(f"  {slot}: {top}")
    print(f"\n듣기 {out / 'listen.html'}")


if __name__ == "__main__":
    load_dotenv()
    if len(sys.argv) >= 2 and sys.argv[1] == "screen":
        screen()
    elif len(sys.argv) >= 2 and sys.argv[1] == "group":
        drop = {int(a) for a in sys.argv[3:]} if len(sys.argv) >= 4 and sys.argv[2] == "--drop" else set()
        group(drop)
    elif len(sys.argv) >= 3 and sys.argv[1] == "tune":
        tune(sys.argv[2:])
    else:
        sys.exit(__doc__)
