package com.example.finalproject_demo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.finalproject_demo.ui.shell.PinPad
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 테스터 (#394) — 「비밀번호 4칸을 다 채워도 넘어가지 않는다」. 번호가 틀렸는데 화면이 알려 주지 못했다:
 * 한 번 틀리면 「비밀번호가 맞지 않아요」가 계속 남아, 그 뒤로는 틀려도 달라지는 것이 없었다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class PinWrongMessageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val wrongText = "비밀번호가 맞지 않아요"

    private fun enterFour() = repeat(4) { compose.onNodeWithText("1").performClick() }

    private fun wrongShown() = compose.onAllNodes(androidx.compose.ui.test.hasText(wrongText)).fetchSemanticsNodes().isNotEmpty()

    @Test fun theWrongNumberMessageGoesAwayAndComesBackOnTheNextMiss() {
        var tries = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { PinPad("부모 비밀번호", "네 자리를 눌러 주세요", onDone = { tries++; false }) }
        compose.mainClock.advanceTimeBy(100)

        enterFour()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, tries)
        compose.onNodeWithText(wrongText).assertIsDisplayed()

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("틀렸다는 말이 계속 남아 있다", false, wrongShown())
        compose.onNodeWithText("네 자리를 눌러 주세요").assertIsDisplayed()

        enterFour()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(2, tries)
        assertEquals("두 번째로 틀렸을 때 알려 주지 않는다", true, wrongShown())
    }
}
