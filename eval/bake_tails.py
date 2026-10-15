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
    # story · after a heard name, place, vehicle or the child's call (StoryBank · #502)
    "뭐가 제일 먼저 보여?",
    "사는 친구들은 뭘 보면서 놀까?",
    "뭐가 보여?",
    "흔들었구나!",
    "소리쳤을까, 조용히 있었을까?",
    "놀랐을까?",
    "왜 그랬을까?",
    "얼굴을 잘 봐.",
    "기분이 어땠을까?",
    "무엇을 하고 싶었을까?",
    "뭘 원했을까?",
    "마음을 한번 생각해 볼까?",
    "흔드는 것 말고 다른 방법은 없었을까?",
    "어울리는 이름은 뭘까?",
    "보면 어떤 소리가 떠올라?",
    "좋아할 이름이 뭘까?",
    "자기 이름을 알려 준대!",
    "같이 어디를 지나가 볼까?",
    "어디로 먼저 갈까?",
    "좋아하는 놀이가 뭘까?",
    "제일 좋아할 곳은 어디일까?",
    "크게 말할까, 작게 말할까?",
    "뭐라고 말하면 멈출까?",
    "옆에 누가 있지?",
    "웃으려면 뭐가 있으면 좋을까?",
    "뭐가 필요할까?",
    "뭘 찾고 있을까?",
    "도와주려면 뭘 챙겨 가야 할까?",
    "도우려면 누가 제일 잘할까?",
    "기운 나게 뭘 해 주면 좋을까?",
    "좋아할 게 뭘까?",
    "뭘 줬을까, 뭐라고 했을까?",
    "먼저 말을 걸었을까?",
    "부끄러웠을까?",
    "고개를 저었어.",
    "무서웠을까?",
    "좋아할 걸 해 볼까?",
    "계속 세게 흔들다가 어떻게 됐을까?",
    "흔들렸을까?",
    "괜찮았을까?",
    "무엇을 알게 됐을까?",
    "다시 친해지려면 어떻게 하면 좋을까?",
    "뭘 하면 좋을까?",
    "쳐다봤을까?",
    "마음도 들어 볼까?",
    "나쁜 마음이었을까?",
    "붙잡고 물어봤어.",
    "뭐라고 했을까?",
    "뭘 하면 좋아할까?",
    '"누가 이기나 보자!" 했어.',
    "뭘 잘해?",
    "뭐 하고 놀까?",
    "얼굴을 떠올려 봐.",
    "웃었을까?",
    "오늘 무엇을 알게 됐을까?",
    # story · sentences that never change, inside lines whose other sentence does (StoryBank)
    "창밖을 봐!",
    "그다음엔 어떻게 됐을 것 같아?",
    "어떤 기분인 것 같아?",
    "뭘까?",
    "무엇 때문이었을까?",
    "오해가 풀렸어!",
    "뭘로 겨룰까?",
    "어떤 표정일까?",
    # story · partner · drawing · sound · shelf (Scenes · StoryDrawing · StorySound · StoryTurn)
    "함께구나!",
    "잘 부탁드려요!",
    "소리를 직접 만들어 볼까?",
    "어떻게 생겼을까?",
    "크레용으로 그려 줄래?",
    "이 모습 그대로 책에 넣을게.",
    "오늘은 여기까지!",
    "다른 생각도 들려줄래?",
    # co-op · after a heard place, thing, person or the picked story (CoopQuestions · CoopTemplatePack · CoopSteps · #502)
    "가면 어디부터 가 보고 싶어?",
    "경기장에는 뭐가 있을까?",
    "그 일을 왜 할 것 같아?",
    "그거 할 때 왜 신났을까?",
    "그때 어떻게 할까?",
    "그때 어떻게 했을까?",
    "누구를 만날까?",
    "누구를 만났어?",
    "누구를 만났을까?",
    "누구한테 말했어?",
    "되면 어디에 가 보고 싶어?",
    "만난 사람 있어?",
    "무슨 건물에서 일할까?",
    "무슨 일이 생겼을까?",
    "무슨 일이 생기면 재밌을까?",
    "무슨 일이 생길까?",
    "뭐 때문에 그랬을까?",
    "뭐 때문에 그럴까?",
    "뭐 만져 봤어?",
    "뭐 봤어?",
    "뭐라고 할까?",
    "뭐라고 했어?",
    "뭘 볼까?",
    "뭘 봤을까?",
    "배우는 곳은 어떤 데일까?",
    "어떤 얼굴이었어?",
    "어떤 얼굴이었을까?",
    "어떤 얼굴일까?",
    "어떻게 됐어?",
    "어떻게 됐을까?",
    "어떻게 될까?",
    "얼굴이 어땠어?",
    "얼굴이 어땠을 것 같아?",
    "없으면 어떻게 될까?",
    "왜 그랬을 것 같아?",
    "왜 그럴까?",
    "이야기에서 제일 궁금한 게 뭐야?",
    "일하는 곳에서 뭐가 보였어?",
    "일하다가 누굴 만났을까?",
    "제일 가 보고 싶은 데는 어디야?",
    "제일 열심히 한 게 뭐야?",
    "제일 오래 있었던 데는 어디야?",
    "하루 종일 뭐 할까?",
    "하면서 뭐가 제일 재밌었어?",
    "한 곳은 어떤 곳이었어?",
    "혼자 했어, 같이 했어?",
    # co-op · the question after spoken choices — 「A, B, C. | {question}」 (choices are shuffled on purpose · 근거 #10 · #27)
    "그다음에 뭐 했을까?",
    "누가 같이 갈까?",
    "누가 도와 달라고 했을까?",
    "누구 때문일까?",
    "누구랑 갈까?",
    "누구랑 갔어?",
    "다음엔 뭐 할까?",
    "무슨 일을 할까?",
    "뭐 하고 싶어?",
    "뭐 해 봤어?",
    "뭐가 나올까?",
    "뭐가 제일 하고 싶어?",
    "어디가 좋아?",
    "어디가 좋았어?",
    "어디부터 갈까?",
    "어디서 배울까?",
    "어디서 열릴까?",
    "어디서 일할까?",
    "어디서 했어?",
    "어디에 가 봤어?",
    "어떤 마음이야?",
    "어떤 마음이었어?",
    "어떤 마음이었을까?",
    "어떤 일이 생길까?",
    "어떤 일이 있었어?",
    "어떻게 고쳤을까?",
    "어떻게 끝날까?",
    "어떻게 끝났을까?",
    "어떻게 할까?",
    "어떻게 했어?",
    "어떻게 했을까?",
    "오늘은 어디 있었어?",
    "왜 필요할까?",
    "왜 할까?",
    "집에서 뭐 했어?",
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
