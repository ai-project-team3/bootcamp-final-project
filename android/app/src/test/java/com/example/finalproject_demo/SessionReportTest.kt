package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.MOMENT_ADD
import com.example.finalproject_demo.demo.MOMENT_FEEL
import com.example.finalproject_demo.demo.MOMENT_REASON
import com.example.finalproject_demo.demo.SessionReport
import com.example.finalproject_demo.demo.SessionReports
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.buildSessionReport
import com.example.finalproject_demo.demo.sessionReportOf
import com.example.finalproject_demo.demo.toJson
import com.example.finalproject_demo.demo.withFirsts
import com.example.finalproject_demo.ui.SessionReportView
import com.example.finalproject_demo.ui.SessionTalkView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 부모 리포트 「오늘의 기록」 (10-06 종훈 시안) — 대화를 출처째 남기고, 오또가 채운 것은 숫자에 넣지 않는다(규칙 5),
 * 지난번의 이 아이와만 견줘 「처음 해낸 것」을 고른다(규칙 9). 그림은 build/report/ 아래 — 보는 용도.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1600dp-port-240dpi")
class SessionReportTest {
    @get:Rule val compose = createComposeRule()

    @After fun clean() = SessionReports.clear()

    /** 시안의 이야기 한 권 — 질문은 오또가 말한 것(`say`), 답은 앱이 남기는 utterance 이벤트 그대로 */
    private fun session(): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        d.s.title = "무서운 숲의 토끼"
        d.say("오늘은 어디로 가 볼까?")
        d.event("utterance", "speaker" to "child", "mode" to "voice", "text" to "깜깜한 숲에 갔어. 나무가 엄청 크고 부엉이가 부엉부엉 울어")
        d.s.slots["place"] = "깜깜한 숲"; d.s.slotBy["place"] = "child"
        d.say("좋아!")   // not a question — never in the transcript
        d.say("숲에서 누굴 만났어?")
        d.event("utterance", "speaker" to "child", "mode" to "voice", "text" to "아기 토끼")
        d.s.slots["newcomer"] = "아기 토끼"; d.s.slotBy["newcomer"] = "child"
        d.say("아기 토끼한테 무슨 일이 생겼을까?")
        d.event("utterance", "speaker" to "child", "mode" to "voice", "text" to "토끼가 무서워서 울었어 왜냐면 밤이라서 깜깜하니까")
        d.s.slots["problem"] = "토끼가 무서워서 울었어"; d.s.slotBy["problem"] = "child"
        d.s.slots["cause"] = "밤이라서 깜깜하니까"; d.s.slotBy["cause"] = "child"
        d.say("그때 누가 와 줬어?")
        d.event("utterance", "speaker" to "child", "mode" to "voice", "text" to "친구가 와서 괜찮아졌어. 그리고 토끼 집에 별도 있어")
        d.signal("S2", "친구가 와서 괜찮아졌어. 그리고 토끼 집에 별도 있어", "추가")
        d.say("그래서 이야기는 어떻게 끝날까?")
        d.event("utterance", "speaker" to "child", "mode" to "card", "text" to "친구가 같이 집에 가 줌")
        d.s.slots["solution"] = "친구가 같이 집에 가 줌"; d.s.slotBy["solution"] = "card"
        d.say("그날 날씨는 어땠을까?")
        d.talk("mascot", "바람 부는 밤")
        return d
    }

    @Test fun theTranscriptKeepsQuestionsAndEverySourceApart() {
        val r = session().s.buildSessionReport("b1")
        assertEquals(listOf("otto", "child", "otto", "child", "otto", "child", "otto", "child", "otto", "card", "otto", "mascot"), r.talk.map { it.who })
        assertFalse("a reaction is not a question", r.talk.any { it.text == "좋아!" })
        assertEquals("answers by the child or a card · the mascot's fill is not one", 5, r.exchanges)
        assertEquals(4, r.spoken)
        assertEquals(9, r.longestWords)
        assertEquals(listOf("어디서", "누가", "무슨 일", "왜", "어떻게 끝나"), r.bones.map { it.label })
        assertEquals(4, r.childBones)
        assertEquals("the child's bone shows the child's own words", "깜깜한 숲에 갔어. 나무가 엄청 크고 부엉이가 부엉부엉 울어", r.bones[0].text)
        assertEquals("card", r.bones.last().by)
        assertEquals(listOf(MOMENT_REASON, MOMENT_ADD, MOMENT_FEEL), r.moments.map { it.kind })
        assertTrue(r.moments.all { m -> r.talk.any { it.who == "child" && it.text == m.quote } })
    }

    @Test fun aReportSurvivesJsonAndFirstsComeOnlyFromTheChildsOwnPast() {
        val r = session().s.buildSessionReport("b1")
        assertEquals(r, sessionReportOf(r.toJson()))
        val earlier = r.copy(bookId = "b0", moments = r.moments.filter { it.kind == MOMENT_FEEL }, longestWords = 3)
        val firsts = r.withFirsts(listOf(earlier)).firsts
        assertEquals(3, firsts.size)
        assertTrue(firsts.any { "까닭 말하기" in it } && firsts.any { "이야기 보태기" in it } && firsts.any { "9낱말" in it })
        assertEquals("the second bone from the same line shows only its part", "밤이라서 깜깜하니까", session().s.buildSessionReport().bones[3].text)
        assertTrue("no past, nothing to call a first", r.withFirsts(emptyList()).firsts.isEmpty())
    }

    @Test fun shelvingKeepsTheReportAndRemovingTheBookForgetsIt() {
        val d = session()
        SessionReports.keep(d.s, "b1")
        assertEquals("b1", d.s.lastReport?.bookId)
        assertEquals(d.s.lastReport, SessionReports.of("b1"))
        d.s.resetStory()
        assertTrue("a new session starts an empty transcript", d.s.talk.isEmpty())
        SessionReports.forget("b1")
        assertEquals(null, SessionReports.of("b1"))
    }

    private fun shot(name: String, r: SessionReport, talk: Boolean = false) {
        compose.setContent {
            Box(Modifier.background(Color(0xFFF7F0E3)).fillMaxWidth().padding(24.dp)) {
                if (talk) SessionTalkView(r, "지우", onBack = {}) else SessionReportView(r, "지우", onTalk = {})
            }
        }
        compose.onRoot().captureRoboImage(File("build/report/$name.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun drawsTheStoryReport() {
        val r = session().s.buildSessionReport("b1")
        shot("story", r.withFirsts(listOf(r.copy(bookId = "b0", moments = emptyList(), longestWords = 4))))
    }

    @Test fun drawsTheTranscript() = shot("talk", session().s.buildSessionReport("b1"), talk = true)

    /** 말이 적었던 날 — 0 인 숫자 칸 없이, 카드로 만든 것을 앞에 */
    @Test fun drawsAQuietDay() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.DIARY
        d.s.title = "놀이터"
        d.say("오늘 어디 갔었어?")
        d.event("utterance", "speaker" to "child", "mode" to "card", "text" to "놀이터")
        d.s.slots["place"] = "놀이터"; d.s.slotBy["place"] = "card"
        d.say("거기서 뭐 했어?")
        d.talk("mascot", "그네 타기")
        d.s.slots["problem"] = "그네 타기"; d.s.slotBy["problem"] = "mascot"
        val r = d.s.buildSessionReport("q1")
        assertEquals(0, r.spoken)
        assertEquals(1, r.exchanges)
        shot("quiet", r)
    }
}
