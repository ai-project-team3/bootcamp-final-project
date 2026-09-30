package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.askStory
import com.example.finalproject_demo.demo.judge
import com.example.finalproject_demo.demo.pick
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLiveAskTest {
    @Test
    fun connectionRetryAndBlockedAnswerDoNotBecomeBookMaterial() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        var calls = 0
        var retryShown = false
        try {
            val job = launch {
                val reply = d.askStory(Question("어떤 일이 생겼어?", Kind.EASY), "problem") {
                    calls++
                    when (calls) {
                        1 -> null
                        2 -> Server.TurnResult(Server.Verdict("blocked_by_filter", emptyList(), null, null,
                            false, false, null, false, false, false, null), null)
                        else -> Server.TurnResult(Server.Verdict("ok", listOf("problem" to "길을 잃었다"), "reaction", null,
                            false, false, null, false, false, false, null), null)
                    }
                }
                assertEquals("안전한 새 이야기", (reply as Reply.Spoke).text)
            }
            val feeder = launch {
                while (job.isActive) {
                    if (d.s.stage is Stage.Confirm) {
                        retryShown = true; d.send(Reply.Tapped("ok", "다시 연결"))
                    } else d.send(Reply.Spoke(if (calls < 2) "검사에서 거절될 이야기" else "안전한 새 이야기"))
                    delay(40)
                }
            }
            withTimeout(8_000) { job.join() }
            feeder.cancelAndJoin()
            assertTrue(retryShown)
            assertEquals(3, calls)
            assertEquals("길을 잃었다", d.s.slots["problem"])
            assertFalse(d.s.slots.values.any { "거절될" in it })
        } finally { Server.liveModes = emptySet(); Server.base = null; scope.cancel() }
    }
    private suspend fun answerAfterVoice(job: Job, d: Director, answer: String) {
        // The mic button appears before the mascot finishes speaking. Keep offering the
        // answer until ask() starts listening instead of relying on a fixed TTS delay.
        while (job.isActive) {
            d.send(Reply.Spoke(answer))
            delay(100)
        }
    }

    @Test
    fun chosenAndMascotAnswersReachTheServerWithoutBecomingChildSpeech() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            for (mascot in listOf(false, true)) {
                var sent: Server.Turn? = null
                val job = launch {
                    val reply = d.askStory(Question("어디로 갈까?", Kind.EASY), "place") {
                        sent = it
                        Server.TurnResult(Server.Verdict("ok", listOf("place" to "숲"), "problem", null,
                            false, false, null, false, true, true, null), null)
                    }
                    assertTrue(reply is Reply.Tapped)
                }
                val answer = launch {
                    while (job.isActive) { d.send(Reply.Tapped("forest", "숲", byMascot = mascot)); delay(40) }
                }
                withTimeout(8_000) { job.join() }
                answer.cancelAndJoin()
                assertEquals("숲", sent?.utterance)
                assertEquals(if (mascot) "mascot" else "card", d.s.slotBy["place"])
                assertEquals("problem", d.s.storyNextSlot)
            }
        } finally {
            Server.liveModes = emptySet(); Server.base = null; scope.cancel()
        }
    }

    @Test
    fun nextMatchingStorySlotUsesTheQuestionWrittenByTheServer() = runBlocking {
        val server = StoryTestServer { _, _ -> JSONObject().put("judge", JSONObject().put("reason", "ok")) }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.turn = 3
        d.s.storyNextSlot = "reaction"
        d.s.storyServerQuestion = "그때 너는 어떻게 했어?"
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            val job = launch { d.askSlot("reaction") }
            withTimeout(3_000) { while (!d.s.micEnabled) delay(5) }
            assertEquals("그때 너는 어떻게 했어?", d.s.line)
            val answer = launch { answerAfterVoice(job, d, "친구를 불렀어") }
            withTimeout(8_000) { job.join() }
            answer.cancel()
        } finally {
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
            server.close()
        }
    }

    @Test
    fun aSpokenStoryAnswerCallsTurnAndKeepsTheServerQuestion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        var calls = 0
        try {
            val job = launch {
                val reply = d.askStory(Question("어떤 일이 생겼어?", Kind.EASY), "problem") {
                    calls++
                    Server.TurnResult(
                        Server.Verdict("ok", listOf("problem" to "길을 잃었다"), "reaction", null,
                            false, false, null, false, true, false, null),
                        Server.Line("길을 잃었구나", null, "그다음에는 어떻게 했어?"),
                    )
                }
                d.judge(d.s.pick("reaction"), reply, "어떤 일이 생겼어?")
            }
            withTimeout(3_000) { while (!d.s.micEnabled) delay(5) }
            val answer = launch { answerAfterVoice(job, d, "길을 잃었어") }
            withTimeout(8_000) { job.join() }
            answer.cancel()
            assertEquals(1, calls)
            assertEquals("길을 잃었다", d.s.slots["problem"])
            assertEquals("reaction", d.s.storyNextSlot)
            assertEquals("그다음에는 어떻게 했어?", d.s.storyServerQuestion)
            assertTrue(d.s.line.contains("길을 잃었구나"))
            assertEquals("길을 잃었어", d.s.notes.single().a)
            assertTrue(d.s.notes.single().s1)
        } finally {
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
        }
    }
}
