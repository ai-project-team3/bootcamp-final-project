"""Book pages. Spec: docs/프롬프트_모음.md §5."""
from pydantic import BaseModel


class Scene(BaseModel):
    index: int
    caption: str      # -어요 ending, 15 eojeol or fewer, names still masked
    keywords: str     # English, for asset lookup


class StoryRequest(BaseModel):
    slots: dict[str, str | None]
    template: str
    level: str | None = None


class StoryResult(BaseModel):
    scenes: list[Scene]
