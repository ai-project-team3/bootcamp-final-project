"""검수 라벨을 **빈칸 채우기 파일**로 붙인다 (역할 1 · 안치영).

대화형 `label.py` 는 문항당 질문 12개를 타이핑해야 하고 별도 터미널이 필요하다.
이 도구는 채울 파일 하나를 만들어 주고, 다 채우면 그것을 읽어 `.jsonl` 로 바꾼다.
**앞으로 돌아가 고치기가 쉽다** — 지시서 §2-2 가 중요하게 보는 부분이다.

  python tools/sheet.py --new           # 채울 파일을 만든다 (검수 20문항)
  (파일을 열어 빈칸을 채운다)
  python tools/sheet.py --read          # 채운 파일 → labels_치영_검수20.jsonl

  python tools/sheet.py --new --all --by 진웅   # 100문항 전부 (박진웅용)

아래에서 **지시서** = 노션 「오늘 모델 검증 — 4명 실행 지시서」.
칸 이름과 판정 필드의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` 다.

⚠️ 검수하는 사람은 **박진웅의 라벨을 보지 않은 상태에서** 채워야 대조가 의미 있다.
"""

import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
FIXTURES = HERE / "fixtures_judge.jsonl"

ENUM = ["place", "problem", "reaction", "cause", "newcomer", "name",
        "companion", "sound", "adult", "solution", "title", "extra"]

# (필드, 종류, 옆에 적어 줄 말).  종류: slot 칸하나 · slots 칸여럿 · bool 예아니오 · text 글
#
# ⚠️ 검수 시트는 **대조에 쓰이는 9개만** 받는다.
#    `value_1` · `value_2` · `emotion` 은 자유 서술이라 `kappa.py` 가 비교하지 않는다.
#    최종 정답지(`gold`)에는 들어가야 하므로 박진웅의 100문항 시트(--all)에만 넣는다.
CORE = [
    ("slot_1", "slot", "이 말이 채우는 칸. 못 채우는 말이면 빈칸"),
    ("slot_2", "slot", "같은 말이 둘째 칸도 채웠으면. 아니면 빈칸"),
    ("s1_reason", "bool", "까닭을 말했나. 순차의 '-서'는 n (가서 먹었어)"),
    ("s2_addition", "bool", "묻지 않은 것을 스스로 더 말했나"),
    ("contradiction", "bool", "앞에서 정한 칸과 어긋나나. 찬 칸이 없으면 n"),
    ("unclear", "bool", "뜻을 알 수 없어 되물어야 하나 (공뇽…)"),
    ("no_longer_needed", "slot", "이 말로 필요 없어진 칸 (혼자 갔어 → companion)"),
    ("next_slot_ok", "slots", "다음에 물어도 되는 칸 전부. 띄어쓰기로 여러 개"),
    ("story_ready", "bool", "이제 이야기를 만들어도 되나"),
]
EXTRA = [
    ("value_1", "text", "slot_1 칸에 들어갈 값"),
    ("value_2", "text", "slot_2 칸에 들어갈 값"),
    ("emotion", "text", "마음을 말했으면 그 말 (무섭다)"),
]
FIELDS = CORE  # --all 이면 main() 에서 CORE + EXTRA 로 바꾼다
WIDTH = max(len(f) for f, _, _ in CORE + EXTRA)


# 채워 보인 예시. **검수 20문항에 없는 문항들만** 골랐다 — 답을 미리 보여 주면 대조가 무의미해진다
EXAMPLES = [
    ("j003", "칸을 하나 채운 보통의 답", {
        "slot_1": "place", "s1_reason": "n", "s2_addition": "n", "contradiction": "n",
        "unclear": "n", "next_slot_ok": "problem companion", "story_ready": "n"},
     "묻는 칸이 place 이고 '달님 마을'이 거기 들어간다. 까닭도 덧붙임도 없다."),
    ("j002", "한 말이 칸 **둘**을 채운 답", {
        "slot_1": "place", "slot_2": "companion", "s1_reason": "n", "s2_addition": "y",
        "contradiction": "n", "unclear": "n", "next_slot_ok": "problem newcomer",
        "story_ready": "n"},
     "place 를 물었는데 데려갈 친구까지 말했다 → slot_2 에 companion. 묻지 않은 것을 더 말했으니 s2_addition 도 y."),
    ("j036", "칸을 채운 게 아니라 **없앤** 답", {
        "s1_reason": "n", "s2_addition": "n", "contradiction": "n", "unclear": "n",
        "no_longer_needed": "companion", "next_slot_ok": "problem newcomer",
        "story_ready": "n"},
     "'아무도 안 데려가' 는 companion 칸에 값을 넣은 게 아니라 그 칸을 **지웠다**. "
     "그래서 slot_1 은 빈칸이고 no_longer_needed 에 companion 을 적는다."),
    ("j001", "칸을 **못 채우는** 답", {
        "s1_reason": "n", "s2_addition": "n", "contradiction": "y", "unclear": "n",
        "next_slot_ok": "place", "story_ready": "n"},
     "problem 을 물었는데 딴 말을 했다 → slot_1 빈칸. "
     "게다가 앞에서 정한 place=우주 를 뒤집었으니 contradiction 이 y."),
]

YES = {"y", "yes", "예", "o", "ㅇ", "true", "1"}
NO = {"n", "no", "아니오", "아니요", "x", "ㄴ", "false", "0", ""}


def sample_for_review(items: list[dict], n: int = 20) -> list[dict]:
    """`label.py` 와 **같은 20문항**을 뽑는다 (같은 씨앗·같은 층별 몫)."""
    import label
    return label.sample_for_review(items, n)


# ── 파일 만들기 ────────────────────────────────────────────────────

def block(n: int, total: int, it: dict) -> str:
    filled = " · ".join(f"{k}={v}" for k, v in it["slots"].items() if v) or "(전부 빔)"
    empty = " · ".join(k for k, v in it["slots"].items() if not v)
    out = [
        f"## {n}/{total}  {it['id']}   [{it['type']}]   템플릿 {it['template']}",
        "",
        f"    찬 칸 : {filled}",
        f"    빈 칸 : {empty}",
        f"    질문  : {it['context']}",
        f"    묻는 칸: {it['asked']}",
        f"    아이  : {it['utterance']}",
        "",
        "```",
    ]
    for f, kind, hint in FIELDS:
        out.append(f"{f:<{WIDTH}} =            # {hint}")
    out += ["```", ""]
    return "\n".join(out)


def example_blocks(all_items: list[dict]) -> list[str]:
    """맨 위에 붙일 '채워 보인 예시'. 이 문항들은 검수 20개에 없다."""
    by_id = {it["id"]: it for it in all_items}
    out = ["## 먼저 — 채워 보인 예시 4개",
           "",
           "**아래 넷은 채우는 문항이 아니다.** 어떻게 적는지 보여 주는 것뿐이다.",
           "실제로 채울 것은 그 다음 `1/20` 부터다.",
           ""]
    for n, (id_, title, vals, why) in enumerate(EXAMPLES, 1):
        it = by_id.get(id_)
        if not it:
            continue
        filled = " · ".join(f"{k}={v}" for k, v in it["slots"].items() if v) or "(전부 빔)"
        out += [
            f"### 예시 {n} — {title}",
            "",
            f"    찬 칸 : {filled}",
            f"    질문  : {it['context']}",
            f"    묻는 칸: {it['asked']}",
            f"    아이  : {it['utterance']}",
            "",
            "```",
        ]
        for f, _, _ in FIELDS:
            out.append(f"{f:<{WIDTH}} = {vals.get(f, '')}".rstrip())
        out += ["```", "", f"→ {why}", ""]
    out += ["---", ""]
    return out


def make(items: list[dict], out_path: Path, by: str, all_items: list[dict]) -> None:
    head = [
        f"# 판정 라벨 채우기 — {by or '이름 미기입'} · {len(items)}문항",
        "",
        "> `=` 뒤에 답을 적는다. **`#` 뒤는 설명이니 지우지 않아도 된다.**",
        "> 다 채우면 `python tools/sheet.py --read` 를 돌린다.",
        "> 틀린 곳이 있으면 알려 주고 **아무것도 저장하지 않는다.** 마음 놓고 채워도 된다.",
        "",
        "| 종류 | 어떻게 적나 | 없을 때 |",
        "| --- | --- | --- |",
        "| 칸 이름 | 아래 12개 중 하나 그대로 | **빈칸** |",
        "| 예/아니오 | `y` 또는 `n` | **빈칸 = n** |",
        "| 칸 여럿 | 띄어쓰기로 — `problem cause` | 빈칸 |",
        "",
        "**칸 이름 12개**",
        "",
        "| 칸 | 뜻 | 칸 | 뜻 |",
        "| --- | --- | --- | --- |",
        "| `place` | 어디 | `companion` | 같이 가는 친구 |",
        "| `problem` | 무슨 일 | `sound` | 우는 소리 |",
        "| `reaction` | 그래서 어떻게 | `adult` | 어른이 하는 말 |",
        "| `cause` | 왜 | `solution` | 어떻게 해결 |",
        "| `newcomer` | 새로 나온 친구 | `title` | 책 제목 |",
        "| `name` | 그 친구 이름 | `extra` | 위 11개에 없는 질문 |",
        "",
        "애매하면 `평가셋_라벨링_지침.md` §2 의 기준표와 경계 사례를 본다. 물어봐도 된다.",
        "",
        "---",
        "",
    ]
    body = example_blocks(all_items) + [block(i, len(items), it)
                                        for i, it in enumerate(items, 1)]
    out_path.write_text("\n".join(head) + "\n".join(body),
                        encoding="utf-8", newline="\n")   # OS 상관없이 LF


# ── 파일 읽기 ──────────────────────────────────────────────────────

def parse_value(field: str, kind: str, raw: str, where: str, errs: list[str]):
    v = raw.split("#")[0].strip()
    if kind == "bool":
        if v.lower() in YES:
            return True
        if v.lower() in NO:
            return False
        errs.append(f"{where} {field}: '{v}' — y 나 n 으로 적는다")
        return False
    if kind == "text":
        return v or None
    if kind == "slot":
        if not v:
            return None
        if v not in ENUM:
            errs.append(f"{where} {field}: '{v}' 라는 칸은 없다")
            return None
        return v
    if kind == "slots":
        got = v.replace(",", " ").split()
        bad = [x for x in got if x not in ENUM]
        if bad:
            errs.append(f"{where} {field}: {' '.join(bad)} — 그런 칸은 없다")
        return [x for x in got if x in ENUM]
    return None


def read(sheet_path: Path, items: list[dict], out_path: Path, by: str) -> None:
    by_id = {it["id"]: it for it in items}
    kinds = {f: k for f, k, _ in FIELDS}
    rows: list[dict] = []
    errs: list[str] = []
    cur_id: str | None = None
    gold: dict = {}

    for line in sheet_path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s.startswith("## "):
            if cur_id and gold:
                rows.append({"id": cur_id, "gold": gold})
            parts = s.split()
            cur_id = next((p for p in parts if p in by_id), None)
            gold = {}
            continue
        if cur_id and "=" in s:
            field = s.split("=", 1)[0].strip()
            if field in kinds:
                gold[field] = parse_value(field, kinds[field], s.split("=", 1)[1],
                                          f"[{cur_id}]", errs)
    if cur_id and gold:
        rows.append({"id": cur_id, "gold": gold})

    # 빠진 것 · 앞뒤가 안 맞는 것
    for r in rows:
        missing = [f for f, _, _ in FIELDS if f not in r["gold"]]
        if missing:
            errs.append(f"[{r['id']}] 줄이 없다: {' '.join(missing)}")
        g = r["gold"]
        if g.get("value_1") and not g.get("slot_1"):
            errs.append(f"[{r['id']}] value_1 을 적었는데 slot_1 이 비어 있다")
        if g.get("slot_2") and not g.get("slot_1"):
            errs.append(f"[{r['id']}] slot_2 만 있고 slot_1 이 비어 있다")
        if g.get("slot_1") and g.get("slot_1") == g.get("slot_2"):
            errs.append(f"[{r['id']}] slot_1 과 slot_2 가 같다")
        if g.get("slot_1") and g.get("no_longer_needed") == g.get("slot_1"):
            errs.append(f"[{r['id']}] 같은 칸을 채우면서 필요 없다고 했다")

    filled = sum(1 for r in rows
                 if any(v not in (None, False, []) for v in r["gold"].values()))
    if rows and filled == 0:
        errs.append(f"{len(rows)}문항이 **전부 빈칸**이다 — 아직 안 채운 파일로 보인다")

    if errs:
        print(f"⚠️ 고칠 곳 {len(errs)}개 — 아무것도 저장하지 않았다\n")
        for e in errs[:30]:
            print("  " + e)
        if len(errs) > 30:
            print(f"  … 그리고 {len(errs) - 30}개 더")
        return

    out = [{"id": r["id"], "type": by_id[r["id"]]["type"],
            "stratum": by_id[r["id"]]["type"].split("-")[0],
            "gold": r["gold"], "by": by} for r in rows]
    out_path.write_text("\n".join(json.dumps(o, ensure_ascii=False) for o in out) + "\n",
                        encoding="utf-8", newline="\n")   # OS 상관없이 LF
    print(f"저장했습니다: {out_path.name} · {len(out)}문항")
    print(f"  값이 하나라도 들어간 문항 {filled}개 / {len(out)}개")
    if filled < len(out):
        blank = [o["id"] for o in out
                 if not any(v not in (None, False, []) for v in o["gold"].values())]
        print(f"  ⚠️ 전부 빈칸인 문항: {' '.join(blank)} — 안 채운 것인지 확인한다")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--new", action="store_true", help="채울 파일을 만든다")
    p.add_argument("--read", action="store_true", help="채운 파일을 .jsonl 로 바꾼다")
    p.add_argument("--all", action="store_true", help="100문항 전부 (기본은 검수 20문항)")
    p.add_argument("--by", default="치영", help="붙이는 사람 이름")
    p.add_argument("--sheet", default="", help="파일 이름 (기본: labels_<이름>_검수20.md)")
    args = p.parse_args()

    all_items = [json.loads(l) for l in FIXTURES.read_text(encoding="utf-8").splitlines() if l.strip()]
    items = all_items
    if args.all:
        global FIELDS
        FIELDS = CORE + EXTRA   # 최종 정답지에는 값·마음까지 필요하다
    else:
        items = sample_for_review(all_items, 20)

    tag = "전체100" if args.all else "검수20"
    stem = args.sheet or f"labels_{args.by}_{tag}"
    sheet = HERE / (stem if stem.endswith(".md") else stem + ".md")
    jsonl = sheet.with_suffix(".jsonl")

    if args.new:
        if sheet.exists():
            print(f"⚠️ {sheet.name} 이 이미 있다. 덮어쓰지 않았다 — 지우고 다시 돌린다")
            return
        make(items, sheet, args.by, all_items)
        print(f"만들었습니다: {sheet.name} · {len(items)}문항")
        print("  이 파일을 열어 = 뒤를 채운 뒤  python tools/sheet.py --read")
    elif args.read:
        if not sheet.exists():
            print(f"⚠️ {sheet.name} 이 없다 — 먼저 --new 로 만든다")
            return
        read(sheet, items, jsonl, args.by)
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
