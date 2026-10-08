"""From what the child meant to what Otto does — rules, not the model (#323 · rule 4's spirit).

The decider only signals an intent with a confidence. Which act follows, which slots go back and
what the verdict may still fill are decided here, so the same reply always gets the same handling.
"""
from __future__ import annotations

from typing import Optional

from ..schemas.judge import JudgeResult
from ..schemas.turn import Act, TurnRequest
from .decide import Decision

# Taking a real answer for a correction or a refusal throws the child's words away, so those two
# need more certainty than the rest (#323 Q17 · fixed before measuring, set for Jev).
FLOOR = 0.6
FLOOR_COSTLY = 0.8
COSTLY = {"correct", "refuse"}

ACTS: dict[str, Optional[Act]] = {
    "answer": None,
    "correct": "repair",
    "ask_back": "answer_back",
    "not_heard": "rephrase",
    "refuse": None,          # each mode already has a ladder for 「몰라」 (diary: easier once, then move on)
    "aside": "aside",
    "continue": None,        # out of this round (10-08): the app should keep listening — a plain turn, logged
}

# acts where the reply is not an answer to the question, so the verdict fills nothing
NOT_AN_ANSWER: set[str] = {"answer_back", "rephrase"}


def act_for(d: Decision | None) -> Optional[Act]:
    if d is None:
        return None
    floor = FLOOR_COSTLY if d.intent in COSTLY else FLOOR
    if d.intent_conf is not None and d.intent_conf < floor:
        return None
    return ACTS.get(d.intent)


def _last_filled(req: TurnRequest) -> str | None:
    """The slot Otto last wrote down — what a bare 「그거 아니야」 most likely points at."""
    for h in reversed(req.history):
        for f in reversed(h.fills):
            if req.slots.get(f.slot):
                return f.slot
    return None


def retract_for(act: Optional[Act], d: Decision | None, req: TurnRequest) -> list[str]:
    if act != "repair" or d is None:
        return []
    sure = d.target and (d.target_conf is None or d.target_conf >= FLOOR)
    slot = d.target if sure else _last_filled(req)
    return [slot] if slot and req.slots.get(slot) else []


def trim_verdict(act: Optional[Act], d: Decision | None, v: JudgeResult | None) -> JudgeResult | None:
    """Drop fills the reply cannot have made. The rest of the verdict (next slot, signals) stands."""
    if v is None or act is None:
        return v
    drop = set()
    for slot_f, value_f in (("slot_1", "value_1"), ("slot_2", "value_2")):
        slot = getattr(v, slot_f)
        if slot is None:
            continue
        if act in NOT_AN_ANSWER:
            drop.add(slot_f)
        # 「그거 아니야」 alone answers nothing — and Jev copies the words into a slot (jev.py value_1)
        elif act == "repair" and not (d and d.new_value):
            drop.add(slot_f)
    if not drop:
        return v
    out = v.model_copy()
    for slot_f in drop:
        setattr(out, slot_f, None)
        setattr(out, slot_f.replace("slot", "value"), None)
    return out
