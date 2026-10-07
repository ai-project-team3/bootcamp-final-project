package com.example.finalproject_demo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.WorldItem
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.plainFloor
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「바닥이 됐다 안 됐다」 (10-06 조장 · #223) — 바닥은 배경 키트 무대에만 있어서, 서버가 그린 배경 · 우주 · 바닷속 그림 ·
 * 협업에서는 인물이 공중에 떠 있었다. 땅이 없는 그림에는 그 곳의 키트 땅 색으로 펠트 바닥을 깐다.
 * 그림은 build/ 아래에만 남긴다 — 보는 용도다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class StageFloorTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String) = compose.onRoot().captureRoboImage(
        File("build/floor/$name.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    /** a 「generated」 background with no ground — sky blue top to bottom, as a server picture may come */
    private fun generatedSky(): String {
        val bmp = Bitmap.createBitmap(1344, 768, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF9CC9E8.toInt()) }
        val f = File.createTempFile("floor_bg", ".png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return "local:${f.absolutePath}"
    }

    private fun stage(place: String?, theme: String = "space", generated: String? = null): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.themeKey = theme
        d.s.placeLabel = place
        d.s.storyBackground = generated
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(listOf(WorldItem(Art.HeroArt(attr), 0.30f, 0.32f, 0.11f, depth = 1f)))
        return d
    }

    @Test
    fun aPictureWithNoGroundGetsTheFloorOfItsPlace() {
        assertEquals("서버가 그린 바닷가", Color(SceneKits.all["beach"]!!.ground), stage("바닷가", generated = generatedSky()).s.plainFloor())
        assertEquals("키트 낱말 밖의 곳은 풀밭", Color(SceneKits.all["park"]!!.ground), stage("미래 도시", generated = generatedSky()).s.plainFloor())
        assertEquals("우주 그림", Color(SceneKits.all["space"]!!.ground), stage("우주", theme = "space").s.plainFloor())
        assertEquals("바닷속 그림", Color(SceneKits.all["sea"]!!.ground), stage("바닷속", theme = "sea").s.plainFloor())
        assertNull("공룡 나라 그림에는 땅이 그려져 있다", stage("공룡 나라", theme = "dino").s.plainFloor())
    }

    @Test
    fun theFloorUnderAGeneratedBeachAndTheSpacePicture() {
        val beach = stage("바닷가", generated = generatedSky())
        compose.setContent { StageView(beach) }
        snap("generated_beach")
    }

    @Test
    fun theFloorUnderTheSpacePicture() {
        val space = stage("우주", theme = "space")
        compose.setContent { StageView(space) }
        snap("space")
    }
}
