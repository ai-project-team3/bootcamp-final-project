package com.example.finalproject_demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.ChildCall
import com.example.finalproject_demo.ui.ParentView
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render the production shelf and parent screens on API 26 with synthetic authors only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26], qualifiers = "w868dp-h411dp-land-420dpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SavedAuthorCallViewTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/review/saved-author-478/${name}_api26.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun savedStoryCoverUsesStoredAuthorAndLegacyCoverUsesGenericChild() {
        val old = ChildCall.name
        val frozen = motionFrozen
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            motionFrozen = true
            ChildCall.set("단아")
            val made = DemoState().apply { resetStory(); templateKey = "C"; title = "콩이의 모험"; storyHeroCall = "콩이" }
            var book by mutableStateOf(made.completedStoryBook()!!)
            ChildCall.set("보람")
            val d = Director(scope).apply { s.title = "진행 중인 책"; s.slots["place"] = "바닷속" }
            val state = d.s.captureDraft(Scene.CAUSE, 1).toJson().toString()
            compose.setContent { SavedStoryView(d, Stage.SavedStory(book, 0)) }
            compose.onNodeWithText("글 · 그림 단아").assertExists()
            compose.onNodeWithText("글 · 그림 보람").assertDoesNotExist()
            shot("story_cover_known")
            compose.runOnIdle { book = book.copy(authorCall = null) }
            compose.onNodeWithText("글 · 그림 아이").assertExists()
            compose.onNodeWithText("글 · 그림 보람").assertDoesNotExist()
            shot("story_cover_legacy")
            assertEquals(state, d.s.captureDraft(Scene.CAUSE, 1).toJson().toString())
            assertEquals("보람", ChildCall.call)
        } finally { scope.cancel(); motionFrozen = frozen; ChildCall.set(old.orEmpty()) }
    }

    @Test fun parentReportAndTranscriptUseSavedAuthorWhileHeaderKeepsCurrentChild() {
        val old = ChildCall.name
        val frozen = motionFrozen
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            motionFrozen = true
            ChildCall.set("단아")
            val made = DemoState().apply {
                resetStory(); templateKey = "C"; title = "콩이의 모험"
                talk += TalkLine("otto", "어디서 놀았어?")
                talk += TalkLine("child", "공원에서 놀았어")
            }
            val report = made.buildSessionReport("synthetic-book")
            ChildCall.set("보람")
            val d = Director(scope).apply { s.lastReport = report }
            compose.setContent { ParentView(d, "rec") }
            compose.onNodeWithText("보람네 기록 · 어른만").assertExists()
            compose.onNodeWithText("오늘 단아가 만든 이야기").assertExists()
            compose.onNodeWithText("오늘 보람이 만든 이야기").assertDoesNotExist()
            shot("parent_report_known")
            compose.onNodeWithText("대화 전체 보기 ›").performScrollTo().performClick()
            compose.onNodeWithText("단아가 말함").assertExists()
            compose.onNodeWithText("보람이 말함").assertDoesNotExist()
            shot("parent_talk_known")
            assertEquals("보람", ChildCall.call)
        } finally { scope.cancel(); motionFrozen = frozen; ChildCall.set(old.orEmpty()) }
    }

    @Test fun legacyParentReportAndTranscriptKeepTheAuthorUnknown() {
        val old = ChildCall.name
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            ChildCall.set("보람")
            val report = DemoState().apply {
                title = "옛 이야기"
                talk += TalkLine("child", "그대로 저장된 말")
            }.buildSessionReport("legacy-synthetic").copy(authorCall = null)
            val d = Director(scope).apply { s.lastReport = report }
            compose.setContent { ParentView(d, "rec") }
            compose.onNodeWithText("오늘 아이가 만든 이야기").assertExists()
            compose.onNodeWithText("오늘 보람이 만든 이야기").assertDoesNotExist()
            shot("parent_report_legacy")
            compose.onNodeWithText("대화 전체 보기 ›").performScrollTo().performClick()
            compose.onNodeWithText("아이가 말함").assertExists()
            compose.onNodeWithText("보람이 말함").assertDoesNotExist()
            shot("parent_talk_legacy")
            assertEquals("보람", ChildCall.call)
        } finally { scope.cancel(); ChildCall.set(old.orEmpty()) }
    }
}
