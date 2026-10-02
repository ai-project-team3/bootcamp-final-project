package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopRole
import com.example.finalproject_demo.demo.coopFill
import com.example.finalproject_demo.demo.coopNameFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 같이 만들기 — 아이 답에서 **이름 하나**를 떼어 다음 질문에 조사까지 맞게 끼운다 (10-02 · 협업 질문 업그레이드 §8).
 * 아이 문장을 활용형으로 바꾸지 않는다. 이름을 못 떼면 null → 고정 질문으로 돌아간다.
 */
class CoopHeardTest {
    private fun place(t: String) = coopNameFrom(t, CoopRole.PLACE)
    private fun fill(tpl: String, vararg h: Pair<String, String>) = coopFill(tpl, mapOf(*h))

    @Test
    fun aPlaceTakesItsParticle() {
        assertEquals("유치원에서", fill("{place:에서}", "place" to place("유치원이야")!!))
        assertEquals("수영장에서", fill("{place:에서}", "place" to place("수영장이요")!!))
        assertEquals("바다에서", fill("{place:에서}", "place" to place("바다")!!))
        assertEquals("공원에서", fill("{place:에서}", "place" to place("음 그러니까 공원")!!))
        assertEquals("큰 소방서", place("큰 소방서에서 일해"))
        assertEquals("놀이터", place("놀이터에 갔어"))
        assertEquals("큰 소방서에서 무슨 일이 생겼어?", fill("{place:에서} 무슨 일이 생겼어?", "place" to "큰 소방서"))
    }

    @Test
    fun aThingTakesSubjectAndObjectParticles() {
        val car = coopNameFrom("소방차", CoopRole.THING)!!
        assertEquals("소방차가", fill("{thing:가}", "thing" to car))
        assertEquals("소방차를", fill("{thing:을}", "thing" to car))
        val bear = coopNameFrom("곰", CoopRole.THING)!!
        assertEquals("곰이", fill("{thing:가}", "thing" to bear))
        assertEquals("곰을", fill("{thing:을}", "thing" to bear))
        assertEquals("불", coopNameFrom("불이 났어", CoopRole.THING))
        assertEquals("소방차", coopNameFrom("소방차가 왔어", CoopRole.THING))
        assertEquals("고양이", coopNameFrom("고양이가 울었어", CoopRole.THING))
        assertEquals("고양이", coopNameFrom("고양이 왔어", CoopRole.THING))
        assertEquals("고양이", coopNameFrom("고양이야", CoopRole.THING))
        assertEquals("불은", fill("{thing:은}", "thing" to "불"))
    }

    @Test
    fun meBecomesYou() {
        assertEquals("엄마랑 너", coopNameFrom("엄마랑 나", CoopRole.WHO))
        assertEquals("엄마랑 너랑", fill("{who:랑}", "who" to "엄마랑 너"))
        assertEquals("아빠", coopNameFrom("아빠랑 갔어", CoopRole.WHO))
    }

    @Test
    fun nothingIsInsertedWhenThereIsNoName() {
        listOf("배고파", "몰라", "모르겠어", "응", "아직 못 들은 곳", "", "   ",
            "어제 엄마랑 아빠랑 동생이랑 같이 기차 타고 멀리 갔어",
            // 흐름 검사에서 실제로 끼었던 말 토막 (10-02)
            "사람 도와줄 거야", "도구를 챙길 거야", "불 끄는 것", "소방관 옷 입을 거야").forEach { t ->
            CoopRole.entries.forEach { r -> assertNull("$r「$t」", coopNameFrom(t, r)) }
        }
        // 이름 자리가 하나라도 비면 질문 전체를 포기한다 — 고정 질문으로 돌아간다
        assertNull(fill("{place:에서} 무슨 일이 생겼어?"))
        assertNull(fill("{thing:가} 왜 그랬을까?", "place" to "공원"))
    }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("그래서 어떻게 됐어?", fill("그래서 어떻게 됐어?"))
    }
}
