"""Synthetic fixtures verify the measurement harness, not child validity."""
import json

import pytest

from eval.language_metrics import Morpheme, Utterance
from eval.language_metrics_bench import Record, read_aihub, read_jsonl, summarize


def tokens(text):
    return [Morpheme("곰", "NNG"), Morpheme("이", "JKS")] if text == "short" else [
        Morpheme("곰", "NNG"), Morpheme("이", "JKS"), Morpheme("자", "VV"),
        Morpheme("었", "EP"), Morpheme("어", "EF")]


def record(speaker, age, text, kind="open", by="child"):
    return Record(speaker, age, Utterance(text, by, kind))


def corpus():
    return [record(f"{age}-{i}", age, "short" if age == 3 else "long")
            for age in (3, 6) for i in range(10) for _ in range(50)]


def test_age_summary_uses_speakers_not_pooled_utterances_and_no_identifiers():
    rows = corpus() + [record("3-0", 3, "long") for _ in range(500)]
    result = summarize(rows, tokens)
    assert result["age_direction"] == "met"
    assert result["ages"]["3"]["speakers"] == 10
    assert result["ages"]["3"]["mean_mlu_m"] == 2
    assert result["ages"]["6"]["mean_mlu_m"] == 5
    assert "3-0" not in json.dumps(result)
    assert result == summarize(rows, tokens)


def test_small_samples_are_not_validation_success():
    result = summarize([record("one", 3, "short"), record("two", 6, "long")], tokens)
    assert result["age_direction"] == "insufficient_sample"
    assert result["eligible_speakers"] == 0


def test_mixed_question_analysis_cannot_pass_open_question_validity():
    rows = [record(r.speaker, r.age, r.answer.text, "unknown") for r in corpus()]
    assert summarize(rows, tokens)["age_direction"] == "insufficient_sample"
    result = summarize(rows, tokens, scope="all_child")
    assert result["age_direction"] == "exploratory_only"
    assert result["ages"]["3"]["mean_mlu_m"] == 2


def test_card_mascot_and_confirmation_are_excluded_from_speaker_windows():
    rows = corpus() + [record("3-0", 3, "long", "open", "mascot"),
                       record("3-0", 3, "long", "confirmation"),
                       record("3-0", 3, "long", "open", "card")]
    result = summarize(rows, tokens)
    assert result["exclusions"]["mascot"] == 1
    assert result["exclusions"]["card"] == 1
    assert result["exclusions"]["not_open"] == 1


def test_overlapping_age_intervals_do_not_pass():
    rows = [record(r.speaker, r.age, "short") for r in corpus()]
    assert summarize(rows, tokens)["age_direction"] == "not_met_or_tied"


def test_inconsistent_speaker_age_is_rejected():
    with pytest.raises(ValueError, match="Inconsistent"):
        summarize([record("one", 3, "short"), record("one", 6, "long")], tokens)


def test_jsonl_requires_provenance_question_type_and_real_integer_age(tmp_path):
    path = tmp_path / "answers.jsonl"
    base = {"speaker_id": "anonymous", "age": 3, "text": "응", "by": "child", "question_type": "open"}
    path.write_text(json.dumps(base), encoding="utf-8")
    assert list(read_jsonl(path))[0].age == 3
    for broken in ({**base, "age": True}, {**base, "by": "parent"}, {k: v for k, v in base.items() if k != "question_type"}):
        path.write_text(json.dumps(broken), encoding="utf-8")
        with pytest.raises(ValueError):
            list(read_jsonl(path))


def test_aihub_reader_needs_no_audio_and_never_infers_open_questions(tmp_path):
    (tmp_path / "sample.json").write_text(json.dumps({
        "발화정보": {"stt": "(SN:기침) 곰이 잤어.", "fileNm": "not-present.wav"},
        "녹음자정보": {"recorderId": "anonymous", "age": "3"},
    }), encoding="utf-8")
    records = list(read_aihub(tmp_path))
    assert len(records) == 1
    assert records[0].answer.text == "곰이 잤어."
    assert records[0].answer.question_type == "unknown"
    assert records[0].answer.by == "child"


def test_missing_recorder_id_is_not_coerced_into_one_shared_speaker(tmp_path):
    (tmp_path / "sample.json").write_text(json.dumps({
        "발화정보": {"stt": "응"}, "녹음자정보": {"recorderId": None, "age": 3},
    }), encoding="utf-8")
    with pytest.raises(ValueError):
        list(read_aihub(tmp_path))


def test_invalid_scope_is_rejected_even_without_any_records():
    with pytest.raises(ValueError):
        summarize([], tokens, scope="guess")
