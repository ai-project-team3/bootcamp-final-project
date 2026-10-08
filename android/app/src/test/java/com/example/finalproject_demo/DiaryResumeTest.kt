package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #335 — 그림을 다 그리고 이야기 질문 중에 🏠 → 「이어서 하기」를 누르면 질문이 아니라 그림 그리기부터 다시 시작했다.
 * `go(Scene.DIARY)` 가 `pictureDiary()` 를 처음부터 돌리고, 그 첫 줄이 새 일기(`newDiaryDay`)를 만들었다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryResumeTest {
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
        assertNotNull(await { s.stage is DiaryStart })
        assertNotNull(await { if (s.stage is DiaryStart) send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard })
    }

    private fun Director.draw(x: Float) {
        s.drawing += Stroke(Color.Red, listOf(Offset(x, .3f), Offset(x + .03f, .45f)))
        s.diaryDay.catchUp(s.drawing)
    }

    private suspend fun Director.finish() {
        assertNotNull(await { if (s.diaryDay.watching) send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk })
    }

    /** 🏠 → 방 → 「이어서 하기」 */
    private suspend fun Director.leaveAndResume() {
        leaveToRoom()
        assertNotNull("방으로 나가지 않았다", await { s.scene == Scene.ADULT && s.stage == Stage.Adult })
        delay(30)
        assertNotNull("이어서 하기가 일기로 돌아가지 않았다", await { send(Reply.Tapped("resume", "이어서 하기")); Thread.sleep(20); s.scene == Scene.DIARY })
    }

    @Test
    fun resumingDuringTheQuestionsGoesBackToTheQuestions() = run { d ->
        d.board()
        d.draw(.3f)
        d.finish()
        val day = d.s.diaryDay
        val strokes = d.s.sceneDrawing.toList() + d.s.drawing.toList()
        assertNotNull(await { d.s.micEnabled })
        d.leaveAndResume()
        assertNotNull("이어서 한 일기가 질문으로 가지 않았다 — 무대=${d.s.stage}", await { d.s.stage is DiaryAsk })
        assertFalse("이어서 했는데 그리기부터 다시 시작했다", d.s.stage is DiaryStart || d.s.stage is DiaryBoard)
        delay(200)
        assertFalse("이어서 했는데 「그릴래?」로 돌아갔다", d.s.stage is DiaryStart)
        assertSame("이어서 했는데 새 일기가 됐다 — 조각 · 이름 · 판정이 사라진다", day, d.s.diaryDay)
        assertEquals("그림이 사라졌다", strokes, d.s.sceneDrawing.toList() + d.s.drawing.toList())
    }

    @Test
    fun resumingWhileDrawingGoesBackToTheBoardWithTheSameLines() = run { d ->
        d.board()
        d.draw(.3f)
        val day = d.s.diaryDay
        assertNotNull(await { day.watching })
        d.leaveAndResume()
        assertNotNull("이어서 한 일기가 그림판으로 가지 않았다 — 무대=${d.s.stage}", await { d.s.stage is DiaryBoard })
        delay(200)
        assertFalse("이어서 했는데 「그릴래?」부터 다시 물었다", d.s.stage is DiaryStart)
        assertSame(day, d.s.diaryDay)
        assertEquals("그린 선이 사라졌다", 1, d.s.drawing.size)
        assertEquals(1, d.s.diaryDay.pieces.size)
    }

    @Test
    fun aNewDiaryAfterLeavingStartsFresh() = run { d ->
        d.board()
        d.draw(.3f)
        d.finish()
        val old = d.s.diaryDay
        d.leaveToRoom()
        assertNotNull(await { d.s.scene == Scene.ADULT && d.s.stage == Stage.Adult })
        delay(30)
        assertNotNull(await { d.send(Reply.Tapped("diary", "오늘 있었던 일로")); Thread.sleep(20); d.s.scene == Scene.DIARY })
        assertNotNull("새 일기가 「그릴래?」부터 시작하지 않았다 — 무대=${d.s.stage}", await { d.s.stage is DiaryStart })
        assertTrue("새 일기가 지난 일기를 이어 썼다", old !== d.s.diaryDay)
        assertTrue(d.s.diaryDay.pieces.isEmpty())
    }
}
