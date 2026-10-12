package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.SessionReportView
import com.example.finalproject_demo.ui.StorySpeechHistoryView
import com.github.takahirom.roborazzi.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalRoborazziApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1600dp-port-240dpi")
class SpeechObservationsViewTest {
    @get:Rule val compose = createComposeRule()
    @After fun clean() = SessionReports.clear()

    private fun show(lines: List<TalkLine>, mode: StoryMode = StoryMode.STORY) {
        val s = DemoState().also { it.mode = mode; it.title = "곰의 하루"; it.talk.addAll(lines) }
        val report = s.buildSessionReport("book")
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFFF7F0E3))
                .verticalScroll(rememberScrollState()).padding(24.dp)) {
                SessionReportView(report, "지우", onTalk = {})
            }
        }
    }

    @Test fun storyReportShowsMeasuredUnitsAndSelectionsWithoutAnAgeScore() {
        show(listOf(TalkLine("child", "곰이 집에 갔어", questionType = ReportQuestionType.OPEN),
            TalkLine("child", "배가 아프니까 쉬었어", questionType = ReportQuestionType.REASON),
            TalkLine("card", "공룡", questionType = ReportQuestionType.CHOICE)))
        compose.onNodeWithText("이번 동화의 말 기록").assertExists()
        compose.onNodeWithText("열린·까닭 질문에 직접 답한 말 2개").assertExists()
        compose.onNodeWithText("답당 평균 3.0낱말 · 서로 다른 낱말 6개").assertExists()
        compose.onNodeWithText("까닭 표현이 들어간 답 1개").assertExists()
        compose.onNodeWithText("“공룡” · 1번").assertExists()
        compose.onNodeWithText("또래 평균").assertDoesNotExist()
        val file = File("build/report/speech-observations.png").also { it.parentFile?.mkdirs() }
        compose.onRoot().captureRoboImage(file.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun aChoiceOnlyBookShowsNoZeroAbilityAverage() {
        show(listOf(TalkLine("card", "숲", questionType = ReportQuestionType.CHOICE)))
        compose.onNodeWithText("열린·까닭 질문의 말 기록을 모으고 있어요.").assertExists()
        compose.onNodeWithText("답당 평균 0.0낱말", substring = true).assertDoesNotExist()
        compose.onNodeWithText("“숲” · 1번").assertExists()
    }

    @Test fun otherModesKeepTheirExistingReportUntilTheirOwnersConnectMetrics() {
        show(listOf(TalkLine("child", "집에 갔어", questionType = ReportQuestionType.OPEN)), StoryMode.DIARY)
        compose.onNodeWithText("이번 동화의 말 기록").assertDoesNotExist()
    }

    @Test fun cumulativeStoryMetricsFollowBookDeletionAndChangingTheChild() {
        SessionReports.attach(org.robolectric.RuntimeEnvironment.getApplication())
        SessionReports.clear()
        fun save(book: String, mode: StoryMode) {
            val s = DemoState().also { it.mode = mode }
            s.talk += TalkLine("child", "집에 갔어", questionType = ReportQuestionType.OPEN)
            SessionReports.keep(s, book)
        }
        save("story-one", StoryMode.STORY)
        save("story-two", StoryMode.STORY)
        save("diary", StoryMode.DIARY)
        compose.setContent { StorySpeechHistoryView() }
        compose.onNodeWithText("열린·까닭 질문에 직접 답한 말 3개").assertDoesNotExist()
        compose.onNodeWithText("열린·까닭 질문에 직접 답한 말 2개").assertExists()
        SessionReports.forget("story-one")
        compose.onNodeWithText("열린·까닭 질문에 직접 답한 말 1개").assertExists()
        AnswerHistory.reload()
        compose.onNodeWithText("열린·까닭 질문에 직접 답한 말 1개").assertExists()
        AnswerHistory.startNewChild()
        compose.onNodeWithText("열린·까닭 질문의 말 기록을 모으고 있어요.").assertExists()
    }
}
