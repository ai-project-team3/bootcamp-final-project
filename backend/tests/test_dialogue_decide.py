"""Dialogue repair (#323): the short state the quick decider reads, and how its answers come back."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                  # noqa: E402
from app.dialogue import decide as decide_mod    # noqa: E402
from app.dialogue.decide import decide, from_answers, state_text   # noqa: E402
from app.llm import jev                          # noqa: E402
from app.schemas.judge import SLOT_NAMES         # noqa: E402
from app.schemas.turn import TurnRequest         # noqa: E402

EMPTY = {k: None for k in SLOT_NAMES}


def req(utterance="그거 아니야", history=True, **over):
    b = {
        "mode": "diary", "slots": {**EMPTY, "place": "놀이터"},
        "asked_slot": "problem", "question": "놀이터에서 무슨 일이 있었어?", "utterance": utterance,
        "names": ["민지"],
        "history": [
            {"turn": 1, "asked_slot": "place",
             "otto": {"ack": "놀이터에 갔구나!", "question": "어디에서 놀았어?"},
             "child": {"text": "놀이터", "by": "child"},
             "fills": [{"slot": "place", "value": "놀이터"}]},
        ] if history else [],
    }
    b.update(over)
    return TurnRequest.model_validate(b)


def test_the_state_carries_otto_s_last_words_and_the_child_s_reply():
    s = state_text(req())
    assert "놀이터에 갔구나!" in s and "그거 아니야" in s and "place=놀이터" in s


def test_the_state_never_passes_otto_off_as_the_child():
    assert "아이: 놀이터에 갔구나!" not in state_text(req())


def test_the_state_shows_the_question_just_asked():
    assert "오또: 놀이터에서 무슨 일이 있었어?" in state_text(req())


def test_answers_come_back_as_a_decision():
    d = from_answers({"intent": {"choice": "correct", "confidence": 0.91},
                      "target": {"choice": "place", "confidence": 0.7}, "new_value": {"noul": 0.2}})
    assert (d.intent, d.target, d.new_value) == ("correct", "place", False)


def test_an_unknown_intent_is_no_opinion():
    assert from_answers({"intent": {"choice": "joke", "confidence": 0.99}}) is None


def test_the_target_none_is_no_slot():
    d = from_answers({"intent": {"choice": "ask_back", "confidence": 0.9}, "target": {"choice": "none"}})
    assert d.target is None


def test_no_history_means_no_call(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)

    async def boom(*a, **k):
        raise AssertionError("called")
    monkeypatch.setattr(jev, "ask", boom)
    assert asyncio.run(decide(req(history=False))) is None


def test_a_failed_decider_leaves_a_plain_turn(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "dialogue_decider", "jev")

    async def fail(*a, **k):
        raise jev.JevError("HTTP 500")
    monkeypatch.setattr(jev, "ask", fail)
    assert asyncio.run(decide(req())) is None


def test_names_are_masked_before_they_leave(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "dialogue_decider", "jev")
    seen = {}

    async def fake(state, qs, timeout_s):
        seen["state"] = state
        return {"intent": {"choice": "answer", "confidence": 0.9}}, 0.1
    monkeypatch.setattr(jev, "ask", fake)
    asyncio.run(decide(req(utterance="민지가 그네 탔어")))
    assert "민지" not in seen["state"] and "{주인공}" in seen["state"]


def test_the_mock_reads_a_bare_denial_and_one_with_the_right_value():
    assert decide_mod.mock(req("그거 아니야")).new_value is False
    assert decide_mod.mock(req("아니야, 바닷가야")).new_value is True


def test_the_state_runs_in_the_order_it_was_said():
    # question → the child's answer → Otto's reaction to it; then the question just asked (10-09: the
    # reaction came first, so Otto seemed to settle 「아빠랑 갔구나!」 before the child answered)
    lines = state_text(req()).splitlines()
    order = [lines.index(x) for x in ("오또: 어디에서 놀았어?", "아이: 놀이터", "오또: 놀이터에 갔구나!",
                                      "오또: 놀이터에서 무슨 일이 있었어?", "아이(방금): 그거 아니야")]
    assert order == sorted(order)
