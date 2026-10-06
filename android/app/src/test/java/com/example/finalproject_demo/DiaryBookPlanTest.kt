package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPlanPage
import com.example.finalproject_demo.demo.DIARY_PLAN_MAX
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookFromJson
import com.example.finalproject_demo.demo.diaryPagePlan
import com.example.finalproject_demo.demo.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #220 ③ — 일기 책의 쪽은 앱이 정해 `/story` 에 `pages` 로 보낸다(협업이 쓰는 길 · API 그대로).
 * 장소+누구랑 → 무슨 일 → extra 의 말 하나에 한 쪽 → 기분+왜를 한 쪽 → 어떻게 됐나 → 내일.
 * 쪽 종류를 짐작하지 않고 보낸 차례 그대로 받는다 — 어느 쪽이 어느 조각 이야기인지 알 수 있다(4번 PR 의 쪽 확대)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryBookPlanTest {

    private val beach = mapOf(
        "place" to "바닷가", "companion" to "아빠",
        "problem" to "모래성 만들었어 / 파도가 와서 무너졌어",
        "extra" to "아빠가 도와줬어 / 양동이: 물 떠 왔어",
        "reaction" to "속상했어", "cause" to "열심히 만들었으니까",
        "solution" to "다시 크게 만들었어", "keep" to "또 바다 가고 싶어",
    )

    @Test
    fun aFullDayIsPlannedInTheDiaryOrderWithOneExtraSayingAPage() {
        val plan = diaryPagePlan(beach)
        assertEquals(listOf("DEPART", "SHAKE", "RUB", "RUB", "FAIL", "DRAG", "TOGETHER"), plan.map { it.serverKind })
        assertEquals(listOf(null, null, "아빠가 도와줬어", "양동이: 물 떠 왔어", null, null, null), plan.map { it.item })
        assertEquals(DiaryPageKind.REACTION, plan[4].kind)
    }

    @Test
    fun feelingAndWhyShareOnePage() {
        val plan = diaryPagePlan(mapOf("place" to "식물원", "problem" to "구경했어", "reaction" to "지루했어", "cause" to "걸어 다니기만 해서"))
        assertEquals(listOf("DEPART", "SHAKE", "FAIL"), plan.map { it.serverKind })
        assertEquals("까닭만 있어도 그 쪽이다", listOf("DEPART", "FAIL"), diaryPagePlan(mapOf("place" to "집", "cause" to "추워서")).map { it.serverKind })
    }

    @Test
    fun theBookStopsAtEightPagesDroppingExtraSayingsFromTheEnd() {
        val many = beach + ("extra" to (1..9).joinToString(" / ") { "말 $it" })
        val plan = diaryPagePlan(many)
        assertEquals(DIARY_PLAN_MAX, plan.size)
        assertEquals("맺음은 남긴다", "TOGETHER", plan.last().serverKind)
        assertEquals("고정 쪽 다섯 + extra 셋", listOf("말 1", "말 2", "말 3"), plan.mapNotNull { it.item })
    }

    /**
     * 다른 칸이 거의 그대로 말한 extra 의 말은 쪽을 받지 않는다 — 받으면 모델이 같은 뜻을 피하다 빈 쪽을
     * 「그림 속에 모래성과 양동이가 있었어요」처럼 아이가 하지 않은 말로 채웠다 (10-06 측정 P3)
     */
    @Test
    fun anExtraSayingAnotherSlotAlreadySaysGetsNoPage() {
        val plan = diaryPagePlan(mapOf(
            "place" to "바닷가", "problem" to "아빠랑 모래성 만들었어",
            "extra" to "아빠: 모래성 같이 만들었어 / 양동이: 물 떠 왔어 / 아빠가 밀어 줬어",
        ))
        assertEquals(listOf("양동이: 물 떠 왔어", "아빠가 밀어 줬어"), plan.mapNotNull { it.item })
    }

    @Test
    fun anEmptyDayHasNoPlan() {
        assertTrue(diaryPagePlan(emptyMap()).isEmpty())
        assertTrue(diaryPagePlan(mapOf("place" to "  ")).isEmpty())
    }

    private fun input(plan: List<DiaryPlanPage>?, written: List<String>) = DiaryBookInput(
        lines = mapOf("place" to "바닷가", "problem" to "모래성 만들었어", "reaction" to "속상했어", "solution" to "다시 만들었어"),
        by = mapOf("place" to "child", "problem" to "child", "reaction" to "child", "solution" to "child"),
        hasDrawing = false, written = written, missions = false, puzzle = false, plan = plan,
    )

    @Test
    fun theWrittenPagesTakeThePlannedKindsAndCarryTheirSaying() {
        val plan = listOf(
            DiaryPlanPage("DEPART", DiaryPageKind.PLACE), DiaryPlanPage("SHAKE", DiaryPageKind.PROBLEM),
            DiaryPlanPage("RUB", DiaryPageKind.PROBLEM, "양동이: 물 떠 왔어"), DiaryPlanPage("FAIL", DiaryPageKind.REACTION),
        )
        val book = buildDiaryBook(input(plan, listOf("나는 오늘 바닷가에 갔어요.", "모래성을 만들었어요.", "양동이로 물을 떠 왔어요.", "속상했어요.")))
        assertEquals(listOf(DiaryPageKind.PLACE, DiaryPageKind.PROBLEM, DiaryPageKind.PROBLEM, DiaryPageKind.REACTION), book.take(4).map { it.kind })
        assertEquals("양동이: 물 떠 왔어", book[2].item)
    }

    @Test
    fun aPlanThatDoesNotMatchTheWrittenCountFallsBackToGuessing() {
        val plan = listOf(DiaryPlanPage("DEPART", DiaryPageKind.PLACE), DiaryPlanPage("RUB", DiaryPageKind.PROBLEM, "말"))
        val book = buildDiaryBook(input(plan, listOf("나는 오늘 바닷가에 갔어요.", "모래성을 만들었어요.", "속상했어요.")))
        assertTrue("어긋난 쪽 구성의 말을 붙였다", book.all { it.item == null })
    }

    @Test
    fun aSavedDiaryKeepsItsPagePlan() {
        val plan = listOf(DiaryPlanPage("DEPART", DiaryPageKind.PLACE), DiaryPlanPage("RUB", DiaryPageKind.PROBLEM, "양동이: 물 떠 왔어"))
        val book = SavedDiaryBook("p1", "바닷가", "2026-10-06", 2, input(plan, listOf("가", "나")), emptyList())
        val back = diaryBookFromJson(book.toJson()) { _, _ -> null }
        assertEquals(plan, back.input.plan)
        val old = book.toJson().apply { getJSONObject("diary").remove("plan") }
        assertEquals("쪽 구성이 없던 옛 책도 읽힌다", null, diaryBookFromJson(old) { _, _ -> null }.input.plan)
    }
}
