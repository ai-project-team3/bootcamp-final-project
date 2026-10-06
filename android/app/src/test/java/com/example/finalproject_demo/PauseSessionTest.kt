package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⏸ 일시정지 (#125 · 10-05 조장) — 화면이 꺼지거나 ⏸ 를 누르면 아이 차례가 그 자리에 선다.
 * 쉬는 동안 시간이 흘러 「대답 없음」으로 넘어가면, 돌아온 아이는 쉬운 질문 · 카드부터 보게 된다.
 */
class PauseSessionTest {

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        d.s.mode = StoryMode.STORY
        try { block(d) } finally { sup.cancel() }
    }

    @Test
    fun theChildsTurnDoesNotRunOutWhilePaused() = run { d ->
        val q = Question(text = "거기서 누굴 만났어?", kind = Kind.EASY, noCards = true)
        val asked = async { d.ask(q) }
        delay(100)
        d.holdSession()
        assertTrue(d.s.holding)
        // 쉬운 질문은 5초(빨리 감기 0.05초) 뒤 무응답으로 넘어간다 — 쉬는 동안은 넘어가지 않는다
        delay(800)
        assertFalse("쉬는 동안 아이 차례가 끝났다", asked.isCompleted)

        d.resumeSession()
        assertFalse(d.s.holding)
        d.send(Reply.Spoke("공룡"))
        val r = withTimeoutOrNull(5_000) { asked.await() }
        assertNotNull("이어 한 뒤 답을 받지 못했다", r)
    }

    @Test
    fun pausingTwiceOrResumingWithoutPauseChangesNothing() = run { d ->
        d.resumeSession()
        assertFalse(d.s.holding)
        d.holdSession(); d.holdSession()
        assertTrue(d.s.holding)
        d.resumeSession()
        assertEquals(false, d.s.holding)
    }

    @Test
    fun leavingToTheRoomFromThePauseEndsTheHold() = run { d ->
        d.holdSession()
        d.leaveToRoom()
        assertFalse("방으로 나갔는데 아직 쉬는 중", d.s.holding)
    }
}
