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
    # One place, one cut: the server and eval/run_judge.py both go through prompt_block.system_block
    import importlib.util
    spec = importlib.util.spec_from_file_location("rj", judge_prompt.EVAL / "run_judge.py")
    assert "judge_system(path, mode)" in (judge_prompt.EVAL / "run_judge.py").read_text(encoding="utf-8")
    measured = judge_prompt.judge_system(judge_prompt.EVAL / "judge_prompt.md")
    assert judge_prompt.system().startswith(measured)
    # #121: a story request gets the story piece only — what run_judge.py measures on the story cases
    story = judge_prompt.judge_system(judge_prompt.EVAL / "judge_prompt.md", "story")
    assert judge_prompt.system("story").startswith(story)
    assert "story (동화)" in story and "diary (일기)" not in story and "coop (협업)" not in story
    assert "{모드 조각}" not in judge_prompt.system("coop") and "coop (협업)" in judge_prompt.system("coop")


def test_effort_none_is_sent_not_dropped(monkeypatch):
    """Dropping effort='none' lets the model reason by default and cut its own answer off."""
    import asyncio
    import httpx
    from app.llm import client as llm

    sent = {}

    class Fake:
        def __init__(self, *a, **k): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, headers, json):
            sent.update(json)
            body = {"output": [{"type": "message", "content": [{"type": "output_text", "text": "{}"}]}]}
            return httpx.Response(200, json=body)

    monkeypatch.setattr(settings, "openai_api_key", "test")
    monkeypatch.setattr(llm.httpx, "AsyncClient", Fake)
    asyncio.run(llm.complete("s", "u", {}, effort="none"))
    assert sent["reasoning"] == {"effort": "none"}


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


# ── /story ────────────────────────────────────────────────────────────
from app.routers import story as story_route          # noqa: E402
from app.schemas.story import Scene, StoryResult       # noqa: E402


def test_story_story_mode_gives_six_scenes(client):
    r = client.post("/story", json={"slots": {**EMPTY, "place": "공룡나라"}, "template": "C"})
    assert r.status_code == 200 and len(r.json()["scenes"]) == 6


def test_story_diary_reads_the_diary_prompt_not_the_story_one():
    diary, tale = story_route.system("diary"), story_route.system("story")
    assert "일기" in diary and "클리프행어" not in diary, "the diary must not be told to leave a crisis open"
    assert "[입력 슬롯]" not in tale, "the input block is sent as the user message, not twice"


def test_story_diary_input_carries_by_and_keep():
    from app.schemas.story import StoryRequest
    text = story_route.user(StoryRequest(mode="diary", slots={"solution": "잤어"},
                                         slot_by={"solution": "mascot"}, keep="또 가고 싶어"))
    assert '"solution":"mascot"' in text and "또 가고 싶어" in text


def _book(*caps):
    return StoryResult(scenes=[Scene(index=i + 1, caption=c, keywords="x") for i, c in enumerate(caps)])


def test_story_check_throws_away_the_wrong_scene_count():
    assert story_route.check(_book(*["가요."] * 5), "story")
    assert story_route.check(_book(*["가요."] * 6), "story") is None
    assert story_route.check(_book(*["가요."] * 4), "diary") is None


def test_story_check_throws_away_an_invented_name():
    assert story_route.check(_book("{주인공}과 {철수}가 놀았어요.", *["가요."] * 5), "story")
    assert story_route.check(_book("{주인공}과 {친구1}가 놀았어요.", *["가요."] * 5), "story") is None


def test_the_block_cut_drops_notes_and_the_input_block():
    """The cut the story uses (and the judge will, once measured better): no notes, no unfilled input."""
    s = judge_prompt.system_block(judge_prompt.EVAL / "judge_prompt.md")
    assert s.startswith("당신은"), s[:40]
    assert "[지금 상태]" not in s and "{utterance}" not in s
    assert "## 치환 변수" not in s


def test_a_short_diary_day_keeps_its_one_page():
    """#39: a day with only place + what happened is one honest page, not a 502."""
    assert story_route.check(_book("놀이터에 갔어요."), "diary") is None
    assert story_route.check(_book("놀이터에 갔어요."), "coop")
    assert story_route.check(_book(*["가요."] * 7), "diary")


def test_the_title_is_never_the_next_turn():
    """#156: the app asks the title after the book; a judge that picks it loops the story."""
    from app.routers.judge import enforce
    from app.schemas.judge import JudgeResult
    r = JudgeResult(reason="r", slot_1=None, value_1=None, slot_2=None, value_2=None, contradiction=False,
                    contradiction_with=None, s1_reason=False, s2_addition=False, emotion=None, unclear=False,
                    unclear_of=None, next_slot="title", next_reason="제목", no_longer_needed=None, story_ready=False)
    assert enforce(r, JudgeRequest(**judge_body())).next_slot is None
