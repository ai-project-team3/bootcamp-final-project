"""eval/bench_diary_pages.py — the #301 diary checks count what they claim (no API call)."""
import sys
from pathlib import Path

EVAL = Path(__file__).resolve().parents[2] / "eval"
sys.path.insert(0, str(EVAL))
sys.argv = sys.argv[:1]

import bench_diary_pages as bench  # noqa: E402

CASE = {"want": {"child": ["롯데월드", "뽀로로"], "no_new": ["에버랜드"], "dup": [], "items": []},
        "req": {"slots": {"place": "롯데월드"}}}


def test_a_lost_name_and_an_invented_brand_count():
    m = bench.measure(CASE, ["나는 오늘 놀이공원에 갔어요.", "에버랜드보다 좋았어요."], None)
    assert m["missing"] == ["롯데월드", "뽀로로"] and m["invented_words"] == ["에버랜드"]


def test_the_diary_opens_in_the_first_person():
    assert bench.measure(CASE, ["나는 오늘 롯데월드에 갔어요.", "뽀로로를 봤어요."], None)["opens_na"] == 1
    assert bench.measure(CASE, ["엄마랑 롯데월드에 갔어요."], None)["opens_na"] == 0


def test_another_fixture_file_can_be_read():
    assert bench.load(EVAL / "fixtures_diary_given_names.jsonl")[0]["req"]["mode"] == "diary"
