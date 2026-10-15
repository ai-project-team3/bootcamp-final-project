package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
 * #323 in co-op — the child's spoken answers carry the story so far (`history`), so the server's decider can tell a correction,
 * a question to Otto or 「뭐라고?」 from an answer. What the app catches itself (negations · child questions) never sends history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopDialogueRepairTest {
    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** A judge that fills the asked slot with the child's words; [acts] maps an utterance to the decider's act */
    private fun server(seen: MutableList<JSONObject>, acts: Map<String, Pair<String, JSONObject?>>) = StoryTestServer { path, body ->
        if (path != "/turn") JSONObject() else {
            seen += body
            val said = body.optString("utterance")
            val asked = body.optString("asked_slot").takeIf { it.isNotBlank() && it != "null" }
            val judge = JSONObject().put("reason", "ok")
            val act = acts[said]
            if (act == null && asked != null) judge.put("slot_1", asked).put("value_1", said)
            JSONObject().put("judge", judge).put("act", act?.first ?: JSONObject.NULL)
                .put("retract", JSONArray(if (act?.first == "repair") listOf("place") else emptyList<String>()))
                .put("line", act?.second ?: JSONObject.NULL)
        }
    }

    private suspend fun Director.toFirstQuestion() {
        go(Scene.ADULT)
        assertNotNull(await(4_000) { s.buttons.any { "같이 만들기" in it.label } })
        s.buttons.first { "같이 만들기" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = CoopPick("place", "놀이공원", "done")
        assertNotNull(await(4_000) { s.buttons.any { "카드를 탭" in it.label } })
        s.buttons.first { "카드를 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    private suspend fun Director.sayOnce(text: String) {
        assertNotNull(await { s.micEnabled })
        val id = s.lineId
        send(Reply.Spoke(text))
        assertNotNull("「$text」 뒤에 다음 질문이 없었다 — ${s.line}", await { s.lineId > id && s.micEnabled && '?' in s.line })
    }

    private fun JSONObject.historyTexts(): List<String> =
        optJSONArray("history")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONObject("child").getString("text") } }.orEmpty()

    /** 「틀렸어」 after the place was filled — the server takes the place back; Otto says sorry, the book loses the wrong place */
    @Test
    fun aCorrectionTheAppDidNotCatchTakesTheSlotBack() = run { d ->
        val seen = mutableListOf<JSONObject>()
        val server = server(seen, mapOf("틀렸어" to ("repair" to null)))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.sayOnce("회전목마")
            assertEquals("회전목마", d.s.place)
            d.sayOnce("틀렸어")
            assertNull("틀렸다는 장소가 책에 남았다", d.s.place)
            assertTrue("구운 사과를 안 했다: ${d.s.log.take(15)}", d.s.log.any { "/turn act repair" in it && "앗, 내가 잘못 알았구나!" in it })
            val asked = seen.last { it.optString("utterance") == "틀렸어" }
            assertEquals("기록에 앞 답이 없다", listOf("회전목마"), asked.historyTexts())
            assertTrue("고친 말이 거절된 답으로 남았다", "틀렸어" !in d.s.slots.values)
        } finally { server.close() }
    }

    /** A question to Otto the app did not catch — Otto answers with the server's line, the same step is asked again, no slot is filled */
    @Test
    fun aQuestionToOttoIsAnsweredAndTheStepIsAskedAgain() = run { d ->
        val seen = mutableListOf<JSONObject>()
        val line = JSONObject().put("ack", "오또는 나이가 없는 이야기 친구야!").put("expand", JSONObject.NULL).put("question", JSONObject.NULL)
        val server = server(seen, mapOf("오또 몇 살" to ("answer_back" to line)))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.sayOnce("오또 몇 살")
            assertTrue("서버 대답을 안 했다: ${d.s.log.take(15)}", d.s.log.any { "/turn act answer_back" in it && "나이가 없는" in it })
            assertNull("오또에게 한 질문이 장소 칸에 들어갔다", d.s.place)
            d.sayOnce("회전목마")
            assertEquals("회전목마", d.s.place)
            assertTrue("첫 요청에 기록이 없다", seen.first().has("history"))
        } finally { server.close() }
    }
}
