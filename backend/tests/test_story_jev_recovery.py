"""#156: a non-final Jev verdict with no next slot must not feed another open-ended turn."""
import asyncio
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings
from app.llm import jev
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


def test_recovery_can_ask_for_a_missing_ending_without_forcing_completion(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")

    async def fast(*args, **kwargs):
        return {"reason": "jev", "story_ready": False}

    async def full(*args, **kwargs):
        return {"reason": "ending is missing", "story_ready": False, "next_slot": "solution"}

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", full)
    slots = {k: v for k, v in STORY.items() if k != "solution"}
    result = asyncio.run(judge.run(JudgeRequest(
        slots=slots, template="E", question="그다음에는?", utterance="더 없어",
    )))
    assert result.story_ready is False and result.next_slot == "solution"


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
def test_other_modes_keep_their_existing_jev_path(monkeypatch, mode):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary,coop")

    async def fast(*args, **kwargs):
        return {"reason": "jev", "story_ready": False}

    async def forbidden(*args, **kwargs):
        pytest.fail("story recovery must not change the other modes")

    monkeypatch.setattr(jev, "judge", fast)
    monkeypatch.setattr(judge, "complete", forbidden)
    result = asyncio.run(judge.run(JudgeRequest(mode=mode, slots={}, question="그다음에는?", utterance="놀았어")))
    assert result.reason == "jev" and result.next_slot is None and not result.story_ready
