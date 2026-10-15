"""Listening test, round 2 (10-14 진웅 · after eval/splice_listen.py).

Round 1 (blind, 1–5): whole 5.0 · 「놀이터」+「에서 무슨 일이 있었어?」 1.5 · 「놀이터에서」+「무슨 일이 있었어?」 4.0 ·
「빨간 공」+「도 그렸네!」+「빨간 공」+「이야기 해 줄래?」 1.75. Round 2 asks three things:

  piece — every heard word keeps its particle: 「빨간 공도」 + 「그렸네!」 + 「빨간 공」 + 「이야기 해 줄래?」
          (진웅: round 1 sounded wrong because 「빨간 공」 and 「도」 were cut apart)
  place — a word with no final consonant (no 연음 across the cut) + 「에서 무슨 일이 있었어?」, three ways:
          raw    as in round 1
          tight  the word's drawn-out last syllable shortened to 75% (WSOLA), joined with an 8 ms cross-fade
          pitch  tight, and the tail moved (±4 semitones at most) so it starts at the pitch the word ended on
          and 「{word}에서」 + 「무슨 일이 있었어?」 (round 1's 4.0) as the reference
  syll  — the line written as it is pronounced (「노리터에서 무슨 이리 이써써?」) and joined one syllable clip at a time

Parts come from /tts once and are kept in eval/tts_out/splice/parts (shared with round 1).

  backend/.venv/Scripts/python.exe eval/splice_listen2.py      → eval/tts_out/splice2/*.mp3 + meta.json
"""
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
sys.path.insert(0, str(ROOT / "eval"))
from app.audio_level import RATE, _encode  # noqa: E402
from splice_listen import edge, join, trim, voice  # noqa: E402

OUT = ROOT / "eval/tts_out/splice2"

PIECES = ["빨간 공", "모래성", "자동차", "미끄럼틀"]
OPEN_PLACES = ["놀이터", "바닷가", "키즈카페", "학교", "바다", "카페"]      # no final consonant — nothing links across the cut
SYLLABLES = {   # as pronounced; eojeol by eojeol
    "놀이터에서 무슨 일이 있었어?": [["노", "리", "터", "에", "서"], ["무", "슨"], ["이", "리"], ["이", "써", "써"]],
    "바닷가에서 무슨 일이 있었어?": [["바", "다", "까", "에", "서"], ["무", "슨"], ["이", "리"], ["이", "써", "써"]],
}
TAIL = "에서 무슨 일이 있었어?"


def wsola(x: np.ndarray, speed: float) -> np.ndarray:
    """Time-stretch without changing pitch; speed > 1 is shorter"""
    n = int(0.03 * RATE)
    hs = n // 2
    tol = int(0.008 * RATE)
    win = np.hanning(n).astype(np.float32)
    out_len = int(len(x) / speed) + n
    out = np.zeros(out_len, np.float32)
    norm = np.zeros(out_len, np.float32)
    x = np.concatenate([x, np.zeros(n + tol, np.float32)])
    pos, prev = 0, 0
    k = 0
    while True:
        target = int(k * hs * speed)
        if target + n + tol >= len(x) or k * hs + n >= out_len:
            break
        if k == 0:
            pos = target
        else:
            nat = x[prev + hs: prev + hs + n]
            best, bestc = target, -1e9
            for d in range(-tol, tol + 1, 2):
                s = target + d
                if s < 0:
                    continue
                c = float(np.dot(x[s: s + n], nat))
                if c > bestc:
                    best, bestc = s, c
            pos = best
        out[k * hs: k * hs + n] += x[pos: pos + n] * win
        norm[k * hs: k * hs + n] += win
        prev = pos
        k += 1
    out = out / np.maximum(norm, 1e-3)
    return out[: int(len(x[: -n - tol]) / speed)]


def tight_word(w: np.ndarray) -> np.ndarray:
    w = trim(w)
    cut = max(0, len(w) - int(0.18 * RATE))
    return np.concatenate([w[:cut], wsola(w[cut:], 1 / 0.75)])


def shift(x: np.ndarray, st: float) -> np.ndarray:
    """Pitch by st semitones, same length: resample then stretch back"""
    r = 2 ** (st / 12)
    y = np.interp(np.arange(0, len(x) - 1, r), np.arange(len(x)), x).astype(np.float32)
    return wsola(y, 1 / r)[: len(x)]


def write(name: str, pcm: np.ndarray) -> float:
    (OUT / f"{name}.mp3").write_bytes(_encode(pcm))
    return round(len(pcm) / RATE, 2)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    cache: dict = {}
    meta = []
    for i, p in enumerate(PIECES):
        line = f"{p}도 그렸네! {p} 이야기 해 줄래?"
        full = trim(voice(line, cache))
        parts = [voice(f"{p}도", cache), voice("그렸네!", cache), voice(p, cache), voice("이야기 해 줄래?", cache)]
        meta.append({"id": f"piece{i}", "line": line, "kind": "piece", "variants": {
            "full": {"name": "통째", "secs": write(f"piece{i}_full", full), "tts_chars": len(line), "parts": [line]},
            "heads": {"name": "낱말마다 조사까지", "secs": write(f"piece{i}_heads", join(parts, [70, 260, 70])),
                      "tts_chars": len(p) * 2 + 1, "parts": [f"{p}도", "그렸네!", p, "이야기 해 줄래?"]},
        }})
    t = voice(TAIL, cache)
    bare = voice("무슨 일이 있었어?", cache)
    for i, p in enumerate(OPEN_PLACES):
        line = f"{p}에서 무슨 일이 있었어?"
        w = voice(p, cache)
        tw = tight_word(w)
        fa, _ = edge(tw, True)
        fb, _ = edge(t, False)
        st = float(np.clip(12 * np.log2(fa / fb), -4, 4)) if fa and fb else 0.0
        v = {
            "full": {"name": "통째", "secs": write(f"place{i}_full", trim(voice(line, cache))), "tts_chars": len(line), "parts": [line]},
            "raw": {"name": "낱말 + 꼬리", "secs": write(f"place{i}_raw", join([w, t], [0])), "tts_chars": len(p), "parts": [p, TAIL]},
            "tight": {"name": "낱말 끝 줄이기", "secs": write(f"place{i}_tight", join([tw, t], [0])), "tts_chars": len(p), "parts": [p, TAIL]},
            "pitch": {"name": "끝 줄이기 + 음높이 맞추기", "secs": write(f"place{i}_pitch", join([tw, shift(trim(t), st)], [0])),
                      "tts_chars": len(p), "parts": [p, f"{TAIL} ({st:+.1f}반음)"]},
            "wordp": {"name": "조사까지 + 꼬리", "secs": write(f"place{i}_wordp", join([voice(f"{p}에서", cache), bare], [70])),
                      "tts_chars": len(p) + 2, "parts": [f"{p}에서", "무슨 일이 있었어?"]},
        }
        meta.append({"id": f"place{i}", "line": line, "kind": "place", "variants": v})
    for i, (line, words) in enumerate(SYLLABLES.items()):
        clips, gaps = [], []
        for wi, word in enumerate(words):
            for si, syl in enumerate(word):
                clips.append(voice(syl, cache))
                if clips[1:]:
                    gaps.append(70 if si == 0 else 0)
        meta.append({"id": f"syll{i}", "line": line, "kind": "syll", "variants": {
            "full": {"name": "통째", "secs": write(f"syll{i}_full", trim(voice(line, cache))), "tts_chars": len(line), "parts": [line]},
            "syll": {"name": "글자 이어 붙이기", "secs": write(f"syll{i}_syll", join(clips, gaps)), "tts_chars": 0,
                     "parts": [" ".join("·".join(w) for w in words)]},
        }})
    (OUT / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")
    for m in meta:
        print(m["line"], " · ".join(f"{k} {v['secs']}s" for k, v in m["variants"].items()))


if __name__ == "__main__":
    main()
