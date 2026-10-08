package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.causeAsksOtherEvent
import com.example.finalproject_demo.demo.coopAck
import com.example.finalproject_demo.demo.coopServerDropped
import com.example.finalproject_demo.demo.coopServerReaction
import com.example.finalproject_demo.demo.pastEcho
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.CoopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #304 (10-07 진웅 실기기 · 같이 만들기)
 * 1. 꼬리 답이 해결을 먼저 채우면 서버가 cause 자리에 해결의 까닭을 물었다 — 「아빠는 왜 풍선을 잡아줬을까?」
 * 2. 서버 대사를 못 쓴 까닭이 로그에 없었고, 이름 없는 꼬리 답에는 늘 「응응!」 — 짧은 지난 일 말은 끝만 바꿔 되비춘다(10-07 치영 결정)
 */
class CoopCauseAndAckTest {
    private val problem = "풍선을 놓쳤어"
    private val solution = "아빠가 잡아줬어"

    @Test
    fun aCauseQuestionAboutTheSolutionIsDropped() {
        listOf(
            Triple("아빠는 왜 풍선을 잡아줬을까?", problem, solution) to true,
            Triple("풍선은 왜 놓쳤을까?", problem, solution) to false,
            Triple("왜 그렇게 됐을까?", problem, solution) to false,
            // 조사가 붙어도 줄기로 본다
            Triple("아빠가 왜 그랬을까?", problem, solution) to true,
            // 문제와 해결이 같은 낱말을 나누면 그 낱말은 걸리지 않는다
            Triple("풍선이 왜 날아갔을까?", "풍선이 날아갔어", "풍선을 다시 잡았어") to false,
            Triple("왜 다시 잡았을까?", "풍선이 날아갔어", "풍선을 다시 잡았어") to true,
            // 같이 간 사람이 문제에도 있으면 그 사람 이름만으로는 거르지 않는다
            Triple("아빠는 왜 풍선을 놓쳤을까?", "아빠랑 풍선을 놓쳤어", solution) to false,
            // 해결이 아직 없으면 거르지 않는다(지금과 같다)
            Triple("아빠는 왜 풍선을 잡아줬을까?", problem, null) to false,
        ).forEach { (c, want) -> assertEquals(c.toString(), want, causeAsksOtherEvent(c.first, c.second, c.third)) }
    }

    /** 협업 사다리 둘째 칸 — 같이 간 사람의 행동이 아니라 문제의 까닭을 묻는다. 일기 사다리는 그대로 */
    @Test
    fun theCoopLadderAsksWhyTheTroubleHappened() {
        fun rungs(mode: StoryMode): List<String> {
            val s = DemoState().apply { this.mode = mode; companionKind = "아빠"; problem = "블록이 무너졌어" }
            val step = COOP_STEPS.first { it.slot == "cause" }
            return step.rungs(s)
        }
        assertTrue(rungs(StoryMode.COOP).toString(), "무엇 때문에 그런 일이 생겼을까?" in rungs(StoryMode.COOP))
        assertFalse(rungs(StoryMode.COOP).toString(), rungs(StoryMode.COOP).any { "왜 그랬을 것 같아" in it })
        assertTrue("일기 사다리는 그대로", rungs(StoryMode.DIARY).any { "왜 그랬을 것 같아" in it })
    }

    @Test
    fun aShortPastAnswerIsEchoedWithItsEndingOnly() {
        assertEquals("츄러스 사 먹었구나!", pastEcho("츄러스 사 먹었어"))
        assertEquals("아빠가 잡아줬구나!", pastEcho("아빠가 잡아줬어."))      // 줄임꼴(주었어)
        assertEquals("놀이공원 갔구나!", pastEcho("놀이공원 갔어"))
        assertEquals("츄러스 사 먹었구나!", coopAck("츄러스 사 먹었어", null, CoopReason.DONE))
        listOf(
            "불이 크니까 무서웠어",            // 이음 끝
            "학교 가서 놀았어",                // 이음 끝
            "나 츄러스 먹었어",                // 1인칭
            "우리가 같이 갔어",                // 1인칭
            "츄러스 먹을 거야",                // 지난 일이 아님
            "아침에 일찍 일어나서 다 같이 놀이공원 갔어",   // 6어절 · 이음 끝
            "엄마랑 아빠랑 동생이랑 할머니랑 삼촌이랑 갔어",  // 6어절
            "츄러스 먹었어. 맛있었어",          // 두 문장
            "놀았어요",                        // 끝이 었어 · 았어 · 했어 · 였어 아님
            "강아지가 있어",                    // 지금 있는 것
            "비가 오겠어",                      // 짐작
        ).forEach { assertNull(it, pastEcho(it)) }
        val plain = coopAck("나 츄러스 먹었어", null, CoopReason.DONE)!!
        assertTrue(plain, plain in setOf("그랬구나!", "우와!", "응응!"))
    }

    @Test
    fun aDroppedServerLineSaysWhy() {
        assertEquals(listOf("대사 없음"), coopServerDropped(null))
        val asks = Server.Line("츄러스 맛있었어?", "달콤한 츄러스를 먹으며 신이 났어요 그리고 또 먹고 싶었어요 정말 맛있어서요 또", null)
        assertNull(coopServerReaction(asks))
        val why = coopServerDropped(asks)
        assertEquals(2, why.size)
        assertTrue(why.toString(), why[0].startsWith("ack 물음표"))
        assertTrue(why.toString(), why[1].startsWith("expand 40자 넘음"))
    }
}
