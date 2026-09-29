package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.askStory
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLiveAskTest {
    @Test
    fun nextMatchingStorySlotUsesTheQuestionWrittenByTheServer() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.turn = 3
        d.s.storyNextSlot = "reaction"
        d.s.storyServerQuestion = "그때 너는 어떻게 했어?"
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            val job = launch { d.askSlot("reaction") }
            withTimeout(3_000) { while (!d.s.micEnabled) delay(5) }
            assertEquals("그때 너는 어떻게 했어?", d.s.line)
            delay(40)
            d.send(Reply.Spoke("친구를 불렀어"))
            withTimeout(6_000) { job.join() }
        } finally {
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
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
                d.askStory(Question("어떤 일이 생겼어?", Kind.EASY), "problem") {
                    calls++
                    Server.TurnResult(
                        Server.Verdict("ok", listOf("problem" to "길을 잃었다"), "reaction", null,
                            false, false, null, false, false, false, null),
                        Server.Line("길을 잃었구나", null, "그다음에는 어떻게 했어?"),
                    )
                }
            }
            withTimeout(3_000) { while (!d.s.micEnabled) delay(5) }
            delay(40) // ask() discards input sent before its question has finished speaking
            d.send(Reply.Spoke("길을 잃었어"))
            withTimeout(3_000) { job.join() }
            assertEquals(1, calls)
            assertEquals("길을 잃었다", d.s.slots["problem"])
            assertEquals("reaction", d.s.storyNextSlot)
            assertEquals("그다음에는 어떻게 했어?", d.s.storyServerQuestion)
            assertTrue(d.s.line.contains("길을 잃었구나"))
        } finally {
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
        }
    }
}
