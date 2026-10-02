"""Bake the neutral sounds the mascot makes the moment the child stops talking (10-01).

Step 1 of the reaction plan (docs/0928_민우_판정대기_리액션_초안.md · guidelines/6 「한 바퀴 이후」 2):
the answer is not transcribed yet, so the sound must be content-neutral — no feeling, no praise,
no question. It plays from the phone at once and covers the wait for /stt + /turn (≈ 5–6 s on
the public address, 10-01). Kept short so the /turn reply ("들려줘서 고마워!" …) does not repeat it.

Same voice as everything else (backend settings · eval/bake_lines.py).

  py eval/bake_neutral.py   → android/app/src/main/assets/voice/neutral_<n>.mp3
"""
import io
import sys
from pathlib import Path

import av

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bake_lines import OUT, shrink, speak  # noqa: E402

# 10-02 조장: 「음~」 「응응.」 is too short and sounds machine-made — a bare interjection gives the
# voice nothing to shape. Whole short sentences, still saying nothing about what the child said
# ("몰라" must fit too): heard you · thinking.
NEUTRAL = [
    "잘 들었어! 잠깐만 생각해 볼게.",
    "응, 그랬구나. 어디 보자~",
    "이야기해 줘서 고마워. 음, 그러니까…",
    "아하, 그렇구나! 내가 생각해 볼게.",
    "응응, 다 듣고 있었어. 잠깐만 기다려 줘.",
    "오호, 그랬구나~ 어디 한번 볼까?",
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("neutral_*.mp3"):
        f.unlink()
    listen = Path(__file__).parent / "tts_out" / "neutral"     # the same files, to hear on the PC
    listen.mkdir(parents=True, exist_ok=True)
    for i, line in enumerate(NEUTRAL):
        mp3 = shrink(speak(line))
        (OUT / f"neutral_{i}.mp3").write_bytes(mp3)
        (listen / f"neutral_{i}.mp3").write_bytes(mp3)
        print(f"neutral_{i}.mp3  {float(av.open(io.BytesIO(mp3)).duration / 1e6):.2f}s  {line}")


if __name__ == "__main__":
    main()
