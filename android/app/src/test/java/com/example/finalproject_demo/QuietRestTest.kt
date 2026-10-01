package com.example.finalproject_demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.finalproject_demo.ui.rememberQuietRest
import com.example.finalproject_demo.ui.wakeOnTouch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 가만히 두면 쉬기 (#40) — 조용한 시간이 지나면 쉬고, 누르는 순간 바로 깬다. 깨우는 터치가 아래 버튼을 막지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuietRestTest {
    @get:Rule val compose = createComposeRule()

    private var clicks = 0

    private fun show() = compose.setContent {
        val quiet = rememberQuietRest(afterMs = 1_000, enabled = true)
        Box(Modifier.fillMaxSize().wakeOnTouch(quiet)) {
            Text(if (quiet.resting) "쉬는 중" else "움직이는 중", Modifier.clickable { clicks++ })
        }
    }

    @Test
    fun restsAfterTheQuietSpellAndWakesOnTheNextTouch() {
        show()
        compose.onNodeWithText("움직이는 중").assertExists()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("움직이는 중").assertExists()      // 아직 아니다
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("쉬는 중").assertExists()

        compose.onRoot().performTouchInput { down(center); up() }
        compose.waitForIdle()
        compose.onNodeWithText("움직이는 중").assertExists()      // 누르는 순간 깬다

        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("쉬는 중").assertExists()          // 다시 조용하면 다시 쉰다
    }

    @Test
    fun theWakingTouchStillReachesTheButtonUnderIt() {
        show()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("쉬는 중").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals("깨우는 터치가 버튼을 막았다", 1, clicks)
    }

    @Test
    fun screenTestsNeverRest() {
        compose.setContent {
            val quiet = rememberQuietRest(afterMs = 1_000)   // 기본값: 화면 검사(motionFrozen)에서는 쉬지 않는다
            Text(if (quiet.resting) "쉬는 중" else "움직이는 중")
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("움직이는 중").assertExists()
    }
}
