package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopStats
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #327 §4 — 아이가 오또에게 되물으면 짧게 대답하고 같은 질문을 다시 한다. 그 질문은 칸에도, 거절 목록(사다리 끝에서
 * 칸 값이 된다)에도 들어가지 않고 `/turn` 도 부르지 않는다. 한 걸음에 둘까지 — 셋째는 지금처럼 쉬운 질문.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopQuestionFlowTest {
    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** 무엇을 말해도 물은 칸에 그대로 넣는 판정 — 전에는 아이 질문이 이렇게 칸 값이 됐다 */
    private fun acceptingServer() = StoryTestServer { path, body ->
        if (path != "/turn") JSONObject() else {
            val asked = body.optString("asked_slot").takeIf { it.isNotBlank() && it != "null" }
            val judge = JSONObject().put("reason", "ok")
            if (asked != null) judge.put("slot_1", asked).put("value_1", body.optString("utterance"))
            JSONObject().put("judge", judge)
        }
    }

    private suspend fun Director.toFirstQuestion() {
        go(Scene.ADULT)
        assertNotNull(await(4_000) { s.buttons.any { "같이 만들기" in it.label } })
        s.buttons.first { "같이 만들기" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = CoopPick("place", "놀이공원", "done")       // 고른 이야기가 있어야 새 흐름(coopAskInFlow)이다
        assertNotNull(await(4_000) { s.buttons.any { "카드를 탭" in it.label } })
        s.buttons.first { "카드를 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    /** 마이크가 켜졌을 때 한 번 말하고, 오또가 다음 말을 할 때까지 기다린다 */
    private suspend fun Director.sayOnce(text: String) {
        assertNotNull(await { s.micEnabled })
        val before = s.talk.size
        send(Reply.Spoke(text))
        assertNotNull("「$text」 뒤에 아무 일도 없었다", await { s.talk.size > before && s.micEnabled })
    }

    @Test
    fun aChildsQuestionIsAnsweredThenAskedAgainAndNeverFillsTheSlot() = run { d ->
        val server = acceptingServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.sayOnce("기린은 뭐 먹어?")
            assertNull("아이 질문이 칸에 들어갔다", d.s.place)
            assertTrue("아이 질문에 /turn 을 불렀다", server.requests.none { it.first == "/turn" && it.second.optString("utterance") == "기린은 뭐 먹어?" })
            // 진짜 답이 오면 그대로 간다
            withTimeoutOrNull(10_000) { while (d.s.place == null) { d.send(Reply.Spoke("놀이터에 갔어")); delay(60) } }
            assertEquals("놀이터에 갔어", d.s.place)
            // 대화록 — 오또 질문 · 아이 질문 · 오또 대답(「?」 없음) · 같은 오또 질문 · 아이 답
            val talk = d.s.talk.map { it.who to it.text }
            val i = talk.indexOf("child" to "기린은 뭐 먹어?")
            assertTrue("대화록에 아이 질문이 없다: $talk", i >= 1)
            val asked = talk[i - 1]
            assertEquals("otto", asked.first)
            assertEquals("otto", talk[i + 1].first); assertTrue(talk[i + 1].second, '?' !in talk[i + 1].second)
            assertEquals("같은 질문을 다시 하지 않았다: $talk", asked, talk[i + 2])
            assertEquals("child" to "놀이터에 갔어", talk[i + 3])
            assertEquals(1, d.s.coopStats?.childQuestions?.get("world"))
        } finally { server.close() }
    }

    /** 같은 질문을 계속 해도 — 전에는 거절 목록에 들어가 「같은 음절 = 하고 싶은 말」로 칸에 들어갔다(#327 §1) */
    @Test
    fun theSameQuestionAgainAndAgainNeverBecomesTheSlotValue() = run { d ->
        val server = acceptingServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            withTimeoutOrNull(15_000) {
                while ((d.s.coopStats?.childQuestions?.values?.sum() ?: 0) < 4) { d.send(Reply.Spoke("그게 뭐야?")); delay(60) }
            }
            assertTrue("넷 이상 물었다", (d.s.coopStats?.childQuestions?.values?.sum() ?: 0) >= 4)
            assertNull("아이 질문이 칸 값이 됐다", d.s.place)
            assertTrue(server.requests.none { it.first == "/turn" && it.second.optString("utterance") == "그게 뭐야?" })
            assertTrue("질문은 「몰라」로 세지 않는다", (d.s.coopStats?.dontKnows ?: 0) == 0)
        } finally { server.close() }
    }
}
