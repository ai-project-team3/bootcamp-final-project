"""One mascot turn: judge, then the three-piece line. Spec: guidelines/7_프롬프트.md §3.

The line is split from the judge on purpose (§2): a wrong verdict must still
leave the mascot something to say, and a failed line must not lose the verdict.
"""
from typing import Literal, Optional

from pydantic import BaseModel

from .judge import JudgeRequest, JudgeResult


class TurnRequest(JudgeRequest):
    # false: the app already has the next question (in coop, a parent's question is next) and
    # reads it itself. The mascot keeps the reaction (ack · expand) and leaves the question empty.
    ask: bool = True
    # coop only: why the parent picked the story (CoopTemplates.kt CoopReason) — sets the tense of
    # the question, as /story does for the book (#52 · #53 C). done · soon · dream; None = done
    reason: Optional[Literal["done", "soon", "dream"]] = None


class Line(BaseModel):
    ack: str                             # 받아주기 — mirror the child's words, 8 eojeol max
    expand: Optional[str] = None         # 되돌려주기 — add one thing; null if nothing to add
    question: Optional[str] = None       # 질문 — the next slot, open, 12 eojeol max
    # 답 후보 — up to 3 short answers for the slot `question` asks. When the child cannot answer,
    # the app shows them as cards, then the mascot takes the first (#79 · guidelines/2 §1-1).
    # Null with no question, and in diary (what really happened today is not picked for the child).
    options: Optional[list[str]] = None


class TurnResult(BaseModel):
    # Either may be null; the app fills the gap from its script (spec §3-0).
    judge: Optional[JudgeResult] = None
    line: Optional[Line] = None
