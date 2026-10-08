"""Pictures during the session. Spec: guidelines/3 §3-3-1 · rule 8."""
from typing import Literal, Optional

from pydantic import BaseModel, model_validator


MAX_DRAWING_B64 = 4_000_000             # ~3 MB PNG — a phone-sized piece is far smaller


class ImageRequest(BaseModel):
    kind: Literal["background", "character", "redraw"] = "background"
    place: Optional[str] = None          # background: the place slot as the child said it, names masked
    description: Optional[str] = None    # character · redraw: who / what it is, names masked
    # redraw only: the child's piece (transparent background, only what was drawn).
    # ⚠️ Never logged, never written, never sent to the (external) check — guidelines/1 §1-5
    png_base64: Optional[str] = None
    mode: Literal["story", "diary", "coop"] = "story"
    # redraw only (#168 · 10-06): "background" = the diary's ground · sky · sea lines, sent as the whole board.
    # Drawn as a wide colored-pencil scene and not cut out. None = a piece, as before
    role: Optional[Literal["object", "background"]] = None
    # The book's art style (10-07 종훈 · #223 그림체). felt = the app's own wool felt; crayon = a child's crayon
    # drawing. Only the world changes (결정 27) — the diary's colored-pencil redraw keeps its own style
    style: Literal["felt", "crayon"] = "felt"

    @model_validator(mode="after")
    def _has_words(self):
        words = self.place if self.kind == "background" else self.description
        if not (words or "").strip():
            raise ValueError(f"{self.kind} needs {'place' if self.kind == 'background' else 'description'}")
        if self.kind == "redraw":
            if not self.png_base64:
                raise ValueError("redraw needs png_base64")
            if len(self.png_base64) > MAX_DRAWING_B64:
                raise ValueError("drawing too large")
        return self

    def __repr__(self) -> str:          # keep the drawing out of any log line or traceback
        return f"ImageRequest(kind={self.kind!r}, mode={self.mode!r}, style={self.style!r})"

    __str__ = __repr__

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
    # character only (never redraw): which skeleton the app should put in it (docs/캐릭터_생성_규격.md)
    rig: Optional[Literal["human", "quad", "blob"]] = None
