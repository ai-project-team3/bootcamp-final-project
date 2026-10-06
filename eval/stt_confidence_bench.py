# -*- coding: utf-8 -*-
"""Does whisper's word probability tell a word it heard from a word it made up? (#149 · 10-06)

A device round (10-02) got 「친구」 for a mumble — a plausible word the child never said — and the app put it
in a slot. The plan was: if a word's probability is low, treat the answer as 「못 알아들음」 and ask again.
That only works if right and wrong words land on different probabilities. This measures it on AI-Hub 108
(3~6세 · 260 speakers · 3,681 clips — the same set as the 09-23 CER run), with the server's own settings
(`large-v3`, beam 5, Korean, no VAD) plus word timestamps for the probabilities.

A hypothesis eojeol is 「right」 when the same eojeol is in the reference, 「wrong」 otherwise — a made-up
word, a misheard one, or a split that no longer matches. Prints the AUC and, per threshold, how many wrong
words a 「below → ask again」 rule would catch and how many right answers it would send back.

    set AIHUB_CHILD=D:\\aihub\\child36
    .venv-diar/Scripts/python.exe -m eval.stt_confidence_bench [--limit 15]

⚠️ AI-Hub data must not be redistributed; the CSV goes to eval/raw/ (ignored). No external STT.
"""
from __future__ import annotations

import argparse
import csv
import io
import re
import sys
from datetime import date

try:
    from .stt_child_bench import DATA, RAW, items
    from .stt_bias_bench import Whisper
    from .stt_eval import edit_distance
except ImportError:
    from stt_child_bench import DATA, RAW, items
    from stt_bias_bench import Whisper
    from stt_eval import edit_distance


def eojeols(s: str) -> list[str]:
    return [w for w in re.sub(r"[^\w\s]", " ", s).split() if w]


def auc(pos: list[float], neg: list[float]) -> float:
    """P(a right word scores above a wrong one) — 0.5 is a coin, 1.0 separates fully."""
    allv = sorted([(v, 1) for v in pos] + [(v, 0) for v in neg])
    rank_sum, i = 0.0, 0
    while i < len(allv):
        j = i
        while j < len(allv) and allv[j][0] == allv[i][0]:
            j += 1
        mid = (i + j + 1) / 2                       # average rank for ties, 1-based
        rank_sum += mid * sum(1 for k in range(i, j) if allv[k][1])
        i = j
    return (rank_sum - len(pos) * (len(pos) + 1) / 2) / (len(pos) * len(neg))


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=None, help="clips per speaker")
    ap.add_argument("--model", default="large-v3")
    a = ap.parse_args()
    if not DATA.exists():
        sys.exit(f"no data at {DATA} — set AIHUB_CHILD")

    w = Whisper(a.model)
    clips = list(items(a.limit))
    RAW.mkdir(exist_ok=True)
    out = RAW / f"stt_confidence_{date.today():%Y%m%d}.csv"
    words, utts = [], []                             # (age, prob, right) · (age, min prob, cer)
    with io.open(out, "w", encoding="utf-8-sig", newline="") as f:
        wr = csv.writer(f)
        wr.writerow(["clip_id", "age", "ref", "hyp", "word", "prob", "right"])
        for n, it in enumerate(clips, 1):
            segs, _ = w.m.transcribe(str(it["wav"]), language="ko", beam_size=5, vad_filter=False,
                                     word_timestamps=True)
            segs = list(segs)
            hyp = "".join(s.text for s in segs).strip()
            ref_set = set(eojeols(it["ref"]))
            age = it["condition"].split("_")[0]
            probs = []
            for s in segs:
                for x in s.words or []:
                    token = "".join(eojeols(x.word))
                    if not token:
                        continue
                    right = token in ref_set
                    words.append((age, x.probability, right))
                    probs.append(x.probability)
                    wr.writerow([it["clip_id"], age, it["ref"], hyp, token, f"{x.probability:.4f}", int(right)])
            ref_c, hyp_c = "".join(eojeols(it["ref"])), "".join(eojeols(hyp))
            cer = edit_distance(ref_c, hyp_c) / max(1, len(ref_c))
            utts.append((age, min(probs) if probs else 0.0, cer))
            if n % 100 == 0:
                print(f"\r{n}/{len(clips)}", end="", flush=True)
    print(f"\n→ {out}\n")

    print("| 나이 | 낱말 | 맞은 낱말 확률 p50 | 틀린 낱말 확률 p50 | AUC |")
    print("|---|---:|---:|---:|---:|")
    for age in sorted({x[0] for x in words}) + ["전체"]:
        sel = [x for x in words if age == "전체" or x[0] == age]
        pos = sorted(p for _, p, r in sel if r)
        neg = sorted(p for _, p, r in sel if not r)
        if pos and neg:
            print(f"| {age} | {len(sel)} | {pos[len(pos) // 2]:.2f} | {neg[len(neg) // 2]:.2f} | {auc(pos, neg):.3f} |")

    print("\n문턱 아래 낱말이 하나라도 있으면 「다시 말해 줄래?」로 돌린다면 —")
    print("| 문턱 | 틀린 낱말 걸러짐 | 맞은 낱말 잘못 걸림 | 되묻는 발화 | 그중 CER ≤ 20% (멀쩡한 답) | CER > 50% 발화 중 걸러짐 |")
    print("|---:|---:|---:|---:|---:|---:|")
    neg_all = [p for _, p, r in words if not r]
    pos_all = [p for _, p, r in words if r]
    bad = [u for u in utts if u[2] > 0.5]
    for t in (0.2, 0.3, 0.4, 0.5, 0.6):
        flagged = [u for u in utts if u[1] < t]
        ok_flagged = sum(1 for u in flagged if u[2] <= 0.2)
        print(f"| {t} | {sum(p < t for p in neg_all) / len(neg_all):.0%} | {sum(p < t for p in pos_all) / len(pos_all):.0%} "
              f"| {len(flagged) / len(utts):.0%} | {ok_flagged / max(1, len(flagged)):.0%} "
              f"| {sum(1 for u in bad if u[1] < t) / max(1, len(bad)):.0%} |")


if __name__ == "__main__":
    main()
