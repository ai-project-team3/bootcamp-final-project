"""Pipeline entry point. Every input source produces this shape."""

from typing import Literal

from pydantic import BaseModel


class Utterance(BaseModel):
    id: str
    meeting_id: str
    speaker: Literal["me", "them", "unknown"]
    text: str
    t_start: float  # seconds from meeting start
    t_end: float
    confidence: float  # 0.0 - 1.0, speaker attribution confidence
