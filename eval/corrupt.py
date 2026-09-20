from __future__ import annotations

import argparse
import json
import random
import re
from datetime import date
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

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ANDROID_THEME_SOURCE = (
    PROJECT_ROOT
    / "android"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "example"
    / "finalproject_demo"
    / "demo"
    / "Model.kt"
)


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


def load_android_theme_labels(path: Path = ANDROID_THEME_SOURCE) -> list[str]:
    """Read the real location preset labels from the Android THEMES definition."""
    text = path.read_text(encoding="utf-8")
    match = re.search(r"val\s+THEMES\s*=\s*listOf\((.*?)\n\)\s*\n", text, re.DOTALL)
    if not match:
        raise ValueError(f"THEMES list not found in {path}")
    labels = re.findall(
        r"Theme\(\s*key\s*=\s*\"[^\"]+\"\s*,\s*label\s*=\s*\"([^\"]+)\"",
        match.group(1),
        re.DOTALL,
    )
    if not labels:
        raise ValueError(f"Theme labels not found in {path}")
    if len(labels) != len(set(labels)):
        raise ValueError(f"duplicate Theme labels in {path}")
    return labels


def render_app_results(summary: dict, source: Path, count: int) -> str:
    def pct(value: float) -> str:
        return f"{value * 100:.1f}%"

    try:
        source_label = source.resolve().relative_to(PROJECT_ROOT.resolve()).as_posix()
    except ValueError:
        source_label = source.as_posix()
    lines = [
        f"## {date.today().isoformat()} · 실제 앱 프리셋 자모 손상",
        "",
        f"> 정본: `{source_label}`의 `THEMES.label` {count}개 · seed `{SEED}`.",
        "",
        "| 강도 | 조건 | 매칭 전 | 자모 매칭 후 | 합격선 | 판정 |",
        "| --- | --- | ---: | ---: | ---: | --- |",
    ]
    thresholds = {"0": 1.0, "1": 0.95, "2": 0.85}
    conditions = {"0": "손상 0%", "1": "손상 10%", "2": "손상 25% + 음절/띄어쓰기 오류"}
    for level in ("0", "1", "2"):
        item = summary[level]
        threshold = thresholds[level]
        verdict = "통과" if item["after_accuracy"] >= threshold else "미달"
        lines.append(
            f"| {level} | {conditions[level]} | {pct(item['before_accuracy'])} | "
            f"{pct(item['after_accuracy'])} | ≥ {pct(threshold)} | {verdict} |"
        )
    lines += [
        "",
        "> 어절 전체 탈락은 별도 실험으로 합치지 않았다. 현재 정본은 한 단어 장소가 2/3이라 "
        "이름 자체에서 어절을 지우면 실제 STT 발화 탈락을 대표하지 못한다. 실제 발화/전사 쌍이 생기면 별도 표로 측정한다.",
    ]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="실제 Android 장소 프리셋 자모 손상 실험")
    parser.add_argument("--source", default=str(ANDROID_THEME_SOURCE))
    parser.add_argument("--out", default=str(ROOT / "raw" / "corrupt_app.jsonl"))
    parser.add_argument("--append-results", action="store_true")
    args = parser.parse_args()

    source = Path(args.source)
    presets = load_android_theme_labels(source)
    rows, summary = evaluate_presets(presets)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")

    markdown = render_app_results(summary, source, len(presets))
    print(markdown)
    print(f"raw: {out}")
    if args.append_results:
        from eval.run_team_eval import append_results

        results_path = ROOT / "results.md"
        appended = append_results(results_path, markdown)
        action = "누적" if appended else "동일 결과 존재 — 중복 생략"
        print(f"results ({action}): {results_path}")


if __name__ == "__main__":
    main()
