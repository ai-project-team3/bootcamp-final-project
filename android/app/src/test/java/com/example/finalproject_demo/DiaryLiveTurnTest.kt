package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.requestDiaryStory
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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

    /**
     * 칸이 [value] 가 될 때까지 기다렸다가 본다. 가짜 `/turn` 은 요청이 **들어올 때** `asked` 에 적고, 앱은 응답을 **받은 뒤** 칸을 채운다 —
     * `asked.size` 만 보고 바로 칸을 읽으면 느린 CI(리눅스)에서 아직 null 이다 (#94 · 응답을 300ms 늦추면 그대로 재현)
     */
    private suspend fun Director.slotBecomes(key: String, value: String) {
        await { s.slots[key] == value }
        assertEquals(value, s.slots[key])
    }

    /** `/turn` 응답 JSON — 서버 명세(guidelines/3 §3-2-1)의 judge 16필드 중 쓰는 것만 */
    private fun turn(fills: List<Pair<String, String>>, next: String?, ack: String, question: String?, ready: Boolean = false, s1: Boolean = false): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", next ?: JSONObject.NULL)
            .put("story_ready", ready).put("s1_reason", s1)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = JSONObject().put("ack", ack).put("expand", JSONObject.NULL).put("question", question ?: JSONObject.NULL)
        return JSONObject().put("judge", judge).put("line", line).toString()
    }

    /** 「내일」 답 — 판정이 ready 를 줘도 앱은 「내일」을 한 번 묻는다([tomorrowIsStillAskedWhenTheVerdictSaysReady]) */
    private fun wish() = turn(listOf("extra" to "또 가고 싶어"), null, "또 가고 싶구나!", null, ready = true)

    /**
     * 서버 모드 — 앞에 가짜 `/turn` 서버를 띄운다([FakeHttp]). [fake] 는 받은 요청 → 돌려줄 JSON (null = 502).
     * 앱은 판정을 `exchangeTurn` 의 기본 요청으로 부른다 — 요청 함수를 넘기면 Kotlin IR 백엔드가 죽어서다
     */
    private fun live(
        fake: (JSONObject) -> String?,
        story: suspend (Map<String, String?>, Map<String, String>, String?) -> List<String>? = { _, _, _ -> null },
        block: suspend (Director) -> Unit,
    ) = runBlocking {
        val http = FakeHttp { path, body -> if (path == "/turn") fake(JSONObject(body)) else null }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        val realStory = requestDiaryStory
        requestDiaryStory = story
        Server.base = http.base
        Server.liveModes = setOf(StoryMode.DIARY)
        try { block(d) } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            requestDiaryStory = realStory
            Server.liveModes = emptySet()
            Server.base = null
            http.close()
        }
    }

    /** 그림 없이 → D3. 서버 모드처럼 값 없는 말로 답한다 */
    private suspend fun Director.toQuestions() {
        go(Scene.DIARY)
        // D0 에 있을 때만 누른다 — 남은 누름이 D3 첫 질문의 답으로 들어가면 장소를 건너뛴다
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("skip", "그림 없이")); s.stage == DiaryAsk } != null)
    }

    /** 오또가 듣기 시작하면(마이크) 한 번 보낸다. 1.5초 안에 진행이 없을 때만 다시 — 남은 답이 다음 질문에 들어가지 않게 */
    private suspend fun Director.answer(text: String, until: () -> Boolean) {
        repeat(4) {
            assertTrue("오또가 듣지 않는다 — 말=${s.line}", await { s.micEnabled } != null)
            delay(100)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    @Test
    fun theVerdictPicksTheNextQuestionAndFillsSlotsAsTheChilds() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(listOf("place" to "놀이터"), "reaction", "놀이터에 갔구나!", "거기서 기분이 어땠어?")
                2 -> turn(listOf("reaction" to "신났다", "problem" to "미끄럼틀을 탔다"), null, "신났구나!", null, ready = true, s1 = true)
                else -> turn(listOf("extra" to "또 타고 싶어"), null, "또 타고 싶구나!", null, ready = true)
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "거기서 기분이 어땠어?" }
            assertEquals("diary", asked[0].getString("mode"))
            assertEquals("place", asked[0].getString("asked_slot"))
            assertEquals("놀이터 갔어", s.slots["place"])
            assertEquals("child", s.slotBy["place"])
            d.answer("미끄럼틀 타서 신났어") { asked.size == 2 && s.line == "내일 또 하고 싶은 거 있어?" }
            d.answer("또 타고 싶어") { asked.size == 3 && s.stage !is DiaryAsk }
            assertEquals("고정 차례가 아니라 판정이 고른 칸을 물었다", "reaction", asked[1].getString("asked_slot"))
            assertEquals("첫 칸의 책 문장은 아이 말 그대로", "미끄럼틀 타서 신났어", s.slots["reaction"])
            assertEquals("둘째 칸은 판정이 채운 값", "미끄럼틀을 탔다", s.slots["problem"])
            assertTrue(s.slotBy.values.all { it == "child" })
            assertEquals("story_ready", s.endReason)
            assertEquals(3, s.diaryDay.turnCalls)
        }
    }

    /** 판정이 `next_slot = extra` 를 주면 그 답은 `keep`(내일)에 들어간다 — 이미 답했으면 다시 묻지 않는다 (#64-3) */
    @Test
    fun anAnsweredWishIsNotAskedAgainWhenTheServerPicksExtra() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(listOf("place" to "놀이터"), "extra", "놀이터에 갔구나!", "내일 또 하고 싶은 거 있어?")
                2 -> turn(listOf("extra" to "또 미끄럼틀 타고 싶어"), "extra", "또 타고 싶구나!", "내일 또 하고 싶은 거 있어?")
                else -> turn(emptyList(), null, "그랬구나!", null, ready = true)
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "내일 또 하고 싶은 거 있어?" }
            d.answer("또 미끄럼틀 타고 싶어") { asked.size == 2 && s.line != "내일 또 하고 싶은 거 있어?" }
            d.slotBecomes("keep", "또 미끄럼틀 타고 싶어")
            assertTrue("이미 답한 「내일」을 또 물었다 — 말=${s.line}", await(1_500) { s.line == "내일 또 하고 싶은 거 있어?" } == null)
        }
    }

    /**
     * 판정이 결말을 듣고 `story_ready` 를 줘도 「내일」은 묻는다 — 모드 절을 넣은 판정은 결말 답에서 ready · next 없음을 준다
     * (`eval/results.md` 10-02). 그대로 멈추면 일기의 맺음 질문이 빠진다 (10-02 진웅)
     */
    @Test
    fun tomorrowIsStillAskedWhenTheVerdictSaysReady() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (t.getString("asked_slot")) {
                "place" -> turn(listOf("place" to "놀이터", "problem" to "그네 탔다"), "solution", "그네 탔구나!", "그래서 어떻게 됐어?")
                "solution" -> turn(listOf("solution" to "집에 왔다"), null, "그랬구나!", null, ready = true)
                else -> {
                    // Keep receipt and response completion distinct, as they are on Linux CI.
                    Thread.sleep(250)
                    turn(listOf("extra" to "또 그네 타고 싶어"), null, "또 타고 싶구나!", null, ready = true)
                }
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 가서 그네 탔어") { s.line == "그래서 어떻게 됐어?" }
            d.answer("집에 왔어") { asked.size == 2 && s.line != "그래서 어떻게 됐어?" }
            assertTrue("판정이 ready 라고 「내일」을 건너뛰었다 — 말=${s.line}", await { s.line == "내일 또 하고 싶은 거 있어?" } != null)
            d.answer("또 그네 타고 싶어") { s.stage is DiaryPaper }
            assertEquals("extra", asked[2].getString("asked_slot"))
            d.slotBecomes("keep", "또 그네 타고 싶어")
        }
    }

    /**
     * 전체 상한 없이 칸마다만 막는다 — 다른 모드와 같은 방식 (#89).
     * 결말을 못 채우면 쉽게 바꿔 한 번 더 묻고, 그래도 못 채우면 비워 두고 「내일」로 간다. 결말을 지어 넣지 않는다
     */
    @Test
    fun anEmptySlotIsAskedTwiceThenLeftAndTomorrowStillComes() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (t.getString("asked_slot")) {
                "place" -> turn(listOf("place" to "놀이터"), "problem", "놀이터 갔구나!", "놀이터에서 무슨 일이 있었어?")
                "problem" -> turn(listOf("problem" to "그네 탔다"), "solution", "그네 탔구나!", "그래서 어떻게 됐어?", ready = true)
                "solution" -> turn(emptyList(), "solution", "그랬구나!", "그네 타고 나서 어떻게 됐어?", ready = true)
                else -> wish()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터") { s.line == "놀이터에서 무슨 일이 있었어?" }
            d.answer("그네 탔어") { s.line == "그래서 어떻게 됐어?" }
            d.answer("음") { asked.size == 3 }                                   // 결말 — 못 채웠다
            assertTrue("못 채운 결말을 쉽게 바꿔 한 번 더 묻지 않았다 — 말=${s.line}", await { s.line == "그다음엔 뭐 했어?" } != null)
            d.answer("음") { asked.size == 4 }                                   // 두 번째도 못 채웠다 → 비워 둔다
            assertTrue("결말을 비워 두고 「내일」로 안 갔다 — 말=${s.line}", await { s.line == "내일 또 하고 싶은 거 있어?" } != null)
            d.answer("또 가고 싶어") { s.stage is DiaryPaper }
            assertEquals(listOf("place", "problem", "solution", "solution", "extra"), asked.map { it.getString("asked_slot") })
            d.slotBecomes("keep", "또 가고 싶어")
            assertTrue("결말을 지어 넣었다", s.slots["solution"].isNullOrBlank())
        }
    }

    /**
     * 그리는 중 이야기 — 첫 질문은 고정(「여기는 어디야?」), 다음은 서버가 앞 말을 받아 쓴 질문(「놀이터에서 무슨 일이 있었어?」).
     * 사이에 그림 질문이 끼어도 그 질문 그대로 묻고, 다 그린 뒤 D3 는 답한 칸을 다시 묻지 않는다 (10-02 진웅)
     */
    @Test
    fun storyQuestionsWhileDrawingFollowTheServer() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(listOf("place" to "놀이터"), "problem", "놀이터에 갔구나!", "놀이터에서 무슨 일이 있었어?")
                2 -> turn(listOf("problem" to "미끄럼틀 탔다"), "solution", "미끄럼틀 탔구나!", "미끄럼틀 타고 나서 어떻게 됐어?")
                else -> turn(emptyList(), null, "그랬구나!", null, ready = true)
            }
        }) { d ->
            val s = d.s
            d.go(Scene.DIARY)
            assertTrue(await { s.stage is DiaryStart } != null)
            assertTrue(await { if (s.stage is DiaryStart) d.send(Reply.Tapped("draw", "그릴래")); s.stage is com.example.finalproject_demo.demo.DiaryBoard } != null)
            s.drawing += com.example.finalproject_demo.demo.Stroke(androidx.compose.ui.graphics.Color.Blue,
                listOf(androidx.compose.ui.geometry.Offset(.1f, .3f), androidx.compose.ui.geometry.Offset(.2f, .6f)))
            s.diaryDay.catchUp(s.drawing)
            s.diaryDay.pieces[0] = s.diaryDay.pieces[0].copy(name = "미끄럼틀")                // 조각은 이미 이름이 있다 — 물을 조각이 없다
            fun pause() { if (s.diaryDay.watching) d.send(Reply.Tapped("pause", "붓 멈춤")) }
            assertTrue("첫 이야기는 고정 질문 — 말=${s.line}", await { pause(); s.line == "여기는 어디야?" } != null)
            d.answer("놀이터 갔어") { asked.size == 1 && s.line != "여기는 어디야?" }
            assertEquals("diary", asked[0].getString("mode"))
            assertEquals("place", asked[0].getString("asked_slot"))
            await { s.slotBy["place"] != null }
            assertEquals("child", s.slotBy["place"])
            assertTrue("다음 이야기가 서버 질문이 아니다 — 말=${s.line}", await { pause(); s.line == "놀이터에서 무슨 일이 있었어?" } != null)
            d.answer("미끄럼틀 탔어") { asked.size == 2 && s.line != "놀이터에서 무슨 일이 있었어?" }
            assertEquals("problem", asked[1].getString("asked_slot"))
            // 그리는 중 이야기는 두 번까지 — 다 그린 뒤 D3 는 서버가 골라 둔 질문으로 잇는다
            assertTrue(await { if (s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); s.stage is DiaryAsk } != null)
            assertTrue("D3 가 서버가 골라 둔 질문으로 잇지 않았다 — 말=${s.line}", await { s.line == "미끄럼틀 타고 나서 어떻게 됐어?" } != null)
        }
    }

    /**
     * 필수 칸에 아이가 진짜로 답했는데 판정이 두 번 못 받으면 — 비우지 않고 아이 말 그대로(아이 출처). 협업과 같다 (10-05 진웅).
     * 「몰라」였다면 넣지 않는다(다른 테스트)
     */
    @Test
    fun aRealAnswerTheJudgeRejectsTwiceIsKeptAsTheChilds() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1, 2 -> turn(emptyList(), "place", "그랬구나!", "어디였어?")
                else -> turn(emptyList(), null, "그랬구나!", null, ready = true)
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("뒷산 약수터") { asked.size == 1 && s.line != "오늘 어디 갔었어?" }
            d.answer("뒷산 약수터") { asked.size == 2 }
            d.slotBecomes("place", "뒷산 약수터")
            assertEquals("child", s.slotBy["place"])
        }
    }

    /** 서버 질문이 갈무리에 걸리면(「언제」) 서버가 고른 그 칸을 앱 질문으로 묻는다 — 순서는 판정이 정한다 */
    @Test
    fun aServerQuestionThatFailsTheGuardIsAskedInTheAppsWordsForTheSameSlot() {
        live({ t ->
            when (t.getString("asked_slot")) {
                "place" -> turn(listOf("place" to "놀이터"), "problem", "놀이터에 갔구나!", "놀이터에는 언제 갔어?")
                else -> turn(emptyList(), null, "그랬구나!", null, ready = true)
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "놀이터에서 무슨 일이 있었어?" }
        }
    }

    @Test
    fun whenTheServerFailsTheChildsWordsStayAndTheFixedOrderGoesOn() {
        live({ null }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("어린이집 갔어") { s.line == "어린이집에서 무슨 일이 있었어?" }
            assertEquals("어린이집 갔어", s.slots["place"])
            assertEquals("child", s.slotBy["place"])
        }
    }

    /** D4 — 서버가 쓴 책 문장으로 그림일기가 짜인다. 이름은 가려서 보냈다가 받은 문장에서 푼다 */
    @Test
    fun theBookIsWrittenByTheServer() {
        var sent: Map<String, String?>? = null
        live(
            { t -> if (t.getString("asked_slot") == "extra") wish() else turn(listOf("place" to "놀이터"), null, "놀이터에 갔구나!", null, ready = true) },
            { slots, _, _ -> sent = slots; listOf("나는 오늘 놀이터에 갔어요.", "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요.", "재미있었어요.") },
        ) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "내일 또 하고 싶은 거 있어?" }
            d.answer("또 가고 싶어") { s.stage is DiaryPaper }
            assertEquals("놀이터 갔어", sent?.get("place"))
            assertEquals("나는 오늘 놀이터에 갔어요.", buildDiaryBook(s.diaryBookInput()).first().text)
        }
    }

    /**
     * 한 말이 두 칸을 채우면 둘째 칸(판정의 요약)은 책 · `/story` 에 다시 보내지 않는다.
     * 10-01 실기기: 「뽀삐가 미끄럼틀에서 넘어져서 울었어」 → reaction 「울었다」 → 책이 「나는 울었어요」를 지어냈다
     */
    @Test
    fun theSecondSlotOfOneSayingIsNotWrittenAgain() {
        var sent: Map<String, String?>? = null
        live(
            { t ->
                if (t.getString("asked_slot") == "extra") wish()
                else if (t.getString("asked_slot") == "place") turn(listOf("place" to "놀이터"), "problem", "놀이터에 갔구나!", "놀이터에서 무슨 일이 있었어?")
                else turn(listOf("problem" to "뽀삐가 미끄럼틀에서 넘어졌다", "reaction" to "울었다"), null, "그랬구나!", null, ready = true)
            },
            { slots, _, _ -> sent = slots; null },
        ) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "놀이터에서 무슨 일이 있었어?" }
            d.answer("뽀삐가 미끄럼틀에서 넘어져서 울었어") { s.line == "내일 또 하고 싶은 거 있어?" }
            d.answer("또 가고 싶어") { s.stage is DiaryPaper }
            assertEquals("판정 상태에는 남는다", "울었다", s.slots["reaction"])
            assertEquals("뽀삐가 미끄럼틀에서 넘어져서 울었어", sent?.get("problem"))
            assertEquals("둘째 칸을 /story 에 보냈다", null, sent?.get("reaction"))
            val book = buildDiaryBook(s.diaryBookInput()).map { it.text }
            assertTrue("앱 책에 「울었다」 쪽이 따로 생겼다 — $book", book.none { it.startsWith("울었") })
        }
    }

    @Test
    fun whenTheBookServerFailsTheAppWritesTheBook() {
        live({ t -> if (t.getString("asked_slot") == "extra") wish() else turn(listOf("place" to "놀이터"), null, "놀이터에 갔구나!", null, ready = true) }) { d ->
            val s = d.s
            d.toQuestions()
            assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
            d.answer("놀이터 갔어") { s.line == "내일 또 하고 싶은 거 있어?" }
            d.answer("또 가고 싶어") { s.stage is DiaryPaper }
            assertEquals(null, s.diaryDay.written)
            assertEquals("앱이 아이 말로 짠 문장", "나는 오늘 놀이터 갔어요.", buildDiaryBook(s.diaryBookInput()).first().text)
        }
    }
}
