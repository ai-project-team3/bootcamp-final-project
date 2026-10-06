"""POST /judge — the verdict alone. The app calls /turn (judge + line); this route serves the tests,
the eval harness and judge.run() for /turn.

The model reads the whole slot state and decides what to ask next; the rules
here keep the story from wandering. Spec: guidelines/7_프롬프트.md §2.
"""
from fastapi import APIRouter, HTTPException

from ..config import settings
from ..filters.blocklist import is_blocked
import logging

from ..llm import jev, judge_prompt
from ..llm.client import LLMError, complete
from ..schemas.judge import JudgeRequest, JudgeResult, SLOT_NAMES

router = APIRouter()
log = logging.getLogger("otto.judge")

# Order the mock walks when it picks the next slot. Only for MOCK=1 — the real
# order is the model's call (rule 2: required slots are not fixed).
_MOCK_ORDER = ("place", "problem", "reaction", "cause", "solution")


def enforce(result: JudgeResult, req: JudgeRequest) -> JudgeResult:
    """Three guardrails the rules keep. Everything else is the model's call."""
    for field in ("slot_1", "slot_2", "next_slot", "no_longer_needed"):
        name = getattr(result, field)
        if name is not None and name not in SLOT_NAMES:
            setattr(result, field, None)

    # the book's title is asked by the app after the book is made, never as the next turn —
    # the model picked it once the story was full, and the app looped on 「더 들려줄래?」(#156 · 10-06)
    if result.next_slot == "title":
        result.next_slot = None

    # never ask again for a slot that already has a value (confirming an
    # unclear word is the one exception)
    if result.next_slot and req.slots.get(result.next_slot) and not result.unclear:
        result.next_slot = None

    return result


def blocked() -> JudgeResult:
    """Guardrail 3: a blocked utterance never reaches the LLM. Nothing is filled."""
    return JudgeResult(reason="blocked_by_filter", unclear=True)


def mock(req: JudgeRequest) -> JudgeResult:
    """Fixed, spec-shaped answer: the utterance fills the asked slot, then the next empty one."""
    slot = req.asked_slot if req.asked_slot in SLOT_NAMES else "extra"
    filled = {**req.slots, slot: req.utterance}
    nxt = next((s for s in _MOCK_ORDER if not filled.get(s)), None)
    return JudgeResult(
        reason="mock", slot_1=slot, value_1=req.utterance,
        next_slot=nxt, next_reason="mock order", story_ready=nxt is None,
    )


async def run(req: JudgeRequest) -> JudgeResult:
    """The verdict with its guardrails. Raises LLMError; /judge and /turn both call this."""
    if is_blocked(req.utterance):
        return blocked()
    if settings.mock:
        return enforce(mock(req), req)
    if req.mode in {m.strip() for m in settings.judge_jev_modes.split(",") if m.strip()}:
        try:
            raw = await jev.judge(judge_prompt.system(), judge_prompt.user(req), req.utterance)
            log.info("judge jev %.2fs", raw.pop("_seconds", 0.0))
            return enforce(JudgeResult.model_validate(raw), req)
        except jev.JevError as e:
            log.warning("judge jev failed, luna instead: %s", e)
    raw = await complete(judge_prompt.system(req.mode), judge_prompt.user(req), judge_prompt.schema(),
                         effort=settings.llm_effort_judge, timeout_s=settings.judge_deadline_s)
    return enforce(JudgeResult.model_validate(raw), req)


@router.post("/judge", response_model=JudgeResult)
async def judge(req: JudgeRequest) -> JudgeResult:
    try:
        return await run(req)
    except LLMError as e:
        raise HTTPException(502, str(e)) from e
