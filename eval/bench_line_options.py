"""Does asking the line model for answer options (#79) cost time or the line's quality? (10-02)

Same inputs, the line prompt and schema before the change (git HEAD~ copies, read from git) and
after (the working files), alternating so drift hits both. Prints p50 per side and every
options list, to read by eye — the options must fit story_so_far, be three different short
noun phrases, and never come in diary.

  py eval/bench_line_options.py [--rounds 4] [--before <git rev>]

Costs a few dozen won (gpt-6-luna · 2 × rounds × cases).
"""
import asyncio
import json
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.llm.client import complete  # noqa: E402
from app.llm.judge_prompt import _blocks  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.judge import JudgeResult  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 4
BEFORE = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else "origin/main"

CASES = [
    ("story", {"place": "바닷속"}, "누굴 만났어?", "문어", "newcomer"),
    ("story", {"place": "바닷속", "newcomer": "문어"}, "그런데 무슨 일이 생겼어?", "몰라", "problem"),
    ("story", {}, "오늘은 어디로 가 볼까?", "음…", "place"),
    ("coop", {"place": "소방서"}, "소방관은 무슨 일을 할까?", "불 꺼", "solution"),
    ("diary", {"place": "놀이터"}, "거기서 뭐 했어?", "미끄럼틀", "solution"),
]


def git_file(rev: str, path: str) -> str:
    return subprocess.run(["git", "show", f"{rev}:{path}"], cwd=ROOT, capture_output=True,
                          text=True, encoding="utf-8", check=True).stdout


def old_system_and_schema() -> tuple[str, dict]:
    tmp = ROOT / "eval" / "_line_prompt_before.md"
    tmp.write_text(git_file(BEFORE, "eval/line_prompt.md"), encoding="utf-8")
    try:
        system = _blocks.system_block(tmp)
    finally:
        tmp.unlink()
    s = json.loads(git_file(BEFORE, "eval/line_schema.json"))
    return system, {k: v for k, v in s.items() if k not in ("name", "description")}


async def one(system: str, schema: dict, req: TurnRequest, v: JudgeResult) -> tuple[float, dict | None]:
    t = time.monotonic()
    try:
        raw = await complete(system, turn.user(req, v), schema, name="mascot_line", effort="none", timeout_s=25)
    except Exception as e:                        # counted, not fatal — a failure is a result too
        print(f"  ! {type(e).__name__}: {e}")
        return time.monotonic() - t, None
    return time.monotonic() - t, raw


async def main() -> None:
    old = old_system_and_schema()
    new = (turn.system(), turn.schema())
    times = {"before": [], "after": []}
    fails = {"before": 0, "after": 0}
    for r in range(ROUNDS):
        for mode, slots, q, said, nxt in CASES:
            req = TurnRequest(mode=mode, slots=slots, asked_slot=nxt, question=q, utterance=said, turn=3,
                              reason="soon" if mode == "coop" else None)
            v = JudgeResult(reason="ok", story_ready=False, next_slot=nxt)
            order = [("before", old), ("after", new)] if r % 2 == 0 else [("after", new), ("before", old)]
            for side, (system, schema) in order:
                dt, raw = await one(system, schema, req, v)
                if raw is None:
                    fails[side] += 1
                    continue
                times[side].append(dt)
                if side == "after" and r == 0:
                    print(f"[{mode}] {said!r} → q={raw.get('question')!r} options={raw.get('options')}")
    print()
    for side, xs in times.items():
        print(f"{side:<7} p50 {statistics.median(xs):.2f}s  mean {statistics.mean(xs):.2f}s  max {max(xs):.2f}s  n={len(xs)} · failed {fails[side]}")


if __name__ == "__main__":
    asyncio.run(main())
