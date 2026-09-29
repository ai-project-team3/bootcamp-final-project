"""One mascot turn: judge, then the three-piece line. Spec: guidelines/7_프롬프트.md §3.

The line is split from the judge on purpose (§2): a wrong verdict must still
leave the mascot something to say, and a failed line must not lose the verdict.
"""
from typing import Optional

from pydantic import BaseModel

from .judge import JudgeRequest, JudgeResult


class TurnRequest(JudgeRequest):
    # coop: the parent wrote the next question in advance, the app reads it.
    # The mascot keeps the reaction (ack · expand) and leaves the question empty.
    ask: bool = True


class Line(BaseModel):
    ack: str                             # 받아주기 — mirror the child's words, 8 eojeol max
    expand: Optional[str] = None         # 되돌려주기 — add one thing; null if nothing to add
    question: Optional[str] = None       # 질문 — the next slot, open, 12 eojeol max


class TurnResult(BaseModel):
    # Either may be null; the app fills the gap from its script (spec §3-0).
    judge: Optional[JudgeResult] = None
    line: Optional[Line] = None
