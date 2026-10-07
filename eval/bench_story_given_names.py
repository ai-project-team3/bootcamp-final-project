"""Measure caption name retention before/after #250's story-only decision.

Run the same fixtures and settings with --prompt pointing at each version.
Names must occur in captions, not merely in a title or hidden asset keywords.
Uses the production request, schema and rejection checks; never prints credentials.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import statistics
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import Settings, settings  # noqa: E402
from app.llm.client import LLMError, complete  # noqa: E402
from app.routers import story  # noqa: E402
from app.schemas.story import Page, StoryRequest, StoryResult  # noqa: E402
from prompt_block import system_block  # noqa: E402

PAGES = [Page(kind=k) for k in ("DEPART", "SHAKE", "MEET", "TALK", "TALK", "TOGETHER")]


def score(book: StoryResult, req: StoryRequest, names: list[str]) -> dict:
    captions = "\n".join(scene.caption for scene in book.scenes)
    missing = [name for name in names if name not in captions]
    rejection = story.check(book, "story", req.pages)
    return {"missing": missing, "rejection": rejection, "kept": not missing and rejection is None}


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", type=Path, default=EVAL / "story_prompt.md")
    ap.add_argument("--fixtures", type=Path, default=EVAL / "fixtures_story_given_names.jsonl")
    ap.add_argument("--env", type=Path, help="Existing local settings file; values are never printed")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--concurrency", type=int, default=2)
    a = ap.parse_args()
    if a.env:
        other = Settings(_env_file=a.env)
        for key in ("openai_api_key", "openai_base_url", "llm_provider", "llm_model",
                    "llm_effort_story", "story_deadline_s"):
            setattr(settings, key, getattr(other, key))
    if not settings.openai_api_key:
        print("Evaluation cannot run: API credential is not configured.")
        return 2
    cases = [json.loads(line) for line in a.fixtures.read_text(encoding="utf-8").splitlines() if line.strip()]
    system = system_block(a.prompt)
    metadata = {"model": settings.llm_model, "effort": settings.llm_effort_story,
                "prompt_sha256": hashlib.sha256(system.encode()).hexdigest(),
                "fixtures_sha256": hashlib.sha256(a.fixtures.read_bytes()).hexdigest()}
    gate = asyncio.Semaphore(a.concurrency)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open("w", encoding="utf-8") as output:
        async def one(case: dict, run: int) -> dict:
            async with gate:
                req = StoryRequest(mode="story", slots=case["slots"], pages=PAGES)
                started = time.monotonic()
                row = {**metadata, "id": case["id"], "group": case["group"], "run": run}
                try:
                    raw = await complete(system, story.user(req), story.schema(), name="story",
                                         effort=settings.llm_effort_story, max_output_tokens=6000,
                                         timeout_s=settings.story_deadline_s)
                    book = StoryResult.model_validate(raw)
                    row.update(score(book, req, case["names"]))
                    row["book"] = book.model_dump()
                except (LLMError, ValueError) as error:
                    row.update(kept=False, error=str(error) if isinstance(error, LLMError)
                               else "Invalid story response")
                row["seconds"] = round(time.monotonic() - started, 3)
                output.write(json.dumps(row, ensure_ascii=False) + "\n")
                output.flush()
                print(f"{case['id']} run {run}: kept={row['kept']} missing={row.get('missing', [])} "
                      f"error={row.get('error', row.get('rejection'))}", flush=True)
                return row
        rows = await asyncio.gather(*(one(case, run) for run in range(1, a.runs + 1) for case in cases))
    for group in ("given_name", "control"):
        selected = [row for row in rows if row["group"] == group]
        print(f"{group}: {sum(row['kept'] for row in selected)}/{len(selected)} books kept")
    times = [row["seconds"] for row in rows]
    print(f"errors={sum('error' in row for row in rows)} rejected={sum(bool(row.get('rejection')) for row in rows)} "
          f"p50={statistics.median(times):.2f}s max={max(times):.2f}s")
    return 0 if all(row["kept"] for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
