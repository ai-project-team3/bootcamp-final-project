package com.example.finalproject_demo

import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.ChildCall
import com.example.finalproject_demo.ui.ParentView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1600dp-port-240dpi")
class AnswerHistorySettingsTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob())

    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        SessionReports.attach(context); SessionReports.clear()
        ChildCall.attach(context); ChildCall.set("콩이")
        val state = DemoState()
        state.talk += TalkLine("child", "집에 갔어", questionType = ReportQuestionType.OPEN)
        SessionReports.keep(state, "book")
    }

    @After fun clean() { SessionReports.clear(); ChildCall.reset(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    @Test fun resettingTheHistoryNeedsConfirmationAndPreservesBooksReports() {
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithText("말 기록 새로 시작").performScrollTo().performClick()
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNodeWithText("네, 새로 시작할게요").performScrollTo().performClick()
        assertTrue(AnswerHistory.languageAnswers().isEmpty())
        assertNotNull(SessionReports.of("book"))
        compose.onNodeWithText("말 기록 새로 시작").assertExists()
        val destination = java.io.File("C:/Users/hi/Documents/Codex/2026-10-09/report-followups/history-settings.png")
        compose.onRoot().captureRoboImage(destination.absolutePath,
            com.github.takahirom.roborazzi.RoborazziOptions(
                taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
    }

    @Test fun changingACallKeepsHistoryOnlyAfterTheGuardianChoosesSameChild() {
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNode(hasSetTextAction()).performTextReplacement("별님")
        compose.onNodeWithText("저장").performScrollTo().performClick()
        assertEquals("콩이", ChildCall.name)
        compose.onNodeWithText("같은 아이예요").performScrollTo().performClick()
        assertEquals("별님", ChildCall.name)
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNode(hasSetTextAction()).performTextReplacement("꼬마")
        compose.onNodeWithText("저장").performScrollTo().performClick()
        compose.onNodeWithText("다른 아이가 써요").performScrollTo().performClick()
        assertEquals("꼬마", ChildCall.name)
        assertTrue(AnswerHistory.languageAnswers().isEmpty())
        assertNotNull(SessionReports.of("book"))
    }
}