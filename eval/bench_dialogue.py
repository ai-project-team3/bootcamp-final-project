# -*- coding: utf-8 -*-
"""Dialogue repair (#323): B (no repair) vs R (decider + rules) vs M (the line model picks), one turn each.

Criteria were fixed on #323 before any run. Same cases, same order, k runs per variant, through the
server's own /turn function (judge on Jev for diary, as served since 10-05).

    py eval/bench_dialogue.py --runs 3 --yes-spend [--env C:/path/.env] [--variants B,R,M]
    py eval/bench_dialogue.py --score eval/raw/dialogue_<stamp>.jsonl      # score a saved run again

Writes every call to eval/raw/ (git-ignored) and prints the table for eval/results.md. The blind
pairs for the satisfaction check (B vs R, run 1, sides shuffled) go to eval/raw/ too.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import random
import statistics
import sys
import time
from collections import defaultdict
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import Settings, settings  # noqa: E402
from app.llm import client, jev  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

CASES = [json.loads(l) for l in (EVAL / "fixtures_dialogue.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
RAW = EVAL / "raw"
PRICES: dict[str, float] = {}                # set only from the command line (--cached-usd · --jev-won)

USD_IN, USD_OUT, KRW = 0.10, 0.50, 1400      # luna list price per Mtok (model_catalog.json 09-25)
# ⚠️ Prices are not the bill. 10-08's runs priced every input token at list price and Jev at 0.292원 a call
# (09-22's 「100문항 29.2원」 divided by calls — never checked): about 2× the OpenAI bill (10-09: 1,228 calls,
# $0.13~0.14). Now the report gives token counts (cached apart) and Jev calls; won only with prices passed in.
DROPS_WORDS = {"repair", "answer_back", "rephrase"}   # acts that may drop the judge's fills
NEEDS_QUESTION = {"answer_back", "rephrase", "aside"}
ALLOWED = {None, "repair", "answer_back", "rephrase", "aside"}


# --- running ---

class Meter:
    """Tokens, Jev calls and the seconds of each step inside one /turn call."""

    def __init__(self):
        self.tin = self.tout = self.tcached = self.jev = 0
        self.decision = None
        self.steps: dict[str, float] = {}       # judge · decide · line · line_choose · jev_judge · jev_ask

    def add(self, step: str, secs: float):
        self.steps[step] = round(self.steps.get(step, 0.0) + secs, 3)


async def one(req: TurnRequest, m: Meter) -> dict:
    t = time.monotonic()
    try:
        out = await turn.turn(req)
        err = None
    except Exception as e:                   # a 502 is a result here, not a crash
        out, err = None, f"{type(e).__name__}: {e}"
    secs = time.monotonic() - t
    v = out.judge if out else None
    line = out.line if out else None
    d = m.decision
    return {
        "secs": round(secs, 3), "error": err,
        "act": line.act if line else None, "line": line.model_dump() if line else None,
        "retract": out.retract if out else [],
        "fills": [[s, x] for s, x in ((v.slot_1, v.value_1), (v.slot_2, v.value_2)) if s] if v else [],
        "next_slot": v.next_slot if v else None,
        "decision": d.__dict__ if d else None,
        "tin": m.tin, "tcached": m.tcached, "tout": m.tout, "jev": m.jev,
        "steps": m.steps,
    }


def instrument() -> list[Meter]:
    """Count tokens and Jev calls, and keep the decider's answer, per call (calls run one at a time)."""
    cur: list[Meter] = [Meter()]

    def usage(name, tin, tout):
        cur[0].tin += tin
        cur[0].tout += tout
    client.on_usage = usage

    def cached(name, n):
        cur[0].tcached += n
    client.on_cached = cached

    def timed(owner, fn: str, step: str, keep_decision: bool = False):
        orig = getattr(owner, fn)

        async def wrapped(*a, **k):
            t = time.monotonic()
            try:
                out = await orig(*a, **k)
            finally:
                cur[0].add(step, time.monotonic() - t)
            if keep_decision:
                cur[0].decision = out
            return out
        setattr(owner, fn, wrapped)

    for fn in ("ask", "judge"):
        orig = getattr(jev, fn)

        async def counted(*a, _orig=orig, **k):
            cur[0].jev += 1
            return await _orig(*a, **k)
        setattr(jev, fn, counted)
    # the vendor round trips themselves — a failed Jev call is timed too
    timed(jev, "judge", "jev_judge")
    timed(jev, "ask", "jev_ask")
    # the steps /turn runs: judge and decider side by side, then the line
    timed(turn.judge, "run", "judge")
    timed(turn, "decide", "decide", keep_decision=True)
    timed(turn, "run_line", "line")
    timed(turn, "run_line_choosing", "line_choose")
    return cur


async def run(variants: list[str], runs: int, mock: bool = False, sink: Path | None = None) -> list[dict]:
    cur = instrument()
    settings.mock = mock                                # mock: words-only decider, canned lines — free
    settings.judge_jev_modes = "story,diary,coop"        # as served since 10-05
    rows = []
    # variants take turns on every case — a slow vendor minute lands on all of them, not on one (10-08:
    # run one after another, the >8 s calls bunched into R's last runs). Who goes first flips per case.
    for r in range(runs):
        for i, c in enumerate(CASES):
            for variant in (variants if (i + r) % 2 == 0 else variants[::-1]):
                settings.dialogue_policy = "llm" if variant == "M" else "rule"
                body = dict(c["req"])
                if variant == "B":
                    body["history"] = []                   # main's /turn: no history, no repair
                cur[0] = Meter()
                res = await one(TurnRequest.model_validate(body), cur[0])
                rows.append({"variant": variant, "run": r, "id": c["id"], **res})
                if sink:                                   # each call as it lands — a stopped run keeps what it measured
                    with sink.open("a", encoding="utf-8") as f:
                        f.write(json.dumps(rows[-1], ensure_ascii=False) + "\n")
                st = res["steps"]
                print(f"{variant} r{r} {c['id']} {res['act'] or '-':<11} {res['secs']:.2f}s · judge {st.get('judge', 0):.2f}"
                      f" · decide {st.get('decide', 0):.2f} · line {st.get('line', st.get('line_choose', 0)):.2f}", flush=True)
    return rows


# --- scoring ---

def pct(xs, q):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(q * len(xs)))] if xs else 0.0


def wilson(k, n, z=1.96):
    if n == 0:
        return (0.0, 0.0)
    p = k / n
    den = 1 + z * z / n
    mid = (p + z * z / (2 * n)) / den
    half = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / den
    return (max(0.0, mid - half), min(1.0, mid + half))


def judge_row(row: dict, case: dict) -> dict:
    g, req = case["gold"], case["req"]
    act = row["act"]
    d = row["decision"] or {}
    filled = {s for s, v in req["slots"].items() if v}
    wrong = req["slots"].get(g["target"]) if g["target"] else None
    out = {
        "act_ok": act == g["act"],
        "intent_ok": (d.get("intent") == g["intent"]) if d else None,
        # a plain answer taken for a correction or a refusal (#323 criterion 1), and any act that drops its words
        "false_pos": g["intent"] == "answer" and (
            act == "repair" or (d.get("intent") in ("correct", "refuse") and (d.get("intent_conf") or 0) >= 0.8)),
        "words_dropped": g["intent"] == "answer" and act in DROPS_WORDS,
        "ignored": g["intent"] in ("correct", "ask_back", "not_heard") and act is None,
        "reasked": bool(row["next_slot"] and row["next_slot"] in filled and row["next_slot"] not in row["retract"]),
        "broken": row["line"] is None or act not in ALLOWED or (
            act in NEEDS_QUESTION and not (row["line"] or {}).get("question")) or not (row["line"] or {}).get("ack"),
    }
    if g["intent"] == "correct":
        out["retracted"] = g["target"] in row["retract"]
        refilled_wrong = any(s == g["target"] and wrong and wrong in (x or "") for s, x in row["fills"])
        out["repaired"] = out["retracted"] and not refilled_wrong and (
            any(s == g["target"] for s, _ in row["fills"]) if g["new_value"] else
            not any(s == g["target"] for s, _ in row["fills"]))
    return out


def score(rows: list[dict]) -> dict:
    cases = {c["id"]: c for c in CASES}
    by_v = defaultdict(list)
    for row in rows:
        by_v[row["variant"]].append((row, judge_row(row, cases[row["id"]])))
    report = {}
    for variant, items in by_v.items():
        runs = sorted({r["run"] for r, _ in items})
        per_run = lambda key, pick=lambda c: True: [sum(1 for r, s in items if r["run"] == k and pick(cases[r["id"]]) and s.get(key))
                                                     for k in runs]
        n_answer = sum(1 for c in CASES if c["gold"]["intent"] == "answer")
        n_correct = sum(1 for c in CASES if c["gold"]["intent"] == "correct")
        n_ign = sum(1 for c in CASES if c["gold"]["intent"] in ("correct", "ask_back", "not_heard"))
        act_ok = sum(1 for _, s in items if s["act_ok"])
        intents = [s["intent_ok"] for _, s in items if s["intent_ok"] is not None]
        all_ok = defaultdict(lambda: True)
        for r, s in items:
            all_ok[r["id"]] = all_ok[r["id"]] and s["act_ok"]
        secs = [r["secs"] for r, _ in items]
        rs = [r for r, _ in items]
        mean = lambda k: round(statistics.mean(r.get(k, 0) for r in rs), 1)
        report[variant] = {
            "runs": len(runs),
            "false_pos_per_run": per_run("false_pos"), "of_answers": n_answer,
            "words_dropped_per_run": per_run("words_dropped"),
            "retracted_per_run": per_run("retracted"), "repaired_per_run": per_run("repaired"), "of_corrections": n_correct,
            "ignored_per_run": per_run("ignored"), "of_non_answers": n_ign,
            "reasked_per_run": per_run("reasked"),
            "act_acc": round(act_ok / len(items), 3), "act_acc_ci": [round(x, 3) for x in wilson(act_ok, len(items))],
            "intent_acc": round(sum(intents) / len(intents), 3) if intents else None,
            "pass_k": round(sum(all_ok.values()) / len(all_ok), 3),
            "pass_k_ci": [round(x, 3) for x in wilson(sum(all_ok.values()), len(all_ok))],
            "broken": sum(1 for _, s in items if s["broken"]),
            "p50": round(statistics.median(secs), 2), "p95": round(pct(secs, .95), 2),
            # per turn: luna tokens (cached apart) and Jev calls — the bill's own units
            "luna_in": mean("tin"), "luna_cached": mean("tcached"), "luna_out": mean("tout"), "jev_calls": mean("jev"),
            **({"won_per_turn": round(statistics.mean(
                ((r["tin"] - r.get("tcached", 0)) * USD_IN + r.get("tcached", 0) * PRICES["cached_usd"]
                 + r["tout"] * USD_OUT) / 1e6 * KRW + r["jev"] * PRICES["jev_won"] for r in rs), 3)}
               if PRICES else {}),
            "steps": steps([r for r, _ in items]),
            "errors": sum(1 for r, _ in items if r["error"]),
        }
    if "R" in by_v and "M" in by_v:
        report["cascade"] = cascade(by_v["R"], by_v["M"], cases)
    return report


def steps(rows: list[dict]) -> dict:
    """p50 / p95 of each step, and how long the turn waited on the decider past the judge (they run side by side)."""
    out = {}
    names = sorted({k for r in rows for k in (r.get("steps") or {})})
    for k in names:
        xs = [r["steps"][k] for r in rows if k in (r.get("steps") or {})]
        out[k] = [round(statistics.median(xs), 2), round(pct(xs, .95), 2), len(xs)]
    waits = [max(0.0, r["steps"]["decide"] - r["steps"].get("judge", 0.0))
             for r in rows if "decide" in (r.get("steps") or {})]
    if waits:
        out["wait_on_decider"] = [round(statistics.median(waits), 2), round(pct(waits, .95), 2), len(waits)]
    return out


def cascade(r_items, m_items, cases) -> list[dict]:
    """ⓒ without extra calls: keep R's act when the decider is at least t sure, else take M's (same run)."""
    m_act = {(r["id"], r["run"]): r["act"] for r, _ in m_items}
    out = []
    for t in (0.5, 0.6, 0.7, 0.8, 0.9, 0.95):
        ok = sent = 0
        for r, _ in r_items:
            conf = (r["decision"] or {}).get("intent_conf")
            use_m = conf is None or conf < t
            act = m_act.get((r["id"], r["run"])) if use_m else r["act"]
            sent += use_m
            ok += act == cases[r["id"]]["gold"]["act"]
        out.append({"t": t, "sent_to_llm": round(sent / len(r_items), 3), "act_acc": round(ok / len(r_items), 3)})
    return out


def blind_pairs(rows: list[dict], stamp: str) -> None:
    """B vs R, run 0, sides shuffled — for two people to pick from without knowing which is which."""
    rnd = random.Random(323)
    b = {r["id"]: r for r in rows if r["variant"] == "B" and r["run"] == 0}
    rr = {r["id"]: r for r in rows if r["variant"] == "R" and r["run"] == 0}
    say = lambda r: " ".join(x for x in ((r["line"] or {}).get(k) for k in ("ack", "expand", "question")) if x) or "(대본)"
    lines, key = ["| id | 아이 말 | 가 | 나 | 고른 쪽 |", "|---|---|---|---|---|"], {}
    for c in CASES:
        if c["id"] not in b or c["id"] not in rr:
            continue
        sides = [("B", b[c["id"]]), ("R", rr[c["id"]])]
        rnd.shuffle(sides)
        key[c["id"]] = [s for s, _ in sides]
        lines.append(f"| {c['id']} | {c['req']['utterance']} | {say(sides[0][1])} | {say(sides[1][1])} | |")
    (RAW / f"dialogue_{stamp}_pairs.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (RAW / f"dialogue_{stamp}_pairs_key.json").write_text(json.dumps(key, ensure_ascii=False), encoding="utf-8")


def show(report: dict) -> None:
    for k, v in report.items():
        print(f"{k}: {json.dumps(v, ensure_ascii=False)}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--variants", default="B,R,M")
    ap.add_argument("--env", default=None, help="a .env to read keys from (values are never printed)")
    ap.add_argument("--yes-spend", action="store_true", help="real vendor calls cost money")
    ap.add_argument("--score", default=None, help="score a saved raw file instead of running")
    ap.add_argument("--mock", action="store_true", help="dry run on the mock server — checks the harness, measures nothing")
    ap.add_argument("--cached-usd", type=float, default=None, help="luna cached input $/Mtok — with --jev-won, adds won per turn")
    ap.add_argument("--jev-won", type=float, default=None, help="won per Jev call, once known from the bill")
    a = ap.parse_args()
    if a.cached_usd is not None and a.jev_won is not None:
        PRICES.update(cached_usd=a.cached_usd, jev_won=a.jev_won)
    if a.score:
        rows = [json.loads(l) for l in Path(a.score).read_text(encoding="utf-8").splitlines() if l.strip()]
        show(score(rows))
        return
    if not a.yes_spend and not a.mock:
        sys.exit(f"{len(CASES)} cases × {a.runs} runs × {a.variants} — add --yes-spend to call the vendors")
    if a.env:
        other = Settings(_env_file=a.env)
        for k in ("openai_api_key", "typesafe_api_key", "openai_base_url"):
            setattr(settings, k, getattr(other, k))
    stamp = ("mock_" if a.mock else "") + time.strftime("%m%d_%H%M")
    RAW.mkdir(exist_ok=True)
    rows = asyncio.run(run(a.variants.split(","), a.runs, a.mock, RAW / f"dialogue_{stamp}.jsonl"))
    blind_pairs(rows, stamp)
    show(score(rows))
    print(f"raw: eval/raw/dialogue_{stamp}.jsonl · pairs: eval/raw/dialogue_{stamp}_pairs.md")


if __name__ == "__main__":
    main()
