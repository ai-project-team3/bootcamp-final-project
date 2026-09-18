"""POST /judge — one call per turn. ~16 per session.

The model reads the whole slot state and decides what to ask next; the rules
here keep the story from wandering. Spec: guidelines/7_프롬프트.md §2.
"""
from fastapi import APIRouter
from ..schemas.judge import JudgeRequest, JudgeResult, SLOT_NAMES

router = APIRouter()


def enforce(result: JudgeResult, req: JudgeRequest) -> JudgeResult:
    """Three guardrails the rules keep. Everything else is the model's call."""
    for field in ("slot_1", "slot_2", "next_slot", "no_longer_needed"):
        name = getattr(result, field)
        if name is not None and name not in SLOT_NAMES:
            setattr(result, field, None)

    # never ask again for a slot that already has a value (confirming an
    # unclear word is the one exception)
    if result.next_slot and req.slots.get(result.next_slot) and not result.unclear:
        result.next_slot = None

    return result


@router.post("/judge", response_model=JudgeResult)
async def judge(req: JudgeRequest) -> JudgeResult:
    raise NotImplementedError("W1: implement after the model is chosen")
