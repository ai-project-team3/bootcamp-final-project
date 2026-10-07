package com.example.finalproject_demo

import com.example.finalproject_demo.net.Bgm
import com.example.finalproject_demo.net.BgmChannel
import com.example.finalproject_demo.net.BgmOutput
import android.os.Looper
import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BgmTest {
    @Before fun fresh() = Bgm.resetForTest()
    @After fun reset() = Bgm.resetForTest()

    /** 자산이 없거나 기기가 못 여는 곡 — 죽지 않고 조용하다 (#221) */
    @Test fun missingAssetIsSilentNotACrash() {
        Bgm.attach(RuntimeEnvironment.getApplication())
        Bgm.play("no_such_track.webm")
        assertNull(Bgm.mixer.playing)
    }

    /** a player that errored or was released must not crash the main thread (#221) */
    @Test fun deadPlayerChannelStaysQuiet() {
        Bgm.attach(RuntimeEnvironment.getApplication())
        val path = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "bgm/adventure_1.webm").absolutePath
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
            org.robolectric.shadows.util.DataSource.toDataSource(path), org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0))
        val ch = Bgm.open("adventure_1.webm")
        assertNotNull(ch)
        ch!!.release()   // the underlying player is now unusable
        ch.setVolume(0.5f); ch.start(); ch.pause()
        ch.durationMs; ch.positionMs
    }

    /** a held mixer does nothing per tick, so the main looper must not be woken 20 times a second for it (#221) */
    @Test fun aHeldMusicLeavesNoTickerRunning() {
        var reads = 0
        Bgm.output = BgmOutput { object : BgmChannel {
            override val durationMs = 600_000L; override val positionMs = 0L
            override fun start() {}; override fun pause() {}; override fun setVolume(v: Float) {}; override fun release() {}
        } }
        Bgm.resetMixerForTest()
        Bgm.clock = { reads++; 0L }
        Bgm.attach(RuntimeEnvironment.getApplication())
        val looper = shadowOf(Looper.getMainLooper())
        Bgm.play("a.webm"); looper.idle()
        Bgm.hold("pause"); looper.idle()
        val before = reads
        looper.idleFor(Duration.ofSeconds(2))
        assertEquals("no tick may run while held", before, reads)
        Bgm.resume("pause"); looper.idleFor(Duration.ofMillis(200))
        assertTrue("ticking returns after resume", reads > before)
    }
}
