package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.ShelfBook
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.ShelfView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 10-07 종훈: books sat left of the shelf's middle and did not fit the compartments. Renders for looking (build/shelf/). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShelfFitShotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, n: Int) {
        val d = Director(CoroutineScope(SupervisorJob()))
        repeat(n) { d.s.shelf += ShelfBook("책 ${it + 1}", "A", "bg_park", savedStoryId = "s$it") }
        compose.setContent { ShelfView(d, Stage.Shelf(false)) }
        compose.onRoot().captureRoboImage(File("build/shelf/$name.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
    @Test fun phoneS10() = shot("phone_s10", 12)

    @Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
    @Test fun phoneNarrow() = shot("phone_807", 12)

    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-240dpi")
    @Test fun tablet() = shot("tablet", 12)
}
