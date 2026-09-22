"""W1 음성 인식 비교 채점 스크립트.

입력: CSV (UTF-8) — 한 줄이 클립 하나 × 후보 하나
  clip_id, engine, condition, is_silence, ref_heard, ref_meant, target_words, hyp, latency_ms
  ref_speaker, hyp_speaker   ← 화자 분리를 잴 때만. 없으면 그 표를 안 낸다

  engine        whisper_turbo | clova 등
  condition     예) short_quiet · short_noise · long_quiet · long_noise · silence
  is_silence    1 이면 말소리 없는 클립 (ref 는 비워 둠)
  ref_heard     들린 대로 전사 ("공뇽 나라")
  ref_meant     뜻한 대로 전사 ("공룡 나라")
  target_words  슬롯에 들어가야 할 낱말, | 로 구분 ("공룡|나라")
  hyp           엔진 출력
  latency_ms    말 끝 → 텍스트 도착 시간
  ref_speaker   사람이 들은 화자 — child | adult
  hyp_speaker   엔진·앱이 판정한 화자 — child | adult | unsure

출력
  ① 엔진 × 조건별 CER(들린/뜻한), 뜻한 낱말 정답률, 무음 헛출력 비율, 지연 p50/p95
  ② 화자 분리 (ref_speaker 열이 있을 때만) — 아래 §화자 분리

사용: python stt_eval.py results.csv

──────────────────────────────────────────────────────────────────────
화자 분리 — 부모 협업 모드의 급소 (`부모협업모드_설계.md` §9-2)

협업 모드에서는 **어른이 매 턴 말한다.** 부모 말이 `child` 로 들어가면
**수준 판단과 부모 리포트 인용이 통째로 오염된다**(`guidelines/2` §1-4 출처 `by` 3종).
동화 모드는 어른이 가끔 말하니 새어도 티가 안 나지만 협업 모드는 매 턴이다.

그래서 **정확도 한 숫자로 보지 않고 방향을 나눠 본다.** 두 오류의 값이 다르다.

| 재는 것 | 뜻 | 왜 중요한가 |
|---|---|---|
| **adult → child 오염률** | 부모 말을 아이 말로 봤다 | **가장 위험하다.** 리포트가 거짓말을 시작한다 |
| child → adult 누락률 | 아이 말을 부모 말로 봤다 | 주고받기 횟수가 낮게 나온다. 아깝지만 거짓은 아니다 |
| unsure 비율 | 판정을 못 했다 | `unsure` 를 버리면 누락으로, 살리면 오염 위험으로 바뀐다 |

`unsure` 를 버릴지는 **adult→child 오염률과 child→adult 누락률을 견줘서** 정한다.
9/23 기본값은 「버린다」다 — 오염이 누락보다 비싸기 때문이다.
"""
import csv
import sys
import unicodedata
from collections import defaultdict


def normalize(text: str) -> str:
    """공백·문장부호를 지우고 음절만 남긴다 (CER 은 음절 단위)."""
    text = unicodedata.normalize("NFC", text or "")
    return "".join(ch for ch in text if ch.isalnum())


def edit_distance(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def percentile(values, q):
    if not values:
        return None
    ordered = sorted(values)
    k = (len(ordered) - 1) * q
    lo, hi = int(k), min(int(k) + 1, len(ordered) - 1)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo)


SPEAKERS = ("child", "adult")


def speaker_table(confusion: dict) -> None:
    """화자 분리 결과. 정확도 한 숫자로 뭉치지 않고 **오류 방향을 나눠** 낸다."""
    if not confusion:
        return
    print()
    print("화자 분리 (ref_speaker 열이 있는 줄만)")
    header = ["engine", "condition", "clips", "정확도",
              "adult→child(오염)", "child→adult(누락)", "unsure_child", "unsure_adult"]
    print("\t".join(header))

    def rate(num, den):
        return f"{num / den * 100:.1f}%" if den else "-"

    for (engine, condition), c in sorted(confusion.items()):
        n_child = sum(c[("child", h)] for h in ("child", "adult", "unsure"))
        n_adult = sum(c[("adult", h)] for h in ("child", "adult", "unsure"))
        correct = c[("child", "child")] + c[("adult", "adult")]
        print("\t".join([
            engine, condition, str(n_child + n_adult),
            rate(correct, n_child + n_adult),
            rate(c[("adult", "child")], n_adult),     # 부모 말이 아이 말로 새는 비율
            rate(c[("child", "adult")], n_child),     # 아이 말이 부모 말로 새는 비율
            rate(c[("child", "unsure")], n_child),
            rate(c[("adult", "unsure")], n_adult),
        ]))
    print()
    print("⚠️ adult→child 가 이 모드의 급소다 — 부모 말이 아이 말로 기록되면")
    print("   수준 판단과 리포트 인용이 오염된다. child→adult 는 아까운 누락이지 거짓은 아니다.")
    print("   unsure 를 버릴지는 이 두 값을 견줘서 정한다 (부모협업모드_설계.md §9-2).")


def main(path: str) -> None:
    groups = defaultdict(lambda: {
        "err_heard": 0, "len_heard": 0, "err_meant": 0, "len_meant": 0,
        "targets": 0, "targets_hit": 0, "silence": 0, "silence_output": 0,
        "latency": [], "clips": 0,
    })
    confusion = defaultdict(lambda: defaultdict(int))
    with open(path, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            ref_sp = (row.get("ref_speaker") or "").strip().lower()
            hyp_sp = (row.get("hyp_speaker") or "").strip().lower() or "unsure"
            if ref_sp in SPEAKERS:
                if hyp_sp not in SPEAKERS:
                    hyp_sp = "unsure"          # 빈칸·미지의 값은 판정 못 한 것으로 본다
                for key in ((row["engine"], row["condition"]), (row["engine"], "ALL")):
                    confusion[key][(ref_sp, hyp_sp)] += 1
            for key in ((row["engine"], row["condition"]), (row["engine"], "ALL")):
                g = groups[key]
                g["clips"] += 1
                hyp = normalize(row["hyp"])
                if row["latency_ms"].strip():
                    g["latency"].append(float(row["latency_ms"]))
                if row["is_silence"].strip() == "1":
                    g["silence"] += 1
                    g["silence_output"] += bool(hyp)
                    continue
                for ref_key, err_key, len_key in (("ref_heard", "err_heard", "len_heard"),
                                                  ("ref_meant", "err_meant", "len_meant")):
                    ref = normalize(row[ref_key])
                    g[err_key] += edit_distance(ref, hyp)
                    g[len_key] += len(ref)
                for word in filter(None, (w.strip() for w in row["target_words"].split("|"))):
                    g["targets"] += 1
                    g["targets_hit"] += normalize(word) in hyp

    def rate(num, den):
        return f"{num / den * 100:.1f}%" if den else "-"

    header = ["engine", "condition", "clips", "CER_heard", "CER_meant",
              "target_hit", "silence_hallucination", "latency_p50", "latency_p95"]
    print("\t".join(header))
    for (engine, condition), g in sorted(groups.items()):
        p50, p95 = percentile(g["latency"], 0.5), percentile(g["latency"], 0.95)
        print("\t".join([
            engine, condition, str(g["clips"]),
            rate(g["err_heard"], g["len_heard"]),
            rate(g["err_meant"], g["len_meant"]),
            rate(g["targets_hit"], g["targets"]),
            rate(g["silence_output"], g["silence"]),
            f"{p50:.0f}ms" if p50 is not None else "-",
            f"{p95:.0f}ms" if p95 is not None else "-",
        ]))

    speaker_table(confusion)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
