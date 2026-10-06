package com.example.finalproject_demo

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.pieceToPng
import com.example.finalproject_demo.demo.requestRedraw
import com.example.finalproject_demo.demo.requestBackgroundRedraw
import com.example.finalproject_demo.demo.boardToPng
import com.example.finalproject_demo.demo.ottoSpots
import com.example.finalproject_demo.demo.BoardBox
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「오또가 대신 그려 주기」(#32) — 아이가 고른 조각만 PNG 로 만들어 `/image` redraw 로 보내고,
 * 온 그림을 그 조각에 붙인다. 못 그렸으면 원본 그대로.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DiaryRedrawTest {

    private fun line(c: Color, vararg xy: Float) = Stroke(c, xy.toList().chunked(2).map { Offset(it[0], it[1]) })

    @Test
    fun aPieceBecomesATransparentPngOfOnlyWhatWasDrawn() {
        val red = Color(0xFFE8604C)
        // 판 폭이 높이의 2배 — 판 좌표로 정사각형(폭 0.2 × 높이 0.4)은 실제로도 정사각형이다
        val piece = DiaryPiece(0, listOf(line(red, .40f, .30f, .60f, .30f, .60f, .70f, .40f, .70f, .40f, .30f)))
        val png = pieceToPng(piece, aspect = 2f)
        assertNotNull("선이 있는데 PNG 가 안 나왔다", png)
        png!!
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals("긴 변이 512 가 아니다", 512, maxOf(bmp.width, bmp.height))
        assertTrue("가로를 되돌리지 않아 찌그러졌다 (${bmp.width}×${bmp.height})", kotlin.math.abs(bmp.width - bmp.height) < 8)
        assertEquals("가운데는 비어 있어야 한다 — 배경이 투명하지 않다", 0, bmp.getPixel(bmp.width / 2, bmp.height / 2) ushr 24)
        val edge = bmp.getPixel(bmp.width / 2, (bmp.height * 0.02f / 0.44f).toInt() + 2)
        assertTrue("그린 선이 PNG 에 없다", edge ushr 24 > 0)
    }

    @Test
    fun anEmptyPieceIsNotSent() {
        assertNull(pieceToPng(DiaryPiece(0, emptyList()), aspect = 2f))
    }

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    /** 서버 모드처럼 값 없는 말을, 오또가 듣기 시작할 때까지 보낸다 */
    private suspend fun Director.say(text: String, until: () -> Boolean) {
        assertTrue("「$text」가 먹히지 않았다 — 말=${s.line}", await { send(Reply.Spoke(text)); until() } != null)
    }

    private fun live(fake: suspend (ByteArray, String) -> ByteArray?, block: suspend (Director) -> Unit) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        d.s.drawingAspect = 2f
        val real = requestRedraw
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.DIARY)
        requestRedraw = fake
        try { block(d) } finally {
            requestRedraw = real
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
        }
    }

    /** 그리기 → 「뭐 그린 거야?」 → 「우리 집이야」 → 「나도 그려볼까?」 → 「응!」 → 다음 멈춤 */
    private suspend fun Director.askOttoToDrawTheHouse() {
        // 감독은 듣기 전에 온 입력을 버린다 — 먹힐 때까지 누른다
        assertTrue(await { send(Reply.Tapped("draw", "그릴래")); s.buttons.any { "붓이 멈춤" in it.label } } != null)
        s.drawing += line(Color.Blue, .10f, .40f, .30f, .40f, .30f, .80f, .10f, .80f)
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        say("우리 집이야") { "우리 집이구나" in s.line }
        assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
        say("응!") { "나도 그려 볼게" in s.line }
        s.drawing += line(Color.Red, .70f, .20f, .90f, .30f)
        assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
    }

    @Test
    fun aYesSendsOnlyThatPieceAndOttosDrawingArrivesOnTheNextPause() {
        val art = byteArrayOf(1, 2, 3)
        var sent: ByteArray? = null
        var words: String? = null
        live({ png, description -> sent = png; words = description; art }) { d ->
            val s = d.s
            d.go(Scene.DIARY)
            d.askOttoToDrawTheHouse()
            assertTrue("오또 그림을 보여 주지 않았다 — 말=${s.line}", await {
                s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
                "짠!" in s.line
            } != null)
            assertEquals("조각 이름이 아니라 다른 말을 보냈다", "우리 집", words)
            val sentBmp = BitmapFactory.decodeByteArray(sent, 0, sent!!.size)
            assertTrue("보낸 것이 PNG 그림이 아니다", sentBmp != null && sentBmp.width > 0)
            val house = s.diaryDay.pieces.first { it.name == "우리 집" }
            assertTrue("온 그림이 조각에 안 붙었다", house.ottoPng.contentEquals(art))
            assertEquals("고르기 전에 오또 그림으로 바꿨다 — 원본이 기본값이다", PieceLook.ORIGINAL, house.look)
            d.send(Reply.Tapped("otto", "오또 그림"))
            assertTrue(await { s.diaryDay.pieces.first { it.name == "우리 집" }.look == PieceLook.OTTO } != null)
        }
    }

    /**
     * 오또 그림이 다 되면 붓 멈춤을 또 기다리지 않는다 — 아이가 그리기를 멈추고 기다리면 그림이 영영 안 떴다(10-02 실기기).
     * 그리는 중(손가락이 판에) · 말하는 중이면 끝날 때까지 기다렸다가 보여 준다
     */
    @Test
    fun ottosDrawingShowsWhenItArrivesWhileTheChildWaits() {
        live({ _, _ -> delay(200); byteArrayOf(1, 2, 3) }) { d ->
            val s = d.s
            d.go(Scene.DIARY)
            assertTrue(await { d.send(Reply.Tapped("draw", "그릴래")); s.buttons.any { "붓이 멈춤" in it.label } } != null)
            s.drawing += line(Color.Blue, .10f, .40f, .30f, .40f, .30f, .80f, .10f, .80f)
            assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우와, 지금 그리는 건 뭐야?" } != null)
            d.say("우리 집이야") { "우리 집이구나" in s.line }
            assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
            s.diaryDay.penDown = true                                // 오는 사이 다시 긋기 시작했다
            d.say("응!") { "나도 그려 볼게" in s.line }
            delay(600)
            assertTrue("그리는 중에 그림 고르기를 띄웠다", "짠!" !in s.line)
            s.diaryDay.penDown = false                               // 손을 떼고 기다린다 — 붓 멈춤 없이
            assertTrue("다 된 그림을 보여 주지 않았다 — 말=${s.line}", await { "짠!" in s.line } != null)
        }
    }

    @Test
    fun whenOttoCannotDrawItTheChildsOwnStays() {
        live({ _, _ -> null }) { d ->
            val s = d.s
            d.go(Scene.DIARY)
            d.askOttoToDrawTheHouse()
            assertTrue("못 그렸다고 알리지 않았다 — 말=${s.line}", await {
                s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
                "잘 못 그렸어" in s.line
            } != null)
            val house = s.diaryDay.pieces.first { it.name == "우리 집" }
            assertNull(house.ottoPng)
            assertEquals(PieceLook.ORIGINAL, house.look)
            assertTrue("그림 고르기를 띄웠다", s.log.none { "짠!" in it })
        }
    }

    // ── 배경 조각 (#168 · 10-06 진웅) ─────────────────────────────

    /** 배경은 잘라 보내지 않는다 — 판 전체, 선은 그린 자리 그대로(80% 높이의 땅은 PNG 에서도 80%) */
    @Test
    fun aBackgroundBecomesAPngOfTheWholeBoard() {
        val ground = DiaryPiece(0, listOf(line(Color(0xFF5AAA50), .05f, .80f, .95f, .80f)), role = PieceRole.BACKGROUND)
        val png = boardToPng(ground, aspect = 2f)
        assertNotNull(png)
        val bmp = BitmapFactory.decodeByteArray(png, 0, png!!.size)
        assertEquals("판 비율이 아니다 (${bmp.width}×${bmp.height})", 2f, bmp.width / bmp.height.toFloat(), 0.02f)
        assertEquals("위는 투명해야 한다", 0, bmp.getPixel(bmp.width / 2, bmp.height / 4) ushr 24)
        assertTrue("땅 선이 80% 높이에 없다", bmp.getPixel(bmp.width / 2, (bmp.height * 0.8f).toInt()) ushr 24 > 0)
        assertEquals("양 끝까지 잘라 냈다 — 판 왼쪽 2% 는 비어 있어야 한다", 0, bmp.getPixel(bmp.width / 100, (bmp.height * 0.8f).toInt()) ushr 24)
    }

    /** 오또 배경은 판 전체에 깔린다 — 조각 가운데 정사각형이 아니다 */
    @Test
    fun aBackgroundsOttoDrawingCoversTheWholeBoard() {
        val ground = DiaryPiece(0, listOf(line(Color.Green, .05f, .80f, .95f, .82f)), role = PieceRole.BACKGROUND)
        assertEquals(listOf(BoardBox(0f, 0f, 1f, 1f)), ground.ottoSpots())
    }

    /**
     * 땅을 그렸다 → 「여기는 어디야?」 → 「바다야」 → [그려 줘] — 배경 부탁으로 판 전체를 보내고,
     * 온 그림은 그 배경 조각에 붙는다. 물건 부탁은 부르지 않는다
     */
    @Test
    fun drawMeOnABackgroundSendsTheWholeBoardAsABackground() {
        val art = byteArrayOf(9, 9, 9)
        var sent: ByteArray? = null
        var words: String? = null
        var pieceCalls = 0
        live({ _, _ -> pieceCalls++; null }) { d ->
            val s = d.s
            val real = requestBackgroundRedraw
            requestBackgroundRedraw = { png, description -> sent = png; words = description; art }
            try {
                d.go(Scene.DIARY)
                assertTrue(await { d.send(Reply.Tapped("draw", "그릴래")); s.buttons.any { "붓이 멈춤" in it.label } } != null)
                s.drawing += line(Color(0xFF5AAA50), .05f, .80f, .50f, .79f, .95f, .80f)
                assertTrue("말=${s.line}", await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "여기는 어디야?" } != null)
                d.say("바다야") { s.line != "여기는 어디야?" }
                assertTrue(await { s.diaryDay.watching } != null)
                d.send(Reply.Tapped("drawme", "그려 줘"))
                assertTrue("오또 배경을 보여 주지 않았다 — 말=${s.line}", await {
                    s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
                    s.diaryDay.pieces.any { it.ottoPng != null }
                } != null)
                assertEquals("물건 부탁을 불렀다", 0, pieceCalls)
                assertTrue("배경 이름이 아니라 다른 말을 보냈다: $words", words.orEmpty().contains("바다"))
                val bmp = BitmapFactory.decodeByteArray(sent, 0, sent!!.size)
                assertEquals("판 전체가 아니다 (${bmp.width}×${bmp.height})", 2f, bmp.width / bmp.height.toFloat(), 0.02f)
                val bg = s.diaryDay.pieces.first { it.role == PieceRole.BACKGROUND }
                assertTrue("온 그림이 배경 조각에 안 붙었다", bg.ottoPng.contentEquals(art))
                // 이름 없는 배경도 고르게 한다 — 이름이 없다고 건너뛰어 그림이 영영 안 보였다 (10-06 실기기)
                assertTrue("오또 배경을 고르게 하지 않았다 — 말=${s.line}", await { "짠!" in s.line && "바다" in s.line } != null)
                d.send(Reply.Tapped("otto", "오또 그림"))
                assertTrue("오또 배경을 골랐는데 바뀌지 않았다", await {
                    s.diaryDay.pieces.first { it.role == PieceRole.BACKGROUND }.look == PieceLook.OTTO
                } != null)
            } finally { requestBackgroundRedraw = real }
        }
    }
}
