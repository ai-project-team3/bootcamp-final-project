# -*- coding: utf-8 -*-
"""Is the running server still transcribing children as well as on 09-23? (10-01)

The team felt transcripts got worse in the evening. This sends the same AI-Hub 3~6 clips that
were measured on 09-23 with large-v3 (eval/raw/stt_child36_v3_20260923.csv) to the live /stt
and scores both on exactly those clips. Same clips, same scoring → if the CER matches, the
server is not the cause and the difference is in what the phone sends.

Over the LAN to PC1 only — the audio never goes to an outside service (differentiator 1,
AI-Hub terms). Clips are read from D:\\aihub\\child36 (not in git).

    py eval/stt_live_check.py --per-age 40 [--server http://192.168.0.46:8000]
"""
from __future__ import annotations

import argparse
import csv
import random
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from stt_eval import edit_distance, normalize  # noqa: E402

BASE = Path(__file__).parent / "raw" / "stt_child36_v3_20260923.csv"
AUDIO = Path(r"D:\aihub\child36")


def cer(ref: str, hyp: str) -> float:
    r, h = normalize(ref), normalize(hyp)
    return edit_distance(r, h) / max(1, len(r))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-age", type=int, default=40)
    ap.add_argument("--server", default="http://192.168.0.46:8000")
    ap.add_argument("--seed", type=int, default=1001)
    a = ap.parse_args()

    rows = list(csv.DictReader(open(BASE, encoding="utf-8-sig")))
    by_age: dict[str, list[dict]] = {}
    for r in rows:
        if r["dropped"]:            # 09-23 already had nothing usable — not a fair comparison clip
            continue
        by_age.setdefault(r["condition"].split("_")[0], []).append(r)
    rnd = random.Random(a.seed)
    pick = [r for age in sorted(by_age) for r in rnd.sample(by_age[age], min(a.per_age, len(by_age[age])))]

    wavs = {p.stem: p for p in AUDIO.rglob("*.wav")}
    out = Path(__file__).parent / "raw" / f"stt_live_check_{time.strftime('%Y%m%d_%H%M')}.csv"
    stats: dict[str, list] = {}
    with httpx.Client(timeout=60) as http, open(out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["clip_id", "age", "ref", "hyp_0923", "hyp_live", "cer_0923", "cer_live", "ms"])
        for i, r in enumerate(pick, 1):
            wav = wavs.get(r["clip_id"])
            if wav is None:
                continue
            t0 = time.monotonic()
            res = http.post(f"{a.server}/stt", files={"file": (wav.name, wav.read_bytes(), "audio/wav")})
            ms = round((time.monotonic() - t0) * 1000)
            live = res.json().get("text", "") if res.status_code == 200 else f"<HTTP {res.status_code}>"
            age = r["condition"].split("_")[0]
            c0, c1 = cer(r["ref_heard"], r["hyp"]), cer(r["ref_heard"], live)
            stats.setdefault(age, []).append((c0, c1, live == "", normalize(live) == normalize(r["hyp"]), ms))
            w.writerow([r["clip_id"], age, r["ref_heard"], r["hyp"], live, f"{c0:.3f}", f"{c1:.3f}", ms])
            if i % 20 == 0:
                print(f"  {i}/{len(pick)}", flush=True)

    print(f"\n{'age':<6}{'clips':>6}{'CER 09-23':>11}{'CER live':>10}{'same text':>11}{'empty':>7}{'p50 ms':>8}")
    allr = []
    for age in sorted(stats):
        s = stats[age]; allr += s
        p50 = sorted(x[4] for x in s)[len(s) // 2]
        print(f"{age:<6}{len(s):>6}{sum(x[0] for x in s)/len(s):>10.1%}{sum(x[1] for x in s)/len(s):>10.1%}"
              f"{sum(x[3] for x in s)/len(s):>11.0%}{sum(x[2] for x in s):>7}{p50:>8}")
    print(f"{'all':<6}{len(allr):>6}{sum(x[0] for x in allr)/len(allr):>10.1%}{sum(x[1] for x in allr)/len(allr):>10.1%}"
          f"{sum(x[3] for x in allr)/len(allr):>11.0%}{sum(x[2] for x in allr):>7}")
    print(f"→ {out.relative_to(Path(__file__).resolve().parents[1])}")


if __name__ == "__main__":
    main()
