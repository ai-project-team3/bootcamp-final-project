# -*- coding: utf-8 -*-
"""What the child would actually hear in the picture diary, from a saved /turn answer (#323 · 10-09).

The blind table first showed the server line. But the diary's after-drawing loop does not speak it
when the verdict filled nothing: it asks that slot's easy question instead (PictureDiary.kt
`if (v.fills.isEmpty())` → `continue`, before `sayReaction`). 30 of B's 35 non-answer rows were so.
This applies the app's rules to the saved rows — no vendor call:

- B (main): the rules as they are
- R: what the app is planned to do once connected — an act speaks the line; no act = B's rules.
  ⚠️ planned, not yet built in the app. A failed line with an act falls back to B's rules
  (the baked lines are not written yet)

The first ask of a slot is assumed (`easyTried` empty). Drawing clues (`withClue`) are not modelled.

    py eval/dialogue_heard.py eval/raw/dialogue_1009_1131.jsonl   # prints one json row per call
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

EVAL = Path(__file__).resolve().parent
CASES = {json.loads(l)["id"]: json.loads(l)
         for l in (EVAL / "fixtures_dialogue.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()}

# PictureDiary.kt PICTURE_QUESTIONS — (key, ask, easy) in the app's order; ask for problem is atPlace(…)
QUESTIONS = [
    ("place", "오늘 어디 갔었어?", "아침 먹고 어디 갔어?"),
    ("companion", "누구랑 같이 있었어?", "혼자 있었어, 아니면 같이 있었어?"),
    ("problem", None, "거기서 뭐 했어?"),
    ("reaction", "그때 기분이 어땠어?", "그때 마음이 어땠어?"),
    ("solution", "그래서 어떻게 됐어?", "그다음엔 뭐 했어?"),
    ("keep", "내일 또 하고 싶은 거 있어?", "내일은 뭐 하고 싶어?"),
]
EASY = {k: e for k, _, e in QUESTIONS}
NON_ANSWER = {"응", "어", "음", "아니", "네", "예", "없어", "몰라요", "싫어", "몰라"}   # CoopScenes.kt (subset that matters here)


def first_empty(slots: dict) -> str | None:
    for key, ask, _ in QUESTIONS:
        if not slots.get(key):
            if key == "problem":
                place = slots.get("place")
                return f"{place}에서 무슨 일이 있었어?" if place else "오늘 무슨 일이 있었어?"
            return ask
    return None


def heard_b(row: dict, case: dict) -> str:
    req = case["req"]
    key = "keep" if req["asked_slot"] == "extra" else req["asked_slot"]
    line = row["line"]
    if not row["fills"] and EASY.get(key):
        return EASY[key]                                   # no reaction, the easy question
    slots = dict(req["slots"])
    for s, v in row["fills"]:
        slots[s] = v
    said = []
    if line and (line.get("ack") or line.get("expand")):
        said += [x for x in (line.get("ack"), line.get("expand")) if x]
    elif row["fills"] and req["utterance"].strip() not in NON_ANSWER:
        said.append("(낱말 하나로 받기)")                    # sayAck → coopAck: not modelled word for word
    q = line.get("question") if line else None
    said.append(q or first_empty(slots) or "")
    return " ".join(x for x in said if x)


def heard_r(row: dict, case: dict) -> str:
    line = row["line"]
    if row["act"] and line:
        return " ".join(x for x in (line.get("ack"), line.get("expand"), line.get("question")) if x)
    return heard_b(row, case)


def heard(row: dict) -> str:
    case = CASES[row["id"]]
    return heard_r(row, case) if row["variant"] == "R" else heard_b(row, case)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for l in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
        if l.strip():
            r = json.loads(l)
            print(json.dumps({"variant": r["variant"], "run": r["run"], "id": r["id"], "heard": heard(r)}, ensure_ascii=False))
