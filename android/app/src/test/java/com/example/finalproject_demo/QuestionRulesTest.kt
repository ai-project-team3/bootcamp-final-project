package com.example.finalproject_demo

import com.example.finalproject_demo.ui.questionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 부모 질문 귀띔 — `부모협업모드_설계.md` §5-1의 예시가 그대로 걸리고, 좋은 질문은 안 걸린다 */
class QuestionRulesTest {

    @Test
    fun goodQuestionsGetNoHint() {
        listOf(
            "오늘 제일 재밌었던 게 뭐였어?",
            "거기서 누구랑 있었어?",
            "오늘 어디 갔었어?",
            "왜 그랬을까?",
            "그래서 어떻게 됐어?",
            "오늘 있었던 일 하나만 말해 줄래?",   // 의문사는 없지만 열린 질문이다
            "친구가 뭐라고 했는지 들려줄래?",
        ).forEach { assertNull("좋은 질문이 걸렸다: $it", questionHint(it)) }
    }

    @Test
    fun yesNoQuestionsGetTheOpenQuestionHint() {
        listOf("재밌었어?", "오늘 좋았어?", "친구랑 놀았어?").forEach {
            val h = questionHint(it)
            assertNotNull("예/아니오 질문이 안 걸렸다: $it", h)
            assertTrue(h!!.why.contains("예/아니오"))
        }
    }

    @Test
    fun twoQuestionWordsGetTheOneAtATimeHint() {
        val h = questionHint("오늘 누구랑 뭐 하고 놀았어?")
        assertNotNull(h)
        assertTrue(h!!.why.contains("하나만"))
    }

    @Test
    fun whenGetsItsOwnHintFirst() {
        // '언제'가 있으면 다른 규칙보다 먼저 — 시간 표현이 아직 안 선다
        val h = questionHint("언제 누구랑 갔어?")
        assertNotNull(h)
        assertTrue(h!!.why.contains("언제"))
    }

    @Test
    fun blankIsStillBeingTyped() {
        assertNull(questionHint(""))
        assertNull(questionHint("   "))
    }

    @Test
    fun hintsNeverContainScores() {
        // 설계 §7 — "당신의 질문은 60점"은 앱을 지우게 만든다
        listOf("재밌었어?", "누구랑 뭐 했어?", "언제 갔어?").forEach { q ->
            val h = questionHint(q)!!
            listOf("점", "등급", "수준", "부족", "잘못").forEach { bad ->
                assertEquals("귀띔에 '$bad' 가 있다: ${h.why} / ${h.example}", false, bad in h.why || bad in h.example)
            }
        }
    }
}
