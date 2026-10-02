package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DIARY_BG_FALLBACK
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPartPack
import com.example.finalproject_demo.ui.COOP_ITEMS
import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopKind
import com.example.finalproject_demo.ui.questionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 같이 만들기 — 고른 요소에 맞춘 질문 · 선택지 · 배경 (10-02 사용자 요청).
 * 목록 요소 열다섯 개마다 재료([COOP_ITEMS])가 있고, 그 재료가 우리 질문 규칙을 지키며, 일기 모드에는 번지지 않는다.
 */
class CoopItemTest {
    private val parts = COOP_STEPS.filter { it.required }
    private fun coop(pick: CoopPick?, place: String? = null) =
        DemoState().apply { mode = StoryMode.COOP; coopPick = pick; this.place = place; placeLabel = place }

    @Test
    fun everyListedItemHasMaterialsAndARealBackground() {
        val drawable = File("src/main/res/drawable")
        COOP_KINDS.flatMap { it.items }.forEach { name ->
            val item = assertNotNull("$name: 재료가 없다", COOP_ITEMS[name]).let { COOP_ITEMS.getValue(name) }
            assertTrue("$name: 그림이 없는 배경 ${item.bg}", File(drawable, "${item.bg}.png").exists())
            listOf(item.spots, item.troubles, item.causes, item.fixes).forEach { assertEquals("$name: 선택지는 셋", 3, it.size) }
        }
        assertEquals(COOP_KINDS.flatMap { it.items }.toSet(), COOP_ITEMS.keys)
    }

    @Test
    fun theProblemQuestionNamesTheItemsWorld() {
        val job = coopKind("job")!!
        assertEquals("거기서 불이 나면 소방관은 무슨 일을 할까?", job.questions("소방관", CoopReason.SOON)[1])
        assertEquals("거기서 요리사는 무슨 음식을 만들까?", job.questions("요리사", CoopReason.SOON)[1])
        assertEquals("축구공을 차다가 무슨 일이 있었어?", coopKind("sport")!!.questions("축구", CoopReason.DONE)[1])
        // 직접 쓴 요소는 틀 그대로
        assertEquals("거기서 무슨 일을 할까?", job.questions("선생님", CoopReason.SOON)[1])
        // 다른 줄(어디 · 왜 · 어떻게)은 그대로
        assertEquals("소방관은 어디서 일할까?", job.questions("소방관", CoopReason.SOON)[0])
    }

    @Test
    fun everyItemQuestionAndChoiceRungKeepsOurRules() {
        COOP_KINDS.forEach { k -> k.items.forEach { name -> CoopReason.entries.forEach { r ->
            val s = coop(CoopPick(k.key, name, r.key))
            parts.forEach { step ->
                s.coopPartPack(step)!!.rungs.forEach { q -> assertTrue("$name $r ${step.slot}: $q", questionHint(q) == null) }
            }
        } } }
    }

    @Test
    fun theChoiceRungOffersTheItemsOwnWords() {
        val zoo = coop(CoopPick("place", "동물원", "done"))
        val spots = zoo.coopPartPack(parts[0])!!.rungs.last()
        listOf("사자 우리", "기린 마당", "원숭이 산").forEach { assertTrue(spots, it in spots) }
        assertTrue(spots, spots.endsWith("어디가 좋았어?"))
        val fire = coop(CoopPick("job", "소방관", "dream")).coopPartPack(parts[3])!!.rungs.last()
        assertTrue(fire, "사다리 타고 구하기" in fire)
        // 직접 쓴 요소는 원래 선택지
        assertTrue("입구" in coop(CoopPick("place", "할머니 집", "done")).coopPartPack(parts[0])!!.rungs.last())
    }

    @Test
    fun theBackgroundFollowsThePickedItemFirst() {
        assertEquals("bg_firestation", coop(CoopPick("job", "소방관", "soon")).bgName)
        // 「불 난 집」의 「집」이 일기 낱말에 걸려도 소방서
        assertEquals("bg_firestation", coop(CoopPick("job", "소방관", "done"), place = "불 난 집").bgName)
        assertEquals("bg_space", coop(CoopPick("job", "우주비행사", null)).bgName)
        assertEquals("bg_soccer", coop(CoopPick("sport", "축구", "done")).bgName)
        // 직접 쓴 요소 — 아이가 말한 곳, 그다음 요소 이름
        assertEquals("bg_park", coop(CoopPick("place", "할머니 집", "done"), place = "공원").bgName)
        assertEquals("bg_grandma", coop(CoopPick("place", "할머니 집", "done")).bgName)
        assertEquals(DIARY_BG_FALLBACK, coop(CoopPick("job", "선생님", "soon")).bgName)
        // 고른 이야기 없이 질문만 — 일기처럼 아이가 말한 곳
        assertEquals("bg_playground", coop(null, place = "놀이터").bgName)
    }

    @Test
    fun theDiaryModeIsUntouched() {
        val diary = DemoState().apply { mode = StoryMode.DIARY; coopPick = CoopPick("job", "소방관", "soon"); place = "놀이터"; placeLabel = "놀이터" }
        assertEquals("bg_playground", diary.bgName)
        assertEquals(null, diary.coopPartPack(parts[1]))
    }
}
