"""How high and how fast each sample speaks — to explain by numbers why one sounds like a child.

Children speak higher (F0 roughly 250-400 Hz at 5-8; adult women ~180-230, men ~100-140)
and in shorter bursts. For every mp3 in the given folders: median F0 over voiced frames
(autocorrelation, 70-600 Hz), its spread (p10-p90) and seconds of audio for the same three lines.

  py eval/voice_pitch.py eval/tts_out/openai_voices2 [more folders]
"""
import sys
from pathlib import Path

import av
import numpy as np

SR = 16000


def load(path: Path) -> np.ndarray:
    c = av.open(str(path))
    res = av.AudioResampler(format="flt", layout="mono", rate=SR)
    out = []
    for frame in c.decode(audio=0):
        for f in res.resample(frame):
            out.append(f.to_ndarray().reshape(-1))
    return np.concatenate(out) if out else np.zeros(1)


def f0_track(x: np.ndarray) -> np.ndarray:
    win, hop = int(0.04 * SR), int(0.01 * SR)
    lo, hi = SR // 600, SR // 70
    rms_all = np.sqrt(np.mean(x ** 2)) + 1e-9
    f0 = []
    for s in range(0, len(x) - win, hop):
        fr = x[s:s + win] - np.mean(x[s:s + win])
        if np.sqrt(np.mean(fr ** 2)) < 0.5 * rms_all:
            continue
        ac = np.correlate(fr, fr, "full")[win - 1:]
        if ac[0] <= 0:
            continue
        ac = ac / ac[0]
        lag = lo + int(np.argmax(ac[lo:hi]))
        if ac[lag] > 0.45:                       # clearly periodic = voiced
            f0.append(SR / lag)
    return np.array(f0)


def main():
    rows = []
    for d in sys.argv[1:]:
        for p in sorted(Path(d).glob("*.mp3")):
            x = load(p)
            f = f0_track(x)
            if len(f) < 20:
                continue
            rows.append((p.stem, float(np.median(f)), float(np.percentile(f, 10)), float(np.percentile(f, 90)), len(x) / SR))
    rows.sort(key=lambda r: -r[1])
    print(f"{'sample':<24} {'F0 med':>7} {'p10-p90':>12} {'secs':>6}")
    for name, med, p10, p90, secs in rows:
        print(f"{name:<24} {med:7.0f} {p10:5.0f}-{p90:<5.0f} {secs:6.1f}")


if __name__ == "__main__":
    main()
