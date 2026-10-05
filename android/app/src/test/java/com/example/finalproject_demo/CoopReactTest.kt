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
        assertEquals("소방차구나!", coopAck("소방차", CoopRole.THING, done))
        assertEquals("아빠구나!", coopAck("아빠랑", CoopRole.WHO, CoopReason.SOON))
        assertEquals("엄마랑 너!", coopAck("엄마랑 나", CoopRole.WHO, done))
        // 지금 방식에서 어색하게 나오던 말들 — 이름이 없으면 한마디만
        listOf("사다리 타고 올라가서 사람 구해. 불이 크니까.", "공룡 나라에서 축구하다가 넘어졌어").forEach { t ->
            val a = coopAck(t, null, CoopReason.DREAM)!!
            assertFalse(a, a.endsWith("니까구나!") || a.endsWith("에서구나!"))
        }
    }

    /** 서술어를 이름으로 떼지 않는다 — 실기기 #98 「기다렸어구나!」. 진짜 이름(바다 · 할머니 · 노래 · 상어)은 그대로 */
    @Test
    fun aVerbIsNeverEchoedAsAName() {
        listOf("기다렸어", "재밌었어", "놀았어요", "기다려", "같이 해 줘", "이거 봐", "좋아요").forEach { t ->
            listOf(null, CoopRole.THING, CoopRole.PLACE, CoopRole.WHO).forEach { role ->
                val a = coopAck(t, role, done)
                assertFalse("$t($role) → $a", a != null && a.dropLast(1).removeSuffix("이구나").removeSuffix("구나").let { it.isNotEmpty() && it in t && a !in setOf("그랬구나!", "우와!", "응응!") })
            }
        }
        assertEquals("바다구나!", coopAck("바다", CoopRole.PLACE, done))
        assertEquals("할머니구나!", coopAck("할머니", CoopRole.WHO, done))
        assertEquals("노래구나!", coopAck("노래", CoopRole.THING, done))
        assertEquals("상어구나!", coopAck("상어", CoopRole.THING, done))
    }

    /** 이름이 문장의 주어면 이름만 되짚지 않는다 — 일어난 일을 놓친다 (10-02 사용자 결정) */
    @Test
    fun whenTheNameIsTheSubjectTheAckIsJustThatsHowItWas() {
        listOf(
            "소방차가 왔어" to done, "불이 났어" to done, "불이 날 것 같아" to CoopReason.SOON,
            "공이 하늘로 날아갔어" to CoopReason.DREAM, "공룡이 뻥 찼어" to CoopReason.DREAM,
            "사자가 문을 열고 나왔어" to done, "고양이가 나무에 올라갔어" to done,
        ).forEach { (t, r) -> assertEquals(t, "그랬구나!", coopAck(t, CoopRole.THING, r)) }
        // 바로 앞도 「그랬구나!」였으면 다른 맞장구로 — 같은 말이 이어 나오지 않는다
        val next = coopAck("공이 하늘로 날아갔어", CoopRole.THING, CoopReason.DREAM, last = "그랬구나!")
        assertTrue(next!!, next != "그랬구나!" && next in listOf("우와!", "응응!"))
        assertEquals("아빠구나!", coopAck("아빠랑", CoopRole.WHO, done, last = "그랬구나!"))
        // 주어가 아니면 지금처럼 이름을 되짚는다
        assertEquals("큰 소방서!", coopAck("큰 소방서", CoopRole.PLACE, CoopReason.SOON))
        // 다녀왔어요의 엉뚱한 답은 주어여도 상상 낱말을 받아 준다
        assertEquals("공룡이면 깜짝 놀라겠다!", coopAck("공룡이 불을 뿜었어", CoopRole.THING, done, wild = true))
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
