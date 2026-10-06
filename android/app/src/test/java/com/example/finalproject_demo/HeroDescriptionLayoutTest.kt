package com.example.finalproject_demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.*
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-160dpi")
@OptIn(ExperimentalRoborazziApi::class)
class HeroDescriptionLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun checkDescription(fontScale: Float) {
        val beforeFrozen = motionFrozen
        val scope = CoroutineScope(SupervisorJob())
        try {
            motionFrozen = true
            val d = Director(scope)
            val heard = "뾰족한 머리에 빨간 망토를 두르고 있어"
            d.s.stage = Stage.HeroAnswer(heard)
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
            val transcript = compose.onNodeWithText(heard)
            transcript.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            transcript.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("the transcript must wrap rather than truncate", layouts.single().hasVisualOverflow)
            for (label in listOf("맞아", "다시 말할래")) {
                val button = compose.onNode(hasText(label) and hasClickAction())
                button.performScrollTo().assertIsDisplayed()
                val bounds = button.fetchSemanticsNode().boundsInRoot
                assertTrue("$label must retain its child-sized touch target",
                    bounds.height + 1f >= Touch.KidMin.value * fittedDensity)
                val textLayouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
                val layout = textLayouts.single()
                val diagnostic = File("build/qa/hero-description-$fontScale.png").apply { parentFile?.mkdirs() }
                compose.onRoot().captureRoboImage(diagnostic.path,
                    roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
                assertFalse("$label must not be clipped: width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} size=${layout.size} constraints=${layout.layoutInput.constraints}", layout.hasVisualOverflow)
            }
            val file = File("build/qa/hero-description-$fontScale.png").apply { parentFile?.mkdirs() }
            compose.onRoot().captureRoboImage(file.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally { scope.cancel(); motionFrozen = beforeFrozen }
    }

    @Test fun smallPhoneDisplaysTheWholeAnswerAndReadableConfirmationButtons() = checkDescription(1f)
    @Test fun largeFontsKeepTranscriptAndBothChoicesReachable() = checkDescription(1.5f)
}
