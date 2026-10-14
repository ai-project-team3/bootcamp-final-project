"""Can a line be spoken as a live word + a baked tail, and how does the seam sound? (10-14 진웅)

Why: a line like 「놀이터에서 무슨 일이 있었어?」 goes whole to /tts because of one word. If the word
(the child's place · a drawn piece) is voiced once per session and the rest is a baked clip, the paid
part is only the word, and the word's voice is reused every time it comes back.

For each sentence this writes, at the app's format (mono · 24 kHz · 40 kbps · one loudness):
  full   — the whole line from /tts as today
  word   — the word alone + the tail with its particle baked     「놀이터」 + 「에서 무슨 일이 있었어?」
  wordp  — the word with its particle + the bare tail baked       「놀이터에서」 + 「무슨 일이 있었어?」 (the app's own baked clip)
Pieces repeat the word inside one line: 「X」 + 「도 그렸네!」 + 「X」 + 「이야기 해 줄래?」 — one word voice, used twice.

Each clip is trimmed to its voice (20 ms frames above audio_level.GATE_DBFS, 15 ms kept around) and joined
with a short gap and an 8 ms cross-fade. At every seam: the pitch jump (semitones, median F0 of the last
vs first 150 ms of voice) and the loudness jump (dB). Characters sent to /tts are counted per variant.

  backend/.venv/Scripts/python.exe eval/splice_listen.py      → eval/tts_out/splice/*.mp3 + meta.json
Uses the server's voice settings (backend/app/config.py), like eval/bake_lines.py. Costs ~40 /tts calls.
"""
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
sys.path.insert(0, str(ROOT / "eval"))
from app.audio_level import GATE_DBFS, RATE, _decode, _encode, level  # noqa: E402
from bake_lines import key, speak  # noqa: E402
from voice_pitch import SR as F0_SR, f0_track  # noqa: E402

OUT = ROOT / "eval/tts_out/splice"
BAKED = ROOT / "android/app/src/main/assets/voice"

PLACES = ["놀이터", "바닷가", "할머니 집", "수영장", "유치원", "동물원", "키즈카페", "큰 공원"]
PIECES = [("빨간 공", "도"), ("모래성", "도"), ("자동차", "도"), ("미끄럼틀", "도")]

FRAME = RATE // 50
PAD = int(0.015 * RATE)
FADE = int(0.008 * RATE)


def voice(text: str, cache: dict) -> np.ndarray:
    """One /tts call per distinct text in this run — the per-session reuse the app would do."""
    if text not in cache:
        f = OUT / "parts" / f"{key(text)}.mp3"
        if not f.exists():
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_bytes(level(speak(text)))
            print(f"  /tts {len(text):2d} chars  {text}")
        cache[text] = _decode(f.read_bytes())
    return cache[text]


def baked(text: str) -> np.ndarray | None:
    f = BAKED / f"{key(text)}.mp3"
    return _decode(f.read_bytes()) if f.exists() else None


def trim(x: np.ndarray) -> np.ndarray:
    n = len(x) // FRAME
    db = 20 * np.log10(np.sqrt(np.mean(x[: n * FRAME].reshape(n, FRAME) ** 2, axis=1)) + 1e-9)
    on = np.flatnonzero(db > GATE_DBFS)
    if on.size == 0:
        return x
    return x[max(0, on[0] * FRAME - PAD): min(len(x), (on[-1] + 1) * FRAME + PAD)]


def join(parts: list[np.ndarray], gaps_ms: list[int]) -> np.ndarray:
    out = trim(parts[0])
    for p, g in zip(parts[1:], gaps_ms):
        p = trim(p)
        if g > 0:
            out = np.concatenate([out, np.zeros(int(g / 1000 * RATE), dtype=np.float32), p])
        else:
            ramp = np.linspace(0, 1, FADE, dtype=np.float32)
            out = np.concatenate([out[:-FADE], out[-FADE:] * (1 - ramp) + p[:FADE] * ramp, p[FADE:]])
    return out


def edge(x: np.ndarray, end: bool) -> tuple[float | None, float]:
    """median F0 (Hz) and level (dB) of the first / last 150 ms of voice"""
    x = trim(x)
    w = int(0.15 * RATE)
    seg = x[-w:] if end else x[:w]
    db = 20 * np.log10(np.sqrt(np.mean(seg ** 2)) + 1e-9)
    s16 = np.interp(np.arange(0, len(seg), RATE / F0_SR), np.arange(len(seg)), seg)
    f = f0_track(s16)
    return (float(np.median(f)) if len(f) >= 3 else None), float(db)


def seams(parts: list[np.ndarray]) -> list[dict]:
    rows = []
    for a, b in zip(parts, parts[1:]):
        fa, la = edge(a, True)
        fb, lb = edge(b, False)
        st = 12 * np.log2(fb / fa) if fa and fb else None
        rows.append({"pitch_st": None if st is None else round(float(st), 1), "level_db": round(lb - la, 1)})
    return rows


def write(name: str, pcm: np.ndarray) -> float:
    (OUT / f"{name}.mp3").write_bytes(_encode(pcm))
    return round(len(pcm) / RATE, 2)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    cache: dict = {}
    meta = []
    tail = "에서 무슨 일이 있었어?"
    bare = baked("무슨 일이 있었어?")
    if bare is None:
        raise SystemExit("baked 「무슨 일이 있었어?」 not in the app")
    for i, p in enumerate(PLACES):
        line = f"{p}에서 무슨 일이 있었어?"
        full = trim(voice(line, cache))
        w, t = voice(p, cache), voice(tail, cache)        # the tail is spoken once and kept — as a baked clip would be
        wp = voice(f"{p}에서", cache)
        meta.append({"id": f"place{i}", "line": line, "kind": "place", "variants": {
            "full": {"secs": write(f"place{i}_full", full), "tts_chars": len(line), "parts": [line]},
            "word": {"secs": write(f"place{i}_word", join([w, t], [0])), "tts_chars": len(p),
                     "parts": [p, tail], "seams": seams([w, t])},
            "wordp": {"secs": write(f"place{i}_wordp", join([wp, bare], [70])), "tts_chars": len(p) + 2,
                      "parts": [f"{p}에서", "무슨 일이 있었어?"], "seams": seams([wp, bare])},
        }})
    for i, (p, ptl) in enumerate(PIECES):
        line = f"{p}{ptl} 그렸네! {p} 이야기 해 줄래?"
        full = trim(voice(line, cache))
        w = voice(p, cache)
        a, b = voice(f"{ptl} 그렸네!", cache), voice("이야기 해 줄래?", cache)
        meta.append({"id": f"piece{i}", "line": line, "kind": "piece", "variants": {
            "full": {"secs": write(f"piece{i}_full", full), "tts_chars": len(line), "parts": [line]},
            "word": {"secs": write(f"piece{i}_word", join([w, a, w, b], [0, 260, 70])), "tts_chars": len(p),
                     "parts": [p, f"{ptl} 그렸네!", p, "이야기 해 줄래?"], "seams": seams([w, a, w, b])},
        }})
    (OUT / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")
    for m in meta:
        print(f"\n{m['line']}")
        for k, v in m["variants"].items():
            print(f"  {k:<6} {v['secs']:5.2f}s · /tts {v['tts_chars']:2d} chars · seams {v.get('seams', '')}")


if __name__ == "__main__":
    main()
