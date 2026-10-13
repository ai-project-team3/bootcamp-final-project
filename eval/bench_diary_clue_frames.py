"""Diary: do the app's fixed clue frames (demo/DiaryClue.kt) presume anything happened? (10-05)

The same scenes as bench_diary_drawing.py, the frame the app would say for an unsaid drawn piece,
graded by the same presumption grader, 3 times each (the grader is noisy).

  py eval/bench_diary_clue_frames.py --yes-spend      (a few won)
"""
import asyncio
import sys

from bench_diary_drawing import CASES, grade

# id → the app's frame for the slot the judge picked (DiaryClue.kt clueQuestion)
FRAMES = {
    "d01": "미끄럼틀도 그렸네! 미끄럼틀 이야기 해 줄래?",
    "d02": "강아지도 그렸네! 강아지 이야기 해 줄래?",
    "d03": "아빠도 그렸네! 아빠는 오늘 뭐 했어?",
    "d04": "친구도 그렸네! 친구는 오늘 뭐 했어?",
    "d05": "할머니 집 그렸네! 오늘 어디 있었어?",
    "d07": "주사도 그렸네! 주사 이야기 해 줄래?",
    "d08": "그네도 그렸네! 그네 이야기 해 줄래?",
    "d09": "엄마도 그렸네! 엄마는 오늘 뭐 했어?",
    "d12": "코끼리도 그렸네! 코끼리 이야기 해 줄래?",
}
REPS = 3


async def main() -> None:
    if "--yes-spend" not in sys.argv:
        sys.exit("costs a few won — pass --yes-spend")
    flagged = 0
    total = 0
    for case in CASES:
        frame = FRAMES.get(case[0])
        if frame is None:
            continue
        votes = []
        for _ in range(REPS):
            g = await grade(case, {"ack": None, "expand": None, "question": frame})
            votes.append(g)
        n = sum(1 for g in votes if g.get("presumes"))
        total += REPS
        flagged += n
        why = next((g.get("what") for g in votes if g.get("presumes")), "")
        print(f"{case[0]}  {n}/{REPS}  {frame}" + (f"  ← {why}" if n else ""))
    print(f"\npresumes {flagged}/{total}")


if __name__ == "__main__":
    asyncio.run(main())
