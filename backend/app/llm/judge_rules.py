"""When the talk ends and what to ask next — rules, not the model, for the chooser judges.

Laya uses both (10-08 · app/llm/laya.py). Jev uses the diary · coop rule (10-12): the server judges every mode
on Jev again (cloud CPU, no GPU), and Jev's own story_ready / next_slot there are guesses about an order it never
saw. A story on Jev keeps its own verdict and the #156 luna recovery (routers/judge.py) — the story rule's six
required slots come from the labelled 100, but what a story needs depends on its template.

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
