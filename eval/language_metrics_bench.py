"""Offline #320 transcript evaluation: anonymous aggregates, no audio or APIs.

AI-Hub labels do not establish question type. Their all-child view is exploratory
and cannot enable the open-question policy, even if age intervals separate.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from functools import lru_cache
import importlib.metadata
import json
from pathlib import Path
import random
import re
import statistics
from typing import Iterable

from eval.language_metrics import QUESTION_TYPES, Tokenizer, Utterance, kiwi_tokenizer, measure

WINDOW = 50
MIN_SPEAKERS = 10
BOOTSTRAPS = 2000
SEED = 320


@dataclass(frozen=True)
class Record:
    speaker: str
    age: int
    answer: Utterance


def validate_record(obj: dict) -> Record:
    """Reject missing provenance rather than assuming the child said it."""
    if not isinstance(obj, dict):
        raise ValueError("Each record must be an object")
    required = {"speaker_id", "age", "text", "by", "question_type"}
    if not required <= obj.keys():
        raise ValueError("Record requires speaker_id, age, text, by, question_type")
    if not isinstance(obj["speaker_id"], str) or not obj["speaker_id"].strip():
        raise ValueError("speaker_id must be a nonempty string")
    if type(obj["age"]) is not int or obj["age"] not in {3, 4, 5, 6}:
        raise ValueError("age must be an integer from 3 through 6")
    if not isinstance(obj["text"], str):
        raise ValueError("text must be a string")
    if obj["by"] not in {"child", "card", "mascot"} or obj["question_type"] not in QUESTION_TYPES:
        raise ValueError("Unknown provenance or question type")
    return Record(obj["speaker_id"], obj["age"], Utterance(obj["text"], obj["by"], obj["question_type"]))


def read_jsonl(path: Path) -> Iterable[Record]:
    with path.open(encoding="utf-8-sig") as stream:
        for i, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                yield validate_record(json.loads(line))
            except (ValueError, TypeError) as exc:
                # Never put child text, IDs, or the entire malformed line in an error.
                raise ValueError(f"Invalid JSONL record at line {i}: {type(exc).__name__}") from None


def read_aihub(root: Path) -> Iterable[Record]:
    """Use the existing AI-Hub recorder/utterance schema without finding a wav."""
    if not root.is_dir():
        raise ValueError("AI-Hub label directory does not exist")
    found = False
    for path in sorted(root.rglob("*.json")):
        found = True
        try:
            obj = json.loads(path.read_text(encoding="utf-8-sig"))
            speaker = obj["녹음자정보"]["recorderId"]
            if type(speaker) not in {str, int}:
                raise ValueError("Recorder ID must be a string or integer")
            age = obj["녹음자정보"]["age"]
            if isinstance(age, str) and age.isascii() and age.isdigit():
                age = int(age)
            text = obj["발화정보"]["stt"]
            if not isinstance(text, str):
                raise ValueError("Transcript must be a string")
            # Same non-speech annotation convention as stt_child_bench.py.
            text = re.sub(r"\([A-Z]+:[^)]*\)", " ", text)
            text = re.sub(r"\s+", " ", text).strip()
            yield validate_record({"speaker_id": str(speaker), "age": age, "text": text,
                                   "by": "child", "question_type": "unknown"})
        except (KeyError, ValueError, TypeError):
            raise ValueError("Invalid AI-Hub label schema; check it locally without uploading labels") from None
    if not found:
        raise ValueError("No AI-Hub JSON labels found")


def confidence_interval(values: list[float]) -> list[float]:
    """Resample speakers, not utterances; each speaker has equal weight."""
    rng = random.Random(SEED)
    means = sorted(statistics.mean(rng.choices(values, k=len(values))) for _ in range(BOOTSTRAPS))
    return [means[int(BOOTSTRAPS * 0.025)], means[int(BOOTSTRAPS * 0.975) - 1]]


def summarize(records: Iterable[Record], tokenize: Tokenizer, *, scope: str = "open") -> dict:
    if scope not in {"open", "all_child"}:
        raise ValueError("scope must be open or all_child")
    # Keep cached tokens local to this invocation, never as process-global child data.
    cached = lru_cache(maxsize=4096)(tokenize)
    windows: dict[str, list[Utterance]] = defaultdict(list)
    ages: dict[str, int] = {}
    exclusions: Counter[str] = Counter()
    question_types: Counter[str] = Counter()
    beyond_window = 0
    for row in records:
        if row.age not in {3, 4, 5, 6} or type(row.age) is not int:
            raise ValueError("age must be an integer from 3 through 6")
        if row.speaker in ages and ages[row.speaker] != row.age:
            raise ValueError("Inconsistent age for one speaker")
        ages[row.speaker] = row.age
        one = measure([row.answer], cached, scope=scope)
        exclusions.update(one.excluded)
        if not one.utterances:
            continue
        question_types[row.answer.question_type] += 1
        if len(windows[row.speaker]) >= WINDOW:
            beyond_window += 1
            continue
        windows[row.speaker].append(row.answer)
    groups: dict[int, list] = defaultdict(list)
    undersampled = 0
    for speaker in ages:
        if len(windows[speaker]) < WINDOW:
            undersampled += 1
            continue
        groups[ages[speaker]].append(measure(windows[speaker], cached, scope=scope))
    by_age = {}
    for age in range(3, 7):
        metrics = groups[age]
        if not metrics:
            by_age[str(age)] = {"speakers": 0, "mean_mlu_m": None, "ci95": None}
            continue
        mlu = [m.mlu_m for m in metrics]
        by_age[str(age)] = {
            "speakers": len(metrics), "answers_per_speaker": WINDOW,
            "mean_mlu_m": statistics.mean(mlu), "ci95": confidence_interval(mlu),
            "mean_ndw": statistics.mean(m.ndw for m in metrics),
            "mean_content_tokens": statistics.mean(m.content_tokens for m in metrics),
            "causal_answer_rate": sum(m.causal_utterances for m in metrics) / (len(metrics) * WINDOW),
            "foreign_answers": sum(m.foreign_utterances for m in metrics),
        }
    enough = all(by_age[str(age)]["speakers"] >= MIN_SPEAKERS for age in (3, 6))
    direction = "insufficient_sample"
    if enough:
        direction = "met" if by_age["3"]["ci95"][1] < by_age["6"]["ci95"][0] else "not_met_or_tied"
    if scope == "all_child":
        direction = "exploratory_only"
    cached.cache_clear()
    return {
        "scope": scope, "unit": "one_answer", "window": WINDOW,
        "bootstrap_unit": "speaker", "bootstrap_samples": BOOTSTRAPS, "seed": SEED,
        "eligible_speakers": sum(len(group) for group in groups.values()),
        "undersampled_speakers": undersampled, "beyond_window_answers": beyond_window,
        "question_types": dict(question_types), "exclusions": dict(exclusions),
        "ages": by_age, "sample_criterion_met": enough, "age_direction": direction,
        "clinical_validity": "not_established", "policy_activation": "not_performed",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Local transcript JSONL or AI-Hub label directory")
    parser.add_argument("--format", choices=("jsonl", "aihub"), default="jsonl")
    parser.add_argument("--scope", choices=("open", "all_child"), default="open")
    parser.add_argument("--output", type=Path, default=Path("eval/raw/language_metrics_summary.json"))
    args = parser.parse_args()
    if args.format == "aihub" and args.scope != "all_child":
        parser.error("AI-Hub labels have unknown question types; use --scope all_child for exploration only")
    try:
        records = read_jsonl(args.input) if args.format == "jsonl" else read_aihub(args.input)
        result = summarize(records, kiwi_tokenizer(), scope=args.scope)
        result["analyzer"] = {"name": "Kiwi", "version": importlib.metadata.version("kiwipiepy"),
                              "model_version": importlib.metadata.version("kiwipiepy_model"),
                              "model_type": "cong", "normalize_coda": False, "split_complex": False}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"eligible_speakers": result["eligible_speakers"],
                          "age_direction": result["age_direction"], "output": str(args.output)}, ensure_ascii=False))
    except (ValueError, RuntimeError, OSError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
