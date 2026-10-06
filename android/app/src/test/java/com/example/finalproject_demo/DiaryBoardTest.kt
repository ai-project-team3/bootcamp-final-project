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
import com.example.finalproject_demo.demo.clusters
import com.example.finalproject_demo.demo.ottoSpots
import com.example.finalproject_demo.demo.redrawSample
import com.example.finalproject_demo.demo.redoStroke
import com.example.finalproject_demo.demo.undoStroke
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
     * 오또가 이야기를 마친 조각에는 그 뒤에 그은 선을 몰래 붙이지 않는다 — 크레용을 바꿔 위에 그려도(10-06 실기기 11:04),
     * 이름 없는 조각 곁이어도(10:44). 이야기하는 사이에 그은 선은 전처럼 그 조각에 이어 그린 것이다
     */
    @Test
    fun aStrokeAfterOttoTalkedAboutAPieceStartsANewOne() {
        fun line(c: Color, vararg xy: Float) = Stroke(c, xy.toList().chunked(2).map { Offset(it[0], it[1]) })
        val day = DiaryDay()
        val trees = day.addStroke(line(Color.Blue, .50f, .50f, .80f, .50f, .80f, .95f))
        day.pieces[0] = day.pieces[0].copy(name = "나무들")
        day.talkedAbout(trees, strokesSoFar = 1)
        assertNotEquals("크레용을 바꿔 위에 그린 새 그림이 「나무들」에 붙었다", trees, day.addStroke(line(Color.Red, .40f, .65f, .64f, .90f)))

        val day2 = DiaryDay()
        val tree = day2.addStroke(line(Color.Green, .60f, .40f, .80f, .80f))          // 물었지만 답이 없었다(이름 없음)
        day2.talkedAbout(tree, strokesSoFar = 1)
        assertNotEquals("답 없던 조각 곁에 나중에 그린 점이 그 조각에 붙었다", tree, day2.addStroke(line(Color.Green, .74f, .52f, .79f, .57f)))

        val day3 = DiaryDay()
        val house = day3.addStroke(line(Color.Blue, .10f, .40f, .30f, .40f, .30f, .80f))
        day3.talkedAbout(house, strokesSoFar = 2)                                     // 묻는 사이 한 획을 더 그었다
        assertEquals("이야기하는 사이 그은 선은 그 조각에 이어 그린 것", house, day3.addStroke(line(Color.Blue, .12f, .45f, .28f, .45f)))
        assertNotEquals("이야기를 마친 뒤 그은 선은 새 조각", house, day3.addStroke(line(Color.Blue, .14f, .50f, .26f, .50f)))
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

    /** ↶ 마지막 획을 지우면 조각에서도 빠지고, 마지막 획이었으면 조각도 빠진다. ↷ 되살리면 이름까지 그대로 돌아온다 */
    @Test
    fun undoAndRedoKeepPiecesInStep() {
        val day = DiaryDay()
        val drawing = mutableListOf(diaryLine(.10f, .40f, .30f, .40f), diaryLine(.12f, .42f, .28f, .45f), diaryLine(.80f, .30f, .90f, .30f))
        day.catchUp(drawing)
        day.pieces[0] = day.pieces[0].copy(name = "우리 집")
        assertEquals(2, day.pieces.size)

        val far = day.undoStroke(drawing)!!
        assertEquals("멀리 그린 한 획짜리 조각이 남았다", 1, day.pieces.size)
        val inHouse = day.undoStroke(drawing)!!
        assertEquals("집에서 획이 안 빠졌다", 1, day.pieces.single().strokes.size)
        assertEquals("우리 집", day.pieces.single().name)

        day.redoStroke(drawing, inHouse)
        assertEquals(2, day.pieces.single().strokes.size)
        day.redoStroke(drawing, far)
        assertEquals(3, drawing.size)
        assertEquals(2, day.pieces.size)
        assertEquals("조각과 그림판 획 수가 어긋났다", drawing.size, day.pieces.sumOf { it.strokes.size })

        day.undoStroke(drawing); day.undoStroke(drawing); day.undoStroke(drawing)
        assertTrue(drawing.isEmpty() && day.pieces.isEmpty())
        assertEquals(null, day.undoStroke(drawing))
    }

    /** 판을 가로지르는 납작한 선은 배경 — 그 위에 그린 물체를 빨아들이지 않고, 배경선끼리만 묶인다 */
    @Test
    fun aLineAcrossTheBoardIsBackgroundAndKeepsToItself() {
        val day = DiaryDay()
        val ground = day.addStroke(diaryLine(.05f, .85f, .40f, .88f, .95f, .86f))
        assertEquals(com.example.finalproject_demo.demo.PieceRole.BACKGROUND, day.pieces.single().role)
        val tree = day.addStroke(diaryLine(.30f, .50f, .30f, .84f))                 // 땅에 닿게 세운 나무
        assertNotEquals("땅선이 나무를 빨아들였다", ground, tree)
        assertEquals(com.example.finalproject_demo.demo.PieceRole.OBJECT, day.pieces.first { it.id == tree }.role)
        assertEquals("땅을 두 번 그었는데 따로 갈렸다", ground, day.addStroke(diaryLine(.08f, .90f, .92f, .91f)))
        assertNotEquals("하늘선이 땅에 붙었다", ground, day.addStroke(diaryLine(.05f, .10f, .95f, .12f)))
        val short = day.addStroke(diaryLine(.60f, .40f, .75f, .40f))
        assertNotEquals("작은 가로선을 배경으로 봤다", com.example.finalproject_demo.demo.PieceRole.BACKGROUND, day.pieces.first { it.id == short }.role)
    }

    /** 조각 안을 촘촘히 오가는 획은 색칠 — 이름이 붙은 조각이어도 그 조각에 붙는다(묻지 않는다) */
    @Test
    fun scribblingInsideAPieceColorsIt() {
        val day = DiaryDay()
        val house = day.addStroke(diaryLine(.10f, .40f, .30f, .40f, .30f, .80f, .10f, .80f, .10f, .40f))
        day.pieces[0] = day.pieces[0].copy(name = "우리 집")
        val zigzag = (0..12).flatMap { i -> listOf(.13f + (i % 2) * .14f, .45f + i * .025f) }.toFloatArray()
        assertEquals("집 안 색칠이 새 조각이 됐다", house, day.addStroke(diaryLine(*zigzag)))
        assertEquals("이름이 바뀌었다", "우리 집", day.pieces.single().name)
        assertNotEquals("집 안의 짧은 선 하나까지 색칠로 봤다", house, day.addStroke(diaryLine(.18f, .55f, .22f, .58f)))
    }

    /** 같은 색 작은 것을 떨어진 곳에 또 그리면 무리 — 한 조각에 덩어리 여럿. 오또에게는 가장 큰 덩어리 하나만, 그림은 덩어리마다 */
    @Test
    fun scatteredSmallMarksOfOneColorBecomeAGroup() {
        fun mark(c: Color, x: Float, y: Float, r: Float = .04f) = com.example.finalproject_demo.demo.Stroke(c, listOf(Offset(x - r, y), Offset(x, y - r), Offset(x + r, y), Offset(x, y + r), Offset(x - r, y)))
        val day = DiaryDay()
        day.addStroke(mark(Color.Yellow, .10f, .20f))
        day.addStroke(mark(Color.Yellow, .40f, .15f))
        assertEquals("둘은 따로 둔다(엄마 · 아빠 · 두 눈)", 2, day.pieces.size)
        val first = day.addStroke(mark(Color.Yellow, .70f, .25f, .05f))
        val star = day.pieces.single()
        assertEquals(first, star.id)
        assertEquals("넷째도 무리에", first, day.addStroke(mark(Color.Yellow, .25f, .45f)))
        assertEquals(com.example.finalproject_demo.demo.PieceRole.GROUP, star.role)
        assertEquals(4, day.pieces.single().clusters().size)
        assertEquals("오또에게 덩어리 여럿을 보냈다", 1, star.redrawSample().clusters().size)
        assertEquals(4, day.pieces.single().ottoSpots().size)
        assertNotEquals("다른 색까지 무리에 넣었다", first, day.addStroke(mark(Color.Red, .90f, .80f)))
        assertNotEquals("큰 것까지 무리에 넣었다", first, day.addStroke(mark(Color.Yellow, .50f, .70f, .2f)))
    }

    private fun diaryLine(vararg xy: Float) =
        com.example.finalproject_demo.demo.Stroke(androidx.compose.ui.graphics.Color.Blue, xy.toList().chunked(2).map { androidx.compose.ui.geometry.Offset(it[0], it[1]) })
}
