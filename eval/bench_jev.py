# -*- coding: utf-8 -*-
"""Jev를 우리 평가셋 100문항으로 잰다 — 두 질문에 답하려고.

  1. **한국어에서 되나.** 문서 어디에도 한국어 성능 언급이 없다
     (`docs/조사_Jev_판정모델_검토.md` 5절). 우리 프롬프트는 전부 한국어다.
  2. **확신도가 보정되어 있나.** 0.9 구간이 실제로 90%에 가까운지가
     이 기능을 쓸 수 있는지의 전부다.

⚠️ **다른 후보와 총점을 나란히 두지 않는다.** Jev는 16필드 중 9개만 답한다 —
   나머지 7은 자유 문장이고 한계 문서가 "글을 만들도록 학습되지 않았다"고 적었다.
   **그래서 9필드만 물는다.** 7필드의 0점은 "틀렸다"가 아니라 "안 하는 일"이다.
   그 9개 중 `next_slot` 은 다시 버린다 — 평가셋에 gold 가 없다(아래 UNLABELLED).

값: 100만 입력 토큰당 $0.042 · 출력 무료. 100문항이면 1원 남짓이다.

    python -m eval.bench_jev            (전체)
    python -m eval.bench_jev --n 10     (10문항만)
"""
from __future__ import annotations

import argparse
import io
import json
import statistics
import sys
import time
from pathlib import Path

from .config import load_dotenv, resolve_model
from .providers import create_provider

EVAL = Path(__file__).parent
# Jev가 답할 수 있는 9개. 나머지 7은 애초에 묻지 않는다.
# ⚠️ `next_slot` 은 **채점에서 뺀다.** 평가셋 gold 100개가 전부 null 이다 —
#    라벨링이 안 된 필드이고(`guidelines/6` §6-4가 "일부러 비어 있는 게 아니라 아직 못 정한 것"이라
#    적어 뒀다), 넣어 두면 **어느 모델이든 0%가 나와 모델 실패로 오독된다.**
#    09-22에 실제로 한 번 그렇게 읽었다. 물기는 하되 채점하지 않는다.
UNLABELLED = ["next_slot"]
FIELDS = ["slot_1", "slot_2", "no_longer_needed",
          "s1_reason", "s2_addition", "contradiction", "unclear", "story_ready"]
BINS = [(0.0, 0.5), (0.5, 0.7), (0.7, 0.9), (0.9, 1.01)]


def rows(n: int | None):
    out = []
    with io.open(EVAL / "fixtures_judge.jsonl", encoding="utf-8") as f:
        for line in f:
            r = json.loads(line)
            if r.get("gold"):
                out.append(r)
            if n and len(out) >= n:
                break
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=None)
    args = ap.parse_args()

    load_dotenv()
    cfg = resolve_model("jev-latest")
    p = create_provider("typesafe")
    sysp = io.open(EVAL / "judge_prompt.md", encoding="utf-8").read()
    schema = json.load(io.open(EVAL / "judge_schema.json", encoding="utf-8"))

    data = rows(args.n)
    print("%d문항 · %s · %d필드 채점 (gold 없는 %s 는 제외)\n"
          % (len(data), cfg["api_model"], len(FIELDS), ", ".join(UNLABELLED)), flush=True)

    # hits[field] = [(맞았나, 확신도), ...]
    hits: dict[str, list] = {f: [] for f in FIELDS}
    times, toks, fails = [], 0, 0
    raw = io.open(EVAL / "raw" / "jev-latest.jsonl", "w", encoding="utf-8")

    for i, r in enumerate(data, 1):
        userp = json.dumps({k: r.get(k) for k in ("slots", "asked", "template", "utterance")},
                           ensure_ascii=False)
        for attempt in range(4):
            try:
                t0 = time.time()
                res = p.stream_judge(model=cfg["api_model"], system_prompt=sysp,
                                     user_prompt=userp, schema=schema)
                out = json.loads("".join(c.text for c in res.chunks))
                times.append(time.time() - t0)
                toks += res.usage.input_tokens
                break
            except Exception as e:
                # 문서가 429·529는 백오프 재시도하라고 적었다
                if attempt == 3:
                    print("  [%d] 실패: %s" % (i, str(e)[:120]), flush=True)
                    fails += 1
                    out = None
                    break
                time.sleep(1.5 * (attempt + 1))
        if out is None:
            continue

        conf = out.pop("_confidence", {})
        raw.write(json.dumps({"id": r.get("id"), "answer": out, "confidence": conf},
                             ensure_ascii=False) + "\n")
        g = r["gold"]
        for f in FIELDS:
            hits[f].append((out.get(f) == g.get(f), conf.get(f)))
        if i % 20 == 0:
            print("  %d/%d" % (i, len(data)), flush=True)
    raw.close()

    n = len(times)
    print("\n■ 필드별 정답률")
    for f in FIELDS:
        v = hits[f]
        if not v:
            continue
        acc = sum(1 for ok, _ in v if ok) / len(v)
        cs = [c for _, c in v if c is not None]
        print("  %-19s %5.1f%%   확신도 중앙값 %s"
              % (f, acc * 100, "%.2f" % statistics.median(cs) if cs else "—"))

    flat = [(ok, c) for f in FIELDS for ok, c in hits[f] if c is not None]
    overall = sum(1 for ok, _ in flat if ok) / len(flat) if flat else 0
    print("\n  %d필드 전체 %5.1f%%  (%d개 판단)" % (len(FIELDS), overall * 100, len(flat)))

    print("\n■ 확신도가 보정되어 있나 — 구간별 실제 정답률")
    print("  %-12s %8s %8s  %s" % ("확신도", "판단수", "정답률", ""))
    for lo, hi in BINS:
        b = [ok for ok, c in flat if lo <= c < hi]
        if b:
            acc = sum(b) / len(b)
            bar = "█" * int(acc * 24)
            print("  %.1f~%.1f      %8d %7.1f%%  %s" % (lo, hi, len(b), acc * 100, bar))
        else:
            print("  %.1f~%.1f      %8d        —" % (lo, hi, 0))

    print("\n■ 문턱을 두면 — 통과한 것만의 정답률")
    for floor in (0.0, 0.5, 0.6, 0.7, 0.8, 0.9):
        kept = [ok for ok, c in flat if c >= floor]
        if kept:
            print("  ≥ %.1f   남는 판단 %4d개 (%.0f%%) · 정답률 %5.1f%%"
                  % (floor, len(kept), 100 * len(kept) / len(flat),
                     100 * sum(kept) / len(kept)))

    if n:
        cost = toks / 1e6 * cfg["input_usd_per_mtok"]
        print("\n■ 속도와 값")
        print("  중앙값 %.2f초 · p95 %.2f초 · 최대 %.2f초"
              % (statistics.median(times), sorted(times)[int(n * 0.95) - 1], max(times)))
        print("  입력 %d토큰 · $%.4f (약 %.1f원) · 실패 %d건" % (toks, cost, cost * 1400, fails))
    print("\n  원본: eval/raw/jev-latest.jsonl")


if __name__ == "__main__":
    sys.exit(main())
