package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryDay
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.pictureCrop
import com.example.finalproject_demo.demo.requestPlaceBackground
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
 * #264 (10-08 진웅) — 배경을 안 그린 일기는 책 쪽이 흰 바탕이었다. 다 그린 뒤 질문(D3)에서 장소를 들으면
 * 「내가 ○○를 배경으로 그려 줄까?」 → 응이면 오또가 그린 색연필 배경이 아이 그림 밑에 깔린다. 아니면 그대로
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryPlaceBackgroundTest {
    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun turn(fills: List<Pair<String, String>>, ack: String): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", JSONObject.NULL).put("story_ready", false).put("s1_reason", false)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = JSONObject().put("ack", ack).put("expand", JSONObject.NULL).put("question", JSONObject.NULL)
        return JSONObject().put("judge", judge).put("line", line).toString()
    }

    /** 서버 모드 — `/turn` 은 장소를 물으면 「바닷가」를 채우고, 나머지는 빈 판정. 배경 그림은 [art] 가 돌려준다 */
    private fun live(art: suspend (String) -> ByteArray?, block: suspend (Director) -> Unit) = runBlocking {
        val http = FakeHttp { path, body ->
            if (path != "/turn") null
            else if (JSONObject(body).optString("asked_slot") == "place") turn(listOf("place" to "바닷가"), "바닷가에 갔구나!")
            else turn(emptyList(), "그렇구나!")
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        d.s.drawingAspect = 2f
        val real = requestPlaceBackground
        requestPlaceBackground = art
        Server.base = http.base
        Server.liveModes = setOf(StoryMode.DIARY)
        try { block(d) } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            requestPlaceBackground = real
            Server.liveModes = emptySet(); Server.base = null
            http.close()
        }
    }

    private suspend fun Director.board() {
        go(Scene.DIARY)
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
    }

    /** 사람 하나를 그리고(이름 「아빠」) 다 그렸다 — 배경은 안 그렸다 */
    private suspend fun Director.drawOnlyAPerson() {
        board()
        s.drawing += Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))
        s.diaryDay.catchUp(s.drawing)
        s.diaryDay.pieces[0] = s.diaryDay.pieces[0].copy(name = "아빠")
        assertTrue(await { if (s.diaryDay.watching) send(Reply.Tapped("done", "완료")); s.stage !is DiaryBoard } != null)
    }

    /** 다 그린 뒤 질문에 답한다 — [answer] 가 정한 말, 없으면 「몰라」. 책이 펼쳐질 때까지 */
    private suspend fun Director.talkToTheBook(answer: (String) -> String?) {
        var last = -1
        assertTrue("책까지 가지 않았다 — 말=${s.line}", await(30_000) {
            if (s.stage !is DiaryPaper && s.micEnabled && s.lineId != last) { last = s.lineId; send(Reply.Spoke(answer(s.line) ?: "몰라")) }
            s.stage is DiaryPaper
        } != null)
    }

    private fun DiaryDay.ottoBackdrop() = pieces.firstOrNull { it.role == PieceRole.BACKGROUND && it.strokes.isEmpty() }

    /** 묻지 않는다 — 장소를 들으면 바로 뒤에서 주문하고, 오면 아이 그림 밑에 깐다 */
    @Test
    fun thePlaceIsOrderedWithoutAskingAndLaidUnderTheChildsDrawing() {
        val art = byteArrayOf(7, 7, 7)
        val asked = mutableListOf<String>()
        live({ place -> asked += place; art }) { d ->
            d.drawOnlyAPerson()
            val lines = mutableListOf<String>()
            d.talkToTheBook { line -> lines += line; if ("어디" in line) "바닷가 갔어" else null }
            assertFalse("배경을 그려 줄지 물었다 — 묻지 않는다(10-08 종훈 결정)", lines.any { "배경" in it })
            assertEquals("장소 낱말만 한 번 보낸다", listOf("바닷가"), asked)
            val bg = d.s.diaryDay.ottoBackdrop()
            assertNotNull("오또 배경이 책에 안 들어갔다", bg)
            assertTrue(bg!!.ottoPng.contentEquals(art))
            assertEquals(PieceLook.OTTO, bg.look)
            assertFalse("오또 배경은 아이가 그린 것이 아니다", "바닷가" in d.s.diaryDay.pieceNames)
            assertFalse("바닷가" in d.s.slots["whiteboard"].orEmpty())
            assertTrue(d.s.events.any { it.startsWith("image_request") && "type=background" in it && "result=generated" in it })
            // 그림 칸이 판 전체를 보여 준다 — 잘라 내면 배경이 밀린다(#269 과 같은 길)
            val crop = pictureCrop(d.s.diaryDay.pieces.toList(), 2f)
            assertTrue("그림 칸이 판 전체가 아니다 — $crop", crop.left <= 0f && crop.right >= 1f && crop.top <= 0f && crop.bottom >= 1f)
        }
    }

    /** 그리는 중에 장소를 들었으면 그때 주문한다 — 다 그린 뒤까지 기다리지 않는다(규칙 8) */
    @Test
    fun aPlaceHeardWhileDrawingIsOrderedThen() {
        val asked = mutableListOf<String>()
        live({ place -> asked += place; byteArrayOf(1) }) { d ->
            d.board()
            d.s.drawing += Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))
            d.s.diaryDay.catchUp(d.s.drawing)
            d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "아빠")
            d.s.quotes += "아빠"
            fun pause() { if (d.s.diaryDay.watching) d.send(Reply.Tapped("pause", "붓 멈춤")) }
            assertTrue("그리는 중에 장소를 묻지 않았다 — 말=${d.s.line}", await { pause(); "어디" in d.s.line } != null)
            var last = -1
            assertTrue("그리는 중에 주문하지 않았다 — 말=${d.s.line}", await(10_000) {
                if (d.s.micEnabled && d.s.lineId != last && "어디" in d.s.line) { last = d.s.lineId; d.send(Reply.Spoke("바닷가 갔어")) }
                pause()
                asked.isNotEmpty()
            } != null)
            assertTrue("다 그리기 전이다", d.s.stage is DiaryBoard)
            assertEquals(listOf("바닷가"), asked)
        }
    }

    /** 장소가 바뀌면 한 번만 다시 주문한다 — 그 뒤로 또 바뀌면 그대로 */
    @Test
    fun aChangedPlaceIsReorderedOnceAtMost() {
        val asked = mutableListOf<String>()
        live({ place -> asked += place; byteArrayOf(1) }) { d ->
            d.board()
            d.s.drawing += Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))
            d.s.diaryDay.catchUp(d.s.drawing)
            d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "아빠")
            d.s.quotes += "아빠"
            fun pause() { if (d.s.diaryDay.watching) d.send(Reply.Tapped("pause", "붓 멈춤")) }
            var last = -1
            for (place in listOf("바닷가", "놀이터", "공원")) {
                d.s.placeLabel = place; d.s.slots["place"] = place; d.s.slotBy["place"] = "child"
                // 오또가 묻는 중이면 그 물음이 끝나야 다음 차례에 주문한다 — 「몰라」로 넘긴다
                await(if (place == "공원") 3_000 else 10_000) {
                    if (d.s.micEnabled && d.s.lineId != last) { last = d.s.lineId; d.send(Reply.Spoke("몰라")) }
                    pause(); asked.lastOrNull() == place
                }
            }
            assertEquals(listOf("바닷가", "놀이터"), asked)
        }
    }

    /** 안 오면(검사 · 늦음 · 실패) 흰 바탕 그대로 — 아이가 부탁한 것이 아니니 미안하다고도 하지 않는다 */
    @Test
    fun aBackgroundThatDidNotComeKeepsTheWhitePage() {
        live({ null }) { d ->
            d.drawOnlyAPerson()
            val lines = mutableListOf<String>()
            d.talkToTheBook { line -> lines += line; if ("어디" in line) "바닷가 갔어" else null }
            assertNull(d.s.diaryDay.ottoBackdrop())
            assertFalse(lines.any { "배경" in it })
            assertTrue(d.s.log.any { "장소 배경" in it && "안 왔다" in it })
            assertTrue(d.s.events.any { it.startsWith("image_request") && "type=background" in it && "result=original" in it })
        }
    }

    /** 아이가 배경을 그렸으면 주문하지 않는다 — 아이 그림을 바꾸지 않는다(차별점 1) */
    @Test
    fun aDrawnBackgroundIsNotOrdered() {
        var called = false
        live({ called = true; byteArrayOf(1) }) { d ->
            d.board()
            d.s.drawing += (0..2).map { k -> Stroke(Color.Blue, listOf(Offset(0.02f, 0.6f + k * 0.05f), Offset(0.98f, 0.62f + k * 0.05f))) }
            d.s.diaryDay.catchUp(d.s.drawing)
            assertEquals(PieceRole.BACKGROUND, d.s.diaryDay.pieces.single().role)
            d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "바닷가")
            assertTrue(await { if (d.s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); d.s.stage !is DiaryBoard } != null)
            d.talkToTheBook { line -> if ("어디" in line) "바닷가 갔어" else null }
            assertFalse(called)
            assertNull(d.s.diaryDay.ottoBackdrop())
        }
    }

    /** 주문한 뒤에 아이가 배경을 그리면 아이 배경이 이긴다 — 오또 것은 버린다 */
    @Test
    fun aBackgroundDrawnAfterTheOrderWins() {
        live({ byteArrayOf(1) }) { d ->
            d.drawOnlyAPerson()
            d.talkToTheBook { line ->
                if ("어디" in line) "바닷가 갔어"
                else {
                    // 주문이 나간 뒤 — 아이 배경 조각이 생겼다
                    if (d.s.diaryDay.placeBg != null && d.s.diaryDay.pieces.none { it.role == PieceRole.BACKGROUND })
                        d.s.diaryDay.pieces += DiaryPiece(99, listOf(Stroke(Color.Blue, listOf(Offset(0.02f, 0.6f), Offset(0.98f, 0.62f)))),
                            name = "바다", role = PieceRole.BACKGROUND)
                    null
                }
            }
            assertNull("아이가 그린 배경이 있는데 오또 배경을 깔았다", d.s.diaryDay.ottoBackdrop())
            assertEquals(1, d.s.diaryDay.pieces.count { it.role == PieceRole.BACKGROUND })
        }
    }

    /** 오또 배경은 아이가 그린 것 목록에 들지 않는다 — 부모 리포트 「그린 것」이 거짓말을 하지 않게(규칙 5) */
    @Test
    fun ottosBackdropIsNotSomethingTheChildDrew() {
        val day = DiaryDay()
        day.pieces += DiaryPiece(0, listOf(Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))), name = "아빠")
        day.pieces += DiaryPiece(1, emptyList(), name = "바닷가", look = PieceLook.OTTO, ottoPng = byteArrayOf(1), role = PieceRole.BACKGROUND)
        assertEquals(listOf("아빠"), day.pieceNames)
    }
}
