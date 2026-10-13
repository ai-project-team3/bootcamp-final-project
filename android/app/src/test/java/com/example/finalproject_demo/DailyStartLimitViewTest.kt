package com.example.finalproject_demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.shell.OttoRoom
import com.example.finalproject_demo.ui.shell.SetupStep
import com.example.finalproject_demo.ui.shell.Sheet
import com.example.finalproject_demo.ui.shell.Shell
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26], qualifiers = "w640dp-h360dp-land-240dpi")
class DailyStartLimitViewTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob())
    private var oldBase: String? = null
    private var oldModes: Set<StoryMode> = emptySet()

    @Before fun prepare() {
        oldBase = Server.base; oldModes = Server.liveModes
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        CallLimits.reset(); CallLimits.enabled = true
        repeat(CallLimits.FREE_STARS_PER_DAY + CallLimits.WELCOME_STARS) { CallLimits.starSpent() }
        compose.mainClock.autoAdvance = false
    }

    @After fun clean() { scope.cancel(); Shell.sheet = Sheet.NONE; CallLimits.reset(); Server.base = oldBase; Server.liveModes = oldModes }

    private fun draw(notice: Boolean = true, paused: Boolean = false): Director {
        val d = Director(scope).apply {
            s.limitOn = false
            s.notice = if (notice) "별을 다 썼어.\n내일 또 생겨!\n만들던 건 이어서 해!" else null
            if (paused) s.paused = Scene.MAKEHERO
        }
        compose.setContent { Box(Modifier.size(640.dp, 360.dp)) { OttoRoom(d) } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        return d
    }

    private fun capture(name: String) {
        val file = java.io.File("build/review/daily-start-$name.png")
        file.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(file.path,
            RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun parentUnlimitedDoesNotShowInfinityWhenServiceStartsAreExhausted() {
        draw(notice = false)
        compose.onNodeWithText("∞").assertDoesNotExist()
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test fun serviceLimitShowsItsActualNoticeInsteadOfTheGenericGoodbye() {
        draw()
        val node = compose.onNodeWithText("별을 다 썼어.\n내일 또 생겨!\n만들던 건 이어서 해!", useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // API26 can retain the paragraph's maximum constraint width (384px) while the actual
        // text is only 230px wide. Check every rendered line, not that intrinsic-width flag.
        assertFalse("The limit notice must fit vertically", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) {
            assertTrue("The limit notice line $line must fit: ${layout.getLineRight(line)} > ${layout.size.width}",
                layout.getLineLeft(line) >= 0f && layout.getLineRight(line) <= layout.size.width + 0.5f)
        }
    }

    @Test fun serviceLimitDoesNotPromiseAnExtraStartThatParentApprovalCannotGive() {
        draw()
        compose.onNodeWithText("어른").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithText("별이 없어요").assertIsDisplayed()
        compose.onNodeWithText("새 이야기 한 번 더 시작하기").assertDoesNotExist()
        // the only way on is the shop — and it opens the grown-up check first, not the prices (#445)
        compose.onNodeWithText("별 구매하기").performClick()
        assertEquals(Sheet.STAR_GATE, Shell.sheet)
        capture("service-explanation")
    }

    @Test fun anAlreadyStartedHeroIsAccessibleFromTheLimitNotice() {
        draw(paused = true)
        compose.onNodeWithText("이어서").assertIsDisplayed()
        compose.onNodeWithText("방으로").assertIsDisplayed()
        capture("continue")
    }

    @Test fun storeSetupKeepsEveryChoiceReachableOnASmallLandscapeScreen() {
        compose.mainClock.autoAdvance = true
        compose.setContent { Box(Modifier.size(640.dp, 360.dp)) { SetupStep({}, {}) } }
        compose.mainClock.advanceTimeBy(200)
        // no daily limit choice in the setup any more — the stars are the limit (#445)
        compose.onNodeWithText("3회").assertDoesNotExist()
        compose.onNodeWithText("제한 없음").assertDoesNotExist()
        capture("setup-limits")
        compose.onNodeWithText("비밀번호로 확인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("설정 끝").assertIsDisplayed()
    }
}
