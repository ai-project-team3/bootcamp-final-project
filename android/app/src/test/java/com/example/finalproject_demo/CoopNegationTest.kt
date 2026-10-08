package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPremiseFree
import com.example.finalproject_demo.demo.coopStats
import com.example.finalproject_demo.demo.negationAck
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.CoopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #327 ② §5 — 아이가 오또 질문의 전제를 부정하거나(「아니, 안 좋았어」) 고쳐 말할 때(「회전목마 말고 롤러코스터」).
 * 필수 걸음은 전제 없는 질문으로 한 번 · 꼬리 걸음은 칸 없이 다음 걸음 · 부모 질문 걸음은 그 답 그대로 ·
 * 고쳐 말하기는 고친 말만 판정에 보내고, 이미 찬 칸은 판정도 같은 칸일 때만 덮는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopNegationTest {
    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    @Test
    fun negationAckEchoesTheNegatedWord() {
        assertEquals("안 줬구나!", negationAck("아니, 안 줬어"))
        assertEquals("안 갔구나!", negationAck("안 갔어"))
        assertEquals("기린 없었구나!", negationAck("기린 없었어."))
        assertEquals("안 좋았구나!", negationAck("아니요 안 좋았어"))
        assertNull("이음 끝", negationAck("비가 와서 안 갔어"))
        assertNull("길다", negationAck("우리 오늘 거기 진짜 안 갔어"))
        assertNull("「아니야」만", negationAck("아니야"))
        assertNull("어로 끝나지 않는다", negationAck("안 갔지"))
    }

    @Test
    fun premiseFreeQuestionsHaveNoNamesAndNoChoices() {
        assertEquals("그럼 어디 갔었어?", coopPremiseFree("place", CoopReason.DONE))
        assertEquals("그럼 거기서 뭘 할 거야?", coopPremiseFree("problem", CoopReason.SOON))
        assertEquals("그럼 무엇 때문에 그랬을까?", coopPremiseFree("cause", CoopReason.DREAM))
        assertNull("곧 해요의 까닭은 템플릿 그대로", coopPremiseFree("cause", CoopReason.SOON))
        assertEquals("그럼 그다음엔 어떻게 됐어?", coopPremiseFree("solution", CoopReason.DONE))
        assertNull(coopPremiseFree("detail", CoopReason.DONE))
    }

    /** 물은 칸을 아이 말로 채우는 판정 — 「안 ○○」은 채우지 않는다. 「A 말고 B」면 [corrected] 칸을 B 로 */
    private fun server(corrected: String? = null) = StoryTestServer { path, body ->
        if (path != "/turn") JSONObject() else {
            val said = body.optString("utterance")
            val asked = body.optString("asked_slot").takeIf { it.isNotBlank() && it != "null" }
            val judge = JSONObject().put("reason", "ok")
            when {
                corrected != null && body.optString("question").let { "누구" in it } -> judge.put("slot_1", corrected).put("value_1", said)
                asked != null && "안 " !in said -> judge.put("slot_1", asked).put("value_1", said)
            }
            JSONObject().put("judge", judge)
        }
    }

    private suspend fun Director.toFirstQuestion(parentQuestion: String? = null) {
        go(Scene.ADULT)
        assertNotNull(await(4_000) { s.buttons.any { "같이 만들기" in it.label } })
        s.buttons.first { "같이 만들기" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = CoopPick("place", "놀이공원", "done")
        parentQuestion?.let { s.parentQuestions += it }
        assertNotNull(await(4_000) { s.buttons.any { "카드를 탭" in it.label } })
        s.buttons.first { "카드를 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    /** 한 번 말하고, 오또가 다음 질문을 할 때까지 기다린다(받아주기는 「?」가 없다) */
    private suspend fun Director.sayOnce(text: String) {
        assertNotNull(await { s.micEnabled })
        val id = s.lineId
        send(Reply.Spoke(text))
        assertNotNull("「$text」 뒤에 다음 질문이 없었다 — ${s.line}", await { s.lineId > id && s.micEnabled && '?' in s.line })
    }

    /** 부정 대응 받아주기를 했나 — 받아주기는 대화록에 남지 않아 로그로 본다 */
    private fun Director.ottoSaid(text: String) = s.log.any { "부정 대응 「$text」" in it }

    private suspend fun Director.asks(text: String) = await { text in s.line && s.micEnabled }

    /** 필수 걸음 — 「어디가 제일 좋았어?」에 「아니, 안 좋았어」 → 「안 좋았구나!」 · 전제 없는 질문 · 칸은 진짜 답으로 */
    @Test
    fun aDeniedPremiseOnARequiredStepIsAskedOnceWithoutThePremise() = run { d ->
        val server = server()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            assertTrue(d.s.line, "좋았어" in d.s.line)
            d.sayOnce("아니, 안 좋았어")
            assertTrue("받아주기: ${d.s.log.take(10)}", d.ottoSaid("안 좋았구나!"))
            assertNull(d.s.place)
            assertNotNull("전제 없는 질문이 없다: ${d.s.line}", d.asks("그럼 어디 갔었어?"))
            d.sayOnce("회전목마")
            assertEquals("회전목마", d.s.place)
            assertEquals(1, d.s.coopStats?.childNegations?.get("premise_denied"))
        } finally { server.close() }
    }

    /** 판정이 없을 때도 — 「안 좋았어」가 칸 값이 되지 않는다 */
    @Test
    fun withoutTheServerADenialIsNotTheSlotValue() = run { d ->
        d.toFirstQuestion()
        d.sayOnce("아니, 안 좋았어")
        assertNull(d.s.place)
        assertNotNull(d.asks("그럼 어디 갔었어?"))
        d.sayOnce("회전목마")
        assertEquals("회전목마", d.s.place)
    }

    /** 꼬리 걸음 — 「누구랑 갔어?」에 「아니, 안 갔어」: 칸 없이 다음 걸음으로 (⚖️3) */
    @Test
    fun aDeniedPremiseOnATailStepLeavesTheSlotEmptyAndMovesOn() = run { d ->
        d.toFirstQuestion()
        d.sayOnce("회전목마")
        assertNotNull(await { d.s.micEnabled && "누구" in d.s.line })
        d.sayOnce("아니, 안 갔어")
        assertTrue(d.ottoSaid("안 갔구나!"))
        assertNull(d.s.friend)
        assertNotNull("다음 걸음으로 가지 않았다: ${d.s.line}", await { d.s.micEnabled && "누구" !in d.s.line })
    }

    /** 부모 질문 걸음 — 「안 줬어」도 부모가 알고 싶은 답이다: 부모 질문 칸에 그대로 */
    @Test
    fun aDenialToAParentQuestionIsKeptAsTheAnswer() = run { d ->
        d.toFirstQuestion("기린한테 뭐 줬어?")
        withTimeoutOrNull(20_000) {
            while ("기린한테" !in d.s.line) { if (d.s.micEnabled && '?' in d.s.line) d.sayOnce("회전목마") else delay(10) }
        }
        assertTrue("부모 질문을 묻지 않았다: ${d.s.talk}", "기린한테" in d.s.line)
        d.sayOnce("아니, 안 줬어")
        assertTrue(d.ottoSaid("안 줬구나!"))
        assertEquals("아니, 안 줬어", d.s.slots["parent1"])
    }

    /** 고쳐 말하기 — 「회전목마 말고 롤러코스터」: 판정에는 「롤러코스터」만, 이미 찬 곳 칸을 덮는다(⚖️4) */
    @Test
    fun aCorrectionOverwritesTheFilledSlotWhenTheJudgeAgrees() = run { d ->
        val server = server(corrected = "place")
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.sayOnce("회전목마")
            assertEquals("회전목마", d.s.place)
            assertNotNull(await { d.s.micEnabled && "누구" in d.s.line })
            d.sayOnce("회전목마 말고 롤러코스터")
            assertEquals("롤러코스터", d.s.place)
            assertTrue(d.ottoSaid("아, 롤러코스터구나!"))
            val sent = server.requests.filter { it.first == "/turn" }.map { it.second.optString("utterance") }
            assertTrue("판정에 고친 말만 가지 않았다: $sent", "롤러코스터" in sent && sent.none { "말고" in it })
            // 같이 간 사람 칸에는 넣지 않는다 — 거절 목록에도 없어 사다리 끝에 칸 값이 되지 않는다
            assertNotEquals("롤러코스터", d.s.friend)
        } finally { server.close() }
    }
}
