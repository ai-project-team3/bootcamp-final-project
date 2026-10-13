"""Offline checks use invented utterances, never device transcripts."""
import importlib.util
import json
from pathlib import Path

import pytest


def load_collector():
    path = Path(__file__).resolve().parents[2] / "eval/collect_story_rewrite_trace.py"
    if not path.exists():
        pytest.fail("The offline story trace collector has not been implemented")
    spec = importlib.util.spec_from_file_location("story_trace_collector", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def chunks(event, sequence, session="demo-1", attempt="attempt-1", data=None):
    payload = json.dumps({"event": event, "attempt_id": attempt, "data": data or {}}, ensure_ascii=False)
    pieces = [payload[i:i + 25] for i in range(0, len(payload), 25)]
    return ["I/OttoTrace: story_eval | " + json.dumps({
        "schema_version": 1, "session_id": session, "sequence": sequence,
        "part": i + 1, "parts": len(pieces), "payload": piece, "truncated": False,
    }, ensure_ascii=False) for i, piece in enumerate(pieces)]


def test_reassembles_interleaved_unicode_without_normalizing():
    collector = load_collector()
    text = "  루루 🐥가 말했어.\n찰랑찰랑 e\u0301  "
    first = chunks("utterance", 1, data={"raw_text": text, "by": "child"})
    other = chunks("utterance", 1, session="demo-2", data={"raw_text": "카드", "by": "card"})
    result = collector.collect_lines(["unrelated log"] + first[::-1] + other + first[:1])
    assert len(result["events"]) == 2
    assert result["events"][0]["data"]["raw_text"] == text
    assert result["events"][1]["data"]["by"] == "card"
    assert not result["issues"]


def test_missing_parts_and_conflicting_duplicate_are_quarantined():
    collector = load_collector()
    incomplete = chunks("generation_response", 1)[:-1]
    conflict = chunks("generation_request", 2)
    changed = json.loads(conflict[0].split(" | ", 1)[1])
    changed["payload"] = "different"
    result = collector.collect_lines(incomplete + conflict + ["story_eval | " + json.dumps(changed)])
    assert result["events"] == []
    assert {issue["reason"] for issue in result["issues"]} == {"missing_parts", "conflicting_part"}


@pytest.mark.parametrize("change,reason", [
    ({"schema_version": 2}, "unsupported_schema"),
    ({"parts": 300}, "invalid_envelope"),
    ({"part": True}, "invalid_envelope"),
    ({"truncated": True}, "payload_limit"),
])
def test_incomplete_transport_cannot_become_complete_evidence(change, reason):
    collector = load_collector()
    line = chunks("generation_response", 1)[0]
    envelope = json.loads(line.split(" | ", 1)[1])
    envelope.update(change)
    result = collector.collect_lines(["story_eval | " + json.dumps(envelope)])
    assert not result["events"]
    assert result["issues"][0]["reason"] == reason


def test_secret_fields_are_rejected_without_echoing_values():
    collector = load_collector()
    result = collector.collect_lines(chunks("generation_request", 1, data={
        "slots": {"sound": "탁탁"}, "headers": {"Authorization": "invented-secret-value"},
    }))
    assert not result["events"]
    assert "invented-secret-value" not in json.dumps(result)
    assert result["issues"][0]["reason"] == "forbidden_field"
    safe = collector.collect_lines(chunks("turn_after", 1, data={"slots": {"sound": "탁탁"}}))
    assert len(safe["events"]) == 1


def test_summary_separates_adoption_fallback_cancellation_and_actual_save():
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data={"started_mid_session": False})
    stages = [
        ("a", "generation_started", {}), ("a", "generation_response", {"status": "ok"}),
        ("a", "generation_adopted", {}), ("a", "book_saved", {"book_id": "actual-book"}),
        ("b", "generation_started", {}), ("b", "generation_fallback", {"reason": "parse_failed"}),
        ("b", "book_save_failed", {}),
        ("c", "generation_started", {}), ("c", "generation_cancelled", {}),
        ("d", "generation_started", {}),
    ]
    for seq, (attempt, event, data) in enumerate(stages, 2):
        lines += chunks(event, seq, attempt=attempt, data=data)
    summary = collector.collect_lines(lines)["summary"]
    assert summary["attempts_started"] == 4
    assert summary["generated_adopted"] == 1
    assert summary["fallbacks"] == summary["cancelled"] == summary["books_saved"] == 1
    assert summary["save_failed"] == summary["attempts_without_outcome"] == 1
    assert summary["generation_success_rate"] is None
    assert summary["attempts"][0]["book_ids"] == ["actual-book"]


def test_missing_sequences_and_late_enable_are_incomplete():
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data={"started_mid_session": True})
    lines += chunks("turn_after", 3)
    result = collector.collect_lines(lines)
    assert result["summary"]["sessions"][0]["source_start_available"] is False
    assert any(issue["reason"] == "missing_sequences" for issue in result["issues"])


def test_write_bundle_is_private_atomic_and_refuses_existing_target(tmp_path):
    collector = load_collector()
    result = collector.collect_lines(chunks("utterance", 1, data={"raw_text": "합성 원문"}))
    target = tmp_path / "private-run"
    collector.write_bundle(result, target)
    assert json.loads((target / "events.json").read_text(encoding="utf-8"))[0]["data"]["raw_text"] == "합성 원문"
    manifest = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["data_scope"] == "local_private_not_for_publication"
    original = (target / "events.json").read_bytes()
    with pytest.raises(FileExistsError):
        collector.write_bundle({"events": []}, target)
    assert (target / "events.json").read_bytes() == original


def test_live_command_reads_logs_without_clearing_or_operating_phone():
    collector = load_collector()
    command = collector.adb_command("adb.exe", "synthetic-device")
    assert command == ["adb.exe", "-s", "synthetic-device", "logcat", "-v", "raw", "-T", "1", "-s", "OttoTrace:I", "*:S"]


def test_excessive_parts_bytes_and_deep_json_are_diagnostic_not_crashes():
    collector = load_collector()
    envelope = {"schema_version": 1, "session_id": "demo", "sequence": 1,
                "part": 1, "parts": 10, "payload": "x" * 9000, "truncated": False}
    lines = []
    for part in range(1, 11):
        lines.append("story_eval | " + json.dumps({**envelope, "part": part}))
    # Malformed deeply nested lines can occur in a corrupt file. Do not echo them.
    lines.append("story_eval | " + "[" * 5000 + "]" * 5000)
    result = collector.collect_lines(lines)
    assert not result["events"]
    assert {issue["reason"] for issue in result["issues"]} == {"payload_limit", "invalid_envelope"}


def test_all_terminal_events_are_required_before_a_saved_book_can_be_complete():
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data={"started_mid_session": False})
    lines += chunks("generation_started", 2)
    lines += chunks("generation_adopted", 3)
    lines += chunks("book_saved", 4, data={"book_id": "book"})
    result = collector.collect_lines(lines)
    assert result["summary"]["attempts"][0]["required_events_observed"] is False
    assert result["summary"]["attempts"][0]["source_stages_complete"] is None


@pytest.mark.parametrize("field", ["access_token", "guardian_pin", "apiKey", "response_headers", "audio_path"])
def test_credential_aliases_are_quarantined_without_their_values(field):
    collector = load_collector()
    result = collector.collect_lines(chunks("generation_request", 1, data={field: "synthetic-sensitive-value"}))
    assert not result["events"]
    assert "synthetic-sensitive-value" not in json.dumps(result)
    assert result["issues"][0]["reason"] == "forbidden_field"


@pytest.mark.parametrize("metadata", [{"started_mid_session": False}, {"started_mid_session": 0, "missing_source": 0}])
def test_unattributable_corrupt_line_or_invalid_metadata_prevents_completeness(metadata):
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data=metadata)
    if metadata["started_mid_session"] is False:
        lines.append("story_eval | corrupt")
    assert collector.collect_lines(lines)["summary"]["sessions"][0]["source_start_available"] is False


def test_observed_stage_names_never_prove_content_completeness():
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data={"started_mid_session": False, "missing_source": False})
    for seq, event in enumerate(["generation_started", "generation_request", "generation_response",
                                 "generation_adopted", "book_save_started", "book_saved"], 2):
        lines += chunks(event, seq, data={"book_id": "book"} if event == "book_saved" else {})
    result = collector.collect_lines(lines)
    assert result["summary"]["sessions"][0]["source_start_available"] is True
    assert result["summary"]["attempts"][0]["required_events_observed"] is True
    assert result["summary"]["attempts"][0]["source_stages_complete"] is None
    assert result["summary"]["sessions"][0]["needs_content_review"] is True


def test_capture_input_budget_stops_unbounded_log_retention():
    collector = load_collector()
    collector.MAX_CAPTURE_BYTES = 64
    result = collector.collect_lines(chunks("utterance", 1, data={"raw_text": "합성"}))
    assert not result["events"]
    assert result["issues"][0]["reason"] == "capture_limit"


def test_live_decode_error_preserves_prior_events_as_incomplete(monkeypatch, tmp_path):
    collector = load_collector()
    lines = chunks("session_started", 1, attempt=None, data={"started_mid_session": False, "missing_source": False})
    def broken_stream():
        yield from lines
        raise UnicodeDecodeError("utf8", b"\xff", 0, 1, "synthetic corrupt line")
    class FakeProcess:
        stdout = broken_stream()
        def terminate(self):
            pass
        def wait(self, timeout):
            return 0
    monkeypatch.setattr(collector.subprocess, "Popen", lambda *args, **kwargs: FakeProcess())
    target = tmp_path / "partial-run"
    collector.main(["--adb", "fake-adb", "--serial", "fake-device", "--out", str(target)])
    manifest = json.loads((target / "manifest.json").read_text(encoding="utf8"))
    assert manifest["issues"][0]["reason"] == "stream_decode_failed"
    assert manifest["summary"]["sessions"][0]["source_start_available"] is False
    assert len(json.loads((target / "events.json").read_text(encoding="utf8"))) == 1
