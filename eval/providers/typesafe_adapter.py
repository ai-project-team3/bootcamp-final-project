from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

from .base import ProviderResult, StreamChunk, Usage

ENV_KEY = "TYPESAFE_API_KEY"
API = "https://api.typesafe.ai/v1/systemone"

# ─────────────────────────────────────────────────────────────────────────────
# Jev는 다른 후보들과 근본적으로 모양이 다르다. 글을 만들지 않는다.
#
# 다른 어댑터는 "스키마를 주고 JSON을 받는다". Jev는 그런 걸 안 한다 —
# **타입이 정해진 질문들에 답만** 돌려준다. 그래서 이 어댑터가 하는 일은
# 「판정 16필드를 원자적 질문으로 쪼개서 묻고, 답을 다시 16필드 모양으로 맞추는 것」이다.
# 그게 벤더가 권하는 방식이기도 하다 — *"원자적 질문을 코드로 조합한다."*
#
# ⚠️ **16필드 중 9개만 물을 수 있다.** 나머지 7은 자유 문장이고, 한계 문서 9번이
#    *"글을 만들도록 학습되지 않았다"*고 적어 뒀다.
#
#    물을 수 있는 것 (9)
#      choice ← slot_1 · slot_2 · next_slot · no_longer_needed   (13지선다, 상한 255)
#      noul   ← contradiction · s1_reason · s2_addition · unclear · story_ready
#
#    못 묻는 것 (7)
#      reason · next_reason           설명문. 애초에 판정 근거를 적는 칸이다
#      value_1 · value_2              아이가 한 말 원문. 후보 목록이 없다
#      emotion · unclear_of · contradiction_with
#
# **못 묻는 7은 null로 둔다.** 그럴듯한 값을 채워 넣으면 비교가 오염된다 —
# 채점기는 그 필드에서 0점을 줄 것이고, **그 0점은 "Jev가 틀렸다"가 아니라
# "Jev가 하지 않는 일"로 읽어야 한다.** 보고서에 그렇게 적는다.
# ─────────────────────────────────────────────────────────────────────────────

SLOTS = ["place", "problem", "reaction", "cause", "newcomer", "name",
         "companion", "sound", "adult", "solution", "title", "extra"]

# 슬롯 설명 — Jev의 `criteria`는 "옵션 이름 → 그 옵션의 설명" 맵이다.
# 설명이 판정 품질을 좌우하므로 `guidelines/2_공통_데이터_모델.md` §1-1의 문구를 쓴다.
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
    "s1_reason": ("묻지 않았는데 **왜 그랬는지**를 스스로 붙였는가",
                  "까닭을 스스로 말했다", "까닭을 말하지 않았다"),
    "s2_addition": ("묻지 않은 것을 **한 가지 더 얹어** 문장을 늘렸는가",
                    "묻지 않은 것을 덧붙였다", "물은 것에만 답했다"),
    "contradiction": ("앞에서 한 말과 **어긋나는가**",
                      "앞의 말과 어긋난다", "어긋나지 않는다"),
    "unclear": ("무슨 말인지 **알아들을 수 없는가**",
                "알아들을 수 없다", "알아들을 수 있다"),
    "story_ready": ("이야기 재료가 **다 찼는가**",
                    "이야기를 만들 재료가 다 찼다", "아직 더 물어야 한다"),
}

# 이 문턱 아래면 그 칸을 비운다. 벤더 권고는 0.9 자동 / 0.5~0.9 확인 / 0.5 미만 사람이다.
#
# **0.6 — 09-22 측정으로 정했고 09-25 에 넣었다.** 확신도가 보정돼 있다(구간별 정답률
# 61.9 → 78.6 → 95.5 → 99.5% 단조 증가, `results.md` 09-22 Jev).
# ⚠️ 이 문턱이 거는 것은 **아래 CHOICE 4필드뿐**이다(slot_1 · slot_2 · next_slot · no_longer_needed).
#    09-22 의 「88.6 → 95.1%」는 9필드 전체 값이라 그대로 옮기면 안 된다. 같은 원자료를
#    4필드만 다시 채점하면 **86.4 → 94.0%**(판단 25% 를 버림)다 — 근거는 이 필드들에서도 선다.
# ⚠️ 버린 칸은 **비워 둔다**(= 다음 질문이 그 칸을 다시 묻는다). 루나에게 다시 묻는 길은
#    안 정했다(`results.md` 09-22 「버리는 23%를 어떻게 처리할지」).
# ⚠️ NOUL 5필드(참/거짓)에는 이 문턱을 안 건다. 참/거짓에는 「비움」이 없어서, 걸면 곧
#    「거짓으로 바꾼다」가 되고 그건 다른 결정이다. 같은 원자료에서 NOUL 은 문턱 없이 90.2%.
CONFIDENCE_FLOOR = float(os.getenv("TYPESAFE_CONFIDENCE_FLOOR", "0.6"))


def _questions() -> dict:
    """16필드를 원자적 질문으로. 질문을 더해도 응답 시간이 거의 안 변한다(문서)."""
    qs = {}
    for fid, text in CHOICE_Q.items():
        qs[fid] = {"type": "choice", "instructions": text, "criteria": SLOT_CRITERIA}
    for fid, (text, yes, no) in NOUL_Q.items():
        qs[fid] = {"type": "noul", "instructions": text,
                   "criteria": {"true": yes, "false": no}}
    return qs


def _assemble(answers: dict) -> tuple[dict, dict]:
    """답을 판정 16필드 모양으로 맞춘다. 못 묻는 7은 null."""
    out = {
        "reason": None, "slot_1": None, "value_1": None, "slot_2": None,
        "value_2": None, "contradiction": False, "contradiction_with": None,
        "s1_reason": False, "s2_addition": False, "emotion": None,
        "unclear": False, "unclear_of": None, "next_slot": None,
        "next_reason": None, "no_longer_needed": None, "story_ready": False,
    }
    conf = {}
    for fid in CHOICE_Q:
        a = answers.get(fid) or {}
        c, k = a.get("choice"), a.get("confidence")
        conf[fid] = k
        # "none"은 우리 스키마의 null이다 — Jev에는 빈 답이 없으므로 선택지로 넣었다
        if c and c != "none" and (k is None or k >= CONFIDENCE_FLOOR):
            out[fid] = c
    for fid in NOUL_Q:
        a = answers.get(fid) or {}
        v = a.get("noul")
        # noul은 0~1 값이고 확신도가 따로 안 온다. 0.5를 경계로 읽고 값 자체를 신뢰도로 쓴다.
        # ⚠️ 문서 8번 — P(참)+P(거짓)=1을 보장하지 않는다. 합이 1이라고 가정하지 않는다.
        conf[fid] = None if v is None else abs(v - 0.5) * 2
        if v is not None:
            out[fid] = v >= 0.5
    return out, conf


class TypeSafeAdapter:
    provider_name = "typesafe"

    def stream_judge(
        self,
        *,
        model: str,
        system_prompt: str,
        user_prompt: str,
        schema: dict,
        max_output_tokens: int = 256,
        reasoning_effort: str | None = None,
    ) -> ProviderResult:
        key = os.getenv(ENV_KEY)
        if not key:
            raise RuntimeError(f"{ENV_KEY}가 .env에 없습니다")

        # 스키마는 안 쓴다 — 질문이 스키마 역할을 한다. 인자는 인터페이스를 맞추려고 받는다.
        # system_prompt는 판정 지침이고, Jev는 `state`만 읽으므로 둘을 이어 넣는다.
        # ⚠️ 한계 문서 5번 — 쓸데없는 맥락이 많으면 정확도가 떨어진다.
        #    지금은 다른 후보와 같은 입력을 주려고 통째로 넣는다(비교가 유효하려면 같아야 한다).
        #    걸러 넣는 것이 나은지는 4번 측정에서 본다.
        body = json.dumps({
            "state": f"{system_prompt}\n\n{user_prompt}",
            "model": model,
            "questions": _questions(),
        }, ensure_ascii=False).encode()

        req = urllib.request.Request(
            API, body,
            {"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                res = json.load(r)
        except urllib.error.HTTPError as e:
            # 429·529는 재시도하라고 문서가 적었다. 러너가 재시도를 쥐고 있으므로 올려 보낸다.
            raise RuntimeError(
                f"typesafe {e.code}: {e.read().decode('utf-8', 'replace')[:300]}"
            ) from e

        fields, conf = _assemble(res.get("answers", {}))
        # 확신도는 판정 스키마에 자리가 없다. 채점을 건드리지 않으려고 별도 키로 실어 보낸다 —
        # 이게 이 모델을 쓸지 정하는 값이므로 버리면 안 된다.
        fields["_confidence"] = conf
        u = res.get("usage", {}) or {}
        return ProviderResult(
            chunks=iter([StreamChunk(json.dumps(fields, ensure_ascii=False))]),
            usage=Usage(
                input_tokens=int(u.get("input_tokens", 0)),
                output_tokens=int(u.get("output_tokens", 0)),
            ),
        )
