package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The shared way in (오케스트레이터 ①): in a live mode the 🎤 records, `/stt` transcribes, and the
 * answer enters the flow as the same `Reply.Spoke` a scripted answer would — scenes cannot tell.
 * No microphone and no server here: [Voice.listen] and [Voice.transcribe] are swapped.
 */
class LiveMicTest {
    private val realListen = Voice.listen
    private val realTranscribe = Voice.transcribe
    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun reset() {
        Voice.listen = realListen; Voice.transcribe = realTranscribe
        Server.base = null; Server.liveModes = emptySet()
        scope.cancel()
    }

    private fun live(): Director {
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        return Director(scope).also { it.s.mode = StoryMode.STORY; it.s.micEnabled = true }
    }

    /** As in the app: the question is already waiting when the child presses 🎤 (`awaitReply` drops older input). */
    private fun Director.pressMicAndWait(): Reply = runBlocking {
        val reply = async { withTimeout(3_000) { awaitReply() } }
        yield()
        toggleMic()
        reply.await()
    }

    @Test
    fun whatTheServerHeardEntersTheFlowAsSpokenWords() {
        Voice.listen = { byteArrayOf(1, 2, 3) }
        Voice.transcribe = { "공룡 나라에 갈래" }
        val d = live()
        assertEquals(Reply.Spoke("공룡 나라에 갈래"), d.pressMicAndWait())
        assertFalse("the mic must switch itself off after the child stops", d.s.micOn)
    }

    @Test
    fun nothingHeardIsNoAnswer() {
        Voice.listen = { null }
        assertEquals(Reply.Silent, live().pressMicAndWait())
    }

    /**
     * 10-02 (#79): speech was heard but the transcript came back empty or failed — the child did
     * answer, so the mascot asks again and the ladder does not move. Only the third miss in a row
     * becomes no answer.
     */
    @Test
    fun heardButNotTranscribedAsksAgainBeforeItCountsAsNoAnswer() = runBlocking {
        Voice.listen = { byteArrayOf(1) }
        val d = live()
        var tries = 0
        Voice.transcribe = { tries++; if (tries % 2 == 0) null else "" }   // a dropped hallucination, then the server down
        val reply = async { withTimeout(5_000) { d.awaitReply() } }
        repeat(3) {
            yield()
            d.toggleMic()
            // a press while the transcript is pending is ignored (#232) — wait until it has landed
            withTimeout(2_000) { while (d.s.micOn || d.s.transcribing || tries <= it) delay(5) }
            if (it < 2) {
                delay(50)
                assertFalse("a miss is not an answer yet", reply.isCompleted)
                assertTrue("the mascot asks again", "한 번" in d.s.line)
            }
        }
        assertEquals(Reply.Silent, reply.await())
        assertEquals(3, tries)
    }

    @Test
    fun aRepeatAfterAMissIsTheAnswer() = runBlocking {
        Voice.listen = { byteArrayOf(1) }
        val d = live()
        var tries = 0
        Voice.transcribe = { tries++; if (tries == 1) "" else "바닷속" }
        val reply = async { withTimeout(5_000) { d.awaitReply() } }
        yield(); d.toggleMic()
        // wait for the miss to land: a press while the transcript is pending is ignored (#232), and the CI runner
        // sometimes pressed in that window — the repeat was dropped and the reply timed out (10-07 #265 CI)
        withTimeout(2_000) { while (d.s.micOn || d.s.transcribing || tries < 1) delay(5) }
        d.toggleMic()
        assertEquals(Reply.Spoke("바닷속"), reply.await())
    }

    @Test
    fun withTheSwitchOffTheMicStaysScripted() {
        var recorded = false
        Voice.listen = { recorded = true; null }
        val d = live().also { Server.liveModes = emptySet() }
        d.toggleMic()
        assertFalse("a mode not switched on must never open the real microphone", recorded)
    }

    @Test
    fun wavHeaderIsSixteenKiloHertzMonoPcm() {
        val pcm = shortArrayOf(0, 1000, -1000)
        val wav = Voice.wav(pcm)
        assertEquals(44 + 6, wav.size)
        assertArrayEquals("RIFF".toByteArray(), wav.copyOfRange(0, 4))
        assertArrayEquals("WAVE".toByteArray(), wav.copyOfRange(8, 12))
        val h = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, h.getShort(22).toInt())         // mono
        assertEquals(16_000, h.getInt(24))              // rate
        assertEquals(16, h.getShort(34).toInt())        // bits
        assertEquals(6, h.getInt(40))                   // data bytes
        assertEquals(1000, h.getShort(46).toInt())
    }
}
