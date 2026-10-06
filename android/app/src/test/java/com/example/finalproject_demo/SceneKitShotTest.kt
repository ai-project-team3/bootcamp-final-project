package com.example.finalproject_demo

import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.WorldItem
import com.example.finalproject_demo.demo.scene.FRIEND_SPOT
import com.example.finalproject_demo.demo.scene.HERO_SPOT
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The felt scene kit on the live story stage (10-05 · `docs/배경_조각_목록.md`): the park kit with the hero and a
 * friend at the live story's spots. `screens/world_kit_park.png` is for looking, not a reference image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class SceneKitShotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun parkKitWithTwoActors() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = "dino"; d.s.generatedBg = true; d.s.placeLabel = "공원"
        d.s.sceneKit = "park"; d.s.sceneSeed = 2026L
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(
            WorldItem(Art.HeroArt(attr), HERO_SPOT.x, 0.32f, 0.11f, depth = HERO_SPOT.depth),
            WorldItem(Art.Img("dino_long", Art.Emoji("🦕")), FRIEND_SPOT.x, 0.32f, 0.13f, depth = FRIEND_SPOT.depth),
        ))
        compose.setContent { StageView(d) }
        compose.onRoot().captureRoboImage(
            File("screens/world_kit_park.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    /** The table's `aspect` must match the baked webp — the layout scores overlaps with it */
    /** 공룡 나라 (#97 2순위 · 10-06) — the same two actors; `screens/world_kit_dino.png` is for looking */
    @Test
    fun dinoKitWithTwoActors() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = "dino"; d.s.generatedBg = true; d.s.placeLabel = "공룡 나라"
        d.s.sceneKit = "dino"; d.s.sceneSeed = 2026L
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(
            WorldItem(Art.HeroArt(attr), HERO_SPOT.x, 0.32f, 0.11f, depth = HERO_SPOT.depth),
            WorldItem(Art.Img("dino_long", Art.Emoji("🦕")), FRIEND_SPOT.x, 0.32f, 0.13f, depth = FRIEND_SPOT.depth),
        ))
        compose.setContent { StageView(d) }
        compose.onRoot().captureRoboImage(
            File("screens/world_kit_dino.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    /** 우주 (#97 2순위 · 10-06) — `screens/world_kit_space.png` is for looking */
    @Test
    fun spaceKitWithTwoActors() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = "space"; d.s.generatedBg = true; d.s.placeLabel = "우주"
        d.s.sceneKit = "space"; d.s.sceneSeed = 2026L
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(
            WorldItem(Art.HeroArt(attr), HERO_SPOT.x, 0.32f, 0.11f, depth = HERO_SPOT.depth),
            WorldItem(Art.Img("dino_long", Art.Emoji("🦕")), FRIEND_SPOT.x, 0.32f, 0.13f, depth = FRIEND_SPOT.depth),
        ))
        compose.setContent { StageView(d) }
        compose.onRoot().captureRoboImage(
            File("screens/world_kit_space.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    /** 바닷속 (#97 2순위 · 10-06) — `screens/world_kit_sea.png` is for looking */
    @Test
    fun seaKitWithTwoActors() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = "sea"; d.s.generatedBg = true; d.s.placeLabel = "바닷속"
        d.s.sceneKit = "sea"; d.s.sceneSeed = 2026L
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(
            WorldItem(Art.HeroArt(attr), HERO_SPOT.x, 0.32f, 0.11f, depth = HERO_SPOT.depth),
            WorldItem(Art.Img("dino_long", Art.Emoji("🦕")), FRIEND_SPOT.x, 0.32f, 0.13f, depth = FRIEND_SPOT.depth),
        ))
        compose.setContent { StageView(d) }
        compose.onRoot().captureRoboImage(
            File("screens/world_kit_sea.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    @Test
    fun aspectsInTheTableMatchThePictures() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (kit in SceneKits.all.values) for (p in kit.pieces) {
            val id = ctx.resources.getIdentifier(p.res, "drawable", ctx.packageName)
            assertNotEquals("missing drawable ${p.res}", 0, id)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(ctx.resources, id, o)
            assertEquals(p.res, o.outWidth.toFloat() / o.outHeight, p.aspect, 0.01f)
        }
    }
}
