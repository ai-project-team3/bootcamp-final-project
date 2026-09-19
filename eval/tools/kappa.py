"""라벨 일치도(Cohen's κ)와 층별 일치율을 계산한다 (역할 1 · 안치영).

아래에서 **지시서** = 노션 「오늘 모델 검증 — 4명 실행 지시서」.
칸 이름과 판정 필드의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` 다.

쓰임새 두 가지
  1) 사람끼리:   python tools/kappa.py labels_진웅.jsonl labels_치영_검수20.jsonl
     → 두 사람이 따로 붙인 라벨이 얼마나 일치하나. 낮으면 판정 규칙이 아니라 **라벨 기준**이 모호한 것이다.
     → 불일치 문항이 3개를 넘으면 모델 탓하기 전에 기준표(지시서 §2-2)부터 고친다.
  2) 판정 대 사람: python tools/kappa.py fixtures_judge.jsonl 판정결과.jsonl
     → 확정된 정답(gold) 대비 규칙 · LLM 판정의 일치도. (모델 비교표의 F1 은 최민우의 score.py 가 낸다)

입력 형식: 두 파일 모두 한 줄에 {"id": "j001", "gold": {...지시서 §2-3 의 gold...}} 형태.
        (판정 결과 파일은 "gold" 대신 "pred" 키를 써도 된다)

κ 해석 (참고): .41~.60 보통 · .61~.80 상당한 일치 · .81~ 거의 완전한 일치.
조사2에서 정한 첫 목표는 **κ .61 이상**이다.
"""

import json
import sys
from pathlib import Path

# 값이 정해진 라벨 — 예/아니오와 칸 이름. 이것들이 하나라도 다르면 "불일치 문항"으로 센다
BOOLS = ["s1_reason", "s2_addition", "contradiction", "unclear", "story_ready"]
SLOTS = ["slot_1", "slot_2", "no_longer_needed"]
MAX_DISAGREE = 3  # 지시서 §2-2 — 넘으면 기준표부터 고친다


def load(path: Path) -> dict[str, dict]:
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        labels = row.get("gold") or row.get("pred") or {}
        stratum = row.get("stratum") or str(row.get("type", "")).split("-")[0]
        out[row["id"]] = {"labels": labels, "stratum": stratum}
    return out


def value(labels: dict, key: str):
    """빠진 칸은 없음으로 본다 — 예/아니오는 False, 칸 이름은 None."""
    v = labels.get(key)
    return bool(v) if key in BOOLS else (v or None)


def kappa(a: list, b: list) -> float:
    """두 사람(또는 사람과 기계)이 얼마나 일치해서 붙였나. 값이 둘이든 여럿이든 같은 식이다."""
    n = len(a)
    if n == 0:
        return float("nan")
    agree = sum(1 for x, y in zip(a, b) if x == y) / n
    chance = sum((a.count(c) / n) * (b.count(c) / n) for c in set(a) | set(b))  # 우연히 맞을 확률
    if chance >= 1.0:
        return float("nan")                       # 양쪽이 전부 같은 값 → κ를 정의할 수 없다
    return (agree - chance) / (1 - chance)


def next_slot_agrees(x: dict, y: dict) -> bool | None:
    """next_slot 은 정답이 하나가 아니다(지시서 §2-4). 허용 집합끼리는 겹치면, 예측은 집합에 들면 맞다."""
    sets = []
    for lab in (x, y):
        if "next_slot_ok" in lab:
            sets.append(set(lab["next_slot_ok"] or []))
        elif "next_slot" in lab:
            sets.append({lab["next_slot"]} if lab["next_slot"] else set())
        else:
            return None
    return sets[0] == sets[1] if not (sets[0] and sets[1]) else bool(sets[0] & sets[1])


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        print(__doc__)
        sys.exit(1)
    first, second = load(Path(args[0])), load(Path(args[1]))
    ids = [i for i in first if i in second and first[i]["labels"] and second[i]["labels"]]
    missing = max(len(first), len(second)) - len(ids)
    print(f"공통 문항 {len(ids)}개" + (f" (한쪽에만 있거나 라벨이 빈 문항 {missing}개는 뺐다)" if missing else ""))
    if not ids:
        sys.exit(1)

    print("\n라벨별")
    print(f"{'라벨':18s} {'일치율':>7s} {'κ':>8s}  {'A 있음':>6s} {'B 있음':>6s}")
    for lab in BOOLS + SLOTS:
        a = [value(first[i]["labels"], lab) for i in ids]
        b = [value(second[i]["labels"], lab) for i in ids]
        agree = sum(1 for x, y in zip(a, b) if x == y) / len(ids)
        k = kappa(a, b)
        ks = "정의불가" if k != k else f"{k:8.3f}"
        print(f"{lab:18s} {agree:7.1%} {ks}  {sum(1 for x in a if x):6d} {sum(1 for x in b if x):6d}")

    ns = [r for r in (next_slot_agrees(first[i]["labels"], second[i]["labels"]) for i in ids) if r is not None]
    if ns:
        print(f"{'next_slot(겹침)':18s} {sum(ns) / len(ns):7.1%}   — 허용 집합이 하나라도 겹치면 일치")

    def diff_of(i: str) -> list[str]:
        return [l for l in BOOLS + SLOTS
                if value(first[i]["labels"], l) != value(second[i]["labels"], l)]

    strata = sorted({first[i]["stratum"] for i in ids if first[i]["stratum"]})
    if strata:
        print("\n층별 (위 라벨을 모두 똑같이 붙인 문항의 비율)")
        for st in strata:
            sub = [i for i in ids if first[i]["stratum"] == st]
            same = sum(1 for i in sub if not diff_of(i))
            print(f"  {st:8s} {same:3d}/{len(sub):3d}  {same / len(sub):6.1%}")

    bad = [i for i in ids if diff_of(i)]
    print(f"\n불일치 문항 {len(bad)}개 / {len(ids)}개 — 이 문항들로 라벨 기준을 좁힌다")
    for i in bad[:10]:
        print(f"  {i}: {', '.join(diff_of(i))}")
    if len(bad) > MAX_DISAGREE:
        print(f"\n⚠️ 불일치가 {MAX_DISAGREE}개를 넘었다 — 모델 탓하기 전에 기준표(지시서 §2-2)부터 고친다")


if __name__ == "__main__":
    main()
