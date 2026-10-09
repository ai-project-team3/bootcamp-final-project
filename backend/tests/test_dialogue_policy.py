"""Dialogue repair (#323): the rule table from intent to act, the slots taken back, the recipes."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.dialogue import policy, recipes          # noqa: E402
from app.dialogue.decide import Decision   # noqa: E402
from app.schemas.judge import JudgeResult, SLOT_NAMES   # noqa: E402
from app.schemas.turn import TurnRequest         # noqa: E402

EMPTY = {k: None for k in SLOT_NAMES}


def req(utterance="그거 아니야", **over):
    b = {
        "mode": "diary", "slots": {**EMPTY, "companion": "강아지", "place": "놀이터"},
        "asked_slot": "problem", "question": "놀이터에서 무슨 일이 있었어?", "utterance": utterance,
        "history": [
            {"turn": 1, "asked_slot": "companion",
             "otto": {"ack": "강아지랑 갔구나!", "question": "누구랑 같이 갔어?"},
             "child": {"text": "강아지랑", "by": "child"},
             "fills": [{"slot": "companion", "value": "강아지"}]},
            {"turn": 2, "asked_slot": "place",
             "otto": {"ack": "놀이터에 갔구나!", "question": "어디에서 놀았어?"},
             "child": {"text": "놀이터", "by": "child"},
             "fills": [{"slot": "place", "value": "놀이터"}]},
        ],
    }
    b.update(over)
    return TurnRequest.model_validate(b)


def d(intent, conf=0.9, target=None, target_conf=0.9, new_value=False):
    return Decision(intent=intent, intent_conf=conf, target=target, target_conf=target_conf, new_value=new_value)


# --- the rule table: what the child meant → what Otto does ---

def test_a_plain_answer_is_a_plain_turn():
    assert policy.act_for(d("answer")) is None


def test_each_non_answer_has_its_act():
    assert policy.act_for(d("correct")) == "repair"
    assert policy.act_for(d("ask_back")) == "answer_back"
    assert policy.act_for(d("not_heard")) == "rephrase"
    assert policy.act_for(d("aside")) == "aside"


def test_refusal_is_left_to_the_modes_own_ladder():
    assert policy.act_for(d("refuse")) is None


def test_a_child_still_telling_is_a_plain_turn_this_round():
    assert policy.act_for(d("continue")) is None


def test_a_costly_intent_needs_the_higher_floor():
    # mistaking an answer for a correction throws the child's words away — 0.8, not 0.6 (#323 Q17)
    assert policy.act_for(d("correct", conf=0.7)) is None
    assert policy.act_for(d("correct", conf=0.8)) == "repair"
    assert policy.act_for(d("ask_back", conf=0.6)) == "answer_back"
    assert policy.act_for(d("ask_back", conf=0.55)) is None


def test_no_decision_is_a_plain_turn():
    assert policy.act_for(None) is None


# --- what a repair takes back ---

def test_repair_takes_back_the_slot_the_child_named():
    assert policy.retract_for("repair", d("correct", target="place"), req()) == ["place"]


def test_an_unsure_target_falls_back_to_the_last_slot_otto_filled():
    # Otto's last 「~구나!」 was about the place, so that is what 「그거 아니야」 denies
    assert policy.retract_for("repair", d("correct", target="companion", target_conf=0.4), req()) == ["place"]


def test_nothing_is_taken_back_from_an_empty_slot():
    r = req(slots={**EMPTY})
    assert policy.retract_for("repair", d("correct", target="place"), r) == []


def test_only_a_repair_takes_anything_back():
    assert policy.retract_for("answer_back", d("ask_back", target="place"), req()) == []


# --- what the verdict may still fill ---

def test_a_bare_denial_does_not_refill_the_slot_it_denied():
    v = JudgeResult(reason="jev", slot_1="place", value_1="그거 아니야")
    out = policy.trim_verdict("repair", d("correct", target="place"), v)
    assert out.slot_1 is None and out.value_1 is None


def test_a_correction_s_value_does_not_come_from_the_judge():
    # the judge saw the wrong value still in place; the value comes from the line model (settle_repair)
    v = JudgeResult(reason="x", slot_1="extra", value_1="바다 아니야, 수영장이야")
    out = policy.trim_verdict("repair", d("correct", target="place", new_value=True), v)
    assert out.slot_1 is None and out.value_1 is None


def test_a_question_back_fills_nothing():
    v = JudgeResult(reason="x", slot_1="extra", value_1="오또는 뭐 좋아해")
    out = policy.trim_verdict("answer_back", d("ask_back"), v)
    assert out.slot_1 is None


def test_an_aside_keeps_what_the_judge_filed():
    v = JudgeResult(reason="x", slot_1="extra", value_1="어제 비 왔어")
    assert policy.trim_verdict("aside", d("aside"), v).slot_1 == "extra"


# --- recipes: which slot the question is about, and what context the line gets ---

def test_a_repair_tells_the_line_the_wrong_value_and_the_slot_to_ask_again():
    r = recipes.build("repair", req(), d("correct", target="place"), ["place"])
    assert "wrong:place=놀이터" in r.context and "retry_slot:place" in r.context
    assert "놀이터에 갔구나!" in r.context
    assert r.question_slot is None      # settled after the line: a value moves on, none asks again


# --- the corrected value: the decider's slot, the line model's copy of the child's words ---

def test_the_child_s_own_words_pass():
    assert policy.from_the_child("수영장", "바다 아니야, 수영장이야")
    assert policy.from_the_child("엄마 랑", "아니야, 엄마랑 갔어")         # spaces aside


def test_a_typo_the_child_s_transcript_has_is_kept_as_it_is():
    assert policy.from_the_child("수영쟝", "바다 아니야 수영쟝이야")
    assert not policy.from_the_child("수영장", "바다 아니야 수영쟝이야")   # a fixed typo is not in the words


def test_a_value_the_child_did_not_say_is_refused():
    assert not policy.from_the_child("고양이", "그거 아니야")
    assert not policy.from_the_child("", "아니야")
    assert not policy.from_the_child(None, "아니야")


def test_settle_puts_the_value_in_the_slot_taken_back():
    r = req("바다 아니야, 놀이공원이야")
    v, kept = policy.settle_repair("repair", r, JudgeResult(reason="x", next_slot="problem"), "놀이공원", ["place"])
    assert (v.slot_1, v.value_1, v.next_slot, kept) == ("place", "놀이공원", "problem", True)


def test_settle_without_a_value_asks_the_slot_again():
    v, kept = policy.settle_repair("repair", req(), JudgeResult(reason="x", next_slot="problem"), None, ["place"])
    assert v.slot_1 is None and v.next_slot == "place" and kept


def test_settle_with_an_invented_value_asks_again_and_drops_the_line_s_question():
    v, kept = policy.settle_repair("repair", req("그거 아니야"), JudgeResult(reason="x"), "공원", ["place"])
    assert v.slot_1 is None and v.next_slot == "place" and kept is False


def test_settle_carries_the_value_even_when_the_judge_failed():
    v, _ = policy.settle_repair("repair", req("아니야 공원이야"), None, "공원", ["place"])
    assert v.slot_1 == "place" and v.value_1 == "공원"


def test_settle_leaves_other_acts_alone():
    v = JudgeResult(reason="x", next_slot="problem")
    assert policy.settle_repair("answer_back", req(), v, None, []) == (v, True)


def test_the_same_question_comes_back_after_a_question_back():
    r = recipes.build("answer_back", req("오또는 뭐 좋아해?"), d("ask_back"), [])
    assert r.question_slot == "problem" and "놀이터에서 무슨 일이 있었어?" in r.context


def test_every_act_has_a_recipe():
    assert set(recipes.RECIPES) == {"repair", "answer_back", "rephrase", "aside"}
