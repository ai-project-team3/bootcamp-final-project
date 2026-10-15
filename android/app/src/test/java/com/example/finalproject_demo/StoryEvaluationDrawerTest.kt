package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryEvaluation
import com.example.finalproject_demo.ui.DemoDrawer
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26], qualifiers = "w640dp-h360dp-land")
class StoryEvaluationDrawerTest {
    @get:Rule val compose = createComposeRule()
    private val d = Director(CoroutineScope(Dispatchers.Unconfined))
    @After fun disable() { StoryEvaluation.setEnabled(d.s, false) }
    @Test fun evaluationStartsDisabledAndOnlyTheDevelopmentRecordToggleEnablesIt() {
        assertFalse(StoryEvaluation.enabled)
        d.s.scene = Scene.PLACE
        compose.setContent { DemoDrawer(d) {} }
        compose.onNodeWithText("🧾 기록").performScrollTo().performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/story-eval-drawer/off.png", RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("윤문 평가 기록 켜기 (DEV)").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(StoryEvaluation.enabled) }
        compose.waitForIdle()
        captureScreenRoboImage("build/story-eval-drawer/on.png", RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("윤문 평가 기록 끄기 (DEV)").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(StoryEvaluation.enabled) }
    }
}
