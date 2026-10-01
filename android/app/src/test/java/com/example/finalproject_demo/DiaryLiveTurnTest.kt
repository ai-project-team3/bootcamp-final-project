package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.requestDiaryTurn
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * 그림일기 D3 의 서버 판정 (#39 ① · #34) — 판정이 다음에 물을 칸과 질문을 정한다(차별점 2).
 * 서버 없이 판정 응답을 바꿔 끼워 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryLiveTurnTest {

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun verdict(fills: List<Pair<String, String>>, next: String?, ready: Boolean = false, s1: Boolean = false) =
        Server.Verdict("ok", fills, next, null, ready, false, null, false, s1, false, null)

    private fun live(fake: suspend (Server.Turn) -> Server.TurnResult?, block: suspend (Director) -> Unit) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        val real = requestDiaryTurn
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.DIARY)
        requestDiaryTurn = fake
        try { block(d) } finally {
            requestDiaryTurn = real
            Server.liveModes = emptySet()
            Server.base = null
            scope.cancel()
        }
    }

    /** 그림 없이 → D3. 서버 모드처럼 값 없는 말로 답한다 */
    private suspend fun Director.toQuestions() {
        go(Scene.DIARY)
        // D0 에 있을 때만 누른다 — 남은 누름이 D3 첫 질문의 답으로 들어가면 장소를 건너뛴다
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("skip", "그림 없이")); s.stage == DiaryAsk } != null)
    }

    private suspend fun Director.answer(text: String, until: () -> Boolean) {
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", await { send(Reply.Spoke(text)); until() } != null)
    }

    @Test
    fun theVerdictPicksTheNextQuestionAndFillsSlotsAsTheChilds() {
        val asked = mutableListOf<Server.Turn>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> Server.TurnResult(verdict(listOf("place" to "놀이터"), "reaction"),
                    Server.Line("놀이터에 갔구나!", null, "거기서 기분이 어땠어?"))
                else -> Server.TurnResult(verdict(listOf("reaction" to "신났다", "problem" to "미끄럼틀을 탔다"), null, ready = true, s1 = true),
                    Server.Line("신났구나!", null, null))
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "거기서 기분이 어땠어?" }
            assertEquals("diary", asked[0].mode)
            assertEquals("place", asked[0].askedSlot)
            assertEquals("놀이터 갔어", s.slots["place"])
            assertEquals("child", s.slotBy["place"])
            d.answer("미끄럼틀 타서 신났어") { asked.size == 2 && s.stage !is DiaryAsk }
            assertEquals("고정 차례가 아니라 판정이 고른 칸을 물었다", "reaction", asked[1].askedSlot)
            assertEquals("첫 칸의 책 문장은 아이 말 그대로", "미끄럼틀 타서 신났어", s.slots["reaction"])
            assertEquals("둘째 칸은 판정이 채운 값", "미끄럼틀을 탔다", s.slots["problem"])
            assertTrue(s.slotBy.values.all { it == "child" })
            assertEquals("story_ready", s.endReason)
            assertEquals(2, s.diaryDay.turnCalls)
        }
    }

    @Test
    fun whenTheServerFailsTheChildsWordsStayAndTheFixedOrderGoesOn() {
        live({ null }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("어린이집 갔어") { s.line == "거기서 무슨 일이 있었어?" }
            assertEquals("어린이집 갔어", s.slots["place"])
            assertEquals("child", s.slotBy["place"])
        }
    }
}
