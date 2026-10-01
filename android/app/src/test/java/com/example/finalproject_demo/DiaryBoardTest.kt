package com.example.finalproject_demo

import com.example.finalproject_demo.demo.namedNeighborOf
import com.example.finalproject_demo.demo.namedIn
import com.example.finalproject_demo.demo.mergeInto
import com.example.finalproject_demo.demo.touching
import com.example.finalproject_demo.demo.DiaryDay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.addStroke
import com.example.finalproject_demo.demo.boxOf
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.cropFor
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화이트보드 규칙 — 획을 위치로 조각에 묶기 · 그린 부분만 3:1 로 잘라 맞추기 (docs/review/일기모드_0929 03 · 20 · 47) */
class DiaryBoardTest {

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = Stroke(Color.Black, listOf(Offset(x0, y0), Offset(x1, y1)))

    @Test
    fun strokesDrawnApartOnOnePersonStayOnePiece() {
        val d = DemoState().newDiaryDay()
        val head = d.addStroke(line(0.50f, 0.20f, 0.54f, 0.24f))
        val body = d.addStroke(line(0.52f, 0.27f, 0.52f, 0.40f))        // a little gap under the head
        val arm = d.addStroke(line(0.46f, 0.30f, 0.51f, 0.32f))
        assertEquals(head, body)
        assertEquals(head, arm)
        assertEquals(1, d.pieces.size)
    }

    @Test
    fun drawingSomewhereElseStartsANewPiece() {
        val d = DemoState().newDiaryDay()
        val house = d.addStroke(line(0.10f, 0.50f, 0.30f, 0.80f))
        val sun = d.addStroke(line(0.80f, 0.05f, 0.90f, 0.15f))
        assertNotEquals(house, sun)
        assertEquals(2, d.pieces.size)
    }

    @Test
    fun aNamedPieceDoesNotSwallowWhatIsDrawnOverIt() {
        val d = DemoState().newDiaryDay()
        val house = d.addStroke(line(0.10f, 0.40f, 0.40f, 0.80f))
        d.pieces[0] = d.pieces[0].copy(name = "우리 집")
        val person = d.addStroke(line(0.30f, 0.60f, 0.35f, 0.85f))       // drawn in front of the house
        assertNotEquals("a person drawn in front of the named house was absorbed", house, person)
        val leg = d.addStroke(line(0.33f, 0.85f, 0.36f, 0.95f))
        assertEquals("the person's next stroke goes to the person", person, leg)
    }

    @Test
    fun catchUpPiecesStrokesTheBoardAlreadyHas() {
        val d = DemoState().newDiaryDay()
        val drawing = listOf(line(0.1f, 0.1f, 0.2f, 0.2f), line(0.8f, 0.8f, 0.9f, 0.9f))
        assertEquals(2, d.catchUp(drawing))
        assertEquals(0, d.catchUp(drawing))
        assertEquals(2, d.pieces.size)
    }

    @Test
    fun theCropIsThreeToOneOnScreenAndHoldsEveryStroke() {
        val aspect = 2f                                          // the board is twice as wide as tall
        val strokes = listOf(line(0.40f, 0.30f, 0.50f, 0.70f))    // a tall narrow drawing
        val c = cropFor(strokes, aspect)
        assertEquals(3f, c.width * aspect / c.height, 0.001f)
        val b = boxOf(strokes)!!
        assertTrue(c.left <= b.left && c.right >= b.right && c.top <= b.top && c.bottom >= b.bottom)

        val wide = cropFor(listOf(line(0.05f, 0.50f, 0.95f, 0.52f)), aspect)
        assertEquals(3f, wide.width * aspect / wide.height, 0.001f)
    }

    /** 대화로 합치기 — 이름 붙은 조각에 닿은 새 조각 · 다른 조각 이름을 부른 말 · 합치면 선은 그대로 */
    @Test
    fun aPieceCanBeMergedIntoANamedOneByWhatTheChildSays() {
        val day = DiaryDay()
        val house = day.addStroke(diaryLine(.10f, .40f, .30f, .40f, .30f, .80f, .10f, .80f))
        day.pieces[0] = day.pieces[0].copy(name = "우리 집")
        val window = day.addStroke(diaryLine(.15f, .50f, .20f, .50f, .20f, .55f))          // 집 안에 그린 창문
        val mom = day.addStroke(diaryLine(.70f, .30f, .70f, .70f))                          // 멀리 떨어진 사람
        assertTrue("이름 붙은 조각은 안에 그린 선을 빨아들이지 않는다", house != window)
        assertEquals("우리 집", day.namedNeighborOf(day.pieces.first { it.id == window })?.name)
        assertEquals(null, day.namedNeighborOf(day.pieces.first { it.id == mom }))
        assertEquals("우리 집", day.namedIn("우리 집 창문이야", except = window)?.name)
        assertEquals(null, day.namedIn("엄마야", except = mom))
        assertEquals("우리 집", day.namedIn("우리 집에 그렸어", except = window)?.name)
        assertTrue(day.touching(window, house))
        assertFalse("멀리 떨어진 조각이 닿았다고 했다", day.touching(mom, house))
        day.mergeInto(window, house)
        assertEquals(2, day.pieces.size)
        assertEquals(2, day.pieces.first { it.id == house }.strokes.size)
    }

    /**
     * 색을 바꿔 방금 그리던 조각에 이어 그리면 — 그새 이름이 붙었어도 같은 조각. 새 색으로 이어 긋는 선도 붙고,
     * 다른 데에 그으면 거기서 끝난다. 같은 색으로 이름 조각에 닿게 그린 선은 전처럼 새 조각(「더 그린 거야?」) (10-01 진웅)
     */
    @Test
    fun aNewCrayonOnThePieceJustDrawnKeepsDrawingIt() {
        fun line(c: androidx.compose.ui.graphics.Color, vararg xy: Float) =
            com.example.finalproject_demo.demo.Stroke(c, xy.toList().chunked(2).map { androidx.compose.ui.geometry.Offset(it[0], it[1]) })
        val blue = androidx.compose.ui.graphics.Color.Blue
        val red = androidx.compose.ui.graphics.Color.Red
        val day = DiaryDay()
        val house = day.addStroke(line(blue, .10f, .40f, .30f, .40f, .30f, .80f, .10f, .80f))
        day.pieces[0] = day.pieces[0].copy(name = "우리 집")            // 색을 고르는 사이 오또가 묻고 이름이 붙었다
        assertEquals("색을 바꿔 이어 그린 지붕", house, day.addStroke(line(red, .08f, .40f, .20f, .25f, .32f, .40f)))
        assertEquals("새 색으로 이어 긋는 선", house, day.addStroke(line(red, .15f, .30f, .25f, .30f)))
        val far = day.addStroke(line(red, .70f, .30f, .80f, .30f))
        assertNotEquals("멀리 그은 선은 다른 조각", house, far)
        assertNotEquals("다른 데에 그은 뒤 같은 색으로 돌아와 닿은 선은 묻는 쪽(새 조각)", house, day.addStroke(line(red, .29f, .60f, .33f, .62f)))
    }

    /** 이름은 낱말로 불러야 한다 — 10-01 실기기에서 「강아」 조각이 「이건 우리 집 강아지 뽀삐야」에 합쳐졌다 */
    @Test
    fun aNameInsideAnotherWordDoesNotCallThatPiece() {
        val day = DiaryDay()
        day.addStroke(diaryLine(.10f, .40f, .30f, .40f))
        day.pieces[0] = day.pieces[0].copy(name = "강아")
        day.addStroke(diaryLine(.70f, .30f, .90f, .30f))
        day.pieces[1] = day.pieces[1].copy(name = "해")
        val next = day.addStroke(diaryLine(.50f, .80f, .55f, .85f))
        assertEquals(null, day.namedIn("이건 우리 집 강아지 뽀삐야", except = next))
        assertEquals(null, day.namedIn("해님이야", except = next))
        assertEquals("해", day.namedIn("해가 웃고 있어", except = next)?.name)
        assertEquals("강아", day.namedIn("강아 꼬리야", except = next)?.name)
    }

    private fun diaryLine(vararg xy: Float) =
        com.example.finalproject_demo.demo.Stroke(androidx.compose.ui.graphics.Color.Blue, xy.toList().chunked(2).map { androidx.compose.ui.geometry.Offset(it[0], it[1]) })
}
