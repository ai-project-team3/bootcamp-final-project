package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopAsked
import com.example.finalproject_demo.demo.nextQuestionFromAnswers
import com.example.finalproject_demo.ui.questionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 부모 리포트 「다음에 넣어 볼 질문」을 **부모가 적은 질문에 아이가 한 답**에서 고른다 (협업모드_확장_설계 §3).
 *
 * 점수를 매기지 않는다: 잘 된 질문 하나(아이가 제일 많이 말한 것) 또는 답이 안 나온 질문 하나만 돌려주고,
 * 글자 수 · 순위는 보여 주지 않는다. 부모 질문이 없으면 null — 부르는 쪽이 이유별 예시를 쓴다.
 */
class CoopNextQuestionTest {
    private fun child(q: String, a: String) = CoopAsked(q, a, "child", parent = true)
    private fun template(q: String, a: String) = CoopAsked(q, a, "child", parent = false)

    @Test
    fun withoutParentQuestionsThereIsNothingToPick() {
        assertNull(nextQuestionFromAnswers(emptyList(), "지호"))
        assertNull(nextQuestionFromAnswers(listOf(template("동물원에서 어디가 제일 좋았어?", "기린이랑 사자랑 코끼리")), "지호"))
    }

    @Test
    fun theParentQuestionWithTheLongestChildAnswerIsPicked() {
        val picked = nextQuestionFromAnswers(listOf(
            template("동물원에서 어디가 제일 좋았어?", "기린 사자 코끼리 원숭이 펭귄 다 좋았어"),   // 템플릿 질문은 후보가 아니다
            child("거기서 뭐 먹었어?", "핫도그"),
            child("거기서 누구를 만났어?", "기린이랑 사자랑 코끼리"),
        ), "지호")!!
        assertEquals("거기서 누구를 만났어?", picked.question)
        assertTrue(picked.why, "기린이랑 사자랑 코끼리" in picked.why && "지호" in picked.why && "제일 많이" in picked.why)
        assertTrue("글자 수를 보여 주면 점수다: ${picked.why}", picked.why.none { it.isDigit() })
    }

    @Test
    fun aTieGoesToTheQuestionAskedFirst() {
        val picked = nextQuestionFromAnswers(listOf(child("뭐 먹었어?", "핫도그"), child("뭐 탔어?", "기차야")), "지호")!!
        assertEquals("뭐 먹었어?", picked.question)
    }

    @Test
    fun dontKnowCardsAndMascotAnswersDoNotCountAsAnswers() {
        val picked = nextQuestionFromAnswers(listOf(
            child("거기서 뭐 먹었어?", "몰라"),
            CoopAsked("재밌을 것 같아?", "응", "card", parent = true),
            CoopAsked("누구랑 갈 거야?", "엄마", "mascot", parent = true),
        ), "지호")!!
        // 잘 된 답이 하나도 없다 → 답이 안 나온 첫 질문 하나
        assertEquals("거기서 뭐 먹었어?", picked.question)
        assertTrue(picked.why, "답이 안 나왔어요" in picked.why)
    }

    @Test
    fun aGoodAnswerWinsOverAnUnansweredOne() {
        val picked = nextQuestionFromAnswers(listOf(
            CoopAsked("재밌을 것 같아?", null, null, parent = true),
            child("거기서 누구를 만났어?", "기린이랑 사자"),
        ), "지호")!!
        assertEquals("거기서 누구를 만났어?", picked.question)
    }

    @Test
    fun anUnansweredYesNoQuestionIsRewrittenWithChoicesFirst() {
        val picked = nextQuestionFromAnswers(listOf(CoopAsked("재밌을 것 같아?", null, null, parent = true)), "지호")!!
        assertTrue(picked.why, "“재밌을 것 같아?”" in picked.why && "예/아니오" in picked.why && "선택지" in picked.why)
        // 선택지는 앱이 지어낼 수 없다 — 질문은 그대로 두고 「선택지를 먼저」만 일러 준다
        assertEquals("재밌을 것 같아?", picked.question)
    }

    @Test
    fun anUnansweredDoubleQuestionIsTrimmedToTheFirst() {
        val picked = nextQuestionFromAnswers(listOf(CoopAsked("어디 갔어? 누구랑 갔어?", null, null, parent = true)), "지호")!!
        assertEquals("어디 갔어?", picked.question)
        assertTrue(picked.why, "하나만" in picked.why)
    }

    @Test
    fun anUnansweredWhenQuestionAsksWhereInstead() {
        val picked = nextQuestionFromAnswers(listOf(CoopAsked("언제 갔어?", null, null, parent = true)), "지호")!!
        assertEquals("어디 갔어?", picked.question)
        assertTrue(picked.why, "언제" in picked.why)
    }

    @Test
    fun noScoreWordsEver() {
        listOf(
            listOf(child("뭐 먹었어?", "핫도그"), child("뭐 탔어?", "기차야 기차")),
            listOf(CoopAsked("재밌을 것 같아?", null, null, parent = true)),
            listOf(child("뭐 먹었어?", "몰라")),
        ).forEach { asked ->
            val p = nextQuestionFromAnswers(asked, "지호")!!
            listOf("점수", "등급", "%", "늘었", "줄었", "잘했", "못했").forEach { bad ->
                assertTrue("$bad: ${p.why}", bad !in p.why && bad !in p.question)
            }
        }
    }
}
