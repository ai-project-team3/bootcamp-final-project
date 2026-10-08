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
    assert set(out["line"]) == {"ack", "expand", "question", "options"}
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


def test_a_line_keeping_the_childs_brand_or_character_name_is_not_dropped():
    # #301: the line check is the only place a mascot line is thrown away for its words.
    # A name the child gave stays in what Otto says — it must not send the phone to its script.
    line = Line(ack="광대 이름은 맥도날드구나!", expand="뽀로로랑 롯데월드에 갔어.",
                question="손흥민에게 무슨 일이 생겼을까?", options=["디즈니 성", "포켓몬", "엘사 인형"])
    assert turn_route.check(line) is None
    assert line.options == ["디즈니 성", "포켓몬", "엘사 인형"]


def test_brand_examples_are_not_on_the_line_blocklist():
    # If brands ever join block.txt, child-given names would be discarded with the line.
    # That change needs the given-name exemption #292 built for books (eval/forbidden.py) first.
    assert not {"뽀로로", "디즈니", "포켓몬", "맥도날드", "엘사", "손흥민"} & BLOCK


def test_a_safety_word_is_never_exempt_even_when_the_child_gave_it_as_a_name():
    word = next(iter(BLOCK))
    req = TurnRequest(mode="story", slots={"name": word}, question="이름은 뭐야?", utterance=word)
    assert turn_route.check(Line(ack=f"이름은 {word} 구나", question="무슨 일이 생겼을까?"))
    assert word in turn_route.user(req, None)          # it is in the input, still rejected in the output


def test_the_line_prompt_keeps_given_names_and_forbids_inventing_brands():
    s = turn_route.system()
    assert "브랜드·실존 인물·실존 작품 이름을 쓰지 않습니다" not in s
    assert "먼저 지어 넣지 않습니다" in s
    assert "그대로 부릅니다" in s


def test_the_line_prompt_is_the_fenced_block_only():
    s = turn_route.system()
    assert s.startswith("당신은"), s[:40]
    assert "근거:" not in s and "```" not in s
    assert set(turn_route.schema()["required"]) == {"ack", "expand", "question", "options"}


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


def test_a_slow_llm_call_is_cut_at_its_own_deadline(monkeypatch):
    """10-01: /story with effort high passed httpx's 30 s per-phase timeout and the phone got 502.
    Now each call has a whole-call deadline; past it, LLMError — never a hang past the phone."""
    import asyncio as aio
    from app.llm import client

    class Slow:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *a, **k):
            await aio.sleep(5)

    monkeypatch.setattr(client.settings, "openai_api_key", "x")
    monkeypatch.setattr(client.httpx, "AsyncClient", Slow)
    with pytest.raises(client.LLMError, match="over"):
        aio.run(client.complete("s", "u", {}, effort="none", timeout_s=0.2))


def test_a_slow_judge_costs_the_line_not_the_verdict(monkeypatch):
    """/turn has one deadline (25 s, phone waits 30): when the judge eats it, the verdict goes alone."""
    import asyncio as aio
    from app.routers import judge as judge_route

    monkeypatch.setattr(turn_route.settings, "mock", False)
    monkeypatch.setattr(turn_route.settings, "turn_deadline_s", 0.3)
    v = JudgeResult(reason="r", value_1="바닷속", next_slot="problem")

    async def slow_judge(_):
        await aio.sleep(0.4)
        return v
    called = []

    async def line_llm(*_, **__):
        called.append(1)
        return {"ack": "a", "expand": None, "question": "q"}
    monkeypatch.setattr(judge_route, "run", slow_judge)
    monkeypatch.setattr(turn_route, "complete", line_llm)
    req = TurnRequest(mode="story", slots={}, question="q", utterance="바닷속")
    out = aio.run(turn_route.turn(req))
    assert out.judge == v and out.line is None and not called


# #53 C: a coop question follows the reason the parent picked, the same as the book (#52)
def test_coop_sends_the_reason_and_other_modes_do_not():
    def block(mode, reason=None):
        return turn_route.user(TurnRequest(mode=mode, slots={}, question="거기서 무슨 일을 할까?", utterance="불 꺼",
                                           template="직업 · 소방관", reason=reason), None)
    assert "reason:soon" in block("coop", "soon")
    assert "reason:done" in block("coop")
    assert "\nreason:" not in block("story", "soon") and "\nreason:" not in block("diary")   # not next_reason


def test_options_keep_safe_ones_and_drop_the_rest():
    """#79: an unsafe option goes on its own; the line and the others stay."""
    word = next(iter(BLOCK))
    line = Line(ack="그랬구나!", question="거기서 누굴 만났어?",
                options=["아기 공룡", word, "{지민}", "아기 공룡", "커다란 티라노", "별"])
    assert turn_route.check(line) is None
    assert line.options == ["아기 공룡", "커다란 티라노", "별"]


def test_options_go_with_the_question_and_never_in_diary():
    v = JudgeResult(reason="ok", story_ready=False, next_slot="place")
    story = TurnRequest(**body())
    diary = TurnRequest(**body(mode="diary"))
    asked = lambda: Line(ack="a", question="어디로 갈까?", options=["바닷속", "우주", "숲"])
    assert turn_route.shape(asked(), story, v).options == ["바닷속", "우주", "숲"]
    assert turn_route.shape(asked(), diary, v).options is None
    ready = JudgeResult(reason="ok", story_ready=True)
    assert turn_route.shape(asked(), story, ready).options is None
