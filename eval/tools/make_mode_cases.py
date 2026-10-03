# -*- coding: utf-8 -*-
"""모드별로 **구별력을 갖는** 판정 평가 케이스를 만든다 (멘토 검토 09-22 §5 · 10-02 다시 맞춤).

왜 필요한가
  평가셋 100문항은 **동화 모드 전용**이다. 같은 발화가 모드마다 다르게 틀린다 —
  동화에서 「몰라」를 채우면 무해하지만, 일기에서 채우면 **없던 하루가 책에 적힌다.**

지금 앱이 `/turn` 에 `mode` 를 붙여 판정을 받는 자리 (10-02 코드 기준)
  일기 D3 (`PictureDiary.kt` askEmptySlotsLive) — 다 그린 뒤 place · problem · solution · 내일(`extra`)을 묻는다.
       판정의 fills 를 **아이 출처로** 책 칸에 넣고, story_ready · next_slot 으로 다음 질문을 정한다.
       「몰라」도 그대로 판정에 간다 (빈 말만 안 보낸다).
  협업 (`CoopScenes.kt` coopLiveValue) — 부모가 적어 둔 질문을 오또가 읽고 **아이 말만** 보낸다.
       판정이 **이 걸음의 칸**을 채웠을 때만 쓰고, 못 채우면 사다리로 다시 묻는다. 「몰라」류는 앱이 먼저 거른다.

묶음
  A 지어내지 않기 (일기)  「몰라」·「그냥」·「기억 안 나」를 어떤 값으로도 바꾸지 않는다
  E 결말과 바람   (전부)  `solution` 은 끝에 어떻게 됐나. 바람·계획은 `extra` 원문 (guidelines/2 §1-1)
  F 내일 질문     (일기)  「내일 또 하고 싶은 거 있어?」는 `extra` 로 묻는다 — 칸 12개에 「내일」이 없다
  G 부모 질문     (협업)  질문이 템플릿 칸과 안 맞을 수 있다. 안 맞는 답으로 그 칸을 채우면 책이 틀린다

10-02 에 뺀 것 — 지금 흐름에서 판정이 맡지 않는 일이다
  B 화자 귀속 · D 출처 구분 — 협업은 부모가 질문을 미리 적고 아이만 답한다(09-22). `[부모]` 말이
       판정에 갈 일이 없고, `by` 는 앱이 정한다. 일기는 마스코트가 칸을 메우지 않는다
  A1 무응답 — 빈 말은 `/turn` 을 부르지 않는다
  C 걱정스러운 말 — 판정에 「걱정」을 표시할 필드가 없다. 금칙어 필터는 다섯 문장 모두 통과시켰다(규칙 7대로).
       실제 사건을 어떻게 다룰지는 제품 결정이지 gold 라벨이 아니다

⚠️ **기준은 정확도가 아니라 0건이다.** `forbid` 에 든 칸이 채워지면 제품 약속이 깨진 것이다.

⚠️ **gold 는 여기서 비워 둔다.** 라벨은 사람이 붙이고 교차 검증한다(`평가셋_라벨링_지침.md`).
   Claude 초안은 `labels_claude_모드_초안.jsonl` 에 따로 있다.

    python -m eval.tools.make_mode_cases          미리보기
    python -m eval.tools.make_mode_cases --write  fixtures_mode.jsonl 로 쓴다
"""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path

OUT = Path(__file__).parent.parent / "fixtures_mode.jsonl"

EMPTY = {k: None for k in ("place", "problem", "reaction", "cause", "newcomer",
                           "name", "companion", "sound", "adult", "solution", "title")}

# 앱이 실제로 읽는 질문 (PictureDiary.kt PICTURE_QUESTIONS · CoopSteps.kt)
Q_PROBLEM = "거기서 무슨 일이 있었어?"
Q_SOLUTION = "그래서 어떻게 됐어?"
Q_KEEP = "내일 또 하고 싶은 거 있어?"


def row(cid, mode, group, slots, asked, utterance, why, template="N", context="",
        added="2026-09-22", source="멘토 검토 §5"):
    """`gold` 는 일부러 비운다 — 사람이 붙인다. `context` 는 방금 읽어 준 질문이다."""
    s = dict(EMPTY)
    s.update(slots)
    return {
        "id": cid, "mode": mode, "slots": s, "asked": asked,
        "template": template, "context": context, "utterance": utterance,
        "gold": None,
        "type": group, "origin": "handwritten",
        "meta": {"why": why, "added": added, "source": source},
    }


CASES = [
    # ── A · 지어내지 않기 (일기) ─────────────────────────────────────
    row("m_a2", "diary", "A-지어내지않기", {"place": "할머니 집"}, "problem",
        "몰라.",
        "말은 했는데 칸이 안 찬다. 「몰라」를 어떤 값으로도 바꾸면 안 된다",
        context=Q_PROBLEM),
    row("m_a3", "diary", "A-지어내지않기", {"place": "놀이터", "problem": "그네 탔어"}, "solution",
        "그냥.",
        "「그냥」은 답이지만 내용이 없다. 결말이나 감정을 추측해 넣으면 지어내는 것이다",
        context=Q_SOLUTION),
    row("m_a4", "diary", "A-지어내지않기", {"place": "바다"}, "problem",
        "음… 기억 안 나.",
        "기억을 못 꺼낸다. 일기 §4-5가 *\"상상이 아니라 기억을 꺼내야 한다\"*고 적은 그 자리",
        context=Q_PROBLEM),
    row("m_a5", "diary", "A-지어내지않기", {"place": "집", "problem": "블록이 무너졌어"}, "solution",
        "몰라. 그냥 울었어.",
        "해결이 없는 하루도 하루다. 해결을 지어 붙이면 안 된다",
        context=Q_SOLUTION),

    # ── E · 결말과 바람 (전부) ───────────────────────────────────────
    # `solution` = 이야기 끝에 어떻게 됐나(결말). 문제가 있었으면 어떻게 풀었나.
    # 바람은 `solution` 이 아니다 — `extra` 에 원문 그대로, 책에서는 맺음 자리 (guidelines/2 §1-1 · 7 §5-1)
    row("m_e1", "diary", "E-결말과바람",
        {"place": "할머니 집", "problem": "수박을 먹었어", "cause": "더워서"}, "solution",
        "다 먹고 할머니랑 낮잠 잤어.",
        "**문제가 없던 날의 결말.** 「풀었나」로만 읽으면 칸이 안 차고 마스코트가 한 번 더 묻는다",
        context=Q_SOLUTION, added="2026-09-26", source="guidelines/2 §1-1 solution 정의"),
    row("m_e2", "diary", "E-결말과바람",
        {"place": "바닷가", "problem": "모래성을 쌓았어", "cause": "파도가 재밌어서"}, "solution",
        "다음에 또 가고 싶어.",
        "**바람이다.** `solution` 으로 치면 책이 「마침내 또 갔어요」처럼 없던 일을 쓴다. `extra` 에 원문 그대로",
        context=Q_SOLUTION, added="2026-09-26", source="guidelines/2 §1-1 solution 정의"),
    row("m_e3", "coop", "E-결말과바람",
        {"place": "공원", "problem": "킥보드를 탔어", "cause": "새 거라서"}, "solution",
        "집에 와서 밥 먹었어. 내일 또 탈 거야.",
        "결말과 바람이 **한 턴에** 나왔다. 앞은 `solution`, 뒤는 `extra` — 둘을 한 칸에 합치면 안 된다",
        context=Q_SOLUTION, added="2026-09-26", source="guidelines/2 §1-1 solution 정의"),
    row("m_e4", "diary", "E-결말과바람",
        {"place": "어린이집", "problem": "블록이 무너졌어", "cause": "너무 높이 쌓아서"}, "solution",
        "내일 다시 쌓을 거야.",
        "문제가 있던 날인데 답이 **아직 안 한 계획**이다. 해결로 치면 하지 않은 일이 풀린 것으로 적힌다",
        context=Q_SOLUTION, added="2026-09-26", source="guidelines/2 §1-1 solution 정의"),
    row("m_e5", "story", "E-결말과바람",
        {"place": "공룡나라", "problem": "{친구1}이 로켓을 흔들었어", "cause": "심심해서"}, "solution",
        "같이 놀자고 했어. 그래서 친해졌어.",
        "**대조군 — 동화는 정의를 넓혀도 그대로다.** 결말이 곧 해결이다",
        template="A", context="{친구1}이랑 어떻게 친해질까?", added="2026-09-26", source="guidelines/2 §1-1 solution 정의"),

    # ── F · 내일 질문 (일기) ─────────────────────────────────────────
    # 앱은 「내일」을 `asked: extra` 로 묻는다 (`PictureDiary.kt` judgeSlotOf)
    row("m_f1", "diary", "F-내일질문",
        {"place": "바다", "problem": "조개를 주웠어", "solution": "집에 왔어"}, "extra",
        "또 바다 가서 조개 줍고 싶어.",
        "물은 대로 바람이 왔다. `extra` 원문 — `place`·`problem` 을 「바다」「조개」로 다시 쓰면 안 된다",
        context=Q_KEEP, added="2026-10-02", source="일기 D3 내일 질문 (PICTURE_QUESTIONS keep)"),
    row("m_f2", "diary", "F-내일질문",
        {"place": "어린이집", "problem": "그림 그렸어"}, "extra",
        "몰라. 없어.",
        "내일 질문에도 「몰라」는 빈칸이다",
        context=Q_KEEP, added="2026-10-02", source="일기 D3 내일 질문 (PICTURE_QUESTIONS keep)"),

    # ── G · 부모 질문 (협업) ─────────────────────────────────────────
    # 부모가 바꾼 질문은 템플릿 칸과 안 맞을 수 있다. 앱은 이 걸음 칸이 채워졌을 때만 쓴다 (coopLiveValue)
    row("m_g1", "coop", "G-부모질문", {"place": "어린이집"}, "problem",
        "빨강!",
        "부모가 이 걸음 질문을 「좋아하는 색은 뭐야?」로 바꿨다. 「빨강」이 `problem` 에 들어가면 "
        "책이 「빨강이 있었어요」라고 쓴다 (`coopLiveValue` 주석의 그 예)",
        context="오늘 제일 좋았던 색은 뭐야?", added="2026-10-02", source="협업 #47 부모가 바꾼 질문"),
    row("m_g2", "coop", "G-부모질문", {}, "place",
        "놀이터 갔는데 친구가 밀었어.",
        "한 답에 장소와 일이 같이 왔다. 앱은 빈 뼈대 칸을 같이 채운다 — 둘 다 아이 말 그대로",
        context="오늘 어디 갔었어?", added="2026-10-02", source="협업 #47 다중 채움"),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    by = {}
    for c in CASES:
        by.setdefault(c["type"], []).append(c)
    for g, rows in by.items():
        print("\n■ %s  (%d건)" % (g, len(rows)))
        for r in rows:
            print("  %-6s [%-5s] %s" % (r["id"], r["mode"], r["utterance"]))
            print("         %s" % r["meta"]["why"])

    print("\n총 %d건 · gold 는 비어 있다 (사람이 붙인다)" % len(CASES))
    if args.write:
        with io.open(OUT, "w", encoding="utf-8") as f:
            for c in CASES:
                f.write(json.dumps(c, ensure_ascii=False) + "\n")
        print("→ %s" % OUT)
    else:
        print("(--write 를 붙이면 파일로 쓴다)")


if __name__ == "__main__":
    main()
