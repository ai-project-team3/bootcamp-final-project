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

    @Test fun settingsStartCollapsedAndLeaveCumulativeRecordsInTheReport() {
        AnswerHistory.setChildAge(ChildAge(4, 1))
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithText("호칭 · 콩이").assertExists()
        compose.onNodeWithText("나이 · 만 4세 6~11개월").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("아이 만 나이 · 선택").assertDoesNotExist()
        compose.onNodeWithText("동화 말 기록").assertDoesNotExist()
        compose.onNodeWithText("입력한 나이의 연구 참고").assertDoesNotExist()
    }

    @Test fun reportKeepsBookThenCumulativeThenResearchAndOffersAgeSettings() {
        compose.setContent { ParentView(Director(scope), "rec") }
        compose.onNodeWithText("이번 동화의 말 기록").assertExists()
        compose.onNodeWithText("동화 말 기록").assertExists()
        compose.onNodeWithText("입력한 나이의 연구 참고").assertExists()
        val book = compose.onNodeWithText("이번 동화의 말 기록").fetchSemanticsNode().positionInRoot.y
        val history = compose.onNodeWithText("동화 말 기록").fetchSemanticsNode().positionInRoot.y
        val research = compose.onNodeWithText("입력한 나이의 연구 참고").fetchSemanticsNode().positionInRoot.y
        assertTrue(book < history && history < research)
        compose.onNodeWithText("설정에서 나이 고치기").performScrollTo().assertIsDisplayed()
    }

    @Test fun resettingTheHistoryNeedsConfirmationAndPreservesBooksReports() {
        AnswerHistory.setChildAge(ChildAge(4, 1))
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithText("아이 기록 초기화").performScrollTo().performClick()
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNodeWithText("그대로 둘게요").performClick()
        assertEquals(ChildAge(4, 1), AnswerHistory.childAge)
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNodeWithText("아이 기록 초기화").performScrollTo().performClick()
        compose.onNodeWithText("네, 초기화할게요").performClick()
        assertTrue(AnswerHistory.languageAnswers().isEmpty())
        assertNull(AnswerHistory.childAge)
        assertEquals("콩이", ChildCall.call)
        assertNotNull(SessionReports.of("book"))
        compose.onNodeWithText("아이 기록 초기화").assertExists()
        val destination = java.io.File(System.getProperty("java.io.tmpdir"), "otto-history-settings.png")
        compose.onRoot().captureRoboImage(destination.absolutePath,
            com.github.takahirom.roborazzi.RoborazziOptions(
                taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
    }

    @Test
    @Config(sdk = [26], qualifiers = "w640dp-h360dp-land-240dpi")
    fun smallLandscapeReportCanReachHistoryAndAgeSettings() {
        compose.setContent { ParentView(Director(scope), "rec") }
        compose.onNodeWithText("동화 말 기록").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("놀이 대화 기록의 요약이에요.").onLast().performScrollTo()
        compose.onRoot().captureRoboImage(java.io.File("build/report/child-history-640x360.png").also { it.parentFile?.mkdirs() }.path,
            com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        compose.onNodeWithText("설정에서 나이 고치기").performScrollTo().assertIsDisplayed()
    }

    @Test fun ageCanBeErasedWithoutResettingUnreadableHistory() {
        AnswerHistory.setChildAge(ChildAge(4, 1))
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("answer_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", "not-json").commit()
        AnswerHistory.reload()
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithContentDescription("나이 고치기").performScrollTo().performClick()
        compose.onNodeWithText("나이 정보 지우기").performScrollTo().performClick()
        assertNull(AnswerHistory.childAge)
        assertFalse(prefs.contains("child_age"))
        assertEquals("not-json", prefs.getString("sessions", null))
        assertTrue(AnswerHistory.readFailed)
    }

    @Test fun unreadableHistoryIsShownAndRequiresAnExplicitReset() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("answer_history", android.content.Context.MODE_PRIVATE)
            .edit().putString("sessions", "[{broken-history]").commit()
        AnswerHistory.reload()
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithText("말 기록을 읽지 못했어요. 기존 자료는 보존했고, 새 기록 저장과 책 삭제는 잠시 멈췄어요.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("질문별 말 기록 · 0권 누적").assertDoesNotExist()
        compose.onRoot().captureRoboImage(java.io.File(System.getProperty("java.io.tmpdir"), "otto-history-read-error.png").absolutePath,
            com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        compose.onNodeWithText("아이 기록 초기화").performScrollTo().performClick()
        assertTrue(AnswerHistory.readFailed)
        compose.onNodeWithText("네, 초기화할게요").performClick()
        assertFalse(AnswerHistory.readFailed)
        compose.onNodeWithText("말 기록을 읽지 못했어요.", substring = true).assertDoesNotExist()
        assertTrue(AnswerHistory.all().isEmpty())
    }
    @Test fun changingACallKeepsHistoryOnlyAfterTheGuardianChoosesSameChild() {
        compose.setContent { ParentView(Director(scope), "set") }
        compose.onNodeWithContentDescription("호칭 고치기").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("별님")
        compose.onNodeWithText("저장").performScrollTo().performClick()
        assertEquals("콩이", ChildCall.name)
        compose.onNodeWithText("같은 아이예요").performScrollTo().performClick()
        assertEquals("별님", ChildCall.name)
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNode(hasSetTextAction()).performTextReplacement("꼬마")
        compose.onNodeWithText("저장").performScrollTo().performClick()
        compose.onNodeWithText("다른 아이가 써요").performScrollTo().performClick()
        assertEquals("별님", ChildCall.name)
        assertEquals(1, AnswerHistory.languageAnswers().size)
        compose.onNodeWithText("지금까지의 말 기록과 나이를 지워요. 만든 책과 책별 리포트는 그대로 있어요.").assertExists()
        compose.onNodeWithText("네, 초기화할게요").performClick()
        assertEquals("꼬마", ChildCall.name)
        assertTrue(AnswerHistory.languageAnswers().isEmpty())
        assertNotNull(SessionReports.of("book"))
    }
}
