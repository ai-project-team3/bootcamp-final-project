# -*- coding: utf-8 -*-
"""Does a mission page stop before the mission, or write its result? (#100 10번 · 10-06)

The app puts the result line on a mission page only after the child finishes the mission
(「촛불이 다 꺼졌어요.」). A device round got 「촛불을 후~ 불었어요!」 on the C1 page itself — the child had
already said they blew it, and the book said it before the child did it. Each case names its mission
page and a pattern for 「the action is already done」; this counts how often that page says it.

    py eval/bench_mission_page.py --runs 3 --label before
Keys come from the repo's .env, read by the backend settings as usual — nothing is printed.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import sys
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import settings                       # noqa: E402
from app.llm.client import complete                   # noqa: E402
from app.routers import story as story_route          # noqa: E402
from app.schemas.story import StoryRequest, StoryResult   # noqa: E402


async def one(case: dict) -> tuple[bool, str]:
    req = StoryRequest.model_validate(case["req"])
    raw = await complete(story_route.system(req.mode), story_route.user(req), story_route.schema(), name="story",
                         effort=settings.llm_effort_story, max_output_tokens=6000, timeout_s=settings.story_deadline_s)
    caps = [s.caption for s in StoryResult.model_validate(raw).scenes]
    page = caps[case["mission_page"] - 1] if len(caps) >= case["mission_page"] else ""
    return bool(re.search(case["done"], page)), page


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--label", default="after")
    a = ap.parse_args()
    cases = [json.loads(l) for l in (EVAL / "fixtures_mission_page.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    bad = 0
    for c in cases:
        for r in range(a.runs):
            done, page = await one(c)
            bad += done
            print(f"[{a.label}] {c['id']} run{r + 1} {'✗ 결과까지' if done else '✓ 직전'} · {page}")
    print(f"\n[{a.label}] 미션 쪽이 결과까지 쓴 것 {bad} / {len(cases) * a.runs}")


if __name__ == "__main__":
    asyncio.run(main())
