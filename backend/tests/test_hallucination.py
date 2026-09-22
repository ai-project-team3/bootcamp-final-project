from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.filters.hallucination import check_transcript  # noqa: E402


class HallucinationTest(unittest.TestCase):
    def test_silence_lines_seen_on_09_22_are_dropped(self):
        # 폰 무음 5개가 실제로 낸 문장 그대로
        for line in ["다음 영상에서 만나요.", "다음 영상에서 만나요", "감사합니다.", "아멘", "아멘"]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)
        self.assertEqual(check_transcript("한글자막 by 한효정").reason, "hallucination")
        self.assertEqual(check_transcript("시청해주셔서 감사합니다.").reason, "hallucination")

    def test_short_real_answers_are_kept(self):
        for line in ["또", "응.", "음", "몰라.", "싫어!", "아니"]:
            self.assertTrue(check_transcript(line).keep, line)

    def test_quiet_mic_transcripts_are_kept(self):
        # 이어폰 마이크 녹음 — Silero 가 말소리 0초로 본 클립인데 whisper 는 말을 받아썼다.
        # 길이 검사를 넣었다가 이것들을 버려서 뺐다 (09-22)
        for line in ["하닷속 갈래", "공격나라 갈래?", "동영나라 갈래"]:
            self.assertTrue(check_transcript(line).keep, line)

    def test_only_the_whole_transcript_counts(self):
        # 헛문장이 **안에 들어 있을 뿐**이면 아이 말이다
        self.assertTrue(check_transcript("감사합니다 하고 인사했어").keep)
        self.assertTrue(check_transcript("아멘 하고 기도했어").keep)

    def test_empty(self):
        self.assertEqual(check_transcript("  ").reason, "empty")
        self.assertEqual(check_transcript("...").reason, "empty")

    def test_story_words_that_once_leaked_are_not_listed(self):
        # 1차 강의실 무음이 「고춧가루」를 냈지만 이야기 재료라 목록에 안 넣었다
        self.assertTrue(check_transcript("고춧가루").keep)


if __name__ == "__main__":
    unittest.main()
