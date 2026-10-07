package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.GOLD_CRAYON
import com.example.finalproject_demo.demo.PEN_W
import com.example.finalproject_demo.demo.RAINBOW_COLORS
import com.example.finalproject_demo.demo.Reward
import com.example.finalproject_demo.demo.Rewards
import com.example.finalproject_demo.demo.STAR_STAMP_BOOKS
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.heartStamp
import com.example.finalproject_demo.demo.starStamp
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 업적 보상 (10-06 종훈 · #223) — 받은 보상은 폰에 남고 다음 그림판에서 도구로 쓴다.
 * 받는 조건은 아이가 한 일뿐이다. 도구로 그린 것도 보통 선이라 책에 그대로 들어간다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class RewardsTest {
    @get:Rule val compose = createComposeRule()

    @Before fun attach() { Rewards.attach(ApplicationProvider.getApplicationContext()); Rewards.clear() }
    @After fun clean() = Rewards.clear()

    @Test fun aRewardIsKeptOnThePhoneAndGivenOnce() {
        assertTrue(Rewards.grant(Reward.RAINBOW))
        assertFalse("twice is not a second reward", Rewards.grant(Reward.RAINBOW))
        Rewards.reload()
        assertTrue("survives an app restart", Rewards.has(Reward.RAINBOW))
        assertTrue(Reward.RAINBOW in Rewards.fresh)
        Rewards.tried(Reward.RAINBOW)
        Rewards.reload()
        assertFalse("the new mark goes once tried", Reward.RAINBOW in Rewards.fresh)
    }

    @Test fun booksEarnTheirModesFirstRewardAndTheThirdBookTheStarStamp() {
        assertEquals(listOf(Reward.GOLD), Rewards.bookShelved(StoryMode.DIARY, coop = false))
        assertEquals("co-op is not a diary", listOf(Reward.HEART), Rewards.bookShelved(StoryMode.DIARY, coop = true))
        assertEquals(STAR_STAMP_BOOKS, 3)
        assertEquals(listOf(Reward.STAR), Rewards.bookShelved(StoryMode.STORY, coop = false))
        assertEquals("nothing twice", emptyList<Reward>(), Rewards.bookShelved(StoryMode.DIARY, coop = false))
    }

    @Test fun stampsAreClosedShapesOfTheirSize() {
        listOf(starStamp(Offset(50f, 50f), 20f), heartStamp(Offset(50f, 50f), 20f)).forEach { pts ->
            assertTrue("closed", (pts.first() - pts.last()).getDistance() < 0.5f)
            assertTrue("within the stamp", pts.all { (it - Offset(50f, 50f)).getDistance() <= 21f })
        }
    }

    private fun pad(): Director = Director(CoroutineScope(SupervisorJob())).also {
        it.s.mode = StoryMode.STORY
        it.s.stage = Stage.DrawPad()
    }

    @Test fun withoutRewardsThePadHasNoRewardTools() {
        val d = pad()
        compose.setContent { StageView(d) }
        compose.onNodeWithText("🌈").assertDoesNotExist()
    }

    /** 무지개 크레용으로 한 번 그으면 색이 바뀌는 짧은 선들이 책에 들어간다. 굵은 붓은 굵게 */
    @Test fun theRainbowCrayonAndTheBigBrushDrawIntoTheBook() {
        Reward.entries.forEach { Rewards.grant(it) }
        val d = pad()
        compose.setContent { StageView(d) }
        compose.onNodeWithText("🌈").performClick()
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.35f, height * 0.5f), Offset(width * 0.8f, height * 0.55f), 600) }
        compose.onNodeWithText("🖌").performClick()
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.35f, height * 0.7f), Offset(width * 0.7f, height * 0.75f), 400) }
        compose.onNodeWithText("⭐").performClick()
        compose.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.3f)) }
        compose.onRoot().captureRoboImage(File("build/rewards/pad.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("✅ 다 그렸어").performClick()
        compose.waitForIdle()

        val rainbow = d.s.drawing.filter { it.color in RAINBOW_COLORS && it.w == PEN_W }
        assertTrue("the rainbow changes colour along the line: ${rainbow.map { it.color }.distinct().size}", rainbow.map { it.color }.distinct().size >= 3)
        assertTrue("a big brush line", d.s.drawing.any { it.w > PEN_W * 2 })
        assertTrue("a star stamp", d.s.drawing.any { it.pts.size == 11 })
        assertFalse("the gold crayon was not used", d.s.drawing.any { it.color == GOLD_CRAYON })
        assertFalse("tried tools lose their new mark", Reward.RAINBOW in Rewards.fresh)
    }
}
