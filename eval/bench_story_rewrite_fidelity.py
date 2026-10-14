"""Synthetic story fidelity comparison; default is an offline, zero-call dry run.

Only --run performs calls, sequentially, at most 6 cases x 2 repeats x 2 arms.
Sentinels assist human review; they never establish semantic preservation.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import re
import sys
import time
from functools import lru_cache
from pathlib import Path
from unittest.mock import patch

EVAL = Path(__file__).resolve().parent
FIXTURES = EVAL / "fixtures_story_rewrite_fidelity_1013.jsonl"
CANDIDATE = EVAL / "story_rewrite_fidelity_candidate_1013.md"


@lru_cache(maxsize=1)
def runtime() -> dict:
    """Default preparation must not read any settings file, even during import."""
    from pydantic_settings import BaseSettings

    sys.path.insert(0, str(EVAL.parent / "backend"))
    sys.path.insert(0, str(EVAL))
    original = BaseSettings.__init__

    def no_file(self, *args, **kwargs):
        kwargs["_env_file"] = None
        original(self, *args, **kwargs)

    with patch.object(BaseSettings, "__init__", no_file):
        from app.config import Settings, settings
        from app.llm import client as llm
        from app.routers import story
        from app.schemas.judge import SLOT_NAMES
        from app.schemas.story import StoryRequest, StoryResult
        from prompt_block import system_block
    return {"Settings": Settings, "settings": settings, "llm": llm, "story": story,
            "SLOT_NAMES": SLOT_NAMES, "StoryRequest": StoryRequest, "StoryResult": StoryResult,
            "system_block": system_block}


def digest(value) -> str:
    data = value if isinstance(value, bytes) else value.encode() if isinstance(value, str) else json.dumps(value, ensure_ascii=False, sort_keys=True).encode()
    return hashlib.sha256(data).hexdigest()


def load_cases(path: Path) -> list[dict]:
    cases = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not 1 <= len(cases) <= 6 or len({c["id"] for c in cases}) != len(cases):
        raise ValueError("Use 1-6 uniquely identified cases")
    names = set(runtime()["SLOT_NAMES"])
    for case in cases:
        if case.get("synthetic") is not True or case.get("contains_real_child_data") is not False:
            raise ValueError("Only original synthetic cases are permitted")
        if set(case["slots"]) != names or set(case.get("slot_by", {})) - names:
            raise ValueError("Use exactly the existing 12 slot names")
        if set(case.get("slot_by", {}).values()) - {"child", "card", "mascot"}:
            raise ValueError("Unknown source")
        for hint in case["sentinels"]:
            re.compile(hint["pattern"])
    return cases


def request(case: dict):
    return runtime()["StoryRequest"](mode="story", slots=case["slots"], slot_by=case.get("slot_by", {}),
                                     template=case.get("template"), level=case.get("level"), pages=case["pages"])


def inputs(req) -> dict:
    rt = runtime()
    baseline = rt["story"].user(req)
    sources = json.dumps(req.slot_by, ensure_ascii=False, separators=(",", ":"))
    # Keep story's existing HTTP contract: no new slots and no use of diary/coop keep.
    tail = (f"\n칸마다 by: {sources}\n[내용 보존과 쪽 배치]\n"
            "by는 child·card·mascot 출처다. 없는 출처를 child라고 추측하지 않는다. "
            "카드·오또가 채운 설정을 아이가 직접 말했다고 쓰지 않는다.\n"
            "solution의 여러 행동·대상·결과와 extra의 결말·그 뒤·계획·중단 의사를 모두 남긴다. "
            "미션 쪽은 기존 목록대로 하기 직전에서 멈춘다. 미션을 마친 뒤 앱이 붙이는 결과를 미리 쓰지 않는다. "
            "그 뒤 TOGETHER 마무리 쪽에는 입력의 solution과 extra의 결말을 보존한다. "
            "TOGETHER가 없으면 마지막 비미션 쪽에 배치한다. 미래·부정·그만두기는 입력 뜻을 유지한다.")
    return {"A": {"system": rt["story"].system("story"), "user": baseline},
            "B": {"system": rt["system_block"](CANDIDATE), "user": baseline + tail}}


def score(book, req, case: dict) -> dict:
    captions = "\n".join(sc.caption for sc in book.scenes)
    return {"rejection": runtime()["story"].check(book, req.mode, req.pages),
            "assistance_only": True, "manual_book_pass": None,
            "sentinel_checks": [{"id": h["id"], "found": bool(re.search(h["pattern"], captions))}
                                for h in case["sentinels"]]}


def bounded(high: int):
    def parse(value: str) -> int:
        n = int(value)
        if not 1 <= n <= high:
            raise argparse.ArgumentTypeError(f"Must be between 1 and {high}")
        return n
    return parse


async def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--run", action="store_true", help="Explicitly perform the bounded, paid comparison")
    mode.add_argument("--dry-run", dest="run", action="store_false", help="Prepare inputs without calls (default)")
    ap.set_defaults(run=False)
    ap.add_argument("--env", type=Path, help="Existing team settings file, used only with --run; never printed")
    ap.add_argument("--fixtures", type=Path, default=FIXTURES)
    ap.add_argument("--out", type=Path, required=True, help="A new output directory; existing output is refused")
    ap.add_argument("--runs", type=bounded(2), default=2)
    ap.add_argument("--limit", type=bounded(6), default=6)
    a = ap.parse_args(argv)
    if a.out.exists():
        ap.error("Output already exists; choose a new directory")
    if a.env and not a.run:
        ap.error("--env is only used with --run; dry runs do not read settings files")
    rt = runtime()
    try:
        cases = load_cases(a.fixtures)[:a.limit]
    except (ValueError, KeyError) as error:
        ap.error(str(error) if isinstance(error, ValueError) else "Invalid fixture fields")
    settings = rt["settings"]
    previous = {}
    if a.env:
        if not a.env.is_file():
            ap.error("Existing settings file is unavailable")
        other = rt["Settings"](_env_file=a.env)
        for key in ("openai_api_key", "openai_base_url", "llm_provider", "llm_model", "llm_effort_story"):
            previous[key] = getattr(settings, key)
            setattr(settings, key, getattr(other, key))
    if a.run and (settings.llm_provider != "openai" or not settings.openai_api_key):
        for key, value in previous.items():
            setattr(settings, key, value)
        ap.error("Evaluation credential/provider is not configured; no calls made")
    schema = rt["story"].schema()
    manifest = {"schema_version": 1, "synthetic_only": True, "dry_run": not a.run,
                "model": settings.llm_model, "effort": settings.llm_effort_story,
                "fixtures_sha256": digest(a.fixtures.read_bytes()), "schema_sha256": digest(schema),
                "story_router_sha256": digest((EVAL.parent / "backend/app/routers/story.py").read_bytes()),
                "max_output_tokens": 6000, "timeout_s": 55, "concurrency": 1,
                "planned_calls": len(cases) * a.runs * 2, "attempted_calls": 0,
                "generated": 0, "rejected": 0, "errors": 0, "skipped": 0,
                "manual_book_pass_rate": None,
                "review_note": "Sentinels are hints only. Review every required fact, source and mission contract."}
    llm = rt["llm"]
    old_usage, old_cached = llm.on_usage, llm.on_cached
    usage = None

    def on_usage(name, tin, tout):
        nonlocal usage
        usage = {"input_tokens": tin, "output_tokens": tout, "cached_input_tokens": None}

    def on_cached(name, cached):
        if usage is not None:
            usage["cached_input_tokens"] = cached

    stopped = False
    a.out.mkdir(parents=True, exist_ok=False)
    filename = "results.jsonl" if a.run else "requests.jsonl"
    try:
        if a.run:
            llm.on_usage, llm.on_cached = on_usage, on_cached
        with (a.out / filename).open("x", encoding="utf-8") as output:
            for run in range(1, a.runs + 1):
                for case in cases:
                    req = request(case)
                    arms = inputs(req)
                    # Reverse the order on repeat 2; every request stays frozen across arms.
                    for arm in ("AB" if run % 2 else "BA"):
                        data = arms[arm]
                        row = {"id": case["id"], "run": run, "arm": arm,
                               "request": req.model_dump(), "request_sha256": digest(req.model_dump()),
                               "system_sha256": digest(data["system"]), "user_sha256": digest(data["user"]),
                               "schema_sha256": manifest["schema_sha256"], "model": settings.llm_model,
                               "effort": settings.llm_effort_story, "manual_book_pass": None,
                               "required_facts": case["required_facts"], "unique_expressions": case["unique_expressions"],
                               "source_expectations": case.get("source_expectations", []),
                               "system": data["system"], "user": data["user"], "usage": None}
                        if not a.run:
                            row["status"] = "dry_run"
                        elif stopped:
                            row["status"] = "skipped"
                            row["skip_reason"] = "stopped_after_vendor_error"
                            manifest["skipped"] += 1
                        else:
                            usage = None
                            started = time.monotonic()
                            manifest["attempted_calls"] += 1
                            try:
                                raw = await llm.complete(data["system"], data["user"], schema, name="story",
                                                         effort=settings.llm_effort_story,
                                                         max_output_tokens=6000, timeout_s=55)
                                book = rt["StoryResult"].model_validate(raw)
                                row.update(score(book, req, case))
                                row.update(status="generated", book=book.model_dump())
                                manifest["generated"] += 1
                                manifest["rejected"] += int(row["rejection"] is not None)
                            except (llm.LLMError, ValueError) as error:
                                reason = getattr(error, "reason", None)
                                safe_reason = reason if reason in {"quota", "rate_limit", "auth", "timeout", "network", "server", "unavailable"} else None
                                row.update(status="error", error_kind=type(error).__name__,
                                           error_reason=safe_reason if isinstance(error, llm.LLMError) else "invalid_response")
                                manifest["errors"] += 1
                                # No retries. Any vendor failure stops the paid run, including quota/auth/timeouts.
                                stopped = isinstance(error, llm.LLMError)
                            row["usage"] = usage
                            row["seconds"] = round(time.monotonic() - started, 3)
                        output.write(json.dumps(row, ensure_ascii=False) + "\n")
                        output.flush()
                        print(f"{case['id']} repeat={run} arm={arm} status={row['status']}", flush=True)
    finally:
        llm.on_usage, llm.on_cached = old_usage, old_cached
        for key, value in previous.items():
            setattr(settings, key, value)
        (a.out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("dry_run", "planned_calls", "attempted_calls", "generated", "rejected", "errors", "skipped", "manual_book_pass_rate")}))
    return 1 if manifest["errors"] or manifest["rejected"] else 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
