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
            .put("questionType", "open")))
        val restored = sessionReportOf(original).toJson().getJSONArray("talk").getJSONObject(0)
        assertEquals("open", restored.optString("questionType", "unknown"))
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