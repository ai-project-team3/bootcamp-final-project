"""Item catalogue and per-template item selection."""

from typing import Literal

from pydantic import BaseModel


class Slot(BaseModel):
    """One resolution condition of an item. Consumed by role 3."""

    key: str  # "amount"
    label: str  # "금액"
    cues: list[str]  # 3 examples meaning the slot is satisfied
    blockers: list[str] = []  # 3 examples meaning it was cancelled - drives relapse


class Item(BaseModel):
    """One row of the global catalogue. The id is stable across templates."""

    id: str  # "budget_approval" - english snake_case
    label: str  # "예산 승인" - korean
    aliases: list[str]  # 5 examples meaning this item came up
    keywords: list[str] = []
    slots: list[Slot]  # layer 1: 1-4, layer 2: 0


class TemplateItem(BaseModel):
    item_id: str
    layer: Literal[1, 2]  # 1 objection / 2 required item
    default_on: bool
    priority: int  # lower sorts higher


class Template(BaseModel):
    id: str
    label: str
    items: list[TemplateItem]


class OffListMention(BaseModel):
    """An utterance that matches nothing. Kept verbatim, never classified."""

    meeting_id: str
    t: float
    raw: str
