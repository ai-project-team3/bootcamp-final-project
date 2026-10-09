"""One mascot turn: judge, then the three-piece line. Spec: guidelines/7_프롬프트.md §3.

The line is split from the judge on purpose (§2): a wrong verdict must still
leave the mascot something to say, and a failed line must not lose the verdict.
"""
from typing import Literal, Optional

from pydantic import BaseModel, Field

from .judge import JudgeRequest, JudgeResult, SlotName

# What the mascot does with a reply that is not a plain answer (#323). None = a plain turn.
#   repair      — the child said Otto got it wrong: take the slot back, ask it again
#   answer_back — the child asked Otto something: answer briefly, ask the same question again
#   rephrase    — the child did not catch the question: ask the same slot in easier words
#   aside       — talk off the question: take it in (extra), ask the same question again
# A child still telling (「근데 있잖아」) is a plain turn for now: the app should keep listening, not
# the server answer (10-08 · out of this round)
Act = Literal["repair", "answer_back", "rephrase", "aside"]


class OttoSaid(BaseModel):
    ack: Optional[str] = None
    expand: Optional[str] = None
    question: Optional[str] = None


class ChildSaid(BaseModel):
    text: str
    by: Literal["child", "card"]         # rule 5 — the mascot's words live in `otto`, never here


class Fill(BaseModel):
    slot: SlotName                       # rule 1: off-list names are refused, not stored
    value: str
    prev: Optional[str] = None           # the value it replaced — what a repair puts back


class HistoryTurn(BaseModel):
    """One finished turn, kept by the app and sent back whole each turn (#323).

    The server keeps nothing between turns; it picks what to use from this list.
    """
    turn: int
    asked_slot: Optional[str] = None
    otto: OttoSaid = OttoSaid()
    child: ChildSaid
    fills: list[Fill] = []
    act: Optional[Act] = None


class TurnRequest(JudgeRequest):
    # false: the app already has the next question (in coop, a parent's question is next) and
    # reads it itself. The mascot keeps the reaction (ack · expand) and leaves the question empty.
    ask: bool = True
    # coop only: why the parent picked the story (CoopTemplates.kt CoopReason) — sets the tense of
    # the question, as /story does for the book (#52 · #53 C). done · soon · dream; None = done
    reason: Optional[Literal["done", "soon", "dream"]] = None
    # the session so far, oldest first (#323). Empty = no dialogue repair: the turn runs as before
    history: list[HistoryTurn] = []


class Line(BaseModel):
    ack: str                             # 받아주기 — mirror the child's words, 8 eojeol max
    expand: Optional[str] = None         # 되돌려주기 — add one thing; null if nothing to add
    question: Optional[str] = None       # 질문 — the next slot, open, 12 eojeol max
    # 답 후보 — up to 3 short answers for the slot `question` asks. When the child cannot answer,
    # the app shows them as cards, then the mascot takes the first (#79 · guidelines/2 §1-1).
    # Null with no question, and in diary (what really happened today is not picked for the child).
    options: Optional[list[str]] = None
    # repair turns only: the right value for the slot the decider picked, copied from the child's words
    # as transcribed. Server-side — it goes out as the verdict's slot_1 once it passes the check, never here
    fixed_value: Optional[str] = Field(default=None, exclude=True)


class TurnResult(BaseModel):
    # Either may be null; the app fills the gap from its script (spec §3-0).
    judge: Optional[JudgeResult] = None
    line: Optional[Line] = None
    # what this turn does with the child's reply (#323); None = a plain turn. Here, not in `line`: the
    # rules decide it before the line is written, so a failed line must not take it along — the phone
    # can still answer with a baked line (「앗, 내가 잘못 알았구나!」)
    act: Optional[Act] = None
    # slots the child said were wrong (#323): the app puts back each one's `prev` from its history
    retract: list[SlotName] = []
