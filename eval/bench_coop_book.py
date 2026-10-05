# -*- coding: utf-8 -*-
"""Does a co-op book still come out with empty pages? (10-05 device round)

A co-op 「동물원 다녀왔어요」 book came back with three pages running 「…은 아직 듣지 못했어요」 though the
slots were filled: the server read the day book's pages with the story meanings (FAIL = 해 봤지만 잘 안 된다).
This runs the same requests through the server's own prompt code and counts the empty-place lines.

    py eval/bench_coop_book.py                        # this checkout's story.py + story_prompt_coop.md
    py eval/bench_coop_book.py --story-meanings --prompt <old story_prompt_coop.md> --label before

Keys come from the repo's .env, read by the backend settings as usual — nothing is printed.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
# OTTO_BACKEND=<another checkout>/backend runs that server code instead (a true before, e.g. origin/main)
sys.path.insert(0, os.environ.get("OTTO_BACKEND") or str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import settings                       # noqa: E402
from app.llm.client import complete                   # noqa: E402
from app.routers import story as story_route          # noqa: E402
from app.schemas.story import StoryRequest, StoryResult   # noqa: E402
from prompt_block import system_block                 # noqa: E402

EMPTY = re.compile(r"아직 (?:듣지 못했|못 들었)|그날 알게 될 거예요")


async def one(case: dict, system: str) -> dict:
    req = StoryRequest.model_validate(case["req"])
    t = time.perf_counter()
    raw = await complete(system, story_route.user(req), story_route.schema(), name="story",
                         effort=settings.llm_effort_story, max_output_tokens=6000, timeout_s=settings.story_deadline_s)
    result = StoryResult.model_validate(raw)
    caps = [s.caption for s in result.scenes]
    return {"id": case["id"], "s": round(time.perf_counter() - t, 1), "rejected": story_route.check(result, req.mode, req.pages),
            "empty_pages": sum(bool(EMPTY.search(c)) for c in caps), "title": result.title, "captions": caps}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", help="another story_prompt_coop.md (e.g. the old one) instead of this checkout's")
    ap.add_argument("--story-meanings", action="store_true", help="read day-book pages with the story meanings (before 10-05)")
    ap.add_argument("--label", default="after")
    ap.add_argument("--fixtures", default=str(EVAL / "fixtures_book_coop.jsonl"))
    a = ap.parse_args()
    if a.story_meanings and hasattr(story_route, "DAY_KIND_MEANING"):
        story_route.DAY_KIND_MEANING = {}
    system = system_block(Path(a.prompt)) if a.prompt else story_route.system("coop")
    cases = [json.loads(l) for l in Path(a.fixtures).read_text(encoding="utf-8").splitlines() if l.strip()]
    rows = [await one(c, system) for c in cases]
    for r in rows:
        print(f"\n[{a.label}] {r['id']} · {r['s']}s · 빈 쪽 {r['empty_pages']} · 버림 {r['rejected']} · 『{r['title']}』")
        for i, c in enumerate(r["captions"], 1):
            print(f"  {i}. {c}")
    print(f"\n[{a.label}] 빈 쪽 합계 {sum(r['empty_pages'] for r in rows)} / {sum(len(r['captions']) for r in rows)}쪽 · "
          f"effort {settings.llm_effort_story}")


if __name__ == "__main__":
    asyncio.run(main())
