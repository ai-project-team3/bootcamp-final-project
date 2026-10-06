"""ElevenLabs v4 against the mascot voice we use now (OpenAI sage) — first sound, pitch, price (10-06).

Why: the voice is ~80 % of a session's cost (#172 · 10-02 「한 세션의 값」), and Eleven v4 Turbo is
listed at $0.04 per 1,000 characters (72 % off until 10-12) with ~100 ms model latency. 10-01 dropped
ElevenLabs Flash by ear and by pitch — only sage sat in a child's range (median F0 262-296 Hz).
v4 is a different model, so it is measured again with the same three mascot lines.

What it writes (eval/tts_out/eleven_v4/, gitignored):
- one mp3 per (voice × model × line), named so voice_pitch.py can read them — the ear test listens to these
and prints: first byte · last byte · audio seconds · median F0 · won per line.

Voices: ElevenLabs Korean-native voices are library voices, and the free plan cannot call them through
the API (HTTP 402 · 09-25). Give the ids to try in ELEVEN_VOICES (`id:label,id:label`) — on the free
plan only the premade English voices answer.

    py -m eval.bench_tts_eleven_v4                 # needs ELEVENLABS_API_KEY (and OPENAI_API_KEY for sage)
    ELEVEN_MODELS=eleven_v4_turbo py -m eval.bench_tts_eleven_v4
"""
from __future__ import annotations

import os
import statistics
from pathlib import Path

import numpy as np

from eval.bench_tts import LIVE, mp3_seconds
from eval.bench_tts_compare import synth
from eval.config import load_dotenv
from eval.voice_pitch import f0_track, load

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "eval" / "tts_out" / "eleven_v4"

WON_PER_USD = 1400
# list price per 1,000 characters (elevenlabs.io/pricing/api · 10-06) — the 72 % launch discount ends 10-12
ELEVEN_PRICE = {"eleven_v4_turbo": 0.04, "eleven_v4": 0.08, "eleven_flash_v2_5": 0.04}
SAGE_WON_PER_LINE = 3.686          # 10-02 measured (49-character line) — eval/results.md
CHILD_F0 = (262, 296)              # 10-01: the range sage sat in


def voices() -> list[tuple[str, str]]:
    raw = os.environ.get("ELEVEN_VOICES", "cgSgspJ2msm6clMCkdW9:Jessica")
    return [tuple(v.split(":", 1)) if ":" in v else (v, v) for v in raw.split(",") if v.strip()]


def models() -> list[str]:
    return [m.strip() for m in os.environ.get("ELEVEN_MODELS", "eleven_v4_turbo,eleven_v4").split(",") if m.strip()]


def f0_median(path: Path) -> float | None:
    f = f0_track(load(path))
    return float(np.median(f)) if len(f) >= 20 else None


def main() -> None:
    load_dotenv()
    if not os.environ.get("ELEVENLABS_API_KEY"):
        raise SystemExit("ELEVENLABS_API_KEY 가 없다 — 루트 .env 에 넣고 다시")
    OUT.mkdir(parents=True, exist_ok=True)
    configs = [("elevenlabs", vid, m, f"{label} · {m}") for vid, label in voices() for m in models()]
    if os.environ.get("OPENAI_API_KEY"):
        configs.insert(0, ("openai", "sage", "gpt-4o-mini-tts-2025-03-20", "sage · 지금 오또"))

    print(f"{'목소리':<34}{'첫 소리':>8}{'전체':>7}{'F0 중앙':>8}{'원/줄':>7}")
    for vendor, voice, model, desc in configs:
        ttfb, total, f0s, chars, failed = [], [], [], [], 0
        for i, text in enumerate(LIVE, 1):
            try:
                t1, t2, blob = synth(vendor, voice, model, text)
            except Exception as e:                  # one voice failing must not hide the rest
                failed += 1
                print(f"  ✗ {desc} s{i}: {str(e)[:160]}")
                continue
            path = OUT / f"{vendor}_{voice}_{model}_s{i}.mp3"
            path.write_bytes(blob)
            ttfb.append(t1); total.append(t2); chars.append(len(text))
            if (f := f0_median(path)) is not None:
                f0s.append(f)
            print(f"    {desc} s{i}: {t1:.2f}s / {t2:.2f}s · {mp3_seconds(str(path)):.1f}s audio")
        if not ttfb:
            print(f"{desc:<34}  전부 실패 ({failed})")
            continue
        per_line_chars = 49                         # the 10-02 average line, so the column compares with sage
        won = SAGE_WON_PER_LINE if vendor == "openai" else ELEVEN_PRICE.get(model, 0.08) * per_line_chars / 1000 * WON_PER_USD
        f0 = statistics.median(f0s) if f0s else float("nan")
        mark = "✓" if CHILD_F0[0] <= f0 <= CHILD_F0[1] else " "
        print(f"{desc:<34}{statistics.median(ttfb):7.2f}s{statistics.median(total):6.2f}s{f0:7.0f}{mark}{won:7.2f}"
              + (f"  ({failed} 실패)" if failed else ""))
    print(f"\nF0 ✓ = 10-01 기준 아이 음높이 범위 {CHILD_F0[0]}~{CHILD_F0[1]}Hz 안. 귀 판정은 {OUT} 의 mp3 로 따로.")


if __name__ == "__main__":
    main()
