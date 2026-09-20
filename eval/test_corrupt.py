from __future__ import annotations

import unittest
from pathlib import Path

from eval.corrupt import evaluate_presets, load_android_theme_labels


ROOT = Path(__file__).resolve().parent.parent
MODEL = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "example" / "finalproject_demo" / "demo" / "Model.kt"


class CorruptAppPresetTest(unittest.TestCase):
    def test_loads_theme_labels_from_android_source(self) -> None:
        self.assertEqual(load_android_theme_labels(MODEL), ["우주", "바닷속", "공룡 나라"])

    def test_actual_preset_experiment_is_deterministic(self) -> None:
        presets = load_android_theme_labels(MODEL)
        rows_a, summary_a = evaluate_presets(presets)
        rows_b, summary_b = evaluate_presets(presets)
        self.assertEqual(rows_a, rows_b)
        self.assertEqual(summary_a, summary_b)


if __name__ == "__main__":
    unittest.main()
