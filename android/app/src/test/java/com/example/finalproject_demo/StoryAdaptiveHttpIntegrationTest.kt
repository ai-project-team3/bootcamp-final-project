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

/** Synthetic HTTP fixtures verify app wiring, not model accuracy or child outcomes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryAdaptiveHttpIntegrationTest {
    private var previousBase: String? = null
    private var previousModes: Set<StoryMode> = emptySet()
    private val newStoryAnswer = "놀이터에 갔어"

    @Before fun prepare() {
        previousBase = Server.base
        previousModes = Server.liveModes
        Server.base = null
        Server.liveModes = emptySet()
        SessionReports.attach(RuntimeEnvironment.getApplication())
        SessionReports.clear()
        AnswerHistory.setChildAge(ChildAge(4, 1))
    }

    @After fun clean() {
        SessionReports.clear()
        Server.base = previousBase
        Server.liveModes = previousModes
    }

    @Test fun httpCauseAndLinkingReachTheNextRequestAndReloadedStory() = runBlocking {
        exercise(
            text = "추웠어. 그래서 집에 갔어",
            serverCause = true,
            expectedLinking = true,
            expectedLevel = Level.REASON,
            expectedRequests = listOf("chain", "chain", "reason"),
            expectedWords = 4,
        )
    }

    @Test fun linkingWithoutServerCauseNeverPromotesTheNextRequestOrReloadedStory() = runBlocking {
        exercise(
            text = "추웠어. 그래서 집에 갔어",
            serverCause = false,
            expectedLinking = true,
            expectedLevel = Level.CHAIN,
            expectedRequests = listOf("chain", "chain", "chain"),
            expectedWords = 4,
        )
    }

    @Test fun shortDirectAnswersLowerTheNextRequestAndReloadedStory() = runBlocking {
        exercise(
            text = "도서관",
            serverCause = false,
            expectedLinking = false,
            expectedLevel = Level.PICK,
            expectedRequests = listOf("chain", "chain", "pick"),
            expectedWords = 1,
        )
    }

    private suspend fun exercise(
        text: String,
        serverCause: Boolean,
        expectedLinking: Boolean,
        expectedLevel: Level,
        expectedRequests: List<String>,
        expectedWords: Int,
    ) = coroutineScope {
        val server = StoryTestServer { _, body ->
            val startingNewStory = body.optString("utterance") == newStoryAnswer
            JSONObject().put("judge", JSONObject()
                .put("reason", "ok")
                .put("slot_1", JSONObject.NULL).put("value_1", JSONObject.NULL)
                .put("slot_2", JSONObject.NULL).put("value_2", JSONObject.NULL)
                .put("next_slot", JSONObject.NULL).put("no_longer_needed", JSONObject.NULL)
                .put("story_ready", startingNewStory)
                .put("unclear", false).put("unclear_of", JSONObject.NULL)
                .put("contradiction", false)
                .put("s1_reason", !startingNewStory && serverCause)
                .put("s2_addition", false)
                .put("emotion", JSONObject.NULL))
                .put("line", JSONObject.NULL)
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.level = Level.CHAIN
        d.s.templateKey = "E"
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            // The comparison is history absent versus history restored for the same profile.
            assertEquals("chain", firstStoryRequest(server).getString("level"))
            val beforeAnswers = turnRequests(server).size
            repeat(3) { index ->
                val reply = speak(d, text)
                assertEquals("The HTTP result must keep the original answer", text, reply.text)
                assertEquals(serverCause, reply.answer!!.reason)
                assertEquals(expectedLinking, reply.answer!!.con)
                assertTrue("This fixture supplies no S2 structure", reply.answer!!.el.isEmpty())
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, reply)
                if (index == 0) assertEquals("One signal must not change level", Level.CHAIN, d.s.level)
                else assertEquals(expectedLevel, d.s.level)
            }
            val answerRequests = turnRequests(server).drop(beforeAnswers)
            assertEquals("One HTTP turn per direct answer, without a second judging call", 3, answerRequests.size)
            assertEquals(expectedRequests, answerRequests.map { it.getString("level") })
            assertEquals(listOf(text, text, text), answerRequests.map { it.getString("utterance") })

            SessionReports.keep(d.s, "synthetic-http-book")
            AnswerHistory.reload()
            val saved = AnswerHistory.all().single().answers
            assertEquals(3, saved.size)
            saved.forEach { answer ->
                assertEquals("child", answer.who)
                assertEquals(ReportQuestionType.OPEN, answer.questionType)
                assertEquals(text, answer.text)
                assertEquals(LanguageEvidence(serverCause, emptySet(), expectedLinking, expectedWords), answer.languageEvidence)
            }

            val restored = firstStoryRequest(server)
            assertEquals(expectedRequests.last(), restored.getString("level"))
            assertFalse("Previous book text must stay local", restored.toString().contains(text))
            assertFalse(restored.has("history"))
            assertEquals("Two story-entry probes and three direct answers", 5, turnRequests(server).size)
            assertFalse("Adaptive rules must not add a separate model request", server.requests.any { it.first == "/judge" })
            turnRequests(server).forEach { request ->
                assertFalse("Saved signal facts must stay local", request.toString().contains("languageEvidence"))
                assertFalse("Optional child age must stay local", request.toString().contains("child_age"))
                assertFalse(request.has("age"))
                assertFalse(request.has("years"))
                assertFalse(request.has("half"))
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            server.close()
        }
    }

    private fun turnRequests(server: StoryTestServer) = server.requests.filter { it.first == "/turn" }.map { it.second }

    private suspend fun speak(d: Director, text: String): Reply.Spoke = coroutineScope {
        val job = async { d.askStory(Question("어떤 일이 있었어?", Kind.EASY), "cause") }
        // ask() clears queued input after the mascot speaks; feed until it starts listening.
        val feeder = launch {
            while (job.isActive) { d.send(Reply.Spoke(text)); delay(40) }
        }
        try {
            withTimeout(8_000) { job.await() } as Reply.Spoke
        } finally {
            feeder.cancelAndJoin()
            job.cancelAndJoin()
        }
    }

    private suspend fun firstStoryRequest(server: StoryTestServer): JSONObject = coroutineScope {
        val previousCount = turnRequests(server).size
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.go(Scene.PLACE)
        val feeder = launch {
            while (isActive) {
                if (d.s.micEnabled) d.send(Reply.Spoke(newStoryAnswer))
                delay(40)
            }
        }
        try {
            withTimeout(8_000) {
                while (turnRequests(server).size == previousCount) delay(10)
                turnRequests(server)[previousCount]
            }
        } finally {
            feeder.cancelAndJoin()
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
