package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopReply
import com.example.finalproject_demo.demo.bareNegation
import com.example.finalproject_demo.demo.classifyCoopReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 책 점검(10-14 · k04) — 꼬리 질문 「그때 뭐 하고 있었어?」에 「아니 안 했어」라고 하면 그 말이 칸에 들어가
 * 책에 「아니 안 했어. 그런데 우유를 쏟았어」 · 「그때는 아무것도 안 하고 있었어요」가 나왔다.
 * - 하다는 어떤 꼴이든 같은 어간이다(했 · 하고 · 한)
 * - 하다 · 있다만 부정하고 다른 내용이 없는 답은 꼬리 걸음에서 칸에 넣지 않는다
 * - 내용이 있는 부정(「안 무서웠어」 · 「안 했어, 대신 봤어」)은 그대로다
 */
class CoopEmptyNegationTest {

    @Test
    fun doFormsOfOneVerbAreTheSameStem() {
        assertTrue(classifyCoopReply("아니 안 했어", "그때 뭐 하고 있었어?") is CoopReply.PremiseDenied)
        assertTrue(classifyCoopReply("안 했어", "그때 제일 먼저 한 게 뭐야?") is CoopReply.PremiseDenied)
        // a question about something else is not denied by 「안 했어」 — the classifier alone keeps it an answer
        assertEquals(CoopReply.Answer, classifyCoopReply("안 했어", "그때 어떤 기분이었어?"))
    }

    @Test
    fun aNegationWithNothingInItIsNoTailAnswer() {
        listOf("안 했어", "아니 안 했어", "아무것도 안 했어", "아무 말도 안 했어", "안 하고 있었어", "아니요, 안 했어요.")
            .forEach { assertNotNull(it, bareNegation(it)) }
    }

    @Test
    fun aNegationThatSaysSomethingStaysAnAnswer() {
        listOf("안 무서웠어", "안 울었어", "안 무서울 거야", "안 했어, 대신 사자 봤어", "기린 없었어, 사자 봤어", "우유를 쏟았어", "몰라",
            "없어", "아무도 없었어")
            .forEach { assertNull(it, bareNegation(it)) }
        // the correction path is untouched
        assertTrue(classifyCoopReply("기린 말고 사자 봤어", "기린 봤어?") is CoopReply.Corrected)
    }
}
