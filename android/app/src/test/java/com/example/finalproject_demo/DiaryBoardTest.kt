package com.example.finalproject_demo

import com.example.finalproject_demo.demo.namedNeighborOf
import com.example.finalproject_demo.demo.namedIn
import com.example.finalproject_demo.demo.mergeInto
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
        day.mergeInto(window, house)
        assertEquals(2, day.pieces.size)
        assertEquals(2, day.pieces.first { it.id == house }.strokes.size)
    }

    private fun diaryLine(vararg xy: Float) =
        com.example.finalproject_demo.demo.Stroke(androidx.compose.ui.graphics.Color.Blue, xy.toList().chunked(2).map { androidx.compose.ui.geometry.Offset(it[0], it[1]) })
}
