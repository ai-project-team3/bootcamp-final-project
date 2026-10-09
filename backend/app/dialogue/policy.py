"""From what the child meant to what Otto does — rules, not the model (#323 · rule 4's spirit).

The decider only signals an intent with a confidence. Which act follows, which slots go back and
what the verdict may still fill are decided here, so the same reply always gets the same handling.
"""
from __future__ import annotations

from typing import Optional

from ..filters.blocklist import is_blocked
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
        # a question back or 「뭐라고?」 answers nothing. A correction's value comes from the line model
        # for the slot the decider picked (settle_repair) — the judge saw the wrong value still in place,
        # and on Jev copies the whole reply into some slot (jev.py value_1)
        if act in NOT_AN_ANSWER or act == "repair":
            drop.add(slot_f)
    if not drop:
        return v
    out = v.model_copy()
    for slot_f in drop:
        setattr(out, slot_f, None)
        setattr(out, slot_f.replace("slot", "value"), None)
    return out


def from_the_child(value: str | None, utterance: str) -> bool:
    """A corrected value is taken only when it is the child's own words (rule 5) — a model that wrote
    something the child did not say would make 「아이가 한 말」 a lie. Spaces aside, it must sit inside
    the transcript as it is; the line model is told to copy, not fix (STT typos stay, like every slot)."""
    if not value or not value.strip() or is_blocked(value):
        return False
    squash = lambda s: "".join(s.split())
    return squash(value) in squash(utterance)


def settle_repair(act: Optional[Act], req: TurnRequest, v: JudgeResult | None, fixed_value: str | None,
                  retract: list[str]) -> tuple[JudgeResult | None, bool]:
    """After the line: put the child's right value in the slot taken back, or ask that slot again.

    Returns (verdict, kept) — kept=False means the line's question is about the wrong slot now."""
    if act != "repair" or not retract:
        return v, True
    slot = retract[0]
    base = v or JudgeResult(reason="dialogue_repair")
    if from_the_child(fixed_value, req.utterance):
        return base.model_copy(update={"slot_1": slot, "value_1": fixed_value.strip(),
                                       "slot_2": None, "value_2": None}), True
    return base.model_copy(update={"next_slot": slot}), fixed_value is None
