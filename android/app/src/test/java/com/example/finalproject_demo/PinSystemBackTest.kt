package com.example.finalproject_demo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.PinView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 S25+ · 0.5 — 부모 비밀번호 화면에서 폰의 뒤로 가기를 누르면 방으로 안 가고 앱이 통째로 닫혔다.
 * 화면의 ← 는 「취소」를 보내는데 폰의 뒤로 가기를 받는 곳이 없어 안드로이드 기본 동작으로 갔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class PinSystemBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val sup = SupervisorJob()
    @After fun stop() = sup.cancel()

    @Test fun systemBackCancelsThePinInsteadOfClosingTheApp() {
        val d = Director(CoroutineScope(sup)).also { it.s.speed = 0.01 }
        var passed: Boolean? = null
        CoroutineScope(sup).launch { passed = d.pinGate("parent") }
        compose.waitUntil(5_000) { d.s.stage is Stage.Pin }
        compose.setContent { (d.s.stage as? Stage.Pin)?.let { PinView(d, it) } }
        compose.waitForIdle()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { passed != null }

        assertEquals("뒤로 가기는 비밀번호를 취소해야 한다", false, passed)
        assertFalse("앱이 닫혔다", compose.activity.isFinishing)
    }
}
