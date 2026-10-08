"""What the child meant by this reply — one quick choice call, next to the judge (#323).

The judge sees only the question just asked, so 「그거 아니야」 looks like an answer to it. The decider
reads a short state instead: what Otto said last, the slots it filled, and the reply. It answers
choice/yes-no questions with a confidence, which is all Jev (and Laya after it, #324) can do.

The decider is a setting (`dialogue_decider`): the questions live here, the vendor call does not,
so moving to Laya is a new branch in `decide`, not a change in the callers. The 0.8/0.6 floors in
policy.py were set for Jev; re-measure them on a switch.
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from ..config import settings
from ..llm import jev
from ..schemas.judge import SLOT_NAMES
from ..schemas.turn import TurnRequest

log = logging.getLogger("otto.dialogue")

INTENTS = {
    "answer": "물은 것에 답했다 — 짧거나 엉뚱해 보여도 질문에 대한 답이다",
    "correct": "오또가 앞에서 잘못 알아들은 것을 고친다 — 「아니야」 · 「그거 아닌데」 · 「고양이였어」",
    "ask_back": "오또에게 되묻는다 — 「오또는?」 · 「왜?」 · 「오또는 뭐 좋아해?」",
    "not_heard": "질문을 못 알아들었다 — 「뭐라고?」 · 「응?」 · 「다시 말해 줘」",
    "refuse": "답하기 싫거나 모른다 — 「몰라」 · 「싫어」 · 「안 할래」",
    "aside": "질문과 상관없는 다른 이야기를 한다",
    "continue": "앞에서 하던 이야기를 이어서 더 말하는 중이다 — 「그리고」 · 「그래서」",
}
INTENT_Q = "아이가 오또의 마지막 말을 듣고 방금 한 말은 어떤 말인가"

TARGET_CRITERIA = {
    "place": "어디", "problem": "무슨 일", "reaction": "그때 마음", "cause": "왜", "newcomer": "새로 나온 것",
    "name": "이름", "companion": "누구랑", "sound": "소리", "adult": "어른의 말", "solution": "어떻게 됐나",
    "title": "제목", "extra": "그 밖의 이야기", "none": "고치는 말이 아니다 / 어느 칸인지 모르겠다",
}
TARGET_Q = "아이가 고치려는 것은 오또가 앞에서 받아 적은 칸 중 어느 칸인가"

NEW_VALUE_Q = ("고치면서 **맞는 내용을 같이 말했는가** (「아니야, 고양이였어」)",
               "맞는 내용을 같이 말했다", "아니라고만 했다")

RECENT = 3            # turns of fills the state shows — older ones are not what 「그거」 points at


@dataclass
class Decision:
    intent: str
    intent_conf: float | None
    target: str | None = None
    target_conf: float | None = None
    new_value: bool = False


def state_text(req: TurnRequest) -> str:
    """The short state the decider reads. Otto's words and the child's are labelled apart (rule 5)."""
    lines = []
    for h in req.history[-RECENT:]:
        said = " ".join(x for x in (h.otto.ack, h.otto.expand) if x)
        if said:
            lines.append(f"오또: {said}")
        if h.otto.question:
            lines.append(f"오또: {h.otto.question}")
        lines.append(f"아이: {h.child.text}")
        if h.fills:
            lines.append("  받아 적은 칸: " + " · ".join(f"{f.slot}={f.value}" for f in h.fills))
    last = req.history[-1] if req.history else None
    if last is not None and req.question and req.question != (last.otto.question or ""):
        lines.append(f"오또: {req.question}")
    lines.append(f"아이(방금): {req.utterance}")
    return "[대화]\n" + "\n".join(lines)


def questions() -> dict:
    return {
        "intent": {"type": "choice", "instructions": INTENT_Q, "criteria": INTENTS},
        "target": {"type": "choice", "instructions": TARGET_Q, "criteria": TARGET_CRITERIA},
        "new_value": {"type": "noul", "instructions": NEW_VALUE_Q[0],
                      "criteria": {"true": NEW_VALUE_Q[1], "false": NEW_VALUE_Q[2]}},
    }


def from_answers(a: dict) -> Decision | None:
    intent = (a.get("intent") or {})
    if intent.get("choice") not in INTENTS:
        return None
    target = a.get("target") or {}
    t = target.get("choice")
    return Decision(
        intent=intent["choice"], intent_conf=intent.get("confidence"),
        target=t if t in SLOT_NAMES else None, target_conf=target.get("confidence"),
        new_value=((a.get("new_value") or {}).get("noul") or 0) >= 0.5,
    )


_NEG = re.compile(r"^(아니|아닌데|아냐|그거\s*아니)")


def mock(req: TurnRequest) -> Decision:
    """Words only, for tests and the mock server — never measured, never served."""
    u = req.utterance.strip()
    if _NEG.match(u):
        rest = _NEG.sub("", u).strip(" ,.야")
        return Decision("correct", 0.95, None, None, new_value=len(rest.split()) >= 1)
    if re.search(r"뭐라고|다시 말|응\?$", u):
        return Decision("not_heard", 0.9)
    if u.endswith("?") or u.startswith("오또는"):
        return Decision("ask_back", 0.9)
    if re.fullmatch(r"(몰라|싫어|안 할래)[.!]?", u):
        return Decision("refuse", 0.9)
    return Decision("answer", 0.9)


async def decide(req: TurnRequest) -> Decision | None:
    """None = no opinion (no history, or the decider failed): the turn runs as before."""
    if not req.history:
        return None
    if settings.mock or settings.dialogue_decider == "mock":
        return mock(req)
    if settings.dialogue_decider == "jev":
        try:
            state = jev.mask_names(state_text(req), req.names)
            answers, secs = await jev.ask(state, questions(), timeout_s=settings.dialogue_deadline_s)
            log.info("dialogue jev %.2fs", secs)
            return from_answers(answers)
        except jev.JevError as e:
            log.warning("dialogue jev failed, plain turn: %s", e)
            return None
    log.warning("unknown dialogue_decider %r, plain turn", settings.dialogue_decider)
    return None
