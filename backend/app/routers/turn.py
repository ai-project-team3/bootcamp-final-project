"""POST /turn — one mascot turn: the verdict, then what the mascot says.

Two LLM calls in a row, kept separate on purpose (guidelines/7 §2): a failed
verdict still gets a line (the question just continues the story), and a failed
or unsafe line still returns the verdict. Only when both are missing is it a 502.
The phone fills any missing half from its script (spec §3-0).

The prompt is read from eval/line_prompt.md, not copied (one thing in one place).
"""
import json
import logging
import re
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import REPO, settings
from ..filters.blocklist import ALLOW, BLOCK
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import system_block
from ..schemas.judge import JudgeResult
from ..schemas.turn import Line, TurnRequest, TurnResult
from . import judge

router = APIRouter()
log = logging.getLogger("turn")
EVAL = REPO / "eval"

# guidelines/7 §3: ack 8 eojeol, question 12. Logged, not enforced — a long line
# is not unsafe, and throwing it away would leave the child with the script instead.
_LIMITS = {"ack": 8, "expand": 10, "question": 12}


@lru_cache(maxsize=1)
def system() -> str:
    return system_block(EVAL / "line_prompt.md")


@lru_cache(maxsize=1)
def schema() -> dict:
    s = json.loads((EVAL / "line_schema.json").read_text(encoding="utf-8"))
    return {k: v for k, v in s.items() if k not in ("name", "description")}


def user(req: TurnRequest, v: JudgeResult | None) -> str:
    """The §3 inputs. Without a verdict, the slots are empty and the model just continues."""
    values = [x for x in ((v.value_1, v.value_2) if v else ()) if x]
    return (
        f"mode:{req.mode}\n"
        f"ask:{'true' if req.ask else 'false'}\n"
        f"level:{req.level or ''}\n"
        f"template:{req.template or ''}\n"
        f"question_just_asked:{req.question}\n"
        f"utterance:{req.utterance}\n"
        f"value:{' / '.join(values)}\n"
        f"next_slot:{(v.next_slot if v else None) or ''}\n"
        f"next_reason:{(v.next_reason if v else None) or ''}\n"
        f"unclear_of:{(v.unclear_of if v and v.unclear else None) or ''}\n"
        f"story_ready:{'true' if v and v.story_ready else 'false'}"
    )


def _eojeol(text: str) -> list[str]:
    return [w.strip(".,!?~…\"'") for w in text.split()]


def check(line: Line) -> str | None:
    """Why this line must not reach the child, or None. An unsafe line is dropped, not patched."""
    for field in ("ack", "expand", "question"):
        text = getattr(line, field)
        if not text:
            continue
        words = _eojeol(text)
        if any(w in BLOCK for w in words) and not any(w in ALLOW for w in words):
            return f"blocked word in {field}"
        if re.search(r"\{(?!주인공\}|친구\d\})[^}]*\}", text):
            return f"unknown placeholder in {field}"
        if len(words) > _LIMITS[field]:
            log.info("line %s over %d eojeol: %d", field, _LIMITS[field], len(words))
    return None


def shape(line: Line, req: TurnRequest, v: JudgeResult | None) -> Line:
    """The rules win over the model on when there is no question."""
    if not req.ask or (v is not None and v.story_ready):
        line.question = None
    return line


def mock_line(req: TurnRequest, v: JudgeResult | None) -> Line:
    nxt = v.next_slot if v else None
    return Line(ack="그랬구나!", expand=None, question=f"{nxt} 이야기를 해 줄래?" if nxt else "그다음엔?")


async def run_line(req: TurnRequest, v: JudgeResult | None) -> Line | None:
    if v is not None and v.reason == "blocked_by_filter":
        return None          # a blocked utterance never leaves for the LLM, here either
    if settings.mock:
        return shape(mock_line(req, v), req, v)
    try:
        raw = await complete(system(), user(req, v), schema(), name="mascot_line",
                             effort=settings.llm_effort_line)
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
    try:
        v = await judge.run(req)
    except LLMError as e:
        log.warning("judge failed in /turn: %s", e)
        v = None
    line = await run_line(req, v)
    if v is None and line is None:
        raise HTTPException(502, "judge and line both failed")
    return TurnResult(judge=v, line=line)
