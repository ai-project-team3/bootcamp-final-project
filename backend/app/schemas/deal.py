"""A deal is the unit that ties meeting rounds together."""

from datetime import datetime

from pydantic import BaseModel


class Deal(BaseModel):
    id: str
    label: str  # "A사 신규 도입" / "김OO님 종신보험"
    template_id: str
    counterpart: str  # display name of the other party
    company: str | None  # absent for individual sales
    external_ref: str | None  # room for a CRM id later
    voice_ref: str | None  # counterpart voice embedding, reused in person
    created_at: datetime


class Meeting(BaseModel):
    id: str
    deal_id: str | None  # None means not linked yet
    seq: int  # round number, derived from meeting order within the deal
    template_id: str
    recording_notice: bool
    session_id: str  # reconnection key, deliberately separate from deal_id
    started_at: datetime
    ended_at: datetime | None
