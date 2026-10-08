"""Measure mission pages before/after #259 (10-08 lead decision).

Story books and imagined co-op books: the mission page is a scene in the child's story — the hero or
friend is there and the page ends on a tension that calls the child's hand, never on the result.
Real-day books (diary · co-op done/soon) with a mission the child gave no prop for: the page is marked as
Otto's imagination, and no page states the mission's event as something that happened.

Uses the production request (story.user), system prompt (story.system), schema and rejection check —
run once on the old code and once on the new one with the same fixtures. Never prints credentials.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import re
import statistics
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import Settings, settings  # noqa: E402
from app.llm.client import LLMError, complete  # noqa: E402
from app.routers import story  # noqa: E402
from app.schemas.story import Page, StoryRequest, StoryResult  # noqa: E402

INVITE = re.compile(r"네가|너도|너는|도와줄래|줄래|해 줄래|도와줘|같이 해")
# the mission done on the page itself — the app adds the result after the child's hand (#100)
RESULT = re.compile(r"껐어요|꺼졌어요|치웠어요|닦았어요|지웠어요|깨끗해졌어요|건넸어요|건네줬어요|건네주었어요|고쳤어요|고쳐졌어요"
                    r"|쌓았어요|잠갔어요|불었어요|날아갔어요|깨어났어요|움직였어요")
NAMES = re.compile(r"미션|물대포|문지르기|퍼즐|조각|도구|직전")
MARK = "상상"


def request(case: dict) -> StoryRequest:
    return StoryRequest(mode=case["mode"], reason=case.get("reason"), template=case.get("template"),
                        slots=case["slots"], slot_by=case.get("slot_by", {}), keep=case.get("keep"),
                        pages=[Page(**p) for p in case["pages"]])


def mission_pages(case: dict) -> list[int]:
    """Indexes of the pages this decision is about. Real day: only missions without the child's prop."""
    out = []
    for i, p in enumerate(case["pages"]):
        if not p.get("mission") or p["mission"] in story.NO_SITUATION:
            continue
        if case["group"] == "real" and p.get("prop"):
            continue
        out.append(i)
    return out


def score(book: StoryResult, case: dict, req: StoryRequest) -> dict:
    caps = [sc.caption.strip() for sc in book.scenes]
    rejection = story.check(book, req.mode, req.pages)
    idx = mission_pages(case)
    pages = [caps[i] for i in idx if i < len(caps)]
    row = {"rejection": rejection, "mission_captions": pages, "n_pages": len(pages)}
    row["ends_q"] = sum(c.endswith("?") for c in pages)
    row["invite"] = sum(bool(INVITE.search(c)) for c in pages)
    row["result_tense"] = sum(bool(RESULT.search(c.split(MARK)[-1] if MARK in c else c)) for c in pages)
    row["names"] = sum(bool(NAMES.search(c)) for c in pages)
    if case["group"] == "real":
        row["marker"] = sum(MARK in c for c in pages)
        bad = re.compile(case["invented"])
        facts = []
        for i, c in enumerate(caps):
            told = c.split(MARK)[0] if i in idx else c   # what comes before the imagination mark is told as the day
            if bad.search(told):
                facts.append(i + 1)
        row["invented_fact_pages"] = facts
    else:
        row["cast"] = sum(any(n in c for n in case["cast"]) for c in pages)
    return row


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fixtures", type=Path, default=EVAL / "fixtures_story_mission_page.jsonl")
    ap.add_argument("--env", type=Path, help="Existing local settings file; values are never printed")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--concurrency", type=int, default=3)
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
    meta = {"model": settings.llm_model, "effort": settings.llm_effort_story,
            "prompt_sha256": {m: hashlib.sha256(story.system(m).encode()).hexdigest()[:12] for m in ("story", "diary", "coop")},
            "fixtures_sha256": hashlib.sha256(a.fixtures.read_bytes()).hexdigest()[:12]}
    print(json.dumps(meta, ensure_ascii=False))
    gate = asyncio.Semaphore(a.concurrency)
    stop = asyncio.Event()
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open("w", encoding="utf-8") as output:
        async def one(case: dict, run: int) -> dict:
            async with gate:
                row = {**meta, "id": case["id"], "group": case["group"], "run": run}
                if stop.is_set():
                    row["error"] = "skipped after a rate limit"
                    return row
                req = request(case)
                started = time.monotonic()
                try:
                    raw = await complete(story.system(req.mode), story.user(req), story.schema(), name="story",
                                         effort=settings.llm_effort_story, max_output_tokens=6000,
                                         timeout_s=settings.story_deadline_s)
                    book = StoryResult.model_validate(raw)
                    row.update(score(book, case, req))
                    row["book"] = [sc.caption for sc in book.scenes]
                except (LLMError, ValueError) as error:
                    msg = str(error) if isinstance(error, LLMError) else "Invalid story response"
                    row["error"] = msg
                    if "429" in msg or "quota" in msg.lower():
                        stop.set()
                row["seconds"] = round(time.monotonic() - started, 3)
                output.write(json.dumps(row, ensure_ascii=False) + "\n")
                output.flush()
                print(f"{case['id']} run {run}: {row.get('error') or row.get('rejection') or 'ok'}", flush=True)
                return row
        rows = await asyncio.gather(*(one(c, r) for r in range(1, a.runs + 1) for c in cases))
    ok = [r for r in rows if "error" not in r]

    def tot(group: str, key: str) -> str:
        sel = [r for r in ok if r["group"] in group.split(",")]
        return f"{sum(r[key] for r in sel)}/{sum(r['n_pages'] for r in sel)}"

    print(f"story+dream mission pages: cast={tot('story,dream', 'cast')} ends_q={tot('story,dream', 'ends_q')} "
          f"invite={tot('story,dream', 'invite')} result_tense={tot('story,dream', 'result_tense')} "
          f"names={tot('story,dream', 'names')}")
    real = [r for r in ok if r["group"] == "real"]
    print(f"real-day imagined pages: marker={tot('real', 'marker')} ends_q={tot('real', 'ends_q')} "
          f"invite={tot('real', 'invite')} result_tense={tot('real', 'result_tense')} names={tot('real', 'names')} "
          f"books_with_invented_fact={sum(bool(r['invented_fact_pages']) for r in real)}/{len(real)}")
    times = [r["seconds"] for r in rows if "seconds" in r and "error" not in r]
    print(f"errors={sum('error' in r for r in rows)} rejected={sum(bool(r.get('rejection')) for r in ok)} "
          f"p50={statistics.median(times) if times else 0:.2f}s max={max(times) if times else 0:.2f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
