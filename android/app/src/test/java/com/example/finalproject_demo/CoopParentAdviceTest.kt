package com.example.finalproject_demo

import com.example.finalproject_demo.demo.coopParentAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 부모 화면 귀띔 (10-02 · 협업 질문 업그레이드 §9).
 * 막지 않는다: 무엇이 걸렸는지와 고쳐 쓴 문장 하나만 보여 주고 고르는 것은 부모다.
 */
class CoopParentAdviceTest {
    @Test
    fun aPlainShortQuestionGetsNoAdvice() {
        listOf("", "  ", "제일 재밌었던 게 뭐였어?", "거기서 누구를 만났어?").forEach { assertNull(it, coopParentAdvice(it)) }
    }

    @Test
    fun politeEndingsAndHardWordsGetARewrite() {
        val a = coopParentAdvice("가장 인상 깊었던 게 뭐예요?")!!
        assertEquals("제일 좋았던 게 뭐야?", a.suggestion)
        assertEquals("갔어?", coopParentAdvice("갔어요?")!!.suggestion)
        assertTrue(a.notes.toString(), a.notes.any { "쉬워요" in it })
    }

    @Test
    fun twoQuestionsAreTrimmedToTheFirst() {
        val a = coopParentAdvice("어디 갔어? 누구랑 갔어?")!!
        assertEquals("어디 갔어?", a.suggestion)
        assertTrue(a.notes.toString(), a.notes.any { "2개" in it })
    }

    @Test
    fun aRoughWordIsPointedOutButNotRewritten() {
        val a = coopParentAdvice("친구가 너 때렸어?")
        // 「때리」는 금칙어 §0 허용 목록 — 귀띔하지 않는다 (규칙 7 과잉 차단 금지)
        assertNull(a)
    }
}
