"""Pictures during the session. Spec: guidelines/3 §3-3-1 · rule 8."""
from typing import Literal, Optional

from pydantic import BaseModel, model_validator


class ImageRequest(BaseModel):
    kind: Literal["background", "character"] = "background"
    place: Optional[str] = None          # background: the place slot as the child said it, names masked
    description: Optional[str] = None    # character: who the child described, names masked
    mode: Literal["story", "diary", "coop"] = "story"

    @model_validator(mode="after")
    def _has_words(self):
        words = self.place if self.kind == "background" else self.description
        if not (words or "").strip():
            raise ValueError(f"{self.kind} needs {'place' if self.kind == 'background' else 'description'}")
        return self

    @property
    def words(self) -> str:
        return (self.place if self.kind == "background" else self.description) or ""


class ImageResult(BaseModel):
    # preset = true → use the app's own picture. Always a 200: a missing picture
    # is a normal outcome here, not an error (rule 8: 15 s → preset).
    preset: bool
    reason: str
    scene: Optional[str] = None                  # the English words that were drawn
    png_base64: Optional[str] = None
    # character only: which skeleton the app should put in it (docs/캐릭터_생성_규격.md)
    rig: Optional[Literal["human", "quad", "blob"]] = None
