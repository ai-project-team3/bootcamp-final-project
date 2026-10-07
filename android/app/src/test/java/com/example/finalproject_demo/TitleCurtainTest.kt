package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.finalproject_demo.ui.FeelPrefs
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.shell.TitleScreen
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
 * Title screen start animation (#306) — strings drop, lift the logo, curtains part on the room.
 * Runs the real animation on a manual clock: it goes on by itself in about 2 s, and a second tap skips it.
 * Frames land in `build/review/` to look at, not to compare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class TitleCurtainTest {
    @get:Rule val compose = createComposeRule()
    private val wasFrozen = motionFrozen

    @Before fun moving() { motionFrozen = false; FeelPrefs.setSound(false) }
    @After fun still() { motionFrozen = wasFrozen; FeelPrefs.unload() }

    private fun shot(name: String) =
        captureScreenRoboImage(File("build/review/title_$name.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))

    @Test
    fun aTapPlaysTheCurtainThenGoesOn() {
        var started = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TitleScreen { started++ } }
        compose.mainClock.advanceTimeBy(100); shot("0_waiting")
        compose.onNodeWithText("눌러서 시작").performClick()
        listOf(1 to 180L, 2 to 220L, 3 to 300L, 4 to 300L, 5 to 300L).forEach { (i, ms) ->
            compose.mainClock.advanceTimeBy(ms); shot("${i}_${ms}ms")
        }
        assertEquals("went on before the animation finished", 0, started)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("the animation never went on", 1, started)
    }

    @Test
    fun aSecondTapSkips() {
        var started = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TitleScreen { started++ } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("눌러서 시작").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("눌러서 시작").performClick()
        compose.mainClock.advanceTimeBy(50)
        assertEquals(1, started)
        compose.mainClock.advanceTimeBy(2_500)
        assertEquals("went on twice", 1, started)
    }
}
