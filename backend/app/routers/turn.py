"""POST /turn — one mascot turn: the verdict, then what the mascot says.

Two LLM calls in a row, kept separate on purpose (guidelines/7 §2): a failed
verdict still gets a line (the question just continues the story), and a failed
or unsafe line still returns the verdict. Only when both are missing is it a 502.
The phone fills any missing half from its script (spec §3-0).

The prompt is read from eval/line_prompt.md, not copied (one thing in one place).
"""
import asyncio
import json
import logging
import time
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import REPO, settings
from ..dialogue import policy, recipes
from ..dialogue.decide import Decision, decide, state_text
from ..dialogue.decide import mock as mock_decision
from ..filters.blocklist import eojeol, has_unknown_placeholder, is_blocked
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import load_schema, system_block
from ..schemas.judge import SLOT_NAMES, JudgeResult
from ..schemas.turn import Act, Line, TurnRequest, TurnResult
from . import judge

router = APIRouter()
log = logging.getLogger("turn")
EVAL = REPO / "eval"

# guidelines/7 §3: ack 8 eojeol, question 12. Logged, not enforced — a long line
# is not unsafe, and throwing it away would leave the child with the script instead.
_LIMITS = {"ack": 8, "expand": 10, "question": 12}


@lru_cache(maxsize=None)
def system(mode: str | None = None) -> str:
    """The line prompt for [mode] — only that mode's piece (#121). No mode = every piece."""
    return system_block(EVAL / "line_prompt.md", mode)


@lru_cache(maxsize=None)
def system_for(mode: str | None, act: Act | None) -> str:
    """The line prompt, plus how to take in a reply that is not an answer — only on a turn with an act (#323)."""
    if act is None:
        return system(mode)
    return f"{system(mode)}\n\n{system_block(EVAL / 'line_act.md')}"


def schema() -> dict:
    return load_schema("line_schema.json")


def user(req: TurnRequest, v: JudgeResult | None, recipe: recipes.Recipe | None = None) -> str:
    """The §3 inputs. Without a verdict, the slots are empty and the model just continues.

    With a [recipe] (#323) the act and its context lines are added, and the question's slot is the
    recipe's when it names one. A plain turn's input is byte for byte what it was."""
    values = [x for x in ((v.value_1, v.value_2) if v else ()) if x]
    next_slot = (recipe.question_slot if recipe and recipe.question_slot else None) or (v.next_slot if v else None)
    # what the child has already settled — 10-01 #50: without it the line model asked about
    # places and characters that were not in the story, and the talk drifted
    so_far = " · ".join(f"{k}={val}" for k, val in req.slots.items() if val and str(val).strip())
    return (
        f"story_so_far:{so_far}\n"
        f"mode:{req.mode}\n"
        f"ask:{'true' if req.ask else 'false'}\n"
        f"level:{req.level or ''}\n"
        f"template:{req.template or ''}\n"
        # coop: the tense of the question follows why the parent picked the story (#53 C)
        + (f"reason:{req.reason or 'done'}\n" if req.mode == "coop" else "")
        + f"question_just_asked:{req.question}\n"
        f"utterance:{req.utterance}\n"
        f"value:{' / '.join(values)}\n"
        f"next_slot:{next_slot or ''}\n"
        f"next_reason:{(v.next_reason if v else None) or ''}\n"
        f"unclear_of:{(v.unclear_of if v and v.unclear else None) or ''}\n"
        f"story_ready:{'true' if v and v.story_ready else 'false'}"
        + (f"\nact:{recipe.act}\n{recipe.context}" if recipe else "")
    )


def check(line: Line) -> str | None:
    """Why this line must not reach the child, or None. An unsafe line is dropped, not patched."""
    for field in ("ack", "expand", "question"):
        text = getattr(line, field)
        if not text:
            continue
        if is_blocked(text):
            return f"blocked word in {field}"
        if has_unknown_placeholder(text):
            return f"unknown placeholder in {field}"
        words = eojeol(text)
        if len(words) > _LIMITS[field]:
            log.info("line %s over %d eojeol: %d", field, _LIMITS[field], len(words))
    # an unsafe option is dropped on its own — the line and the other options still stand
    if line.options:
        kept = [o.strip() for o in line.options
                if o and o.strip() and not is_blocked(o) and not has_unknown_placeholder(o)]
        line.options = list(dict.fromkeys(kept))[:3] or None
    return None


def shape(line: Line, req: TurnRequest, v: JudgeResult | None) -> Line:
    """The rules win over the model on when there is no question."""
    if not req.ask or (v is not None and v.story_ready):
        line.question = None
    # options only make sense for a question the mascot asks; never in diary (#79)
    if line.question is None or req.mode == "diary":
        line.options = None
    return line


def mock_line(req: TurnRequest, v: JudgeResult | None) -> Line:
    nxt = v.next_slot if v else None
    return Line(ack="그랬구나!", expand=None, question=f"{nxt} 이야기를 해 줄래?" if nxt else "그다음엔?",
                options=["첫 번째 후보", "두 번째 후보", "세 번째 후보"])


LINE_MIN_S = 3.0      # less than this left after the judge: send the verdict alone


async def run_line(req: TurnRequest, v: JudgeResult | None, budget_s: float = 30.0,
                   recipe: recipes.Recipe | None = None) -> Line | None:
    if v is not None and v.reason == "blocked_by_filter":
        return None          # a blocked utterance never leaves for the LLM, here either
    act = recipe.act if recipe else None
    if settings.mock:
        line = shape(mock_line(req, v), req, v)
        line.act = act
        return line
    if budget_s < LINE_MIN_S:
        log.warning("line skipped: %.1fs left of the /turn deadline", budget_s)
        return None          # the phone asks its own next question; the verdict still counts
    try:
        raw = await complete(system_for(req.mode, act), user(req, v, recipe), schema(), name="mascot_line",
                             effort=settings.llm_effort_line, timeout_s=budget_s)
    except LLMError as e:
        log.warning("line failed: %s", e)
        return None
    line = Line.model_validate(raw)
    why = check(line)
    if why:
        log.warning("line rejected: %s", why)
        return None
    line.act = act
    return shape(line, req, v)


def apply_act(act: Act | None, d: Decision | None, req: TurnRequest,
              v: JudgeResult | None) -> tuple[JudgeResult | None, list[str], recipes.Recipe | None]:
    """The rules after an act is known — the same whether the rules (R) or the line model (M) picked it."""
    retract = policy.retract_for(act, d, req)
    recipe = recipes.build(act, req, d, retract) if act else None
    if act and v is not None and v.reason != "blocked_by_filter":
        v = policy.trim_verdict(act, d, v)
        # the app asks what next_slot names, so the act's slot goes there (a retracted slot is empty
        # again on the phone once it applies `retract`)
        if recipe.question_slot:
            v = v.model_copy(update={"next_slot": recipe.question_slot})
    if act:
        log.info("dialogue act %s · retract %s", act, retract)
    return v, retract, recipe


# --- M: the line model picks the act itself (measurement only · #323 「규칙 대 LLM」) ---

_INTENT_OF = {act: intent for intent, act in policy.ACTS.items() if act}


@lru_cache(maxsize=None)
def choose_schema() -> dict:
    s = json.loads(json.dumps(schema()))
    s["properties"]["act"] = {"type": ["string", "null"], "enum": [*_INTENT_OF, None]}
    s["properties"]["target"] = {"type": ["string", "null"], "enum": [*SLOT_NAMES, None]}
    s["properties"]["new_value"] = {"type": "boolean"}
    s["required"] = [*s["required"], "act", "target", "new_value"]
    return s


@lru_cache(maxsize=None)
def choose_system(mode: str | None) -> str:
    return f"{system_for(mode, 'repair')}\n\n{system_block(EVAL / 'line_act_choose.md')}"


async def run_line_choosing(req: TurnRequest, v: JudgeResult | None,
                            budget_s: float) -> tuple[Line | None, Act | None, Decision | None]:
    """One line call that also picks the act. Same input as a plain turn plus the decider's [대화]."""
    if v is not None and v.reason == "blocked_by_filter":
        return None, None, None
    if settings.mock:
        d = mock_decision(req)
        act = policy.ACTS.get(d.intent)
        line = shape(mock_line(req, v), req, v)
        line.act = act
        return line, act, d
    if budget_s < LINE_MIN_S:
        return None, None, None
    try:
        raw = await complete(choose_system(req.mode), f"{user(req, v)}\n{state_text(req)}", choose_schema(),
                             name="mascot_line_choose", effort=settings.llm_effort_line, timeout_s=budget_s)
    except LLMError as e:
        log.warning("choosing line failed: %s", e)
        return None, None, None
    act = raw.pop("act", None)
    d = Decision(_INTENT_OF.get(act, "answer"), None, raw.pop("target", None), None, bool(raw.pop("new_value", False)))
    line = Line.model_validate(raw)
    if check(line):
        return None, act, d
    line.act = act
    return shape(line, req, v), act, d


async def _judge(req: TurnRequest) -> JudgeResult | None:
    try:
        return await judge.run(req)
    except LLMError as e:
        log.warning("judge failed in /turn: %s", e)
        return None


@router.post("/turn", response_model=TurnResult)
async def turn(req: TurnRequest) -> TurnResult:
    # one deadline for the whole turn, under the phone's 30 s: the judge first (≤ 18 s),
    # the line gets what is left — a slow vendor costs the line, never the verdict
    t0 = time.monotonic()
    if req.history and settings.dialogue_policy == "llm":
        v = await _judge(req)
        line, act, d = await run_line_choosing(req, v, settings.turn_deadline_s - (time.monotonic() - t0))
        v, retract, _ = apply_act(act, d, req, v)
        if v is None and line is None:
            raise HTTPException(502, "judge and line both failed")
        return TurnResult(judge=v, line=line, retract=retract)

    # with history (#323) the quick decider runs next to the judge, so the turn waits for the slower
    # of the two, not both. It never raises: no opinion = the turn runs as before
    v, d = await asyncio.gather(_judge(req), decide(req), return_exceptions=True)
    if isinstance(v, BaseException):
        raise v
    if isinstance(d, BaseException):
        log.warning("dialogue decider raised in /turn: %s", d)
        d = None

    act = policy.act_for(d)
    v, retract, recipe = apply_act(act, d, req, v)
    line = await run_line(req, v, settings.turn_deadline_s - (time.monotonic() - t0), recipe)
    if v is None and line is None:
        raise HTTPException(502, "judge and line both failed")
    return TurnResult(judge=v, line=line, retract=retract)
