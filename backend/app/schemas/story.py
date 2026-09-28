"""Book pages. Spec: guidelines/3_API_명세.md §3-3, prompts eval/story_prompt*.md."""
from typing import Literal, Optional

from pydantic import BaseModel


class Scene(BaseModel):
    index: int
    caption: str      # -어요 ending, 15 eojeol or fewer, names still masked
    keywords: str     # English, for asset lookup


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


class StoryResult(BaseModel):
    scenes: list[Scene]
