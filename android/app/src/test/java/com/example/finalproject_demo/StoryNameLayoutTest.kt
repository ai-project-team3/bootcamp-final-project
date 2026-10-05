package com.example.finalproject_demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.FitScreen
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.PuppetTypography
import com.example.finalproject_demo.ui.Touch
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class StoryNameLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun readableNameControls(heard: String?, fontScale: Float = 1f) {
        val previousFrozen = motionFrozen
        val scope = CoroutineScope(SupervisorJob())
        try {
            motionFrozen = true
            val d = Director(scope)
            d.s.stage = Stage.NameEntry(HeroAttr(), null, heard)
            var fittedDensity = 1f
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    MaterialTheme(typography = PuppetTypography) { FitScreen {
                        fittedDensity = LocalDensity.current.density
                        StageView(d)
                    } }
                }
            }
            val labels = if (heard == null) listOf("이 이름으로")
                else listOf("맞아!", "아니야, 다시 말할래", "이 이름으로")
            for (label in labels) {
                val button = compose.onNode(hasText(label) and hasClickAction())
                if (fontScale > 1f) button.performScrollTo()
                button.assertIsDisplayed()
                val bounds = button.fetchSemanticsNode().boundsInRoot
                // The app requires 64dp child targets; permit only final pixel rounding.
                assertTrue("$label is too short to tap: $bounds",
                    bounds.height + 1f >= Touch.KidMin.value * fittedDensity)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("$label has no rendered text", layouts.isNotEmpty())
                val layout = layouts.single()
                assertFalse("$label is vertically clipped", layout.didOverflowHeight)
                val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$label extends outside its button: $text / $bounds",
                    text.top >= bounds.top && text.bottom <= bounds.bottom &&
                        text.left >= bounds.left && text.right <= bounds.right)
            }
            compose.onNode(hasSetTextAction()).performTextReplacement("반짝이")
            compose.mainClock.advanceTimeByFrame()
            compose.onNode(hasSetTextAction()).assertTextEquals("반짝이")
            val image = File("build/qa/name-${compose.onRoot().fetchSemanticsNode().boundsInRoot.width.toInt()}-$fontScale-${heard != null}.png")
                .apply { parentFile?.mkdirs() }
            compose.onRoot().captureRoboImage(image.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally {
            scope.cancel()
            motionFrozen = previousFrozen
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-160dpi")
    fun heardNameButtonsRemainReadableOnSmallPhone() = readableNameControls("금태양")

    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-160dpi")
    fun typedNameRemainsUsableBeforeVoiceAnswer() = readableNameControls(null)

    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-160dpi")
    fun largeFontNameControlsCanBeReached() = readableNameControls("반짝반짝무지개친구", 1.5f)

    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-160dpi")
    fun tabletNameButtonsRemainReadable() = readableNameControls("금태양")
}
