"""The allow-list keeps story words safe without letting a blocked word ride along (10-08)."""
from app.filters.blocklist import ALLOW, BLOCK, is_blocked

# any one blocked word — read from the list so the test file holds no swear words
BAD = sorted(BLOCK - ALLOW)[0]


def test_allow_words_alone_pass():
    assert not is_blocked("괴물이 나타나서 무서워")
    assert not is_blocked("친구랑 싸웠다 그래서 울었다")


def test_blocked_word_alone_is_caught():
    assert is_blocked(f"{BAD} 했어")


def test_allow_word_does_not_carry_a_blocked_word():
    # before 10-08 any allow word in the sentence let the whole sentence through
    assert is_blocked(f"괴물 {BAD}")
    assert is_blocked(f"무서워 {BAD} 무서워")


def test_eojeol_boundary_still_holds():
    # a blocked word inside a longer eojeol is not a hit ("시발점" is not "시발")
    assert not is_blocked(f"{BAD}점에서 출발")
