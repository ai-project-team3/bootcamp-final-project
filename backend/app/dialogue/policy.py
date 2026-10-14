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
    "aside": None,           # plain turn (10-09): blind raters did not prefer R on asides (R 5 · B 3 · both poor 5)
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


def refused(d: Decision | None) -> bool:
    """The child would rather not answer — at the costly floor, like a correction (#405). No act follows."""
    if d is None or d.intent != "refuse":
        return False
    return d.intent_conf is None or d.intent_conf >= FLOOR_COSTLY


def _last_filled(req: TurnRequest) -> str | None:
    """The slot Otto last wrote down — what a bare 「그거 아니야」 most likely points at."""
    for h in reversed(req.history or []):
        for f in reversed(h.fills):
            if req.slots.get(f.slot):
                return f.slot
    return None


def _just_filled(req: TurnRequest) -> set[str]:
    """The slots Otto's last reaction was about — what the last answered turn wrote down."""
    for h in reversed(req.history or []):
        if h.child.text.strip():           # Otto's words alone (talk while drawing) answer nothing
            return {f.slot for f in h.fills}
    return set()


def is_stale(retract: list[str], req: TurnRequest) -> bool:
    """A slot filled before Otto's last reaction — 「그거 아니야」 alone is not about it."""
    return bool(retract) and retract[0] not in _just_filled(req)


def retract_for(act: Optional[Act], d: Decision | None, req: TurnRequest) -> list[str]:
    if act != "repair" or d is None:
        return []
    # an empty slot holds nothing to deny — the decider then pointed at the question asked, not at what
    # Otto wrote down (10-13 story bench c02: 「그거 아니야」 → target `problem` 0.71–0.75, 3/3)
    sure = d.target and req.slots.get(d.target) and (d.target_conf is None or d.target_conf >= FLOOR)
    slot = d.target if sure else _last_filled(req)
    if not slot or not req.slots.get(slot):
        return []
    # An older slot is taken back for now: the line model is asked to copy its right value, and settle_repair
    # keeps the change only when that value is in the child's words — otherwise the slot stays as it was
    # (10-12 device round 3: 「그거 아니야」 to the next question must not empty a slot fixed two turns before).
    # 10-14: this used to wait for the decider's `new_value`, which it missed on 「놀이터 아니고 공원이야」
    # 1 run in 3 — the slot stayed 「놀이터」 while Otto said 「공원이었구나」 (re-measure 27/30)
    return [slot]


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
                  retract: list[str]) -> tuple[JudgeResult | None, bool, list[str]]:
    """After the line: put the child's right value in the slot taken back, or ask that slot again.

    Returns (verdict, kept, retract) — kept=False means the line's question is about the wrong slot now."""
    if act != "repair" or not retract:
        return v, True, retract
    slot = retract[0]
    base = v or JudgeResult(reason="dialogue_repair")
    if from_the_child(fixed_value, req.utterance):
        return base.model_copy(update={"slot_1": slot, "value_1": fixed_value.strip(),
                                       "slot_2": None, "value_2": None}), True, retract
    if is_stale(retract, req):
        # an older slot was taken back for its right value, and none came — it stays; the question asked stands
        return base.model_copy(update={"next_slot": req.asked_slot}), False, []
    return base.model_copy(update={"next_slot": slot}), fixed_value is None, retract
