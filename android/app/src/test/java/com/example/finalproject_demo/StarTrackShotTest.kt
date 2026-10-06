package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.StoryPreparationPhase
import com.example.finalproject_demo.demo.StoryPreparationProgress
import com.example.finalproject_demo.ui.ProgressTrack
import com.example.finalproject_demo.ui.StoryPreparationTrack
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * One star track for every mode (10-06 종훈) — live story (40 fine steps, 10 beads), scripted story / co-op
 * and the picture diary's two stars. Pictures stay under build/ — for looking at, not for comparing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class StarTrackShotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theTrackAtSeveralFillLevels() {
        compose.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF9CC9E8)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 20, 28).forEach {
                        StoryPreparationTrack(StoryPreparationProgress(it, StoryPreparationPhase.COLLECTING))
                    }
                    StoryPreparationTrack(StoryPreparationProgress(36, StoryPreparationPhase.FINISHING))
                }
                // scripted story / co-op, then the picture diary's two stars
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressTrack(4, 9)
                    ProgressTrack(1, 2)
                    ProgressTrack(2, 2)
                }
            }
        }
        compose.onRoot().captureRoboImage(
            File("build/gauge/levels.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }
}
