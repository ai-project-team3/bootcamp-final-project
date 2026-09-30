package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class SavedStorySoundTest {
    @get:Rule val compose = createComposeRule()

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
