"""Jev judge (10-05): per-mode switch, luna fallback, the 0.6 floor, value_1 from the child's words."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                    # noqa: E402
from app.llm import jev                            # noqa: E402
from app.routers import judge                      # noqa: E402
from app.schemas.judge import JudgeRequest         # noqa: E402


def req(mode="story", utt="공룡 나라에 가서 커다란 알을 찾았어 그리고 엄마한테 보여 줬어"):
    return JudgeRequest(mode=mode, slots={}, asked_slot="place", question="어디 갈까?", utterance=utt)


def test_choices_under_the_floor_stay_empty_and_value_is_eight_eojeol():
    out = jev.assemble({
        "slot_1": {"choice": "place", "confidence": 0.9},
        "next_slot": {"choice": "problem", "confidence": 0.4},          # under 0.6 → empty
        "no_longer_needed": {"choice": "none", "confidence": 0.99},     # "none" is our null
        "story_ready": {"noul": 0.2},
    }, req().utterance)
    assert out["slot_1"] == "place" and "next_slot" not in out and "no_longer_needed" not in out
    assert out["story_ready"] is False
    assert len(out["value_1"].split()) == 8


def test_only_switched_on_modes_use_jev_and_a_failure_falls_back(monkeypatch):
    calls = []

    async def fake_jev(system, user, utterance, timeout_s=6.0, mode="story"):
        calls.append("jev")
        return {"reason": "jev", "slot_1": "place", "value_1": utterance,
                "next_slot": "problem", "_seconds": 0.2}

    async def fake_luna(*a, **k):
        calls.append("luna")
        return {"reason": "luna", "slot_1": "place", "value_1": "공룡 나라"}

    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(judge, "complete", fake_luna)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")

    assert asyncio.run(judge.run(req("story"))).reason == "jev"
    assert asyncio.run(judge.run(req("coop"))).reason == "luna"      # co-op stays on luna

    async def broken(*a, **k):
        raise jev.JevError("HTTP 503")
    monkeypatch.setattr(jev, "judge", broken)
    assert asyncio.run(judge.run(req("story"))).reason == "luna"     # Jev down → luna, the child never waits
    assert calls == ["jev", "luna", "luna"]


def test_names_never_reach_jev_but_luna_keeps_them(monkeypatch):
    sent = {}

    async def fake_jev(system, user, utterance, timeout_s=6.0, mode="story"):
        sent["user"], sent["utterance"] = user, utterance
        return {"reason": "jev", "slot_1": "companion", "value_1": utterance, "next_slot": "problem"}

    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_jev_modes", "story")
    r = JudgeRequest(mode="story", slots={"name": "민수"}, asked_slot="companion", question="지우는 누구랑 갔어?",
                     utterance="지우는 민수랑 김민수 형이랑 갔어", names=["지우", "민수", "김민수"])
    out = asyncio.run(judge.run(r))
    assert "지우" not in sent["user"] and "민수" not in sent["user"]
    assert sent["utterance"] == "{주인공}는 {친구1}랑 {친구2} 형이랑 갔어"   # longest name first
    assert out.value_1.startswith("{주인공}")                                    # the app unmasks it
    assert r.utterance.startswith("지우")                                         # the request itself is untouched


def test_diary_and_coop_end_and_ask_by_the_rules_a_story_keeps_jevs_verdict(monkeypatch):
    # 10-12: every mode back on Jev. Its own story_ready / next_slot are guesses about the diary order
    calls = []
    answer = {}

    async def fake_jev(system, user, utterance, timeout_s=6.0, mode="story"):
        calls.append("jev")
        return {"reason": "jev", **answer, "_seconds": 0.2}

    async def fake_luna(*a, **k):
        calls.append("luna")
        return {"reason": "luna", "story_ready": True}

    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(judge, "complete", fake_luna)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_laya_modes", "")
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary,coop")

    # diary: the next slot is the first empty in diary order, not Jev's pick
    answer.update(slot_1="place", next_slot="cause", story_ready=True)
    d = asyncio.run(judge.run(JudgeRequest(mode="diary", slots={}, asked_slot="place", question="어디 갔어?",
                                           utterance="할머니 집")))
    assert d.reason == "jev" and d.next_slot == "problem" and d.story_ready is False
    # the ending question answered in any way ends it, 「몰라」 too
    answer.clear()
    answer.update(story_ready=False)
    end = asyncio.run(judge.run(JudgeRequest(mode="diary", slots={"place": "할머니 집", "problem": "송편 빚었어"},
                                             asked_slot="solution", question="어떻게 됐어?", utterance="몰라")))
    assert end.story_ready is True and end.next_slot is None
    # coop reads the same
    answer.update(slot_1="problem", story_ready=False)
    c = asyncio.run(judge.run(JudgeRequest(mode="coop", slots={"place": "동물원"}, asked_slot="problem",
                                           question="거기서 뭐 할 거야?", utterance="기린한테 먹이 줄 거야")))
    assert c.next_slot == "reaction" and c.story_ready is False
    assert "luna" not in calls

    # a story keeps Jev's own verdict (its required slots depend on the template)
    answer.clear()
    answer.update(slot_1="newcomer", next_slot="sound", story_ready=False)
    s = asyncio.run(judge.run(JudgeRequest(mode="story", slots={"place": "우주"}, asked_slot="newcomer",
                                           question="누굴 만났어?", utterance="꼬리가 파란 용이야")))
    assert s.reason == "jev" and s.next_slot == "sound"


def test_diary_and_coop_ask_for_another_slot_a_story_keeps_its_wording(monkeypatch):
    # #440: the off-slot wording found 23 of 57 diary · coop answers to another slot (9 before) but cost a
    # story 10 of its labelled 100, so it goes to diary · coop only
    story, coop = jev.questions(), jev.questions("coop")
    assert story["slot_1"]["instructions"] == jev.CHOICE_Q["slot_1"]
    assert story["slot_1"]["criteria"]["companion"] == jev.SLOT_CRITERIA["companion"]
    assert "달라도 된다" in coop["slot_1"]["instructions"]
    assert "아빠" in coop["slot_1"]["criteria"]["companion"] and "아빠" in coop["next_slot"]["criteria"]["companion"]
    assert coop.keys() == story.keys()

    seen = []

    async def fake_jev(system, user, utterance, timeout_s=6.0, mode="story"):
        seen.append(mode)
        return {"reason": "jev", "slot_1": "companion", "_seconds": 0.2}

    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_laya_modes", "")
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary,coop")
    asyncio.run(judge.run(JudgeRequest(mode="coop", slots={"place": "동물원"}, asked_slot="companion",
                                       question="누구랑 갔어?", utterance="아빠")))
    assert seen == ["coop"]


def test_mask_names_skips_blanks_and_placeholders():
    assert jev.mask_names("친구랑 놀았어", ["", "{친구1}"]) == "친구랑 놀았어"
    assert jev.mask_names("아무 이름 없음", []) == "아무 이름 없음"


def test_off_by_default():
    assert settings.judge_jev_modes == ""
