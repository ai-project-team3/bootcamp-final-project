"""Local, morphology-based measurements; not an age estimate or clinical score.

The pure calculator accepts a tokenizer. Kiwi is loaded only by the local adapter,
never by the backend or Android app. No network requests or credential access.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from typing import Callable, Iterable, Sequence


CONTENT_TAGS = frozenset({"NNG", "NNP", "NP", "NR", "VV", "VA", "MAG", "MAJ"})
# Korean morphology, including case particles and endings. SL/SH/SN are counted
# as analyzer tokens, but their presence is reported for manual inspection.
MORPHEME_TAGS = CONTENT_TAGS | frozenset({
    "NNB", "VX", "VCP", "VCN", "MM", "IC", "XPN", "XSN", "XSV", "XSA", "XR",
    "JKS", "JKC", "JKG", "JKO", "JKB", "JKV", "JKQ", "JX", "JC",
    "EP", "EF", "EC", "ETN", "ETM", "SL", "SH", "SN",
})
CAUSE_ENDINGS = frozenset({"어서", "아서", "니까", "으니까", "므로", "으므로", "기에", "길래"})
CAUSE_ADVERBS = frozenset({"왜냐하면", "그래서", "그러므로", "따라서"})
QUESTION_TYPES = frozenset({"open", "choice", "confirmation", "unknown"})


@dataclass(frozen=True)
class Morpheme:
    form: str
    tag: str


@dataclass(frozen=True)
class Utterance:
    text: str
    by: str
    question_type: str


@dataclass(frozen=True)
class Metrics:
    utterances: int
    morphemes: int
    mlu_m: float | None
    ndw: int
    content_tokens: int
    causal_utterances: int
    causal_markers: int
    foreign_utterances: int
    excluded: dict[str, int]


Tokenizer = Callable[[str], Sequence[Morpheme]]


def measure(utterances: Iterable[Utterance], tokenize: Tokenizer, *, scope: str = "open") -> Metrics:
    """Measure one microphone answer per input, excluding proxies before analysis.

    `all_child` is an explicit exploratory corpus view, not an open-question
    calibration. Causal markers are grammatical candidates, not S1 verdicts.
    """
    if scope not in {"open", "all_child"}:
        raise ValueError("scope must be open or all_child")
    excluded: Counter[str] = Counter()
    n = total = content_total = causes = causal_n = foreign = 0
    types: set[tuple[str, str]] = set()
    for utterance in utterances:
        if utterance.by not in {"child", "card", "mascot"}:
            raise ValueError("Unknown utterance provenance")
        if utterance.question_type not in QUESTION_TYPES:
            raise ValueError("Unknown question type")
        if not isinstance(utterance.text, str):
            raise ValueError("Utterance text must be a string")
        if utterance.by != "child":
            excluded[utterance.by] += 1
            continue
        if scope == "open" and utterance.question_type != "open":
            excluded["not_open"] += 1
            continue
        if not utterance.text.strip():
            excluded["empty"] += 1
            continue
        tokens = [(m.form, m.tag.split("-")[0]) for m in tokenize(utterance.text)
                  if m.form.strip()]
        counted = [(form, tag) for form, tag in tokens if tag in MORPHEME_TAGS]
        if not counted:
            excluded["empty"] += 1
            continue
        n += 1
        total += len(counted)
        content = [(form, tag) for form, tag in counted if tag in CONTENT_TAGS]
        content_total += len(content)
        types.update(content)
        markers = sum((tag == "EC" and form in CAUSE_ENDINGS) or
                      (tag in {"MAG", "MAJ"} and form in CAUSE_ADVERBS)
                      for form, tag in counted)
        causes += markers
        causal_n += int(markers > 0)
        foreign += int(any(tag in {"SL", "SH", "SN"} for _, tag in counted))
    return Metrics(n, total, total / n if n else None, len(types), content_total,
                   causal_n, causes, foreign, dict(excluded))


def kiwi_tokenizer() -> Tokenizer:
    """Create a local analyzer with fixed settings and without typo rewriting."""
    try:
        from kiwipiepy import Kiwi
    except ImportError as exc:
        raise RuntimeError("Install eval/requirements-language-metrics.txt for local analysis") from exc
    kiwi = Kiwi(num_workers=1, model_type="cong")

    def tokenize(text: str) -> list[Morpheme]:
        return [Morpheme(token.form, token.tag) for token in kiwi.tokenize(
            text, normalize_coda=False, split_complex=False, typos=None)]

    return tokenize
