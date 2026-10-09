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
@Config(sdk = [26])
class ReportQuestionHistoryTest {
    @Test fun questionProvenanceSurvivesTheStoredReportRoundTrip() {
        val original = DemoState().buildSessionReport("book-one").toJson()
        original.put("talk", JSONArray().put(JSONObject()
            .put("who", "child").put("text", "곰이 집에 갔어")
            .put("questionType", "open").put("question", "무슨 일이 있었어?")))
        val restored = sessionReportOf(original).toJson().getJSONArray("talk").getJSONObject(0)
        assertEquals("open", restored.optString("questionType", "unknown"))
        assertEquals("무슨 일이 있었어?", restored.optString("question"))
    }

    @Test fun aRecordedAnswerUsesTheActualQuestionType() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val d = Director(scope)
        try {
            d.setListening(Question("왜 울었을까?", Kind.HARD))
            d.say("왜 울었을까?")
            d.talk("child", "넘어져서 아팠어")
            assertEquals(ReportQuestionType.REASON, d.s.talk.last().questionType)
            d.setListening(Question("그 말이 맞아?", Kind.EASY))
            d.say("그 말이 맞아?")
            d.talk("child", "응")
            assertEquals(ReportQuestionType.CONFIRMATION, d.s.talk.last().questionType)
            d.setListening(Question("무엇을 했어?", Kind.EASY))
            d.say("무엇을 했어?")
            d.talk("card", "숨바꼭질")
            assertEquals(ReportQuestionType.CHOICE, d.s.talk.last().questionType)
            d.setListening(Question("왜 울었을까?", Kind.HARD))
            d.say("아파서 울었을까, 속상해서 울었을까? 아니면 다른 이유였어?")
            d.talk("child", "아파서")
            assertEquals(ReportQuestionType.CHOICE, d.s.talk.last().questionType)
            d.setListening(null)
            d.talk("child", "그냥 한 말")
            assertEquals(ReportQuestionType.UNKNOWN, d.s.talk.last().questionType)
        } finally { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
    @Test fun completedBooksKeepTypedAnswersAcrossReportReloads() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = ctx.getSharedPreferences("answer_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionReports.attach(ctx)
        SessionReports.clear()
        try {
            val state = DemoState()
            state.talk += TalkLine("child", "곰이 집에 갔어", questionType = ReportQuestionType.OPEN)
            state.talk += TalkLine("card", "숲", questionType = ReportQuestionType.CHOICE)
            SessionReports.keep(state, "one")
            SessionReports.keep(state, "one")
            SessionReports.keep(state, "two")
            SessionReports.reload()
            val sessions = JSONArray(prefs.getString("sessions", "[]"))
            assertEquals("One entry per book, not one per save", 2, sessions.length())
            val answers = sessions.getJSONObject(0).getJSONArray("answers")
            assertEquals("open", answers.getJSONObject(0).getString("questionType"))
            assertEquals("card", answers.getJSONObject(1).getString("who"))
        } finally { SessionReports.clear(); prefs.edit().clear().commit() }
    }
    @Test fun unknownLegacyRecordsAreNotReclassifiedAfterLoading() {
        val json = DemoState().buildSessionReport("old").toJson()
        json.put("talk", JSONArray().put(JSONObject().put("who", "child").put("text", "길게 말했던 답")))
        assertEquals(ReportQuestionType.UNKNOWN, sessionReportOf(json).talk.single().questionType)
    }

    @Test fun aDifferentChildCannotInheritThePausedBooksAnswers() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        SessionReports.attach(context)
        SessionReports.clear()
        try {
            val old = DemoState()
            old.talk += TalkLine("child", "곰이 집에 갔어", questionType = ReportQuestionType.OPEN)
            SessionReports.keep(old, "old")
            assertEquals(1, AnswerHistory.languageAnswers().size)
            assertTrue(AnswerHistory.startNewChild())
            SessionReports.keep(old, "paused-old")
            assertTrue(AnswerHistory.languageAnswers().isEmpty())
            val fresh = DemoState()
            fresh.talk += TalkLine("child", "맞아", questionType = ReportQuestionType.CONFIRMATION)
            fresh.talk += TalkLine("child", "응", questionType = ReportQuestionType.UNKNOWN)
            fresh.talk += TalkLine("mascot", "곰이 집에 갔어", questionType = ReportQuestionType.OPEN)
            fresh.talk += TalkLine("child", "집이 좋아서", questionType = ReportQuestionType.REASON)
            fresh.talk += TalkLine("card", "숲", questionType = ReportQuestionType.CHOICE)
            SessionReports.keep(fresh, "new")
            AnswerHistory.reload()
            assertEquals(listOf("집이 좋아서"), AnswerHistory.languageAnswers().map { it.text })
            assertEquals(listOf("숲"), AnswerHistory.choiceAnswers().map { it.text })
            assertNotNull("Old books and reports are preserved", SessionReports.of("old"))
        } finally { SessionReports.clear() }
    }
    @Test fun undoAndRedoRestoreTheAnswersUsedByTheReport() {
        val s = DemoState()
        val history = TurnHistory(s)
        s.talk += TalkLine("child", "숲에 갔어")
        history.before()
        s.talk += TalkLine("child", "곰을 만났어")
        history.done()
        assertTrue(history.undo())
        assertEquals(listOf("숲에 갔어"), s.talk.map { it.text })
        assertTrue(history.redo())
        assertEquals(listOf("숲에 갔어", "곰을 만났어"), s.talk.map { it.text })
    }
}