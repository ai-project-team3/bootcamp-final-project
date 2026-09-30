package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SavedStoryArtTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rereadingUsesTheSavedHeroPngWithoutChangingTheCurrentSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xFF123456.toInt())
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        val savedState = DemoState().apply {
            templateKey = "C"
            storyHeroImage = StoryImageStore(context).save(png)
            storyHeroRig = "human"
            useGeneratedStory(template!!.pages.indices.map { "${it + 1}쪽 기억할 문장" })
            m1Result = "solo"; m2Result = "solo"
        }
        val book = savedState.completedStoryBook()!!
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val director = Director(scope)
        director.s.storyHeroImage = "local:current-session.png"
        val frozenBefore = motionFrozen
        try {
            motionFrozen = true
            compose.setContent { SavedStoryView(director, Stage.SavedStory(book, 1)) }
            compose.onNodeWithText(book.pages.first().caption).assertExists()
            val screenshot = File("build/review/saved_story_hero.png")
            compose.onRoot().captureRoboImage(screenshot.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val pixels = BitmapFactory.decodeFile(screenshot.path)
            var heroPixels = 0
            for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
                if (pixels.getPixel(x, y) == 0xFF123456.toInt()) heroPixels++
            }
            assertTrue("the saved generated character must actually appear on the page", heroPixels > 20)
            assertEquals("local:current-session.png", director.s.storyHeroImage)
        } finally {
            motionFrozen = frozenBefore
            scope.cancel()
        }
    }
}
