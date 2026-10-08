"""#303 — who sits with the child: the judge stays off the adult slot when nobody does, and
POST /partner reads 「누구랑?」 answers with one Jev choice question."""
import asyncio
import sys
from pathlib import Path

from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                    # noqa: E402
from app.llm import jev                            # noqa: E402
from app.routers import judge                      # noqa: E402
from app.schemas.judge import JudgeRequest, JudgeResult   # noqa: E402
from main import app                               # noqa: E402


def req(partner=None, slots=None):
    return JudgeRequest(slots=slots or {}, question="그래서 어떻게 됐어?", utterance="엄마가 도와줬어",
                        partner=partner)


def test_alone_the_adult_slot_is_never_asked_and_the_next_beat_is():
    """Dropping next_slot alone left the app asking 「조금 더 들려줄래?」 every turn."""
    v = judge.enforce(JudgeResult(reason="r", next_slot="adult"), req("none", {"place": "공원"}))
    assert v.next_slot == "problem"
    full = {s: "x" for s in ("place", "problem", "reaction", "cause", "solution")}
    assert judge.enforce(JudgeResult(reason="r", next_slot="adult"), req("none", full)).next_slot is None


def test_alone_a_word_filed_under_adult_keeps_its_text_in_extra():
    v = judge.enforce(JudgeResult(reason="r", slot_1="adult", value_1="엄마가 도와줬어", slot_2="adult"),
                      req("none"))
    assert (v.slot_1, v.value_1) == ("extra", "엄마가 도와줬어")
    assert v.slot_2 is None                                          # no value, nothing to keep


def test_with_someone_or_an_older_app_the_adult_slot_stays():
    for partner in ("adult", "peer", None):
        v = judge.enforce(JudgeResult(reason="r", slot_1="adult", value_1="v", next_slot="adult"), req(partner))
        assert v.slot_1 == "adult" and v.next_slot == "adult"


def test_partner_reads_the_jev_choice_and_drops_one_under_the_floor(monkeypatch):
    class R:
        def __init__(self, choice, conf):
            self.status_code, self._a = 200, {"answers": {"partner": {"choice": choice, "confidence": conf}}}

        def json(self):
            return self._a

    sent = {}

    class Client:
        def __init__(self, *a, **k):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *a):
            return False

        async def post(self, url, json, headers):
            sent.update(json)
            return answer

    monkeypatch.setattr(settings, "typesafe_api_key", "test")
    monkeypatch.setattr(jev.httpx, "AsyncClient", Client)
    answer = R("mom", 0.9)
    assert asyncio.run(jev.partner("혼자 안 할래, 엄마랑"))[:2] == ("mom", 0.9)
    assert "혼자 안 할래, 엄마랑" in sent["state"] and set(sent["questions"]["partner"]["criteria"]) >= {"solo", "unknown"}
    answer = R("solo", 0.4)
    assert asyncio.run(jev.partner("음…"))[0] is None
    answer = R("robot", 0.99)                                        # off-list never reaches the app
    assert asyncio.run(jev.partner("로봇"))[0] is None


def test_partner_route_mock_blocked_and_failure(monkeypatch):
    c = TestClient(app)
    monkeypatch.setattr(settings, "mock", True)
    assert c.post("/partner", json={"utterance": "엄마랑!"}).json()["kind"] == "unknown"
    monkeypatch.setattr(settings, "mock", False)

    async def down(*a, **k):
        raise jev.JevError("HTTP 503")
    monkeypatch.setattr(jev, "partner", down)
    assert c.post("/partner", json={"utterance": "엄마랑!"}).status_code == 502   # the app reads its word list

    async def up(u, timeout_s=4.0):
        return "grandma", 0.95, 0.5
    monkeypatch.setattr(jev, "partner", up)
    assert c.post("/partner", json={"utterance": "우리 할미!"}).json() == {"kind": "grandma", "confidence": 0.95}
    assert c.post("/partner", json={"utterance": ""}).status_code == 422
