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

NEUTRAL = ["음~", "응응.", "응, 그랬구나."]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for i, line in enumerate(NEUTRAL):
        mp3 = shrink(speak(line))
        (OUT / f"neutral_{i}.mp3").write_bytes(mp3)
        print(f"neutral_{i}.mp3  {float(av.open(io.BytesIO(mp3)).duration / 1e6):.2f}s  {line}")


if __name__ == "__main__":
    main()
