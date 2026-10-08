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

    @Test
    fun aYesLaysOttosPlaceUnderTheChildsDrawing() {
        val art = byteArrayOf(7, 7, 7)
        val asked = mutableListOf<String>()
        live({ place -> asked += place; art }) { d ->
            d.drawOnlyAPerson()
            var offered: String? = null
            d.talkToTheBook { line ->
                when {
                    "어디" in line -> "바닷가 갔어"
                    "배경으로 그려 줄까" in line -> { offered = line; "응" }
                    else -> null
                }
            }
            assertEquals("내가 바닷가를 배경으로 그려 줄까?", offered)
            assertEquals("장소 낱말만 보낸다", listOf("바닷가"), asked)
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

    @Test
    fun aNoKeepsTheWhitePage() {
        var called = false
        live({ called = true; byteArrayOf(1) }) { d ->
            d.drawOnlyAPerson()
            d.talkToTheBook { line -> when { "어디" in line -> "바닷가 갔어"; "배경으로 그려 줄까" in line -> "아니"; else -> null } }
            assertFalse(called)
            assertNull(d.s.diaryDay.ottoBackdrop())
        }
    }

    @Test
    fun aDrawingThatDidNotComeKeepsTheWhitePageAndSaysSo() {
        live({ null }) { d ->
            d.drawOnlyAPerson()
            d.talkToTheBook { line ->
                when { "어디" in line -> "바닷가 갔어"; "배경으로 그려 줄까" in line -> "응"; else -> null }
            }
            assertNull(d.s.diaryDay.ottoBackdrop())
            assertTrue(d.s.log.any { "장소 배경" in it && "안 왔다" in it })
            assertTrue(d.s.events.any { it.startsWith("image_request") && "type=background" in it && "result=original" in it })
        }
    }

    @Test
    fun aDrawnBackgroundIsNotOffered() {
        var called = false
        live({ called = true; byteArrayOf(1) }) { d ->
            d.board()
            d.s.drawing += (0..2).map { k -> Stroke(Color.Blue, listOf(Offset(0.02f, 0.6f + k * 0.05f), Offset(0.98f, 0.62f + k * 0.05f))) }
            d.s.diaryDay.catchUp(d.s.drawing)
            assertEquals(PieceRole.BACKGROUND, d.s.diaryDay.pieces.single().role)
            d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "바닷가")
            assertTrue(await { if (d.s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); d.s.stage !is DiaryBoard } != null)
            var offered = false
            d.talkToTheBook { line -> if ("배경으로 그려 줄까" in line) { offered = true; "응" } else if ("어디" in line) "바닷가 갔어" else null }
            assertFalse("아이가 그린 배경이 있는데 또 물었다", offered)
            assertFalse(called)
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
