"""Score a phone run (run_eval.sh tsv) with evaluate.py's own rules; optionally compare to a GPU run's CSV.

    py score_phone.py <repo> <clips.csv> <phone.tsv> [<baseline.csv> <engine>]

latency = whisper-cli total - load (the app keeps the model loaded); the first clip is warm-up and left out.
"""
import csv
import sys
from pathlib import Path

repo = Path(sys.argv[1])
sys.path.insert(0, str(repo / "eval" / "whisper_ft"))
sys.path.insert(0, str(repo / "backend"))
from evaluate import YOUNG, bootstrap_gain, edit, norm, p50, words_kept  # noqa: E402
from app.filters.hallucination import check_transcript  # noqa: E402

COLLAPSE = "--collapse" in sys.argv
if COLLAPSE:
    sys.argv.remove("--collapse")


def collapse(t: str) -> str:
    """App-side clean-up: a phrase repeated back to back (the reduced-window loop) is kept once."""
    w = t.split()
    i = 0
    while i < len(w):
        for n in range(1, (len(w) - i) // 2 + 1):
            if w[i:i + n] == w[i + n:i + 2 * n]:
                j = i + n
                while w[j:j + n] == w[i:i + n]:
                    j += n
                w = w[:i + n] + w[j:]
                break
        else:
            i += 1
    return " ".join(w)


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
clips = {r["name"]: r for r in csv.DictReader(open(sys.argv[2], encoding="utf-8-sig"))}
rows = []
for i, line in enumerate(open(sys.argv[3], encoding="utf-8", errors="replace")):  # whisper.cpp can cut a token mid-character
    p = line.rstrip("\n").split("\t")
    if len(p) < 4 or p[0] not in clips:
        continue
    c = dict(clips[p[0]])
    c["age"] = int(c["age"])
    c["hyp"] = collapse(p[3]) if COLLAPSE else p[3]
    c["lat"] = (float(p[2]) - float(p[1])) if p[1] and p[2] and i > 0 else None
    c["dropped"] = not check_transcript(c["hyp"]).keep
    rows.append(c)

base = {}
if len(sys.argv) > 5:
    for r in csv.DictReader(open(sys.argv[4], encoding="utf-8-sig")):
        if r["engine"] == sys.argv[5]:
            base[(r["set"], r["id"])] = r


def stats(rs, ages):
    rs = [r for r in rs if r["set"] == "child" and int(r["age"]) in ages]
    err = sum(edit(norm(r["ref"]), norm(r["hyp"])) for r in rs)
    ln = sum(len(norm(r["ref"])) for r in rs)
    by = {}
    for r in rs:
        k, n = words_kept(r["ref"], r["hyp"])
        b = by.setdefault(r["speaker"], [0, 0]); b[0] += k; b[1] += n
    kept = sum(b[0] for b in by.values()) / max(sum(b[1] for b in by.values()), 1) * 100
    drop = sum(bool(r["dropped"]) for r in rs) / max(len(rs), 1) * 100
    lats = [r["lat"] for r in rs if r.get("lat") is not None]
    return {"n": len(rs), "cer": err / max(ln, 1) * 100, "kept": kept, "drop": drop, "p50": p50(lats),
            "p90": sorted(lats)[int(len(lats) * 0.9)] if lats else 0, "by": by}


done = len(rows)
print(f"scored {done} clips (child {sum(r['set'] == 'child' for r in rows)} · adult {sum(r['set'] == 'adult' for r in rows)})")
brows = [dict(base[(r["set"], r["id"])], dropped=base[(r["set"], r["id"])]["dropped"]) for r in rows if (r["set"], r["id"]) in base]
print("| 나이 | 클립 | CER | 어절 보존 | 헛문장 버림 | 지연 p50 / p90 |" + (" 기준선 CER · 어절 |" if base else ""))
for ages, label in [((3,), "3세"), ((4,), "4세"), ((5,), "5세"), ((6,), "6세"), (YOUNG, "3~5세"), ((3, 4, 5, 6), "전체")]:
    s = stats(rows, ages)
    extra = ""
    if base:
        b = stats(brows, ages)
        extra = f" {b['cer']:.1f}% · {b['kept']:.1f}% |"
    print(f"| {label} | {s['n']} | {s['cer']:.1f}% | {s['kept']:.1f}% | {s['drop']:.1f}% | {s['p50']:.0f} / {s['p90']:.0f}ms |{extra}")

ad = {r["id"]: norm(r["ref"]) in norm(r["hyp"]) for r in rows if r["set"] == "adult"}
print(f"\n어른 세트: 맞힘 {sum(ad.values())}/{len(ad)}")
if base:
    bd = {r["id"]: norm(r["ref"]) in norm(r["hyp"]) for r in brows if r["set"] == "adult"}
    lost = sorted(k for k in ad if bd.get(k) and not ad[k])
    print(f"기준선 어른 맞힘 {sum(bd.values())}/{len(bd)} · 기준선은 맞히다 폰에서 놓친 것 {len(lost)}: {lost}")
    yb, yp = stats(brows, YOUNG), stats(rows, YOUNG)
    lo, hi = bootstrap_gain(yb["by"], yp["by"])
    ab, ap = stats(brows, (3, 4, 5, 6)), stats(rows, (3, 4, 5, 6))
    print(f"3~5세 어절 보존 차(폰 − 기준선) {yp['kept'] - yb['kept']:+.1f}%p [{lo:+.1f}, {hi:+.1f}] · "
          f"헛문장 차 {ap['drop'] - ab['drop']:+.1f}%p")
