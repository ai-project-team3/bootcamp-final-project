"""When the talk ends and what to ask next — rules, not the model, for the chooser judges.

Laya uses both (10-08 · app/llm/laya.py). Jev uses the diary · coop rule (10-12): the server judges every mode
on Jev again (cloud CPU, no GPU), and Jev's own story_ready / next_slot there are guesses about an order it never
saw. A story on Jev keeps its own verdict and the #156 luna recovery (routers/judge.py) — the story rule's six
required slots come from the labelled 100, but what a story needs depends on its template. Only a next slot Jev
left open is filled in (story_fill, 10-12): the next empty beat, until the ending is in.

- story: ready when the six required slots are filled (or the asked one was let go) — 100/100 on the labelled
  questions, Laya alone 95. Otherwise the model's next_slot is kept if it is still empty, else the first empty
  slot in the fixed order. Never the title.
- diary · coop: ready when the ending is filled, or the ending / tomorrow question was just answered in any way
  (「몰라」 too — the diary moves on). Otherwise the first empty of place → problem → reaction → cause → solution
  (12 of the 13 labelled mode cases).
"""
from __future__ import annotations

from ..schemas.judge import JudgeRequest

# the order the labelled questions use for 「what to ask next」 and the six a story can't end without
ORDER = ("place", "newcomer", "problem", "cause", "reaction", "name", "sound", "solution", "adult", "title")
REQUIRED = ("place", "problem", "cause", "newcomer", "sound", "solution")
DIARY_ORDER = ("place", "problem", "reaction", "cause", "solution")
# A story on Jev whose next slot was left open or wiped (under Jev's 0.6 floor, or a filled slot enforce() drops):
# the story beats still empty, in the order a live story asks them. companion · adult · name · sound · title are the
# app's own questions (StoryPrompt.kt skips them), never filled in here. Templates E · F tell no cause (StoryBank.kt
# causeLine); before the template is fixed (turn 3) a PICK level story becomes E
STORY_FILL = ("place", "problem", "newcomer", "cause", "reaction", "solution")
NO_CAUSE_TEMPLATES = ("E", "F")


def story_fill(req: JudgeRequest, filled: str | None) -> str | None:
    """The next story beat to ask when Jev gave none — or None once the ending is in, which is left to the
    full judge (#156: whether a story is over is never a rule's call)."""
    if req.slots.get("solution") or filled == "solution":
        return None
    no_cause = req.template in NO_CAUSE_TEMPLATES or (req.template is None and (req.level or "") == "pick")
    return next((s for s in STORY_FILL if not req.slots.get(s) and s != filled
                 and not (s == "cause" and no_cause)), None)


def apply(out: dict, req: JudgeRequest, pick: str | None = None) -> dict:
    """Sets out["story_ready"] and out["next_slot"] from the rules; `pick` is the model's next slot, if any."""
    filled = out.get("slot_1")
    if req.mode != "story":
        out["story_ready"] = (bool(req.slots.get("solution")) or filled == "solution"
                              or req.asked_slot in ("solution", "extra"))
        empty = [s for s in DIARY_ORDER if not req.slots.get(s) and s != filled]
        if out["story_ready"]:
            out.pop("next_slot", None)
        else:
            out["next_slot"] = empty[0] if empty else None
        return out
    dropped = out.get("no_longer_needed")
    out["story_ready"] = all(req.slots.get(s) or s == filled or s == dropped for s in REQUIRED)
    if out["story_ready"]:
        out.pop("next_slot", None)
        return out
    empty = [s for s in ORDER if not req.slots.get(s) and s != filled and s != "title"]
    out["next_slot"] = pick if pick in empty else (empty[0] if empty else None)
    return out
