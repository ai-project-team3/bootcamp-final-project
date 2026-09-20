from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from eval.run_team_eval import append_results


class AppendResultsTest(unittest.TestCase):
    def test_preserves_existing_results_and_skips_identical_section(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "results.md"
            path.write_text("# existing\n\nandroid measurement sentinel\n", encoding="utf-8")
            section = "## 2026-09-20 · model evaluation\n\n| model | JSON |\n| --- | ---: |\n"

            first = append_results(path, section)
            second = append_results(path, section)
            saved = path.read_text(encoding="utf-8")

            self.assertTrue(first)
            self.assertFalse(second)
            self.assertIn("android measurement sentinel", saved)
            self.assertEqual(saved.count("## 2026-09-20 · model evaluation"), 1)


if __name__ == "__main__":
    unittest.main()
