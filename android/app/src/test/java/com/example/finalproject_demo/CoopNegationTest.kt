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
 * #327 ② §5 — the child denies the premise of Otto's question (「아니, 안 좋았어」) or corrects an answer (「회전목마 말고 롤러코스터」).
 * A required step asks once without the premise · a tail step moves on with the slot empty · a parent question keeps the answer as said ·
 * a correction sends only the corrected words to the judge, and overwrites a filled slot only when the judge filled the same slot.
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

    /** A judge that fills the asked slot with the child's words — not 「안 ○○」. For 「A 말고 B」 it fills [corrected] with B */
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

    /** Say it once and wait until Otto asks the next question (an ack has no 「?」) */
    private suspend fun Director.sayOnce(text: String) {
        assertNotNull(await { s.micEnabled })
        val id = s.lineId
        send(Reply.Spoke(text))
        assertNotNull("「$text」 뒤에 다음 질문이 없었다 — ${s.line}", await { s.lineId > id && s.micEnabled && '?' in s.line })
    }

    /** Was the negation ack said — acks are not in the transcript, so the log is checked */
    private fun Director.ottoSaid(text: String) = s.log.any { "ack — negation 「$text」" in it }

    private suspend fun Director.asks(text: String) = await { text in s.line && s.micEnabled }

    /** Required step — 「아니, 안 좋았어」 to 「어디가 제일 좋았어?」 → 「안 좋았구나!」 · the premise-free question · the slot gets the real answer */
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

    /** Without the judge too — 「안 좋았어」 does not become the slot value */
    @Test
    fun withoutTheServerADenialIsNotTheSlotValue() = run { d ->
        d.toFirstQuestion()
        d.sayOnce("아니, 안 좋았어")
        assertNull(d.s.place)
        assertNotNull(d.asks("그럼 어디 갔었어?"))
        d.sayOnce("회전목마")
        assertEquals("회전목마", d.s.place)
    }

    /** Tail step — 「아니, 안 갔어」 to 「누구랑 갔어?」: on to the next step with the slot empty (⚖️3) */
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

    /** Parent question — 「안 줬어」 is an answer the parent wants too: kept as said in the parent slot */
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

    /** Correction — 「회전목마 말고 롤러코스터」: only 「롤러코스터」 goes to the judge, and the filled place slot is overwritten (⚖️4) */
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
            // Not in the companion slot — and not a rejected answer, so it never becomes the slot value at the ladder's end
            assertNotEquals("롤러코스터", d.s.friend)
        } finally { server.close() }
    }
}
