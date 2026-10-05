"""POST /turn — one mascot turn: the verdict, then what the mascot says.

Two LLM calls in a row, kept separate on purpose (guidelines/7 §2): a failed
verdict still gets a line (the question just continues the story), and a failed
or unsafe line still returns the verdict. Only when both are missing is it a 502.
The phone fills any missing half from its script (spec §3-0).

The prompt is read from eval/line_prompt.md, not copied (one thing in one place).
"""
import logging
import time
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import REPO, settings
from ..filters.blocklist import eojeol, has_unknown_placeholder, is_blocked
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import load_schema, system_block
from ..schemas.judge import JudgeResult
from ..schemas.turn import Line, TurnRequest, TurnResult
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


def schema() -> dict:
    return load_schema("line_schema.json")


def user(req: TurnRequest, v: JudgeResult | None) -> str:
    """The §3 inputs. Without a verdict, the slots are empty and the model just continues."""
    values = [x for x in ((v.value_1, v.value_2) if v else ()) if x]
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
        f"next_slot:{(v.next_slot if v else None) or ''}\n"
        f"next_reason:{(v.next_reason if v else None) or ''}\n"
        f"unclear_of:{(v.unclear_of if v and v.unclear else None) or ''}\n"
        f"story_ready:{'true' if v and v.story_ready else 'false'}"
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


async def run_line(req: TurnRequest, v: JudgeResult | None, budget_s: float = 30.0) -> Line | None:
    if v is not None and v.reason == "blocked_by_filter":
        return None          # a blocked utterance never leaves for the LLM, here either
    if settings.mock:
        return shape(mock_line(req, v), req, v)
    if budget_s < LINE_MIN_S:
        log.warning("line skipped: %.1fs left of the /turn deadline", budget_s)
        return None          # the phone asks its own next question; the verdict still counts
    try:
        raw = await complete(system(req.mode), user(req, v), schema(), name="mascot_line",
                             effort=settings.llm_effort_line, timeout_s=budget_s)
    except LLMError as e:
        log.warning("line failed: %s", e)
        return None
    line = Line.model_validate(raw)
    why = check(line)
    if why:
        log.warning("line rejected: %s", why)
        return None
    return shape(line, req, v)


@router.post("/turn", response_model=TurnResult)
async def turn(req: TurnRequest) -> TurnResult:
    # one deadline for the whole turn, under the phone's 30 s: the judge first (≤ 18 s),
    # the line gets what is left — a slow vendor costs the line, never the verdict
    t0 = time.monotonic()
    try:
        v = await judge.run(req)
    except LLMError as e:
        log.warning("judge failed in /turn: %s", e)
        v = None
    line = await run_line(req, v, settings.turn_deadline_s - (time.monotonic() - t0))
    if v is None and line is None:
        raise HTTPException(502, "judge and line both failed")
    return TurnResult(judge=v, line=line)
