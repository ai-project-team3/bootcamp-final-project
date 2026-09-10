"""Latency marks. Six stages, measured as p95, not mean."""

from typing import Literal

from pydantic import BaseModel

Stage = Literal[
    "utterance_end",
    "stt_final",
    "match_done",
    "state_updated",
    "state_rendered",
    "card_rendered",
]


class TimingMark(BaseModel):
    meeting_id: str
    utterance_id: str
    stage: Stage
    at: float
