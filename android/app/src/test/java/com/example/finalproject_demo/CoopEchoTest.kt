package com.example.finalproject_demo

import com.example.finalproject_demo.demo.coopEcho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 협업 모드 받아주기 — LLM이 붙기 전의 대본. 프롬프트 §3의 「받아주기: 아이 말을 그대로 되비춘다, 8어절 이하」를 흉내 낸다.
 * 아이 말을 고치지 않는다(§1 시스템 프롬프트). 어미만 "-구나"로 바꾼다.
 */
class CoopEchoTest {

    @Test
    fun verbEndingsBecomeGuna() {
        assertEquals("할머니 집 갔구나!", coopEcho("할머니 집 갔어."))
        assertEquals("친구랑 놀았구나!", coopEcho("친구랑 놀았어!"))
        assertEquals("블록이 무너졌구나!", coopEcho("블록이 무너졌어"))
        assertEquals("재밌었구나!", coopEcho("재밌었어요"))
    }

    @Test
    fun nounsGetTheRightParticle() {
        assertEquals("어린이집이구나!", coopEcho("어린이집"))
        assertEquals("놀이터구나!", coopEcho("놀이터"))
        assertEquals("민준이구나!", coopEcho("민준"))
    }

    @Test
    fun nonAnswersGetNoEcho() {
        // "몰라"를 "몰구나"로 되비추면 안 된다 — 이런 답은 고정 리액션만
        listOf("몰라", "응", "아니", "싫어", "글쎄", "", "   ").forEach { assertNull("되비추면 안 된다: '$it'", coopEcho(it)) }
    }

    @Test
    fun longAnswersAreNotEchoedWhole() {
        // 8어절 넘는 말은 앞부분만 — 프롬프트 §3 "한 문장, 8어절 이하"
        val e = coopEcho("할머니 집에 갔는데 엄마가 늦게 온다고 해서 거기서 동생이랑 블록 쌓고 놀았어")
        assertEquals(true, e != null && e.split(" ").size <= 8)
    }
}
