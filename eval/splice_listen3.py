"""Listening test, round 3 (10-14 진웅) — cutting a line where a sentence ends.

Rounds 1–2: a word cut from its particle sounds wrong; 「{word+particle}」 + a baked rest rated 4.0 of 5. Many lines in
story and co-op mode put a changing word in the middle, but between two sentences (「다 만들었어! 제목은 『…』이야.」).
Cut at the 「!」 「?」 「.」, the parts are two short lines spoken one after the other — the baked sentence costs nothing,
and only the changing sentence goes to /tts. This round asks whether that join is heard.

  split  — the sentences voiced apart and joined with a pause (GAP_MS) where the sentence ended
  head   — two story-mode lines with the word + particle at the head (the round 1 shape, in another mode)

  backend/.venv/Scripts/python.exe eval/splice_listen3.py      → eval/tts_out/splice3/*.mp3 + meta.json
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
sys.path.insert(0, str(ROOT / "eval"))
from app.audio_level import RATE, _encode  # noqa: E402
from splice_listen import join, trim, voice  # noqa: E402

OUT = ROOT / "eval/tts_out/splice3"
GAP_MS = 280       # a sentence end, a little shorter than the 400 ms between two lines (Director.LINE_GAP_MS)

# (where it is said, the whole line, the parts — the first part listed is the one /tts would still make)
SPLIT = [
    ("동화 · 책 다 만들었을 때", "다 만들었어! 제목은 『용감한 콩이와 반짝 별』이야.", ["다 만들었어!", "제목은 『용감한 콩이와 반짝 별』이야."], 1),
    ("동화 · 보기 고르기", "이 중에서 골라 볼까? 숲속, 바닷가, 구름 위.", ["이 중에서 골라 볼까?", "숲속, 바닷가, 구름 위."], 1),
    ("동화 · 오또가 대신 고를 때", "그럼 오또가 고를게! 집에 돌아왔어!", ["그럼 오또가 고를게!", "집에 돌아왔어!"], 1),
    ("동화 · 조용할 때 다시 묻기", "천천히 생각해 봐. 어떤 일이 생겼어?", ["천천히 생각해 봐.", "어떤 일이 생겼어?"], -1),
    ("협업 · 보기 + 질문", "길을 잃어서, 배가 고파서, 다리가 아파서. 강아지가 왜 그랬을까?",
     ["길을 잃어서, 배가 고파서, 다리가 아파서.", "강아지가 왜 그랬을까?"], 1),
    ("책 · 미션 뒤 그 쪽", "촛불이 활활 타고 있어요. 촛불이 다 꺼졌어요.", ["촛불이 활활 타고 있어요.", "촛불이 다 꺼졌어요."], -1),
    ("동화 · 선물", "와, 민준이가 새로운 방법을 찾았어! 친구와 함께 해결하기!", ["와, 민준이가 새로운 방법을 찾았어!", "친구와 함께 해결하기!"], 0),
    ("동화 · 지난 주인공", "지난번 콩이랑 또 모험할래? 새 친구를 만들래?", ["지난번 콩이랑 또 모험할래?", "새 친구를 만들래?"], 0),
]
# (where, the whole line, head with particle, baked rest)
HEAD = [
    ("동화 · 쉽게 다시 묻기", "콩이가 뭘 원했을까?", "콩이가", "뭘 원했을까?"),
    ("동화 · 소리 만들기", "보리의 소리를 직접 만들어 볼까?", "보리의", "소리를 직접 만들어 볼까?"),
]


def write(name: str, pcm) -> float:
    (OUT / f"{name}.mp3").write_bytes(_encode(pcm))
    return round(len(pcm) / RATE, 2)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    cache: dict = {}
    meta = []
    for i, (where, line, parts, live) in enumerate(SPLIT):
        clips = [voice(p, cache) for p in parts]
        # live: which part still goes to /tts (-1 = none — both parts already made, e.g. a question just asked)
        paid = 0 if live < 0 else len(parts[live])
        meta.append({"id": f"split{i}", "line": line, "where": where, "kind": "split", "variants": {
            "full": {"name": "통째", "secs": write(f"split{i}_full", trim(voice(line, cache))), "tts_chars": len(line), "parts": [line]},
            "split": {"name": "문장 끝에서 나눔", "secs": write(f"split{i}_split", join(clips, [GAP_MS])), "tts_chars": paid,
                      "parts": [f"{p}{' (/tts)' if j == live else ' (구움 · 다시 씀)'}" for j, p in enumerate(parts)]},
        }})
    for i, (where, line, head, rest) in enumerate(HEAD):
        meta.append({"id": f"head{i}", "line": line, "where": where, "kind": "head", "variants": {
            "full": {"name": "통째", "secs": write(f"head{i}_full", trim(voice(line, cache))), "tts_chars": len(line), "parts": [line]},
            "head": {"name": "조사까지 + 꼬리", "secs": write(f"head{i}_head", join([voice(head, cache), voice(rest, cache)], [70])),
                     "tts_chars": len(head), "parts": [f"{head} (/tts)", f"{rest} (구움)"]},
        }})
    (OUT / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")
    for m in meta:
        print(m["line"], " · ".join(f"{k} {v['secs']}s /tts {v['tts_chars']}자" for k, v in m["variants"].items()))


if __name__ == "__main__":
    main()
