package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryQuestionPolicyTest {
    private fun evidenceJson() = JSONObject().put("s1", true).put("a1", true).put("words", 4).put("elements", JSONArray())

    @Test fun malformedSignalDataRemainsUnknownInsteadOfLookingLikeShortSpeech() {
        assertNull(languageEvidenceOf(JSONObject()))
        assertNull(languageEvidenceOf(evidenceJson().put("words", "4")))
        assertNull(languageEvidenceOf(evidenceJson().put("words", -1)))
        assertNull(languageEvidenceOf(evidenceJson().put("s1", "true")))
        assertNull(languageEvidenceOf(evidenceJson().put("counted", "false")))
        assertNull(languageEvidenceOf(evidenceJson().put("elements", JSONArray().put(4))))
        assertTrue(languageEvidenceOf(evidenceJson())!!.counted)
        assertFalse(languageEvidenceOf(evidenceJson().put("counted", false))!!.counted)
    }

    @Test fun onlyTheLastFiftyMeasuredStoryAnswersSetTheStartingStyle() {
        val strong = TalkLine("child", "추웠어. 그래서 집에 갔어", questionType = ReportQuestionType.REASON,
            languageEvidence = LanguageEvidence(true, emptySet(), true, 4))
        val short = TalkLine("child", "집", questionType = ReportQuestionType.OPEN,
            languageEvidence = LanguageEvidence(false, emptySet(), false, 1))
        val history = listOf(AnswerSession("old", "2026-10-11", "story", List(2) { strong }),
            AnswerSession("new", "2026-10-12", "story", List(50) { short }))
        assertEquals(Level.PICK, questionStyleForHistory(history))
    }

    @Test fun anotherModeOtherSpeakersAndOneAnswerCannotSupplyAnInitialStage() {
        val strong = TalkLine("child", "추웠어. 그래서 집에 갔어", questionType = ReportQuestionType.REASON,
            languageEvidence = LanguageEvidence(true, emptySet(), true, 4))
        assertNull(questionStyleForHistory(listOf(AnswerSession("one", "", "story", listOf(strong)))))
        assertNull(questionStyleForHistory(listOf(AnswerSession("diary", "", "diary", List(2) { strong }))))
        assertNull(questionStyleForHistory(listOf(AnswerSession("adult", "", "story", List(2) { strong.copy(who = "adult") }))))
        assertNull(questionStyleForHistory(listOf(AnswerSession("choice", "", "story", List(2) { strong.copy(questionType = ReportQuestionType.CHOICE) }))))
    }

    @Test fun resumingAnInProgressStoryDoesNotApplyHistoryAgain() {
        val state = DemoState().also { it.level = Level.PICK; it.storyStartedAtMs = 1; it.s1streak = 1 }
        state.initializeStoryQuestionStyle()
        assertEquals(Level.PICK, state.level)
        assertEquals(1, state.s1streak)
    }

    @Test fun pastCardOnlySessionsCannotSetTheNextStoryLanguageLevel() {
        val card = TalkLine("card", "숲", questionType = ReportQuestionType.CHOICE)
        assertNull(questionStyleForHistory(listOf(AnswerSession("cards", "", "story", List(4) { card }))))
    }
}
