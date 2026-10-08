# -*- coding: utf-8 -*-
"""Does the judge keep the child's word when it is a brand, a food or a real name? (#250 · 10-07)

10-07 on a phone: the child named the clown 「맥도날드」, the judge left `name` empty and asked again, while
Otto said 「광대 이름을 맥도날드라고 했구나」. Two causes, measured:
- luna read the prompt's 「브랜드·실존 인물·실존 작품 이름을 쓰지 않습니다」 (meant for what Otto writes) as
  a rule for accepting the child's answer
- Jev picked `extra` for 「맥도날드」, and `name` under the 0.6 floor for 「맥도날드야」

Cases: fixtures_judge_brand.jsonl — a brand / food / character / real name given for name · newcomer ·
place · companion, three controls, and three word-filter cases (they never reach a model; checked here only
so the filter is seen unchanged). A case is KEPT when the asked slot is filled (slot_1 or slot_2) and, for
luna, its value holds the child's word.

Through the server's own code, three ways:
- luna  : judge.run() with Jev off (what the story judge falls back to)
- jev   : the raw Jev answer to the production questions (jev.questions()) — slot_1 choice + confidence,
          kept when it is the asked slot at or above jev.FLOOR
- served: judge.run() with Jev on for story — what the app actually gets (Jev, luna when Jev leaves no next slot)

    py eval/bench_brand_names.py --runs 3 [--only luna,jev,served] [--env C:/path/.env]
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from collections import defaultdict
from pathlib import Path

import httpx

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import Settings, settings  # noqa: E402
from app.filters.blocklist import is_blocked  # noqa: E402
from app.llm import jev, judge_prompt  # noqa: E402
from app.routers import judge  # noqa: E402
from app.schemas.judge import JudgeRequest  # noqa: E402

CASES = [json.loads(l) for l in (EVAL / "fixtures_judge_brand.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]


def request(c: dict) -> JudgeRequest:
    return JudgeRequest(mode="story", slots=c["slots"], asked_slot=c["asked"], template=c["template"],
                        question=c["context"], utterance=c["utterance"], turn=4)


def kept(v, c: dict) -> bool:
    word = c["gold"]["value_1"]
    for s, x in ((v.slot_1, v.value_1), (v.slot_2, v.value_2)):
        if s == c["asked"] and word in (x or ""):
            return True
    return False


async def jev_raw(c: dict) -> tuple[str | None, float | None]:
    req = request(c)
    body = {"state": f"{judge_prompt.system()}\n\n{judge_prompt.user(req)}", "model": settings.jev_model,
            "questions": jev.questions()}
    async with httpx.AsyncClient(timeout=15) as http:
        r = await http.post(jev.API, json=body, headers={"Authorization": f"Bearer {settings.typesafe_api_key}"})
    r.raise_for_status()
    a = (r.json().get("answers") or {}).get("slot_1") or {}
    return a.get("choice"), a.get("confidence")


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--only", default="luna,jev,served")
    ap.add_argument("--env", default=None, help="a .env to read keys from (values are never printed)")
    ap.add_argument("--out", default=None, help="jsonl of every answer")
    a = ap.parse_args()
    if a.env:
        other = Settings(_env_file=a.env)
        for k in ("openai_api_key", "typesafe_api_key", "openai_base_url"):
            setattr(settings, k, getattr(other, k))
    ways = [w for w in a.only.split(",") if w]
    log = open(a.out, "w", encoding="utf-8") if a.out else None

    safety = [c for c in CASES if c["meta"].get("blocked")]
    print(f"word filter: {sum(is_blocked(c['utterance']) for c in safety)}/{len(safety)} blocked before any model")
    cases = [c for c in CASES if not c["meta"].get("blocked")]

    for way in ways:
        settings.judge_jev_modes = "story" if way == "served" else ""
        per = defaultdict(list)          # id -> ["✓"/"✗ slot conf" ...]
        tot = defaultdict(int)
        for _ in range(a.runs):
            for c in cases:
                group = "control" if c["id"].startswith("c") else "brand"
                try:
                    if way == "jev":
                        ch, k = await jev_raw(c)
                        ok = ch == c["asked"] and (k is None or k >= jev.FLOOR)
                        mark = f"{ch} {k:.2f}" if k is not None else str(ch)
                        rec = {"slot_1": ch, "confidence": k}
                    else:
                        v = await judge.run(request(c))
                        ok = kept(v, c)
                        mark = f"{v.slot_1}:{v.value_1}" + ("" if ok else f" «{(v.reason or '')[:40]}»")
                        rec = v.model_dump()
                except Exception as e:          # noqa: BLE001 — a failed call counts as not kept
                    ok, mark, rec = False, f"ERR {type(e).__name__}", {"error": str(e)[:200]}
                per[c["id"]].append(("✓ " if ok else "✗ ") + mark)
                tot[group, "n"] += 1
                tot[group, "ok"] += ok
                if log:
                    log.write(json.dumps({"way": way, "id": c["id"], "ok": ok, **rec}, ensure_ascii=False) + "\n")
        print(f"\n== {way}: brand {tot['brand', 'ok']}/{tot['brand', 'n']} · control {tot['control', 'ok']}/{tot['control', 'n']}")
        for c in cases:
            print(f"  {c['id']} {c['asked']:<9} {c['utterance']:<14} " + " | ".join(per[c["id"]]))
    if log:
        log.close()


if __name__ == "__main__":
    asyncio.run(main())
