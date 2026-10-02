package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
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

    /** 고른 이유가 「다녀왔어요」면 서버 질문(과거형)과 맞아서 쓴다. 그리고 /turn 에 고른 이야기가 실린다 (#53 B) */
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
        } finally { server.close() }
    }

    /**
     * 「곧 해요」 · 「좋아해요」는 서버가 시제를 가르기 전까지(#53 C) **대본**으로 묻는다 —
     * 서버 프롬프트가 협업을 과거형으로 물어 「소방관 체험에서 뭐 했어?」가 나가면 안 된다
     */
    @Test
    fun aFutureStoryKeepsTheScriptUntilTheServerKnowsTheTense() = run { d ->
        val server = llmServer(mapOf("place" to ("companion" to "거기서 누구를 만났어?")))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionWith(CoopPick("job", "소방관", "soon"))
            val next = d.answer("큰 건물")
            assertTrue("곧 해요인데 서버 질문을 썼다: $next", next != "거기서 누구를 만났어?")
            assertEquals("대본(곧 해요) 질문이 아니다", "누구랑 같이 갈 거야?", next)
            val turn = server.requests.first { it.first == "/turn" }.second
            assertEquals("같이 만들기 · 직업 · 소방관 · 곧 체험해요(앞으로 할 일)", turn.optString("template"))
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
