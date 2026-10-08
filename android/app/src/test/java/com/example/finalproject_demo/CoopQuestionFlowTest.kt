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
 * #327 §4 — when the child asks Otto back, Otto answers briefly and asks the same question again. The question never goes into a slot
 * or the rejected list (which becomes the slot value at the ladder's end), and `/turn` is not called. Two per step — the third gets the current easier question.
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

    /** A judge that puts whatever is said into the asked slot — this is how a child's question used to become the slot value */
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

    /** Say it once when the mic is on, and wait until Otto speaks next */
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
            // A real answer goes through as before
            withTimeoutOrNull(10_000) { while (d.s.place == null) { d.send(Reply.Spoke("놀이터에 갔어")); delay(60) } }
            assertEquals("놀이터에 갔어", d.s.place)
            // Transcript — Otto's question · the child's question · Otto's answer (no 「?」) · the same Otto question · the child's answer
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

    /** 「없어」 to 「누구랑 갔어?」 is an answer — the companion is 「혼자」 and the next step follows (#327 §3-4 · device 10-08) */
    @Test
    fun nobodyToAWhoQuestionMeansAlone() = run { d ->
        d.toFirstQuestion()
        withTimeoutOrNull(10_000) { while (d.s.place == null) { d.send(Reply.Spoke("회전목마")); delay(60) } }
        assertNotNull(await { d.s.micEnabled && "누구" in d.s.line })
        withTimeoutOrNull(10_000) { while (d.s.friend == null) { d.send(Reply.Spoke("없어")); delay(60) } }
        assertEquals("혼자", d.s.friend)
    }

    /** #341 — recalling (「엄마, 우리 …지?」) neither calls the adult nor waits · it goes back to the child */
    @Test
    fun recallingIsGivenBackToTheChildNotTheParent() = run { d ->
        d.toFirstQuestion()
        d.sayOnce("엄마 우리 어디가 제일 좋았지?")
        val lines = setOf("생각나는 만큼만 말해 줘!", "천천히 떠올려 봐도 돼!")
        assertNotNull("떠올리기 대답이 없다: ${d.s.talk}", await { d.s.talk.any { it.who == "otto" && it.text in lines } })
        val all = d.s.talk.joinToString(" ") { it.text }
        assertTrue("어른을 불렀다: $all", "엄마한테" !in all && "물어봐도" !in all)
        assertNull(d.s.place)
        assertEquals(1, d.s.coopStats?.childQuestions?.get("recall"))
    }

    /** The same question again and again — it used to land in the rejected list and then in the slot as 「same syllables = what the child wants to say」 (#327 §1) */
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
            // The third question passed judge() and stayed as a report quote (#332 review P2)
            assertTrue("아이 질문이 리포트 인용에 남았다: ${d.s.quotes}", "그게 뭐야?" !in d.s.quotes)
        } finally { server.close() }
    }
}
