from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.filters.hallucination import check_transcript, strip_tail  # noqa: E402


class HallucinationTest(unittest.TestCase):
    def test_silence_lines_seen_on_09_22_are_dropped(self):
        # 폰 무음 5개가 실제로 낸 문장 그대로
        for line in ["다음 영상에서 만나요.", "다음 영상에서 만나요", "감사합니다.", "아멘", "아멘"]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)
        self.assertEqual(check_transcript("한글자막 by 한효정").reason, "hallucination")
        self.assertEqual(check_transcript("시청해주셔서 감사합니다.").reason, "hallucination")

    def test_lines_seen_on_the_09_29_phone_are_dropped(self):
        # S25+ 조장 테스트에서 서버에 실제로 온 것 — 「보내주셔서…」는 아이 말로 들어갔었다
        for line in ["보내주셔서 감사합니다.", "자막 제공 배달의민족", "감사합니다. 감사합니다."]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)
        for line in ["MBC 뉴스 김철수입니다.", "구독 좋아요 알림 설정", "이 영상은 유료광고를 포함하고 있습니다"]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)

    def test_the_subtitle_notice_from_the_10_01_phone_is_dropped(self):
        # #50 · 민우 S25: 이야기 도중 아이 말 자리에 떴다 — 앱에는 이 문장이 없다
        for line in ["자막은 설정에서 선택할 수 있어요", "자막은 설정에서 선택하실 수 있습니다."]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)

    def test_real_answers_from_the_09_29_phone_are_kept(self):
        # 같은 날 제대로 받아쓴 답 — 목록이 이것들을 먹으면 안 된다
        for line in ["삼촌", "엄마", "긴 생머리", "알록달록", "무서워", "괴물", "다 별로야", "다음에 또 만나요"]:
            self.assertTrue(check_transcript(line).keep, line)

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

    def test_no_hangul_output_is_dropped(self):
        # AI-Hub 아동 4,000클립 중 4건 — 한국어를 강제해도 이런 게 나온다
        self.assertEqual(check_transcript("Eru þín að naf sér síða keug?").reason, "no_hangul")
        self.assertEqual(check_transcript("2, 3, 2, 3").reason, "no_hangul")
        # 숫자가 섞여도 한글이 있으면 아이 말이다
        self.assertTrue(check_transcript("3개 먹었어").keep)

    def test_empty(self):
        self.assertEqual(check_transcript("  ").reason, "empty")
        self.assertEqual(check_transcript("...").reason, "empty")

    def test_story_words_that_once_leaked_are_not_listed(self):
        # 1차 강의실 무음이 「고춧가루」를 냈지만 이야기 재료라 목록에 안 넣었다
        self.assertTrue(check_transcript("고춧가루").keep)

    def test_lines_seen_on_the_10_06_phone_are_dropped(self):
        # #172 · 치영 S10 협업 측정 — 희미한 소리에 whisper 가 낸 것. 셋 다 아이 말로 책 재료 칸에 들어갔었다
        for line in ["이 영상은 한국국토정보공사의 자막을 사용하였습니다.", "김치볶음밥 김치볶음밥 김치볶음밥",
                     "이른바 이른바 이른바 이른바 이른바"]:
            self.assertEqual(check_transcript(line).reason, "hallucination", line)

    def test_a_word_said_twice_is_the_childs(self):
        # 같은 낱말 두 번은 아이가 한다 — 세 번부터 whisper 의 되풀이로 본다. 낱말이 섞이면 몇 번이든 아이 말이다
        for line in ["아니 아니", "엄마 엄마", "싫어 싫어!", "기린이랑 코끼리가 보였어", "엄마랑 갔어", "김치볶음밥 먹었어", "멍멍 멍멍 하고 짖었어"]:
            self.assertTrue(check_transcript(line).keep, line)


if __name__ == "__main__":
    unittest.main()


class TestTail:
    """10-05 phone: an outro glued onto a real answer is cut, the answer stays"""

    def test_an_outro_tail_is_cut(self):
        assert strip_tail("멋있어서 친구가 됐어 구독과 좋아요 알림 설정 부탁드립니다") == "멋있어서 친구가 됐어"
        assert strip_tail("로봇이랑 놀았어. 시청해 주셔서 감사합니다") == "로봇이랑 놀았어"

    def test_real_words_are_never_cut(self):
        for t in ("엄마한테 감사합니다 했어", "엄마한테 감사합니다", "구독", "공룡나라 갈래", "구독과 좋아요"):
            assert strip_tail(t) == t

