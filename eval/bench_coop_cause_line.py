"""When next_slot is cause in a co-op story whose solution is already filled, what does the line ask? (#304 1 · 10-07)

A device round (10-07, 진웅): one tail answer filled the solution (「아빠가 잡아줬어」), the judge picked cause next,
and the line asked 「아빠는 왜 풍선을 잡아줬을까?」 — the why of the solution, not of the problem. The child said
「몰라」 · 「뭐를 넣어?」. Counts, before (git rev) and after (working files), alternating so drift hits both:
  problem  — the question carries a stem of the problem (낱말 앞 두 글자 · 「풍선」 「놓쳤」)
  solution — the question carries a stem found only in the solution (「아빠」 「잡아」) — the app drops these (#310)
  dropped  — the server would not send the line (blocked word · unknown placeholder) or the call failed

  py eval/bench_coop_cause_line.py [--rounds 3] [--before origin/main]

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
from app.schemas.turn import Line, TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 3
BEFORE = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else "origin/main"

# (reason, place, problem, solution, question just asked, the tail answer that filled the solution)
CASES = [
    ("done", "놀이공원", "풍선을 놓쳤어", "아빠가 잡아줬어", "그때 뭐 하고 있었어?", "아빠가 잡아줬어"),
    ("done", "놀이터", "그네에서 떨어졌어", "엄마가 반창고 붙여 줬어", "그다음엔 어떻게 됐어?", "엄마가 반창고 붙여 줬어"),
    ("done", "동물원", "아이스크림을 떨어뜨렸어", "삼촌이 새로 사 줬어", "누가 도와줬어?", "삼촌이 새로 사 줬어"),
    ("done", "바다", "모래성이 무너졌어", "동생이랑 다시 쌓았어", "그래서 어떻게 했어?", "동생이랑 다시 쌓았어"),
    ("soon", "소방서", "호스가 꼬일 것 같아", "소방관 아저씨가 풀어 줄 거야", "그러면 어떻게 될까?", "소방관 아저씨가 풀어 줄 거야"),
    ("dream", "우주", "로켓이 고장 났어", "외계인 친구가 고쳐 줬어", "그다음엔 무슨 일이 있었을까?", "외계인 친구가 고쳐 줬어"),
]


def stems(t: str) -> set[str]:
    """낱말 앞 두 글자 — 앱의 거름(CoopScenes.kt causeAsksOtherEvent)과 같은 잣대"""
    return {w[:2] for w in re.findall(r"[가-힣A-Za-z0-9]{2,}", t)}


def before_system() -> str:
    """The line prompt and its mode pieces as they were at BEFORE, read from git."""
    tmp = ROOT / "eval" / "_before_cause"
    (tmp / "prompt_modes").mkdir(parents=True, exist_ok=True)
    mode_dir = _blocks.MODE_DIR
    try:
        for p in ("line_prompt.md", *(f"prompt_modes/{n}" for n in ("line_story.md", "line_diary.md", "line_coop.md"))):
            (tmp / p).write_text(subprocess.run(["git", "show", f"{BEFORE}:eval/{p}"], cwd=ROOT, capture_output=True,
                                                text=True, encoding="utf-8", check=True).stdout, encoding="utf-8")
        _blocks.MODE_DIR = tmp / "prompt_modes"
        return _blocks.system_block(tmp / "line_prompt.md", "coop")
    finally:
        _blocks.MODE_DIR = mode_dir
        for f in sorted(tmp.rglob("*"), reverse=True):
            f.rmdir() if f.is_dir() else f.unlink()
        tmp.rmdir()


async def ask(system: str, req: TurnRequest, solution: str) -> str | None:
    v = JudgeResult(reason="ok", slot_1="solution", value_1=solution, story_ready=False,
                    next_slot="cause", next_reason="까닭이 아직 비었다")
    try:
        raw = await complete(system, turn.user(req, v), turn.schema(), name="mascot_line",
                             effort=settings.llm_effort_line, timeout_s=25)
    except Exception:                             # a failure is a result too
        return None
    line = Line.model_validate(raw)
    return None if turn.check(line) else (line.question or "")


async def main() -> None:
    sides = {"before": before_system(), "after": turn.system("coop")}
    count = {s: {"problem": 0, "solution": 0, "dropped": 0} for s in sides}
    for r in range(ROUNDS):
        for reason, place, problem, solution, q, said in CASES:
            req = TurnRequest(mode="coop", reason=reason, template=f"같이 만들기 · 장소 · {place}",
                              slots={"place": place, "problem": problem, "solution": solution},
                              question=q, utterance=said, turn=5)
            only_solution = stems(solution) - stems(problem)
            for side in (("before", "after") if r % 2 == 0 else ("after", "before")):
                question = await ask(sides[side], req, solution)
                if question is None:
                    count[side]["dropped"] += 1
                    print(f"[{side}] {place} ! 대사 없음")
                    continue
                qs = stems(question)
                p, s = bool(qs & stems(problem)), bool(qs & only_solution)
                count[side]["problem"] += p
                count[side]["solution"] += s
                print(f"[{side}] {reason} {place} 문제{'✓' if p else '✗'} 해결{'✗' if s else '·'} {question}")
    n = ROUNDS * len(CASES)
    for side, c in count.items():
        print(f"{side}: 문제 줄기 {c['problem']}/{n} · 해결 줄기 {c['solution']}/{n} · 대사 없음 {c['dropped']}/{n}")


if __name__ == "__main__":
    asyncio.run(main())
