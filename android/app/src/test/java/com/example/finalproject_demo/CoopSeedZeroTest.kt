package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 책 점검(10-14 · k17) — 아이가 모든 질문에 「몰라」라고 한 날, 협업 모드인데 「동화 모드로 넘기기」가 나왔다.
 * 부모가 고른 이야기이므로 갈림길은 「오늘은 여기까지」 · 「다른 이야기 골라 볼까?」 둘이고, 다른 이야기는 칸을 비우고 부모 화면으로 간다.
 */
class CoopSeedZeroTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true } ?: false

    private suspend fun Director.tap(part: String): Boolean {
        if (!await(4_000) { s.buttons.any { part in it.label } }) return false
        s.buttons.first { part in it.label }.onClick()
        return true
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel() }
    }

    /** Every question answered 「몰라」 until the fork */
    private suspend fun Director.toTheFork() {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertTrue(await { s.scene == Scene.BESTIARY })
        s.coopPick = CoopPick("place", "동물원", "dream")
        assertTrue(tap("카드를 탭"))
        assertTrue(await { s.scene == Scene.DIARY })
        var answered = -1
        val end = System.currentTimeMillis() + 60_000
        while (s.buttons.none { "오늘은 여기까지" in it.label } && System.currentTimeMillis() < end) {
            if (s.micEnabled && s.lineId != answered && s.line.isNotBlank()) {
                answered = s.lineId
                withTimeoutOrNull(5_000) { while (s.lineId == answered && s.micEnabled) { send(Reply.Spoke("몰라")); delay(60) } }
            }
            delay(10)
        }
        assertTrue("no fork: ${s.line}", s.buttons.any { "오늘은 여기까지" in it.label })
    }

    @Test
    fun aCoopDayWithNoSeedOffersAnotherStoryNotStoryMode() = run { d ->
        d.toTheFork()
        val labels = d.s.buttons.map { it.label }
        assertTrue(labels.toString(), labels.none { "동화 모드" in it })
        assertTrue(labels.toString(), labels.any { "다른 이야기" in it })
        assertTrue("Otto says what the buttons do: ${d.s.line}", "다른 이야기" in d.s.line && "지어 볼까" !in d.s.line)
    }

    @Test
    fun anotherStoryEmptiesTheStepsAndGoesBackToTheParent() = run { d ->
        d.toTheFork()
        d.s.slots["whiteboard"] = "기린"; d.s.slotBy["whiteboard"] = "draw"
        assertTrue(d.tap("다른 이야기"))
        assertTrue("not back at the parent: ${d.s.scene}", await { d.s.scene == Scene.ADULT })
        assertTrue(d.s.slots.toString(), d.s.slots.keys.none { it in setOf("place", "problem", "cause", "solution", "companion", "whiteboard") || it.startsWith("parent") })
        assertNull(d.s.coopPick)
        assertNull(d.s.endReason)
        assertEquals(0, d.s.parentQuestions.size)
    }
}
