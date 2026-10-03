package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopLab
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.feelingsSaid
import com.example.finalproject_demo.demo.feelingPhrase
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.demo.feelingThatWas
import com.example.finalproject_demo.demo.coopTitle
import com.example.finalproject_demo.demo.coopFriendsLine
import com.example.finalproject_demo.demo.coopMetLabel
import com.example.finalproject_demo.demo.coopDrawLine
import com.example.finalproject_demo.ui.coopItem
import com.example.finalproject_demo.demo.coopFinishLog
import com.example.finalproject_demo.demo.CoopSource
import com.example.finalproject_demo.demo.coopGuard
import com.example.finalproject_demo.demo.coopReportCopy
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.questionHint
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.mission2
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.isNonAnswer
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.coopWriteBook
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #47 1번 · 2번 — 진짜 마이크 답(글자만 있고 대본 값이 없는 `Reply.Spoke`)이 협업 칸에 들어가는가.
 * 전에는 대본 값 `r.value` 만 읽어 진짜 답이 늘 「몰라」가 되고, 마스코트가 칸을 지어냈다.
 * Robolectric 으로 돈다 — `/turn` 요청 본문을 만드는 `org.json` 이 일반 JVM 에서는 빈 껍데기라 흐름이 조용히 죽는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopLiveAnswerTest {

    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private suspend fun Director.tap(part: String): Boolean {
        if (await(4_000) { s.buttons.any { part in it.label } } == null) return false
        s.buttons.first { part in it.label }.onClick()
        return true
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** 같이 만들기 → 주인공 → 첫 질문(어디)을 기다리는 데까지 */
    private suspend fun Director.toFirstQuestion() {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertNotNull(await { s.scene == Scene.BESTIARY })
        assertEquals(StoryMode.COOP, s.mode)
        s.parentQuestions += "오늘 제일 재밌었던 게 뭐였어?"
        assertTrue(tap("카드를 탭"))
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    /** 고른 이야기만 두고(부모 질문 없이) 첫 질문까지 */
    private suspend fun Director.toFirstQuestionWith(pick: CoopPick) {
        go(Scene.ADULT)
        assertTrue(tap("같이 만들기"))
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = pick
        assertTrue(tap("카드를 탭"))
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull("첫 질문에서 마이크가 안 켜졌다", await { s.micEnabled })
    }

    /**
     * 가짜 /turn — 물은 칸을 아이 말로 채우고, [next] 에 따라 다음 칸 · LLM 질문을 돌려준다.
     * [next] 는 물은 칸 → (다음 칸, 질문)
     */
    private fun llmServer(next: Map<String, Pair<String, String>>) = StoryTestServer { path, body ->
        if (path != "/turn") JSONObject() else {
            val asked = body.optString("asked_slot").takeIf { it.isNotBlank() && it != "null" }
            val judge = JSONObject().put("reason", "ok")
            if (asked != null) judge.put("slot_1", asked).put("value_1", body.optString("utterance"))
            val n = asked?.let(next::get)
            if (n != null) judge.put("next_slot", n.first)
            val out = JSONObject().put("judge", judge)
            if (n != null) out.put("line", JSONObject().put("ack", "그랬구나!").put("question", n.second))
            out
        }
    }

    /** 이번 걸음의 질문이 바뀔 때까지 같은 답을 한다. 바뀐 뒤의 질문을 돌려준다 */
    private suspend fun Director.answer(text: String): String {
        val before = s.line
        withTimeoutOrNull(10_000) { while (s.line == before || !s.micEnabled) { send(Reply.Spoke(text)); delay(60) } }
        assertNotNull("다음 질문으로 안 넘어갔다: ${s.line}", await { s.micEnabled })
        return s.line
    }

    /** 받아쓰기처럼 글자만 보낸다 — 마이크가 듣는 동안 계속 */
    private suspend fun Director.speakUntil(text: String, done: () -> Boolean) {
        withTimeoutOrNull(10_000) {
            while (!done()) { send(Reply.Spoke(text)); delay(60) }
        }
    }

    @Test
    fun shortDontKnowsAreNotAnswersButLongSentencesAre() {
        listOf("몰라", "몰라요.", "모르겠어", "글쎄~", "기억 안 나", "응", "없어").forEach { assertTrue(it, isNonAnswer(it)) }
        listOf("놀이터", "친구가 없어서 슬펐어", "몰래 숨었어", "모래놀이 했어").forEach { assertFalse(it, isNonAnswer(it)) }
    }

    @Test
    fun withoutTheServerTheChildsWordsGoIntoTheSlotAsSaid() = run { d ->
        d.toFirstQuestion()
        d.speakUntil("놀이터에 갔어") { d.s.place != null }
        assertEquals("놀이터에 갔어", d.s.place)
        assertEquals("아이 말인데 출처가 바뀌었다", "child", d.s.slotBy["place"])
    }

    @Test
    fun aDontKnowIsAskedAgainInsteadOfFillingTheSlot() = run { d ->
        d.toFirstQuestion()
        val first = d.s.line
        d.speakUntil("몰라") { d.s.line != first && d.s.micEnabled }
        assertNull("「몰라」가 칸에 들어갔다", d.s.place)
    }

    @Test
    fun withTheServerTurnPicksTheSlotsFromWhatTheChildSaid() = run { d ->
        val server = StoryTestServer { path, _ ->
            if (path != "/turn") JSONObject()
            else JSONObject().put(
                "judge", JSONObject().put("reason", "ok")
                    .put("slot_1", "place").put("value_1", "놀이터")
                    .put("slot_2", "problem").put("value_2", "친구가 밀었어"),
            )
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.speakUntil("놀이터 갔는데 친구가 밀었어") { d.s.place != null }
            assertEquals("놀이터", d.s.place)
            assertEquals("같이 나온 뼈대 칸을 안 채웠다", "친구가 밀었어", d.s.problem)
            assertEquals("child", d.s.slotBy["problem"])
            val turn = server.requests.firstOrNull { it.first == "/turn" }?.second
            assertNotNull("/turn 을 안 불렀다", turn)
            assertEquals("coop", turn!!.optString("mode"))
            assertEquals("place", turn.optString("asked_slot"))
        } finally { server.close() }
    }

    /**
     * 앞 답에서 이미 찬 뼈대 칸은 **다시 묻지 않는다** (10-01).
     * 「놀이터 갔는데 친구가 밀었어」로 「무슨 일」이 찼는데 셋째 걸음에서 「무슨 일이 있었어?」를 또 물으면
     * 아이는 방금 한 말을 되풀이하고, 새 답이 앞 값을 덮는다.
     */
    @Test
    fun aSkeletonSlotFilledByAnEarlierAnswerIsNotAskedAgain() = run { d ->
        var first = true
        val server = StoryTestServer { path, body ->
            if (path != "/turn") JSONObject() else {
                val judge = JSONObject().put("reason", "ok")
                val asked = body.optString("asked_slot")
                if (asked.isNotBlank() && asked != "null") judge.put("slot_1", asked).put("value_1", "새로 한 말")
                // 첫 답에만 「무슨 일」이 같이 나온다
                if (first) { first = false; judge.put("slot_1", "place").put("value_1", "놀이터").put("slot_2", "problem").put("value_2", "친구가 밀었어") }
                JSONObject().put("judge", judge)
            }
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            val asked = mutableListOf<String>()
            withTimeoutOrNull(30_000) {
                while (d.s.slots["detail"] == null && d.s.scene == Scene.DIARY) {
                    if (d.s.micEnabled && asked.lastOrNull() != d.s.line) asked += d.s.line
                    d.send(Reply.Spoke("대답했어")); delay(60)
                }
            }
            assertEquals("놀이터", d.s.place)
            assertTrue("「자세히」 걸음까지 못 갔다: $asked", d.s.slots["detail"] != null)
            assertTrue("이미 찬 「무슨 일」을 또 물었다: $asked", asked.none { "무슨 일" in it })
            assertEquals("앞 답이 덮였다", "친구가 밀었어", d.s.problem)
            assertEquals("child", d.s.slotBy["problem"])
        } finally { server.close() }
    }

    /**
     * #53 A — 서버를 켰으면 협업도 **서버 LLM 이 앞 답을 보고 만든 질문**을 묻는다. 단 꼬리질문 자리에서는 부모 질문이 먼저다.
     * (고른 이야기가 없으면 일기형이라 서버 프롬프트의 과거형과 맞는다)
     */
    @Test
    fun withTheServerTheMascotAsksTheLlmQuestionButParentQuestionsComeFirst() = run { d ->
        val server = llmServer(mapOf(
            "place" to ("companion" to "놀이터에 누구랑 갔어?"),          // 둘째 걸음은 부모 질문 자리 → 이 질문은 버려진다
            "companion" to ("problem" to "엄마랑 놀이터에서 뭐 하고 놀았어?"),
        ))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            assertEquals("부모 질문이 LLM 질문에 밀렸다", "오늘 제일 재밌었던 게 뭐였어?", d.answer("놀이터"))
            assertEquals("셋째 걸음에서 LLM 질문을 안 물었다", "엄마랑 놀이터에서 뭐 하고 놀았어?", d.answer("엄마랑"))
        } finally { server.close() }
    }

    /** 다녀왔어요 — 서버 LLM 질문을 쓰고, /turn 에 고른 이야기와 이유(done)를 싣는다. 이유별 시제는 서버가 가른다(#53 C) */
    @Test
    fun aPastStoryUsesTheLlmQuestionAndSendsThePickedStory() = run { d ->
        val server = llmServer(mapOf("place" to ("companion" to "소방서에서 누구를 제일 먼저 만났어?")))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "done"))
            assertEquals("서버 LLM 질문을 안 물었다", "소방서에서 누구를 제일 먼저 만났어?", d.answer("쉬는 방"))
            val turn = server.requests.first { it.first == "/turn" }.second
            assertEquals("같이 만들기 · 직업 · 소방관 · 체험했어요(지난 일)", turn.optString("template"))
            assertEquals("done", turn.optString("reason"))
        } finally { server.close() }
    }

    /**
     * 「곧 해요」도 이제 서버 LLM 질문을 쓴다 — 서버가 `reason` 으로 질문 시제를 가른다(#53 C `8da67b0`, 배포됨).
     * 앱은 `/turn` 에 고른 이유를 실어 보낸다
     */
    @Test
    fun aFutureStoryAsksTheLlmQuestionAndSendsItsReason() = run { d ->
        val server = llmServer(mapOf("place" to ("companion" to "소방서에 누구랑 같이 가 볼까?")))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            assertEquals("곧 해요인데 서버 LLM 질문을 안 물었다", "소방서에 누구랑 같이 가 볼까?", d.answer("큰 건물"))
            val turn = server.requests.first { it.first == "/turn" }.second
            assertEquals("soon", turn.optString("reason"))
            assertEquals("같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)", turn.optString("template"))
        } finally { server.close() }
    }

    /** 비교용 「지금 방식」 경로도 서버 질문을 갈무리한다 — 조건을 푼 뒤 이 경로만 시제 검사 없이 남지 않게 (#53 review) */
    @Test
    fun theComparisonPathAlsoGuardsAWrongTenseLlmQuestion() = run { d ->
        val server = llmServer(mapOf("place" to ("companion" to "소방서에서 누구를 만났니?")))
        val before = CoopLab.followUps
        try {
            CoopLab.followUps = false
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            val next = d.answer("큰 건물")
            assertTrue("지금 방식에서 곧 해요에 지난 일 질문이 나갔다: $next", next != "소방서에서 누구를 만났니?")
        } finally { CoopLab.followUps = before; server.close() }
    }

    /** 이야기를 안 골랐으면 서버는 「있었던 일」로 묻는다 — 서버 질문은 그 기준(다녀왔어요)으로 갈무리해 단정하는 말을 막는다 */
    @Test
    fun withoutAPickServerLinesAreCheckedAsADayThatHappened() = run { d ->
        // 둘째 걸음은 부모 질문 자리라 셋째 걸음(무슨 일)에 서버 질문이 온다. 「~지?」는 다녀왔어요에서만 막히는 단정 말
        val server = llmServer(mapOf("companion" to ("problem" to "놀이터에서 무슨 일이 있었지?")))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            d.answer("놀이터")
            val third = d.answer("엄마랑")
            assertTrue("있었던 일에 단정하는 서버 말이 나갔다: $third", third != "놀이터에서 무슨 일이 있었지?")
        } finally { server.close() }
    }

    /**
     * 그래도 서버가 「곧 해요」에 지난 일을 물으면(「뭐 했어?」) 말하기 직전 갈무리가 막고 **대본**으로 묻는다 (`CoopGuard` 6번)
     */
    @Test
    fun aPastTenseLlmQuestionInAFutureStoryFallsBackToTheScript() = run { d ->
        val server = llmServer(mapOf("place" to ("companion" to "소방서에서 누구를 만났어?")))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            val next = d.answer("큰 건물")
            assertTrue("곧 해요에 지난 일을 묻는 서버 질문이 나갔다: $next", next != "소방서에서 누구를 만났어?")
            assertTrue("대본 질문으로 안 돌아갔다: $next", "누구랑" in next && "갈" in next)
        } finally { server.close() }
    }

    /** 이야기를 골랐는데 이유가 없으면 `dream`(앱이 상상으로 물었다) · 이야기를 안 골랐으면 비운다(서버는 있었던 일로) — 책(`/story`)과 같은 기준 */
    @Test
    fun theTurnReasonFollowsTheSameRuleAsTheBook() = run { d ->
        val server = llmServer(emptyMap())
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("place", "동물원", null))
            d.answer("사자 우리")
            assertEquals("dream", server.requests.first { it.first == "/turn" }.second.optString("reason"))
        } finally { server.close() }
    }

    @Test
    fun withoutAPickTheTurnSendsNoReason() = run { d ->
        val server = llmServer(emptyMap())
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()                       // 부모 질문만 · 이야기는 안 고름
            d.answer("놀이터")
            assertTrue("이야기를 안 골랐는데 이유가 갔다", server.requests.first { it.first == "/turn" }.second.isNull("reason"))
        } finally { server.close() }
    }

    @Test
    fun withTheServerAnAnswerThatDoesNotFitTheQuestionIsAskedAgain() = run { d ->
        // 「무슨 일」 줄을 「좋아하는 색은?」으로 바꾼 것처럼 — 서버가 이 칸을 못 찾으면 칸에 넣지 않는다
        val server = StoryTestServer { path, _ ->
            if (path != "/turn") JSONObject() else JSONObject().put("judge", JSONObject().put("reason", "ok"))
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestion()
            val first = d.s.line
            d.speakUntil("빨강") { server.requests.any { it.first == "/turn" } && d.s.line != first }
            assertNull("질문에 안 맞는 답이 칸에 들어갔다", d.s.place)
        } finally { server.close() }
    }

    /**
     * 실기기(10-03) — 판정이 우리 질문에 한 아이 답을 거절해도 쉬운 질문으로 **한 번만** 더 묻고,
     * 또 거절되면 마스코트가 「아직 못 들은 ○○」로 짓지 않고 아이가 마지막에 한 말을 그 칸에 넣는다
     */
    @Test
    fun aRealAnswerTheJudgeRejectsTwiceGoesIntoTheSlotInsteadOfTheMascotsGuess() = run { d ->
        val server = StoryTestServer { path, _ ->
            if (path != "/turn") JSONObject() else JSONObject().put("judge", JSONObject().put("reason", "앞으로 할 체험 활동이라 사건이 아님"))
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            d.answer("불 끄기")                                   // 한 번 거절 → 쉬운 질문으로 다시
            assertNull("한 번 거절됐는데 벌써 칸을 채웠다", d.s.place)
            d.speakUntil("소방차 타기") { d.s.place != null }
            assertEquals("아이가 마지막에 한 말이 아니다", "소방차 타기", d.s.place)
            assertEquals("아이 말인데 출처가 바뀌었다", "child", d.s.slotBy["place"])
            assertEquals("마스코트가 지어 채운 것으로 셌다", 0, d.s.mascotPicks)
            val placeTurns = server.requests.count { it.first == "/turn" && it.second.optString("asked_slot") == "place" }
            assertEquals("다시 묻는 건 한 번까지인데 더 물었다", 2, placeTurns)
        } finally { server.close() }
    }

    /** 「몰라」만 했으면 받을 말이 없다 — 지금처럼 사다리 끝에서 마스코트가 채운다 */
    @Test
    fun withOnlyDontKnowsTheMascotStillFillsTheSlot() = run { d ->
        val server = StoryTestServer { path, _ ->
            if (path != "/turn") JSONObject() else JSONObject().put("judge", JSONObject().put("reason", "ok"))
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            d.speakUntil("몰라") { d.s.place != null }
            assertEquals("「몰라」뿐인데 아이 말로 채웠다", "mascot", d.s.slotBy["place"])
        } finally { server.close() }
    }

    /**
     * 실기기(10-03) — 「곧 체험해요」 소방관 책인데 리포트가 「오늘 있었던 일로 · 어른이 넣어 둔 질문으로」라고 적었다.
     * 리포트를 열 때는 `coopPick` 이 비어 있어도(`clearParentQuestions`) 시작할 때 고른 이유대로 말한다
     */
    @Test
    fun theParentReportSpeaksInTheReasonThePickedStoryHad() = run { d ->
        d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
        d.s.clearParentQuestions()
        val soon = d.s.coopReportCopy()
        assertTrue(soon.madeFrom, "앞으로 할 일" in soon.madeFrom && "곧 체험해요" in soon.madeFrom && "오늘 있었던" !in soon.madeFrom)
        assertEquals("고른 이야기 질문에 한 답", soon.askedTitle)
        assertTrue(soon.who, "어른 질문" !in soon.who)
        val cards = soon.playCards!!
        assertEquals(3, cards.size)
        (cards.map { it.trim('"') } + soon.nextQuestion).forEach { q ->
            assertNull("질문 규칙에 걸렸다: $q", questionHint(q))
            assertNotNull("곧 해요에 맞지 않는 말: $q", coopGuard(q, CoopReason.SOON, CoopSource.TEMPLATE).text)
            assertTrue("오늘 있었던 일처럼 묻는다: $q", "오늘" !in q || "지은" in q)
        }
    }

    @Test
    fun aDreamStoryReportAsksImaginingQuestions() = run { d ->
        d.toFirstQuestionWith(CoopPick("place", "동물원", null))
        d.s.clearParentQuestions()
        val dream = d.s.coopReportCopy()
        assertTrue(dream.madeFrom, "상상" in dream.madeFrom)
        (dream.playCards!!.map { it.trim('"') } + dream.nextQuestion).forEach { q ->
            assertNull("질문 규칙에 걸렸다: $q", questionHint(q))
            assertNotNull("상상 이야기에 맞지 않는 말: $q", coopGuard(q, CoopReason.DREAM, CoopSource.TEMPLATE).text)
        }
    }

    /** 이야기를 안 고르고 질문만 넣었으면 지금 말 그대로 — 어른이 넣어 둔 질문으로 지은 책 */
    @Test
    fun withoutAPickTheReportKeepsTheParentQuestionWording() = run { d ->
        d.toFirstQuestion()
        d.s.clearParentQuestions()
        val copy = d.s.coopReportCopy()
        assertEquals("오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요", copy.madeFrom)
        assertEquals("어른이 넣어 둔 질문에 한 답", copy.askedTitle)
        assertNull("일기 모드 놀이 카드를 써야 한다", copy.playCards)
    }

    /**
     * 실기기(10-03) — 곧 해요인데 그리기 안내 「오늘 만난 엄마와 아빠를 그려 줄래?」, 제목 「…에서 만난 엄마와 아빠」,
     * 책 끝 「오늘 만난 친구들이야」, 리포트 「오늘 어디 갔었어?」 · 「질문 0개로 이야기했어요」가 나왔다.
     * 이야기가 끝나 고른 이야기를 비운 뒤(실제 순서)에도 고른 이유대로 말한다
     */
    @Test
    fun aSoonStoryNeverSaysItHappenedToday() = run { d ->
        d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
        d.coopFinishLog()
        val s = d.s
        assertEquals("같이 갈 엄마와 아빠를 그려 줄래?", s.coopDrawLine("엄마와 아빠"))
        assertEquals("같이 갈 사람", s.coopMetLabel())
        val friends = s.coopFriendsLine()!!
        assertTrue(friends, "오늘" !in friends && "친구" !in friends)
        val title = s.autoTitleFor()
        assertTrue(title, "두근두근 소방관 이야기" in title && "만난" !in title)
        val copy = s.coopReportCopy()
        assertEquals("소방관은 어디서 일할까?", copy.firstQuestion)
        assertEquals("부모님이 고른 ‘소방관’ 이야기로 함께 지었어요", copy.together(0))
        assertEquals("어른이 넣어 둔 질문 2개로 이야기했어요", copy.together(2))
        assertNull("질문 규칙에 걸렸다: $friends", questionHint(friends))   // 그리기 안내는 부탁이라 질문 규칙 대상이 아니다
    }

    /** 다녀왔어요는 일기 문구 그대로 — 오늘 있었던 일이 맞다 */
    @Test
    fun aDoneStoryKeepsTheDiaryWording() = run { d ->
        d.toFirstQuestionWith(CoopPick("job", "소방관", "done"))
        d.coopFinishLog()
        assertNull(d.s.coopDrawLine("엄마"))
        assertNull(d.s.coopFriendsLine())
        assertNull(d.s.coopTitle())
    }

    /** 서버 판정의 마음 낱말은 「신나다」 · 「떨려」 · 「무섭다, 신나다」 꼴도 온다 — 「신나다던」이 되지 않게 (실기기 10-03) */
    @Test
    fun feelingWordsReadAsKorean() {
        assertEquals("신났던", feelingThatWas("신났"))
        assertNull(feelingThatWas("신나다"))
        assertEquals("‘떨려’라는", feelingPhrase("떨려"))
        assertEquals("‘기쁨’이라는", feelingPhrase("기쁨"))
        assertEquals("신났던, 기뻤던 마음을 말했어요", feelingsSaid(listOf("신났", "기뻤", "신났")))
        assertEquals("마음을 말했어요 — ‘무섭다’ · ‘신나다’ · ‘떨려’", feelingsSaid(listOf("무섭다, 신나다", "떨려")))
    }

    /** 만 3~7세는 망설인 뒤 「몰라」라고 한다 — 「음… 몰라」가 책 재료 칸에 들어가면 안 된다 (실기기 10-03) */
    @Test
    fun aHesitantDontKnowIsStillADontKnow() {
        listOf("음… 몰라", "어, 모르겠어", "으음 몰라요", "음... 글쎄").forEach { assertTrue(it, isNonAnswer(it)) }
        listOf("아빠", "음… 아빠랑", "어, 소방서").forEach { assertFalse(it, isNonAnswer(it)) }
    }

    /** 협업 책을 만들 수 있게 칸을 채워 둔다 */
    private fun Director.filledCoop() {
        s.mode = StoryMode.COOP
        s.parentQuestions += "오늘 제일 재밌었던 게 뭐였어?"
        s.place = "놀이터"; s.problem = "친구가 밀었어"; s.cause = "줄을 서다가"; s.solution = "같이 미끄럼틀을 탔어"
        listOf("place", "problem", "cause", "solution").forEach { s.slotBy[it] = "child" }
    }

    @Test
    fun withTheServerTheCoopBookUsesTheSentencesItWrote() = run { d ->
        val server = StoryTestServer { path, body ->
            if (path != "/story") JSONObject()
            else {
                val n = body.optJSONArray("pages")?.length() ?: 0
                JSONObject().put("scenes", org.json.JSONArray().apply {
                    repeat(n) { put(JSONObject().put("index", it + 1).put("caption", "서버 문장 ${it + 1}")) }
                })
            }
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            val pages = d.s.template!!.pages.size
            d.coopWriteBook()
            assertEquals(pages, d.s.storyCaptions?.size)
            assertEquals("책 쪽 수가 서버 문장 수와 다르다", pages, d.s.pageCount)
            assertEquals("서버 문장 1", d.s.bookCaption(1))
            val req = server.requests.first { it.first == "/story" }.second
            assertEquals("coop", req.optString("mode"))
            assertEquals("놀이터", req.getJSONObject("slots").optString("place"))
            assertEquals(pages, req.getJSONArray("pages").length())
        } finally { server.close() }
    }

    /** 서버가 /story 요청을 받아 두는 가짜 — 문장은 쪽 수만큼 */
    private fun storyServer() = StoryTestServer { path, body ->
        if (path != "/story") JSONObject()
        else JSONObject().put("scenes", org.json.JSONArray().apply {
            repeat(body.optJSONArray("pages")?.length() ?: 0) { put(JSONObject().put("index", it + 1).put("caption", "서버 문장 ${it + 1}")) }
        })
    }

    /** #52 1번 — 고른 이야기와 이유가 /story 에 실린다. 서버는 이유로 책 시제를 가른다(`77a9d5c`) */
    @Test
    fun theCoopBookRequestCarriesThePickedStoryAndItsReason() = run { d ->
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            d.s.coopPick = CoopPick("job", "소방관", "soon")
            d.coopWriteBook()
            val req = server.requests.first { it.first == "/story" }.second
            assertEquals("soon", req.optString("reason"))
            assertEquals("같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)", req.optString("template"))
        } finally { server.close() }
    }

    /**
     * 실기기(10-03) — 이야기가 끝나면 `coopFinishLog` 가 고른 이야기를 비우고 **그다음에** 책을 만든다.
     * 그래서 책 배경이 기본 배경(`bg_today`)으로, `/story` 에는 이유 · 고른 이야기가 빠진 채(=지난 일)로 갔다.
     * 실제 순서대로 — 비운 뒤에도 책은 고른 요소의 배경 · 고른 이유로 만든다
     */
    @Test
    fun theBookMadeAfterTheStoryEndsKeepsThePickedBackdropAndReason() = run { d ->
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            d.coopFinishLog()                                   // 이야기 끝 — 여기서 coopPick 이 비워진다
            assertNull("이 테스트의 전제(이야기 끝에 고른 이야기를 비운다)가 바뀌었다", d.s.coopPick)
            assertEquals("책 배경이 고른 요소(소방관)의 배경이 아니다", coopItem("소방관")!!.bg, d.s.bgName)
            d.filledCoop()
            d.coopWriteBook()
            val req = server.requests.first { it.first == "/story" }.second
            assertEquals("이야기가 끝난 뒤 /story 에 고른 이유가 빠졌다", "soon", req.optString("reason"))
            assertEquals("같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)", req.optString("template"))
        } finally { server.close() }
    }

    /** #52 2번 — 서버가 쓴 협업 책도 미션을 끝내면 그 쪽에 결과 문장이 붙는다. 끝내기 전에는 서버 문장만 */
    @Test
    fun theServerWrittenCoopBookGetsTheMissionResultAfterTheMission() = run { d ->
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            d.coopWriteBook()
            val s = d.s
            val rub = (1..s.pageCount).first { s.pageKind(it) == PageKind.RUB }
            val drag = (1..s.pageCount).first { s.pageKind(it) == PageKind.DRAG }
            assertEquals("미션 전인데 결과가 붙었다", "서버 문장 $rub", s.bookCaption(rub))
            s.m1Result = "solo"
            s.m2Result = "solo"
            val item = s.mission1().blobName
            assertTrue("미션 1 결과가 안 붙었다: ${s.bookCaption(rub)}", s.bookCaption(rub).startsWith("서버 문장 $rub ") && s.bookCaption(rub).endsWith("사라졌어요.") && item in s.bookCaption(rub))
            assertTrue("미션 2 결과가 안 붙었다: ${s.bookCaption(drag)}", s.bookCaption(drag).startsWith("서버 문장 $drag ${s.childName}") && s.bookCaption(drag).endsWith("${s.mission2().give}."))
        } finally { server.close() }
    }

    /** #52 3번 — 미션 쪽에 미션 ID 가 붙어 간다(문지르기 A6 · 건네주기 E1). 다른 쪽은 비운다 */
    @Test
    fun theCoopBookPlanNamesItsMissions() = run { d ->
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            d.coopWriteBook()
            val pages = server.requests.first { it.first == "/story" }.second.getJSONArray("pages")
            val byKind = (0 until pages.length()).map { pages.getJSONObject(it) }
                .associate { it.getString("kind") to (if (it.isNull("mission")) null else it.getString("mission")) }
            assertEquals("A6", byKind["RUB"])
            assertEquals("E1", byKind["DRAG"])
            assertTrue("미션이 아닌 쪽에 미션 ID 가 붙었다: $byKind", byKind.filterKeys { it != "RUB" && it != "DRAG" }.values.all { it == null })
        } finally { server.close() }
    }

    /** 이유를 안 골랐으면 앱 질문이 상상 이야기였으니 책도 dream · 이야기를 안 골랐으면 비운다(있었던 일) */
    @Test
    fun noReasonMeansDreamAndNoPickMeansADayThatHappened() = run { d ->
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            d.s.coopPick = CoopPick("place", "동물원", null)
            d.coopWriteBook()
            d.s.coopPick = null
            d.coopWriteBook()
            val (picked, plain) = server.requests.filter { it.first == "/story" }.map { it.second }
            assertEquals("dream", picked.optString("reason"))
            assertTrue("이야기를 안 골랐는데 이유가 갔다", plain.isNull("reason"))
            assertTrue("이야기를 안 골랐는데 템플릿이 갔다", plain.isNull("template"))
        } finally { server.close() }
    }

    @Test
    fun aWrongNumberOfSentencesKeepsTheTemplateBook() = run { d ->
        val server = StoryTestServer { path, _ ->
            if (path != "/story") JSONObject()
            else JSONObject().put("scenes", org.json.JSONArray().put(JSONObject().put("index", 1).put("caption", "한 쪽만")))
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.filledCoop()
            val templateFirst = d.s.bookCaption(1)
            d.coopWriteBook()
            assertNull(d.s.storyCaptions)
            assertEquals(templateFirst, d.s.bookCaption(1))
        } finally { server.close() }
    }
}
