package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.sound.ChildSound
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
import java.nio.file.Files
import com.example.finalproject_demo.net.Voice
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SavedStorySoundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aChildSoundWaitsForTheActualSavedStoryNarration() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val d = Director(scope)
        val book = DemoState().apply { templateKey = "C" }.completedStoryBook()!!.copy(soundClipId = "clip")
        val previousRoot = ChildSound.root
        val previousFrozen = motionFrozen
        val folder = Files.createTempDirectory("saved_story_voice_order").toFile()
        val clip = java.io.File(folder, "books/${book.id}/clip.wav").apply {
            parentFile!!.mkdirs(); writeBytes(Voice.wav(ShortArray(16_000)))
        }
        val players = mutableListOf<ShadowMediaPlayer>()
        val narration = Job().also { d.queueVoice(it) }
        // Match enqueue's current voice job without requiring a live TTS provider in Robolectric.
        org.robolectric.util.ReflectionHelpers.setField(d, "voiceJob", narration)
        try {
            ChildSound.root = folder
            motionFrozen = true
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(clip.path), ShadowMediaPlayer.MediaInfo(60_000, 0))
            ShadowMediaPlayer.setCreateListener { _, player -> players += player }
            compose.setContent { SavedStoryView(d, Stage.SavedStory(book, 1)) }
            compose.onNodeWithText("🔊 내가 만든 소리").performClick()
            compose.waitForIdle()
            assertTrue("the child's recording must wait for the actual narration queue", players.isEmpty())
            compose.runOnIdle { narration.complete() }
            compose.waitUntil(3_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                players.isNotEmpty()
            }
            compose.runOnIdle { players.single().invokeCompletionListener() }
        } finally {
            narration.cancel(); scope.cancel()
            ShadowMediaPlayer.setCreateListener(null)
            ChildSound.root = previousRoot; motionFrozen = previousFrozen
            folder.deleteRecursively()
        }
    }

    @Test fun aLegacyBooksSoundButtonIsVisibleAboveItsBackground() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val previousFrozen = motionFrozen
        val imageFile = Files.createTempFile("legacy_sound_control", ".png").toFile()
        try {
            motionFrozen = true
            val book = DemoState().apply { templateKey = "C" }.completedStoryBook()!!
                .copy(visuals = null, soundClipId = "local-clip")
            compose.setContent { SavedStoryView(Director(scope), Stage.SavedStory(book, 1)) }
            val bounds = compose.onNodeWithText("🔊 내가 만든 소리").fetchSemanticsNode().boundsInRoot
            compose.onRoot().captureRoboImage(imageFile.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val pixels = BitmapFactory.decodeFile(imageFile.path)
            var visibleButtonPixels = 0
            var total = 0
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    val color = pixels.getPixel(x, y)
                    if (Color.red(color) > 178 && Color.green(color) > 102 && Color.blue(color) < 102) visibleButtonPixels++
                    total++
                }
            }
            assertTrue("The sound control must be painted above the legacy book background",
                visibleButtonPixels > total / 3)
        } finally {
            motionFrozen = previousFrozen
            scope.cancel()
            imageFile.delete()
        }
    }

    @Test fun aReopenedBookOffersItsSoundWithoutUsingTheCurrentStory() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val d = Director(scope)
        val book = DemoState().apply { templateKey = "C" }.completedStoryBook()!!.copy(soundClipId = "missing-clip")
        val previousRoot = ChildSound.root
        val previousFrozen = motionFrozen
        val folder = Files.createTempDirectory("missing_story_sound").toFile()
        ChildSound.root = folder
        try {
            motionFrozen = true
            d.s.soundLine = "진행 중 이야기"
            compose.setContent { SavedStoryView(d, Stage.SavedStory(book, 1)) }
            compose.onNodeWithText("🔊 내가 만든 소리").assertExists().performClick()
            compose.onNodeWithText("소리가 남아 있지 않아").assertExists()
            compose.onNodeWithText(book.pages.first().caption).assertExists()
            assertEquals("진행 중 이야기", d.s.soundLine)
        } finally {
            ChildSound.root = previousRoot
            motionFrozen = previousFrozen
            scope.cancel()
            folder.deleteRecursively()
        }
    }
}
