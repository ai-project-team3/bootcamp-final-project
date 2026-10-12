"""Check capture failure semantics without sending any network requests."""
import base64
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from eval import bench_background_places as bench


class BackgroundCaptureTest(unittest.TestCase):
    def run_capture(self, replies):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "run"
            with patch.object(bench, "request_json", side_effect=replies) as request, \
                    patch.object(bench.time, "sleep"), contextlib.redirect_stdout(io.StringIO()):
                code = bench.run("https://example.test", out)
            return code, json.loads((out / "responses.json").read_text(encoding="utf-8")), request.call_count

    def test_unverified_health_sends_no_image_requests(self):
        for health in ({"http_status": 403}, {"http_status": 200, "body": {"mock": True}},
                       {"http_status": 200, "body": {"mock": False, "status": "down"}}):
            with self.subTest(health=health):
                code, report, count = self.run_capture([health])
                self.assertEqual((code, count, report["cases"]), (1, 1, []))

    def test_preset_mock_and_missing_png_are_not_capture_success(self):
        bodies = [
            {"preset": True, "reason": "over 13s", "scene": "a park"},
            {"preset": False, "reason": "mock", "scene": "mock scene"},
            {"preset": False, "reason": "ok", "scene": "a park"},
            {"preset": False, "reason": "ok", "scene": "a park", "png_base64": "invalid"},
            {},
            {"preset": True, "reason": "check: flagged", "scene": "a park"},
        ]
        replies = [{"http_status": 200, "body": {"mock": False, "status": "ok"}}]
        replies += [{"http_status": 200, "body": body} for body in bodies]
        code, report, count = self.run_capture(replies)
        self.assertEqual((code, count, len(report["cases"])), (1, 7, 6))
        self.assertFalse(any(row["capture_ok"] for row in report["cases"]))
        self.assertEqual(report["cases"][0]["response"]["scene"], "a park")
        self.assertNotIn("png_base64", json.dumps(report))

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(bench, "request_json") as request:
            with self.assertRaises(FileExistsError):
                bench.run("https://example.test", Path(directory))
            request.assert_not_called()

    def test_mock_sized_png_is_rejected(self):
        png = b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR\x00\x00\x00\x01\x00\x00\x00\x01"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "placeholder.png"
            with self.assertRaises(ValueError):
                bench.save_png(base64.b64encode(png), path)
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
