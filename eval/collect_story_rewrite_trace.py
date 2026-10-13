"""Collect opt-in story_eval log chunks locally, without model calls or phone writes.

Captured text is PRIVATE and is not anonymized. This tool never uploads it.
It cannot recover events before collection began or reconstruct lost chunks.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


MAX_PAYLOAD_BYTES = 65_536
MAX_PARTS = 256
MAX_RECORDS = 50_000
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
EVENTS = frozenset({
    "session_started", "session_reset", "session_paused", "session_resumed",
    "session_restored", "utterance", "turn_before", "turn_after", "turn_restored",
    "generation_started", "generation_request", "generation_response",
    "generation_adopted", "generation_fallback", "generation_cancelled",
    "book_save_started", "book_saved", "book_save_failed", "capture_disabled",
})
FORBIDDEN_FIELDS = frozenset({
    "apikey", "key", "password", "pin", "authorization", "auth", "token",
    "headers", "url", "query", "endpoint", "baseurl", "serverurl", "path",
    "audiopath", "audio", "soundclipid", "image", "imageurl", "imagepath",
    "rawbody", "errorbody", "stacktrace", "preferences", "prefs",
})


def _integer(value, low, high):
    return type(value) is int and low <= value <= high


def _identifier(value):
    return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9_-]{1,96}", value))


def _unsafe_field(value):
    if isinstance(value, dict):
        for key, item in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", key.lower())
            sensitive_alias = normalized.endswith(("token", "apikey", "password", "pin", "headers", "url", "path", "soundclipid"))
            if normalized in FORBIDDEN_FIELDS or sensitive_alias or _unsafe_field(item):
                return True
    elif isinstance(value, list):
        return any(_unsafe_field(item) for item in value)
    return False


def collect_lines(lines):
    """Reassemble complete JSON events; quarantine inconsistent/lost evidence."""
    groups, issues, capture_bytes = {}, [], 0
    marker = "story_eval | "
    for line in lines:
        if marker not in line:
            continue
        capture_bytes += len(line.encode("utf-8", errors="replace"))
        if capture_bytes > MAX_CAPTURE_BYTES:
            issues.append({"reason": "capture_limit"})
            break
        if len(line) > 16_384:
            issues.append({"reason": "line_limit"})
            continue
        try:
            envelope = json.loads(line.split(marker, 1)[1])
        except (json.JSONDecodeError, ValueError, RecursionError):
            issues.append({"reason": "invalid_envelope"})
            continue
        if not isinstance(envelope, dict):
            issues.append({"reason": "invalid_envelope"})
            continue
        session, sequence = envelope.get("session_id"), envelope.get("sequence")
        valid_key = _identifier(session) and _integer(sequence, 1, 1_000_000)
        key = (session, sequence) if valid_key else None
        reason = None
        if envelope.get("schema_version") != 1 or type(envelope.get("schema_version")) is not int:
            reason = "unsupported_schema"
        elif (not valid_key or not _integer(envelope.get("parts"), 1, MAX_PARTS)
              or not _integer(envelope.get("part"), 1, envelope.get("parts", 0))
              or not isinstance(envelope.get("payload"), str)
              or type(envelope.get("truncated")) is not bool):
            reason = "invalid_envelope"
        elif envelope["truncated"]:
            reason = "payload_limit"
        if key not in groups and len(groups) >= MAX_RECORDS:
            issues.append({"reason": "record_limit"})
            break
        if reason:
            if key:
                group = groups.setdefault(key, {"parts": {}, "count": envelope.get("parts"), "reason": reason})
                group["reason"] = reason
            else:
                issues.append({"reason": reason})
            continue
        group = groups.setdefault(key, {"parts": {}, "count": envelope["parts"], "reason": None})
        part, payload = envelope["part"], envelope["payload"]
        if group["reason"]:
            continue
        if group["count"] != envelope["parts"] or (part in group["parts"] and group["parts"][part] != payload):
            group["reason"] = "conflicting_part"
        else:
            group["parts"][part] = payload
            try:
                if sum(len(piece.encode("utf-8")) for piece in group["parts"].values()) > MAX_PAYLOAD_BYTES:
                    group["reason"] = "payload_limit"
                    group["parts"].clear()
            except UnicodeError:
                group["reason"] = "invalid_event"
                group["parts"].clear()

    events = []
    for (session, sequence), group in sorted(groups.items()):
        reason = group["reason"]
        if not reason and len(group["parts"]) != group["count"]:
            reason = "missing_parts"
        if not reason:
            payload = "".join(group["parts"][part] for part in range(1, group["count"] + 1))
            try:
                if len(payload.encode("utf-8")) > MAX_PAYLOAD_BYTES:
                    reason = "payload_limit"
                else:
                    decoded = json.loads(payload)
                    if (not isinstance(decoded, dict) or decoded.get("event") not in EVENTS
                            or not isinstance(decoded.get("data"), dict)
                            or (decoded.get("attempt_id") is not None and not _identifier(decoded["attempt_id"]))
                            or set(decoded) != {"event", "attempt_id", "data"}):
                        reason = "invalid_event"
                    elif _unsafe_field(decoded):
                        reason = "forbidden_field"
                    else:
                        events.append({"session_id": session, "sequence": sequence, **decoded})
            except (json.JSONDecodeError, UnicodeError, RecursionError, TypeError, ValueError):
                reason = "invalid_event"
        if reason:
            issues.append({"session_id": session, "sequence": sequence, "reason": reason})

    sessions = sorted({key[0] for key in groups})
    for session in sessions:
        sequences = sorted(sequence for sid, sequence in groups if sid == session)
        if sequences and (sequences[0] != 1 or any(b != a + 1 for a, b in zip(sequences, sequences[1:]))):
            issues.append({"session_id": session, "reason": "missing_sequences"})
    return {"events": events, "issues": issues, "summary": _summary(events, issues, sessions)}


def _summary(events, issues, session_ids):
    attempts, sessions = [], []
    for session in session_ids:
        subset = [event for event in events if event["session_id"] == session]
        start = next((event for event in subset if event["event"] == "session_started"), None)
        source_start_available = bool(start and start["data"].get("started_mid_session") is False
                               and start["data"].get("missing_source") is False
                               and not any(issue.get("session_id") in (None, session) for issue in issues))
        sessions.append({
            "session_id": session, "event_count": len(subset),
            "source_start_available": source_start_available,
            "needs_content_review": True,
        })
        for attempt_id in sorted({event["attempt_id"] for event in subset if event["attempt_id"]}):
            stages = [event for event in subset if event["attempt_id"] == attempt_id]
            names = {event["event"] for event in stages}
            attempts.append({
                "session_id": session, "attempt_id": attempt_id,
                "started": "generation_started" in names,
                "adopted": "generation_adopted" in names,
                "fallback": "generation_fallback" in names,
                "cancelled": "generation_cancelled" in names,
                "save_failed": "book_save_failed" in names,
                "book_ids": sorted({event["data"]["book_id"] for event in stages
                                    if event["event"] == "book_saved" and isinstance(event["data"].get("book_id"), str)}),
                "outcome_observed": bool(names & {"generation_adopted", "generation_fallback", "generation_cancelled"}),
                "required_events_observed": bool(source_start_available and {
                    "generation_started", "generation_request", "generation_response",
                    "generation_adopted", "book_save_started", "book_saved",
                }.issubset(names)),
                "source_stages_complete": None,
                "completeness_note": "Stage presence alone does not verify fields, ordering, actor provenance or saved-book association.",
            })
    return {
        "sessions": sessions, "attempts": attempts,
        "attempts_started": sum(item["started"] for item in attempts),
        "generated_adopted": sum(item["adopted"] for item in attempts),
        "fallbacks": sum(item["fallback"] for item in attempts),
        "cancelled": sum(item["cancelled"] for item in attempts),
        "books_saved": sum(len(item["book_ids"]) for item in attempts),
        "save_failed": sum(item["save_failed"] for item in attempts),
        "attempts_without_outcome": sum(item["started"] and not item["outcome_observed"] for item in attempts),
        "generation_success_rate": None,
        "rate_note": "Counts are observed events, not a complete cohort or a rewrite-quality judgment.",
    }


def write_bundle(result, destination):
    """Create a new bundle only; an existing destination is never replaced."""
    destination = Path(destination)
    if destination.exists():
        raise FileExistsError("Capture destination already exists")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=".story-capture-", dir=destination.parent))
    try:
        serialized = json.dumps(result["events"], ensure_ascii=False, indent=2).encode("utf-8")
        (temporary / "events.json").write_bytes(serialized)
        manifest = {
            "schema_version": 1, "data_scope": "local_private_not_for_publication",
            "events_sha256": hashlib.sha256(serialized).hexdigest(),
            "issues": result["issues"], "summary": result["summary"],
            "server_version": "unknown", "app_git_sha": "unknown",
        }
        (temporary / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        if destination.exists():
            raise FileExistsError("Capture destination already exists")
        os.rename(temporary, destination)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def adb_command(adb, serial):
    return [adb, "-s", serial, "logcat", "-v", "raw", "-T", "1", "-s", "OttoTrace:I", "*:S"]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", type=Path, help="Existing UTF-8 log file")
    source.add_argument("--adb", help="Explicit adb executable for a new, opt-in demo")
    parser.add_argument("--serial", help="Currently verified device serial (live capture only)")
    parser.add_argument("--out", required=True, type=Path, help="New private folder outside the repository")
    args = parser.parse_args(argv)
    if args.out.exists():
        parser.error("Output folder already exists; use a new run folder")
    if args.adb and not args.serial:
        parser.error("--serial is required for live capture")
    if args.input:
        with args.input.open(encoding="utf-8-sig") as stream:
            result = collect_lines(stream)
    else:
        # No shell, no log clearing, no app launch/install, no UI or permission writes.
        process = subprocess.Popen(adb_command(args.adb, args.serial), stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, text=True, encoding="utf-8", errors="strict")
        lines, live_bytes, limit_reached, read_problem = [], 0, False, None
        print("Local private capture started. Stop with Ctrl+C after the demo.")
        try:
            for line in process.stdout:
                if "story_eval | " in line:
                    live_bytes += len(line.encode("utf-8"))
                    if live_bytes > MAX_CAPTURE_BYTES:
                        limit_reached = True
                        break
                    lines.append(line)
                if len(lines) >= MAX_RECORDS * 4:
                    limit_reached = True
                    break
        except KeyboardInterrupt:
            pass
        except UnicodeError:
            read_problem = "stream_decode_failed"
        except OSError:
            read_problem = "stream_read_failed"
        finally:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
        result = collect_lines(lines)
        if limit_reached or read_problem:
            result["issues"].append({"reason": read_problem or "capture_limit"})
            result["summary"] = _summary(result["events"], result["issues"],
                                         [session["session_id"] for session in result["summary"]["sessions"]])
    write_bundle(result, args.out)
    print(json.dumps({"events": len(result["events"]), "issues": len(result["issues"]),
                      "output": str(args.out.resolve()), "scope": "local_private"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
