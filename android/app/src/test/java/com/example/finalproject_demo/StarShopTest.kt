package com.example.finalproject_demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.ui.shell.*
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 별 구매하기 (#445 · 10-13 종훈). 아이에게 결제가 보이지 않아야 한다: 방의 「+」는 어른 확인만 열고,
 * 금액과 구매 버튼은 그 뒤에만 있다. 결제는 아직 붙지 않아 구매 버튼은 「준비 중입니다」를 띄우고 별은 그대로다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-420dpi")
class StarShopTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob())

    @Before fun prepare() { CallLimits.reset(); CallLimits.enabled = true; Shell.sheet = Sheet.NONE }
    @After fun clean() { scope.cancel(); Shell.sheet = Sheet.NONE; CallLimits.reset() }

    private fun capture(name: String) {
        val file = File("build/review/star-$name.png").also { it.parentFile?.mkdirs() }
        compose.onRoot().captureRoboImage(file.path, RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun thePlusBesideTheStarsOpensTheGrownUpCheckNotThePrices() {
        val d = Director(scope)
        compose.setContent { Box(Modifier.size(640.dp, 360.dp)) { OttoRoom(d); if (Shell.sheet != Sheet.NONE) StarShopSheet() } }
        compose.waitForIdle()
        capture("room-plus")
        compose.onNodeWithContentDescription("별 구매").performClick()
        compose.waitForIdle()
        assertEquals(Sheet.STAR_GATE, Shell.sheet)
        compose.onNodeWithText("어른 확인").assertIsDisplayed()
        STAR_PACKS.forEach { compose.onNodeWithText(it.price).assertDoesNotExist() }
        compose.onNodeWithText(STAR_SUBSCRIPTION.price).assertDoesNotExist()
        capture("gate")
        // closing says nothing and goes back
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        assertEquals(Sheet.NONE, Shell.sheet)
    }

    @Test fun pastTheGateEveryOfferShowsItsStarsAndItsPriceAndBuyingIsNotOpenYet() {
        Shell.sheet = Sheet.STAR_SHOP
        compose.setContent { Box(Modifier.size(640.dp, 360.dp)) { if (Shell.sheet != Sheet.NONE) StarShopSheet() } }
        compose.waitForIdle()
        compose.onNodeWithText("지금 가진 별 4개").assertIsDisplayed()
        (STAR_PACKS + STAR_SUBSCRIPTION).forEach {
            compose.onNodeWithText(it.title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(it.price).performScrollTo().assertIsDisplayed()
        }
        capture("shop")
        compose.onNodeWithText("구독하기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("준비 중입니다").assertIsDisplayed()
        capture("soon")
        compose.onNodeWithText("확인").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("준비 중입니다").assertDoesNotExist()
        compose.onAllNodesWithText("구매하기")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("준비 중입니다").assertIsDisplayed()
        assertEquals("아직 결제가 없으니 별이 늘면 안 된다", 4, CallLimits.starsLeft())
    }
}
