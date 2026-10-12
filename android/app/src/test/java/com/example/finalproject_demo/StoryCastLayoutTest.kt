package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-mdpi")
class StoryCastLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phoneStageAndReopenedBookKeepThreeCharactersVisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageStore = StoryImageStore(context)
        // Reuse a local asset as a deterministic generated-PNG fixture; no model call in a layout test.
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.nc_alien)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val actor = GeneratedFriend("괴물", imageStore.save(png)!!, "blob", "problem")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val before = motionFrozen
        try {
            motionFrozen = true
            val d = Director(scope).apply {
                s.mode = StoryMode.STORY; s.templateKey = "A"
                s.storyBackground = "bg_park"; s.sceneKit = "park"; s.sceneSeed = 42L
                s.slots["problem"] = "괴물이 길을 막았어"; s.slotBy["problem"] = "child"
                s.slots["newcomer"] = "토끼"; s.newcomerKind = "토끼"
                s.drawing += Stroke(Color.Blue, listOf(Offset(.3f, .2f), Offset(.4f, .6f), Offset(.7f, .6f), Offset(.7f, .2f)), .03f)
                s.generatedCharacters += actor
                s.stage = s.storyConversationWorld()
            }
            val showing = mutableStateOf(d)
            compose.setContent { StageView(showing.value) }
            compose.onRoot().captureRoboImage("build/review/story_cast_world.png",
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val book = SavedStoryBook("cast-layout", "함께 길을 찾았어요", "dino", "bg_park",
                listOf(SavedStoryPage(PageKind.TALK, "토끼와 괴물이 함께 길을 찾았어요.")), d.s.captureStoryVisuals().copy(liveStory = true))
            val reopened = Director(scope)
            assertTrue(reopened.s.restoreStoryBook(book))
            assertEquals(actor, reopened.s.storyProblemDoll())
            reopened.s.stage = Stage.SavedStory(book, 1)
            showing.value = reopened
            compose.onNodeWithText(book.pages.single().caption).assertExists()
            compose.onRoot().captureRoboImage("build/review/story_cast_reopened.png",
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally { scope.cancel(); motionFrozen = before }
    }
}
