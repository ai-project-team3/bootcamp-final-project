"""The judge on Laya — a local encoder we fine-tuned on our own slots (10-08), in place of Jev for the modes in
config judge_laya_modes (the v4 checkpoint knows story · diary · coop).

Why: Jev sends the child's words to a US vendor that is not in the privacy policy (10-06). Laya runs on
our own machine — nothing leaves it — and answers the same choice / yes-no questions in p50 0.05 s
(Jev 0.25 s). On the same 100 questions, run as each is served (Laya floor 0.9 · Jev floor 0.6, both
with the rules below): 9 fields 95.1 % vs 94.3 %, paired bootstrap +0.8 [−0.9, +2.6] — a tie.
Laya_판정_전환_설계.md §7-8 holds the numbers and how the training data was made.

It runs as a sidecar (backend/scripts/laya_judge_server.py) so torch never enters the backend; this module only
posts the questions. One checkpoint for all three modes (v4): the state starts with a 「모드:」 line, and coop
adds 「이유:」 (done · soon · dream) — the same lines judge_prompt.user() gives luna. Which modes go here is
config judge_laya_modes.

What it answers and what the rules answer:
- slot_1 · no_longer_needed: Laya, kept when its answer confidence is at least settings.laya_floor (0.9).
  The model is over-confident (most wrong answers sit above 0.9), so the floor trims little; an empty
  choice means the next question asks the slot again
- s1_reason · s2_addition · contradiction · unclear: Laya, true at 0.5
- next_slot: Laya picks among the next empty slots in the fixed order (it was trained on the first
  three); anything it picks that is filled, or the title, falls back to the first empty one
- story_ready: a rule, not the model. story: all six required slots are filled (or the asked one was let go) —
  matches the labelled 100 questions 100/100, Laya alone got 95. diary · coop: the ending is filled, or the
  ending / tomorrow question was just answered in any way (「몰라」 too — the diary moves on) — 12 of the 13
  labelled mode cases
- next_slot in diary · coop: the first empty of place → problem → reaction → cause → solution
- slot_2 / value_2 are dropped, as on Jev: one answer filling two slots can't be split without writing

Questions are word for word what the model was trained on — change one and retrain, or it reads
options it has never seen.
"""
from __future__ import annotations

import time

import httpx

from ..config import settings
from ..schemas.judge import JudgeRequest
from .jev import mask_names

# word for word the training questions (the Laya eval bench, main 95f871d4)
SLOT_CRITERIA = {
    "place": "어디로 가나 — 장소",
    "problem": "무슨 일이 생겼나 — 사건",
    "reaction": "그래서 어떻게 됐나",
    "cause": "왜 그랬나 — 까닭",
    "newcomer": "새로 나온 친구",
    "name": "그 친구의 이름",
    "companion": "같이 간 친구",
    "sound": "우는 소리",
    "adult": "옆에 있는 어른의 한마디",
    "solution": "어떻게 풀었나",
    "title": "책 이름",
    "extra": "위 어디에도 안 맞는 것",
    "none": "채워진 칸이 없다 / 해당 없음",
}
CHOICE_Q = {
    "slot_1": "아이가 방금 한 말이 채운 칸은 어디인가",
    "slot_2": "한 번에 두 칸을 채웠다면 두 번째 칸은 어디인가",
    "next_slot": "다음에 물어야 하는 칸은 어디인가",
    "no_longer_needed": "아이 말 때문에 더 이상 물을 필요가 없어진 칸이 있는가",
}
NOUL_Q = {
    "s1_reason": ("묻지 않았는데 **왜 그랬는지**를 스스로 붙였는가", "까닭을 스스로 말했다", "까닭을 말하지 않았다"),
    "s2_addition": ("묻지 않은 것을 **한 가지 더 얹어** 문장을 늘렸는가", "묻지 않은 것을 덧붙였다", "물은 것에만 답했다"),
    "contradiction": ("앞에서 한 말과 **어긋나는가**", "앞의 말과 어긋난다", "어긋나지 않는다"),
    "unclear": ("무슨 말인지 **알아들을 수 없는가**", "알아들을 수 없다", "알아들을 수 있다"),
    "story_ready": ("이야기 재료가 **다 찼는가**", "이야기를 만들 재료가 다 찼다", "아직 더 물어야 한다"),
}
SLOTS = ("place", "problem", "reaction", "cause", "newcomer", "name", "companion", "sound", "adult", "solution", "title")
# the order the labelled questions use for 「what to ask next」 and the six a story can't end without
ORDER = ("place", "newcomer", "problem", "cause", "reaction", "name", "sound", "solution", "adult", "title")
REQUIRED = ("place", "problem", "cause", "newcomer", "sound", "solution")
DIARY_ORDER = ("place", "problem", "reaction", "cause", "solution")


class LayaError(Exception):
    pass


def questions() -> dict:
    qs = {f: {"type": "choice", "instructions": t, "criteria": SLOT_CRITERIA} for f, t in CHOICE_Q.items()}
    qs.update({f: {"type": "noul", "instructions": t, "criteria": {"true": y, "false": n}}
               for f, (t, y, n) in NOUL_Q.items()})
    return qs


def state(req: JudgeRequest) -> str:
    """The short state the model was trained on — the slots in the training order, never the long prompt
    (the long prompt cut the child's words off the end and scored 13 % before training)."""
    filled = ", ".join(f"{k}={req.slots[k]}" for k in SLOTS if req.slots.get(k)) or "없음"
    # coop's 「이유:」 line is what judge_prompt.user() sends luna — done when the app sent none (#100)
    reason = f"이유: {getattr(req, 'reason', None) or 'done'}\n" if req.mode == "coop" else ""
    return mask_names(
        f"모드: {req.mode}\n{reason}"
        f"채워진 칸: {filled}\n"
        f"오또가 물은 칸: {req.asked_slot or '없음'}\n"
        f"오또 질문: {req.question}\n"
        f"아이 말: {req.utterance}", req.names)


def _empty_after(slots: dict, filled: str | None) -> list[str]:
    return [s for s in ORDER if not slots.get(s) and s != filled]


def assemble(answers: dict, req: JudgeRequest) -> dict:
    """Laya's answers in the judge result's shape, with next_slot and story_ready from the rules."""
    out: dict = {"reason": "laya"}
    for f in ("slot_1", "no_longer_needed"):
        a = answers.get(f) or {}
        c, k = a.get("choice"), a.get("answer_confidence")
        if c and c != "none" and c in SLOTS + ("extra",) and (k is None or k >= settings.laya_floor):
            out[f] = c
    for f in ("s1_reason", "s2_addition", "contradiction", "unclear"):
        v = (answers.get(f) or {}).get("noul")
        if v is not None:
            out[f] = v >= 0.5
    if out.get("slot_1"):
        out["value_1"] = " ".join(mask_names(req.utterance, req.names).split()[:8])

    dropped = out.get("no_longer_needed")
    if req.mode != "story":
        out["story_ready"] = (bool(req.slots.get("solution")) or out.get("slot_1") == "solution"
                              or req.asked_slot in ("solution", "extra"))
        if not out["story_ready"]:
            empty = [s for s in DIARY_ORDER if not req.slots.get(s) and s != out.get("slot_1")]
            out["next_slot"] = empty[0] if empty else None
        return out
    out["story_ready"] = all(req.slots.get(s) or s == out.get("slot_1") or s == dropped for s in REQUIRED)
    if not out["story_ready"]:
        empty = [s for s in _empty_after(req.slots, out.get("slot_1")) if s != "title"]
        pick = (answers.get("next_slot") or {}).get("choice")
        out["next_slot"] = pick if pick in empty else (empty[0] if empty else None)
    return out


async def judge(req: JudgeRequest, timeout_s: float = 3.0) -> dict:
    """Raises LayaError; the caller falls back to Jev or luna."""
    t0 = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=timeout_s) as http:
            r = await http.post(f"{settings.laya_url.rstrip('/')}/predict",
                                json={"state": state(req), "questions": questions()})
    except httpx.HTTPError as e:
        raise LayaError(f"network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise LayaError(f"HTTP {r.status_code}")
    try:
        answers = r.json().get("answers") or {}
        if not isinstance(answers, dict):
            raise TypeError(type(answers).__name__)
        out = assemble(answers, req)
    except (ValueError, TypeError, AttributeError) as e:
        # a sidecar that answers 200 with something else must not become a 500 — the next judge takes it
        raise LayaError(f"bad answer: {type(e).__name__}") from e
    out["_seconds"] = round(time.monotonic() - t0, 2)
    return out
