package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.Shell
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 그림일기 ⏸ (#163 · 10-06 진웅 — 동화 · 같이 만들기처럼 오른쪽 위).
 * 앱 틀까지 그려서 **실제 손가락처럼** 누른다 — 오른쪽 위 구석은 시연 서랍(길게 누르기)도 쓰는 자리라,
 * 위에 덮인 것이 터치를 먹는지는 앱 틀을 다 그려야 보인다.
 */
abstract class PauseScreen {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun fresh() { Shell.resetToFirstRun(); ConsentStore.withdraw() }
    @After fun clean() { Shell.resetToFirstRun(); ConsentStore.withdraw() }

    protected val director get() = compose.activity.director!!

    protected fun enter(mode: StoryMode, scene: Scene, stage: Stage) {
        ConsentStore.agree(); Shell.finishOnboarding()
        compose.waitUntil(8_000) { compose.activity.director != null }
        compose.runOnIdle {
            director.s.mode = mode
            director.s.scene = scene
            director.s.stage = stage
            director.inputs(mic = true, next = false)
        }
        compose.waitForIdle()
    }

    protected fun enterDiary(stage: Stage) = enter(StoryMode.DIARY, Scene.DIARY, stage)

    protected fun enterStory() = enter(StoryMode.STORY, Scene.PLACE, Stage.World(emptyList()))

    protected val pause get() = compose.onNodeWithTag("pause")

    protected fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** 누름 · 뗌을 시간을 두고 — [x] · [y] 는 ⏸ 누르는 자리 안의 비율 */
    protected fun fingerOnPause(x: Float = 0.5f, y: Float = 0.5f) {
        pause.performTouchInput { down(Offset(width * x, height * y)); advanceEventTime(80); up() }
        compose.waitForIdle()
    }
}

/** 사용자 폰(갤럭시 S10 5G · 868×411dp) — 앱은 어느 기기에서든 세로 411dp 이상으로 본다(`ui/Fit.kt`)라 가장 낮은 높이이기도 하다 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class DiaryPauseTest : PauseScreen() {
    /** ⏸ 를 서랍 칸 위로 올려도 시연 서랍은 맨 구석을 길게 누르면 열린다 */
    @Test
    fun theDemoDrawerStillOpensFromTheVeryCorner() {
        enterStory()
        compose.onRoot().performTouchInput { down(Offset(width - 4f, 4f)); advanceEventTime(1_000); up() }
        compose.waitForIdle()
        assertTrue("맨 구석을 길게 눌러도 시연 서랍이 안 열린다", compose.onAllNodesWithText("🎮 조작").fetchSemanticsNodes().isNotEmpty())
        assertTrue("서랍을 열다가 ⏸ 가 같이 눌렸다", !director.s.holding)
    }

    /** 동화도 같은 자리 · 같은 서랍 칸 — 일기만 고치고 동화를 두면 안 된다 */
    @Test
    fun aFingerOnThePauseStopsTheStoryToo() {
        enterStory()
        fingerOnPause()
        assertTrue("동화에서 ⏸ 가운데를 눌러도 멈추지 않았다", director.s.holding)
    }
}
