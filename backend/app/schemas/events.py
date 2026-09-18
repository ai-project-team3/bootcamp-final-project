"""Session events. Spec: 노션 「기술개요」 §7.

`by` must survive to the report: mixing what the child said with what we
filled in is how a parent report starts lying.
"""
from typing import Literal, Optional
from pydantic import BaseModel

EventName = Literal[
    "story_start", "utterance", "slot_filled", "signal", "emotion_mention",
    "make", "image_request", "redraw", "mission", "friend_rating", "session_end",
]

Source = Literal["child", "card", "mascot"]


class Event(BaseModel):
    name: EventName
    at: float                     # seconds since story_start
    by: Optional[Source] = None   # required for utterance / slot_filled
    slot: Optional[str] = None
    payload: dict = {}
