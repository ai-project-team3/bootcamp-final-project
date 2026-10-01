package com.example.finalproject_demo

import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.cleanCoopName
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.ui.COOP_SUGGESTIONS
import com.example.finalproject_demo.ui.questionHint
import com.example.finalproject_demo.ui.templateQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 탭 템플릿 — 장소 · 직업 · 스포츠 → 요소 → 고른 이유 (09-29).
 * 고른 것은 맥락이고, 오또가 기승전결 네 자리에서 place → problem → cause → solution 순서로 묻는다 —
 * `CoopScenes.kt` 의 `COOP_PART_SLOTS` 가 템플릿 질문 0~3을 이 순서로 칸에 짝짓는다.
 */
class CoopTemplateTest {

    /** 템플릿마다 목록의 요소 전부 + 직접 쓴 예시, 이유 셋 + 안 고름 */
    private fun everyCombination() = COOP_KINDS.flatMap { k ->
        (k.items + k.customExample).flatMap { name ->
            (CoopReason.entries + listOf(null)).map { r -> Triple(k, name, r) }
        }
    }

    @Test
    fun thereAreThreeKindsWithFiveItemsEach() {
        assertEquals(listOf("place", "job", "sport"), COOP_KINDS.map { it.key })
        COOP_KINDS.forEach { k ->
            assertEquals(k.title, 5, k.items.size)
            assertEquals(k.title, CoopReason.entries.toSet(), k.reasonLabels.keys)
        }
    }

    @Test
    fun everyChoiceFillsExactlyTheFourParts() {
        everyCombination().forEach { (k, name, r) ->
            val q = k.questions(name, r)
            assertEquals("${k.title} $name $r", 4, q.size)
            assertTrue("${k.title} $name $r", q.all { it.isNotBlank() })
        }
    }

    @Test
    fun theFirstLineAlwaysAsksWhere() {
        // 0번 줄 = place. "어디" 가 없으면 장소 칸에 장소가 아닌 답이 들어간다
        everyCombination().forEach { (k, name, r) -> assertTrue("${k.title} $name $r", "어디" in k.questions(name, r)[0]) }
    }

    @Test
    fun theFourthLineAsksHowItEndedNotWhatTheChildWishes() {
        // 4번 줄은 solution 칸이다. 바람을 물으면 판정이 칸을 못 채우고, 책이 바람을 있었던 일로 쓴다
        everyCombination().forEach { (k, name, r) ->
            val q = k.questions(name, r)[3]
            assertTrue("${k.title} $name $r: $q", "싶" !in q)
        }
    }

    @Test
    fun noTemplateQuestionTripsOurOwnHint() {
        // 우리가 내놓은 예시가 귀띔에 걸리면 부모에게 모순을 보여 준다
        everyCombination().forEach { (k, name, r) ->
            k.questions(name, r).forEach { q -> assertNull("${k.title} $name $r: $q", questionHint(q)) }
        }
    }

    @Test
    fun theReasonChangesTheQuestions() {
        // 이유가 이 모드를 가르는 장치다 — 이유마다 첫 줄이 달라야 한다
        COOP_KINDS.forEach { k ->
            val firsts = CoopReason.entries.map { k.questions(k.items[0], it)[0] }
            assertEquals(k.title, 3, firsts.toSet().size)
        }
        // 안 고르면 상상으로
        val job = coopKind("job")!!
        assertEquals(job.questions("소방관", CoopReason.DREAM), job.questions("소방관", null))
    }

    @Test
    fun theNameGoesInWithTheRightParticle() {
        val job = coopKind("job")!!
        assertEquals("소방관은 어디서 일할까?", job.questions("소방관", CoopReason.SOON)[0])
        assertEquals("요리사는 어디서 일할까?", job.questions("요리사", CoopReason.SOON)[0])
        assertEquals("네가 선생님이 되면 어디서 일할까?", job.questions("선생님", CoopReason.DREAM)[0])
        assertEquals("네가 의사가 되면 어디서 일할까?", job.questions("의사", CoopReason.DREAM)[0])
        val sport = coopKind("sport")!!
        assertEquals("태권도를 어디서 했어?", sport.questions("태권도", CoopReason.DONE)[0])
        assertEquals("줄넘기를 어디서 배울까?", sport.questions("줄넘기", CoopReason.SOON)[0])
        assertEquals("수영을 어디서 했어?", sport.questions("수영", CoopReason.DONE)[0])
        // 앞뒤 띄어쓰기는 지운다 — "  동물원 에" 가 나가지 않는다
        assertEquals(coopKind("place")!!.questions("동물원"), coopKind("place")!!.questions("  동물원 "))
    }

    @Test
    fun typedNamesAreCleanedOrRefused() {
        assertEquals("할머니 집", cleanCoopName("  할머니   집 "))
        assertNull(cleanCoopName("K-POP"))
        assertNull(cleanCoopName("   "))
        assertNull(cleanCoopName("열한글자가넘는이름입니다"))
        assertNull(cleanCoopName("소방관!"))
        assertEquals("abc 123", cleanCoopName("abc 123"))
    }

    /** 저장된 고른 것(키 문자열)에서 오또가 물을 네 줄 — 이유 키가 없거나 모르는 값이면 상상으로 */
    @Test
    fun aSavedPickGivesTheMascotsFourQuestions() {
        val job = coopKind("job")!!
        assertEquals(job.questions("소방관", CoopReason.DONE), CoopPick("job", "소방관", "done").templateQuestions())
        assertEquals(job.questions("소방관", CoopReason.DREAM), CoopPick("job", "소방관", null).templateQuestions())
        assertEquals(job.questions("소방관", CoopReason.DREAM), CoopPick("job", "소방관", "???").templateQuestions())
        assertNotEquals(CoopPick("job", "소방관", "done").templateQuestions(), CoopPick("job", "소방관", "soon").templateQuestions())
        assertEquals(emptyList<String>(), CoopPick("nope", "소방관", null).templateQuestions())
    }

    /** 부모 화면의 추천 질문도 우리 귀띔에 걸리면 안 된다 */
    @Test
    fun suggestionsPassOurOwnHints() {
        COOP_SUGGESTIONS.forEach { assertNull(it, questionHint(it)) }
    }
}
