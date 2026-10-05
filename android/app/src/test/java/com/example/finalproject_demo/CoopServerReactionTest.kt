package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopServerReaction
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
 * 10-05 — 협업도 동화처럼 서버의 받아주기(ack) + 되돌려주기(expand)를 오또가 말한다.
 * 전에는 `/turn` 의 대사 중 다음 질문만 쓰고 ack · expand 를 버려, 반응이 늘 앱의 짧은 한마디였다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopServerReactionTest {

    @Test
    fun ackAndExpandAreSaidTogether() {
        assertEquals("동물원에 갔구나! 사자가 어흥 울었겠다.",
            coopServerReaction(Server.Line("동물원에 갔구나!", "사자가 어흥 울었겠다.", "누구랑 갔어?")))
        assertEquals("그랬구나!", coopServerReaction(Server.Line("그랬구나!", null, null)))
    }

    @Test
    fun aPieceTheAppCannotSayIsDropped() {
        // 묻는 말은 다음 걸음이 한다 · 자리표시자가 남은 말 · 너무 긴 말 · 빈 말
        assertEquals("좋았구나!", coopServerReaction(Server.Line("좋았구나!", "그래서 또 갈까?", null)))
        assertEquals("멋지다!", coopServerReaction(Server.Line("{친구1}랑 갔구나!", "멋지다!", null)))
        assertNull(coopServerReaction(Server.Line(" ", "가".repeat(41), null)))
        assertNull(coopServerReaction(null))
    }

    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** 고른 이야기를 두고 첫 질문(어디)까지 */
    private suspend fun Director.toFirstQuestion(pick: CoopPick) {
        go(Scene.ADULT)
        assertNotNull(await(4_000) { s.buttons.any { "같이 만들기" in it.label } })
        s.buttons.first { "같이 만들기" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = pick
        assertNotNull(await(4_000) { s.buttons.any { "카드를 탭" in it.label } })
        s.buttons.first { "카드를 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    /** 첫 답을 말하고, 그 뒤 오또가 한 말을 모은다 */
    private suspend fun Director.linesAfterFirstAnswer(said: String): List<String> {
        val lines = mutableListOf<String>()
        var lastId = s.lineId
        withTimeoutOrNull(8_000) {
            // 받아주기는 칸이 차기 전에 나온다 — 말하는 동안에도 모은다
            var tick = 0
            while (s.place == null || tick < 100) {
                if (s.place == null && tick % 6 == 0) send(Reply.Spoke(said))
                if (s.place != null) tick++
                if (s.lineId != lastId) { lastId = s.lineId; lines += s.line }
                delay(5)
            }
        }
        return lines
    }

    private fun server(line: JSONObject?) = StoryTestServer { path, body ->
        if (path != "/turn") JSONObject() else {
            val judge = JSONObject().put("reason", "ok")
            val asked = body.optString("asked_slot")
            if (asked.isNotBlank() && asked != "null") judge.put("slot_1", asked).put("value_1", body.optString("utterance"))
            JSONObject().put("judge", judge).also { if (line != null) it.put("line", line) }
        }
    }

    @Test
    fun ottoSaysTheServersAckAndExpandAfterAnAnswer() = run { d ->
        val server = server(JSONObject().put("ack", "동물원에 갔구나!").put("expand", "기린이 목을 쭉 뺐겠다."))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion(CoopPick("place", "동물원", "done"))
            val lines = d.linesAfterFirstAnswer("동물원")
            assertTrue("서버 받아주기를 말하지 않았다: $lines", "동물원에 갔구나! 기린이 목을 쭉 뺐겠다." in lines)
        } finally { server.close() }
    }

    @Test
    fun withoutAServerLineTheAppsOwnAckStays() = run { d ->
        val server = server(null)
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion(CoopPick("place", "동물원", "done"))
            val lines = d.linesAfterFirstAnswer("동물원")
            assertTrue("서버 대사가 없는데 받아주기가 사라졌다: $lines", lines.any { "동물원" in it })
        } finally { server.close() }
    }
}
