package com.example.finalproject_demo

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.shell.OpeningArt
import com.example.finalproject_demo.ui.shell.OpeningScene
import com.example.finalproject_demo.ui.shell.openingCamera
import com.example.finalproject_demo.ui.shell.openingCat
import com.example.finalproject_demo.ui.shell.roomOpeningPlacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningFramesTest {
    @get:Rule val compose = createComposeRule()
    private fun frames(device: String) {
        val time = mutableFloatStateOf(0f)
        compose.setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val director = remember { Director(scope) }
            val art = remember { OpeningArt(context) }
            OpeningScene(director, art, time.floatValue)
        }
        for (t in listOf(0f, 2920f, 5200f, 6400f, 8000f, 9800f)) {
            compose.runOnIdle { time.floatValue = t }
            compose.waitForIdle()
            val path = File("build/opening/$device-${t.toInt()}.png")
            path.parentFile.mkdirs()
            captureScreenRoboImage(path.path, RoborazziOptions(taskType = RoborazziTaskType.Record))
        }
    }
    @Test @Config(sdk = [26], qualifiers = "w640dp-h360dp-land-480dpi")
    fun s7Frames() = frames("s7")
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletFrames() = frames("tablet")
    @Test @Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-mdpi")
    fun portraitFrames() = frames("portrait")

    @Test @Config(sdk = [34])
    fun theRopeAndEveryJumpStayInsideAllThreeViewports() {
        for ((w, h) in listOf(640f to 360f, 1280f to 800f, 800f to 1280f)) {
            val placement = roomOpeningPlacement(w.dp, h.dp)
            val initial = openingCamera(0f, w, h, placement)
            val ropeX = (placement.stage.left + placement.stage.width() * 1091f / 1200f) * initial.scale + initial.x
            assertTrue("rope $w/$h", ropeX in 32f..(w - 32f))
            for (t in 4480..9800 step 16) {
                val cat = openingCat(t.toFloat(), placement)
                val camera = openingCamera(t.toFloat(), w, h, placement)
                assertTrue("ears $w/$h at $t", (cat.y - cat.scale) * camera.scale + camera.y >= 0f)
                assertTrue("feet $w/$h at $t", cat.y * camera.scale + camera.y <= h)
            }
        }
    }
}
