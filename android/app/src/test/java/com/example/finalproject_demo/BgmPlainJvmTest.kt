package com.example.finalproject_demo

import com.example.finalproject_demo.net.Bgm
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test

/** 앱에 붙기 전의 Bgm 은 안드로이드 API 를 건드리지 않는다 — 순수 JVM 흐름 테스트가 Director.go 로 부른다 (#221) */
class BgmPlainJvmTest {
    @After fun reset() = Bgm.resetForTest()

    @Test fun callsBeforeAttachAreHarmless() {
        Bgm.play("night_1.webm"); Bgm.duck(true); Bgm.hold("mic"); Bgm.resume("mic"); Bgm.stop(); Bgm.setEnabled(false)
        assertNull(Bgm.mixer.playing)
    }
}
