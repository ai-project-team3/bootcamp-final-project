package com.example.finalproject_demo

import com.example.finalproject_demo.ui.COOP_TEMPLATES
import com.example.finalproject_demo.ui.fillFromTemplate
import com.example.finalproject_demo.ui.questionHint
import com.example.finalproject_demo.ui.refillBlank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 탭 템플릿 카드 (docs/주말_데모_뼈대.md 「B. 협업」).
 * 카드 하나가 질문 네 자리를 place → problem → cause → solution 순서로 채운다 —
 * `CoopScenes.kt` 의 `COOP_PART_SLOTS` 가 입력 줄 0~3을 이 순서로 칸에 짝짓는다.
 */
class CoopTemplateTest {

    @Test
    fun thereAreFourTemplatesAndEachFillsExactlyTheFourParts() {
        assertEquals(4, COOP_TEMPLATES.size)
        COOP_TEMPLATES.forEach { t ->
            val q = t.questions()
            assertEquals(t.title, 4, q.size)
            assertTrue(t.title, q.all { it.isNotBlank() })
        }
    }

    @Test
    fun theFirstLineOfEveryTemplateAsksWhere() {
        // 0번 줄 = place. "어디" 가 없으면 장소 칸에 장소가 아닌 답이 들어간다
        COOP_TEMPLATES.forEach { t -> assertTrue(t.title, "어디" in t.questions()[0]) }
    }

    @Test
    fun theFourthLineAsksHowItEndedNotWhatTheChildWishes() {
        // 4번 줄은 solution 칸이다. 바람을 물으면 판정이 칸을 못 채우고, 책이 바람을 있었던 일로 쓴다
        COOP_TEMPLATES.forEach { t ->
            t.blank?.choices.orEmpty().ifEmpty { listOf(null) }.forEach { v ->
                val q = t.questions(v)[3]
                assertTrue("${t.title}: $q", "싶" !in q)
            }
        }
    }

    @Test
    fun noTemplateQuestionTripsOurOwnHint() {
        // 우리가 내놓은 예시가 귀띔에 걸리면 부모에게 모순을 보여 준다
        COOP_TEMPLATES.forEach { t ->
            t.questions().forEach { q -> assertNull("${t.title}: $q", questionHint(q)) }
        }
    }

    @Test
    fun customizationIsAtMostOneBlankPerTemplate() {
        COOP_TEMPLATES.forEach { t ->
            val b = t.blank ?: return@forEach
            assertTrue(t.title, b.default.isNotBlank())
            assertTrue(t.title, b.choices.isNotEmpty())
        }
    }

    @Test
    fun theWeekendPlaceGoesIntoTheQuestions() {
        val t = COOP_TEMPLATES.first { it.key == "weekend" }
        val q = t.questions("외할머니 집")
        assertTrue(q[0].startsWith("외할머니 집"))
        assertTrue(q.none { "할머니 집" in it && "외할머니 집" !in it })
        // 비우면 기본값으로 — 빈 장소로 "에 가서" 가 나가지 않는다
        assertEquals(t.questions(), t.questions("  "))
    }

    @Test
    fun theParentWorkTemplateUsesTheChosenTitle() {
        val t = COOP_TEMPLATES.first { it.key == "work" }
        assertTrue(t.questions("아빠")[0].startsWith("아빠는"))
        assertTrue(t.questions("엄마")[0].startsWith("엄마는"))
    }

    @Test
    fun tappingACardReplacesTheFourPartsAndKeepsFreeLines() {
        val t = COOP_TEMPLATES.first { it.key == "today" }
        val before = listOf("옛 질문", "", "", "", "자유 질문 하나")
        val after = fillFromTemplate(before, t.questions())
        assertEquals(t.questions(), after.take(4))
        assertEquals("자유 질문 하나", after[4])
        // 비어 있던 목록도 네 줄이 된다
        assertEquals(t.questions(), fillFromTemplate(emptyList(), t.questions()))
    }

    @Test
    fun changingTheBlankKeepsLinesTheParentRewrote() {
        val t = COOP_TEMPLATES.first { it.key == "weekend" }
        val old = t.questions("할머니 집")
        val edited = old.toMutableList().also { it[2] = "누구랑 같이 갔어?" }
        val out = refillBlank(edited, old, t.questions("바닷가"))
        assertTrue(out[0].startsWith("바닷가"))
        assertEquals("누구랑 같이 갔어?", out[2])
    }
}
