"""Turn judgment. Spec: docs/프롬프트_모음.md §2.

Flat by design. Nested schemas make "right value, wrong field" the dominant
failure mode (arXiv 2608.25358). `reason` comes first on purpose: writing the
rationale before the verdict recovers 15-24 points (arXiv 2608.08254).
"""
from typing import Literal, Optional
from pydantic import BaseModel, Field

SlotName = Literal[
    "place", "problem", "reaction", "cause", "newcomer", "name",
    "companion", "sound", "adult", "solution", "title", "extra",
]

# Slot names are a closed set. The model selects; it never invents.
# Anything off-list goes to `extra` verbatim.
SLOT_NAMES: tuple[str, ...] = SlotName.__args__


class JudgeRequest(BaseModel):
    slots: dict[str, Optional[str]]      # whole state, not one slot
    asked_slot: Optional[str] = None     # context only; the answer may fill others
    template: Optional[str] = None       # which story beats are still missing
    level: Optional[str] = None
    turn: int = 0                        # reference value, not a cap
    question: str
    utterance: str                       # names already masked on the phone


class JudgeResult(BaseModel):
    reason: str = Field(description="one-line rationale, written before the verdict")

    slot_1: Optional[SlotName] = None
    value_1: Optional[str] = None
    slot_2: Optional[SlotName] = None    # "공룡나라 갈래, 뿌뿌도 데려갈래"
    value_2: Optional[str] = None

    contradiction: bool = False
    contradiction_with: Optional[str] = None

    s1_reason: bool = False              # gave a cause unprompted
    s2_addition: bool = False            # added something unasked
    emotion: Optional[str] = None

    unclear: bool = False
    unclear_of: Optional[str] = None

    next_slot: Optional[SlotName] = None
    next_reason: Optional[str] = None
    no_longer_needed: Optional[SlotName] = None
    story_ready: bool = False            # ends the session, not a turn count
