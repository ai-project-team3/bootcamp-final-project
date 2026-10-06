package com.example.finalproject_demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.StoryPreparationTrack
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class StoryPreparationTrackTest {
    @get:Rule val compose = createComposeRule()
    private val previousBase = Server.base
    private val previousModes = Server.liveModes

    @Before fun connectStory() {
        Server.base = "http://localhost:1"
        Server.liveModes = setOf(StoryMode.STORY)
        compose.mainClock.autoAdvance = false
    }

    @After fun restoreConnection() {
        Server.base = previousBase
        Server.liveModes = previousModes
    }

    @Test fun trackNamesTheActualPreparationStageOnASmallLandscapeScreen() {
        val s = DemoState().apply {
            listOf("place", "problem", "reaction", "newcomer", "solution").forEach { slots[it] = "아이의 답" }
        }
        compose.setContent { MaterialTheme { StoryPreparationTrack(s.liveStoryProgress!!) } }

        fun check(label: String, name: String) {
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onRoot().captureRoboImage("build/story-progress/$name.png", RoborazziOptions(taskType = RoborazziTaskType.Record))
        }
        check("이야기 모으기", "collecting")
        compose.runOnIdle { s.endReason = "story_ready" }
        check("그림·소리 마무리", "finishing")
        compose.runOnIdle { s.scene = Scene.MAKING; s.stage = Stage.Making("문장 생성 중") }
        check("책 만들기", "writing")
        compose.runOnIdle { s.stage = Stage.Making("완성", 1f) }
        check("책 완성", "complete")
    }
}
