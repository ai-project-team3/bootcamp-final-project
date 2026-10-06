"""When next_slot is reaction, does the line ask how the child felt? (#100 9번 · 10-06)

The app reads `reaction` in diary and co-op as 「그때 마음」 (the 「그때 마음」 page, the ladder
「설레, 떨려, 궁금해. 어떤 마음이야?」). A device round got 「불이 난 소방서에서 어떤 일이 생길 것 같아?」
for reaction in a co-op `soon` story, and the judge then turned down the child's 「무서워… 근데 신나」.
Counts how many reaction questions ask a feeling (마음 · 기분 · 느낌), before (git rev) and after
(working files), alternating so drift hits both.

  py eval/bench_reaction_question.py [--rounds 3] [--before origin/main]

Costs a few dozen won (gpt-6-luna · 2 × rounds × cases). Keys come from .env; nothing is printed.
"""
import asyncio
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.config import settings  # noqa: E402
from app.llm.client import complete  # noqa: E402
from app.llm.judge_prompt import _blocks  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.judge import JudgeResult  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 3
BEFORE = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else "origin/main"
FEELING = re.compile(r"마음|기분|느낌")

# (mode, reason, slots, question just asked, what the child said)
CASES = [
    ("coop", "soon", {"place": "소방서", "problem": "불 끄기"}, "소방서에 가면 뭘 해 보고 싶어?", "불 꺼"),
    ("coop", "soon", {"place": "동물원", "problem": "기린한테 먹이 주기"}, "동물원에서 뭐 하고 싶어?", "기린 밥 줄래"),
    ("coop", "done", {"place": "바다", "problem": "모래성 만들기"}, "바다에서 뭐 했어?", "모래성 만들었어"),
    ("diary", None, {"place": "놀이터", "problem": "미끄럼틀 탔다"}, "거기서 뭐 했어?", "미끄럼틀 탔어"),
    ("diary", None, {"place": "할머니 집", "problem": "송편 만들었다"}, "할머니 집에서 뭐 했어?", "송편 만들었어"),
]


def before_system(mode: str) -> str:
    """The line prompt and its mode pieces as they were at BEFORE, read from git."""
    tmp = ROOT / "eval" / "_before"
    (tmp / "prompt_modes").mkdir(parents=True, exist_ok=True)
    mode_dir = _blocks.MODE_DIR
    try:
        for p in ("line_prompt.md", *(f"prompt_modes/{n}" for n in ("line_story.md", "line_diary.md", "line_coop.md"))):
            src = "eval/" + p
            (tmp / p).write_text(subprocess.run(["git", "show", f"{BEFORE}:{src}"], cwd=ROOT, capture_output=True,
                                                text=True, encoding="utf-8", check=True).stdout, encoding="utf-8")
        _blocks.MODE_DIR = tmp / "prompt_modes"
        return _blocks.system_block(tmp / "line_prompt.md", mode)
    finally:
        _blocks.MODE_DIR = mode_dir
        for f in sorted(tmp.rglob("*"), reverse=True):
            f.rmdir() if f.is_dir() else f.unlink()
        tmp.rmdir()


async def ask(system: str, req: TurnRequest) -> str:
    v = JudgeResult(reason="ok", story_ready=False, next_slot="reaction", next_reason="무슨 일이 생겼는지 나왔으니 결과를 묻는다")
    try:
        raw = await complete(system, turn.user(req, v), turn.schema(), name="mascot_line",
                             effort=settings.llm_effort_line, timeout_s=25)
    except Exception as e:                        # a failure is a result too
        return f"! {type(e).__name__}"
    return raw.get("question") or ""


async def main() -> None:
    sides = {"before": {m: before_system(m) for m in ("coop", "diary")},
             "after": {m: turn.system(m) for m in ("coop", "diary")}}
    hit = {"before": 0, "after": 0}
    for r in range(ROUNDS):
        for mode, reason, slots, q, said in CASES:
            req = TurnRequest(mode=mode, slots=slots, asked_slot="problem", question=q, utterance=said, turn=3,
                              reason=reason)
            for side in (("before", "after") if r % 2 == 0 else ("after", "before")):
                question = await ask(sides[side][mode], req)
                ok = bool(FEELING.search(question))
                hit[side] += ok
                print(f"[{side}] {mode}/{reason or '-'} {slots['place']} {'✓' if ok else '✗'} {question}")
    n = ROUNDS * len(CASES)
    print(f"\n마음을 물은 질문 — before {hit['before']}/{n} · after {hit['after']}/{n}")


if __name__ == "__main__":
    asyncio.run(main())
