"""eval/bench_coop_book.py — the #301 · #304 4 checks count what they claim (no API call)."""
import sys
from pathlib import Path

EVAL = Path(__file__).resolve().parents[2] / "eval"
sys.path.insert(0, str(EVAL))
sys.argv = sys.argv[:1]

import bench_coop_book as bench  # noqa: E402

CASE = {"names": ["롯데월드", "뽀로로"], "indirect": ["괜찮다고 했어"], "direct": ["조심해"]}


def test_reported_speech_in_quotes_and_a_lost_name_count():
    c = bench.given_checks(CASE, ["{주인공}은 롯데월드에 갔어요.", "아빠는 “괜찮다고 했어.”라고 말했어요."])
    assert c["name_lost_what"] == ["뽀로로"] and c["quote_indirect"] == 1
    assert c["direct_lost"] == 1 and c["no_hero"] == 0


def test_a_good_book_counts_nothing():
    c = bench.given_checks(CASE, ["{주인공}은 뽀로로 인형과 롯데월드에 갔어요.", "아빠가 “조심해!” 하고 말했어요.", "아빠는 괜찮다고 했어요."])
    assert (c["name_lost"], c["quote_indirect"], c["direct_lost"], c["no_hero"]) == (0, 0, 0, 0)


def test_a_book_without_the_protagonist_counts():
    assert bench.given_checks({}, ["아빠와 축구장에 갈 거예요."])["no_hero"] == 1


def test_fixtures_without_the_new_keys_still_check_cleanly():
    c = bench.given_checks({}, ["{주인공}은 동물원에 갔어요."])
    assert (c["name_lost"], c["quote_indirect"], c["direct_lost"]) == (0, 0, 0)
