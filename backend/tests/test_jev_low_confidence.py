"""#440 follow-up (10-14): a diary · coop slot Jev picked under its floor — leave it, or ask luna."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402

from app.config import settings                    # noqa: E402
from app.llm import jev                            # noqa: E402
from app.llm.client import LLMError                # noqa: E402
from app.routers import judge                      # noqa: E402
from app.schemas.judge import JudgeRequest         # noqa: E402


def req(mode="coop"):
    return JudgeRequest(mode=mode, slots={"place": "사자 우리"}, asked_slot="problem",
                        question="무슨 일이 있었어?", utterance="사자가 크게 울어서 깜짝 놀랐어", reason="done")


@pytest.fixture
def served(monkeypatch):
    calls = []
    jev_pick = {"label": "problem 0.52"}
    luna = {"slot_1": "problem", "slot_2": "reaction"}

    async def fake_jev(system, user, utterance, timeout_s=6.0, mode="story"):
        calls.append("jev")
        return {"reason": "jev", "_seconds": 0.2, "_slot_1": jev_pick["label"]}

    async def fake_luna(*a, **k):
        calls.append(("luna", k.get("timeout_s")))
        if luna.get("fail"):
            raise LLMError("over 4s")
        return {"reason": "luna", "slot_1": luna["slot_1"], "value_1": "x", "slot_2": luna["slot_2"],
                "value_2": "y" if luna["slot_2"] else None}

    monkeypatch.setattr(jev, "judge", fake_jev)
    monkeypatch.setattr(judge, "complete", fake_luna)
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "judge_laya_modes", "")
    monkeypatch.setattr(settings, "judge_jev_modes", "story,diary,coop")
    return calls, jev_pick, luna


def test_off_by_default_leaves_the_slot_empty(served):
    calls, _, _ = served
    assert settings.jev_low_confidence == ""
    v = asyncio.run(judge.run(req()))
    assert v.slot_1 is None and calls == ["jev"]


def test_agree_keeps_jevs_pick_when_luna_names_it_too(served, monkeypatch):
    calls, _, _ = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    v = asyncio.run(judge.run(req()))
    assert v.slot_1 == "problem" and v.value_1 == "사자가 크게 울어서 깜짝 놀랐어"
    assert v.next_slot == "reaction"            # the diary · coop rule runs again on the filled slot
    assert calls == ["jev", ("luna", settings.jev_low_confidence_deadline_s)]


def test_agree_asks_again_when_luna_disagrees(served, monkeypatch):
    _, _, luna = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    luna.update(slot_1="cause", slot_2=None)
    assert asyncio.run(judge.run(req())).slot_1 is None


def test_agree_needs_lunas_first_slot_not_its_second(served, monkeypatch):
    _, _, luna = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    luna.update(slot_1="place", slot_2="problem")      # 「아빠랑 사자 있는 데 갔어」 shape: only luna's second slot
    assert asyncio.run(judge.run(req())).slot_1 is None


def test_agree_skips_luna_under_the_minimum_or_on_a_denial(served, monkeypatch):
    calls, jev_pick, _ = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    jev_pick["label"] = "problem 0.40"
    assert asyncio.run(judge.run(req())).slot_1 is None and calls == ["jev"]
    jev_pick["label"] = "problem 0.52"
    for words in ("아니야, 쿠키 안 구웠어. 그런 거 안 했다니까.", "나 축구장 안 갔어. 오늘 수영장 갔다니까!"):
        calls.clear()
        r = req()
        r.utterance = words
        assert asyncio.run(judge.run(r)).slot_1 is None and calls == ["jev"]
    for words in ("하나도 안 젖었어", "우주선이 별에 콕 박혀서 안 빠졌어."):   # real answers with 안 — not denials
        assert judge._DENIAL.search(words) is None


def test_luna_mode_takes_lunas_verdict(served, monkeypatch):
    _, _, luna = served
    monkeypatch.setattr(settings, "jev_low_confidence", "luna")
    luna.update(slot_1="reaction", slot_2=None)
    assert asyncio.run(judge.run(req())).slot_1 == "reaction"


def test_a_luna_failure_or_a_confident_or_none_pick_changes_nothing(served, monkeypatch):
    calls, jev_pick, luna = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    luna["fail"] = True
    assert asyncio.run(judge.run(req())).slot_1 is None        # luna down: ask again, never a 502
    luna.pop("fail")
    for label in ("none 0.9", "problem 0.71", "- -"):           # 「none」 · above the floor (enforce dropped it) · no label
        calls.clear()
        jev_pick["label"] = label
        assert asyncio.run(judge.run(req())).slot_1 is None and calls == ["jev"]


def test_a_story_never_asks_luna_here(served, monkeypatch):
    calls, _, _ = served
    monkeypatch.setattr(settings, "jev_low_confidence", "agree")
    s = JudgeRequest(mode="story", slots={"place": "우주"}, asked_slot="problem", question="무슨 일?",
                     utterance="별이 떨어졌어")
    asyncio.run(judge.run(s))
    assert ("luna", settings.jev_low_confidence_deadline_s) not in calls
