from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from eval.providers.base import ProviderResult, StreamChunk, Usage
from eval.providers.http_sse import HttpStatusError
from eval.run_judge import run


SCHEMA = {
    "type": "object",
    "properties": {"reason": {"type": "string"}},
    "required": ["reason"],
    "additionalProperties": False,
}


class FlakyProvider:
    def __init__(self, failures: int) -> None:
        self.failures = failures
        self.calls = 0

    def stream_judge(self, **kwargs) -> ProviderResult:
        self.calls += 1
        call = self.calls

        def chunks():
            if call <= self.failures:
                raise HttpStatusError(429, "https://api.example.test", "rate limited", {})
            yield StreamChunk('{"reason":"ok"}')

        return ProviderResult(chunks=chunks(), usage=Usage(10, 3))


class RetryTest(unittest.TestCase):
    def _files(self, root: Path, count: int = 1) -> tuple[Path, Path, Path, Path]:
        fixtures = root / "fixtures.jsonl"
        fixtures.write_text(
            "".join(
                json.dumps(
                    {"id": f"j{i}", "slots": {}, "asked": "place", "template": "A", "utterance": "우주"},
                    ensure_ascii=False,
                )
                + "\n"
                for i in range(count)
            ),
            encoding="utf-8",
        )
        prompt = root / "prompt.md"
        prompt.write_text("Return JSON.", encoding="utf-8")
        schema = root / "schema.json"
        schema.write_text(json.dumps(SCHEMA), encoding="utf-8")
        return fixtures, prompt, schema, root / "raw.jsonl"

    def test_retries_429_then_succeeds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            files = self._files(Path(tmp))
            provider = FlakyProvider(failures=2)
            with patch("eval.run_judge.create_provider", return_value=provider), patch(
                "eval.run_judge.time.sleep", return_value=None
            ) as sleep:
                rows = run(
                    model="mock-judge-v1",
                    fixtures=files[0],
                    prompt_path=files[1],
                    schema_path=files[2],
                    out=files[3],
                    max_retries=2,
                    request_delay=0,
                )

            self.assertTrue(rows[0]["ok"])
            self.assertEqual(rows[0]["attempts"], 3)
            self.assertEqual(provider.calls, 3)
            self.assertEqual(sleep.call_count, 2)

    def test_exhausted_429_is_recorded_and_next_item_runs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            files = self._files(Path(tmp), count=2)
            provider = FlakyProvider(failures=99)
            with patch("eval.run_judge.create_provider", return_value=provider), patch(
                "eval.run_judge.time.sleep", return_value=None
            ):
                rows = run(
                    model="mock-judge-v1",
                    fixtures=files[0],
                    prompt_path=files[1],
                    schema_path=files[2],
                    out=files[3],
                    max_retries=1,
                    request_delay=0,
                )

            self.assertEqual(len(rows), 2)
            self.assertTrue(all(not row["ok"] for row in rows))
            self.assertTrue(all(row["attempts"] == 2 for row in rows))
            self.assertTrue(all("HTTP 429" in row["error"] for row in rows))

    def test_request_delay_is_applied_between_items(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            files = self._files(Path(tmp), count=2)
            provider = FlakyProvider(failures=0)
            with patch("eval.run_judge.create_provider", return_value=provider), patch(
                "eval.run_judge.time.sleep", return_value=None
            ) as sleep:
                run(
                    model="mock-judge-v1",
                    fixtures=files[0],
                    prompt_path=files[1],
                    schema_path=files[2],
                    out=files[3],
                    max_retries=0,
                    request_delay=0.25,
                )

            sleep.assert_called_once_with(0.25)


if __name__ == "__main__":
    unittest.main()
