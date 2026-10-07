"""The judge on Jev (TypeSafe) — choices and yes/no only, in about 0.7 s (09-22 p95 0.73 s).

Why: /turn runs the judge (luna, ~2.3 s) and then the mascot line (~1.8 s) one after the other;
the judge is the part a chooser can take. 09-26 on the same 100 questions: Jev 88.6 % against luna
88.0 % on the nine fields it can answer (eval/results.md · guidelines/10 §4).

What Jev cannot do — it writes no text (vendor limits, no. 9):
- value_1 is the child's words cut to eight eojeol (the luna judge also shortens to eight)
- slot_2 / value_2 are dropped: one answer filling two slots can't be split without writing
- reason · next_reason · emotion · unclear_of · contradiction_with stay empty

Same questions and the same 0.6 floor as eval/providers/typesafe_adapter.py (measured 09-22:
choice fields 86.4 → 94.0 % above it). A choice under the floor is left empty, so the next
question asks the slot again. On any error the caller falls back to the luna judge.

Names do not go here (10-06): the request's `names` are replaced with `{주인공}` · `{친구n}` before
sending (mask_names) — picking a slot needs none. ⚠️ The child's other words still go as they are;
TypeSafe must be in the published privacy policy before the Play build talks to the server.
"""
from __future__ import annotations

import time

import httpx

from ..config import settings

API = "https://api.typesafe.ai/v1/systemone"
FLOOR = 0.6

SLOT_CRITERIA = {
    # the child's word is the answer even when it is a brand, a food or a real name (#250 · 10-07):
    # 「맥도날드」 went to extra 3/3 and 「손흥민이랑」 sat under the floor
    "place": "어디로 가나 — 장소 (가게 · 놀이공원 이름도 장소다)",
    "problem": "무슨 일이 생겼나 — 사건",
    "reaction": "그래서 어떻게 됐나",
    "cause": "왜 그랬나 — 까닭",
    "newcomer": "새로 나온 친구",
    "name": "그 친구의 이름 — 아이가 지어 준 말이면 상표 · 음식 · 캐릭터 · 사람 이름도 이름이다",
    "companion": "같이 간 친구 — 캐릭터 · 실존 인물이어도 친구다",
    "sound": "우는 소리",
    "adult": "옆에 있는 어른의 한마디",
    "solution": "어떻게 풀었나",
    "title": "책 이름",
    "extra": "위 어디에도 안 맞는 것",
    "none": "채워진 칸이 없다 / 해당 없음",
}
CHOICE_Q = {
    "slot_1": "아이가 방금 한 말이 채운 칸은 어디인가",
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


class JevError(Exception):
    pass


def mask_names(text: str, names: list[str]) -> str:
    """The session's names → `{주인공}` · `{친구n}`, the same marks the app's NameMask turns back (10-06).

    Jev only picks slots, so it needs no names; the luna judge and the voice still get them (rule 6).
    Longest name first, so 「민수야」's 「민수」 is not cut out of 「김민수」 the wrong way round.
    """
    marks = {}
    for i, n in enumerate(names):
        n = (n or "").strip()
        if n and not n.startswith("{") and n not in marks:
            marks[n] = "{주인공}" if i == 0 else f"{{친구{i}}}"
    for n in sorted(marks, key=len, reverse=True):
        text = text.replace(n, marks[n])
    return text


def questions() -> dict:
    qs = {f: {"type": "choice", "instructions": t, "criteria": SLOT_CRITERIA} for f, t in CHOICE_Q.items()}
    qs.update({f: {"type": "noul", "instructions": t, "criteria": {"true": y, "false": n}}
               for f, (t, y, n) in NOUL_Q.items()})
    return qs


def assemble(answers: dict, utterance: str) -> dict:
    """Jev's answers in the judge result's shape (the fields JudgeResult reads)."""
    out: dict = {"reason": "jev"}
    for f in CHOICE_Q:
        a = answers.get(f) or {}
        c, k = a.get("choice"), a.get("confidence")
        if c and c != "none" and (k is None or k >= FLOOR):
            out[f] = c
    for f in NOUL_Q:
        v = (answers.get(f) or {}).get("noul")
        if v is not None:
            out[f] = v >= 0.5
    if out.get("slot_1"):
        out["value_1"] = " ".join(utterance.split()[:8])
    return out


# Who sits with the child — the answer to 「오늘은 누구랑 같이 이야기를 만들어?」(#303). Keys are the app's
# PARTNERS (demo/Model.kt) plus solo and unknown. The app's word list missed 「혼자 안 할래, 엄마랑」 (#300)
# and every relative it had not listed (09-29 「삼촌」 looped); a choice question reads the whole answer.
PARTNER_CRITERIA = {
    "mom": "엄마 · 어머니",
    "dad": "아빠 · 아버지",
    "aunt": "이모 · 고모 · 숙모 · 큰엄마 같은 여자 어른 친척",
    "grandma": "할머니 · 외할머니 · 할미",
    "grandpa": "할아버지 · 외할아버지 · 할부지",
    "uncle": "삼촌 · 외삼촌 · 이모부 · 고모부 · 큰아빠 같은 남자 어른 친척",
    "teacher": "선생님 · 쌤 같은 가르치는 어른",
    "sibling": "언니 · 누나 · 오빠 · 형 · 동생 같은 형제자매",
    "friend": "친구 — 이름만 말한 아이(민수 · 지민이)도 친구다",
    "solo": "아무도 없이 아이 혼자 한다 — 「혼자」 · 「나만」 · 「아무도 없어」. 「혼자 안 할래, 엄마랑」은 혼자가 아니다",
    "unknown": "누구인지 알 수 없다 — 대답이 아니거나 알아들을 수 없는 말",
}
PARTNER_Q = "아이가 「오늘은 누구랑 같이 이야기를 만들어?」에 답했다. 지금 아이 옆에서 같이 하는 사람은 누구인가"


async def partner(utterance: str, timeout_s: float = 4.0) -> tuple[str | None, float | None, float]:
    """(key, confidence, seconds). Raises JevError; the app falls back to its word list.

    Under the 0.6 floor the key is None, like the judge's choices.
    """
    if not settings.typesafe_api_key:
        raise JevError("missing TYPESAFE_API_KEY")
    body = {"state": f"{PARTNER_Q}\n\n아이의 답: {utterance}", "model": settings.jev_model,
            "questions": {"partner": {"type": "choice", "instructions": PARTNER_Q, "criteria": PARTNER_CRITERIA}}}
    t0 = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=timeout_s) as http:
            r = await http.post(API, json=body, headers={"Authorization": f"Bearer {settings.typesafe_api_key}"})
    except httpx.HTTPError as e:
        raise JevError(f"network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise JevError(f"HTTP {r.status_code}")
    a = (r.json().get("answers", {}) or {}).get("partner") or {}
    key, conf = a.get("choice"), a.get("confidence")
    if key not in PARTNER_CRITERIA or (conf is not None and conf < FLOOR):
        key = None
    return key, conf, round(time.monotonic() - t0, 2)


async def judge(system: str, user: str, utterance: str, timeout_s: float = 6.0) -> dict:
    """Raises JevError; the caller falls back to the luna judge."""
    if not settings.typesafe_api_key:
        raise JevError("missing TYPESAFE_API_KEY")
    body = {"state": f"{system}\n\n{user}", "model": settings.jev_model, "questions": questions()}
    t0 = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=timeout_s) as http:
            r = await http.post(API, json=body, headers={"Authorization": f"Bearer {settings.typesafe_api_key}"})
    except httpx.HTTPError as e:
        raise JevError(f"network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise JevError(f"HTTP {r.status_code}")
    out = assemble(r.json().get("answers", {}) or {}, utterance)
    out["_seconds"] = round(time.monotonic() - t0, 2)
    return out
