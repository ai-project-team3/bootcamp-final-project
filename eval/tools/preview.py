"""평가셋을 사람이 읽기 쉬운 문서로 바꾼다 (역할 1 · 안치영).

아래에서 **지시서** = 노션 「오늘 모델 검증 — 4명 실행 지시서」.
칸 이름과 판정 필드의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` 다.

`.jsonl`은 도구가 한 줄씩 읽는 형식이라 줄바꿈을 넣을 수 없다.
그래서 같은 내용을 표와 들여쓴 블록으로 다시 그려 `평가셋_미리보기.md`를 만든다.

  python tools/preview.py
"""

import json
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
JUDGE = HERE / "fixtures_judge.jsonl"
STORY = HERE / "fixtures_story.jsonl"
OUT = HERE / "평가셋_미리보기.md"

TYPE_NAME = {
    "정답형": "칸에 맞는 답",
    "S1": "까닭을 말한 답 (S1) — 인과 `-서` 3개 · 순차 함정 2개 포함",
    "S2": "스스로 덧붙인 답 (S2) — 칸 둘을 채우는 다중채움 10개 포함",
    "필수해제": "필수해제 — 그 칸이 더는 필요 없어지는 말",
    "무관": "무관 · 엉뚱 (뒤집는 말 4개 포함)",
    "미완결": "짧거나 미완결 · 뭉개진 말",
}


def state_of(slots: dict) -> str:
    """찬 칸만 한 줄로. 빈 칸은 적지 않는다."""
    return " · ".join(f"{k}={v}" for k, v in slots.items() if v is not None) or "(전부 빔)"


def labels_of(gold: dict | None) -> str:
    """정답 라벨을 한 칸에 보기 좋게. 아직 안 붙였으면 '(라벨 대기)'."""
    if not gold:
        return "(라벨 대기)"
    out = []
    for n in ("1", "2"):
        if gold.get(f"slot_{n}"):
            out.append(f"{gold[f'slot_{n}']}={gold.get(f'value_{n}') or ''}")
    if gold.get("s1_reason"):
        out.append("S1")
    if gold.get("s2_addition"):
        out.append("S2")
    if gold.get("emotion"):
        out.append(f"마음({gold['emotion']})")
    if gold.get("contradiction"):
        out.append("뒤집음")
    if gold.get("unclear"):
        out.append("되묻기")
    if gold.get("no_longer_needed"):
        out.append(f"해제({gold['no_longer_needed']})")
    if gold.get("next_slot_ok"):
        out.append("다음→" + "/".join(gold["next_slot_ok"]))
    if gold.get("story_ready"):
        out.append("완성")
    return " ".join(out) if out else "—"


def main() -> None:
    judge = [json.loads(l) for l in JUDGE.read_text(encoding="utf-8").splitlines() if l.strip()]
    story = [json.loads(l) for l in STORY.read_text(encoding="utf-8").splitlines() if l.strip()]

    md = [
        "# 평가셋 미리보기 (자동 생성 · 고치지 말 것)",
        "",
        "> `tools/preview.py`가 `fixtures_judge.jsonl` · `fixtures_story.jsonl`을 읽어 다시 그린다.",
        "> 내용을 바꿀 때는 `.jsonl`을 고치고 이 스크립트를 다시 돌린다.",
        "> 라벨 뜻과 작업 순서는 `평가셋_라벨링_지침.md`를 본다.",
        "",
        f"## 1. 판정 평가셋 — {len(judge)}문항",
        "",
        "판정기는 **찬 칸 + 묻는 칸 + 템플릿 + 아이 말**을 받는다 (지시서 §1-1). `extra` = 11칸에 없는 질문.",
        "",
        "라벨 표기: `칸=값` = 채운 칸 · `S1` = 까닭 · `S2` = 덧붙임 · `뒤집음` = 앞서 정한 것과 어긋남 · "
        "`되묻기` = 뜻을 알 수 없음 · `해제(칸)` = 필요 없어진 칸 · `다음→` = 물어도 되는 칸",
        "",
        "라벨은 박진웅이 붙인다 (지시서 §2). 아직이면 `(라벨 대기)`로 나온다.",
        "",
    ]

    for st, name in TYPE_NAME.items():
        rows = [r for r in judge if r["type"].split("-")[0] == st]
        hand = sum(1 for r in rows if r["origin"] == "handwritten")
        md += [
            f"### {name} · {len(rows)}문항"
            + (f" (사람이 쓴 것 {hand}개)" if hand else ""),
            "",
            "| id | 찬 칸 | 묻는 칸 · 질문 | 아이 말 | 뼈대 | 표시 | 라벨 |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
        for r in rows:
            q = r["context"].replace("|", "\\|")
            u = r["utterance"].replace("|", "\\|")
            mark = r["type"] if "-" in r["type"] else ""
            md.append(f"| {r['id']} | {state_of(r['slots'])} | `{r['asked']}` {q} | **{u}** "
                      f"| {r['template']} | {mark} | {labels_of(r['gold'])} |")
        md.append("")

    md += [
        "---",
        "",
        f"## 2. 생성 입력 — {len(story)}벌",
        "",
    ]
    for st in story:
        md += [
            f"### {st['id']} · {st['world']}",
            "",
            f"- 왜 골랐나: {st['note']}",
            "",
            "칸 (다 찬 상태)",
            "",
            "```json",
            json.dumps(st["slots"], ensure_ascii=False, indent=2),
            "```",
            "",
            "지켜야 할 것 (자동 채점이 그대로 잰다)",
            "",
            "```json",
            json.dumps(st["expect"], ensure_ascii=False, indent=2),
            "```",
            "",
        ]

    OUT.write_text("\n".join(md) + "\n", encoding="utf-8", newline="\n")   # OS 상관없이 LF
    print(f"{OUT.name}: 판정 {len(judge)}문항 · 생성 입력 {len(story)}벌")


if __name__ == "__main__":
    main()
