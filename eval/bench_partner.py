"""#303 — who sits with the child: the app's word list against one Jev choice question.

Same answers (eval/fixtures_partner.jsonl), each read by
- words: a Python port of the app's `partnerIn` (demo/Model.kt, with #300's solo rule) — the phone's
  answer today, and its fallback after this change
- jev:   POST /partner's question (backend/app/llm/jev.py `partner`), RUNS times

Prints accuracy overall and per kind (plain · name · solo · negation · variant · unclear), Jev latency,
and every miss. For words, None (nothing found) counts as unknown, as the app then asks again.

    py eval/bench_partner.py --runs 3
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

from app.llm import jev  # noqa: E402

FIXTURES = ROOT / "eval" / "fixtures_partner.jsonl"

# ── port of demo/Model.kt partnerIn (rev of #300) ──────────────────────────
PARTNER_WORDS = [
    ("외할아버지", "grandpa"), ("친할아버지", "grandpa"), ("할아버지", "grandpa"), ("할부지", "grandpa"),
    ("외할머니", "grandma"), ("친할머니", "grandma"), ("할머니", "grandma"), ("할미", "grandma"),
    ("큰아빠", "uncle"), ("작은아빠", "uncle"), ("큰아버지", "uncle"), ("작은아버지", "uncle"),
    ("이모부", "uncle"), ("고모부", "uncle"), ("외삼촌", "uncle"), ("삼촌", "uncle"),
    ("큰엄마", "aunt"), ("작은엄마", "aunt"), ("외숙모", "aunt"), ("숙모", "aunt"), ("고모", "aunt"), ("이모", "aunt"),
    ("어머니", "mom"), ("엄마", "mom"), ("아버지", "dad"), ("아빠", "dad"),
    ("선생님", "teacher"), ("쌤", "teacher"),
    ("언니", "sibling"), ("누나", "sibling"), ("오빠", "sibling"), ("형아", "sibling"), ("형", "sibling"), ("동생", "sibling"),
    ("친구", "friend"),
]
NOT_A_NAME = {"몰라", "없어", "아무도", "혼자", "나", "나랑", "응", "아니", "싫어", "그냥", "몰라요", "없어요"}
SOLO_EXACT = {"나만", "나만있어", "나만할래", "나만왔어", "저만", "저혼자요"}


def words(text: str) -> str | None:
    compact = "".join(c for c in text if "가" <= c <= "힣")
    denies = re.search(r"혼자(?:가)?(?:아니|아닌|말고)", compact)
    if not denies and ("혼자" in compact or "아무도없" in compact or compact in SOLO_EXACT):
        return "solo"
    for w, key in PARTNER_WORDS:
        if w in text:
            return key
    first = (text.strip().split() or [""])[0].rstrip("!.?~,")
    for suf in ("이랑", "랑", "하고", "이요", "요", "이야", "야", "아"):
        first = first.removesuffix(suf)
    looks = len(text.strip().split()) <= 2 and 2 <= len(first) <= 3 \
        and all("가" <= c <= "힣" for c in first) and first not in NOT_A_NAME
    return "friend" if looks else None


async def main(runs: int) -> None:
    rows = [json.loads(line) for line in FIXTURES.read_text(encoding="utf-8").splitlines() if line.strip()]
    hit = defaultdict(lambda: defaultdict(lambda: [0, 0]))      # reader → kind → [right, total]
    misses, secs, errors = defaultdict(list), [], 0

    def score(reader: str, row: dict, got: str | None) -> None:
        got = got or "unknown"
        for k in (row["kind"], "all"):
            hit[reader][k][1] += 1
            hit[reader][k][0] += got == row["want"]
        if got != row["want"]:
            misses[reader].append(f'{row["text"]} → {got} (want {row["want"]})')

    for row in rows:
        score("words", row, words(row["text"]))
    for r in range(runs):
        for row in rows:
            try:
                key, _conf, s = await jev.partner(row["text"])
                secs.append(s)
            except jev.JevError as e:
                errors += 1
                key = None
                print(f"run {r + 1} error: {e}", file=sys.stderr)
            score("jev", row, key)

    kinds = ["all", "plain", "name", "solo", "negation", "variant", "unclear"]
    for reader in ("words", "jev"):
        cells = [f"{k} {hit[reader][k][0]}/{hit[reader][k][1]}" for k in kinds if hit[reader][k][1]]
        print(f"{reader:5} | " + " · ".join(cells))
    if secs:
        q = statistics.quantiles(secs, n=20)
        print(f"jev latency p50 {statistics.median(secs):.2f}s · p95 {q[18]:.2f}s · errors {errors}")
    for reader in ("words", "jev"):
        print(f"\n{reader} misses ({len(misses[reader])}):")
        for m in sorted(set(misses[reader])):
            print(f"  {m} ×{misses[reader].count(m)}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    asyncio.run(main(ap.parse_args().runs))
