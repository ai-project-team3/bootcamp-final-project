package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.net.Bgm
import com.example.finalproject_demo.net.BgmChannel
import com.example.finalproject_demo.net.BgmOutput
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.sound.ChildSound
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Where the music yields: Otto's voice (duck), any mic (hold), ⏸ (hold) — #221. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BgmHooksTest {
    private var now = 0L
    private val duckCalls = mutableListOf<Boolean>()
    private val realCapture = ChildSound.capture

    inner class Fake : BgmChannel {
        override val durationMs = 600_000L
        override val positionMs get() = 0L
        @JvmField var volume = -1f
        var starts = 0; var pauses = 0; var running = false
        override fun start() { starts++; running = true }
        override fun pause() { pauses++; running = false }
        override fun setVolume(v: Float) { volume = v }
        override fun release() { running = false }
    }
    private val channels = mutableListOf<Fake>()
    private val channel get() = channels.first()

    @Before fun fresh() {
        Bgm.resetForTest()
        Bgm.output = BgmOutput { Fake().also { channels += it } }
        Bgm.resetMixerForTest()
        Bgm.clock = { now }
        Bgm.onDuck = { duckCalls += it }
    }

    @After fun reset() {
        ChildSound.capture = realCapture
        Bgm.onDuck = {}
        Bgm.resetForTest()
    }

    private fun startMusic() { Bgm.play("night_1.webm"); now += 2000; Bgm.mixer.tick(now) }

    /** the test thread is Robolectric's main thread, so the line runs off it and the main looper is pumped by hand */
    private fun runLine(audio: ByteArray, cancelAfterMs: Long = -1) {
        val job = CoroutineScope(Dispatchers.Default).launch { Voice.playAndWait(audio) }
        val end = System.currentTimeMillis() + 5000
        val cancelAt = if (cancelAfterMs >= 0) System.currentTimeMillis() + cancelAfterMs else Long.MAX_VALUE
        while (!job.isCompleted && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() >= cancelAt) job.cancel()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the line finished", job.isCompleted)
    }

    @Test fun aMascotLineDucksThenUnducks() {
        Voice.attach(RuntimeEnvironment.getApplication())
        startMusic()
        runLine(ByteArray(64))   // not a real mp3: playback fails fast, and the duck must still be released
        assertEquals(listOf(true, false), duckCalls)
    }

    @Test fun aCancelledLineStillUnducks() {
        Voice.attach(RuntimeEnvironment.getApplication())
        startMusic()
        // every data source opens, and a 60 s clip never completes on its own: the line stays inside the player
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider { org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(60_000, 0) }
        try {
            val job = CoroutineScope(Dispatchers.Default).launch { Voice.playAndWait(ByteArray(64)) }
            val end = System.currentTimeMillis() + 5000
            while (duckCalls != listOf(true) && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertEquals(listOf(true), duckCalls)
            assertFalse("the line is still playing", job.isCompleted)
            job.cancel()
            while (!job.isCompleted && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(true, false), duckCalls)
        } finally {
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test fun leavingToTheRoomFromPauseReleasesTheHold() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.holdSession()
        d.leaveToRoom()
        Bgm.play("night_1.webm")
        assertTrue("later music must start", channels.last().running)
    }

    @Test fun pauseHoldsAndResumeReturnsTheMusic() {
        val d = Director(CoroutineScope(SupervisorJob()))
        startMusic()
        assertTrue(channel.running)
        d.holdSession()
        assertFalse(channel.running)
        d.resumeSession()
        assertTrue(channel.running)
        assertEquals(2, channel.starts)
    }

    @Test fun childSoundRecordingHoldsTheMusic() = runBlocking {
        startMusic()
        var pausedInside = false
        ChildSound.capture = { _ -> pausedInside = !channel.running; null }
        ChildSound.record(1000)
        assertTrue("music must be paused while the mic is open", pausedInside)
        assertTrue("music returns after recording", channel.running)
    }

    @Test fun childSoundRecordingReleasesTheHoldEvenWhenCaptureThrows() = runBlocking {
        startMusic()
        ChildSound.capture = { _ -> error("mic broke") }
        runCatching { ChildSound.record(1000) }
        assertTrue(channel.running)
    }

    @Test fun twoHoldsNeedTwoResumes() {
        val d = Director(CoroutineScope(SupervisorJob()))
        startMusic()
        Bgm.hold("screen")
        d.holdSession()
        Bgm.resume("screen")
        assertFalse("still paused by ⏸", channel.running)
        d.resumeSession()
        assertTrue(channel.running)
    }
}
