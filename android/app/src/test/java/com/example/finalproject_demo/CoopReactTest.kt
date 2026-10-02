package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopRole
import com.example.finalproject_demo.demo.CoopSource
import com.example.finalproject_demo.demo.coopAck
import com.example.finalproject_demo.demo.coopGuard
import com.example.finalproject_demo.demo.coopRedirect
import com.example.finalproject_demo.demo.fantasyWordIn
import com.example.finalproject_demo.demo.isWildForReality
import com.example.finalproject_demo.ui.CoopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 받아주기 한마디 · 엉뚱한 답 (10-02 · 협업 질문 업그레이드 §5 · §7 · §8).
 * 받아주기는 아이 말에서 뗀 이름 하나만 되짚고, 활용형으로 바꾸지 않는다. 짧은 답은 되비추지 않는다.
 */
class CoopReactTest {
    private val done = CoopReason.DONE

    @Test
    fun theAckEchoesOneNameWithoutConjugating() {
        assertEquals("큰 소방서!", coopAck("큰 소방서에서 일해. 거기 빨간 차가 있어.", CoopRole.PLACE, CoopReason.SOON))
        assertEquals("소방차구나!", coopAck("소방차가 왔어", CoopRole.THING, done))
        assertEquals("불이구나!", coopAck("불이 났어", CoopRole.THING, done))
        assertEquals("엄마랑 너!", coopAck("엄마랑 나", CoopRole.WHO, done))
        // 지금 방식에서 어색하게 나오던 말들 — 이름이 없으면 한마디만
        listOf("사다리 타고 올라가서 사람 구해. 불이 크니까.", "공룡 나라에서 축구하다가 넘어졌어").forEach { t ->
            val a = coopAck(t, null, CoopReason.DREAM)!!
            assertFalse(a, a.endsWith("니까구나!") || a.endsWith("에서구나!"))
        }
    }

    @Test
    fun shortAnswersAreNotEchoed() {
        listOf("몰라", "응", "아니", "글쎄", "음", "모르겠어").forEach { assertNull(it, coopAck(it, CoopRole.PLACE, done)) }
    }

    @Test
    fun anAckIsOneShortSentence() {
        listOf("큰 소방서에서 일해", "소방차가 왔어", "엄마랑 나", "배고파서 울었어", "공룡이 왔어").forEach { t ->
            val a = coopAck(t, CoopRole.THING, CoopReason.DREAM)!!
            assertEquals(a, 1, Regex("[.!?]").findAll(a).count())
            assertTrue(a, a.length <= 14)
        }
    }

    @Test
    fun aWildAnswerInARealLifeStoryIsGentlyTurnedBack() {
        assertTrue(isWildForReality("공룡이 불을 뿜었어", CoopReason.DONE))
        assertTrue(isWildForReality("용이 날아왔어", CoopReason.SOON))
        assertFalse(isWildForReality("공룡이 불을 뿜었어", CoopReason.DREAM))     // 상상 이야기에는 섞는다
        assertFalse(isWildForReality("용기를 냈어", CoopReason.DONE))
        assertEquals("공룡", fantasyWordIn("공룡이 불을 뿜었어"))
        assertEquals("공룡이면 깜짝 놀라겠다!", coopAck("공룡이 불을 뿜었어", CoopRole.THING, done, wild = true))
        assertEquals("용이면 깜짝 놀라겠다!", coopAck("용이 날아왔어", CoopRole.THING, done, wild = true))
        assertEquals("로봇이면 깜짝 놀라겠다!", coopAck("로봇이 도와줬어", CoopRole.THING, done, wild = true))
    }

    @Test
    fun theRedirectKeepsOurRulesAndTheTense() {
        listOf("place", "companion", "problem", "cause", "solution", "detail").forEach { k ->
            listOf(CoopReason.DONE, CoopReason.SOON).forEach { r ->
                val q = coopRedirect(k, r)
                assertTrue(q, q.startsWith("진짜로는 "))
                assertNotNull("$k $r: $q", coopGuard(q, r, CoopSource.HEARD).text)
            }
        }
    }
}
