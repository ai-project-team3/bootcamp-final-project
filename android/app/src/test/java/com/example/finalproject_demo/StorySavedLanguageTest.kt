package com.example.finalproject_demo

import android.content.Context
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorySavedLanguageTest {
    private var previousBase: String? = null
    private var previousModes: Set<StoryMode> = emptySet()
    @Before fun prepare() {
        previousBase = Server.base; previousModes = Server.liveModes
        Server.base = null; Server.liveModes = emptySet()
        SessionReports.attach(RuntimeEnvironment.getApplication()); SessionReports.clear()
    }
    @After fun clean() { SessionReports.clear(); Server.base = previousBase; Server.liveModes = previousModes }

    private fun saveAnswer(text: String, reason: Boolean, connected: Boolean): org.json.JSONObject {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        try {
            d.setListening(Question("왜 집에 갔어?", Kind.HARD))
            d.say("왜 집에 갔어?")
            d.talk("child", text)
            d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                Reply.Spoke(text, answer = Answer(text, reason = reason, con = connected)))
            SessionReports.keep(d.s, "book")
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("answer_history", Context.MODE_PRIVATE)
            return JSONArray(prefs.getString("sessions", "[]")).getJSONObject(0).getJSONArray("answers").getJSONObject(0)
        } finally { scope.cancel() }
    }

    @Test fun measuredSignalsAreSavedWithTheirOriginalTypedAnswer() {
        val line = saveAnswer("추웠어. 그래서 집에 갔어", true, true)
        assertTrue("Actual question-rule evidence must survive restart", line.has("languageEvidence"))
        val evidence = line.getJSONObject("languageEvidence")
        assertTrue(evidence.getBoolean("s1"))
        assertTrue(evidence.getBoolean("a1"))
        assertEquals(4, evidence.getInt("words"))
        assertEquals("reason", line.getString("questionType"))
        assertEquals("추웠어. 그래서 집에 갔어", line.getString("text"))
        assertFalse(evidence.has("level"))
        AnswerHistory.reload()
        assertEquals("추웠어. 그래서 집에 갔어", AnswerHistory.languageAnswers().single().text)
    }

    @Test fun reportOnlyReasonTagsDoNotBecomeMeasuredS1() {
        val line = saveAnswer("밤이라서 집에 갔어", false, false)
        assertTrue(SessionReports.of("book")!!.talk.last().tags.contains(MOMENT_REASON))
        assertTrue(line.has("languageEvidence"))
        assertFalse(line.getJSONObject("languageEvidence").getBoolean("s1"))
    }
}
