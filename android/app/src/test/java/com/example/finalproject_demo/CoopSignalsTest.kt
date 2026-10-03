package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_LIVE_LV
import com.example.finalproject_demo.demo.coopSignals
import com.example.finalproject_demo.demo.isChoiceQuestion
import com.example.finalproject_demo.net.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 진짜 마이크 답의 수준 신호 (10-02 · 설계 A · B).
 * 지금까지는 신호가 비어 늘 「내림」이었다. 이제 이유 · 이야기 요소를 말하면 올림 신호가 된다.
 */
class CoopSignalsTest {
    @Test
    fun aReasonToAWhyQuestionIsS1() {
        val a = coopSignals("배고파서 울었어", "곰이 왜 그랬을까?", null)
        assertTrue(a.reason)
        assertEquals(COOP_LIVE_LV, a.lv)
        assertTrue("잇는 말 없어도 이유면 인정", coopSignals("배고파", "곰이 왜 그랬을까?", null).reason)
    }

    @Test
    fun aWayToAHowQuestionIsS1ButANameIsNot() {
        assertTrue(coopSignals("사다리 타고 올라가서 구해 줬어", "불은 어떻게 됐어?", null).reason)
        assertFalse(coopSignals("소방차", "불은 어떻게 됐어?", null).reason)
    }

    @Test
    fun unaskedStoryElementsAndLinkersAreCounted() {
        val a = coopSignals("불이 났는데 다시 물을 뿌렸어. 그래서 꺼졌어", "큰 소방서에서 무슨 일이 생겼어?", null)
        assertTrue(a.el.toString(), "시도" in a.el && "결과" in a.el)
        assertTrue(a.con)
    }

    @Test
    fun choiceAnswersShortAnswersAndDontKnowCarryNoSignal() {
        val q = "배고파서, 심심해서, 놀라서. 기린이 왜 그랬을까?"
        assertTrue(isChoiceQuestion(q))
        listOf(coopSignals("배고파서", q, null), coopSignals("몰라", "왜 그랬을까?", null), coopSignals("응", "왜 그랬을까?", null)).forEach { a ->
            assertFalse(a.reason); assertTrue(a.el.isEmpty()); assertFalse(a.con)
        }
        assertFalse(isChoiceQuestion("거기서 뭐 했어?"))
    }

    @Test
    fun theServerVerdictWinsWhenThereIsOne() {
        val v = Server.Verdict("ok", emptyList(), null, null, false, false, null, false, s1Reason = true, s2Addition = true, emotion = "신났")
        val a = coopSignals("소방차", "거기서 뭐 했어?", v)
        assertTrue(a.reason); assertEquals(setOf("추가"), a.el); assertEquals("신났", a.emo)
    }
}
