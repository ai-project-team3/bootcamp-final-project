"""Where /turn's time goes, and did story_so_far (10-01 #50) make the line slower?

/turn runs the judge, then the mascot line, one after the other. The live server averaged 4.3 s
on 10-01 evening. This calls the same two functions the server calls (backend code, local .env
keys — same vendor, same model) and times each, with the line run twice per round: with the
story_so_far line in its input and without it, in alternating order so drift hits both.

  py eval/bench_turn_split.py [--rounds 6]

Costs a few dozen won (gpt-6-luna, about 3 × rounds × 3 calls).
"""
import asyncio
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.routers import judge, turn  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 6
TEXTS = {
    "short": "문어",
    "medium": "문어가 먹물을 쏘아서 바다가 깜깜해졌어",
    "long": ("바닷속에 갔는데 문어가 갑자기 먹물을 쏘아서 바다가 깜깜해졌어 그래서 나는 무서워서 "
             "친구 손을 꼭 잡았는데 그때 반짝이는 해파리가 와서 길을 밝혀 줬어"),
}
SLOTS = {"place": "바닷속", "companion": "{친구1}", "newcomer": "문어"}

_user = turn.user


def without_so_far(req, v):
    return "\n".join(l for l in _user(req, v).splitlines() if not l.startswith("story_so_far:"))


async def timed(coro):
    t = time.monotonic()
    out = await coro
    return time.monotonic() - t, out


async def main() -> None:
    res: dict[str, dict[str, list[float]]] = {}
    for k, text in TEXTS.items():
        r = res.setdefault(k, {"judge": [], "line+so_far": [], "line-so_far": []})
        for i in range(ROUNDS):
            req = TurnRequest(mode="story", slots=SLOTS, asked_slot="problem",
                              question="그런데 무슨 일이 생겼어?", utterance=text, turn=4)
            tj, v = await timed(judge.run(req))
            r["judge"].append(tj)
            order = [True, False] if i % 2 == 0 else [False, True]
            for with_it in order:
                turn.user = _user if with_it else without_so_far
                tl, _ = await timed(turn.run_line(req, v, budget_s=25))
                r["line+so_far" if with_it else "line-so_far"].append(tl)
            turn.user = _user
            print(f"  {k} {i + 1}/{ROUNDS}", flush=True)

    print(f"\n{'answer':<8}{'part':<14}{'p50':>7}{'mean':>7}{'max':>7}")
    for k, r in res.items():
        for part, xs in r.items():
            print(f"{k:<8}{part:<14}{statistics.median(xs):>6.2f}s{statistics.mean(xs):>6.2f}s{max(xs):>6.2f}s")


if __name__ == "__main__":
    asyncio.run(main())
