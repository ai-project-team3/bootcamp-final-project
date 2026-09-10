"""Co-attendees receive state and signals. Never audio."""

from pydantic import BaseModel


class BackchannelSignal(BaseModel):
    meeting_id: str
    item_id: str  # the item the co-attendee tapped
    text: str  # fixed phrase generated from the item
    at: float
    reflected: bool = False  # did the presenter go on to address it
