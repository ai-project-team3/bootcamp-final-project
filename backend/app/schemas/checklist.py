"""Layer 2. Two states, monotonic, no relapse, no slots."""

from datetime import datetime

from pydantic import BaseModel


class ChecklistState(BaseModel):
    meeting_id: str
    item_id: str
    mentioned: bool  # auto-detected
    mentioned_at: float | None
    evidence: str | None  # verbatim evidence utterance
    confirmed: bool = False  # a human confirms on the meeting-end screen
    confirmed_at: datetime | None = None
