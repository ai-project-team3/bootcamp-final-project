"""One loudness for every mascot line (#42, 10-02).

The 365 baked lines measured -35 to -21 LUFS — 14 dB apart — so a few lines came out much louder
than the rest, and a live /tts line was another level again. Both paths now go through here: the
voiced part of the line is brought to TARGET_DBFS (RMS of the frames above the gate, which tracks
LUFS closely for one speaker), and the gain is held back so no sample peaks above PEAK_DBFS
(no limiter: a peak-capped line is a little quieter, never clipped).

Output is the app's format: mono · 24 kHz · 40 kbps mp3 (what eval/bake_lines.py always wrote).
"""
import io

import av
import numpy as np

TARGET_DBFS = -20.0      # the loud end of what we had (-21 LUFS), so most lines come up, none jump
PEAK_DBFS = -1.5
GATE_DBFS = -45.0        # 20 ms frames quieter than this are pauses, not voice
RATE = 24000
KBPS = 40


def _decode(mp3: bytes) -> np.ndarray:
    src = av.open(io.BytesIO(mp3))
    res = av.AudioResampler(format="flt", layout="mono", rate=RATE)
    chunks = []
    for frame in src.decode(audio=0):
        for f in res.resample(frame):
            chunks.append(f.to_ndarray().reshape(-1))
    for f in res.resample(None):
        chunks.append(f.to_ndarray().reshape(-1))
    src.close()
    return np.concatenate(chunks) if chunks else np.zeros(0, dtype=np.float32)


def _encode(pcm: np.ndarray) -> bytes:
    buf = io.BytesIO()
    dst = av.open(buf, "w", format="mp3")
    st = dst.add_stream("libmp3lame", rate=RATE)
    st.layout = "mono"
    st.bit_rate = KBPS * 1000
    res = av.AudioResampler(format=st.format, layout="mono", rate=RATE)
    frame = av.AudioFrame.from_ndarray(pcm.astype(np.float32).reshape(1, -1), format="flt", layout="mono")
    frame.sample_rate = RATE
    for f in res.resample(frame) + res.resample(None):
        for p in st.encode(f):
            dst.mux(p)
    for p in st.encode(None):
        dst.mux(p)
    dst.close()
    return buf.getvalue()


def gain_db(pcm: np.ndarray) -> float:
    """How much to turn this line up (or down). 0 when there is no voice to measure."""
    n = RATE // 50
    frames = pcm[: len(pcm) // n * n].reshape(-1, n) if len(pcm) >= n else pcm.reshape(1, -1)
    if frames.size == 0:
        return 0.0
    rms = np.sqrt(np.mean(frames.astype(np.float64) ** 2, axis=1))
    db = 20 * np.log10(np.maximum(rms, 1e-9))
    voiced = rms[db > GATE_DBFS]
    if voiced.size == 0:
        return 0.0
    level = 20 * np.log10(np.sqrt(np.mean(voiced ** 2)))
    peak = 20 * np.log10(max(float(np.max(np.abs(pcm))), 1e-9))
    return min(TARGET_DBFS - level, PEAK_DBFS - peak)


def level(mp3: bytes) -> bytes:
    """The same line at the one mascot loudness, re-encoded in the app's format."""
    pcm = _decode(mp3)
    if pcm.size == 0:
        return mp3
    return _encode(pcm * (10 ** (gain_db(pcm) / 20)))
