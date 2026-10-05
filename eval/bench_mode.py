# -*- coding: utf-8 -*-
"""Mode cases (`fixtures_mode.jsonl`) through the server's own judge call, one prompt file at a time.

Why not run_judge.py: the server sends `mode:` and `question:` lines (backend/app/llm/judge_prompt.py
`user()`) and run_judge.py does not. A mode section can only be measured with the lines that carry the mode.

What it scores is the promise, not accuracy (make_mode_cases.py): per case the gold draft says which
slot sets are acceptable, which slots must be filled and which must never be. A broken promise is a
count to drive to zero. Field agreement (emotion · s1 · s2 · story_ready) is reported beside it.

    backend/.venv/Scripts/python eval/bench_mode.py --prompt eval/judge_prompt.md --label main --runs 3 --yes-spend
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import settings                       # noqa: E402
from app.llm.client import LLMError, complete         # noqa: E402
from app.llm.judge_prompt import schema, user         # noqa: E402
from app.routers.judge import enforce                 # noqa: E402
from app.schemas.judge import JudgeRequest, JudgeResult, SLOT_NAMES   # noqa: E402
from prompt_block import judge_system                 # noqa: E402

FIXTURES = EVAL / "fixtures_mode.jsonl"
GOLD = EVAL / "labels_claude_모드_초안.jsonl"
RAW = EVAL / "raw"


def system(prompt: Path, mode: str | None = None) -> str:
    """backend/app/llm/judge_prompt.py `system(mode)` with the file as a parameter — the case's own mode piece (#121)."""
    return f"{judge_system(prompt, mode)}\n\n[공통 JSON 스키마]\n{json.dumps(schema(), ensure_ascii=False, separators=(',', ':'))}\n"


def request(case: dict) -> JudgeRequest:
    return JudgeRequest(
        mode=case["mode"], slots=case["slots"], asked_slot=case["asked"] or None,
        template=case.get("template"), question=case.get("context", ""), utterance=case["utterance"],
    )


def fills(v: dict) -> list[tuple[str, str]]:
    return [(v[f"slot_{i}"], v.get(f"value_{i}") or "") for i in (1, 2) if v.get(f"slot_{i}") in SLOT_NAMES]


def breaks(v: dict, g: dict) -> list[str]:
    """Why this verdict breaks the case's promise. Empty = kept."""
    f = fills(v)
    names = {s for s, _ in f}
    why = []
    if g["forbid"] == "*" and names:
        why.append("filled " + "+".join(sorted(names)))
    elif g["forbid"] != "*":
        why += [f"filled {s}" for s in sorted(names & set(g["forbid"]))]
    why += [f"missing {s}" for s in g["require"] if s not in names]
    for s, bad in g["forbid_value"].items():
        why += [f"{s} has 「{bad}」" for slot, val in f if slot == s and bad in val]
    return why


def score(v: dict, g: dict) -> dict:
    names = sorted({s for s, _ in fills(v)})
    return {
        "broken": breaks(v, g),
        "accepted": names in [sorted(a) for a in g["accept"]],
        "emotion": (v.get("emotion") or None) == g["emotion"],
        "s1": bool(v.get("s1_reason")) == g["s1_reason"],
        "s2": bool(v.get("s2_addition")) == g["s2_addition"],
        "ready": bool(v.get("story_ready")) == g["story_ready"],
        "fills": names,
    }


async def one(sys_prompt: str, case: dict) -> tuple[dict | None, float, str]:
    req = request(case)
    t = time.perf_counter()
    try:
        raw = await complete(sys_prompt, user(req), schema(), effort=settings.llm_effort_judge, timeout_s=30)
        v = enforce(JudgeResult.model_validate(raw), req).model_dump()
        return v, time.perf_counter() - t, ""
    except (LLMError, ValueError) as e:
        return None, time.perf_counter() - t, str(e)


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", type=Path, required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--yes-spend", action="store_true", help="real paid API calls")
    a = ap.parse_args()
    if not a.yes_spend:
        sys.exit("paid calls: add --yes-spend")

    cases = [json.loads(l) for l in FIXTURES.read_text(encoding="utf-8").splitlines() if l.strip()]
    gold = {r["id"]: r["gold"] for r in map(json.loads, GOLD.read_text(encoding="utf-8").splitlines()) if r}
    sys_prompt = system(a.prompt)
    RAW.mkdir(exist_ok=True)
    out = RAW / f"mode_{a.label}.jsonl"
    rows = []
    with out.open("w", encoding="utf-8") as f:
        for run in range(a.runs):
            for c in cases:
                v, sec, err = await one(system(a.prompt, c["mode"]), c)
                r = {"label": a.label, "model": settings.llm_model, "run": run, "id": c["id"], "sec": round(sec, 2),
                     "error": err, "verdict": v, "score": score(v, gold[c["id"]]) if v else None}
                rows.append(r)
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
                mark = "ERR" if v is None else ("BREAK " + ", ".join(r["score"]["broken"]) if r["score"]["broken"] else "ok")
                print(f"[{a.label} r{run}] {c['id']:5} {sec:5.2f}s  {mark}", flush=True)

    ok = [r for r in rows if r["score"]]
    n = len(ok)
    print(f"\n{a.label} · {settings.llm_model} · {a.runs} runs × {len(cases)} cases · errors {len(rows) - n}")
    print(f"broken promises {sum(bool(r['score']['broken']) for r in ok)}/{n} · accepted slot set "
          f"{sum(r['score']['accepted'] for r in ok)}/{n}")
    for k in ("emotion", "s1", "s2", "ready"):
        print(f"  {k:8} {sum(r['score'][k] for r in ok)}/{n}")
    print("→", out)


if __name__ == "__main__":
    asyncio.run(main())
