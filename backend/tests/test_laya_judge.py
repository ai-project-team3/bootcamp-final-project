"""Laya judge (10-08): the short state, the 0.9 floor, rules for next_slot · story_ready, and the order
Laya → Jev → luna when one of them fails."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                    # noqa: E402
from app.llm import jev, laya                      # noqa: E402
from app.routers import judge                      # noqa: E402
from app.schemas.judge import JudgeRequest         # noqa: E402

REQUIRED_BUT_SOLUTION = {"place": "우주", "problem": "로켓이 흔들렸어", "cause": "심심해서", "newcomer": "외계인",
                         "sound": "삐리삐리"}


def req(slots=None, asked="place", utt="공룡 나라 갈래", mode="story", names=()):
    return JudgeRequest(mode=mode, slots=slots or {}, asked_slot=asked, question="어디 갈까?",
                        utterance=utt, names=list(names))


def ans(slot_1="place", conf=0.97, nxt="newcomer", nln="none", **noul):
    a = {"slot_1": {"choice": slot_1, "answer_confidence": conf},
         "slot_2": {"choice": "companion", "answer_confidence": 0.99},
         "next_slot": {"choice": nxt, "answer_confidence": 0.33},
         "no_longer_needed": {"choice": nln, "answer_confidence": 0.99}}
    for f in ("s1_reason", "s2_addition", "contradiction", "unclear"):
        a[f] = {"noul": noul.get(f, 0.1)}
    return a


def test_state_is_the_short_training_shape_with_names_masked():
    s = laya.state(req(slots={"newcomer": "외계인", "place": "우주"}, asked="name",
                       utt="지우가 뿌뿌라고 불렀어", names=["지우"]))
    assert s.splitlines() == ["모드: story", "채워진 칸: place=우주, newcomer=외계인", "오또가 물은 칸: name",
                              "오또 질문: 어디 갈까?", "아이 말: {주인공}가 뿌뿌라고 불렀어"]


def test_a_choice_under_the_floor_stays_empty_and_slot_2_is_dropped():
    out = laya.assemble(ans(conf=0.85), req())
    assert "slot_1" not in out and "value_1" not in out
    assert "slot_2" not in out                                  # can't split one answer without writing
    out = laya.assemble(ans(conf=0.95, s1_reason=0.8), req(utt="공룡 나라 갈래 거기 친구가 많아서 좋아 그리고 또"))
    assert out["slot_1"] == "place" and out["s1_reason"] is True and out["unclear"] is False
    assert len(out["value_1"].split()) == 8


def test_next_slot_must_be_empty_and_falls_back_to_the_fixed_order():
    assert laya.assemble(ans(nxt="problem"), req())["next_slot"] == "problem"
    # the model named a filled slot → the first empty one after this answer, in the fixed order
    out = laya.assemble(ans(nxt="place"), req(slots={"newcomer": "외계인"}))
    assert out["next_slot"] == "problem"
    assert laya.assemble(ans(nxt="title"), req())["next_slot"] == "newcomer"   # never the title


def test_story_ready_is_the_rule_not_the_model():
    done = laya.assemble(ans(slot_1="solution"), req(slots=REQUIRED_BUT_SOLUTION, asked="solution"))
    assert done["story_ready"] is True and "next_slot" not in done
    let_go = laya.assemble(ans(slot_1="none", nln="solution"), req(slots=REQUIRED_BUT_SOLUTION, asked="solution"))
    assert let_go["story_ready"] is True
    not_yet = laya.assemble(ans(slot_1="adult"), req(slots=REQUIRED_BUT_SOLUTION, asked="adult"))
    assert not_yet["story_ready"] is False and not_yet["next_slot"] == "reaction"   # first empty in the order


def test_coop_adds_the_reason_line_and_defaults_to_done():
    from app.schemas.turn import TurnRequest
    soon = TurnRequest(mode="coop", reason="soon", slots={"place": "소방서"}, asked_slot="problem",
                       question="거기서 뭐 할까?", utterance="호스로 물 뿌릴 거야")
    assert laya.state(soon).splitlines()[:2] == ["모드: coop", "이유: soon"]
    assert laya.state(req(mode="coop")).splitlines()[1] == "이유: done"
    assert "이유:" not in laya.state(req(mode="diary"))


def test_diary_ends_on_the_ending_question_and_asks_in_diary_order():
    day = {"place": "놀이터", "problem": "그네 탔어"}
    # 「몰라」 to the ending question still ends the diary — it moves on to tomorrow
    assert laya.assemble(ans(slot_1="none"), req(slots=day, asked="solution", mode="diary"))["story_ready"] is True
    mid = laya.assemble(ans(slot_1="problem"), req(slots={"place": "놀이터"}, asked="problem", mode="diary"))
    assert mid["story_ready"] is False and mid["next_slot"] == "reaction"


def test_laya_first_then_jev_then_luna(monkeypatch):
    calls = []

    async def fake_laya(r, timeout_s=3.0):
        calls.append("laya")
        return {"reason": "laya", "slot_1": "place", "value_1": r.utterance, "next_slot": "newcomer",
                "_seconds": 0.05}

    async def fake_jev(system, user, utterance, timeout_s=6.0):
        calls.append("jev")
        return {"reason": "jev", "slot_1": "place", "value_1": utterance, "next_slot": "problem"}

    async def fake_luna(*a, **k):
        calls.append("luna")
        return {"reason": "luna", "slot_1": "place", "value_1": "공룡 나라"}

    monkeypatch.setattr(laya, "judge", fake_laya)
    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(judge, "complete", fake_luna)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_laya_modes", "story")
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary")

    assert asyncio.run(judge.run(req())).reason == "laya"
    assert asyncio.run(judge.run(req(mode="diary"))).reason == "jev"           # only the modes switched on go to Laya

    async def down(*a, **k):
        raise laya.LayaError("network: ConnectError")
    monkeypatch.setattr(laya, "judge", down)
    assert asyncio.run(judge.run(req())).reason == "jev"                       # sidecar down → Jev
    monkeypatch.setattr(settings, "judge_jev_modes", "")
    assert asyncio.run(judge.run(req())).reason == "luna"                      # and with Jev off → luna
    assert calls == ["laya", "jev", "jev", "luna"]


def test_a_strange_sidecar_answer_falls_to_the_next_judge_not_a_500(monkeypatch):
    import httpx

    async def fake_luna(*a, **k):
        return {"reason": "luna", "slot_1": "place", "value_1": "공룡 나라"}

    real = httpx.AsyncClient
    monkeypatch.setattr(judge, "complete", fake_luna)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_laya_modes", "story")
    monkeypatch.setattr(settings, "judge_jev_modes", "")
    for body in (b"<html>busy</html>", b'{"answers": ["not", "a", "dict"]}'):
        transport = httpx.MockTransport(lambda request, b=body: httpx.Response(200, content=b))
        monkeypatch.setattr(laya.httpx, "AsyncClient", lambda **kw: real(transport=transport, **kw))
        assert asyncio.run(judge.run(req())).reason == "luna"

    async def bad_shape(r, timeout_s=3.0):
        return {"reason": "laya", "slot_1": "spaceship"}              # not a slot name — JudgeResult refuses it
    monkeypatch.setattr(laya, "judge", bad_shape)
    assert asyncio.run(judge.run(req())).reason == "luna"


def test_extra_is_a_slot_the_result_takes():
    from app.schemas.judge import SLOT_NAMES
    assert "extra" in SLOT_NAMES


def test_off_by_default():
    # the field's own default, not the live settings — a server .env with JUDGE_LAYA_MODES must not fail this
    from app.config import Settings
    assert Settings.model_fields["judge_laya_modes"].default == ""
