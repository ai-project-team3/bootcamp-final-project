from __future__ import annotations

import json
import random
import re
from pathlib import Path

SEED = 20260918
BASE = 0xAC00
CHO_UNIT = 588
JUNG_UNIT = 28

CHO = list("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
JUNG = list("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ")
JONG = ["", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]

CHO_SWAP = {"ㄹ":"ㄴ", "ㄴ":"ㄹ", "ㄱ":"ㅋ", "ㅋ":"ㄱ", "ㅂ":"ㅍ", "ㅍ":"ㅂ", "ㄷ":"ㅌ", "ㅌ":"ㄷ", "ㅅ":"ㅆ", "ㅆ":"ㅅ", "ㅈ":"ㅊ", "ㅊ":"ㅈ"}
JUNG_SWAP = {"ㅐ":"ㅔ", "ㅔ":"ㅐ", "ㅗ":"ㅜ", "ㅜ":"ㅗ", "ㅓ":"ㅗ", "ㅑ":"ㅕ", "ㅕ":"ㅑ"}
JONG_SWAP = {"ㄴ":"ㅇ", "ㅇ":"ㄴ", "ㄱ":"ㅋ", "ㅋ":"ㄱ"}


def decompose_char(ch: str) -> tuple[int, int, int] | None:
    code = ord(ch) - BASE
    if 0 <= code <= 11171:
        return code // CHO_UNIT, (code % CHO_UNIT) // JUNG_UNIT, code % JUNG_UNIT
    return None


def compose(cho_i: int, jung_i: int, jong_i: int) -> str:
    return chr(BASE + cho_i * CHO_UNIT + jung_i * JUNG_UNIT + jong_i)


def corrupt_text(text: str, rate: float, *, rng: random.Random, severe: bool = False) -> str:
    chars = list(text)
    hangul_idxs = [i for i, ch in enumerate(chars) if decompose_char(ch)]
    if not hangul_idxs or rate <= 0:
        return text
    n = max(1, round(len(hangul_idxs) * rate))
    targets = rng.sample(hangul_idxs, min(n, len(hangul_idxs)))

    for idx in targets:
        parts = decompose_char(chars[idx])
        if not parts:
            continue
        ci, vi, fi = parts
        choices = []
        if CHO[ci] in CHO_SWAP:
            choices.append("cho")
        if JUNG[vi] in JUNG_SWAP:
            choices.append("jung")
        if fi != 0:
            choices.append("drop_jong")
        if JONG[fi] in JONG_SWAP:
            choices.append("jong")
        if severe:
            choices.append("drop_syllable")
        action = rng.choice(choices or ["drop_syllable"])
        if action == "cho":
            ci = CHO.index(CHO_SWAP[CHO[ci]])
            chars[idx] = compose(ci, vi, fi)
        elif action == "jung":
            vi = JUNG.index(JUNG_SWAP[JUNG[vi]])
            chars[idx] = compose(ci, vi, fi)
        elif action == "drop_jong":
            chars[idx] = compose(ci, vi, 0)
        elif action == "jong":
            fi = JONG.index(JONG_SWAP[JONG[fi]])
            chars[idx] = compose(ci, vi, fi)
        else:
            chars[idx] = ""

    out = "".join(chars)
    if severe and len(out.replace(" ", "")) >= 3 and rng.random() < 0.65:
        compact = out.replace(" ", "")
        cut = rng.randint(1, len(compact) - 1)
        out = compact[:cut] + " " + compact[cut:]
    return out


def jamo_string(text: str) -> str:
    result = []
    for ch in re.sub(r"\s+", "", text):
        parts = decompose_char(ch)
        if parts:
            ci, vi, fi = parts
            result.extend([CHO[ci], JUNG[vi]])
            if fi:
                result.append(JONG[fi])
        else:
            result.append(ch)
    return "".join(result)


def levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(cur[-1] + 1, prev[j] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def raw_exact_match(text: str, presets: list[str]) -> str | None:
    normalized = re.sub(r"\s+", "", text)
    for p in presets:
        if normalized == re.sub(r"\s+", "", p):
            return p
    return None


def jamo_nearest_match(text: str, presets: list[str]) -> str:
    target = jamo_string(text)
    scored = [(levenshtein(target, jamo_string(p)), p) for p in presets]
    scored.sort(key=lambda x: (x[0], x[1]))
    return scored[0][1]


def evaluate_presets(presets: list[str]) -> tuple[list[dict], dict]:
    rng = random.Random(SEED)
    rows = []
    summary = {}
    for label, rate, severe in [("0", 0.0, False), ("1", 0.10, False), ("2", 0.25, True)]:
        before_ok = after_ok = 0
        level_rows = []
        for original in presets:
            corrupted = corrupt_text(original, rate, rng=rng, severe=severe)
            before = raw_exact_match(corrupted, presets)
            after = jamo_nearest_match(corrupted, presets)
            before_hit = before == original
            after_hit = after == original
            before_ok += before_hit
            after_ok += after_hit
            row = {
                "level": label,
                "rate": rate,
                "original": original,
                "corrupted": corrupted,
                "before_match": before,
                "after_match": after,
                "before_ok": before_hit,
                "after_ok": after_hit,
            }
            rows.append(row)
            level_rows.append(row)
        summary[label] = {
            "before_accuracy": before_ok / len(presets),
            "after_accuracy": after_ok / len(presets),
            "count": len(presets),
        }
    return rows, summary


def load_presets(path: Path) -> list[str]:
    return json.loads(path.read_text(encoding="utf-8"))
