package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopMissionInBook
import com.example.finalproject_demo.demo.coopMissionProp
import com.example.finalproject_demo.demo.coopMissionResult
import com.example.finalproject_demo.demo.coopWriteBook
import com.example.finalproject_demo.demo.m1Line
import com.example.finalproject_demo.demo.placeWord
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-05 실기기 — 협업 「동물원 다녀왔어요」 책에 아이가 말한 적 없는 먼지(미션 1)와 별(미션 2)이 들어갔고,
 * 미션 안내문이 「목이 긴 기린이 보였어.에는 먼지가 아직 잔뜩 남아 있어」였다.
 * 실제 하루 책은 아이 말에서 나온 물건만 미션 쪽 문장에 넣는다. 미션 놀이는 그대로 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopMissionFitTest {

    private fun zooDay(reason: String) = DemoState().apply {
        mode = StoryMode.COOP
        coopPick = CoopPick("place", "동물원", reason)
        place = "동물원"; problem = "기린이 긴 혀로 내 손을 핥았어"; cause = "기린이 당근이 먹고 싶어서"; solution = "당근을 하나 더 줬어"
        solutionItem = "star"
        listOf("place", "problem", "cause", "solution").forEach { slotBy[it] = "child" }
    }

    @Test
    fun aRealDayWithNothingToRubOrGiveKeepsTheMissionsOutOfTheBook() {
        val s = zooDay("done")
        assertNull(s.coopMissionProp(PageKind.RUB))
        assertNull(s.coopMissionProp(PageKind.DRAG))
        assertFalse(s.coopMissionInBook(PageKind.RUB))
        s.m1Result = "solo"; s.m2Result = "solo"
        assertNull("먼지 결과 문장이 책에 붙었다", s.coopMissionResult(PageKind.RUB))
        assertNull("별 결과 문장이 책에 붙었다", s.coopMissionResult(PageKind.DRAG))
    }

    @Test
    fun whatTheChildSaidBecomesTheMissionObject() {
        val s = zooDay("done").apply { problem = "모래놀이를 하다가 옷에 모래가 묻었어" }
        assertEquals("모래", s.coopMissionProp(PageKind.RUB))
        assertTrue(s.coopMissionInBook(PageKind.RUB))
        s.m1Result = "solo"
        assertEquals("모래가 사라졌어요.", s.coopMissionResult(PageKind.RUB))
    }

    @Test
    fun anImaginedStoryStillPutsTheMissionInTheBook() {
        val s = zooDay("dream")
        assertTrue(s.coopMissionInBook(PageKind.RUB))
        s.m1Result = "solo"
        assertNotNull(s.coopMissionResult(PageKind.RUB))
    }

    @Test
    fun aSentenceInThePlaceSlotIsNotUsedAsAPlaceName() {
        val s = zooDay("done").apply { placeLabel = "목이 긴 기린이 보였어." }
        assertNull(s.placeWord())
        assertFalse("문장에 조사가 붙었다", "보였어.에는" in s.m1Line())
        assertEquals("북극 바다", DemoState().apply { placeLabel = "북극 바다" }.placeWord())
        assertEquals("동물원", DemoState().apply { placeLabel = "동물원" }.placeWord())
        assertNull(DemoState().apply { placeLabel = "할머니 댁에 갔어" }.placeWord())
    }

    // ── /story 요청 ─────────────────────────────────────────────

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    private suspend fun Director.storyPages(): List<JSONObject> {
        val server = StoryTestServer { path, body ->
            if (path != "/story") JSONObject()
            else JSONObject().put("scenes", JSONArray().apply {
                repeat(body.optJSONArray("pages")?.length() ?: 0) { put(JSONObject().put("index", it + 1).put("caption", "서버 문장 ${it + 1}")) }
            })
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            coopWriteBook()
            val pages = server.requests.first { it.first == "/story" }.second.getJSONArray("pages")
            return List(pages.length()) { pages.getJSONObject(it) }
        } finally { server.close() }
    }

    @Test
    fun theStoryRequestCarriesAPropOnlyFromTheChildsWords() = run { d ->
        val z = zooDay("done")
        d.s.mode = z.mode; d.s.coopPick = z.coopPick
        d.s.place = z.place; d.s.problem = "모래놀이를 하다가 옷에 모래가 묻었어"; d.s.cause = z.cause; d.s.solution = z.solution
        d.s.solutionItem = "star"
        listOf("place", "problem", "cause", "solution").forEach { d.s.slotBy[it] = "child" }
        val pages = d.storyPages()
        val rub = pages.first { it.getString("kind") == "RUB" }
        val drag = pages.first { it.getString("kind") == "DRAG" }
        assertEquals("모래", rub.getString("prop"))
        assertTrue("아이가 말하지 않은 별을 보냈다", drag.isNull("prop"))
    }
}
