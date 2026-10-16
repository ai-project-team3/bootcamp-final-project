"""POST /judge — the verdict alone. The app calls /turn (judge + line); this route serves the tests,
the eval harness and judge.run() for /turn.

The model reads the whole slot state and decides what to ask next; the rules
here keep the story from wandering. Spec: guidelines/7_프롬프트.md §2.
"""
import re

from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from ..config import settings
from ..filters.blocklist import is_blocked
import logging

from ..llm import jev, judge_prompt, judge_rules, laya
from ..llm.client import LLMError, complete
from ..schemas.judge import JudgeRequest, JudgeResult, SLOT_NAMES

router = APIRouter()
log = logging.getLogger("otto.judge")

# Order the mock walks when it picks the next slot. Otherwise the order is the model's call
# (rule 2: required slots are not fixed); the one other use is standing in for an adult slot
# nobody can answer (#303).
_BEAT_ORDER = ("place", "problem", "reaction", "cause", "solution")


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

    # nobody sits with the child (#303): the adult slot is a real adult's line, so there is no one to
    # ask and nothing to fill. Dropping next_slot alone left the app re-asking 「조금 더 들려줄래?」
    # every turn, so the next empty story beat is asked instead; a word filed under adult keeps its
    # text in extra (rule 1)
    if req.partner == "none":
        for slot, value in (("slot_1", "value_1"), ("slot_2", "value_2")):
            if getattr(result, slot) == "adult":
                setattr(result, slot, "extra" if getattr(result, value) else None)
        if result.next_slot == "adult":
            result.next_slot = next((s for s in _BEAT_ORDER if not req.slots.get(s)), None)

    return result


def blocked() -> JudgeResult:
    """Guardrail 3: a blocked utterance never reaches the LLM. Nothing is filled."""
    return JudgeResult(reason="blocked_by_filter", unclear=True)


def mock(req: JudgeRequest) -> JudgeResult:
    """Fixed, spec-shaped answer: the utterance fills the asked slot, then the next empty one."""
    slot = req.asked_slot if req.asked_slot in SLOT_NAMES else "extra"
    filled = {**req.slots, slot: req.utterance}
    nxt = next((s for s in _BEAT_ORDER if not filled.get(s)), None)
    return JudgeResult(
        reason="mock", slot_1=slot, value_1=req.utterance,
        next_slot=nxt, next_reason="mock order", story_ready=nxt is None,
    )


def _modes(setting: str) -> set[str]:
    return {m.strip() for m in setting.split(",") if m.strip()}


def _low_pick(picked: str) -> tuple[str, float] | None:
    """Jev's own slot and confidence from its raw 「slot confidence」 log label, when it is a slot under the floor."""
    slot, _, conf = (picked or "").partition(" ")
    try:
        c = float(conf)
    except ValueError:
        return None
    return (slot, c) if c < jev.FLOOR and slot in SLOT_NAMES else None


# The child denies doing it (「아니야, 쿠키 안 구웠어」 · 「축구장 안 갔어」): no slot to fill, however sure both models are.
# On the 10-15 re-count every such sentence in the 203 + device set had an empty gold, and these were three of the
# five extra wrong fills under the old agree rule; 「안 남았어」 · 「안 젖었어」 (real answers) do not match.
_DENIAL = re.compile(r"아니(야|에요|거든)|아닌데|안 ?(갔|했|해|갈|할)")


async def _second_opinion(req: JudgeRequest, result: JudgeResult, picked: str) -> JudgeResult:
    """A diary · coop turn Jev answered under its floor: ask luna instead of handing it back to the child
    (settings.jev_low_confidence). Any luna failure or a slow answer keeps the empty slot — the app asks again."""
    low = _low_pick(picked)
    if low is None:
        return result
    pick, conf = low
    agree = settings.jev_low_confidence == "agree"
    if agree and (conf < settings.jev_low_confidence_min or _DENIAL.search(req.utterance)):
        log.info("judge jev low confidence %s %.2f · below %.2f or a denial, ask again", pick, conf,
                 settings.jev_low_confidence_min)
        return result
    try:
        raw = await complete(judge_prompt.system(req.mode), judge_prompt.user(req), judge_prompt.schema(),
                             effort=settings.llm_effort_judge, timeout_s=settings.jev_low_confidence_deadline_s)
        luna = enforce(JudgeResult.model_validate(raw), req)
    except (LLMError, ValidationError) as e:
        log.warning("judge jev low confidence: luna failed, ask again: %s", e)
        return result
    if not agree:
        log.info("judge jev low confidence %s → luna %s", pick, luna.slot_1)
        return luna
    # luna's first slot only: its second one named 「아빠랑 사자 있는 데 갔어」 a companion when the place was asked
    if pick != luna.slot_1:
        log.info("judge jev low confidence %s · luna %s/%s · no agreement, ask again", pick, luna.slot_1, luna.slot_2)
        return result
    # both name it: Jev's pick, the child's words as Jev keeps them, and the diary · coop rules again for what is next
    out = result.model_dump()
    out.update(slot_1=pick, value_1=" ".join(jev.mask_names(req.utterance, req.names).split()[:8]))
    out = judge_rules.apply(out, req, out.get("next_slot"))
    log.info("judge jev low confidence %s · luna agrees", pick)
    return enforce(JudgeResult.model_validate(out), req)


async def run(req: JudgeRequest) -> JudgeResult:
    """The verdict with its guardrails. Raises LLMError; /judge and /turn both call this."""
    if is_blocked(req.utterance):
        return blocked()
    if settings.mock:
        return enforce(mock(req), req)
    if req.mode in _modes(settings.judge_laya_modes):
        try:
            # our own model on our own machine — nothing leaves it (10-08 · app/llm/laya.py)
            raw = await laya.judge(req)
            log.info("judge laya %.2fs", raw.pop("_seconds", 0.0))
            result = enforce(JudgeResult.model_validate(raw), req)
            if req.mode != "story" or result.story_ready or result.next_slot is not None:
                return result
            log.warning("story judge laya has no ending or next slot; next judge instead")
        except (laya.LayaError, ValidationError) as e:
            # ValidationError: an answer JudgeResult won't take — still the next judge's turn, never a 500
            log.warning("judge laya failed, next judge instead: %s", e)
    if req.mode in _modes(settings.judge_jev_modes):
        try:
            # names stay with us — TypeSafe gets placeholders (10-06); the app unmasks value_1
            raw = await jev.judge(judge_prompt.system(), jev.mask_names(judge_prompt.user(req), req.names),
                                  jev.mask_names(req.utterance, req.names), mode=req.mode)
            # labels only, no words
            picked = raw.pop("_slot_1", "-")
            log.info("judge jev %.2fs · %s · slot_1 %s", raw.pop("_seconds", 0.0), req.mode, picked)
            # diary · coop: the ending and the next slot are rules, as on Laya (judge_rules.py · 12 of the 13
            # labelled mode cases) — Jev's own story_ready / next_slot there are guesses about a diary order it
            # never saw. A story keeps Jev's verdict and the #156 recovery below: its required slots depend on
            # the template, which the fixed rule list does not know — only an open next slot is filled in
            if req.mode != "story":
                raw = judge_rules.apply(raw, req, raw.get("next_slot"))
            result = enforce(JudgeResult.model_validate(raw), req)
            if req.mode != "story" and result.slot_1 is None and settings.jev_low_confidence:
                result = await _second_opinion(req, result, picked)
            if req.mode == "story" and not result.story_ready and result.next_slot is None:
                # Jev left the next slot open or enforce() wiped it: ask the next empty beat instead of a full luna
                # judge (2~3 s and luna's price a turn — 10-12 device, most story turns went there). Once the
                # ending is in, whether the story is over stays with the recovery below (judge_rules.story_fill)
                result.next_slot = judge_rules.story_fill(req, result.slot_1)
                if result.next_slot:
                    log.info("story judge jev: next slot by rule → %s", result.next_slot)
            if req.mode != "story" or result.story_ready or result.next_slot is not None:
                return result
            # A dropped/invalid choice is still a successful Jev response. Without
            # an ending or a next slot it strands the story in an open-ended line
            # loop (#156). Let the existing full judge choose, never force ready.
            log.warning("story judge jev has no ending or next slot; full judge instead")
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
