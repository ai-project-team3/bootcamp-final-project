from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from eval.forbidden import find_forbidden_hits, load_story_policy
from eval.score import score_stories


ROOT = Path(__file__).resolve().parent.parent
GUIDELINE = ROOT / "guidelines" / "8_금칙어.md"


class ForbiddenPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy = load_story_policy(GUIDELINE)

    def test_loads_concrete_terms_from_canonical_guideline(self) -> None:
        self.assertIn("공룡", self.policy.allowed)
        for term in ("씨발", "죽여", "협박", "도박", "디즈니"):
            self.assertIn(term, self.policy.forbidden)

    def test_allowed_story_conflict_words_are_not_blocked(self) -> None:
        text = "공룡 괴물이 무서워서 친구와 싸웠다가 울었어요."
        self.assertEqual(find_forbidden_hits(text, self.policy), [])

    def test_word_boundaries_prevent_substring_false_positive(self) -> None:
        self.assertEqual(find_forbidden_hits("새로운 이야기의 시발점이에요.", self.policy), [])
        self.assertTrue(find_forbidden_hits("정말 시발! 하고 외쳤어요.", self.policy))

    def test_jamo_distance_catches_one_edit_stt_variant(self) -> None:
        hits = find_forbidden_hits("아이가 씨바 하고 말했어요.", self.policy)
        self.assertTrue(any(hit.term == "씨발" for hit in hits))

    def test_brand_names_from_guideline_are_blocked(self) -> None:
        hits = find_forbidden_hits("디즈니 성에 갔어요.", self.policy)
        self.assertTrue(any(hit.term == "디즈니" for hit in hits))

    def test_story_schema_caption_is_scored(self) -> None:
        scenes = [
            {"index": 1, "caption": "{주인공}과 {친구1}가 공룡을 만났어요.", "keywords": "dinosaur"},
            {"index": 2, "caption": "새로운 이야기의 시발점이에요.", "keywords": "start"},
            {"index": 3, "caption": "괴물이 무서워서 울었어요.", "keywords": "monster"},
            {"index": 4, "caption": "디즈니 성이 보였어요.", "keywords": "castle"},
            {"index": 5, "caption": "친구와 함께 걸었어요.", "keywords": "friends"},
            {"index": 6, "caption": "안전하게 집에 왔어요.", "keywords": "home"},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            fixtures = Path(tmp) / "stories.jsonl"
            fixtures.write_text(
                json.dumps({"id": "s1", "scenes": scenes}, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            result = score_stories(fixtures=fixtures, forbidden_words_path=GUIDELINE)

        self.assertEqual(result["forbidden_count"], 1)
        self.assertEqual(result["forbidden_hits"][0]["word"], "디즈니")
        self.assertEqual(result["placeholder_integrity_rate"], 1.0)


if __name__ == "__main__":
    unittest.main()
