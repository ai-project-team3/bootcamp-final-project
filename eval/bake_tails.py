"""Bake the fixed tails a spliced line ends with (10-14 진웅 · eval/splice_listen.py).

A line such as 「놀이터에서 뭐 했어?」 is played as the place with its particle (「놀이터에서」 · /tts once, kept for the
session) + a tail baked here (「뭐 했어?」); a line of two sentences such as 「다 만들었어! 제목은 『…』이야.」 as a baked
sentence here + the changing one from /tts. The app splices only when the rest after the heard word is baked — here, or
a line already baked whole (「무슨 일이 있었어?」 · 「누구랑 같이 있었어?」, eval/bake_lines.py) — so a tail not in either
list just sends the whole line to /tts (net/VoiceSplice.kt).

The 10-14 listening test kept the particle with the word: 「놀이터」+「에서 무슨 일이 있었어?」 rated 1.5 of 5,
「놀이터에서」+「무슨 일이 있었어?」 4.0. So a tail here starts a sentence of its own, never with a particle.

Same voice and loudness as eval/bake_lines.py. Written to assets/voice_tail/<key>.mp3 — a folder of its own,
because bake_lines.py removes every file in assets/voice/ it did not bake itself. A tail no longer listed is removed.

  backend/.venv/Scripts/python.exe eval/bake_tails.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
sys.path.insert(0, str(ROOT / "eval"))
from app.audio_level import level  # noqa: E402
from bake_lines import key, speak  # noqa: E402

OUT = ROOT / "android/app/src/main/assets/voice_tail"
HEARD = ROOT / "eval/tts_out/splice/parts"      # the tails the listening test used, when it ran first

TAILS = [
    # after a heard word + particle (round 1: 4.0)
    # 「거기서 뭐 했어?」 with the heard place instead — 「{장소}에서 뭐 했어?」 (PictureQuestion.easyAt)
    "뭐 했어?",
    # the server's own 「{장소}에서 뭐 하고 놀았어?」
    "뭐 하고 놀았어?",
    # a stroke near a named piece — 「{조각}에 더 그린 거야, 새로 그린 거야?」 (PictureDiary · 21 times in the device rounds)
    "더 그린 거야, 새로 그린 거야?",
    # sentences of their own in lines whose other sentence changes (round 3: 5.0 on 8 lines)
    "다 만들었어!",                 # 「다 만들었어! 제목은 『…』이야.」 (Scenes · the book is made)
    "다 그렸구나!",                 # 「다 그렸구나! {그린 것} 멋지다!」 (PictureDiary · every diary)
    "천천히 생각해 봐.",             # 「천천히 생각해 봐. {the question just asked}」 (silence ladder)
    "이 중에서 골라 볼까?",          # 「이 중에서 골라 볼까? {options}.」 (story options)
    "그럼 오또가 고를게!",           # 「그럼 오또가 고를게! {option}!」
    "친구와 함께 해결하기!",         # 「와, {호칭}가 새로운 방법을 찾았어! 친구와 함께 해결하기!」 (gift)
    "새 친구를 만들래?",             # 「지난번 {주인공}랑 또 모험할래? 새 친구를 만들래?」 (hero reuse)
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    want = {key(t) for t in TAILS}
    for f in OUT.glob("*.mp3"):
        if f.stem not in want:
            f.unlink()
    for t in TAILS:
        f = OUT / f"{key(t)}.mp3"
        if f.exists():
            continue
        heard = HEARD / f"{key(t)}.mp3"
        f.write_bytes(heard.read_bytes() if heard.exists() else level(speak(t)))
        print(f"  {key(t)} {t}{' (from the listening test)' if heard.exists() else ''}")
    size = sum(f.stat().st_size for f in OUT.glob("*.mp3"))
    print(f"{len(TAILS)} tails · {size / 1e3:.0f} KB in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
