package com.example.finalproject_demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.WorldItem
import com.example.finalproject_demo.demo.scene.FRIEND_SPOT
import com.example.finalproject_demo.demo.scene.HERO_SPOT
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.LocalSceneTime
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * A reel of the living background (doc §6-3) for looking at without a phone: the park kit drawn at pinned
 * times ([LocalSceneTime]), one PNG a frame. **Only runs when `OTTO_SCENE_REEL` names a folder** — it is a
 * tool, not a check (the checks are `SceneMotionTest`). `tools/scene_reel.py` joins the frames into a GIF.
 *
 *   OTTO_SCENE_REEL=/tmp/reel ./gradlew testDebugUnitTest --tests '*SceneReelTest'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class SceneReelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun parkKitReel() {
        val out = System.getenv("OTTO_SCENE_REEL")
        assumeTrue("set OTTO_SCENE_REEL to a folder to record the reel", !out.isNullOrBlank())
        val fps = (System.getenv("OTTO_SCENE_REEL_FPS") ?: "12").toInt()
        val seconds = (System.getenv("OTTO_SCENE_REEL_S") ?: "10").toInt()
        val dir = File(out!!).apply { mkdirs() }

        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = "dino"; d.s.generatedBg = true; d.s.placeLabel = "공원"
        d.s.sceneKit = "park"; d.s.sceneSeed = (System.getenv("OTTO_SCENE_REEL_SEED") ?: "2026").toLong()
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(
            WorldItem(Art.HeroArt(attr), HERO_SPOT.x, 0.32f, 0.11f, depth = HERO_SPOT.depth),
            WorldItem(Art.Img("dino_long", Art.Emoji("🦕")), FRIEND_SPOT.x, 0.32f, 0.13f, depth = FRIEND_SPOT.depth),
        ))
        var t by mutableStateOf(0.0)
        compose.setContent { CompositionLocalProvider(LocalSceneTime provides t) { StageView(d) } }
        for (i in 0 until fps * seconds) {
            t = i / fps.toDouble()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(
                File(dir, "frame_%04d.png".format(i)).path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
        }
    }
}
