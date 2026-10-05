# -*- coding: utf-8 -*-
"""Does asking /story for a cover title cost the book anything? (#86 · 10-05)

The app stopped asking the child for a title (#85) and reads `title` from /story when it comes. Adding the
field changes the story prompt and schema, so: same fixtures, old and new prompt, same day — rejected books
(the server's own check), time, and whether the titles keep the rule (4-12 letters, a noun phrase, no
punctuation, no new placeholder).

    py eval/bench_story_title.py --prompt <old story_prompt.md> --schema <old story_schema.json> --label before
    py eval/bench_story_title.py --label after
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import statistics
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import settings                       # noqa: E402
from app.llm.client import complete                   # noqa: E402
from app.routers import story as story_route          # noqa: E402
from app.schemas.story import StoryRequest, StoryResult   # noqa: E402
from prompt_block import system_block                 # noqa: E402

STORIES = [json.loads(l) for l in (EVAL / "fixtures_story.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]


def title_ok(t: str | None) -> bool:
    if not t:
        return False
    bare = re.sub(r"\{[^}]+\}", "가", t)              # a placeholder counts as one letter
    return 4 <= len(bare.replace(" ", "")) <= 12 and not re.search(r"[.!?。…]", t)


async def one(sys_text: str, schema: dict, s: dict) -> tuple[str | None, str | None, float]:
    req = StoryRequest(mode="story", slots=s["slots"])
    t = time.monotonic()
    raw = await complete(sys_text, story_route.user(req), schema, name="story",
                         effort=settings.llm_effort_story, max_output_tokens=6000, timeout_s=settings.story_deadline_s)
    dt = time.monotonic() - t
    r = StoryResult.model_validate(raw)
    why = story_route.check(r, "story", None)
    r = story_route.stamp(r, req)
    return why, r.title, dt


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", type=Path, default=EVAL / "story_prompt.md")
    ap.add_argument("--schema", type=Path, default=EVAL / "story_schema.json")
    ap.add_argument("--label", required=True)
    ap.add_argument("--runs", type=int, default=3)
    a = ap.parse_args()
    sys_text = system_block(a.prompt)
    schema = json.loads(a.schema.read_text(encoding="utf-8"))
    rows = []
    for _ in range(a.runs):
        rows += await asyncio.gather(*(one(sys_text, schema, s) for s in STORIES))
    rejected = [w for w, _, _ in rows if w]
    titles = [t for _, t, _ in rows]
    times = [d for _, _, d in rows]
    print(f"{a.label}: {len(rows)} books · rejected {len(rejected)} {rejected} · "
          f"p50 {statistics.median(times):.1f}s max {max(times):.1f}s · effort {settings.llm_effort_story}")
    if any(titles):
        print(f"  titles keeping the rule {sum(map(title_ok, titles))}/{len(titles)}")
        for t in titles:
            print(f"   {'ok ' if title_ok(t) else 'BAD'} {t}")


if __name__ == "__main__":
    asyncio.run(main())
