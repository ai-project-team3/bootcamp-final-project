"""Cards are regenerated every turn and thrown away."""

from typing import Literal

from pydantic import BaseModel


class Card(BaseModel):
    meeting_id: str
    rank: Literal[1, 2]  # there is no rank 3
    item_id: str
    evidence: str  # verbatim evidence utterance
    evidence_t: float
    script: str | None  # LLM-written suggestion, rank 1 only
    created_at: float
