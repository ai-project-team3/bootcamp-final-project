"""POST /turn: the verdict and the mascot's three pieces, each surviving the other's failure."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                  # noqa: E402
from app.filters.blocklist import BLOCK         # noqa: E402
from app.llm.client import LLMError              # noqa: E402
from app.routers import judge as judge_route     # noqa: E402
from app.routers import turn as turn_route       # noqa: E402
from app.schemas.judge import JudgeResult, SLOT_NAMES   # noqa: E402
from app.schemas.turn import Line, TurnRequest   # noqa: E402
from main import app                             # noqa: E402

EMPTY = {k: None for k in SLOT_NAMES}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    return TestClient(app)


def body(**over):
    b = {"mode": "story", "slots": dict(EMPTY), "asked_slot": "place",
         "question": "어디로 가 볼까?", "utterance": "공룡 나라"}
    b.update(over)
    return b


def test_turn_gives_verdict_and_line(client):
    out = client.post("/turn", json=body()).json()
    assert out["judge"]["slot_1"] == "place"
    assert set(out["line"]) == {"ack", "expand", "question"}
    assert out["line"]["ack"] and out["line"]["question"]


def test_coop_with_a_parent_question_leaves_the_question_empty(client):
    out = client.post("/turn", json=body(mode="coop", ask=False)).json()
    assert out["line"]["ack"] and out["line"]["question"] is None


def test_story_ready_stops_asking(client):
    slots = {**EMPTY, "place": "놀이터", "problem": "넘어졌어", "reaction": "울었어",
             "cause": "뛰어서", "solution": "일어났어"}
    out = client.post("/turn", json=body(slots=slots, asked_slot="extra", utterance="또 가고 싶어")).json()
    assert out["judge"]["story_ready"] is True and out["line"]["question"] is None


def test_a_blocked_utterance_gets_no_line(client):
    word = next(iter(BLOCK))
    out = client.post("/turn", json=body(utterance=f"{word} 했어")).json()
    assert out["judge"]["reason"] == "blocked_by_filter" and out["line"] is None


def test_a_failed_judge_still_leaves_a_line(client, monkeypatch):
    async def boom(_):
        raise LLMError("down")
    monkeypatch.setattr(judge_route, "run", boom)
    out = client.post("/turn", json=body()).json()
    assert out["judge"] is None and out["line"]["ack"]


def test_both_failing_is_a_502_in_the_spec_shape(client, monkeypatch):
    async def boom(*_):
        raise LLMError("down")

    async def no_line(*_):
        return None
    monkeypatch.setattr(judge_route, "run", boom)
    monkeypatch.setattr(turn_route, "run_line", no_line)
    r = client.post("/turn", json=body())
    assert r.status_code == 502 and r.json()["error"] is True


def test_check_drops_unsafe_lines():
    word = next(iter(BLOCK))
    assert turn_route.check(Line(ack=f"{word} 했구나"))
    assert turn_route.check(Line(ack="그랬구나", question="{철수}는 어디 갔을까?"))
    assert turn_route.check(Line(ack="그랬구나", expand="{친구1}도 같이 갔대.", question="그다음엔?")) is None


def test_the_line_prompt_is_the_fenced_block_only():
    s = turn_route.system()
    assert s.startswith("당신은"), s[:40]
    assert "근거:" not in s and "```" not in s
    assert set(turn_route.schema()["required"]) == {"ack", "expand", "question"}


def test_user_message_carries_ask_and_only_a_real_unclear():
    req = TurnRequest(mode="coop", slots={}, question="q", utterance="공룡", ask=False)
    v = JudgeResult(reason="r", value_1="공룡", next_slot="problem", unclear=False, unclear_of="공룡")
    text = turn_route.user(req, v)
    assert "ask:false" in text and "next_slot:problem" in text
    assert "unclear_of:\n" in text, "unclear_of is sent only when unclear is true"
    assert "next_slot:\n" in turn_route.user(req, None)


def test_the_line_model_sees_the_story_so_far():
    """#50 · 10-01: the line model got only the last answer, so it asked about places and
    characters that were not in the story. Filled slots go with it; empty ones do not."""
    req = TurnRequest(mode="story", slots={"place": "바닷속", "newcomer": "문어", "problem": None, "cause": ""},
                      question="누굴 만났어?", utterance="문어")
    text = turn_route.user(req, None)
    assert text.startswith("story_so_far:place=바닷속 · newcomer=문어\n")
    assert "problem=" not in text and "cause=" not in text
    assert "지금까지의 이야기" in turn_route.system()
