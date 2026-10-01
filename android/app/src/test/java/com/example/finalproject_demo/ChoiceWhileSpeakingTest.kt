package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #50 · 10-01 (민우 S25): 「안녕」 · 「또 만날래」 pressed while the mascot spoke did nothing — the
 * scene sat in `pause()` until the voice ended, then `awaitReply()` drained the tap away.
 * A choice section takes the tap at once and keeps it.
 */
class ChoiceWhileSpeakingTest {
    private fun director() = Director(CoroutineScope(SupervisorJob())).apply { s.speed = 1.0 }

    @Test
    fun aTapDuringTheRestIsReturnedAtOnceNotDrained() = runBlocking {
        val d = director()
        val t0 = System.currentTimeMillis()
        val rest = async { d.pauseOrChoice(5_000) }
        delay(50)
        d.send(Reply.Tapped("keep:friend", "콩이"))
        val r = rest.await()
        assertEquals("keep:friend", (r as Reply.Tapped).value)
        assertTrue("waited the whole rest", System.currentTimeMillis() - t0 < 2_000)
    }

    @Test
    fun noTapMeansTheRestRunsOutWithNothing() = runBlocking {
        assertNull(director().pauseOrChoice(30))
    }

    @Test
    fun twoQuickTapsAreTwoInputsNotOneLost() = runBlocking {
        val d = director()
        val rest = async { d.pauseOrChoice(5_000) }
        delay(50)
        d.send(Reply.Tapped("keep:friend", "콩이"))
        d.send(Reply.Tapped("done", "다음"))
        assertEquals("keep:friend", (rest.await() as Reply.Tapped).value)
        // the second one waits in line for the next pauseOrChoice — the scene decides what it means
        assertEquals("done", (d.pauseOrChoice(5_000) as Reply.Tapped).value)
    }

    @Test
    fun enteringAChoiceDropsTheLastScreensTaps() = runBlocking {
        val d = director()
        d.send(Reply.Tapped("old", "앞 화면"))
        val choice = async { d.awaitChoice() }
        delay(50)
        d.send(Reply.Tapped("bye:friend", "콩이"))
        assertEquals("bye:friend", (choice.await() as Reply.Tapped).value)
    }
}
