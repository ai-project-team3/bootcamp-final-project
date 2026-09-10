"""State is a list of changes. The current state is derived, never stored."""

from typing import Literal

from pydantic import BaseModel

State = Literal["unseen", "raised", "partial", "resolved"]
RANK: dict[str, int] = {"unseen": 0, "raised": 1, "partial": 2, "resolved": 3}

# Changes with these triggers are excluded from every metric.
EXCLUDED_TRIGGERS = ("carryover", "manual")


class StateChange(BaseModel):
    id: str
    meeting_id: str
    item_id: str
    at: float  # seconds into the meeting; carryover is 0.0
    from_st: State
    to_st: State
    trigger: str  # evidence utterance | "carryover" | "manual"
    utterance_id: str | None


def is_relapse(change: StateChange) -> bool:
    """A relapse is any change that moves down the rank ladder."""
    return RANK[change.to_st] < RANK[change.from_st]
