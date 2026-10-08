# -*- coding: utf-8 -*-
"""How good is a co-op book's text? — the automatic checks of the picture-book design (§5-2-2), per book.

Started 10-05 as an empty-page count (a 「동물원 다녀왔어요」 book came back with three pages running
「…은 아직 듣지 못했어요」). 10-06: the rest of the design's checks, so a prompt change is measured on one yardstick.

    py eval/bench_coop_book.py                        # this checkout's story.py + story_prompt_coop.md
    py eval/bench_coop_book.py --prompt <old story_prompt_coop.md> --label before
    OTTO_BACKEND=<other checkout>/backend py eval/bench_coop_book.py --label main   # a true before

Keys come from the repo's .env, read by the backend settings as usual — nothing is printed.
Every check is a count of pages (or books) that break the rule — lower is better; the summary line adds them up.

10-07 (#301 · #304 4): a fixture may also carry
    "names":    the child's own names (brands · characters · real people) — each missing from the captions counts
    "indirect": answers that already report someone's words (「괜찮다고 했어」) — each page quoting one counts
    "direct":   words someone said (「조심해」) — a book that quotes none of them counts
and every book without {주인공} in its captions counts (#292 dropped the protagonist in 16/18 books).
    --runs 3 --concurrency 3 runs each fixture three times · --only c012,c013 runs some
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
# a lesson told, not shown (design R6) — only outside quotes, a child may have said it
MORAL = re.compile(r"(?:는|은|다는) 걸 (?:알았|배웠)어요|된답니다|해야 해요\.?$")
# linkers the design allows once per book (R2)
LINKERS = ["그런데 갑자기", "그때였어요", "그러자", "알고 보니", "드디어", "그래서", "그 뒤로", "바로 그때", "그러고 나서"]
PAST = re.compile(r"(?:았|었|했)(?:어요|대요|죠)\.?$|(?:갔|봤|왔|탔|먹었|줬|놀았)어요")
MAX_EOJEOL, MAX_PAGE_EOJEOL = 12, 20


def sentences_of(caption: str) -> list[str]:
    return [x.strip() for x in re.split(r"(?<=[.!?])\s+", caption) if x.strip()]


def strip_quotes(text: str) -> str:
    return re.sub(r"[\"“「『][^\"”」』]*[\"”」』]", "", text)


def quoted(text: str) -> list[str]:
    return [m.strip() for m in re.findall(r"[\"“「『]([^\"”」』]+)[\"”」』]", text)]


def norm(s: str) -> str:
    return re.sub(r"[\s.,!?~…·\"“”「」『』]", "", s)


def checks(req: StoryRequest, caps: list[str]) -> dict:
    """Pages (or 1 for a book-level rule) breaking each design check."""
    pages = [sentences_of(c) for c in caps]
    eoj = lambda s: len(s.split())  # noqa: E731
    too_long = sum(1 for p in pages if not (1 <= len(p) <= 2) or any(eoj(s) > MAX_EOJEOL for s in p) or sum(eoj(s) for s in p) > MAX_PAGE_EOJEOL)
    linker_twice = sum(1 for l in LINKERS if sum(c.count(l) for c in caps) >= 2)
    moral = sum(1 for c in caps if MORAL.search(strip_quotes(c)))
    # refrain (R3): one standalone short sentence on >= 3 pages (2 when the book is <= 5 pages) — only story · dream
    want_refrain = req.mode == "story" or (req.mode == "coop" and req.reason == "dream")
    need = 2 if len(caps) <= 5 else 3
    short = {}
    for i, p in enumerate(pages):
        for s in p:
            key = norm(s)
            if 2 <= len(key) <= 8 and "{" not in s:
                short.setdefault(key, set()).add(i)
    refrains = {k for k, v in short.items() if len(v) >= 2}
    # refrains first — a refrain may open two pages (10-06: same_start read `refrains` before it existed and crashed)
    first = [norm(p[0].split()[0]) if p and p[0].split() else "" for p in pages]
    same_start = sum(1 for a, b in zip(first, first[1:]) if a and a == b and a not in refrains)
    has_refrain = any(len(v) >= need for v in short.values())
    no_refrain = 1 if want_refrain and not has_refrain else 0
    # a sentence on two pages is a repeat — unless it is the refrain the design asks for
    all_sentences = [s for p in pages for s in p if norm(s) not in refrains]
    repeats = len(all_sentences) - len(set(all_sentences))
    # soon: no past tense (R · tense)
    past = sum(1 for c in caps if PAST.search(c)) if req.reason == "soon" else 0
    # quotes must be the child's own words (R5) — found verbatim in extra / keep / quotes
    sources = [v for v in [req.slots.get("extra"), req.keep] if v] + list(getattr(req, "quotes", None) or [])
    pool = norm(" ".join(sources))
    bad_quote = sum(1 for c in caps for q in quoted(c) if norm(q) and norm(q) not in pool)
    # required slots present somewhere (slot coverage)
    text = norm(" ".join(caps))
    # a slot counts as present when one of its words (particles stripped, ≥ 1 char — 「달」 is a place) appears in the book
    def bare(w: str) -> str:
        return re.sub(r"(으로|에서|에게|한테|이랑|랑|은|는|이|가|을|를|에|로|와|과|도)$", "", norm(w))
    missing = [k for k in ("place", "problem", "solution")
               if req.slots.get(k) and not any(bare(w) and bare(w) in text for w in req.slots[k].split())]
    # mission prop named on its page
    prop_miss = sum(1 for pg, c in zip(req.pages or [], caps) if pg.prop and norm(pg.prop) not in norm(c))
    return {"empty": sum(bool(EMPTY.search(c)) for c in caps), "long": too_long, "same_start": same_start,
            "linker2": linker_twice, "moral": moral, "repeat": repeats, "no_refrain": no_refrain, "past_in_soon": past,
            "bad_quote": bad_quote, "missing": len(missing), "missing_what": missing, "prop_miss": prop_miss}


def given_checks(case: dict, caps: list[str]) -> dict:
    """#301 · #304 4 — the child's names kept · reported speech not quoted · direct speech still quoted · {주인공} present"""
    text = " ".join(caps)
    names_lost = [n for n in case.get("names", []) if n not in text]
    quotes = [norm(q) for c in caps for q in quoted(c)]
    indirect = sum(1 for c in caps for q in quoted(c) for i in case.get("indirect", []) if norm(i)[:-1] in norm(q))
    direct = case.get("direct", [])
    direct_lost = 1 if direct and not any(norm(d) in q for d in direct for q in quotes) else 0
    return {"name_lost": len(names_lost), "name_lost_what": names_lost, "quote_indirect": indirect,
            "direct_lost": direct_lost, "no_hero": 0 if "{주인공}" in text else 1}


async def one(case: dict, system: str) -> dict:
    req = StoryRequest.model_validate(case["req"])
    t = time.perf_counter()
    raw = await complete(system, story_route.user(req), story_route.schema(), name="story",
                         effort=settings.llm_effort_story, max_output_tokens=6000, timeout_s=settings.story_deadline_s)
    result = StoryResult.model_validate(raw)
    caps = [s.caption for s in result.scenes]
    return {"id": case["id"], "reason": req.reason, "s": round(time.perf_counter() - t, 1),
            "rejected": story_route.check(result, req.mode, req.pages), "title": result.title, "captions": caps,
            "c": {**checks(req, caps), **given_checks(case, caps)}}


KEYS = [("empty", "빈 쪽"), ("long", "긴 쪽"), ("same_start", "첫 어절 반복"), ("linker2", "이음말 2회"), ("moral", "교훈 결말"),
        ("repeat", "같은 문장"), ("no_refrain", "후렴 없음"), ("past_in_soon", "곧 해요 과거형"), ("bad_quote", "지어낸 인용"),
        ("missing", "빠진 필수 칸"), ("prop_miss", "미션 물건 빠짐"),
        ("name_lost", "아이 이름 빠짐"), ("quote_indirect", "옮긴 말 따옴표"), ("direct_lost", "직접 말 따옴표 없음"), ("no_hero", "주인공 없음")]


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", help="another story_prompt_coop.md (e.g. the old one) instead of this checkout's")
    ap.add_argument("--story-meanings", action="store_true", help="read day-book pages with the story meanings (before 10-05)")
    ap.add_argument("--label", default="after")
    ap.add_argument("--fixtures", default=str(EVAL / "fixtures_book_coop.jsonl"))
    ap.add_argument("--json", help="also write every book and its checks here (one JSON line per book)")
    ap.add_argument("--runs", type=int, default=1, help="books per fixture")
    ap.add_argument("--concurrency", type=int, default=1)
    ap.add_argument("--only", help="comma-separated fixture ids")
    a = ap.parse_args()
    if a.story_meanings and hasattr(story_route, "DAY_KIND_MEANING"):
        story_route.DAY_KIND_MEANING = {}
    system = system_block(Path(a.prompt)) if a.prompt else story_route.system("coop")
    cases = [json.loads(l) for l in Path(a.fixtures).read_text(encoding="utf-8").splitlines() if l.strip()]
    if a.only:
        cases = [c for c in cases if c["id"] in a.only.split(",")]
    gate = asyncio.Semaphore(a.concurrency)

    async def gated(c: dict, run: int) -> dict:
        async with gate:
            try:
                return {**await one(c, system), "run": run}
            except Exception as e:  # noqa: BLE001 — one failed book is a row, not a crash
                return {"id": c["id"], "run": run, "error": type(e).__name__, "reason": c["req"].get("reason"),
                        "s": 0, "rejected": "error", "title": "", "captions": [], "c": {k: 0 for k, _ in KEYS} | {"missing_what": [], "name_lost_what": []}}
    rows = await asyncio.gather(*(gated(c, run) for run in range(1, a.runs + 1) for c in cases))
    for r in rows:
        flags = " · ".join(f"{k2} {r['c'][k]}" for k, k2 in KEYS if r["c"][k])
        print(f"\n[{a.label}] {r['id']}#{r.get('run', 1)} ({r['reason']}) · {r['s']}s · 버림 {r['rejected']} · 『{r['title']}』 · {flags or '검사 전부 통과'}"
              + (f" · 빠진 칸 {r['c']['missing_what']}" if r["c"]["missing_what"] else "")
              + (f" · 빠진 이름 {r['c']['name_lost_what']}" if r["c"].get("name_lost_what") else ""))
        for i, c in enumerate(r["captions"], 1):
            print(f"  {i}. {c}")
    total = {k: sum(r["c"][k] for r in rows) for k, _ in KEYS}
    npages = sum(len(r["captions"]) for r in rows)
    print(f"\n[{a.label}] {len(rows)}권 {npages}쪽 · {settings.llm_model} · effort {settings.llm_effort_story} · 버린 책 {sum(1 for r in rows if r['rejected'])} · 오류 {sum(1 for r in rows if 'error' in r)}")
    print("  " + " · ".join(f"{k2} {total[k]}" for k, k2 in KEYS))
    print(f"  어긴 것 합계 {sum(total.values())}")
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps({"label": a.label, **r}, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    asyncio.run(main())
