"""Book pages. Spec: guidelines/3_API_명세.md §3-3, prompts eval/story_prompt*.md."""
from typing import Literal, Optional

from pydantic import BaseModel, Field


# The app's PageKind (StoryBank.kt) — closed, like slot names. The app owns the
# book's shape: template → pages → which page carries which mission (09-29, 최민우:
# templates run 6-8 pages and put missions on different pages, so the server must
# not guess them from a fixed six).
PageKindName = Literal["COVER", "DEPART", "SHAKE", "MEET", "TALK", "JOURNEY",
                       "FAIL", "RUB", "DRAG", "TOGETHER"]
# docs/미션_구상.md §3. The app picks the mission (§4 table); the server only
# writes the page up to it. The result line ("불이 다 꺼졌어요") stays the app's.
MissionId = Literal["A1", "A2", "A3", "A4", "A5", "A6", "B1", "B2", "B3",
                    "C1", "C2", "D1", "D2", "D3", "E1", "E2"]


class Page(BaseModel):
    kind: PageKindName
    mission: Optional[MissionId] = None


class Scene(BaseModel):
    index: int
    caption: str      # -어요 ending, 15 eojeol or fewer, names still masked
    keywords: str     # English, for asset lookup
    kind: Optional[PageKindName] = None   # stamped by the server from the request, never the model's


class StoryRequest(BaseModel):
    # story → six scenes (story_prompt.md); diary · coop → a record of the day
    # (story_prompt_diary.md, 진웅's §5-1 rules). Defaults to story for old callers.
    mode: Literal["story", "diary", "coop"] = "story"
    slots: dict[str, Optional[str]]
    # who filled each slot: child · card · mascot. A mascot-filled ending must not
    # get "마침내" (rule 5: the book must not pass our words off as the child's)
    slot_by: dict[str, str] = {}
    # the closing wish, verbatim (diary · coop). Kept apart from `extra` so the
    # prompt can leave its tense alone
    keep: Optional[str] = None
    template: Optional[str] = None
    level: Optional[str] = None
    # the page plan, in order. When given, the book has exactly these pages;
    # when absent, the old shape (story: six · diary/coop: 3-6) for old callers
    pages: Optional[list[Page]] = Field(default=None, min_length=1, max_length=10)


class StoryResult(BaseModel):
    scenes: list[Scene]
