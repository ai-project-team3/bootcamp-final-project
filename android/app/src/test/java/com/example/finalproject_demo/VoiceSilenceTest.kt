package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/**
 * #331 — a recording with no voice in it stays on the phone. whisper writes 「자막을 키고 해줘」 onto ~1 s of a
 * quiet mic; such a clip is answered "" here (the empty-transcript path) and `/stt` is never called.
 * Rule 7: anything at a speaking level still goes to the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceSilenceTest {
    @After
    fun reset() { Server.base = null; Server.resetCalls() }

    /** [seconds] of a 220 Hz tone whose RMS is [dbfs] — null = digital silence. */
    private fun clip(seconds: Double, dbfs: Double?): ByteArray {
        val n = (Voice.RATE * seconds).toInt()
        val amp = dbfs?.let { 32768.0 * Math.pow(10.0, it / 20) * Math.sqrt(2.0) } ?: 0.0
        return Voice.wav(ShortArray(n) { (amp * sin(2 * PI * 220 * it / Voice.RATE)).toInt().toShort() })
    }

    /** A 1 s clip that is silent except for one 0.15 s syllable at [dbfs] in the middle — a child's 「응」. */
    private fun oneSyllable(dbfs: Double): ByteArray {
        val n = Voice.RATE
        val amp = 32768.0 * Math.pow(10.0, dbfs / 20) * Math.sqrt(2.0)
        val from = n / 2; val to = from + (Voice.RATE * 0.15).toInt()
        return Voice.wav(ShortArray(n) { if (it in from until to) (amp * sin(2 * PI * 300 * it / Voice.RATE)).toInt().toShort() else 0 })
    }

    @Test
    fun theLoudestFrameIsMeasuredInDbfs() {
        assertEquals(-20.0, Voice.loudestDbfs(clip(1.0, -20.0))!!, 0.5)
        assertEquals(-33.0, Voice.loudestDbfs(oneSyllable(-33.0))!!, 0.5)
        assertTrue(Voice.loudestDbfs(clip(1.0, null))!! < -90)
    }

    @Test
    fun notOurWavIsNotJudged() {
        // the test doubles elsewhere send three bytes; a stranger's file goes to the server as before
        assertNull(Voice.loudestDbfs(byteArrayOf(1, 2, 3)))
        assertNull(Voice.loudestDbfs(ByteArray(2_000)))
    }

    @Test
    fun aSilentSecondIsAnsweredOnThePhone() = runBlocking {
        Server.base = "http://127.0.0.1:1"
        Server.resetCalls()
        for (quiet in listOf(null, -60.0, -50.0)) {
            assertEquals("silent ($quiet dBFS) is the empty transcript", "", Voice.transcribe(clip(1.0, quiet)))
        }
        assertEquals("no /stt call for silence", "", Server.callSummary())
    }

    @Test
    fun aQuietChildStillGoesToTheServer() = runBlocking {
        // 30 cm speech measured -33 dBFS at its quietest (BlowDetector 10-07); one short syllable is enough to keep it
        Server.base = "http://127.0.0.1:1"
        Server.resetCalls()
        for (audio in listOf(oneSyllable(-33.0), oneSyllable(-40.0), clip(1.0, -42.0))) {
            assertNull("sent to /stt (no server here → null, not \"\")", Voice.transcribe(audio))
        }
        assertTrue(Server.callSummary(), Server.callSummary().contains("/stt 3"))
        // the floor stays well under that measured speech — 12 dB is the margin the rule-7 comment promises
        assertTrue(Voice.SILENCE_DBFS <= 20 * log10(750.0 / 32768) - 12 + 0.5)
    }
}
