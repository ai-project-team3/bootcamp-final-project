"""Dialogue repair (#323): the history the app sends, the act the server picks, the slots it takes back."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                  # noqa: E402
from app.schemas.judge import SLOT_NAMES         # noqa: E402
from app.schemas.turn import TurnRequest, TurnResult   # noqa: E402
from main import app                             # noqa: E402

EMPTY = {k: None for k in SLOT_NAMES}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    return TestClient(app)


def walked_to_park():
    """One diary turn already done: Otto heard 「강아지랑」 and said so."""
    return [{
        "turn": 1, "asked_slot": "companion",
        "otto": {"ack": "강아지랑 놀았구나!", "expand": None, "question": "누구랑 같이 갔어?"},
        "child": {"text": "강아지랑", "by": "child"},
        "fills": [{"slot": "companion", "value": "강아지", "prev": None}],
        "act": None,
    }]


def body(**over):
    b = {"mode": "diary", "slots": {**EMPTY, "companion": "강아지"}, "asked_slot": "place",
         "question": "어디에서 놀았어?", "utterance": "놀이터"}
    b.update(over)
    return b


# --- the contract: every new field is optional, so older apps and other modes are unchanged ---

def test_history_is_optional_and_defaults_empty():
    req = TurnRequest.model_validate(body())
    assert req.history == []


def test_history_keeps_who_said_it():
    req = TurnRequest.model_validate(body(history=walked_to_park()))
    h = req.history[0]
    assert h.child.by == "child" and h.fills[0].slot == "companion" and h.otto.ack.startswith("강아지")


def test_history_rejects_an_unknown_speaker():
    bad = walked_to_park()
    bad[0]["child"]["by"] = "parent"
    with pytest.raises(ValueError):
        TurnRequest.model_validate(body(history=bad))


def test_history_rejects_an_invented_slot_name():
    bad = walked_to_park()
    bad[0]["fills"][0]["slot"] = "pet"
    with pytest.raises(ValueError):
        TurnRequest.model_validate(body(history=bad))


def test_result_defaults_take_nothing_back():
    assert TurnResult().retract == []


def test_a_turn_without_history_answers_as_before(client):
    out = client.post("/turn", json=body()).json()
    assert out["retract"] == [] and out["line"]["act"] is None
