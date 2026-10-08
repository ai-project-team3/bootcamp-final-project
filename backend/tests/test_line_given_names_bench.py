"""The mascot-line benchmark must tell a kept name from a dropped one, and a given brand from an invented one (#301)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "eval"))

from bench_line_given_names import invented, request, score, verdict  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.turn import Line  # noqa: E402

FIXTURES = Path(__file__).resolve().parents[2] / "eval" / "fixtures_line_given_names.jsonl"
CASE = {"mode": "story", "slots": {"newcomer": "광대", "name": "맥도날드"}, "question": "무슨 일이 생겼을까?",
        "utterance": "걔 모자가 날아갔어", "verdict": {"slot_1": "problem", "value_1": "모자가 날아갔어"},
        "names": ["맥도날드"]}


def test_a_generic_noun_in_place_of_the_name_is_not_kept():
    assert score(Line(ack="광대의 모자가 날아갔구나."), CASE)["missing"] == ["맥도날드"]
    assert score(Line(ack="맥도날드의 모자가 날아갔구나."), CASE)["kept"] is True


def test_a_name_only_in_the_options_does_not_count_as_spoken():
    assert score(Line(ack="모자가 날아갔구나.", question="어떻게 찾을까?", options=["맥도날드가 찾아"]), CASE)["kept"] is False


def test_a_given_brand_is_not_invented_but_a_new_one_is():
    assert invented(Line(ack="맥도날드의 모자가 날아갔구나."), CASE) == []
    assert invented(Line(ack="모자가 날아갔구나.", options=["뽀로로가 찾아 줘"]), CASE) == ["뽀로로"]


def test_every_fixture_builds_a_server_request():
    cases = [json.loads(l) for l in FIXTURES.read_text(encoding="utf-8").splitlines() if l.strip()]
    assert {c["group"] for c in cases} == {"given_name", "so_far", "so_far_control", "control"}
    for c in cases:
        text = turn.user(request(c), verdict(c))
        assert all(n in text for n in c["names"]), c["id"]
