"""The synthetic comparison must preserve requests and never invent quality scores."""
import asyncio
import json
import sys
from pathlib import Path

import pytest

EVAL = Path(__file__).resolve().parents[2] / "eval"
sys.path.insert(0, str(EVAL))


def bench():
    import bench_story_rewrite_fidelity
    return bench_story_rewrite_fidelity


def test_dry_run_writes_both_arms_without_a_model_call(tmp_path, monkeypatch):
    b = bench()

    async def forbidden(*args, **kwargs):
        pytest.fail("A dry run must never call the model")

    monkeypatch.setattr(b.runtime()["llm"], "complete", forbidden)
    out = tmp_path / "comparison"
    assert asyncio.run(b.main(["--out", str(out)])) == 0
    rows = [json.loads(line) for line in (out / "requests.jsonl").read_text(encoding="utf-8").splitlines()]
    assert len(rows) == 24
    assert {r["arm"] for r in rows} == {"A", "B"}
    assert all(r["status"] == "dry_run" and r["manual_book_pass"] is None for r in rows)
    pairs = {}
    for row in rows:
        pairs.setdefault((row["id"], row["run"]), []).append(row)
    for pair in pairs.values():
        assert pair[0]["request"] == pair[1]["request"]
        assert pair[0]["request_sha256"] == pair[1]["request_sha256"]
        assert pair[0]["schema_sha256"] == pair[1]["schema_sha256"]
        assert len(pair[0]["request"]["slots"]) == 12
    manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["planned_calls"] == 24 and manifest["attempted_calls"] == 0


def test_candidate_adds_sources_without_replacing_slots_or_story_keep():
    b = bench()
    case = b.load_cases(b.FIXTURES)[-1]
    req = b.request(case)
    req.keep = "UNSUPPORTED_STORY_KEEP"
    before = req.model_dump()
    inputs = b.inputs(req)
    assert '"solution":"mascot"' in inputs["B"]["user"]
    assert '"companion":"child"' in inputs["B"]["user"]
    assert "UNSUPPORTED_STORY_KEEP" not in inputs["A"]["user"]
    assert "UNSUPPORTED_STORY_KEEP" not in inputs["B"]["user"]
    assert req.model_dump() == before
    assert inputs["B"]["user"].startswith(inputs["A"]["user"])


def test_keyword_hints_cannot_turn_a_changed_ending_into_a_pass():
    b = bench()
    case = b.load_cases(b.FIXTURES)[0]
    req = b.request(case)
    result = b.runtime()["StoryResult"].model_validate({"scenes": [
        {"index": i + 1, "caption": "숲집 나무 종 돌탑 모두 만들었어요.", "keywords": "home"}
        for i in range(len(req.pages))]})
    score = b.score(result, req, case)
    assert score["manual_book_pass"] is None
    assert score["assistance_only"] is True
    assert any(not hint["found"] for hint in score["sentinel_checks"])


@pytest.mark.parametrize("args", [["--runs", "3"], ["--runs", "0"], ["--limit", "7"]])
def test_call_bound_cannot_be_increased(tmp_path, args):
    with pytest.raises(SystemExit):
        asyncio.run(bench().main(["--out", str(tmp_path / "new"), *args]))


def test_existing_output_is_refused_before_any_call(tmp_path, monkeypatch):
    b = bench()
    out = tmp_path / "existing"
    out.mkdir()
    marker = out / "marker.txt"
    marker.write_text("existing", encoding="utf-8")
    with pytest.raises(SystemExit):
        asyncio.run(b.main(["--out", str(out)]))
    assert marker.read_text(encoding="utf-8") == "existing"


def test_missing_credentials_refuse_a_real_run_before_output_or_calls(tmp_path, monkeypatch):
    b = bench()
    monkeypatch.setattr(b.runtime()["settings"], "openai_api_key", "")

    async def forbidden(*args, **kwargs):
        pytest.fail("Preflight must refuse the run before a model call")

    monkeypatch.setattr(b.runtime()["llm"], "complete", forbidden)
    out = tmp_path / "not_created"
    with pytest.raises(SystemExit) as error:
        asyncio.run(b.main(["--run", "--out", str(out)]))
    assert error.value.code == 2
    assert not out.exists()


def test_dry_run_refuses_a_settings_file_argument_before_reading_it(tmp_path):
    out = tmp_path / "not_created"
    with pytest.raises(SystemExit) as error:
        asyncio.run(bench().main(["--dry-run", "--env", str(tmp_path / "missing.env"), "--out", str(out)]))
    assert error.value.code == 2
    assert not out.exists()


def test_a_quota_failure_is_counted_and_stops_the_remaining_calls(tmp_path, monkeypatch):
    b = bench()
    rt = b.runtime()
    monkeypatch.setattr(rt["settings"], "openai_api_key", "test-not-a-real-key")
    count = 0

    async def quota(*args, **kwargs):
        nonlocal count
        count += 1
        raise rt["llm"].LLMError("DO_NOT_STORE_RAW_ERROR", reason="quota")

    monkeypatch.setattr(rt["llm"], "complete", quota)
    out = tmp_path / "quota"
    assert asyncio.run(b.main(["--run", "--out", str(out), "--limit", "1"])) == 1
    rows = [json.loads(line) for line in (out / "results.jsonl").read_text(encoding="utf-8").splitlines()]
    assert count == 1
    assert [r["status"] for r in rows] == ["error", "skipped", "skipped", "skipped"]
    assert rows[0]["error_reason"] == "quota"
    assert rows[0]["usage"] is None
    assert "DO_NOT_STORE_RAW_ERROR" not in (out / "results.jsonl").read_text(encoding="utf-8")
    manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["planned_calls"] == 4 and manifest["attempted_calls"] == 1
    assert manifest["errors"] == 1 and manifest["skipped"] == 3


def test_real_run_records_usage_and_still_needs_manual_review(tmp_path, monkeypatch):
    b = bench()
    rt = b.runtime()
    monkeypatch.setattr(rt["settings"], "openai_api_key", "test-not-a-real-key")
    seen = []

    async def fake(system, user, schema, **kwargs):
        seen.append((system, user, schema, kwargs))
        rt["llm"].on_usage("story", 100, 40)
        rt["llm"].on_cached("story", 20)
        return {"scenes": [{"index": i + 1, "caption": "돌아왔어요.", "keywords": "home"}
                            for i in range(6)]}

    monkeypatch.setattr(rt["llm"], "complete", fake)
    old_usage, old_cached = rt["llm"].on_usage, rt["llm"].on_cached
    out = tmp_path / "generated"
    assert asyncio.run(b.main(["--run", "--out", str(out), "--limit", "1", "--runs", "1"])) == 0
    rows = [json.loads(line) for line in (out / "results.jsonl").read_text(encoding="utf-8").splitlines()]
    assert len(rows) == len(seen) == 2
    assert all(r["usage"] == {"input_tokens": 100, "output_tokens": 40, "cached_input_tokens": 20} for r in rows)
    assert all(r["manual_book_pass"] is None for r in rows)
    assert all(s[3]["max_output_tokens"] == 6000 and s[3]["timeout_s"] == 55 for s in seen)
    assert rt["llm"].on_usage is old_usage and rt["llm"].on_cached is old_cached
