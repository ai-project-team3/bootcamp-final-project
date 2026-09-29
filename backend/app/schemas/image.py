"""Background picture during the session. Spec: guidelines/3 §3-3-1 · rule 8."""
from typing import Literal, Optional

from pydantic import BaseModel


class ImageRequest(BaseModel):
    kind: Literal["background"] = "background"   # characters come next (need a cut-out step)
    place: str                                   # the place slot as the child said it, names masked
    mode: Literal["story", "diary", "coop"] = "story"


class ImageResult(BaseModel):
    # preset = true → use the app's own picture. Always a 200: a missing picture
    # is a normal outcome here, not an error (rule 8: 15 s → preset).
    preset: bool
    reason: str
    scene: Optional[str] = None                  # the English words that were drawn
    png_base64: Optional[str] = None
