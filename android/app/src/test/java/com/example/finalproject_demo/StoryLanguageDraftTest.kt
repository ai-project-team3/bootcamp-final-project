package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLanguageDraftTest {
    @Before fun prepare() {
        SessionReports.attach(RuntimeEnvironment.getApplication())
        SessionReports.clear()
    }
    @After fun clean() { SessionReports.clear() }

    private fun roundTrip(draft: SessionDraft) = DemoState().apply {
        applyDraft(SessionDraft.fromJson(JSONObject(draft.toJson().toString()))!!)
    }

    @Test fun resumedAnswersKeepMeasuredSignalsAndExplicitNonCountingQuestions() {
        val strong = TalkLine("child", "추웠어. 그래서 집에 갔어", questionType = ReportQuestionType.REASON,
            languageEvidence = LanguageEvidence(true, setOf("결과"), true, 4))
        val state = DemoState().apply {
            mode = StoryMode.STORY
            storyStartedAtMs = 1
            talk += strong
            talk += TalkLine("child", "좋았어", questionType = ReportQuestionType.OPEN,
                languageEvidence = LanguageEvidence(false, emptySet(), false, 1, counted = false))
            talk += strong
            notes += TurnNote("고를까?", "곰", "voice", false, emptySet(), false, 1, languageEligible = false)
        }
        val restored = roundTrip(state.captureDraft(Scene.EVENT))
        assertEquals(state.talk.toList(), restored.talk.toList())
        assertEquals(state.notes.toList(), restored.notes.toList())
        assertEquals(Level.REASON, questionStyleForHistory(listOf(AnswerSession("saved", "", "story", restored.talk.toList()))))
        SessionReports.keep(restored, "same-child")
        assertEquals(Level.REASON, questionStyleForHistory(AnswerHistory.all()))
    }

    @Test fun olderDraftsKeepUnknownEvidenceAndLegacyNoteCompatibility() {
        val state = DemoState().apply {
            mode = StoryMode.STORY
            talk += TalkLine("child", "추웠어", listOf("reason"), ReportQuestionType.REASON)
            notes += TurnNote("왜?", "추웠어", "voice", true, emptySet(), false, 1)
        }
        val draft = state.captureDraft(Scene.EVENT)
        draft.state.getJSONArray("talk").getJSONObject(0).remove("languageEvidence")
        draft.state.getJSONArray("notes").getJSONObject(0).remove("languageEligible")
        val restored = roundTrip(draft)
        assertNull(restored.talk.single().languageEvidence)
        assertTrue(restored.notes.single().languageEligible)
        assertNull(questionStyleForHistory(listOf(AnswerSession("old", "", "story", restored.talk.toList()))))
    }

    @Test fun changingChildrenBeforeRestoringDoesNotAssignThePreviousStoryToTheNewChild() {
        val original = DemoState().apply {
            mode = StoryMode.STORY
            storyStartedAtMs = 1
            talk += TalkLine("child", "추웠어. 그래서 집에 갔어", questionType = ReportQuestionType.REASON,
                languageEvidence = LanguageEvidence(true, emptySet(), true, 4))
        }
        val draft = original.captureDraft(Scene.EVENT)
        assertTrue(AnswerHistory.startNewChild())
        assertNotEquals(original.reportProfileId, AnswerHistory.profileId)
        val restored = roundTrip(draft)
        assertEquals(original.reportProfileId, restored.reportProfileId)
        SessionReports.keep(restored, "previous-child")
        assertTrue(AnswerHistory.all().isEmpty())
        assertNotNull(SessionReports.of("previous-child"))
    }

    @Test fun aLegacyDraftWithoutAnOwnerIsNotAssignedToTheCurrentChild() {
        val draft = DemoState().apply {
            talk += TalkLine("child", "집에 갔어", questionType = ReportQuestionType.OPEN)
        }.captureDraft(Scene.EVENT)
        draft.state.remove("reportProfileId")
        val restored = roundTrip(draft)
        assertNotEquals(AnswerHistory.profileId, restored.reportProfileId)
        SessionReports.keep(restored, "unknown-child")
        assertTrue(AnswerHistory.all().isEmpty())
    }
}
