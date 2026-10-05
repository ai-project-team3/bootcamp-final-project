# -*- coding: utf-8 -*-
"""Would one LLM call for judge + line be faster than two in a row? Latency only. (10-05 · turn-time goal)

/turn runs the judge, then the mascot line on the judge's answer — about 3.7 s inside the server
(10-05, LAN). A merged call reads both instructions and writes both answers in one structured output.
This times it against today's two calls, same model and effort, same inputs, alternating order. It does
NOT judge quality: if merging is not clearly faster there is nothing to measure further.

    py eval/bench_turn_merged.py [--rounds 5]
"""
from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402
from app.llm import judge_prompt  # noqa: E402
from app.llm.client import complete  # noqa: E402
from app.routers import judge, turn  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

CASES = [
    ({"place": "풍선마을"}, "problem", "풍선마을에서 어떤 일이 생길까?", "바늘괴물이 나타났어"),
    ({"place": "풍선마을", "problem": "바늘괴물이 나타났어"}, "problem", "바늘괴물이 나타나자 어떤 일이 생겼어?",
     "바늘괴물이 풍선 마을을 돌아다니면서 풍선을 마구 터뜨렸어"),
    ({"place": "풍선마을", "problem": "바늘괴물이 풍선을 터뜨렸어"}, "cause", "바늘괴물은 왜 풍선을 터뜨렸을까?", "터트리는 게 재밌어서"),
]


def merged_schema() -> dict:
    def body(s: dict) -> dict:                      # the schema files carry name/description on top
        return {k: v for k, v in s.items() if k not in ("name", "description")}
    return {"name": "turn_merged", "type": "object", "additionalProperties": False, "required": ["judge", "line"],
            "properties": {"judge": body(judge_prompt.schema()), "line": body(turn.schema())}}


def merged_system() -> str:
    return (judge_prompt.system() + "\n\n=== 이어서 같은 답 안에서 ===\n"
            "판정(judge)을 먼저 쓰고, 그 판정을 보고 마스코트 대사(line)를 쓴다. 대사 규칙은 아래와 같다.\n\n"
            + turn.system())


async def two_calls(req: TurnRequest) -> float:
    t = time.monotonic()
    v = await judge.run(req)
    await turn.run_line(req, v)
    return time.monotonic() - t


async def one_call(req: TurnRequest) -> float:
    t = time.monotonic()
    user = judge_prompt.user(req) + "\n---\n" + turn.user(req, None)
    await complete(merged_system(), user, merged_schema(), name="turn_merged",
                   effort=settings.llm_effort_judge, max_output_tokens=1200)
    return time.monotonic() - t


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=5)
    a = ap.parse_args()
    two, one = [], []
    for r in range(a.rounds):
        for slots, asked, q, u in CASES:
            req = TurnRequest(mode="story", slots=slots, asked_slot=asked, question=q, utterance=u, turn=2)
            pair = [two_calls(req), one_call(req)] if r % 2 == 0 else [one_call(req), two_calls(req)]
            a1 = await pair[0]; a2 = await pair[1]
            (two.append(a1), one.append(a2)) if r % 2 == 0 else (one.append(a1), two.append(a2))
    print(f"two calls (today) p50 {statistics.median(two):.2f}s · max {max(two):.2f}s · n={len(two)}")
    print(f"one merged call   p50 {statistics.median(one):.2f}s · max {max(one):.2f}s · n={len(one)}")


if __name__ == "__main__":
    asyncio.run(main())
