"""The four routes answer the spec's shapes (guidelines/3 §3). No keys, no GPU: MOCK=1."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings          # noqa: E402
from app.llm import judge_prompt         # noqa: E402
from app.schemas.judge import JudgeRequest, SLOT_NAMES   # noqa: E402
from main import app                     # noqa: E402

EMPTY = {k: None for k in SLOT_NAMES}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    return TestClient(app)


def judge_body(**over):
    body = {"mode": "diary", "slots": dict(EMPTY), "asked_slot": "place",
            "question": "오늘 어디 갔었어?", "utterance": "놀이터 갔어"}
    body.update(over)
    return body


def test_health_says_whether_it_is_mock(client):
    assert client.get("/health").json() == {"status": "ok", "mock": True}


def test_judge_answers_all_sixteen_fields(client):
    r = client.post("/judge", json=judge_body())
    assert r.status_code == 200
    out = r.json()
    assert set(out) == set(judge_prompt.schema()["required"]), "response drifted from the schema"
    assert out["slot_1"] == "place" and out["value_1"] == "놀이터 갔어"
    assert out["next_slot"] == "problem"


def test_judge_rejects_an_unknown_mode(client):
    r = client.post("/judge", json=judge_body(mode="game"))
    assert r.status_code == 422
    assert r.json()["error"] is True, "errors must use the spec §3-0 shape"


def test_mode_defaults_to_story_for_old_callers():
    assert JudgeRequest(slots={}, question="q", utterance="u").mode == "story"


def test_never_asks_again_for_a_filled_slot(client):
    slots = {**EMPTY, "place": "놀이터", "problem": "넘어졌어", "reaction": "울었어",
             "cause": "뛰어서", "solution": "일어났어"}
    out = client.post("/judge", json=judge_body(slots=slots, asked_slot="extra", utterance="또 가고 싶어")).json()
    assert out["next_slot"] is None and out["story_ready"] is True


def test_the_user_prompt_carries_mode_and_question():
    text = judge_prompt.user(JudgeRequest(**judge_body()))
    assert "mode:diary" in text and "question:오늘 어디 갔었어?" in text


def test_the_system_prompt_is_the_measured_one():
    # One place only: the server reads eval/judge_prompt.md, it does not keep a copy
    measured = (judge_prompt.EVAL / "judge_prompt.md").read_text(encoding="utf-8").strip()
    assert judge_prompt.system().startswith(measured)


def test_stt_returns_text(client):
    r = client.post("/stt", files={"file": ("a.wav", b"RIFF....", "audio/wav")})
    assert r.status_code == 200 and isinstance(r.json()["text"], str)


def test_stt_refuses_empty_audio(client):
    r = client.post("/stt", files={"file": ("a.wav", b"", "audio/wav")})
    assert r.status_code == 400 and r.json()["error"] is True


def test_tts_returns_audio(client):
    r = client.post("/tts", json={"text": "놀이터 갔구나! 거기서 뭐 했어?"})
    assert r.status_code == 200 and r.headers["content-type"].startswith("audio/")
    assert r.content[:4] == b"RIFF"


def test_a_broken_body_still_gets_the_spec_error_shape(client):
    r = client.post("/judge", content=b"{not json", headers={"Content-Type": "application/json"})
    assert r.status_code in (400, 422) and r.json()["error"] is True


def test_tts_refuses_empty_text(client):
    assert client.post("/tts", json={"text": ""}).status_code == 422
