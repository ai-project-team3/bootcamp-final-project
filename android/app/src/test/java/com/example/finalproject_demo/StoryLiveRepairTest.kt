package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.SORRY_LINE
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.askStory
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The live story asks the server with the story so far when the child speaks (#323) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLiveRepairTest {

    private fun verdict(next: String?) = Server.Verdict("ok", emptyList(), next, null,
        false, false, null, false, false, false, null)

    @Test
    fun aSpokenCorrectionWithoutALineGetsTheBakedApology() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.slots["place"] = "바다"
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        var sent: Server.Turn? = null
        try {
            val job = launch {
                d.askStory(Question("바다에서 무슨 일이 생겼어?", Kind.EASY), "problem", singleAttempt = true) {
                    sent = it
                    Server.TurnResult(verdict("place"), null, act = "repair", retract = listOf("place"))
                }
            }
            val feeder = launch { while (job.isActive) { d.send(Reply.Spoke("그거 아니야")); delay(40) } }
            withTimeout(8_000) { job.join() }
            feeder.cancelAndJoin()
            assertNotNull("a spoken answer turns the reading on", sent!!.history)
            assertEquals(SORRY_LINE, d.s.ottoSaid)
            assertNull(d.s.slots["place"])
            assertEquals("place", d.s.storyNextSlot)
        } finally { Server.liveModes = emptySet(); Server.base = null; scope.cancel() }
    }

    @Test
    fun aPickedCardSendsNoHistory() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        var sent: Server.Turn? = null
        try {
            val job = launch {
                d.askStory(Question("어디로 갈까?", Kind.EASY), "place", singleAttempt = true) {
                    sent = it
                    Server.TurnResult(Server.Verdict("ok", listOf("place" to "바다"), "problem", null,
                        false, false, null, false, false, false, null), null)
                }
            }
            val feeder = launch { while (job.isActive) { d.send(Reply.Tapped("바다", "바다")); delay(40) } }
            withTimeout(8_000) { job.join() }
            feeder.cancelAndJoin()
            assertNull(sent!!.history)
            assertEquals(0, d.s.storyTalk.size)
        } finally { Server.liveModes = emptySet(); Server.base = null; scope.cancel() }
    }
}
