# -*- coding: utf-8 -*-
"""Jev judge vs luna judge, through the server's own /turn path — quality and time. (10-05)

guidelines/10 §4 left one measurement open before Jev could take the judge: the real total time once
the turn is split (Jev judge → luna line). This runs both providers through judge.run() + turn.run_line()
on the same cases and reports:
- broken promises on the mode cases (fixtures_mode.jsonl + gold, as eval/bench_mode.py scores them) and the
  co-op reason cases (fixtures_coop_reason.jsonl, as eval/bench_coop_reason.py)
- judge time and judge + line time, p50 / p95

    py eval/bench_jev_turn.py --runs 2 [--env C:/path/.env]
"""
from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import Settings, settings  # noqa: E402
from app.routers import judge, turn  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

MODE = [json.loads(l) for l in (EVAL / "fixtures_mode.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
GOLD = {g["id"]: g for g in (json.loads(l) for l in (EVAL / "labels_claude_모드_초안.jsonl").read_text(encoding="utf-8").splitlines() if l.strip())}
COOP = [json.loads(l) for l in (EVAL / "fixtures_coop_reason.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]


def mode_case(c: dict) -> tuple[TurnRequest, dict]:
    req = TurnRequest(mode=c["mode"], slots={k: v for k, v in c["slots"].items() if v}, asked_slot=c.get("asked"),
                      template=c.get("template"), question=c.get("context") or "", utterance=c["utterance"], turn=2)
    return req, (GOLD.get(c["id"]) or {}).get("gold", {})


def mode_broken(v, g: dict) -> bool:
    """Same promise as eval/bench_mode.py breaks(): forbid ('*' = nothing at all), require, forbid_value."""
    fills = [(s, x or "") for s, x in ((v.slot_1, v.value_1), (v.slot_2, v.value_2)) if s]
    names = {s for s, _ in fills}
    if g["forbid"] == "*" and names:
        return True
    if g["forbid"] != "*" and names & set(g["forbid"]):
        return True
    if any(s not in names for s in g["require"]):
        return True
    return any(bad in val for s, bad in g["forbid_value"].items() for slot, val in fills if slot == s)


def coop_case(c: dict) -> TurnRequest:
    return TurnRequest(mode="coop", slots=c["slots"], asked_slot=c["asked"], template=c["template"],
                       question=c["question"], utterance=c["utterance"], reason=c["reason"], turn=2)


def coop_broken(v, c: dict) -> bool:
    filled = {s for s in (v.slot_1, v.slot_2) if s}
    return any(s not in filled for s in c["must"]) or any(s in filled for s in c["never"])


async def one(req: TurnRequest) -> tuple[object, float, float]:
    t = time.monotonic()
    v = await judge.run(req)
    tj = time.monotonic() - t
    await turn.run_line(req, v)
    return v, tj, time.monotonic() - t


def pct(xs: list[float], q: float) -> float:
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(q * len(xs)))]


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--env", default=None, help="a .env to read keys from (values are never printed)")
    a = ap.parse_args()
    if a.env:
        other = Settings(_env_file=a.env)
        for k in ("openai_api_key", "typesafe_api_key", "openai_base_url"):
            setattr(settings, k, getattr(other, k))
    print(f"mode cases {len(MODE)} (gold {sum(1 for c in MODE if c['id'] in GOLD)}) · coop cases {len(COOP)} · runs {a.runs}")
    for provider in ("luna", "jev"):
        settings.judge_jev_modes = "story,diary,coop" if provider == "jev" else ""
        broken_mode = broken_coop = 0
        tj, tt, n_mode, n_coop = [], [], 0, 0
        for _ in range(a.runs):
            for c in MODE:
                req, g = mode_case(c)
                v, j, t = await one(req)
                tj.append(j); tt.append(t)
                if g:
                    n_mode += 1
                    broken_mode += mode_broken(v, g)
            for c in COOP:
                v, j, t = await one(coop_case(c))
                tj.append(j); tt.append(t)
                n_coop += 1
                broken_coop += coop_broken(v, c)
        print(f"{provider:<5} broken mode {broken_mode}/{n_mode} · coop {broken_coop}/{n_coop} · "
              f"judge p50 {statistics.median(tj):.2f}s p95 {pct(tj, .95):.2f}s · "
              f"judge+line p50 {statistics.median(tt):.2f}s p95 {pct(tt, .95):.2f}s")


if __name__ == "__main__":
    asyncio.run(main())
