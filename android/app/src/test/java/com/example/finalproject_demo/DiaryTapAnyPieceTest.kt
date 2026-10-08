package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.addStroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.demo.pieceAt
import com.example.finalproject_demo.demo.sendBoardTool
import com.example.finalproject_demo.demo.tapBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #302 — the child picks any piece for Otto's drawing: an unnamed one by touching it on the board (Otto asks its name
 * first), any piece on the D3 picture until the book is made, and the same piece again — [OTTO_ORDERS_MAX] times in all.
 * The original stays the default; nothing goes to the server unless the child picks it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryTapAnyPieceTest {
    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun run(block: suspend (Director) -> Unit) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        try { block(d) } finally { scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
    }

    private suspend fun Director.board() {
        go(Scene.DIARY)
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
        s.slots["place"] = "공원"; s.slotBy["place"] = "child"
    }

    private fun Director.draw(x: Float) {
        s.drawing += Stroke(Color.Red, listOf(Offset(x, .3f), Offset(x + .03f, .45f)))
        s.diaryDay.catchUp(s.drawing)
    }

    /** a board button, sent while Otto watches — then wait until the drawing loop took it (it drops input left over from before) */
    private suspend fun Director.tool(value: String) {
        await { s.diaryDay.watching }
        tapBoardTool(value)
        await(1_000) { !s.diaryDay.watching }
    }

    private fun Director.tapBoardTool(value: String) = sendBoardTool(Reply.Tapped(value, value))

    private suspend fun Director.finish() {
        assertTrue(await { if (s.diaryDay.watching) send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
    }

    private suspend fun Director.talkD3(until: () -> Boolean, answer: (String) -> String?) {
        var last = -1
        assertTrue("D3 가 거기까지 가지 않았다 — 말=${s.line}", await(20_000) {
            if (!until() && s.micEnabled && s.lineId != last) { last = s.lineId; send(Reply.Spoke(answer(s.line) ?: "몰라")) }
            until()
        } != null)
    }

    // ── 1 · touching a piece on the board ──────────────────────────

    @Test
    fun aTouchFindsThePieceUnderTheFinger() {
        val day = DemoState().apply { drawingAspect = 1f }.newDiaryDay()
        val left = day.addStroke(Stroke(Color.Red, (0..10).map { Offset(.2f, .3f + it * .02f) }))
        val right = day.addStroke(Stroke(Color.Blue, (0..10).map { Offset(.7f, .3f + it * .02f) }))
        assertEquals(left, day.pieceAt(.21f, .4f)?.id)
        assertEquals(right, day.pieceAt(.69f, .35f)?.id)
        assertNull("an empty spot picks nothing", day.pieceAt(.45f, .9f))
    }

    @Test
    fun aTouchOnAThingOverTheBackgroundPicksTheThing() {
        val day = DemoState().apply { drawingAspect = 1f }.newDiaryDay()
        val ground = day.addStroke(Stroke(Color.Green, (0..40).map { Offset(.05f + it * .022f, .8f) }))
        val dog = day.addStroke(Stroke(Color.Black, (0..10).map { Offset(.5f, .62f + it * .015f) }))
        assertEquals(PieceRole.BACKGROUND, day.pieces.first { it.id == ground }.role)
        assertEquals(dog, day.pieceAt(.5f, .78f)?.id)
        assertEquals(ground, day.pieceAt(.15f, .8f)?.id)
    }

    @Test
    fun anUnnamedPieceTouchedOnTheBoardIsTheOneDrawnAndItsNameIsAskedFirst() = run { d ->
        d.board()
        d.draw(.1f)
        d.draw(.7f)                                            // the last piece — [그려 줘] used to point here
        val first = d.s.diaryDay.pieces.first().id
        await { d.s.diaryDay.watching }
        d.tapBoard(.115f, .37f)
        await(1_000) { !d.s.diaryDay.watching }
        d.tool("drawme")
        assertTrue("Otto did not ask what it is — 말=${d.s.line}", await { d.s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertEquals("Otto asked about another piece than the one touched", first, d.s.diaryDay.askingPiece)
    }
}
