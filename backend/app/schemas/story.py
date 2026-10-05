"""Book pages. Spec: guidelines/3_API_명세.md §3-3, prompts eval/story_prompt*.md."""
from typing import Annotated, Literal, Optional

from pydantic import BaseModel, Field


# The app's PageKind (StoryBank.kt) — closed, like slot names. The app owns the
# book's shape: template → pages → which page carries which mission (09-29, 최민우:
# templates run 6-8 pages and put missions on different pages, so the server must
# not guess them from a fixed six).
PageKindName = Literal["COVER", "DEPART", "SHAKE", "MEET", "TALK", "JOURNEY",
                       "FAIL", "RUB", "DRAG", "TOGETHER"]
# docs/미션_구상.md §3. The app picks the mission (§4 table); the server only
# writes the page up to it. The result line ("불이 다 꺼졌어요") stays the app's.
MissionId = Literal["A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "B1", "B2", "B3",
                    "C1", "C2", "C3", "D1", "D2", "D3", "D4", "D5", "E1", "E2"]


class Page(BaseModel):
    kind: PageKindName
    mission: Optional[MissionId] = None
    # the object the mission uses — the page sets up that same thing (10-05: the page said a balloon, the
    # mission handed a shiny stone). The app's word, short; checked like any text that reaches the model
    prop: Optional[str] = Field(default=None, max_length=20)


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
    # story: the template letter. coop: the story the parent picked, as words
    # ("직업 · 소방관 · 곧 체험해요") — context for the model, never a page plan
    template: Optional[str] = None
    # coop only: why the parent picked it (CoopTemplates.kt CoopReason) — sets the book's
    # tense. done = it happened · soon = it is coming · dream = imagined. None = done (#52)
    reason: Optional[Literal["done", "soon", "dream"]] = None
    level: Optional[str] = None
    # coop only (#113): the spots inside the picked item (CoopTemplates.kt COOP_ITEMS spots —
    # "소방차 차고 · 출동 준비실"). Scenery for the pages and keywords, never an event: the
    # item's trouble/cause/fix choices are not sent, the child did not say them
    stage: Optional[list[Annotated[str, Field(min_length=1, max_length=12)]]] = Field(default=None, max_length=5)
    # the page plan, in order. When given, the book has exactly these pages;
    # when absent, the old shape (story: six · diary/coop: 3-6) for old callers.
    # Today's templates run 6-8 pages, but that is the app's choice, not the API's
    # (최민우 09-29). The cap only stops a runaway request from buying a huge book.
    pages: Optional[list[Page]] = Field(default=None, min_length=1, max_length=30)


class StoryResult(BaseModel):
    # the cover line (10-05 · #86: the app no longer asks the child for one). Optional — an unsafe
    # or empty title is dropped and the app keeps its own, the book itself still goes
    title: Optional[str] = None
    scenes: list[Scene]
