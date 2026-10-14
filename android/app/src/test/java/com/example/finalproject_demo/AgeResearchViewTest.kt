package com.example.finalproject_demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.*
import com.github.takahirom.roborazzi.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalRoborazziApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1000dp-port-240dpi")
class AgeResearchViewTest {
    @get:Rule val compose = createComposeRule()
    @Before fun setup() { SessionReports.attach(RuntimeEnvironment.getApplication()); SessionReports.clear() }
    @After fun cleanup() = SessionReports.clear()

    @Test fun guardianCanSaveAndRemoveAgeWithoutARequiredFieldOrNetwork() {
        compose.setContent { ChildAgeSection() }
        compose.onNodeWithText("동의하고 나이 저장").assertDoesNotExist()
        compose.onNodeWithText("만 4세").performClick()
        assertEquals(ChildAge(4, null), AnswerHistory.childAge)
        compose.onNodeWithText("6~11개월").performClick()
        assertEquals(ChildAge(4, 1), AnswerHistory.childAge)
        compose.onNodeWithText("나이를 이 기기에 저장했어요.").assertExists()
        compose.onRoot().captureRoboImage(File("build/report/age-settings.png").also { it.parentFile?.mkdirs() }.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("나이 정보 지우기").performClick()
        assertNull(AnswerHistory.childAge)
    }

    @Test fun researchNumbersAreHiddenBeforeFiftyEligibleAnswers() {
        AnswerHistory.setChildAge(ChildAge(4, 1))
        compose.setContent { AgeResearchView(49) }
        compose.onNodeWithText("기록 쌓는 중", substring = true).assertExists()
        compose.onNodeWithText("연구 대화의 발화당 평균", substring = true).assertDoesNotExist()
    }

    @Test fun publishedNumbersKeepTheirUnitsAndIncompatibilityWarning() {
        AnswerHistory.setChildAge(ChildAge(4, 1))
        compose.setContent { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AgeResearchView(50) } }
        compose.onNodeWithText("연구 대화의 발화당 평균 4.03어절 · 표준편차 1.16").assertExists()
        compose.onNodeWithText("숫자를 직접 비교할 수 없어요.", substring = true).assertExists()
        compose.onNodeWithText("출처: 이윤경·임수아(2025), 표 2").assertExists()
        compose.onRoot().captureRoboImage(File("build/report/age-reference.png").also { it.parentFile?.mkdirs() }.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun deletingABookOrChangingChildRemovesTheReferenceNumbers() {
        AnswerHistory.setChildAge(ChildAge(3, 0))
        val s = DemoState()
        repeat(50) { s.talk += TalkLine("child", "곰이 집에 갔어", questionType = ReportQuestionType.OPEN) }
        SessionReports.keep(s, "fifty-answers")
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { StorySpeechHistoryView() } }
        compose.onNodeWithText("연구 대화의 발화당 평균", substring = true).assertExists()
        SessionReports.forget("fifty-answers")
        compose.onNodeWithText("연구 대화의 발화당 평균", substring = true).assertDoesNotExist()
        AnswerHistory.startNewChild()
        assertNull(AnswerHistory.childAge)
    }

    @Test fun unsupportedAgeDoesNotBorrowAFiveYearOldReference() {
        AnswerHistory.setChildAge(ChildAge(6, null))
        compose.setContent { AgeResearchView(0) }
        compose.onNodeWithText("만 6~7세는 이 화면에서 제공하는 연구 참고값이 아직 없어요. 말 기록은 그대로 볼 수 있어요.").assertExists()
        compose.onNodeWithText("기록 쌓는 중", substring = true).assertDoesNotExist()
        compose.onNodeWithText("연구 대화의 발화당 평균", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-240dpi")
    fun smallLandscapeSetupHasOnlyRequiredPreferencesAndCanFinish() {
        var finished = false
        compose.setContent { com.example.finalproject_demo.ui.shell.SetupStep({}, { finished = true }) }
        compose.onNodeWithText("2권").performScrollTo().performClick()
        compose.onNodeWithText("펠트").performScrollTo().performClick()
        compose.onNodeWithText("받지 않아요").performScrollTo().performClick()
        compose.onNodeWithText("만 4세").assertDoesNotExist()
        compose.onRoot().captureRoboImage(File("build/report/age-setup-small.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("설정 끝").performClick()
        assertTrue(finished)
        assertNull(AnswerHistory.childAge)
    }

    @Test fun missingAgeOffersSettingsBeforeFiftyAnswers() {
        var opened = false
        compose.setContent { AgeResearchView(0, onSettings = { opened = true }) }
        compose.onNodeWithText("설정에서 나이 고치기").performClick()
        assertTrue(opened)
        compose.onNodeWithText("기록 쌓는 중", substring = true).assertDoesNotExist()
    }

    @Test fun choosingSevenImmediatelyExplainsTheMissingResearch() {
        compose.setContent { ChildAgeSection() }
        compose.onNodeWithText("만 7세").performClick()
        assertEquals(ChildAge(7, null), AnswerHistory.childAge)
        compose.onNodeWithText("만 6~7세는 이 화면에서 제공하는 연구 참고값이 아직 없어요. 말 기록은 그대로 볼 수 있어요.").assertExists()
    }

    @Test
    @Config(sdk = [26], qualifiers = "w640dp-h360dp-land-240dpi")
    fun smallLandscapeIntroductionCanReachSevenAndItsNoticeAboveBothActions() {
        compose.setContent { com.example.finalproject_demo.ui.shell.ChildIntroductionStep({}, {}) }
        compose.onNodeWithText("만 7세").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("만 6~7세는 이 화면에서 제공하는 연구 참고값이 아직 없어요. 말 기록은 그대로 볼 수 있어요.")
            .assertIsDisplayed()
        val notice = compose.onNodeWithText("만 6~7세는 이 화면에서 제공하는 연구 참고값이 아직 없어요. 말 기록은 그대로 볼 수 있어요.").getUnclippedBoundsInRoot()
        val next = compose.onNodeWithText("다음").getUnclippedBoundsInRoot()
        assertTrue("안내가 진행 버튼에 가렸다", notice.bottom <= next.top)
        compose.onNodeWithText("다음").assertIsDisplayed()
        compose.onNodeWithText("건너뛰기").assertIsDisplayed()
        compose.onRoot().captureRoboImage(File("build/report/child-introduction-seven-640x360.png").also { it.parentFile?.mkdirs() }.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        assertEquals(ChildAge(7, null), AnswerHistory.childAge)
    }

    @Test
    @Config(sdk = [26], qualifiers = "w640dp-h360dp-land-240dpi")
    fun introductionCanSkipBlankFieldsAndKeepsBothActionsVisible() {
        com.example.finalproject_demo.net.ChildCall.attach(RuntimeEnvironment.getApplication())
        com.example.finalproject_demo.net.ChildCall.set("")
        var finished = false
        compose.setContent { com.example.finalproject_demo.ui.shell.ChildIntroductionStep({}, { finished = true }) }
        listOf("실명 대신 별명을 권해요", "비우면 「친구」로 불러요").forEach { hint ->
            compose.onNodeWithText(hint).assertIsDisplayed()
            compose.onNodeWithText(hint).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(get(layouts))
                assertFalse(layouts.single().didOverflowWidth)
                assertFalse(layouts.single().didOverflowHeight)
            }
        }
        compose.onNodeWithText("다음").assertIsDisplayed()
        compose.onNodeWithText("건너뛰기").assertIsDisplayed()
        val next = compose.onNodeWithText("다음").fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithText("건너뛰기").fetchSemanticsNode().boundsInRoot
        assertEquals(next.width, skip.width, 1f)
        assertEquals(next.height, skip.height, 1f)
        compose.onRoot().captureRoboImage(File("build/report/child-introduction-640x360.png").also { it.parentFile?.mkdirs() }.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithText("건너뛰기").performClick()
        assertTrue(finished)
        assertNull(AnswerHistory.childAge)
        assertEquals("친구", com.example.finalproject_demo.net.ChildCall.call)
    }

    @Test fun introductionSavesTheOptionalCallOnNext() {
        com.example.finalproject_demo.net.ChildCall.attach(RuntimeEnvironment.getApplication())
        com.example.finalproject_demo.net.ChildCall.set("")
        var finished = false
        compose.setContent { com.example.finalproject_demo.ui.shell.ChildIntroductionStep({}, { finished = true }) }
        compose.onNode(hasSetTextAction()).performTextInput("별님")
        compose.onNodeWithText("만 4세").performScrollTo().performClick()
        compose.onNodeWithText("다음").performClick()
        assertEquals("별님", com.example.finalproject_demo.net.ChildCall.call)
        assertEquals(ChildAge(4, null), AnswerHistory.childAge)
        assertTrue(finished)
    }
}
