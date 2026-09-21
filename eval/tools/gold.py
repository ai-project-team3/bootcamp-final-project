"""확정된 gold 를 픽스처에서 **빼내고 되돌려 넣는다** (역할 1 · 안치영).

왜 필요한가
  `make_fixtures.py` 는 픽스처를 **`gold: null` 로 다시 쓴다.** 앱 질문이 바뀌어 평가셋을 다시 뽑으면
  확정된 라벨 100개가 그 자리에서 사라진다. README·지침에 경고만 있고 **되살릴 방법이 없었다.**
  이 도구가 그 구멍을 막는다.

  python tools/gold.py --export --by 진웅         # 픽스처 → labels_진웅_전체100.jsonl
  python tools/make_fixtures.py --dump <경로>     # 평가셋 다시 뽑기 (gold 가 비워진다)
  python tools/gold.py --apply --by 진웅          # 라벨 되돌려 넣기

⚠️ **`id` 로 짝을 맞추지 않는다.** 다시 뽑으면 문항 순서가 바뀌어 `id` 가 다른 내용에 붙을 수 있다.
   **`asked` + `utterance` + `context`** 로 지문을 만들어 맞춘다. 못 맞춘 것은 조용히 넘기지 않고 보고한다.

아래에서 **지시서** = 노션 「오늘 모델 검증 — 4명 실행 지시서」.
칸 이름과 판정 필드의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` 다.
"""

import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
FIXTURES = HERE / "fixtures_judge.jsonl"


def fingerprint(item: dict) -> tuple[str, str, str]:
    """문항의 지문. **`id` 가 아니라 내용으로** 짝을 맞춘다."""
    return (item.get("asked", ""), item.get("utterance", ""), item.get("context", ""))


def load_fixtures() -> list[dict]:
    return [json.loads(l) for l in FIXTURES.read_text(encoding="utf-8").splitlines() if l.strip()]


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n",
                    encoding="utf-8", newline="\n")   # OS 상관없이 LF


def do_export(items: list[dict], out_path: Path, by: str) -> None:
    filled = [it for it in items if it.get("gold")]
    if not filled:
        raise SystemExit(f"{FIXTURES.name} 의 gold 가 전부 비어 있다 — 빼낼 것이 없다")

    rows = [{
        "id": it["id"],
        "type": it["type"],
        "stratum": it["type"].split("-")[0],
        "gold": it["gold"],
        "by": by,
        # 다시 뽑은 뒤 되돌려 넣을 때 쓰는 지문. id 는 믿지 않는다
        "match": {"asked": it["asked"], "utterance": it["utterance"], "context": it["context"]},
    } for it in filled]
    write_jsonl(out_path, rows)

    print(f"빼냈습니다: {out_path.name} · {len(rows)}문항 / 픽스처 {len(items)}문항")
    if len(rows) < len(items):
        print(f"  ⚠️ gold 가 빈 문항 {len(items) - len(rows)}개는 담지 않았다")
    print("  이제 픽스처를 다시 뽑아도 된다. 되돌릴 때는 --apply")


def do_apply(items: list[dict], labels_path: Path) -> None:
    if not labels_path.exists():
        raise SystemExit(f"{labels_path.name} 이 없다 — 먼저 --export 로 빼낸다")
    labels = [json.loads(l) for l in labels_path.read_text(encoding="utf-8").splitlines() if l.strip()]

    by_fp: dict[tuple, dict] = {}
    no_match_info = [r for r in labels if not r.get("match")]
    for r in labels:
        m = r.get("match")
        if m:
            by_fp[(m["asked"], m["utterance"], m["context"])] = r

    applied, overwritten, unmatched = 0, 0, []
    for it in items:
        row = by_fp.pop(fingerprint(it), None)
        if not row:
            if it.get("gold"):
                unmatched.append(it["id"])   # 이미 있던 gold 는 건드리지 않는다
            continue
        if it.get("gold") and it["gold"] != row["gold"]:
            overwritten += 1
        it["gold"] = row["gold"]
        applied += 1

    write_jsonl(FIXTURES, items)
    print(f"되돌렸습니다: {FIXTURES.name} · {applied}문항에 gold 를 넣었다")
    if overwritten:
        print(f"  ⚠️ 그중 {overwritten}개는 **다른 값이 이미 있었는데 덮어썼다**")
    if by_fp:
        print(f"  ⚠️ 픽스처에서 짝을 못 찾은 라벨 {len(by_fp)}개 — 문항이 바뀌었거나 빠졌다:")
        for (asked, utt, _), r in list(by_fp.items())[:10]:
            print(f"       {r['id']} [{r['type']}] asked={asked} · {utt[:28]}")
    if unmatched:
        print(f"  gold 가 이미 있고 라벨에 없던 문항 {len(unmatched)}개는 그대로 뒀다: {' '.join(unmatched[:10])}")
    if no_match_info:
        print(f"  ⚠️ 라벨 {len(no_match_info)}개에 match 지문이 없다 — 옛 형식이라 짝을 못 맞춘다")
    if not by_fp and not no_match_info:
        print("  전부 짝이 맞았다 ✅")


def main() -> None:
    p = argparse.ArgumentParser(description="확정된 gold 를 픽스처에서 빼내고 되돌려 넣는다")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--export", action="store_true", help="픽스처의 gold → 라벨 파일")
    g.add_argument("--apply", action="store_true", help="라벨 파일 → 픽스처의 gold")
    p.add_argument("--by", default="진웅", help="라벨을 붙인 사람 (파일 이름에 들어간다)")
    p.add_argument("--labels", default="", help="라벨 파일 이름 (기본: labels_<이름>_전체100.jsonl)")
    args = p.parse_args()

    items = load_fixtures()
    stem = args.labels or f"labels_{args.by}_전체100"
    path = HERE / (stem if stem.endswith(".jsonl") else stem + ".jsonl")

    if args.export:
        do_export(items, path, args.by)
    else:
        do_apply(items, path)


if __name__ == "__main__":
    main()
