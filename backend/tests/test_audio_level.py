"""#42: every mascot line at one loudness — a quiet take and a loud take come out alike, never clipped."""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import audio_level as al          # noqa: E402


def tone(db: float, seconds: float = 1.0) -> bytes:
    t = np.arange(int(al.RATE * seconds)) / al.RATE
    amp = 10 ** (db / 20) * np.sqrt(2)          # sine RMS = amp / sqrt 2
    return al._encode((amp * np.sin(2 * np.pi * 220 * t)).astype(np.float32))


def rms_db(mp3: bytes) -> float:
    pcm = al._decode(mp3)
    return 20 * np.log10(np.sqrt(np.mean(pcm.astype(np.float64) ** 2)))


def test_quiet_and_loud_lines_meet_at_one_level():
    quiet, loud = al.level(tone(-36)), al.level(tone(-22))
    assert abs(rms_db(quiet) - rms_db(loud)) < 1.0
    assert abs(rms_db(quiet) - al.TARGET_DBFS) < 1.5


def test_the_gain_never_pushes_a_peak_past_the_cap():
    out = al._decode(al.level(tone(-36)))
    assert 20 * np.log10(np.max(np.abs(out))) <= al.PEAK_DBFS + 1.0      # mp3 overshoot


def test_silence_is_left_alone():
    assert al.gain_db(np.zeros(al.RATE, dtype=np.float32)) == 0.0
