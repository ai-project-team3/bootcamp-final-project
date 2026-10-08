"""POST /partner — who sits with the child, from the answer to 「오늘은 누구랑 같이 이야기를 만들어?」(#303).

One Jev choice question (~0.7 s). The app keeps its word list (demo/Model.kt partnerIn) for when this
returns nothing — server off, Jev down, or an answer under the 0.6 floor — so this never blocks the start.
Only the kind comes back; what the child called the person (「할미」 · 「민수」) stays on the phone.
"""
import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ..config import settings
from ..filters.blocklist import is_blocked
from ..llm import jev

router = APIRouter()
log = logging.getLogger("otto.partner")


class PartnerRequest(BaseModel):
    utterance: str = Field(min_length=1, max_length=200)


class PartnerResult(BaseModel):
    # a PARTNERS key (mom · dad · aunt · grandma · grandpa · uncle · teacher · sibling · friend),
    # solo, unknown — or None when unsure; the app then reads its own word list
    kind: str | None = None
    confidence: float | None = None


@router.post("/partner", response_model=PartnerResult)
async def partner(req: PartnerRequest) -> PartnerResult:
    if is_blocked(req.utterance):
        return PartnerResult()
    if settings.mock:
        return PartnerResult(kind="unknown")
    try:
        kind, conf, seconds = await jev.partner(req.utterance)
    except jev.JevError as e:
        log.warning("partner jev failed: %s", e)
        raise HTTPException(502, str(e)) from e
    log.info("partner jev %.2fs → %s", seconds, kind)
    return PartnerResult(kind=kind, confidence=conf)
