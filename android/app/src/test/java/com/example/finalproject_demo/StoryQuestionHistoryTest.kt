package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
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
class StoryQuestionHistoryTest {
    private var previousBase: String? = null
    private var previousModes: Set<StoryMode> = emptySet()
    @Before fun prepare() {
        previousBase = Server.base; previousModes = Server.liveModes
        Server.base = null; Server.liveModes = emptySet()
        SessionReports.attach(RuntimeEnvironment.getApplication()); SessionReports.clear()
    }
    @After fun clean() { SessionReports.clear(); Server.base = previousBase; Server.liveModes = previousModes }

    private fun saveMeasuredBook(book: String = "earlier", withChoiceBetween: Boolean = false, withEmotionBetween: Boolean = false) {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        try {
            repeat(2) { index ->
                if (index == 1 && withEmotionBetween) {
                    d.setListening(Question("어떤 마음이었어?", Kind.EASY))
                    d.say("어떤 마음이었어?")
                    d.talk("child", "속상했어")
                    d.judge(BANK.first { it.emotion && !it.counts }, Reply.Spoke("속상했어", answer = Answer("속상했어")))
                }
                if (index == 1 && withChoiceBetween) {
                    d.setListening(Question("공룡이야, 토끼야?", Kind.CHOICE))
                    d.say("공룡이야, 토끼야?")
                    d.talk("child", "공룡")
                    d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, Reply.Spoke("공룡", answer = Answer("공룡")))
                }
                d.setListening(Question("왜 집에 갔어?", Kind.HARD))
                d.say("왜 집에 갔어?")
                d.talk("child", "추웠어. 그래서 집에 갔어")
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                    Reply.Spoke("추웠어. 그래서 집에 갔어", answer = Answer("추웠어. 그래서 집에 갔어", reason = true, con = true)))
            }
            SessionReports.keep(d.s, book)
        } finally { scope.cancel() }
    }

    private suspend fun firstStoryRequest(): JSONObject = coroutineScope {
        val oldBase = Server.base
        val oldModes = Server.liveModes
        val server = StoryTestServer { _, _ -> JSONObject().put("judge", JSONObject().put("reason", "ok").put("story_ready", true)) }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = server.base; Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.PLACE)
            val feeder = launch {
                while (isActive) { if (d.s.micEnabled) d.send(Reply.Spoke("놀이터에 갔어")); delay(40) }
            }
            val request = withTimeout(8_000) {
                while (server.requests.none { it.first == "/turn" }) delay(10)
                server.requests.first { it.first == "/turn" }.second
            }
            feeder.cancelAndJoin()
            assertFalse("Saved transcripts stay on the phone", request.toString().contains("추웠어"))
            assertFalse(request.has("languageEvidence"))
            request
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); Server.base = oldBase; Server.liveModes = oldModes; server.close() }
    }

    @Test fun actualStoryEntryRecomputesQuestionStyleFromReloadedSignalFacts() = runBlocking {
        saveMeasuredBook()
        AnswerHistory.reload()
        assertEquals("reason", firstStoryRequest().getString("level"))
    }

    @Test fun unknownLegacyTagsAreNotTreatedAsMeasuredLanguageEvidence() = runBlocking {
        val s = DemoState()
        repeat(2) { s.talk += TalkLine("child", "추웠어. 그래서 집에 갔어", tags = listOf(MOMENT_REASON), questionType = ReportQuestionType.REASON) }
        SessionReports.keep(s, "legacy")
        AnswerHistory.reload()
        assertEquals("chain", firstStoryRequest().getString("level"))
    }

    @Test fun choosingInBetweenBreaksAConsecutiveLanguageSignalStreak() = runBlocking {
        saveMeasuredBook(withChoiceBetween = true)
        assertEquals("chain", firstStoryRequest().getString("level"))
    }

    @Test fun anExplicitlyNonCountingQuestionIsSkippedAsInTheLiveRule() = runBlocking {
        saveMeasuredBook(withEmotionBetween = true)
        AnswerHistory.reload()
        assertEquals("reason", firstStoryRequest().getString("level"))
    }

    @Test fun deletingTheBookAndChangingTheChildRemoveTheirQuestionStyleEvidence() = runBlocking {
        saveMeasuredBook()
        SessionReports.forget("earlier")
        assertEquals("chain", firstStoryRequest().getString("level"))
        saveMeasuredBook("second")
        assertTrue(AnswerHistory.startNewChild())
        assertEquals("chain", firstStoryRequest().getString("level"))
    }
}
