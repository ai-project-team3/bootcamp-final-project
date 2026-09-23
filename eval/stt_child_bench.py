# -*- coding: utf-8 -*-
"""AI-Hub 아동 음성으로 자체 whisper 를 잰다 — **아이 목소리 첫 측정** (09-23).

데이터
  AI-Hub 「자유대화 음성(소아남여)」 샘플 — 9/18 신청 즉시 승인, 9/20 다운로드(`New_Sample.zip`).
  화자 2명(7세 여 · 9세 여) × 2,000발화 · 실내 · iOS/Android 녹음 · 16kHz mono · 평균 4.3초 · 평균 5.2어절.
  라벨 json 의 `발화정보.stt` 가 정답 전사다.
  ⚠️ **재배포 금지 데이터다.** 풀어 둔 폴더는 깃 밖(`%TEMP%/aihub_child`)이고 `eval/audio/aihub/` 도 무시된다.
  ⚠️ **Grok 등 외부 STT 에 넣지 않는다** — 차별점 1 · 이용 조건.

무엇을 재나
  1. CER (음절 편집거리 / 정답 길이) — 성인 7.8% · 아동 120.8% 전례(whisper base)와 같은 자리
  2. 어절 정확 일치율 — 슬롯에 들어갈 낱말이 살아남는지의 대리 지표 (라벨에 target_words 가 없다)
  3. 헛문장 거르기(`backend/app/filters/hallucination.py`)가 아이 말을 버리는 수 — 0 이어야 한다
  4. 지연 p50/p95

출력은 `eval/stt_eval.py` 의 CSV 형식이다 — 채점을 그 하네스에 맡긴다.

    .venv-diar/Scripts/python.exe -m eval.stt_child_bench            (전부 · 20분 남짓)
    .venv-diar/Scripts/python.exe -m eval.stt_child_bench --limit 200  (화자당 200)
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
from datetime import date
from pathlib import Path

try:
    from .stt_bias_bench import Whisper, norm
    from .stt_eval import edit_distance, percentile
except ImportError:
    from stt_bias_bench import Whisper, norm
    from stt_eval import edit_distance, percentile

ROOT = Path(__file__).resolve().parent
DATA = Path(os.environ.get("AIHUB_CHILD", os.path.join(os.environ.get("TEMP", "."), "aihub_child")))
RAW = ROOT / "raw"


def items(limit: int | None):
    per = {}
    for j in sorted(DATA.rglob("*.json")):
        d = json.loads(j.read_text(encoding="utf-8-sig"))
        u, c, r = d["발화정보"], d["대화정보"], d["녹음자정보"]
        wav = next(DATA.rglob(u["fileNm"]), None)
        if wav is None:
            continue
        key = r["recorderId"]
        if limit and per.get(key, 0) >= limit:
            continue
        per[key] = per.get(key, 0) + 1
        # 라벨의 잡음·비유창 표기 「(SN:)」「(SP:)」 등은 말이 아니다 — 정답에서 뗀다
        ref = re.sub(r"\([A-Z]+:[^)]*\)", " ", u["stt"])
        ref = re.sub(r"\s+", " ", ref).strip()
        yield {
            "clip_id": j.stem, "wav": wav, "ref": ref, "sec": float(u["recrdTime"]),
            "condition": f"age{r['age']}_{r['gender']}_{c['recrdUnit']}_{c['recrdEnvrn']}",
        }


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=None, help="화자당 클립 수")
    ap.add_argument("--models", default="large-v3-turbo", help="쉼표로 여러 개 — 예: large-v3-turbo,large-v3")
    a = ap.parse_args()
    if not DATA.exists():
        sys.exit(f"데이터가 없습니다: {DATA} — New_Sample.zip 을 풀어 두거나 AIHUB_CHILD 로 경로를 준다")

    sys.path.insert(0, str(ROOT.parent / "backend"))
    from app.filters.hallucination import check_transcript

    RAW.mkdir(exist_ok=True)
    out = RAW / f"stt_child_{date.today():%Y%m%d}.csv"
    models = [m.strip() for m in a.models.split(",") if m.strip()]
    clips = list(items(a.limit))
    fields = ["clip_id", "engine", "condition", "is_silence", "ref_heard", "ref_meant", "target_words", "hyp", "latency_ms", "dropped"]
    rows = []
    with io.open(out, "w", encoding="utf-8-sig", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=fields); wr.writeheader()
        for mname in models:
            # 모델을 하나씩 올린다 — 같은 클립 목록을 같은 순서로 돈다. 3060 12GB 에 둘을 동시에 올릴 이유가 없다
            w = Whisper(mname)
            for n, it in enumerate(clips, 1):
                r = w(it["wav"], None)
                v = check_transcript(r["hyp"])
                row = {"clip_id": it["clip_id"], "engine": f"whisper_{mname}", "condition": it["condition"], "is_silence": "0",
                       "ref_heard": it["ref"], "ref_meant": it["ref"], "target_words": "", "hyp": r["hyp"],
                       "latency_ms": f"{r['latency_ms']:.0f}", "dropped": "" if v.keep else v.reason}
                wr.writerow(row); f.flush(); rows.append(row)
                if n % 100 == 0:
                    print(f"\r{mname} {n}/{len(clips)}", end="")
            print()
            del w
    print(f"→ {out}\n")

    # 요약 — 모델 × 조건
    by = {}
    for r in rows:
        g = by.setdefault((r["engine"], r["condition"]), {"n": 0, "err": 0, "len": 0, "wok": 0, "w": 0, "lat": [], "drop": 0, "exact": 0})
        ref, hyp = norm(r["ref_heard"]), norm(r["hyp"])
        g["n"] += 1; g["err"] += edit_distance(ref, hyp); g["len"] += len(ref); g["lat"].append(float(r["latency_ms"]))
        g["exact"] += ref == hyp
        rw, hw = r["ref_heard"].split(), r["hyp"].replace(".", "").replace(",", "").replace("?", "").split()
        g["w"] += len(rw); g["wok"] += sum(1 for x in rw if x in hw)
        g["drop"] += bool(r["dropped"])
    print("| 모델 | 조건 | 클립 | CER | 문장 정확 일치 | 어절 보존 | 헛문장으로 버림 | 지연 p50 | p95 |")
    print("|---|---|---:|---:|---:|---:|---:|---:|---:|")
    for (e, c), g in sorted(by.items()):
        print(f"| {e[8:]} | {c} | {g['n']} | {g['err']/g['len']*100:.1f}% | {g['exact']/g['n']*100:.1f}% | {g['wok']/g['w']*100:.1f}% | {g['drop']} | "
              f"{percentile(g['lat'],0.5):.0f}ms | {percentile(g['lat'],0.95):.0f}ms |")
    worst = sorted(rows, key=lambda r: -edit_distance(norm(r["ref_heard"]), norm(r["hyp"])) / max(len(norm(r["ref_heard"])), 1))[:12]
    print("\n**가장 많이 틀린 12** (정답 → 받아쓴 말)")
    for r in worst:
        print(f"- [{r['engine'][8:]} {r['condition'][:5]}] 「{r['ref_heard']}」 → 「{r['hyp']}」")
    drops = [r for r in rows if r["dropped"]]
    if drops:
        print("\n⚠️ 헛문장으로 버린 아이 말:")
        for r in drops:
            print(f"- [{r['engine'][8:]}] 「{r['ref_heard']}」 → 「{r['hyp']}」 ({r['dropped']})")


if __name__ == "__main__":
    main()
