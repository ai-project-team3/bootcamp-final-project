# -*- coding: utf-8 -*-
"""원래 `large-v3` 와 학습한 모델을 **같은 평가 세트 · 같은 서버 설정**으로 재고, README 의 채택 기준으로 가른다.

서버 설정 그대로: faster-whisper · `int8_float16` · beam 5 · language ko · vad 끔(폰이 이미 자른다) ·
헛문장 거르기 `backend/app/filters/hallucination.py`.

    py eval/whisper_ft/evaluate.py --data D:/whisper_ft/data --tuned D:/whisper_ft/ct2

재는 것
  아이  — prepare.py 가 뗀 평가 화자(학습에 안 쓴 아이). 나이별 CER · 어절 보존 · 헛문장으로 버림 · 지연
  어른  — eval/audio/bias (어른 · 강의실 소음 · 짧은 답 「응」 포함). 아이로만 학습해 어른이 나빠지지 않는지
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import re
import sys
import time
import unicodedata
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]           # eval/
TAG = re.compile(r"\([A-Z]+:[^)]*\)")

# ── 채택 기준 (10-06 조장 · 데이터를 보기 **전에** 고정) — README 와 같은 숫자. 바꾸려면 README 를 먼저 고친다 ──
YOUNG = (3, 4, 5)
MIN_GAIN_YOUNG = 10.0        # 3~5세 어절 보존 +10%p 이상, 그리고 화자 부트스트랩 95% 구간 아래끝 > 0
MAX_LOSS_SIX_CER = 1.0       # 6세 CER 이 1%p 넘게 나빠지면 탈락
MAX_ADULT_WORSE = 1          # 어른 세트에서 맞히던 것을 2개 이상 놓치면 탈락
MAX_DROP_RISE = 0.5          # 헛문장으로 버린 아이 말 비율이 0.5%p 넘게 늘면 탈락
MAX_LATENCY_RISE_MS = 100    # 아이 세트 지연 p50 이 100ms 넘게 늘면 탈락


def norm(t: str) -> str:
    return "".join(ch for ch in unicodedata.normalize("NFC", t or "") if ch.isalnum())


def edit(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def words_kept(ref: str, hyp: str) -> tuple[int, int]:
    rw = ref.split()
    hw = re.sub(r"[.,?!~]", "", hyp).split()
    return sum(1 for w in rw if w in hw), len(rw)


def p50(xs: list[float]) -> float:
    s = sorted(xs)
    return s[len(s) // 2] if s else 0.0


def load_model(name: str):
    sys.path.insert(0, str(ROOT))
    # Windows 에서 cuBLAS 를 PATH 에 올린다 — `stt_bias_bench.Whisper` 와 같은 길(nvidia 휠 → 없으면 torch/lib)
    ok = False
    try:
        from bench_coresident import add_cuda_dlls
        ok = add_cuda_dlls()
    except Exception:
        pass
    if not ok and sys.platform == "win32":
        import importlib.util
        import os
        spec = importlib.util.find_spec("torch")
        if spec and spec.origin:
            lib = str(Path(spec.origin).parent / "lib")
            os.add_dll_directory(lib)
            os.environ["PATH"] = lib + os.pathsep + os.environ.get("PATH", "")
    from faster_whisper import WhisperModel
    return WhisperModel(name, device="cuda", compute_type="int8_float16")


def transcribe(m, wav: Path) -> tuple[str, float]:
    t0 = time.perf_counter()
    segs, _ = m.transcribe(str(wav), language="ko", beam_size=5, vad_filter=False)
    text = "".join(s.text for s in segs).strip()
    return text, (time.perf_counter() - t0) * 1000


def child_clips(data: Path) -> list[dict]:
    rows = []
    for j in sorted((data / "eval").rglob("*.json")):
        d = json.loads(j.read_text(encoding="utf-8"))
        u, r = d["발화정보"], d["녹음자정보"]
        ref = re.sub(r"\s+", " ", TAG.sub(" ", u["stt"])).strip()   # stt_child_bench.py 와 같은 정답
        wav = j.with_suffix(".wav")
        if wav.exists() and ref:
            rows.append({"set": "child", "id": j.stem, "wav": wav, "ref": ref, "age": int(r["age"]), "speaker": r["recorderId"]})
    return rows


def adult_clips(base: Path) -> list[dict]:
    man = base / "manifest.csv"
    if not man.exists():
        return []
    rows = []
    with open(man, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            wav = base / f"{r['clip_id']}.wav"
            if wav.exists():
                rows.append({"set": "adult", "id": r["clip_id"], "wav": wav, "ref": r["ref"], "age": 0, "speaker": "adult", "group": r["group"]})
    return rows


def bootstrap_gain(by_spk_base: dict, by_spk_tuned: dict, n: int = 2000, seed: int = 7) -> tuple[float, float]:
    """화자 단위로 다시 뽑아 어절 보존 차(%p)의 95% 구간 — 한 아이의 클립끼리는 독립이 아니다"""
    spk = sorted(by_spk_base)
    rng = random.Random(seed)
    gains = []
    for _ in range(n):
        pick = [rng.choice(spk) for _ in spk]
        kb = sum(by_spk_base[s][0] for s in pick); nb = sum(by_spk_base[s][1] for s in pick)
        kt = sum(by_spk_tuned[s][0] for s in pick); nt = sum(by_spk_tuned[s][1] for s in pick)
        gains.append((kt / max(nt, 1) - kb / max(nb, 1)) * 100)
    gains.sort()
    return gains[int(n * 0.025)], gains[int(n * 0.975)]


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--tuned", required=True, help="merge_convert.py 의 --out")
    ap.add_argument("--base", default="large-v3")
    ap.add_argument("--adult", default=str(ROOT / "audio" / "bias"), help="어른 녹음 폴더(깃 밖 · 데이터와 같이 옮긴다)")
    a = ap.parse_args()
    sys.path.insert(0, str(ROOT.parent / "backend"))
    from app.filters.hallucination import check_transcript

    clips = child_clips(Path(a.data)) + adult_clips(Path(a.adult))
    print(f"아이 {sum(c['set'] == 'child' for c in clips)} · 어른 {sum(c['set'] == 'adult' for c in clips)} 클립")
    (ROOT / "raw").mkdir(exist_ok=True)
    out = ROOT / "raw" / f"whisper_ft_{date.today():%Y%m%d}.csv"
    res: dict[str, list[dict]] = {}
    with open(out, "w", encoding="utf-8-sig", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=["engine", "set", "id", "age", "speaker", "ref", "hyp", "latency_ms", "dropped"])
        wr.writeheader()
        for engine, name in (("base", a.base), ("tuned", a.tuned)):
            m = load_model(name)
            transcribe(m, clips[0]["wav"])                  # 첫 호출은 데우기 — 지연에 넣지 않는다
            rows = []
            for i, c in enumerate(clips, 1):
                hyp, ms = transcribe(m, c["wav"])
                v = check_transcript(hyp)
                row = {"engine": engine, "set": c["set"], "id": c["id"], "age": c["age"], "speaker": c["speaker"],
                       "ref": c["ref"], "hyp": hyp, "latency_ms": f"{ms:.0f}", "dropped": "" if v.keep else v.reason}
                wr.writerow(row); rows.append(row)
                if i % 200 == 0:
                    print(f"\r{engine} {i}/{len(clips)}", end="")
            print()
            res[engine] = rows
            del m
    print(f"→ {out}\n")

    def stats(rows, ages):
        rs = [r for r in rows if r["set"] == "child" and r["age"] in ages]
        err = sum(edit(norm(r["ref"]), norm(r["hyp"])) for r in rs); ln = sum(len(norm(r["ref"])) for r in rs)
        kept = [words_kept(r["ref"], r["hyp"]) for r in rs]
        by = {}
        for r, (k, n) in zip(rs, kept):
            b = by.setdefault(r["speaker"], [0, 0]); b[0] += k; b[1] += n
        return {"n": len(rs), "cer": err / max(ln, 1) * 100, "kept": sum(k for k, _ in kept) / max(sum(n for _, n in kept), 1) * 100,
                "drop": sum(bool(r["dropped"]) for r in rs) / max(len(rs), 1) * 100,
                "p50": p50([float(r["latency_ms"]) for r in rs]), "by": by}

    print("| 나이 | 클립 | CER 원래 → 학습 | 어절 보존 원래 → 학습 | 헛문장 버림 | 지연 p50 |")
    print("|---|---:|---:|---:|---:|---:|")
    for ages, label in [((3,), "3세"), ((4,), "4세"), ((5,), "5세"), ((6,), "6세"), (YOUNG, "3~5세"), ((3, 4, 5, 6), "전체")]:
        b, t = stats(res["base"], ages), stats(res["tuned"], ages)
        print(f"| {label} | {b['n']} | {b['cer']:.1f}% → **{t['cer']:.1f}%** | {b['kept']:.1f}% → **{t['kept']:.1f}%** | "
              f"{b['drop']:.1f}% → {t['drop']:.1f}% | {b['p50']:.0f} → {t['p50']:.0f}ms |")

    def adult_ok(rows):
        return {r["id"]: norm(r["ref"]) in norm(r["hyp"]) for r in rows if r["set"] == "adult"}
    ab, at = adult_ok(res["base"]), adult_ok(res["tuned"])
    lost = [k for k in ab if ab[k] and not at.get(k)]
    if ab:
        print(f"\n어른 세트: 맞힘 {sum(ab.values())}/{len(ab)} → {sum(at.values())}/{len(at)} · 원래는 맞히다 놓친 것 {len(lost)}: {lost[:8]}")

    # ── 판정 ──
    yb, yt = stats(res["base"], YOUNG), stats(res["tuned"], YOUNG)
    sb, st = stats(res["base"], (6,)), stats(res["tuned"], (6,))
    ab_all, at_all = stats(res["base"], (3, 4, 5, 6)), stats(res["tuned"], (3, 4, 5, 6))
    lo, hi = bootstrap_gain(yb["by"], yt["by"])
    gain = yt["kept"] - yb["kept"]
    checks = [
        (f"3~5세 어절 보존 +{MIN_GAIN_YOUNG:.0f}%p 이상 · 95% 구간 아래끝 > 0", gain >= MIN_GAIN_YOUNG and lo > 0, f"{gain:+.1f}%p [{lo:+.1f}, {hi:+.1f}]"),
        (f"6세 CER 악화 ≤ {MAX_LOSS_SIX_CER}%p", st["cer"] - sb["cer"] <= MAX_LOSS_SIX_CER, f"{st['cer'] - sb['cer']:+.1f}%p"),
        # 어른 녹음(조장 목소리)은 깃 밖이라 남는 PC 에는 없다 — 그때는 보류로 두고 조장 PC 에서 마저 잰다
        (f"어른 세트에서 놓친 것 ≤ {MAX_ADULT_WORSE}", None if not ab else len(lost) <= MAX_ADULT_WORSE,
         "어른 녹음 없음 — 조장 PC 에서 따로 확인" if not ab else f"{len(lost)}개"),
        (f"헛문장 버림 증가 ≤ {MAX_DROP_RISE}%p", at_all["drop"] - ab_all["drop"] <= MAX_DROP_RISE, f"{at_all['drop'] - ab_all['drop']:+.1f}%p"),
        (f"지연 p50 증가 ≤ {MAX_LATENCY_RISE_MS}ms", at_all["p50"] - ab_all["p50"] <= MAX_LATENCY_RISE_MS, f"{at_all['p50'] - ab_all['p50']:+.0f}ms"),
    ]
    print("\n**채택 기준** (README · 10-06 고정)")
    for name, ok, val in checks:
        print(f"- {'⏸' if ok is None else '✅' if ok else '❌'} {name} — {val}")
    if any(ok is False for _, ok, _ in checks):
        verdict = "탈락 — 서버는 그대로 둔다"
    elif any(ok is None for _, ok, _ in checks):
        verdict = "나머지 기준 통과 · 어른 확인 보류 — 조장이 어른 녹음으로 마저 확인한 뒤 정한다"
    else:
        verdict = "채택 — 서버 STT_MODEL 을 바꿔도 된다"
    print(f"\n**판정: {verdict}**")


if __name__ == "__main__":
    main()
