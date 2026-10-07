package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #281 — A 를 그리고 쉬지 않고 B 를 그리면 오또는 B 만 묻고 A 는 D3 에서야 이름이 붙어 「오또가 그려 줘」를 고를 기회가 없었다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryLatePieceTest {
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
        s.slots["place"] = "공원"; s.slotBy["place"] = "child"              // 배경 · 장소 질문이 끼지 않게
    }

    private suspend fun Director.tell(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled }
            delay(80)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    private fun Director.pause() { if (s.diaryDay.watching) send(Reply.Tapped("pause", "붓 멈춤")) }

    private fun Director.draw(x: Float) {
        s.drawing += Stroke(Color.Red, listOf(Offset(x, .3f), Offset(x + .03f, .45f)))
        s.diaryDay.catchUp(s.drawing)
    }

    /** 1 — 방금 조각을 다 물은 멈춤에 앞서 그린 이름 없는 조각을 짚어 묻는다. 이름이 붙으면 「나도 그려볼까?」도 */
    @Test
    fun aPieceDrawnBeforeTheLastOneIsAskedAtTheNextQuietPause() = run { d ->
        d.board()
        d.draw(.1f)
        d.draw(.8f)                                                         // 쉬지 않고 다음 조각
        val (a, b) = d.s.diaryDay.pieces.map { it.id }.also { assertEquals(2, it.size) }
        assertTrue(await { d.pause(); d.s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertEquals(b, d.s.diaryDay.askingPiece)
        d.tell("나무야") { "그려볼까" in d.s.line }
        d.tell("아니") { d.s.line == "좋아, 네 그림이 최고야!" }
        assertTrue("앞서 그린 조각을 묻지 않았다 — 말=${d.s.line}", await { d.pause(); d.s.line == "이건 뭐 그린 거야?" } != null)
        assertEquals("그 조각을 짚어 묻는다", a, d.s.diaryDay.askingPiece)
        d.tell("강아지야") { "그려볼까" in d.s.line }
        assertEquals("강아지", d.s.diaryDay.pieces.first { it.id == a }.name)
        assertEquals("나도 강아지를 그려볼까?", d.s.line)
    }

    /** 1 — 지금 그리는 조각에 이름이 없으면 그것이 먼저다. 앞 조각은 그다음 */
    @Test
    fun theCurrentPieceStillComesFirst() = run { d ->
        d.board()
        d.draw(.1f)
        d.draw(.8f)
        assertTrue(await { d.pause(); d.s.diaryDay.askingPiece != null } != null)
        assertEquals(d.s.diaryDay.pieces.last().id, d.s.diaryDay.askingPiece)
    }
}
