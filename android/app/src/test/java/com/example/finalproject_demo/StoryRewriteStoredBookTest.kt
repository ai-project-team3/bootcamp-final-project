package com.example.finalproject_demo

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26], qualifiers = "w640dp-h360dp-land-mdpi")
class StoryRewriteStoredBookTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theFullCompoundEndingSurvivesSavingReopeningAndPhoneRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalStoryBookStore(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val before = motionFrozen
        try {
            motionFrozen = true
            val source = DemoState().apply { templateKey = "E"; title = "시장에서 만난 친구" }
            source.slots["solution"] = "문을 닫았어"; source.slotBy["solution"] = "child"
            source.recordTemplateAnswer(StoryPrompt("resolve", "", true), "시장으로 돌아갔고 장사는 그만뒀어", "child")
            source.recordTemplateAnswer(StoryPrompt("goal", "", true), "내일은 다시 놀러 가고 싶어", "child")
            val book = source.completedStoryBook()!!
            assertTrue(book.pages.last().caption.contains("시장으로 돌아갔고 장사는 그만뒀어"))
            assertTrue(book.pages.last().caption.contains("내일은 다시 놀러 가고 싶어"))
            store.save(book)
            val loaded = store.load().single { it.id == book.id }
            assertEquals(book.pages, loaded.pages)
            val reopened = Director(scope)
            assertTrue(reopened.s.restoreStoryBook(loaded))
            reopened.s.stage = Stage.SavedStory(loaded, loaded.pages.size)
            compose.setContent { StageView(reopened) }
            compose.onNodeWithText(loaded.pages.last().caption).assertExists()
            compose.onRoot().captureRoboImage("build/review/story_rewrite_ending_api26.png",
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally { scope.cancel(); motionFrozen = before }
    }
}
