package com.example.finalproject_demo

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.ArtView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w512dp-h512dp-160dpi")
class StoryPresetArtTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reopeningTheChosenMeteorKeepsItsFeltPictureInsteadOfAFlatCircle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val state = DemoState().apply {
            templateKey = "C"
            newcomerKind = "운석"
            drawnPreset = 1
            title = "운석 친구"
        }
        LocalStoryBookStore(context).save(state.completedStoryBook()!!)
        val reader = DemoState()
        assertTrue(reader.restoreStoryBook(LocalStoryBookStore(context).load().single()))
        compose.setContent {
            Box(Modifier.size(256.dp).testTag("friend-picture")) {
                ArtView(reader.friendArt, Modifier.size(256.dp))
            }
        }
        val image = File("build/qa/story-meteor.png").apply { parentFile.mkdirs() }
        compose.onNodeWithTag("friend-picture").captureRoboImage(image.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val pixels = BitmapFactory.decodeFile(image.absolutePath)
        val colors = mutableSetOf<Int>()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) colors += pixels.getPixel(x, y)
        assertTrue("the felt resource must replace the few flat colors of the circle: ${colors.size}", colors.size > 1_000)
    }
}
