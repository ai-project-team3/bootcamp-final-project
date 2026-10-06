"""Drop transcripts whisper invented from silence, before they become the child's words.

Runs right after STT, before name masking and the word filter. Returns a
verdict, not text: the caller treats a drop as "no answer" and the mascot asks
again, so a wrong drop costs one repeated question while a wrong keep writes
something the child never said into their record (rule 5).

One net: known hallucination lines (words/stt_hallucination.txt), matched
against the whole transcript only, so "감사합니다 하고 인사했어" still passes.
On the 09-22 phone recording it dropped all five silence clips and one real
clip, which whisper had already mis-heard ("몰라" -> "아멘"). No correctly
transcribed answer was dropped.

Tried and removed -- a speech-length floor re-measured with Silero on the
server. It caught nothing the phrase list missed, and on a quiet earphone-mic
recording Silero heard 0.00s of speech in clips whisper transcribed correctly,
so the floor dropped four real answers. The phone already cuts segments with
the same VAD; a second pass here only overrules it unless its settings match
the phone's, which nobody has measured. Chosen after seeing the data, and
said so in eval/results.md.

Not used: whisper's no_speech_prob. large-v3-turbo reported 0.00 on every
clip, silent or not.

Known gap: noise that whisper turns into an ordinary word ("고춧가루" from a
noisy classroom) passes. Listing story words would delete them when a child
really says them, so they stay out of the list.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

WORDS = Path(__file__).parent / "words"


def _norm(text: str) -> str:
    text = unicodedata.normalize("NFC", text or "").lower()
    return "".join(ch for ch in text if ch.isalnum())


def _load(name: str) -> tuple[frozenset[str], tuple[str, ...]]:
    exact, prefix = set(), []
    p = WORDS / name
    if p.exists():
        for raw in p.read_text(encoding="utf-8").splitlines():
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            if line.endswith("*"):
                prefix.append(_norm(line[:-1]))
            else:
                exact.add(_norm(line))
    return frozenset(exact), tuple(prefix)


EXACT, PREFIX = _load("stt_hallucination.txt")


@dataclass(frozen=True)
class Verdict:
    keep: bool
    reason: str | None = None     # "empty" | "no_hangul" | "hallucination" | None


_HANGUL = re.compile(r"[가-힣]")


def strip_tail(text: str) -> str:
    """Cut a known outro whisper glued onto a real answer — 10-05 phone: 「멋있어서 친구가 됐어」 came back
    with a 「구독과 좋아요 알림 설정」 tail, which the whole-transcript match let through. Only the listed
    prefixes and long lines are cut as tails; a short 「감사합니다」 after real words is kept, a child says it."""
    words = (text or "").split()
    for i in range(1, len(words)):
        rest = _norm(" ".join(words[i:]))
        if any(rest.startswith(p) for p in PREFIX) or (rest in EXACT and len(rest) >= 6):
            return " ".join(words[:i]).rstrip(" ,.")
    return text


def check_transcript(text: str) -> Verdict:
    """Keep or drop one transcribed segment."""
    t = _norm(text)
    if not t:
        return Verdict(False, "empty")
    # language="ko" is forced, yet on 4 of 4,000 AI-Hub child clips turbo emitted
    # Icelandic-looking Latin text or bare digits ("2, 3, 2, 3" for 토스트 해 주세요).
    # A Korean child's answer with no Hangul at all is not an answer we can use.
    if not _HANGUL.search(text):
        return Verdict(False, "no_hangul")
    if t in EXACT or any(t.startswith(p) for p in PREFIX):
        return Verdict(False, "hallucination")
    if _one_word_repeated(text):
        return Verdict(False, "hallucination")
    return Verdict(True)


def _one_word_repeated(text: str) -> bool:
    """One word three or more times and nothing else — 10-06 phone (#172): 「김치볶음밥 김치볶음밥 김치볶음밥」 and
    「이른바 이른바 이른바 이른바 이른바」 came back for a child's sentence and went into the book as the child's words.
    Whisper loops like this on faint audio. A word said twice (「아니 아니」) is a child's and is kept."""
    words = [w for w in (_norm(w) for w in (text or "").split()) if w]
    return len(words) >= 3 and len(set(words)) == 1
