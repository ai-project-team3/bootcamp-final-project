from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from eval.run_team_eval import render_diagnostics, render_results
from eval.score import has_reason_marker, load_jsonl, score_judge


class ScoreDiagnosticsTest(unittest.TestCase):
    def test_frozen_fixture_has_28_markers_and_10_traps(self) -> None:
        fixtures = load_jsonl(Path(__file__).parent / "fixtures_judge.jsonl")
        marked = [row for row in fixtures if has_reason_marker(row["utterance"])]
        self.assertEqual(len(fixtures), 100)
        self.assertEqual(len(marked), 28)
        self.assertEqual(sum(row["gold"]["s1_reason"] is False for row in marked), 10)

    def test_reason_marker_excludes_location_but_keeps_sequence_traps(self) -> None:
        self.assertFalse(has_reason_marker("거기서 친구들이 기다릴 거야."))
        self.assertFalse(has_reason_marker("어린이집에서 블록 쌓았어."))
        self.assertTrue(has_reason_marker("엄마한테 달려가서 말해."))
        self.assertTrue(has_reason_marker("친구가 왔어. 그래서 놀았어."))
        self.assertTrue(has_reason_marker("배가 고프니까 먹었어."))
        self.assertTrue(has_reason_marker("그러면 별도 딸 수 있어."))

    def test_subset_uses_gold_not_type_and_keeps_failed_rows_in_denominator(self) -> None:
        fixtures = [
            {"id": "j1", "utterance": "엄마한테 달려가서 말해.", "asked": "problem", "type": "S1", "gold": {"s1_reason": False, "slot_1": None}},
            {"id": "j2", "utterance": "배고프니까 먹었어.", "asked": "problem", "type": "other", "gold": {"s1_reason": True, "slot_1": None}},
            {"id": "j3", "utterance": "꽃게가 왔어.", "asked": "extra", "type": "other", "gold": {"s1_reason": False, "slot_1": "extra"}},
            {"id": "j4", "utterance": "그래서 놀았어.", "asked": "extra", "type": "S1", "gold": {"s1_reason": False, "slot_1": "extra"}},
        ]
        raw = [
            {"id": "j1", "ok": True, "pred": {"s1_reason": False, "slot_1": None}},
            {"id": "j2", "ok": True, "pred": {"s1_reason": True, "slot_1": None}},
            {"id": "j3", "ok": True, "pred": {"s1_reason": False, "slot_1": "extra"}},
            {"id": "j4", "ok": False, "error": "schema violation"},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            fixtures_path = Path(tmp) / "fixtures.jsonl"
            raw_path = Path(tmp) / "raw.jsonl"
            fixtures_path.write_text("\n".join(json.dumps(row) for row in fixtures) + "\n", encoding="utf-8")
            raw_path.write_text("\n".join(json.dumps(row) for row in raw) + "\n", encoding="utf-8")
            metrics = score_judge(fixtures=fixtures_path, raw=raw_path)
            raw_path.write_text(json.dumps(raw[0]) + "\n", encoding="utf-8")
            partial_metrics = score_judge(fixtures=fixtures_path, raw=raw_path)

        marked = metrics["diagnostics"]["s1_reason_marked"]
        extra = metrics["diagnostics"]["slot_1_extra"]
        self.assertEqual(marked["fixture_count"], 3)
        self.assertEqual(marked["count"], 2)
        self.assertEqual(marked["trap_count"], 2)
        self.assertEqual(marked["trap_scored_count"], 1)
        self.assertEqual(marked["trap_correct_count"], 1)
        self.assertEqual(marked["f1"], 1.0)
        self.assertEqual(extra["asked_count"], 2)
        self.assertEqual(extra["gold_count"], 2)
        self.assertEqual(extra["scored_gold_count"], 1)
        self.assertEqual(extra["f1"], 1.0)
        self.assertIn("2/3", render_diagnostics([("test-model", metrics)]))
        self.assertIn("표지·`extra` 보조 진단", render_results([("test-model", metrics)], 1400.0))
        self.assertEqual(partial_metrics["diagnostics"]["s1_reason_marked"]["fixture_count"], 1)
        self.assertEqual(partial_metrics["diagnostics"]["slot_1_extra"]["gold_count"], 0)


if __name__ == "__main__":
    unittest.main()
