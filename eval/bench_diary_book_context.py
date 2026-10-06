# -*- coding: utf-8 -*-
"""Diary book: a short answer that leans on its question reads as a bare line (10-06 device round).

「할아버지랑 감 땄어」 then 「같이 먹었어」 came out as 「같이 먹었어요.」, and the wish 「자전거 탈 거야」 (asked
「내일은 뭐 하고 싶어?」) as 「자전거 탈 거예요.」. This runs the same requests through the server's own story code
with a given system prompt, three times each, and writes every caption plus the counts the rules can check.

    py eval/bench_diary_book_context.py --prompt <story_prompt_diary.md> --label before|after [--n 3]

Keys come from an .env read by the backend settings (--env, default the main checkout's) — nothing is printed.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app import config                                # noqa: E402
from app.llm.client import complete                   # noqa: E402
from app.routers import story as story_route          # noqa: E402
from app.schemas.story import StoryRequest, StoryResult   # noqa: E402
from prompt_block import system_block                 # noqa: E402

PAST_IN_WISH = re.compile(r"(했어요|었어요|았어요|였어요)\.?$")
TIME = re.compile(r"내일|다음에|다음엔|또 ")
DRAMA = re.compile(r"마침내|알고 보니")
EMPTY = re.compile(r"아직 (?:듣지 못했|못 들었)")


def counts(case: dict, caps: list[str]) -> dict:
    want = case["want"]
    sentences = [x.strip() for c in caps for x in re.split(r"(?<=[.!?])\s+", c) if x.strip()]
    keep = case["req"].get("keep")
    last = caps[-1] if caps else ""
    text = " ".join(caps)
    return {
        "long": sum(len(s.split()) > 15 for s in sentences),                       # rule 2: 15 eojeol
        "not_yo": sum(not re.search(r"요[.!?]?$", s) for s in sentences),          # rule 2: -어요
        "wish_past": int(bool(keep) and bool(PAST_IN_WISH.search(last.strip()))),  # rule 3
        "drama": len(DRAMA.findall(text)),                                         # rule 4 · 5 (none of these days earn it)
        "empty_end_missing": int(want.get("empty_end", False) and not EMPTY.search(text)),   # rule 1
        "invented": [w for w in want.get("no_new", []) if w in text],              # rule 1, by word list
        "carry": {w: w in " ".join(caps[-3:]) for w in want.get("carry", [])},     # target: the bare line gets its who / what
        "keep_time": (bool(TIME.search(last)) if want.get("keep_time") else None), # target: 「내일은 …」
    }


async def one(case: dict, system: str) -> dict:
    req = StoryRequest.model_validate(case["req"])
    t = time.perf_counter()
    raw = await complete(system, story_route.user(req), story_route.schema(), name="story",
                         effort=config.settings.llm_effort_story, max_output_tokens=6000,
                         timeout_s=config.settings.story_deadline_s)
    result = StoryResult.model_validate(raw)
    caps = [s.caption for s in result.scenes]
    return {"id": case["id"], "s": round(time.perf_counter() - t, 1),
            "rejected": story_route.check(result, req.mode, req.pages), "captions": caps, **counts(case, caps)}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--n", type=int, default=3)
    ap.add_argument("--env", default="D:/bootcamp-final-project/.env")
    ap.add_argument("--out", default=str(EVAL / "results_diary_book_context"))
    a = ap.parse_args()
    loaded = type(config.settings)(_env_file=a.env)
    for f in type(config.settings).model_fields:
        setattr(config.settings, f, getattr(loaded, f))
    system = system_block(Path(a.prompt))
    cases = [json.loads(l) for l in (EVAL / "fixtures_book_diary.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    rows = await asyncio.gather(*[one(c, system) for c in cases for _ in range(a.n)], return_exceptions=True)
    out = Path(a.out); out.mkdir(exist_ok=True)
    keep = [r if isinstance(r, dict) else {"error": f"{type(r).__name__}: {r}"} for r in rows]
    (out / f"{a.label}.json").write_text(json.dumps(keep, ensure_ascii=False, indent=1), encoding="utf-8")
    ok = [r for r in keep if "error" not in r]
    print(a.label, "runs", len(ok), "errors", len(keep) - len(ok),
          "| long", sum(r["long"] for r in ok), "not_yo", sum(r["not_yo"] for r in ok),
          "wish_past", sum(r["wish_past"] for r in ok), "drama", sum(r["drama"] for r in ok),
          "empty_end_missing", sum(r["empty_end_missing"] for r in ok),
          "invented", sum(len(r["invented"]) for r in ok), "rejected", sum(bool(r["rejected"]) for r in ok))


if __name__ == "__main__":
    asyncio.run(main())
