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


# --- /turn with history: the act, what is taken back, what the line is told ---

def test_a_bare_denial_takes_back_what_otto_just_heard(client):
    out = client.post("/turn", json=body(utterance="그거 아니야", history=walked_to_park())).json()
    assert out["line"]["act"] == "repair"
    assert out["retract"] == ["companion"]
    # the denial is not an answer to the place question, nor a new companion
    assert out["judge"]["slot_1"] is None
    assert out["judge"]["next_slot"] == "companion"


def test_a_correction_with_the_right_value_fills_the_slot_taken_back(client):
    out = client.post("/turn", json=body(utterance="아니야, 고양이야", history=walked_to_park())).json()
    assert out["line"]["act"] == "repair" and out["retract"] == ["companion"]
    assert (out["judge"]["slot_1"], out["judge"]["value_1"]) == ("companion", "고양이야")
    assert "fixed_value" not in out["line"]           # server-side only — the value goes out as slot_1


def test_an_invented_value_is_dropped_with_its_question(client, monkeypatch):
    from app.routers import turn as turn_route
    monkeypatch.setattr(turn_route, "mock_fixed_value", lambda req: "고양이")
    out = client.post("/turn", json=body(utterance="그거 아니야", history=walked_to_park())).json()
    assert out["judge"]["slot_1"] is None and out["judge"]["next_slot"] == "companion"
    assert out["line"]["question"] is None


def test_the_repair_schema_adds_fixed_value_and_the_served_one_does_not():
    from app.routers import turn as turn_route
    assert "fixed_value" in turn_route.repair_schema()["required"]
    assert "fixed_value" not in turn_route.schema()["properties"]


def test_a_question_back_fills_nothing_and_asks_the_same_slot(client):
    out = client.post("/turn", json=body(utterance="오또는 뭐 좋아해?", history=walked_to_park())).json()
    assert out["line"]["act"] == "answer_back"
    assert out["judge"]["slot_1"] is None and out["judge"]["next_slot"] == "place"
    assert out["retract"] == []


def test_a_plain_answer_with_history_is_a_plain_turn(client):
    out = client.post("/turn", json=body(history=walked_to_park())).json()
    assert out["line"]["act"] is None and out["retract"] == []
    assert out["judge"]["slot_1"] == "place"


def test_the_act_prompt_is_added_only_when_there_is_an_act():
    from app.routers import turn as turn_route
    plain = turn_route.system("diary")
    assert turn_route.system_for("diary", None) == plain
    with_act = turn_route.system_for("diary", "repair")
    assert with_act.startswith(plain) and "repair" in with_act


def test_the_line_is_told_the_act_and_its_context():
    from app.dialogue import recipes
    from app.dialogue.decide import Decision
    from app.routers import turn as turn_route
    req = TurnRequest.model_validate(body(utterance="그거 아니야", history=walked_to_park()))
    d = Decision("correct", 0.9, "companion", 0.9, False)
    r = recipes.build("repair", req, d, ["companion"])
    text = turn_route.user(req, None, r)
    assert "act:repair" in text and "wrong:companion=강아지" in text and "retry_slot:companion" in text


def test_a_plain_line_input_is_unchanged_by_the_feature():
    from app.routers import turn as turn_route
    req = TurnRequest.model_validate(body(history=walked_to_park()))
    assert turn_route.user(req, None, None) == turn_route.user(req, None)
    assert "act:" not in turn_route.user(req, None)


# --- M: the line model picks the act itself (measurement only) ---

@pytest.fixture
def llm_client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    monkeypatch.setattr(settings, "dialogue_policy", "llm")
    return TestClient(app)


def test_the_llm_path_applies_the_same_rules_after_its_choice(llm_client):
    out = llm_client.post("/turn", json=body(utterance="그거 아니야", history=walked_to_park())).json()
    assert out["line"]["act"] == "repair" and out["retract"] == ["companion"]
    assert out["judge"]["slot_1"] is None


def test_the_llm_path_without_history_is_a_plain_turn(llm_client):
    out = llm_client.post("/turn", json=body()).json()
    assert out["line"]["act"] is None and out["retract"] == []


def test_the_choosing_schema_adds_act_target_and_new_value():
    from app.routers import turn as turn_route
    s = turn_route.choose_schema()
    assert {"act", "target", "new_value"} <= set(s["required"])
    assert None in s["properties"]["act"]["enum"] and "repair" in s["properties"]["act"]["enum"]
    assert "act" not in turn_route.schema()["properties"]      # the served schema is untouched
