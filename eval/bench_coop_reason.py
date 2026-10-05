# -*- coding: utf-8 -*-
"""Does the judge read a co-op 「곧 해요」 answer as the plan it is? (#100 1 · 2, 10-05)

10-03 device: asked 「거기서 무슨 일을 할까?」 in a 곧 해요 story, the child said 「불 끄기」 and the judge
filled nothing — "that is a planned activity, not an event". The judge never saw the reason: /turn sends
it to the line model only (backend/app/routers/turn.py). Each case in fixtures_coop_reason.jsonl says which
slot must be filled and which never; a broken promise is a count to drive to zero.

    py eval/bench_coop_reason.py --prompt eval/judge_prompt.md --label before --runs 3
    py eval/bench_coop_reason.py --prompt eval/judge_prompt.md --label after --runs 3 --with-reason

`--with-reason` adds the `reason:` line the way the server sends it; without it the input is today's.
Uses the server's own judge call (system · schema · enforce), the configured model and effort.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from collections import Counter
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import settings                       # noqa: E402
from app.llm.client import complete                   # noqa: E402
from app.llm import judge_prompt                      # noqa: E402
from app.routers.judge import enforce                 # noqa: E402
from app.schemas.judge import JudgeResult             # noqa: E402
from app.schemas.turn import TurnRequest              # noqa: E402
from prompt_block import judge_system                 # noqa: E402

CASES = [json.loads(l) for l in (EVAL / "fixtures_coop_reason.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]


def system(prompt: Path) -> str:
    return f"{judge_system(prompt)}\n\n[공통 JSON 스키마]\n{json.dumps(judge_prompt.schema(), ensure_ascii=False, separators=(',', ':'))}\n"


async def one(sys_text: str, c: dict, with_reason: bool) -> tuple[set[str], str, float]:
    req = TurnRequest(mode="coop", slots=c["slots"], asked_slot=c["asked"], template=c["template"],
                      question=c["question"], utterance=c["utterance"], reason=c["reason"] if with_reason else None)
    user = judge_prompt.user(req)
    if not with_reason:                                  # today's input carries no reason line at all
        user = "\n".join(l for l in user.split("\n") if not l.startswith("reason:"))
    t = time.monotonic()
    raw = await complete(sys_text, user, judge_prompt.schema(), effort=settings.llm_effort_judge,
                         timeout_s=settings.judge_deadline_s)
    v = enforce(JudgeResult.model_validate(raw), req)
    return {s for s in (v.slot_1, v.slot_2) if s}, v.reason, time.monotonic() - t


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", type=Path, required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--with-reason", action="store_true")
    a = ap.parse_args()
    sys_text = system(a.prompt)
    broken = Counter()
    rows = []
    for r in range(a.runs):
        res = await asyncio.gather(*(one(sys_text, c, a.with_reason) for c in CASES))
        for c, (filled, why, dt) in zip(CASES, res):
            miss = [s for s in c["must"] if s not in filled]
            bad = [s for s in c["never"] if s in filled]
            ok = not miss and not bad
            if not ok:
                broken[c["id"]] += 1
            rows.append({"run": r, "id": c["id"], "filled": sorted(filled), "ok": ok, "reason": why, "s": round(dt, 2)})
    out = EVAL / "raw" / f"coop_reason_{a.label}.jsonl"
    out.parent.mkdir(exist_ok=True)
    out.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows), encoding="utf-8")
    total = len(CASES) * a.runs
    print(f"{a.label}: broken promises {sum(broken.values())}/{total}  ({settings.llm_model} · effort {settings.llm_effort_judge})")
    for cid, n in broken.most_common():
        filled = [x["filled"] for x in rows if x["id"] == cid]
        print(f"  {cid:<18} {n}/{a.runs}  filled {filled}")
    print(f"  → {out.relative_to(EVAL.parent)}")


if __name__ == "__main__":
    asyncio.run(main())
