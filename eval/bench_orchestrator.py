"""R (rule orchestrator) vs M (LLM orchestrator) on the 100 judge questions.

Criteria were fixed before any run: docs/멀티에이전트_검토.md §3.

    R: rule -> judge                         (one LLM call per turn)
    M: orchestrator LLM picks agents -> judge (two LLM calls per turn)

The judge is the same in both (same prompt as the server: prompt_block.judge_system,
same model, reasoning off), so the only difference is the orchestrator layer.
The story agent is not executed — only whether M *asks* for it is scored against
gold.story_ready (R calls it from the judge's story_ready by rule).

    py -m eval.bench_orchestrator --runs 3 --yes-spend
"""
from __future__ import annotations

import argparse
import json
import os
import statistics
import time
from pathlib import Path

import httpx

from eval.config import load_dotenv
from eval.prompt_block import judge_system
from eval.run_judge import build_user_prompt

ROOT = Path(__file__).resolve().parent
MODEL = "gpt-6-luna"
USD_IN, USD_OUT = 0.10, 0.50          # per Mtok, model_catalog.json (09-25)
KRW = 1400
TURNS_PER_BOOK = 16                   # guidelines/3 §3-2

AGENTS = ["judge", "story", "jev", "safety"]

ORCH_SYSTEM = """당신은 3~7세 아이와 대화로 그림책을 만드는 앱의 오케스트레이터입니다.
아이가 방금 한 말을 보고, 이번 턴에 부를 에이전트를 고릅니다. 직접 판정하지 않습니다.

에이전트
- judge: 아이 말에서 어느 칸이 찼는지, 다음에 무엇을 물을지 판정한다. 아이 말을 이야기에 반영하려면 반드시 필요하다
- story: 칸이 충분히 찼을 때 책 문장을 만든다. 이야기가 끝날 만큼 찼을 때만 부른다
- jev: 고르기 · 예/아니오 · 정도 같은 빠른 판단 (그림 낱말 분류, 물건 배치 깊이)
- safety: 아이 말에 걱정스러운 내용(다침 · 위험 · 학대 신호)이 있을 때 확인한다

reason 에 한 줄 근거를 먼저 쓰고 calls 에 부를 에이전트를 순서대로 넣는다. JSON 외에는 출력하지 마라."""

ORCH_SCHEMA = {
    "type": "object", "additionalProperties": False, "required": ["reason", "calls"],
    "properties": {
        "reason": {"type": "string"},
        "calls": {"type": "array", "items": {"type": "string", "enum": AGENTS}},
    },
}


def call(http: httpx.Client, system: str, user: str, schema: dict, name: str) -> tuple[float, dict | None, int, int]:
    body = {
        "model": MODEL, "instructions": system, "input": user, "store": False,
        "max_output_tokens": 768, "reasoning": {"effort": "none"},
        "text": {"format": {"type": "json_schema", "name": name, "schema": schema, "strict": True}},
    }
    t0 = time.perf_counter()
    r = http.post("https://api.openai.com/v1/responses", json=body,
                  headers={"Authorization": f"Bearer {os.environ['OPENAI_API_KEY']}"})
    took = time.perf_counter() - t0
    if r.status_code != 200:
        return took, None, 0, 0
    j = r.json()
    u = j.get("usage") or {}
    text = next((p.get("text") for it in j.get("output") or [] if it.get("type") == "message"
                 for p in it.get("content") or [] if p.get("type") == "output_text"), None)
    try:
        out = json.loads(text) if text else None
    except json.JSONDecodeError:
        out = None
    return took, out, int(u.get("input_tokens") or 0), int(u.get("output_tokens") or 0)


def p95(xs: list[float]) -> float:
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(0.95 * (len(xs) - 1))))]


def krw(tin: int, tout: int, n: int) -> float:
    """Judge-path cost of one book (16 turns) from the average tokens per turn."""
    per_turn = (tin * USD_IN + tout * USD_OUT) / 1e6 / n
    return per_turn * TURNS_PER_BOOK * KRW


def run_once(rows: list[dict], judge_sys: str, judge_schema: dict, http: httpx.Client) -> dict:
    R = {"t": [], "tin": 0, "tout": 0, "fail": 0}
    M = {"t": [], "tin": 0, "tout": 0, "fail": 0, "judge_called": 0, "story_tp": 0, "story_fp": 0, "story_fn": 0}
    for i, row in enumerate(rows):
        user = build_user_prompt(row)
        gold_ready = bool((row.get("gold") or {}).get("story_ready"))
        order = ("R", "M") if i % 2 == 0 else ("M", "R")      # alternate to cancel drift
        for arm in order:
            if arm == "R":
                t, out, a, b = call(http, judge_sys, user, judge_schema, "judge")
                R["t"].append(t); R["tin"] += a; R["tout"] += b; R["fail"] += out is None
            else:
                t1, plan, a1, b1 = call(http, ORCH_SYSTEM, user, ORCH_SCHEMA, "orchestrate")
                calls = (plan or {}).get("calls") or []
                t2, a2, b2 = 0.0, 0, 0
                if "judge" in calls:
                    M["judge_called"] += 1
                    t2, out, a2, b2 = call(http, judge_sys, user, judge_schema, "judge")
                    M["fail"] += out is None
                M["t"].append(t1 + t2); M["tin"] += a1 + a2; M["tout"] += b1 + b2
                asked_story = "story" in calls
                M["story_tp"] += asked_story and gold_ready
                M["story_fp"] += asked_story and not gold_ready
                M["story_fn"] += (not asked_story) and gold_ready
        print(f"  {row['id']} R={R['t'][-1]:.2f}s M={M['t'][-1]:.2f}s", flush=True)
    n = len(rows)
    tp, fp, fn = M["story_tp"], M["story_fp"], M["story_fn"]
    f1 = (2 * tp / (2 * tp + fp + fn)) if (tp + fp + fn) else 1.0
    return {
        "R_p50": statistics.median(R["t"]), "R_p95": p95(R["t"]), "R_krw_book": krw(R["tin"], R["tout"], n),
        "M_p50": statistics.median(M["t"]), "M_p95": p95(M["t"]), "M_krw_book": krw(M["tin"], M["tout"], n),
        "M_judge_rate": M["judge_called"] / n, "M_story_f1": f1, "M_story_tp_fp_fn": (tp, fp, fn),
        "R_fail": R["fail"], "M_fail": M["fail"],
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--yes-spend", action="store_true")
    args = ap.parse_args()
    if not args.yes_spend:
        raise SystemExit("paid API calls — pass --yes-spend")
    load_dotenv()
    rows = [json.loads(l) for l in (ROOT / "fixtures_judge.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.limit:
        rows = rows[: args.limit]
    schema = json.loads((ROOT / "judge_schema.json").read_text(encoding="utf-8"))
    schema = {k: v for k, v in schema.items() if k not in ("name", "description")}
    judge_sys = f"{judge_system(ROOT / 'judge_prompt.md')}\n\n[공통 JSON 스키마]\n{json.dumps(schema, ensure_ascii=False, separators=(',', ':'))}\n"
    results = []
    with httpx.Client(timeout=60) as http:
        for k in range(args.runs):
            print(f"run {k + 1}/{args.runs}", flush=True)
            results.append(run_once(rows, judge_sys, schema, http))
            print(json.dumps(results[-1], ensure_ascii=False), flush=True)
    out = ROOT / "raw" / "orchestrator_runs.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved {out}")


if __name__ == "__main__":
    main()
