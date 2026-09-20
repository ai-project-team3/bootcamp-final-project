from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from eval.corrupt import jamo_string, levenshtein


_TOKEN_RE = re.compile(r"[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]+")
_SUFFIXES = {
    "은", "는", "이", "가", "을", "를", "에", "에서", "에게", "한테",
    "으로", "로", "와", "과", "도", "만", "의", "부터", "까지", "처럼",
    "보다", "야", "아", "요", "서", "고", "면", "지", "니", "까", "라고",
    "하고", "했다", "했어", "해요", "하자", "다가", "지만",
}


@dataclass(frozen=True)
class ForbiddenPolicy:
    allowed: frozenset[str]
    forbidden: frozenset[str]
    manual_categories: tuple[str, ...] = ()


@dataclass(frozen=True)
class ForbiddenHit:
    term: str
    matched: str
    start: int
    end: int
    fuzzy: bool


def _between(text: str, start: str, end: str) -> str:
    start_at = text.index(start) + len(start)
    end_at = text.index(end, start_at)
    return text[start_at:end_at]


def _clean_term(raw: str) -> str:
    term = raw.strip().strip("*`")
    term = re.sub(r"\([^)]*\)", "", term).strip()
    return term


def _split_terms(raw: str) -> list[str]:
    return [term for piece in raw.split("·") if (term := _clean_term(piece))]


def _table_terms(section: str, value_column: int) -> set[str]:
    terms: set[str] = set()
    for line in section.splitlines():
        if not line.lstrip().startswith("|") or "---" in line:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) <= value_column:
            continue
        value = cells[value_column]
        if value.startswith(("낱말", "아이 말", "갈래")):
            continue
        terms.update(_split_terms(value))
    return terms


def _load_markdown_policy(path: Path) -> ForbiddenPolicy:
    text = path.read_text(encoding="utf-8")
    allowed = _table_terms(_between(text, "## 0.", "\n---"), 1)
    blocked = _table_terms(_between(text, "### 1-1.", "### 1-2."), 1)
    softened_originals = _table_terms(_between(text, "### 1-2.", "### 1-3."), 0)
    story_section = _between(text, "## 2.", "\n---")

    extra: set[str] = set()
    manual_categories: list[str] = []
    for line in story_section.splitlines():
        stripped = line.strip()
        if stripped.startswith("- **§1-1"):
            summary = re.search(r"\(([^)]*)\)", stripped)
            if summary:
                extra.update(_split_terms(summary.group(1)))
        elif stripped.startswith("- 추가:"):
            value = stripped.removeprefix("- 추가:").strip()
            if value.startswith("특정 종교"):
                manual_categories.extend(_split_terms(value))
            elif value.startswith("상표명"):
                examples = re.search(r"\(([^)]*)\)", value)
                if examples:
                    for term in _split_terms(examples.group(1)):
                        extra.add(re.sub(r"\s+등$", "", term).strip())
            else:
                extra.update(_split_terms(value))

    forbidden = (blocked | softened_originals | extra) - allowed
    return ForbiddenPolicy(
        allowed=frozenset(allowed),
        forbidden=frozenset(term for term in forbidden if term),
        manual_categories=tuple(manual_categories),
    )


def load_story_policy(path: Path) -> ForbiddenPolicy:
    if path.suffix.lower() == ".md":
        return _load_markdown_policy(path)
    forbidden = {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    return ForbiddenPolicy(allowed=frozenset(), forbidden=frozenset(forbidden))


def _matches_term(candidate_tokens: list[str], term_tokens: list[str]) -> bool:
    if len(candidate_tokens) != len(term_tokens):
        return False
    if candidate_tokens[:-1] != term_tokens[:-1]:
        return False
    candidate_last = candidate_tokens[-1]
    term_last = term_tokens[-1]
    if candidate_last == term_last:
        return True
    if not candidate_last.startswith(term_last):
        return False
    return candidate_last[len(term_last):] in _SUFFIXES


def _term_hits(text: str, term: str, *, fuzzy: bool) -> list[ForbiddenHit]:
    tokens = list(_TOKEN_RE.finditer(text))
    term_tokens = [match.group(0) for match in _TOKEN_RE.finditer(term)]
    if not term_tokens or len(tokens) < len(term_tokens):
        return []

    hits = []
    width = len(term_tokens)
    term_jamo = jamo_string(" ".join(term_tokens))
    for index in range(len(tokens) - width + 1):
        window = tokens[index:index + width]
        candidate_tokens = [match.group(0) for match in window]
        exact = _matches_term(candidate_tokens, term_tokens)
        fuzzy_match = False
        if not exact and fuzzy and len(term_jamo) >= 4:
            candidate_jamo = jamo_string(" ".join(candidate_tokens))
            fuzzy_match = levenshtein(candidate_jamo, term_jamo) <= 1
        if exact or fuzzy_match:
            start, end = window[0].start(), window[-1].end()
            hits.append(ForbiddenHit(term, text[start:end], start, end, fuzzy_match))
    return hits


def find_forbidden_hits(text: str, policy: ForbiddenPolicy) -> list[ForbiddenHit]:
    allowed_spans = {
        (hit.start, hit.end)
        for term in policy.allowed
        for hit in _term_hits(text, term, fuzzy=False)
    }
    hits = []
    seen = set()
    for term in sorted(policy.forbidden, key=lambda item: (-len(item), item)):
        for hit in _term_hits(text, term, fuzzy=True):
            if any(hit.start < end and start < hit.end for start, end in allowed_spans):
                continue
            key = (hit.start, hit.end, hit.term)
            if key not in seen:
                seen.add(key)
                hits.append(hit)
    return sorted(hits, key=lambda hit: (hit.start, hit.end, hit.term))
