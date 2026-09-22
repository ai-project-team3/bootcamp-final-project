from __future__ import annotations

import unittest

from eval.stt_bias_bench import build_manifest, judge, matched_preset

PRESETS = ["우주", "바닷속", "공룡 나라"]


def row(group, ref, hyp, read=""):
    return {"group": group, "ref": ref, "hyp": hyp, "read": read}


class MatchedPresetTest(unittest.TestCase):
    def test_exact_and_spacing(self):
        self.assertEqual(matched_preset("공룡나라 갈래", PRESETS), "공룡 나라")
        self.assertEqual(matched_preset("우주로 가자", PRESETS), "우주")

    def test_phoneme_swap_is_recovered(self):
        # 음소 치환은 같은 낱말로 본다 — 자모 매칭이 원래 풀던 것
        self.assertEqual(matched_preset("곰뇽 나라 갈래", PRESETS), "공룡 나라")

    def test_dropped_word_is_not_a_hit(self):
        # 09-19 의 실패 모양. 늘 하나를 고르는 nearest 매칭이면 여기서 셋 중 하나가 뽑힌다
        self.assertIsNone(matched_preset("갈래", PRESETS))
        self.assertIsNone(matched_preset("", PRESETS))

    def test_short_answers_are_not_presets(self):
        for w in ["응", "몰라", "싫어", "또", "아니"]:
            self.assertIsNone(matched_preset(w, PRESETS), w)


class JudgeTest(unittest.TestCase):
    def test_outside_pulled_into_preset_fails(self):
        self.assertTrue(judge(row("outside", "눈 나라", "눈 나라 갈래"), PRESETS)["ok"])
        r = judge(row("outside", "눈 나라", "우주 나라 갈래"), PRESETS)
        self.assertFalse(r["ok"]); self.assertTrue(r["pulled"])

    def test_bias_leaking_into_short_answer_counts(self):
        r = judge(row("short", "몰라", "공룡 나라"), PRESETS)
        self.assertFalse(r["ok"]); self.assertTrue(r["pulled"])
        self.assertTrue(judge(row("short", "몰라", "몰라."), PRESETS)["ok"])

    def test_silence_must_stay_empty(self):
        self.assertTrue(judge(row("silence", "", ""), PRESETS)["ok"])
        self.assertFalse(judge(row("silence", "", "우주, 바닷속, 공룡 나라"), PRESETS)["ok"])


class ManifestTest(unittest.TestCase):
    def test_counts_and_stable_order(self):
        a, b = build_manifest(PRESETS), build_manifest(PRESETS)
        self.assertEqual([r["clip_id"] for r in a], [r["clip_id"] for r in b])
        by = {g: sum(r["group"] == g for r in a) for g in ("preset", "outside", "short", "silence")}
        self.assertEqual(by, {"preset": 24, "outside": 5, "short": 25, "silence": 5})
        self.assertEqual(len({r["clip_id"] for r in a}), len(a))


if __name__ == "__main__":
    unittest.main()
