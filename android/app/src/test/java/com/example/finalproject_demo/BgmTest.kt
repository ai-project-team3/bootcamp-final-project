package com.example.finalproject_demo

import com.example.finalproject_demo.net.Bgm
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
