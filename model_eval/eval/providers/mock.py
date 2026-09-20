from __future__ import annotations

import json
import random
import time
from dataclasses import dataclass

from .base import ProviderResult, StreamChunk, Usage


@dataclass
class MockProvider:
    seed: int = 20260918
    provider_name: str = "mock"

    def stream_judge(
        self,
        *,
        model: str,
        system_prompt: str,
        user_prompt: str,
        schema: dict,
        max_output_tokens: int = 768,
        reasoning_effort: str | None = None,
    ) -> ProviderResult:
        local = random.Random(f"{self.seed}|{model}|{system_prompt}|{user_prompt}")
        item_id = _extract_value(user_prompt, "id")
        asked = _extract_value(user_prompt, "asked")
        utterance = _extract_value(user_prompt, "utterance")
        slots = _extract_json(user_prompt, "slots") or {}
        pred = _demo_prediction(item_id=item_id, asked=asked, utterance=utterance, slots=slots)

        # 채점기가 실제 오판을 잡는지 보여주기 위한 의도적 demo 오류.
        if item_id == "j006":
            pred["slot_1"] = "reaction"
        if item_id == "j007":
            pred["next_slot"] = "title"
        if item_id == "j014":
            pred["s1_reason"] = False
        if item_id == "j015":
            pred["slot_2"] = None
            pred["value_2"] = None
        if item_id == "j018":
            pred["no_longer_needed"] = None

        # JSON 파싱 실패 1건: 실패를 기록하고 러너가 계속 진행하는지 검증.
        if item_id == "j017":
            payload = '{"reason":"mock malformed","slot_1":null'
        else:
            payload = json.dumps(pred, ensure_ascii=False)

        cut = max(1, min(len(payload) - 1, local.randint(20, 55))) if len(payload) > 1 else 1
        pieces = [payload[:cut], payload[cut:]] if payload[cut:] else [payload]

        def generator():
            time.sleep(local.uniform(0.004, 0.010))
            for idx, piece in enumerate(pieces):
                if idx:
                    time.sleep(local.uniform(0.002, 0.006))
                yield StreamChunk(piece)

        usage = Usage(
            input_tokens=max(30, (len(system_prompt) + len(user_prompt)) // 3),
            output_tokens=max(30, len(payload) // 3),
        )
        return ProviderResult(chunks=generator(), usage=usage)


def _extract_value(prompt: str, key: str) -> str:
    prefix = f"{key}:"
    for line in prompt.splitlines():
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    return ""


def _extract_json(prompt: str, key: str):
    raw = _extract_value(prompt, key)
    try:
        return json.loads(raw)
    except Exception:
        return None


def _base(*, next_slot=None) -> dict:
    return {
        "reason": "mock 규칙 기반 판정",
        "slot_1": None,
        "value_1": None,
        "slot_2": None,
        "value_2": None,
        "contradiction": False,
        "contradiction_with": None,
        "s1_reason": False,
        "s2_addition": False,
        "emotion": None,
        "unclear": False,
        "unclear_of": None,
        "next_slot": next_slot,
        "next_reason": "mock 다음 슬롯 선택" if next_slot else None,
        "no_longer_needed": None,
        "story_ready": False,
    }


def _demo_prediction(*, item_id: str, asked: str, utterance: str, slots: dict) -> dict:
    # 이 mock은 실제 모델 성능을 흉내 내기 위한 것이 아니라 새 17필드 파이프라인 검증용이다.
    known = {
        "j001": ("place", "공룡나라", None, None, False, False, None, False, "problem", None, False),
        "j002": ("reaction", "무서워서 도망갔다", None, None, True, False, "무섭다", False, "cause", None, False),
        "j003": ("reaction", "가서 먹었다", None, None, False, False, None, False, "problem", None, False),
        "j004": ("place", "공룡나라", "companion", "뿌뿌", False, True, None, False, "problem", None, False),
        "j005": (None, None, None, None, False, False, None, False, "problem", "companion", False),
        "j006": ("problem", "로켓이 흔들렸다", None, None, False, False, None, False, "reaction", None, False),
        "j007": ("cause", "비가 와서 미끄러졌다", None, None, True, False, None, False, "solution", None, False),
        "j008": ("problem", "불이 났다", "reaction", "무서웠다", True, True, "무섭다", False, "cause", None, False),
        "j009": (None, None, None, None, False, False, None, False, "place", None, False),
        "j010": (None, None, None, None, False, False, None, True, "place", None, False),
        "j011": ("place", "공뇽나라", None, None, False, False, None, False, "problem", None, False),
        "j012": (None, None, None, None, False, False, None, False, "problem", "newcomer", False),
        "j013": ("companion", "뿌뿌", None, None, False, False, None, False, "problem", None, False),
        "j014": ("cause", "배고프니까 먹었다", None, None, True, False, None, False, "solution", None, False),
        "j015": ("solution", "물을 뿌린다", "sound", "사이렌이 울린다", False, True, None, False, "reaction", None, False),
        "j016": ("place", "바닷속", None, None, False, False, None, False, "problem", None, False),
        "j017": (None, None, None, None, False, False, None, True, "place", None, False),
        "j018": (None, None, None, None, False, False, None, False, "problem", "adult", False),
        "j019": ("title", "공룡나라 대모험", None, None, False, False, None, False, None, None, True),
        "j020": ("reaction", "밖에 나가서 뛰었다", None, None, False, False, None, False, "problem", None, False),
    }
    slot1, value1, slot2, value2, s1, s2, emotion, unclear, next_slot, no_longer, ready = known.get(
        item_id,
        (asked or None, utterance or None, None, None, False, False, None, False, None, None, False),
    )
    p = _base(next_slot=next_slot)
    p.update({
        "slot_1": slot1,
        "value_1": value1,
        "slot_2": slot2,
        "value_2": value2,
        "s1_reason": s1,
        "s2_addition": s2,
        "emotion": emotion,
        "unclear": unclear,
        "unclear_of": asked if unclear else None,
        "no_longer_needed": no_longer,
        "story_ready": ready,
    })
    if item_id == "j016":
        p["contradiction"] = True
        p["contradiction_with"] = "place"
    return p
