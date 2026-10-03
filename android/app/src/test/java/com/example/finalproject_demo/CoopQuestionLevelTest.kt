package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_WHY_MAX
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopSource
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.coopFollowUp
import com.example.finalproject_demo.demo.coopGuard
import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.questionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 수준별 이어 받기 질문 (10-02 · 협업 질문 업그레이드 §6).
 * 앞 답의 이름이 들어가고, 수준마다 맞는 질문 종류가 나가고, 「왜」는 이야기당 두 번까지, 가정 질문은 까닭 짓기 + 상상에서만.
 */
class CoopQuestionLevelTest {
    private val fire = CoopPick("job", "소방관", "soon")
    private val zoo = CoopPick("place", "동물원", "done")
    private fun f(slot: String, lv: Level, r: CoopReason, heard: Map<String, String>, pick: CoopPick? = fire, why: Int = 0,
                  template: String? = null, rungs: List<String> = emptyList()) =
        coopFollowUp(slot, lv, r, heard, pick, why, template, rungs)

    @Test
    fun theProblemQuestionCarriesThePlaceTheChildSaid() {
        assertEquals("큰 소방서에서 불이 나면 소방관은 무슨 일을 할까?",
            f("problem", Level.CHAIN, CoopReason.SOON, mapOf("place" to "큰 소방서"), template = "거기서 불이 나면 소방관은 무슨 일을 할까?"))
        assertEquals("유치원에서 무슨 일이 있었어?", f("problem", Level.CHAIN, CoopReason.DONE, mapOf("place" to "유치원"), pick = zoo))
        assertEquals("바다에서 뭐 봤어?", f("problem", Level.PICK, CoopReason.DONE, mapOf("place" to "바다"), pick = zoo))
        // 장소를 못 들었으면 이어 받기 없음 → 템플릿 그대로
        assertNull(f("problem", Level.CHAIN, CoopReason.DONE, emptyMap(), pick = zoo))
    }

    @Test
    fun theCauseQuestionNamesTheThingAndFollowsTheLevel() {
        // 까닭 짓기 — 열린 「왜」
        assertEquals("곰이 왜 그랬을까?", f("cause", Level.REASON, CoopReason.DREAM, mapOf("thing" to "곰")))
        // 이어 · 고르며 짓기 — 선택지 먼저, 질문 마지막
        val chain = f("cause", Level.CHAIN, CoopReason.DONE, mapOf("thing" to "불"), pick = CoopPick("job", "소방관", "done"))!!
        assertTrue(chain, chain.endsWith(". 불이 왜 그랬을까?") && "불장난을 해서" in chain)
        val pick = f("cause", Level.PICK, CoopReason.DONE, mapOf("thing" to "소방차"), pick = CoopPick("job", "소방관", "done"))!!
        assertTrue(pick, pick.endsWith(". 소방차가 왜 그랬을까?"))
    }

    /** 곧 해요의 「왜」는 이어 받지 않는다 — 앞 칸 답이 사건이 아니라 할 일이라 사건 까닭 선택지가 어긋난다 (실기기 10-03) */
    @Test
    fun aSoonStoryAsksTheTemplateWhyInsteadOfAFollowUp() {
        Level.entries.forEach { lv ->
            listOf(mapOf("thing" to "훈련"), emptyMap()).forEach { heard ->
                assertNull("$lv $heard", f("cause", lv, CoopReason.SOON, heard, rungs = listOf("불장난을 해서, 전선이 낡아서. 왜 그럴까?")))
            }
        }
        // 다녀왔어요 · 좋아해요는 그대로 이어 받는다
        assertEquals("훈련이 왜 그랬을까?", f("cause", Level.REASON, CoopReason.DONE, mapOf("thing" to "훈련")))
    }

    @Test
    fun whyIsAskedAtMostTwicePerStory() {
        val third = f("cause", Level.REASON, CoopReason.DONE, mapOf("thing" to "불"), why = COOP_WHY_MAX)!!
        assertTrue(third, "왜" !in third && "뭐 때문에" in third)
    }

    @Test
    fun theSolutionQuestionFollowsTheLevel() {
        val pick = f("solution", Level.PICK, CoopReason.DONE, emptyMap(), pick = CoopPick("job", "소방관", "done"))!!
        assertTrue(pick, pick.endsWith(". 그다음에 뭐 했어?"))
        assertEquals("그다음에 뭐 할까?", f("solution", Level.CHAIN, CoopReason.SOON, emptyMap()))
        assertEquals("불은 어떻게 됐어?", f("solution", Level.REASON, CoopReason.DONE, mapOf("thing" to "불")))
        assertEquals("그래서 어떻게 될까?", f("solution", Level.REASON, CoopReason.SOON, emptyMap()))
    }

    @Test
    fun aHypotheticalOnlyWhenReasoningAndImagining() {
        assertEquals("네가 소방관이었다면 어떻게 했을까?", f("solution", Level.REASON, CoopReason.DREAM, emptyMap(), pick = CoopPick("job", "소방관", "dream")))
        assertEquals("네가 의사였다면 어떻게 했을까?", f("solution", Level.REASON, CoopReason.DREAM, emptyMap(), pick = CoopPick("job", "의사", null)))
        listOf(Level.PICK, Level.CHAIN).forEach { lv ->
            assertTrue("$lv", f("solution", lv, CoopReason.DREAM, emptyMap(), pick = CoopPick("job", "소방관", "dream"))?.contains("였다면") != true)
        }
        listOf(CoopReason.DONE, CoopReason.SOON).forEach { r ->
            assertTrue("$r", f("solution", Level.REASON, r, emptyMap())?.contains("였다면") != true)
        }
    }

    @Test
    fun everyFollowUpKeepsOurRules() {
        val heard = mapOf("place" to "큰 소방서", "thing" to "소방차", "who" to "엄마랑 너")
        COOP_KINDS.forEach { k -> (k.items + k.customExample).forEach { name -> CoopReason.entries.forEach { r ->
            Level.entries.forEach { lv -> listOf("problem", "cause", "solution").forEach { slot -> (0..3).forEach { why ->
                val q = f(slot, lv, r, heard, pick = CoopPick(k.key, name, r.key), why = why, template = "거기서 무슨 일이 생길까?")
                if (q != null) {
                    assertNull("$name $r $lv $slot: $q", questionHint(q.substringAfterLast(". ")))
                    assertNotNull("$name $r $lv $slot: $q", coopGuard(q, r, CoopSource.HEARD).text)
                    assertEquals("$name $r $lv $slot: 물음표", 1, q.count { it == '?' })
                    if (why >= COOP_WHY_MAX) assertTrue("$name $r $lv $slot: $q", "왜" !in q)
                }
            } } }
        } } }
    }
}
