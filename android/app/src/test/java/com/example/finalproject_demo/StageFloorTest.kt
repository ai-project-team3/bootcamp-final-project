package com.example.finalproject_demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.WorldItem
import com.example.finalproject_demo.demo.coopClaimBackground
import com.example.finalproject_demo.demo.coopUseGeneratedBackground
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.plainFloor
import com.example.finalproject_demo.ui.GroundCache
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.TopChrome
import com.example.finalproject_demo.ui.BottomChrome
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val scopes = mutableListOf<CoroutineScope>()
    private var previousFrozen = false
    @Before fun freezeMotion() { previousFrozen = motionFrozen; motionFrozen = true }
    @After fun restore() { scopes.forEach { it.cancel() }; motionFrozen = previousFrozen }

    private fun snap(name: String) = compose.onRoot().captureRoboImage(
        File("build/floor/$name.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    /** a 「generated」 background with no ground — sky blue top to bottom, as a server picture may come */
    private fun generatedSky(): String {
        val bmp = Bitmap.createBitmap(1344, 768, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF9CC9E8.toInt()) }
        val f = File.createTempFile("floor_bg", ".png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return "local:${f.absolutePath}"
    }

    /** Keep the same source pixels across the horizon, unlike the app's separate felt floor. */
    private fun generatedGround(ground: Int): String {
        val bmp = Bitmap.createBitmap(1344, 768, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(1344 * 768) { i -> if (i / 1344 < 400) 0xFF9CC9E8.toInt() else ground }
        bmp.setPixels(pixels, 0, 1344, 0, 0, 1344, 768)
        val file = File.createTempFile("painted_ground", ".png").apply {
            outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return "local:${file.absolutePath}"
    }

    private fun stage(place: String?, theme: String = "space", generated: String? = null): Director {
        val d = Director(CoroutineScope(SupervisorJob()).also { scopes += it })
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
        val sky = generatedSky()
        val ground = GroundCache.of(sky)!!
        assertEquals("서버가 그린 바닷가", Color(SceneKits.all["beach"]!!.ground), stage("바닷가", generated = sky).s.plainFloor(ground))
        assertEquals("키트 낱말 밖의 곳은 풀밭", Color(SceneKits.all["park"]!!.ground), stage("미래 도시", generated = sky).s.plainFloor(ground))
        assertEquals("우주 그림", Color(SceneKits.all["space"]!!.ground), stage("우주", theme = "space").s.plainFloor())
        assertEquals("바닷속 그림", Color(SceneKits.all["sea"]!!.ground), stage("바닷속", theme = "sea").s.plainFloor())
        assertNull("공룡 나라 그림에는 땅이 그려져 있다", stage("공룡 나라", theme = "dino").s.plainFloor())
    }

    @Test
    fun theFloorUnderAGeneratedBeachAndTheSpacePicture() {
        val image = generatedSky()
        val beach = stage("바닷가", generated = image)
        compose.setContent { StageView(beach) }
        compose.waitUntil(5_000) { GroundCache.peek(image) != null }
        snap("generated_beach")
        val rendered = BitmapFactory.decodeFile("build/floor/generated_beach.png")
        assertTrue("sky-only pictures still need a visible grounding floor",
            rendered.getPixel((rendered.width * 0.88f).toInt(), (rendered.height * 0.90f).toInt()) != 0xFF9CC9E8.toInt())
    }

    @Test
    fun theFloorUnderTheSpacePicture() {
        val space = stage("우주", theme = "space")
        compose.setContent { StageView(space) }
        snap("space")
    }

    private fun preservesPaintedGround(name: String, ground: Int, mode: StoryMode = StoryMode.STORY) {
        val image = generatedGround(ground)
        assertTrue("the fixture contains a ground", GroundCache.of(image)!!.hasGround)
        val director = stage("미래 도시", generated = image).also {
            it.s.mode = mode
            if (mode == StoryMode.COOP) {
                it.s.coopClaimBackground("미래 도시")
                assertTrue(it.s.coopUseGeneratedBackground(image, "미래 도시"))
            }
        }
        compose.setContent { StageView(director) }
        snap(name)
        val rendered = BitmapFactory.decodeFile("build/floor/$name.png")
        // Away from the actor, shadow and frame edges; this is in the added floor's old 18–23% band.
        val frameHeight = if (rendered.width < rendered.height) rendered.width * 768f / 1344f else rendered.height.toFloat()
        val frameTop = if (rendered.width < rendered.height) {
            val density = rendered.width / 800f // portrait fixture below is 800dp wide
            val topInset = TopChrome.value * density
            val room = rendered.height - topInset - BottomChrome.value * density
            topInset + ((room - frameHeight) / 2).coerceAtLeast(0f)
        } else 0f
        assertEquals("the app must not cover the source ground with felt", ground,
            rendered.getPixel((rendered.width * 0.88f).toInt(), (frameTop + frameHeight * 0.90f).toInt()))

        // The contact shadow still grounds the actor without painting an opaque plane across the whole scene.
        val near = if (rendered.width < rendered.height) 0.84f else minOf(0.84f, 1f - BottomChrome.value / 393f)
        val shadow = rendered.getPixel((rendered.width * 0.30f).toInt(), (frameTop + frameHeight * (near + 0.02f)).toInt())
        assertTrue("the actor must keep its existing contact shadow", shadow != ground)
    }

    @Test fun aGeneratedSnowfieldsGroundRemainsVisible() = preservesPaintedGround("snow", 0xFFF2F4F8.toInt())
    @Test fun aGeneratedRoomsFloorRemainsVisible() = preservesPaintedGround("room", 0xFFB07A48.toInt())
    @Test fun aGeneratedBeachRemainsVisible() = preservesPaintedGround("sand", 0xFFE8D2A0.toInt())
    @Test fun coopsGeneratedGroundIsNotCovered() = preservesPaintedGround("coop_snow", 0xFFF2F4F8.toInt(), StoryMode.COOP)

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-160dpi")
    fun portraitFramesKeepThePicturesGround() = preservesPaintedGround("portrait_snow", 0xFFF2F4F8.toInt())

    @Test fun aPendingGroundReadDoesNotFlashATemporaryParkFloor() {
        assertNull(stage("미래 도시", generated = generatedGround(0xFFF2F4F8.toInt())).s.plainFloor())
    }
}
