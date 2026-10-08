# -*- coding: utf-8 -*-
"""Diary book: the app's page plan + extra sayings + "no same meaning twice" (#220 part 3).

before — the prompt as it was (the #192 rules) and the request as the app sent it: no page list.
after  — the new prompt and the app's page plan (`pages`), same slots (including extra).

    py eval/bench_diary_pages.py --prompt <story_prompt_diary.md> --label before|after [--pages] [--n 3]

Counts per book: the #192 rule checks (`bench_diary_book_context.counts`), a feeling said on two pages,
words the child said that are missing, invented words, and — with a page plan — whether every RUB page
carries its own extra saying. Keys come from an .env read by the backend settings — nothing is printed.
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
from bench_diary_book_context import counts as rule_counts   # noqa: E402

PLAN_MAX = 8
SAID_ALREADY = 0.7


def stems(t: str) -> set[str]:
    return {w[:2] for w in re.split(r"[\s:,.!?~]+", t) if w}


def said_already(item: str, others: list[str]) -> bool:
    """The app's `saidAlready` (DiaryBook.kt): most of the saying is already in one other slot or an earlier saying."""
    mine = stems(item)
    return not mine or any(sum(s in stems(o) for s in mine) >= SAID_ALREADY * len(mine) for o in others)


def plan(slots: dict, keep: str | None) -> list[dict]:
    """The app's `diaryPagePlan` (DiaryBook.kt), in Python."""
    has = lambda k: bool((slots.get(k) or "").strip())
    head = ([{"kind": "DEPART"}] if has("place") or has("companion") else []) + ([{"kind": "SHAKE"}] if has("problem") else [])
    tail = ([{"kind": "FAIL"}] if has("reaction") or has("cause") else []) + ([{"kind": "DRAG"}] if has("solution") else []) \
        + ([{"kind": "TOGETHER"}] if (keep or "").strip() else [])
    said = [v for k in ("place", "companion", "problem", "reaction", "cause", "solution") if (v := (slots.get(k) or "").strip())]         + ([keep] if (keep or "").strip() else [])
    items: list[str] = []
    for x in (x.strip() for x in (slots.get("extra") or "").split(" / ")):
        if x and not said_already(x, said + items):
            items.append(x)
    items = items[: max(0, PLAN_MAX - len(head) - len(tail))]
    return head + [{"kind": "RUB", "item": it} for it in items] + tail


def measure(case: dict, caps: list[str], pages: list[dict] | None) -> dict:
    want = case["want"]
    text = " ".join(caps)
    dup = any(sum(any(w in c for w in group) for c in caps) >= 2 for group in want.get("dup", []))
    items = None
    if pages:
        rub = [i for i, p in enumerate(pages) if p["kind"] == "RUB"]
        wanted = [w for w, it in zip(want.get("items", []), [x.strip() for x in (case["req"]["slots"].get("extra") or "").split(" / ") if x.strip()])
                  if it in [p.get("item") for p in pages]]
        items = [all(w in caps[i] for w in words) for i, words in zip(rub, wanted) if i < len(caps)]
    return {
        "pages": len(caps),
        "dup": int(dup),
        "missing": [w for w in want.get("child", []) if w not in text],
        "invented_words": [w for w in want.get("no_new", []) if w in text],
        "items_ok": items,
        "opens_na": int(bool(caps) and caps[0].lstrip().startswith("나는")),   # prompt: the first page opens 「나는 오늘」
    }


def load(path: Path) -> list[dict]:
    return [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]


async def one(case: dict, system: str, with_pages: bool) -> dict:
    body = dict(case["req"])
    pages = plan(body["slots"], body.get("keep")) if with_pages else None
    if pages:
        body["pages"] = [{"kind": p["kind"]} for p in pages]
        # the app sends only the sayings that got a page (PictureDiary.writeDiaryBook)
        body["slots"] = {**body["slots"], "extra": " / ".join(p["item"] for p in pages if p["kind"] == "RUB") or None}
    req = StoryRequest.model_validate(body)
    t = time.perf_counter()
    raw = await complete(system, story_route.user(req), story_route.schema(), name="story",
                         effort=config.settings.llm_effort_story, max_output_tokens=6000,
                         timeout_s=config.settings.story_deadline_s)
    result = StoryResult.model_validate(raw)
    caps = [s.caption for s in result.scenes]
    return {"id": case["id"], "s": round(time.perf_counter() - t, 1), "rejected": story_route.check(result, req.mode, req.pages),
            "captions": caps, **rule_counts({"want": {}, "req": case["req"]}, caps), **measure(case, caps, pages)}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--pages", action="store_true", help="send the app's page plan")
    ap.add_argument("--n", type=int, default=3)
    ap.add_argument("--env", default="D:/bootcamp-final-project/.env")
    ap.add_argument("--out", default=str(EVAL / "results_diary_pages"))
    ap.add_argument("--rescore", action="store_true", help="count again from the saved captions, no model call")
    ap.add_argument("--fixtures", default=str(EVAL / "fixtures_book_diary_pages.jsonl"),
                    help="fixtures_diary_given_names.jsonl for #301 (names the child gave)")
    a = ap.parse_args()
    cases = load(Path(a.fixtures))
    out = Path(a.out); out.mkdir(exist_ok=True)
    if a.rescore:
        by_id = {c["id"]: c for c in cases}
        keep = json.loads((out / f"{a.label}.json").read_text(encoding="utf-8"))
        for r in keep:
            if "error" in r:
                continue
            c = by_id[r["id"]]
            pages = plan(c["req"]["slots"], c["req"].get("keep")) if a.pages else None
            r.update(measure(c, r["captions"], pages))
        (out / f"{a.label}.json").write_text(json.dumps(keep, ensure_ascii=False, indent=1), encoding="utf-8")
        summary(a, cases, keep)
        return
    loaded = type(config.settings)(_env_file=a.env)
    for f in type(config.settings).model_fields:
        setattr(config.settings, f, getattr(loaded, f))
    system = system_block(Path(a.prompt))
    rows = await asyncio.gather(*[one(c, system, a.pages) for c in cases for _ in range(a.n)], return_exceptions=True)
    keep = [r if isinstance(r, dict) else {"error": f"{type(r).__name__}: {r}"} for r in rows]
    (out / f"{a.label}.json").write_text(json.dumps(keep, ensure_ascii=False, indent=1), encoding="utf-8")
    summary(a, cases, keep)


def summary(a, cases: list[dict], keep: list[dict]) -> None:
    ok = [r for r in keep if "error" not in r]
    dup_cases = [r for r in ok if any(c["id"] == r["id"] and c["want"].get("dup") for c in cases)]
    item_checks = [x for r in ok for x in (r["items_ok"] or [])]
    child_total = sum(len(c["want"].get("child", [])) for c in cases) * a.n
    print(a.label, "runs", len(ok), "errors", len(keep) - len(ok),
          "| dup", f"{sum(r['dup'] for r in dup_cases)}/{len(dup_cases)}",
          "| missing child words", f"{sum(len(r['missing']) for r in ok)}/{child_total}",
          "| invented", sum(len(r["invented_words"]) for r in ok),
          "| items on their page", f"{sum(item_checks)}/{len(item_checks)}" if item_checks else "-",
          "| pages", round(sum(r["pages"] for r in ok) / max(1, len(ok)), 1),
          "| long", sum(r["long"] for r in ok), "not_yo", sum(r["not_yo"] for r in ok),
          "wish_past", sum(r["wish_past"] for r in ok), "drama", sum(r["drama"] for r in ok),
          "rejected", sum(bool(r["rejected"]) for r in ok),
          "| opens 나는", f"{sum(r.get('opens_na', 0) for r in ok)}/{len(ok)}")


if __name__ == "__main__":
    asyncio.run(main())
