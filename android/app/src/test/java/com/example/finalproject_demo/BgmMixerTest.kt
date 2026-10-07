package com.example.finalproject_demo

import com.example.finalproject_demo.net.BGM_BASE
import com.example.finalproject_demo.net.BGM_DUCK
import com.example.finalproject_demo.net.BgmChannel
import com.example.finalproject_demo.net.BgmMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 배경음악 믹서 — 페이드 · 크로스페이드 · 반복 이음새 · 덕킹 · 멈춤 이유 (#221). 시계는 손으로 돌린다 */
class BgmMixerTest {
    private var now = 0L

    inner class FakeChannel(val track: String, override val durationMs: Long) : BgmChannel {
        @JvmField var volume = -1f; var started = false; var released = false; var starts = 0
        private var playedMs = 0L; private var since = -1L
        override val positionMs get() = playedMs + if (since >= 0) now - since else 0
        override fun start() { started = true; starts++; since = now }
        override fun pause() { if (since >= 0) playedMs += now - since; since = -1 }
        override fun setVolume(v: Float) { volume = v }
        override fun release() { released = true; pause() }
    }

    private val opened = mutableListOf<FakeChannel>()
    private var length = 60_000L
    private var canOpen = true
    private val mixer = BgmMixer { t -> if (canOpen) FakeChannel(t, length).also { opened += it } else null }
    private fun at(ms: Long) { now = ms; mixer.tick(now) }
    private fun near(want: Float, got: Float) = assertEquals(want, got, 0.01f)

    @Test fun startsWithAFadeIn() {
        mixer.play("a", now)
        near(0f, opened[0].volume)
        at(750); near(BGM_BASE / 2, opened[0].volume)
        at(1500); near(BGM_BASE, opened[0].volume)
    }

    @Test fun theSameTrackIsNotRestarted() {
        mixer.play("a", now); at(3000); mixer.play("a", now)
        assertEquals(1, opened.size)
    }

    @Test fun aNewMoodCrossfades() {
        mixer.play("a", now); at(3000)
        mixer.play("b", now); at(3750)
        near(BGM_BASE / 2, opened[0].volume); near(BGM_BASE / 2, opened[1].volume)
        at(4500)
        assertTrue(opened[0].released); near(BGM_BASE, opened[1].volume)
        assertEquals("b", mixer.playing)
    }

    @Test fun stopFadesOutThenReleases() {
        mixer.play("a", now); at(3000); mixer.stop(now)
        at(3750); near(BGM_BASE / 2, opened[0].volume)
        at(4500); assertTrue(opened[0].released); assertNull(mixer.playing); assertFalse(mixer.active)
    }

    @Test fun theLoopSeamCrossfadesIntoTheSameTrack() {
        length = 10_000
        mixer.play("a", now)
        at(7_900); assertEquals(1, opened.size)
        at(8_000); assertEquals(2, opened.size); assertEquals("a", opened[1].track); assertTrue(opened[1].started)
        at(9_000); near(BGM_BASE / 2, opened[0].volume); near(BGM_BASE / 2, opened[1].volume)
        at(10_000); assertTrue(opened[0].released); near(BGM_BASE, opened[1].volume)
    }

    @Test fun duckRampsDownUnderTheVoiceAndBackUp() {
        mixer.play("a", now); at(2000)
        mixer.duck(true, now); at(2300); near(BGM_BASE * BGM_DUCK, opened[0].volume)
        mixer.duck(false, now); at(2900)   // 700 ms wait …
        at(3000); at(3300); near(BGM_BASE, opened[0].volume)   // … then a 300 ms ramp
    }

    @Test fun duckReleaseWaitsSoBackToBackLinesDoNotPump() {
        mixer.play("a", now); at(2000)
        mixer.duck(true, now); at(2300)
        mixer.duck(false, now); at(2700)        // the 400 ms gap between two lines
        mixer.duck(true, now); at(3000)
        near(BGM_BASE * BGM_DUCK, opened[0].volume)
    }

    @Test fun resumesOnlyWhenEveryHoldIsReleased() {
        mixer.play("a", now); at(2000)
        mixer.hold("pause"); mixer.hold("screen")
        val starts = opened[0].starts
        mixer.resume("screen", now); at(3000)
        assertEquals(starts, opened[0].starts)        // one reason left — still paused
        mixer.resume("pause", now)
        assertEquals(starts + 1, opened[0].starts)
        near(0f, opened[0].volume); at(3750); near(BGM_BASE / 2, opened[0].volume)
    }

    @Test fun aHoldDropsTheFadingOutTrack() {
        mixer.play("a", now); at(2000); mixer.play("b", now); at(2500)
        mixer.hold("mic")
        assertTrue(opened[0].released); assertEquals("b", mixer.playing)
    }

    @Test fun playWhileHeldWaitsForTheResume() {
        mixer.hold("mic"); mixer.play("a", now)
        assertFalse(opened.isEmpty() || opened[0].started)
        mixer.resume("mic", now); assertTrue(opened[0].started)
    }

    @Test fun turningMusicOffSilencesAndOnPlaysTheWantedTrack() {
        mixer.setEnabled(false, now); mixer.play("a", now)
        assertTrue(opened.isEmpty())
        mixer.setEnabled(true, now)
        assertEquals("a", mixer.playing); assertTrue(opened[0].started)
    }

    @Test fun turningMusicOnPlaysTheWantedTrack() {
        mixer.play("a", now); at(2000)
        mixer.setEnabled(false, now); at(3500)
        assertTrue(opened[0].released)
        mixer.play("b", now)                    // the child turned a page meanwhile
        mixer.setEnabled(true, now)
        assertEquals("b", mixer.playing)
    }

    @Test fun aTrackThatCannotOpenLeavesSilence() {
        canOpen = false
        mixer.play("a", now); at(1000)
        assertNull(mixer.playing); assertFalse(mixer.active)
    }

    @Test fun stopWhileHeldStaysSilentOnResume() {
        mixer.play("a", now); at(2000)
        mixer.hold("mic"); mixer.stop(now)
        val starts = opened[0].starts
        mixer.resume("mic", now)
        assertTrue(opened[0].released); assertNull(mixer.playing); assertEquals(starts, opened[0].starts)
    }

    @Test fun disableWhileHeldStaysSilentOnResume() {
        mixer.play("a", now); at(2000)
        mixer.hold("mic"); mixer.setEnabled(false, now)
        val starts = opened[0].starts
        mixer.resume("mic", now)
        assertTrue(opened[0].released); assertNull(mixer.playing); assertEquals(starts, opened[0].starts)
    }

    @Test fun newMoodWhileHeldResumesOnlyTheNewOne() {
        mixer.play("a", now); at(2000)
        mixer.hold("mic"); mixer.play("b", now)
        mixer.resume("mic", now)
        assertTrue(opened[0].released); assertEquals(1, opened[0].starts)
        assertEquals(1, opened[1].starts); near(0f, opened[1].volume)
        at(2750); near(BGM_BASE / 2, opened[1].volume)
    }

    @Test fun releaseFreesEveryChannelAtOnce() {
        mixer.play("a", now); at(2000); mixer.play("b", now); at(2500)
        mixer.release()
        assertTrue(opened[0].released); assertTrue(opened[1].released)
        assertFalse(mixer.active); assertNull(mixer.playing)
    }

    @Test fun releaseKeepsMusicEnabled() {
        mixer.play("a", now); at(2000)
        mixer.release()
        mixer.play("b", now)
        assertEquals("b", mixer.playing); assertTrue(opened.last().started)
    }

    @Test fun releaseClearsEveryHold() {
        mixer.play("a", now); at(2000)
        mixer.hold("pause")
        mixer.release()
        mixer.play("b", now)
        assertTrue("a hold must not outlive release", opened.last().started)
        assertFalse(mixer.held)
    }

    @Test fun releaseResetsTheDuck() {
        mixer.play("a", now); at(2000)
        mixer.duck(true, now); at(2400)
        mixer.release()
        mixer.play("b", now); at(4000); at(6000)
        near(BGM_BASE, opened.last().volume)
    }

    @Test fun heldIsTrueWhileAnyHoldIsOpen() {
        mixer.play("a", now); at(2000)
        assertFalse(mixer.held)
        mixer.hold("pause"); mixer.hold("screen")
        assertTrue(mixer.held); assertTrue(mixer.active)
        mixer.resume("pause", now); assertTrue(mixer.held)
        mixer.resume("screen", now); assertFalse(mixer.held)
    }
}
