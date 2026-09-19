"""라벨 붙이기 도구 — 문항을 하나씩 보여 주고 gold 를 받는다.

아래에서 **지시서** = 노션 「오늘 모델 검증 — 4명 실행 지시서」.
칸 이름과 판정 필드의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` 다.

  # 박진웅: 100개 전부
  python tools/label.py --out labels_진웅.jsonl --by 진웅

  # 안치영: 검수용 20개만 (지시서 §2 — 박진웅 것과 대조)
  python tools/label.py --out labels_치영_검수20.jsonl --sample 20 --by 치영

  # 이어서 하기 — 이미 붙인 문항은 건너뛴다
  python tools/label.py --out labels_진웅.jsonl --resume

입력하는 값
  예/아니오 : y = true · n 또는 엔터 = false
  칸 고르기 : 번호나 이름 · 엔터 = 없음(null)
  글        : 그대로 적는다 · 엔터 = 없음(null)
  어디서든  : b = 앞 문항으로 · s = 건너뛰기 · q = 저장하고 끝

⚠️ 검수하는 사람은 **박진웅의 라벨을 보지 않은 상태에서** 붙여야 대조가 의미 있다.
   이 도구는 다른 사람의 라벨을 절대 보여 주지 않는다.
"""

import argparse
import json
import random
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
FIXTURES = HERE / "fixtures_judge.jsonl"

# 판정 스키마의 고정 enum (지시서 §1-1)
ENUM = ["place", "problem", "reaction", "cause", "newcomer", "name",
        "companion", "sound", "adult", "solution", "title", "extra"]

# 검수 표본 20개의 층별 몫 — 사람마다 갈리는 곳(`-서` · 다중채움 · 필수해제)을 두껍게 본다
SAMPLE_QUOTA = {"S1-인과서": 3, "S1-순차함정": 2, "S1": 2, "S2-다중채움": 3, "S2": 2,
                "필수해제": 4, "정답형": 1, "무관": 1, "미완결": 2}

CONTROL = ("b", "s", "q")


def ask_bool(prompt: str) -> str | bool:
    while True:
        v = input(f"    {prompt} [y/n] ").strip().lower()
        if v in ("y", "yes"):
            return True
        if v in ("n", "no", ""):
            return False
        if v in CONTROL:
            return v
        print("    y · n · 엔터(n) · b(뒤로) · s(건너뛰기) · q(끝내기) 중에서")


def parse_slot(v: str) -> str | None:
    if v.isdigit() and 1 <= int(v) <= len(ENUM):
        return ENUM[int(v) - 1]
    return v if v in ENUM else None


def ask_slot(prompt: str) -> str | None:
    """칸 하나. 엔터면 None."""
    while True:
        v = input(f"    {prompt} [번호/이름/엔터=없음] ").strip().lower()
        if v == "" or v in CONTROL:
            return v or None
        slot = parse_slot(v)
        if slot:
            return slot
        print("    " + " ".join(f"{i}.{s}" for i, s in enumerate(ENUM, 1)))


def ask_slots(prompt: str) -> str | list[str]:
    """칸 여럿 (next_slot_ok). 쉼표나 띄어쓰기로 나눈다."""
    while True:
        v = input(f"    {prompt} [번호/이름 여러 개] ").strip().lower()
        if v in CONTROL:
            return v
        got = [parse_slot(t) for t in v.replace(",", " ").split()]
        if all(got):
            return got  # 빈 목록도 된다 — story_ready 면 물을 칸이 없다
        print("    " + " ".join(f"{i}.{s}" for i, s in enumerate(ENUM, 1)))


def ask_text(prompt: str) -> str | None:
    v = input(f"    {prompt} [글/엔터=없음] ").strip()
    return v or None


def label_one() -> str | dict:
    """한 문항의 gold. 'b' · 's' · 'q' 면 그 글자를 돌려준다. (문항은 show() 가 이미 보여 줬다)"""
    g: dict = {}

    def put(key: str, v) -> bool:
        if isinstance(v, str) and v in CONTROL:
            g["_control"] = v
            return False
        g[key] = v
        return True

    steps = [
        ("slot_1", lambda: ask_slot("slot_1 — 이 말이 채우는 칸 (엉뚱한 말 · 필수해제면 없음)")),
        ("value_1", lambda: ask_text("value_1 — 그 칸에 들어갈 값") if g.get("slot_1") else None),
        ("slot_2", lambda: ask_slot("slot_2 — 묻지 않았는데 함께 채운 둘째 칸") if g.get("slot_1") else None),
        ("value_2", lambda: ask_text("value_2 — 둘째 칸의 값") if g.get("slot_2") else None),
        ("s1_reason", lambda: ask_bool("s1_reason — 까닭을 말했는가? (순차의 '-서'는 n — '가서 먹었어')")),
        ("s2_addition", lambda: ask_bool("s2_addition — 묻지 않은 것을 스스로 더 말했는가?")),
        ("emotion", lambda: ask_text("emotion — 마음을 말했으면 그 말 ('무섭다')")),
        ("contradiction", lambda: ask_bool("contradiction — 앞에서 정한 칸과 어긋나는가? (앞 내용이 없으면 n)")),
        ("unclear", lambda: ask_bool("unclear — 뜻을 알 수 없어 되물어야 하는가? ('공뇽…')")),
        ("no_longer_needed", lambda: ask_slot("no_longer_needed — 이 말로 필요 없어진 칸 ('혼자 갔어' → companion)")),
        ("next_slot_ok", lambda: ask_slots("next_slot_ok — 다음에 물어도 되는 칸 **전부**")),
        ("story_ready", lambda: ask_bool("story_ready — 이제 이야기를 만들어도 되는가?")),
    ]
    for key, fn in steps:
        if not put(key, fn()):
            return g["_control"]
    return g


def sample_for_review(items: list[dict], n: int) -> list[dict]:
    """검수 표본. 씨앗을 고정한다 — 다시 돌려도 같은 문항이 나온다."""
    rnd = random.Random(20260918)
    quota = SAMPLE_QUOTA if n == sum(SAMPLE_QUOTA.values()) else None
    if not quota:
        return sorted(rnd.sample(items, n), key=lambda it: it["id"])
    out: list[dict] = []
    for type_, k in quota.items():
        pool = [it for it in items if it["type"] == type_]
        out += rnd.sample(pool, min(k, len(pool)))
    return sorted(out, key=lambda it: it["id"])


def show(it: dict, i: int, total: int) -> None:
    filled = {k: v for k, v in it["slots"].items() if v is not None}
    empty = [k for k, v in it["slots"].items() if v is None]
    print(f"\n[{i + 1}/{total}]  {it['id']}   템플릿 {it['template']}")
    print("    찬 칸 : " + (" · ".join(f"{k}={v}" for k, v in filled.items()) or "(없음)"))
    print("    빈 칸 : " + " · ".join(empty))
    print(f"    질문 : {it['context']}   ← 묻는 칸 \033[1m{it['asked']}\033[0m")
    print(f"    아이 : \033[1m{it['utterance']}\033[0m")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True, help="라벨을 저장할 파일 이름")
    p.add_argument("--sample", type=int, default=0, help="이 개수만 (검수용 · 20이면 층별 몫대로)")
    p.add_argument("--resume", action="store_true", help="이미 붙인 문항은 건너뛴다")
    p.add_argument("--by", default="", help="붙인 사람 이름")
    args = p.parse_args()

    items = [json.loads(l) for l in FIXTURES.read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.sample:
        items = sample_for_review(items, args.sample)

    out_path = HERE / args.out
    done: dict[str, dict] = {}
    if args.resume and out_path.exists():
        for line in out_path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                row = json.loads(line)
                done[row["id"]] = row
        print(f"이어서 합니다 — 이미 붙인 문항 {len(done)}개는 건너뜁니다\n")

    print("칸 번호: " + " ".join(f"{i}.{s}" for i, s in enumerate(ENUM, 1)))
    todo = [it for it in items if it["id"] not in done]
    i = 0
    while i < len(todo):
        it = todo[i]
        show(it, i, len(todo))
        got = label_one()
        if got == "q":
            break
        if got == "b":
            i = max(0, i - 1)
            done.pop(todo[i]["id"], None)
            continue
        if got != "s":
            done[it["id"]] = {
                "id": it["id"],
                "type": it["type"],
                "stratum": it["type"].split("-")[0],
                "gold": got,
                "by": args.by,
            }
        i += 1

    lines = [json.dumps(done[k], ensure_ascii=False) for k in sorted(done)]
    out_path.write_text("\n".join(lines) + ("\n" if lines else ""),
                        encoding="utf-8", newline="\n")   # OS 상관없이 LF
    print(f"\n저장했습니다: {out_path.name} · {len(done)}문항")
    if len(done) < len(items):
        print(f"남은 문항 {len(items) - len(done)}개 — 같은 명령에 --resume 을 붙이면 이어서 합니다")


if __name__ == "__main__":
    main()
