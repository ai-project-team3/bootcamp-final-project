"""#156: a non-final Jev verdict with no next slot must not feed another open-ended turn."""
import asyncio
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings
from app.llm import jev, judge_rules
from app.routers import judge, turn
from app.schemas.judge import JudgeRequest
from main import app


STORY = {
    "place": "공원", "problem": "공놀이를 했어", "reaction": "즐거웠어",
    "cause": "공을 멀리 던졌어", "solution": "행복한 꿈을 꿨어",
}


@pytest.mark.parametrize("jev_fields", [
    {},
    {"slot_1": "solution", "value_1": "행복한 꿈을 꿨어"},
    {"next_slot": "title"},
    {"next_slot": "cause"},
    {"unclear": True},
])
def test_turn_recovers_a_stranded_story_before_writing_another_question(monkeypatch, jev_fields):
    calls = []
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")

    async def fast(*args, **kwargs):
        calls.append("jev")
        return {"reason": "jev", "story_ready": False, **jev_fields}

    async def full(system, user, schema, **kwargs):
        calls.append("full judge")
        # Recover from the actual request, not a guessed or partial Jev fill.
        assert '"solution":"행복한 꿈을 꿨어"' in user
        assert "utterance:더 없어" in user
        return {"reason": "ending is present", "story_ready": True}

    async def line(system, user, schema, **kwargs):
        calls.append("line")
        assert "story_ready:true" in user
        # Even if the line model asks again, the real /turn shape must remove it.
        return {"ack": "그랬구나!", "question": "그다음에는?", "options": ["산책"]}

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", full)
    monkeypatch.setattr(turn, "complete", line)
    with TestClient(app) as client:
        response = client.post("/turn", json={
            "mode": "story", "slots": STORY, "template": "E", "turn": 10,
            "asked_slot": None, "question": "더 하고 싶은 이야기가 있어?", "utterance": "더 없어",
        })
    assert response.status_code == 200
    result = response.json()
    assert result["judge"]["story_ready"] is True
    assert result["line"]["question"] is None and result["line"]["options"] is None
    assert calls == ["jev", "full judge", "line"]


def test_a_missing_ending_is_asked_by_the_rule_without_forcing_completion(monkeypatch):
    # 10-12: before the ending is in, an open next slot is the next empty beat, not a full luna judge
    # (judge_rules.story_fill) — the story is still never made ready by a rule
    calls = []
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")

    async def fast(*args, **kwargs):
        return {"reason": "jev", "story_ready": False}

    async def full(*args, **kwargs):
        calls.append("full judge")
        return {"reason": "ending is missing", "story_ready": False, "next_slot": "solution"}

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", full)
    slots = {k: v for k, v in STORY.items() if k != "solution"}
    result = asyncio.run(judge.run(JudgeRequest(
        slots=slots, template="E", question="그다음에는?", utterance="더 없어",
    )))
    # template E tells no cause; the newcomer is the first empty beat, then the ending
    assert result.reason == "jev" and result.story_ready is False and result.next_slot == "newcomer"
    slots["newcomer"] = "문어"
    result = asyncio.run(judge.run(JudgeRequest(
        slots=slots, template="E", question="그다음에는?", utterance="더 없어",
    )))
    assert result.story_ready is False and result.next_slot == "solution"
    assert calls == []


@pytest.mark.parametrize("template, level, slots, filled, want", [
    ("C", None, {"place": "바다"}, None, "problem"),
    ("C", None, {"place": "바다", "problem": "상어"}, "newcomer", "cause"),     # this turn's slot is not asked
    ("E", None, {"place": "바다", "problem": "상어", "newcomer": "문어"}, None, "reaction"),   # E tells no cause
    (None, "pick", {"place": "바다", "problem": "상어", "newcomer": "문어"}, None, "reaction"),  # pick → E
    (None, "chain", {"place": "바다", "problem": "상어", "newcomer": "문어"}, None, "cause"),
    ("C", None, {"place": "바다"}, "solution", None),                           # the ending came in: full judge
    ("C", None, {"solution": "집에 갔어"}, None, None),
])
def test_story_fill_picks_the_next_empty_beat(template, level, slots, filled, want):
    req = JudgeRequest(slots=slots, template=template, level=level, question="그다음에는?", utterance="응")
    assert judge_rules.story_fill(req, filled) == want


@pytest.mark.parametrize("fields", [{"next_slot": "solution"}, {"story_ready": True}])
def test_a_usable_story_verdict_stays_on_jev_without_an_extra_call(monkeypatch, fields):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")

    async def fast(*args, **kwargs):
        return {"reason": "jev", **fields}

    async def forbidden(*args, **kwargs):
        pytest.fail("a usable Jev verdict must not make another judge call")

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", forbidden)
    slots = {k: v for k, v in STORY.items() if k != "solution"}
    result = asyncio.run(judge.run(JudgeRequest(slots=slots, question="그다음에는?", utterance="놀았어")))
    assert result.reason == "jev"


@pytest.mark.parametrize("mode", ["diary", "coop"])
def test_other_modes_never_take_the_story_recovery(monkeypatch, mode):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary,coop")

    async def fast(*args, **kwargs):
        return {"reason": "jev", "story_ready": False}

    async def forbidden(*args, **kwargs):
        pytest.fail("story recovery must not change the other modes")

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", forbidden)
    result = asyncio.run(judge.run(JudgeRequest(mode=mode, slots={}, question="그다음에는?", utterance="놀았어")))
    # no luna call; the diary · coop rules ask the first empty slot (judge_rules.py · 10-12)
    assert result.reason == "jev" and result.next_slot == "place" and not result.story_ready
