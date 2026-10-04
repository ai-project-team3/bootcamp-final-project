package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopPlan
import com.example.finalproject_demo.demo.CoopPlanStore
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.coopPlanFromJson
import com.example.finalproject_demo.demo.coopPlanToJson
import com.example.finalproject_demo.demo.coopReady
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #98 🔴 — 부모가 [저장하기]로 확정한 같이 만들기 이야기 · 질문이 앱을 껐다 켜도 남는가.
 * 실기기(10-03): 앱을 다시 켜니 소파가 「아직 준비된 이야기가 없어!」였다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopPlanStoreTest {

    /** 폰 저장소 대신 — 「앱을 껐다 켠다」는 같은 저장소에 새 상태를 붙이는 것 */
    private class MemoryStore : CoopPlanStore {
        var raw: String? = null
        override fun load() = raw?.let(::coopPlanFromJson)
        override fun save(pick: CoopPick?, questions: List<String>) {
            raw = if (pick == null && questions.none { it.isNotBlank() }) null else coopPlanToJson(pick, questions)
        }
    }

    @Test
    fun aSavedStoryIsStillThereAfterTheAppRestarts() {
        val store = MemoryStore()
        val before = DemoState().also { CoopPlan.attach(it, store) }
        before.coopPick = CoopPick("job", "소방관", "soon")
        before.parentQuestions += listOf("소방관 아저씨한테 뭐 물어보고 싶어?", "")
        CoopPlan.saved(before)

        val after = DemoState().also { CoopPlan.attach(it, store) }      // 앱을 다시 켰다
        assertEquals(CoopPick("job", "소방관", "soon"), after.coopPick)
        assertEquals(listOf("소방관 아저씨한테 뭐 물어보고 싶어?", ""), after.parentQuestions.toList())
        assertTrue("소파에 이야기가 준비돼 있어야 한다", after.coopReady)
    }

    /** 한 권이 끝나 비우면 폰에서도 지운다 — 오늘 고른 이야기가 내일 또 나오면 안 된다 */
    @Test
    fun aFinishedStoryIsGoneAfterTheAppRestarts() {
        val store = MemoryStore()
        val s = DemoState().also { CoopPlan.attach(it, store) }
        s.coopPick = CoopPick("place", "동물원", null)
        CoopPlan.saved(s)
        s.clearParentQuestions()                                         // 이야기 끝 · 부모 [삭제하기]
        assertNull("비웠는데 폰에 남았다", store.raw)
        val after = DemoState().also { CoopPlan.attach(it, store) }
        assertFalse(after.coopReady)
    }

    @Test
    fun aPickWithoutAReasonAndQuestionsOnlyRoundTrip() {
        val (pick, qs) = coopPlanFromJson(coopPlanToJson(CoopPick("place", "동물원", null), emptyList()))
        assertEquals(CoopPick("place", "동물원", null), pick)
        assertTrue(qs.isEmpty())
        val (none, only) = coopPlanFromJson(coopPlanToJson(null, listOf("오늘 제일 재밌었던 게 뭐였어?")))
        assertNull(none)
        assertEquals(listOf("오늘 제일 재밌었던 게 뭐였어?"), only)
    }
}
