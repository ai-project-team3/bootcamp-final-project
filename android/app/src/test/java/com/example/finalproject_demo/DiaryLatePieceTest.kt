package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.alsoThereIn
import com.example.finalproject_demo.demo.awaitLateArt
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.drawRequestName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * #281 — A 를 그리고 쉬지 않고 B 를 그리면 오또는 B 만 묻고 A 는 D3 에서야 이름이 붙어 「오또가 그려 줘」를 고를 기회가 없었다.
 * 판에 없는 이름으로 「○○ 그려줘」 하면 방금 그리던 다른 조각을 그렸다 · D3 에 새로 나온 물건은 글로만 남았다
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

    /** 4 — 판에 없는 이름으로 「○○ 그려줘」 — 다른 조각을 대신 그리지 않고 먼저 그려 달라고 한다 */
    @Test
    fun aDrawRequestForSomethingNotOnTheBoardAsksTheChildToDrawItFirst() = run { d ->
        d.board()
        d.draw(.1f)
        d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "나무")
        d.tell("강아지 그려줘") { "먼저 그려 줄래" in d.s.line }
        assertEquals("강아지를 먼저 그려 줄래? 그다음에 나도 그려 볼게.", d.s.line)
        assertEquals("나무", d.s.diaryDay.pieces[0].name)
    }

    /** 4 — 방금 그리던 조각에 이름이 없으면 그 조각을 부른 것이다 — 그 이름을 붙이고 그린다 */
    @Test
    fun aDrawRequestNamesThePieceBeingDrawn() = run { d ->
        d.board()
        d.draw(.1f)
        d.tell("강아지 그려줘") { "그려 볼게" in d.s.line }
        assertEquals("강아지", d.s.diaryDay.pieces[0].name)
    }

    @Test
    fun theNameInADrawRequest() {
        assertEquals("강아지", drawRequestName("강아지 그려줘"))
        assertEquals("강아지", drawRequestName("강아지도 그려줘"))
        assertEquals("강아지", drawRequestName("오또야 강아지 좀 그려 줘"))
        assertEquals("포도", drawRequestName("포도 그려줘"))
        assertNull(drawRequestName("너도 그려줘!"))
        assertNull(drawRequestName("이거 그려줘"))
        assertNull(drawRequestName("이거도 그려줘"))
        assertNull(drawRequestName("그려줘"))
    }

    /** 다 그린다 — 붓 멈춤 없이 바로(조각은 이름 없이 남는다) */
    private suspend fun Director.finish() {
        assertTrue(await { if (s.diaryDay.watching) send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
    }

    /** D3 질문에 답한다 — [answer] 가 정한 말, 없으면 「몰라」. [until] 이 될 때까지 */
    private suspend fun Director.talkD3(until: () -> Boolean, answer: (String) -> String?) {
        var last = -1
        assertTrue("D3 가 거기까지 가지 않았다 — 말=${s.line}", await(20_000) {
            if (!until() && s.micEnabled && s.lineId != last) { last = s.lineId; send(Reply.Spoke(answer(s.line) ?: "몰라")) }
            until()
        } != null)
    }

    /** 2 — 다 그린 뒤 이름이 붙은 조각도 「나도 그려볼까?」 — 응이면 D3 가 끝난 뒤 그림판에서 고른다 */
    @Test
    fun aPieceNamedAfterDrawingIsOfferedAndPickedBeforeTheBook() = run { d ->
        d.board()
        d.draw(.3f)
        d.finish()
        val id = d.s.diaryDay.pieces.single().id
        var offered = false
        d.talkD3({ (d.s.stage as? DiaryBoard)?.pick == id }) { line ->
            when (line) {
                "이건 뭐 그린 거야?" -> "강아지야"
                "나도 강아지를 그려볼까?" -> { offered = true; "응" }
                else -> null
            }
        }
        assertTrue("다 그린 뒤 이름이 붙은 조각에 「나도 그려볼까?」를 묻지 않았다", offered)
        assertEquals("짠! 나도 그려 봤어! 어떤 게 좋아?", d.s.line)
        assertEquals("고르는 그림판에 아이 원본이 없다", 1, d.s.drawing.size)
        val reactions = d.s.reactions
        val makes = d.s.events.count { it.startsWith("make") }
        d.send(Reply.Tapped("otto", "오또 그림"))
        assertTrue(await { d.s.diaryDay.pieces.single().look == PieceLook.OTTO } != null)
        assertTrue(await { d.s.stage !is DiaryBoard } != null)
        assertEquals("고르기는 탭 한 번 — 그 뒤 그림판을 거둘 때 또 셌다", reactions + 1, d.s.reactions)
        assertEquals("고르기만 했는데 그리기 이벤트가 또 쌓였다", makes, d.s.events.count { it.startsWith("make") })
    }

    /** 늦게 주문한 오또 그림이 여럿이어도 기다림은 한 번 — 조각마다 기다리면 책 앞에서 최악 12초 */
    @Test
    fun lateDrawingsAreWaitedForOnceInAll() = runBlocking {
        val never = listOf(CompletableDeferred<ByteArray?>(), CompletableDeferred(), CompletableDeferred())
        val ready = CompletableDeferred<ByteArray?>(byteArrayOf(1))
        val t0 = System.nanoTime()
        awaitLateArt(never + ready, 300)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("세 그림을 따로 기다렸다 — ${ms}ms", ms < 600)
        assertTrue(ready.isCompleted)
    }

    /** 한 그림이 실패해도 나머지는 기다린다 */
    @Test
    fun aFailedLateDrawingDoesNotStopTheWait() = runBlocking {
        val failed = CompletableDeferred<ByteArray?>().apply { completeExceptionally(IllegalStateException("no")) }
        val late = CompletableDeferred<ByteArray?>()
        launch { delay(50); late.complete(byteArrayOf(2)) }
        awaitLateArt(listOf(failed, late), 1_000)
        assertTrue(late.isCompleted)
    }

    /** 2 — 「아니」면 원본 그대로 — 고르기 화면이 뜨지 않는다 */
    @Test
    fun noToTheLateOfferKeepsTheOriginal() = run { d ->
        d.board()
        d.draw(.3f)
        d.finish()
        d.talkD3({ d.s.line == "좋아, 네 그림이 최고야!" }) { line ->
            when (line) { "이건 뭐 그린 거야?" -> "강아지야"; "나도 강아지를 그려볼까?" -> "아니"; else -> null }
        }
        assertTrue(d.s.diaryDay.lateArt.isEmpty())
        assertEquals(PieceLook.ORIGINAL, d.s.diaryDay.pieces.single().look)
    }

    /** 3 — D3 에 판에 없는 물건이 나오면 오또가 그리지 않고 아이에게 그려 보자고 한다. 새로 그린 조각에 그 이름이 붙는다 */
    @Test
    fun somethingNewInTheStoryIsDrawnByTheChild() = run { d ->
        d.board()
        d.draw(.1f)
        d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "나무")
        d.finish()
        d.talkD3({ d.s.stage is DiaryBoard }) { line ->
            when (line) { "강아지도 있었구나! 그려 볼래?" -> "응"; else -> "강아지도 있었어" }
        }
        assertEquals("다시 올린 그림판에 앞서 그린 그림이 없다", 1, d.s.drawing.size)
        val before = d.s.diaryDay.pieces.size
        d.draw(.8f)
        assertTrue(await { d.pause(); d.s.line == "다 그렸어? 더 그릴 거 있어?" } != null)
        // 묻기가 듣기 시작하기 전에 보낸 답은 비워진다 — 질문이 바뀔 때까지 보낸다(그림판은 「응」을 그냥 넘긴다)
        assertTrue(await { if (d.s.line == "다 그렸어? 더 그릴 거 있어?") d.send(Reply.Spoke("응")); d.s.line != "다 그렸어? 더 그릴 거 있어?" } != null)
        val offered = await { "그려볼까" in d.s.line } != null
        assertTrue("새로 그린 조각에 「나도 그려볼까?」를 묻지 않았다 — 말=${d.s.line} · ${d.s.log.take(10).reversed()}", offered)
        assertEquals(before + 1, d.s.diaryDay.pieces.size)
        assertEquals("강아지", d.s.diaryDay.pieces.last().name)
        assertEquals("오또는 판에 없는 것을 그리지 않았다 — 아이가 그린 조각에만 제안", "나도 강아지를 그려볼까?", d.s.line)
    }

    /** 3 — 「아니」면 그림판을 올리지 않고 글로만 */
    @Test
    fun noToDrawingTheNewThingKeepsItInWordsOnly() = run { d ->
        d.board()
        d.draw(.1f)
        d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "나무")
        d.finish()
        var asked = false
        d.talkD3({ asked && d.s.line != "강아지도 있었구나! 그려 볼래?" }) { line ->
            if (line == "강아지도 있었구나! 그려 볼래?") { asked = true; "아니" } else "강아지도 있었어"
        }
        assertTrue(d.s.stage !is DiaryBoard)
        assertEquals(0, d.s.diaryDay.boardAgain)
    }

    @Test
    fun whatCountsAsSomethingNew() {
        assertEquals("강아지", alsoThereIn("강아지도 있었어"))
        assertEquals("할머니", alsoThereIn("공원에 할머니도 왔어"))
        assertEquals("고양이", alsoThereIn("고양이도 봤어요"))
        assertNull(alsoThereIn("나도 봤어"))
        assertNull(alsoThereIn("강아지랑 놀았어"))
        assertNull(alsoThereIn("모래성 만들고 집에 왔어"))
    }
}
