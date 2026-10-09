"""One recipe per act: what the line model is told, and which slot its question is about (#323).

A recipe picks from the history only what its act needs, so a plain turn's input stays exactly as
it was. How Otto should sound for each act is prompt text (eval/line_act.md), not code.
Adding a case = one function here + one line in policy.ACTS + one paragraph in line_act.md.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Optional

from ..schemas.turn import Act, TurnRequest
from .decide import Decision


@dataclass
class Recipe:
    act: Act
    context: str                       # extra user-message lines for the line model
    question_slot: Optional[str]       # the slot the question asks; None = the judge's next slot


def _otto_last(req: TurnRequest) -> str:
    if not req.history:
        return ""
    o = req.history[-1].otto
    return " ".join(x for x in (o.ack, o.expand) if x)


def _value_before(req: TurnRequest, slot: str) -> str:
    return req.slots.get(slot) or ""


def repair(req: TurnRequest, d: Decision, retract: list[str]) -> Recipe:
    slot = retract[0] if retract else None
    lines = [f"otto_said_before:{_otto_last(req)}"]
    if slot:
        # the line model copies the right value for this slot (fixed_value) or asks it again (retry_slot);
        # which of the two happened is settled after the line (policy.settle_repair)
        lines += [f"wrong:{slot}={_value_before(req, slot)}", f"retry_slot:{slot}"]
    return Recipe("repair", "\n".join(lines), None)


def answer_back(req: TurnRequest, d: Decision, retract: list[str]) -> Recipe:
    return Recipe("answer_back", f"repeat_question:{req.question}", req.asked_slot)


def rephrase(req: TurnRequest, d: Decision, retract: list[str]) -> Recipe:
    return Recipe("rephrase", f"repeat_question:{req.question}", req.asked_slot)


def aside(req: TurnRequest, d: Decision, retract: list[str]) -> Recipe:
    return Recipe("aside", f"repeat_question:{req.question}", req.asked_slot)


RECIPES: dict[str, Callable[[TurnRequest, Decision, list[str]], Recipe]] = {
    "repair": repair,
    "answer_back": answer_back,
    "rephrase": rephrase,
    "aside": aside,
}


def build(act: Act, req: TurnRequest, d: Decision, retract: list[str]) -> Recipe:
    return RECIPES[act](req, d, retract)
