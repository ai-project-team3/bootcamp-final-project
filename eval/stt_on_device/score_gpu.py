"""Deck numbers for the small model: per-age CER, words kept, hallucination drop, adult set — tuned vs vanilla small.

    py score_gpu.py <repo> <tuned.csv> <vanilla.csv>
"""
import csv
import sys
from pathlib import Path

repo = Path(sys.argv[1])
sys.path.insert(0, str(repo / "eval" / "whisper_ft"))
from evaluate import YOUNG, bootstrap_gain, edit, norm, words_kept  # noqa: E402

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def load(p):
    return list(csv.DictReader(open(p, encoding="utf-8-sig")))


def stats(rows, ages):
    rs = [r for r in rows if r["set"] == "child" and int(r["age"]) in ages]
    err = sum(edit(norm(r["ref"]), norm(r["hyp"])) for r in rs)
    ln = sum(len(norm(r["ref"])) for r in rs)
    by = {}
    for r in rs:
        k, n = words_kept(r["ref"], r["hyp"])
        b = by.setdefault(r["speaker"], [0, 0]); b[0] += k; b[1] += n
    kept = sum(b[0] for b in by.values()) / max(sum(b[1] for b in by.values()), 1) * 100
    drop = sum(r["dropped"] not in ("", "0", "False", "false") for r in rs) / max(len(rs), 1) * 100
    return {"n": len(rs), "cer": err / max(ln, 1) * 100, "kept": kept, "drop": drop, "by": by}


tuned, base = load(sys.argv[2]), load(sys.argv[3])
print("| 나이 | 클립 | CER 원래→학습 | 어절 보존 원래→학습 | 헛문장 원래→학습 |")
for ages, lab in [((3,), "3세"), ((4,), "4세"), ((5,), "5세"), ((6,), "6세"), (YOUNG, "3~5세"), ((3, 4, 5, 6), "전체")]:
    b, t = stats(base, ages), stats(tuned, ages)
    print(f"| {lab} | {t['n']} | {b['cer']:.1f}% → {t['cer']:.1f}% | {b['kept']:.1f}% → {t['kept']:.1f}% | {b['drop']:.1f}% → {t['drop']:.1f}% |")
yb, yt = stats(base, YOUNG), stats(tuned, YOUNG)
lo, hi = bootstrap_gain(yb["by"], yt["by"])
print(f"3~5세 어절 보존 차 {yt['kept'] - yb['kept']:+.1f}%p [{lo:+.1f}, {hi:+.1f}]")
ad = lambda rows: {r["id"]: norm(r["ref"]) in norm(r["hyp"]) for r in rows if r["set"] == "adult"}  # noqa: E731
a_t, a_b = ad(tuned), ad(base)
lost = sorted(k for k in a_t if a_b.get(k) and not a_t[k])
print(f"어른 세트 맞힘 원래 {sum(a_b.values())}/57 → 학습 {sum(a_t.values())}/57 · 새로 놓친 것 {len(lost)}: {lost}")
lat = sorted(int(r["latency_ms"]) for r in tuned if r["latency_ms"])
print(f"GPU 지연 p50 {lat[len(lat)//2]}ms")
