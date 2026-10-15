package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.requestDiaryStory
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #494 — 그리는 중 · 조각 이야기 턴도 대화 기록(`history`)에 남는다. 그래야 다음 턴의 「X가 아니라 Y」가 그 칸을 되돌린다.
 * 10-14 실기기: 조각 이야기로 찬 「강아지가 공을 물고 도망갔다」를 고치자 기록에 그 턴이 없어 판단기가 방금 물은 빈 칸을
 * 짚었다(`retract []` 3/3 · 그 턴을 넣으면 `['problem']` 3/3) — 책에 「강아지」가 남았다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryDrawingTurnHistoryTest {

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun turn(
        fills: List<Pair<String, String>>, ack: String?, question: String? = null,
        act: String? = null, retract: List<String> = emptyList(),
    ): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", JSONObject.NULL).put("story_ready", false)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = ack?.let { JSONObject().put("ack", it).put("expand", JSONObject.NULL).put("question", question ?: JSONObject.NULL) }
        return JSONObject().put("judge", judge).put("line", line ?: JSONObject.NULL)
            .put("act", act ?: JSONObject.NULL).put("retract", JSONArray(retract)).toString()
    }

    /** 서버 모드 그림판 — 이름 붙은 조각들을 놓고 장소는 이미 들었다 */
    private fun board(names: List<String>, fake: (Int, JSONObject) -> String?, block: suspend (Director, MutableList<JSONObject>) -> Unit) = runBlocking {
        val asked = mutableListOf<JSONObject>()
        val http = FakeHttp { path, body -> if (path == "/turn") { val t = JSONObject(body); asked += t; fake(asked.size, t) } else null }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        val realStory = requestDiaryStory
        requestDiaryStory = { _, _, _, _ -> null }
        Server.base = http.base
        Server.liveModes = setOf(StoryMode.DIARY)
        try {
            val s = d.s
            d.go(Scene.DIARY)
            assertTrue(await { s.stage is DiaryStart } != null)
            assertTrue(await { if (s.stage is DiaryStart) d.send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
            s.slots["place"] = "우리 집"; s.slotBy["place"] = "child"
            names.forEachIndexed { i, _ ->
                val x = 0.1f + i * 0.2f
                s.drawing += Stroke(listOf(Color.Blue, Color.Red, Color.Green)[i % 3], listOf(Offset(x, .3f), Offset(x + .05f, .4f)))
            }
            s.diaryDay.catchUp(s.drawing)
            names.forEachIndexed { i, n -> s.diaryDay.pieces[i] = s.diaryDay.pieces[i].copy(name = n) }
            block(d, asked)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            requestDiaryStory = realStory
            Server.liveModes = emptySet()
            Server.base = null
            http.close()
        }
    }

    private suspend fun Director.answer(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled }
            delay(100)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    private fun JSONObject.history(): List<JSONObject> = getJSONArray("history").let { a -> (0 until a.length()).map(a::getJSONObject) }

    /** 10-14 실기기 C판 그대로 — 다 그린 뒤 조각 이야기로 찬 칸을 다음 턴에 고친다 */
    @Test
    fun aPieceStoryAfterDrawingIsInTheHistorySoACorrectionCanTakeItBack() = board(listOf("빨간 공"), { n, _ ->
        when (n) {
            1 -> turn(listOf("problem" to "강아지가 공을 물고 도망갔다"), "강아지가 공을 물고 도망갔구나.")
            2 -> turn(listOf("problem" to "고양이"), "앗, 내가 잘못 알았구나! 고양이였구나.", "누구랑 같이 있었어?",
                act = "repair", retract = listOf("problem"))
            else -> turn(emptyList(), "그렇구나.")
        }
    }) { d, asked ->
        val s = d.s
        assertTrue(await { if (s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
        assertTrue("다 그린 뒤 조각 이야기를 묻지 않았다 — 말=${s.line}", await { s.line.endsWith("이야기 해 줄래?") } != null)
        d.answer("강아지가 공을 물고 도망갔어") { asked.size == 1 && s.slots["problem"] != null }
        d.answer("아니 강아지가 아니라 고양이였어") { asked.size == 2 }
        val h = asked[1].history()
        val piece = h.firstOrNull { it.getJSONObject("child").getString("text") == "강아지가 공을 물고 도망갔어" }
        assertTrue("조각 이야기 턴이 기록에 없다 — ${asked[1].getJSONArray("history")}", piece != null)
        val fill = piece!!.getJSONArray("fills").getJSONObject(0)
        assertEquals("problem", fill.getString("slot"))
        assertEquals("강아지가 공을 물고 도망갔다", fill.getString("value"))
        assertEquals("오또가 실제로 한 말", "강아지가 공을 물고 도망갔구나.", piece.getJSONObject("otto").getString("ack"))
        assertEquals("빨간 공도 그렸네! 빨간 공 이야기 해 줄래?", piece.getJSONObject("otto").getString("question"))
        assertTrue(await { s.slots["problem"] == "고양이" } != null)
        // the piece story also went to the book's `extra` as 「빨간 공: …」 — taken back with the slot, or 「강아지」 came back
        // on another page (#494 · 10-14 device book)
        assertTrue("고친 이야기가 책 재료에 남았다 — extra=${s.slots["extra"]}", "강아지" !in s.slots["extra"].orEmpty())
    }

    /** 그리는 중 붓을 멈췄을 때 물은 조각 이야기 — 오또는 구운 말로 받지만 기록에는 남는다 */
    @Test
    fun aPieceStoryWhileDrawingIsInTheHistory() = board(listOf("아빠", "모래성"), { n, _ ->
        if (n == 1) turn(listOf("problem" to "모래성을 만들었다"), "모래성을 만들었구나.") else turn(emptyList(), "그렇구나.")
    }) { d, asked ->
        val s = d.s
        assertTrue("붓을 멈춰도 조각 이야기를 묻지 않았다 — 말=${s.line}",
            await { if (s.diaryDay.watching) d.send(Reply.Tapped("pause", "붓 멈춤")); s.line.endsWith("뭐 했어?") || s.line.endsWith("이야기 해 줄래?") } != null)
        d.answer("아빠랑 모래성 만들었어") { asked.size == 1 && s.slots["problem"] != null }
        assertTrue(await { if (s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
        assertTrue(await { s.micEnabled } != null)
        d.answer("엄마도 왔어") { asked.size == 2 }
        val piece = asked[1].history().firstOrNull { it.getJSONObject("child").getString("text") == "아빠랑 모래성 만들었어" }
        assertTrue("그리는 중 조각 이야기가 기록에 없다 — ${asked[1].getJSONArray("history")}", piece != null)
        assertEquals("problem", piece!!.getJSONArray("fills").getJSONObject(0).getString("slot"))
        assertEquals("그리는 중엔 구운 말로 받았다", "그렇구나! 계속 그려 봐.", piece.getJSONObject("otto").getString("ack"))
    }

    /** 그리는 중 묻지 않았는데 한 이야기(#220 ①)도 — 덧붙였으면 되돌릴 값(`prev`)은 앞 말이다 */
    @Test
    fun anUntoldStoryWhileDrawingIsInTheHistoryWithWhatItAddedTo() = board(listOf("모래성"), { n, _ ->
        if (n == 1) turn(listOf("problem" to "파도에 무너졌다"), "파도에 무너졌구나!") else turn(emptyList(), "그렇구나.")
    }) { d, asked ->
        val s = d.s
        s.slots["problem"] = "모래성 만들었어"; s.slotBy["problem"] = "child"
        s.diaryDay.pieceStoryAsked += s.diaryDay.pieces.map { it.id }    // 조각 이야기는 이미 물었다 — 아이가 먼저 하는 말만
        for (i in 0 until 4) {
            await { s.micEnabled && s.diaryDay.watching }
            delay(100)
            d.send(Reply.Spoke("파도가 와서 무너졌어"))
            if (await(1_500) { asked.size == 1 } != null) break
        }
        assertEquals(1, asked.size)
        assertTrue(await { s.slots["problem"] != "모래성 만들었어" } != null)
        assertTrue(await { if (s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
        d.answer("엄마도 왔어") { asked.size == 2 }
        val told = asked[1].history().firstOrNull { it.getJSONObject("child").getString("text") == "파도가 와서 무너졌어" }
        assertTrue("묻지 않은 이야기가 기록에 없다 — ${asked[1].getJSONArray("history")}", told != null)
        val fill = told!!.getJSONArray("fills").getJSONObject(0)
        assertEquals("problem", fill.getString("slot"))
        assertEquals("되돌리면 앞 말로", "모래성 만들었어", fill.getString("prev"))
    }
}
