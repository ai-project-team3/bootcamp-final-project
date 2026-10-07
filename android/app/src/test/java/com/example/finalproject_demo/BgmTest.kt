package com.example.finalproject_demo

import com.example.finalproject_demo.net.Bgm
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BgmTest {
    @After fun reset() = Bgm.resetForTest()

    /** 자산이 없거나 기기가 못 여는 곡 — 죽지 않고 조용하다 (#221) */
    @Test fun missingAssetIsSilentNotACrash() {
        Bgm.attach(RuntimeEnvironment.getApplication())
        Bgm.play("no_such_track.webm")
        assertNull(Bgm.mixer.playing)
    }
}
