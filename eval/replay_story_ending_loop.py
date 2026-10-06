"""Replay anonymous #156 completion cases through an explicitly supplied server, without reading keys.

    python eval/replay_story_ending_loop.py --url https://otto-back.shelldocs.cloud --runs 2 --output result.json

Exit 1 means a completion case still fails. Exit 2 means setup/network failure.
This is a three-case regression probe, not a broad model-quality benchmark.
"""
from __future__ import annotations

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
from pathlib import Path


def call(base: str, path: str, body: dict | None = None) -> dict:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    request = urllib.request.Request(base + path, data=data,
                                     headers={"Content-Type": "application/json",
                                              "User-Agent": "OttoEndingRegressionProbe/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="explicit server URL; no environment/config files are loaded")
    parser.add_argument("--runs", type=int, default=2, choices=range(1, 6))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = args.url.rstrip("/")
    fixtures = Path(__file__).with_name("fixtures_story_ending_loop.jsonl")
    cases = [json.loads(line) for line in fixtures.read_text(encoding="utf-8").splitlines() if line.strip()]
    records = []
    try:
        health = call(base, "/health")
        if health.get("status") != "ok" or health.get("mock") is not False:
            print("Not a healthy live server; no model probes were run.")
            return 2
        for trial in range(1, args.runs + 1):
            for case in cases:
                start = time.monotonic()
                response = call(base, "/turn", case["request"])
                seconds = round(time.monotonic() - start, 3)
                verdict = response.get("judge") or {}
                line = response.get("line") or {}
                ready = verdict.get("story_ready") is True
                stranded = bool(verdict) and not ready and verdict.get("next_slot") is None
                passed = ready == case["expect_ready"] and (not ready or line.get("question") is None)
                records.append({"id": case["id"], "trial": trial, "seconds": seconds,
                                "passed": passed, "stranded": stranded, "response": response})
                print(f"{case['id']} run={trial} ready={ready} next={verdict.get('next_slot')} "
                      f"stranded={stranded} seconds={seconds} passed={passed}")
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        # Don't echo error bodies, which might contain server configuration or request data.
        print(f"Probe stopped: {type(error).__name__} status={getattr(error, 'code', None)}")
        args.output.write_text(json.dumps({"server": base, "records": records,
                                          "error_type": type(error).__name__}, indent=2, ensure_ascii=False), encoding="utf-8")
        return 2
    summary = {"requests": len(records), "passed": sum(r["passed"] for r in records),
               "stranded": sum(r["stranded"] for r in records),
               "median_seconds": round(statistics.median(r["seconds"] for r in records), 3)}
    args.output.write_text(json.dumps({"server": base, "summary": summary, "records": records},
                                     ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary))
    return 0 if summary["passed"] == summary["requests"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
