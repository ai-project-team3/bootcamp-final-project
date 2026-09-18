"""Rule-based word filter, layer 1 of 2. Lists: docs/금칙어_목록.md.

Runs before any LLM call so a blocked utterance never leaves our servers.
Name masking already happened on the phone — see docs/구현대본.md.

Two things this must get right, and one is easy to miss:
  - match on eojeol boundaries, so "시발점" does not trip on "시발"
  - never block the allow-list. Fear, monsters and fighting are what a
    five-year-old's story is made of; a filter that eats them eats the product.
"""
from pathlib import Path

WORDS = Path(__file__).parent / "words"


def _load(name: str) -> set[str]:
    p = WORDS / name
    if not p.exists():
        return set()
    return {
        line.strip()
        for line in p.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }


BLOCK = _load("block.txt")
ALLOW = _load("allow.txt")        # wins over BLOCK, always
REPORT_GUARD = _load("report_guard.txt")


def _eojeol(text: str) -> list[str]:
    return [w.strip(".,!?~…\"'") for w in text.split()]


def is_blocked(utterance: str) -> bool:
    words = _eojeol(utterance)
    if any(w in ALLOW for w in words):
        return False
    return any(w in BLOCK for w in words)


def guard_report(sentence: str) -> bool:
    """True when an LLM-written parent sentence must be thrown away."""
    return any(w in sentence for w in REPORT_GUARD)
