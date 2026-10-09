package com.example.finalproject_demo

import com.example.finalproject_demo.demo.ASK_AGAIN_LINE
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.REPAIR_VOICE_CAP
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.SORRY_LINE
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.VOICE_GEN_CAP
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.requestDiaryStory
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 그림일기 D3 의 대화 수선 (#323) — 「그거 아니야」 · 「오또는?」 · 「뭐라고?」를 다음 칸 질문 대신 받는다.
 * 가짜 `/turn` 으로 서버 응답(`act` · `retract`)을 바꿔 끼워 본다. 서버 쪽 판단은 backend/tests 가 본다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryDialogueRepairTest {

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    /** `/turn` 응답 — judge 는 쓰는 필드만, [act] · [retract] 는 맨 위 */
    private fun turn(
        fills: List<Pair<String, String>>, next: String?, ack: String?, question: String?,
        act: String? = null, retract: List<String> = emptyList(), ready: Boolean = false,
    ): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", next ?: JSONObject.NULL).put("story_ready", ready)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = ack?.let { JSONObject().put("ack", it).put("expand", JSONObject.NULL).put("question", question ?: JSONObject.NULL) }
        return JSONObject().put("judge", judge).put("line", line ?: JSONObject.NULL)
            .put("act", act ?: JSONObject.NULL).put("retract", org.json.JSONArray(retract)).toString()
    }

    private fun live(fake: (JSONObject) -> String?, block: suspend (Director) -> Unit) = runBlocking {
        val http = FakeHttp { path, body -> if (path == "/turn") fake(JSONObject(body)) else null }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        val realStory = requestDiaryStory
        requestDiaryStory = { _, _, _, _ -> null }
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

    private suspend fun Director.toQuestions() {
        go(Scene.DIARY)
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("skip", "그림 없이")); s.stage == DiaryAsk } != null)
    }

    private suspend fun Director.answer(text: String, until: () -> Boolean) {
        repeat(4) {
            assertTrue("오또가 듣지 않는다 — 말=${s.line}", await { s.micEnabled } != null)
            delay(100)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    /** 첫 답으로 장소를 채운다 — 다음 요청부터 기록이 실린다 */
    private fun placeFirst() = turn(listOf("place" to "놀이터"), "problem", "놀이터에 갔구나!", "놀이터에서 무슨 일이 있었어?")

    private fun end() = turn(listOf("extra" to "또 가고 싶어"), null, "또 가고 싶구나!", null, ready = true)

    @Test
    fun aBareDenialPutsTheSlotBackAndAsksItAgain() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> placeFirst()
                2 -> turn(emptyList(), "place", "앗, 내가 잘못 알았구나!", "오늘은 어디에 갔었어?", act = "repair", retract = listOf("place"))
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            d.answer("놀이터 갔어") { asked.size == 1 && s.slots["place"] != null }
            d.answer("그거 아니야") { asked.size == 2 && s.slots["place"] == null }
            assertNull("틀렸다고 한 칸은 비운다", s.place)
            assertTrue(await { s.micEnabled } != null)
            d.answer("바닷가") { asked.size == 3 }
            assertEquals("되돌린 칸을 다시 묻는다", "place", asked[2].getString("asked_slot"))
            // 둘째 요청에 첫 턴이 기록으로 갔다 — 오또가 무엇을 받아 적었는지 서버가 안다
            val h = asked[1].getJSONArray("history").getJSONObject(0)
            assertEquals("놀이터 갔어", h.getJSONObject("child").getString("text"))
            assertEquals("놀이터에 갔구나!", h.getJSONObject("otto").getString("ack"))
            assertEquals("place", h.getJSONArray("fills").getJSONObject(0).getString("slot"))
        }
    }

    @Test
    fun aCorrectionWithTheRightValueFillsItAndMovesOn() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> placeFirst()
                2 -> turn(listOf("place" to "수영장"), "problem", "앗, 내가 잘못 알았구나! 수영장이었구나.", "수영장에서 뭐 했어?",
                    act = "repair", retract = listOf("place"))
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            d.answer("놀이터 갔어") { asked.size == 1 && s.slots["place"] != null }
            d.answer("놀이터 아니야, 수영장이야") { asked.size == 2 && s.place == "수영장" }
            assertEquals("책에는 옮긴 값 — 「아니야」가 책에 들어가지 않게", "수영장", s.slots["place"])
            assertEquals("child", s.slotBy["place"])
            assertEquals(1, s.diaryDay.repairVoice)
            d.answer("미끄럼틀 탔어") { asked.size == 3 }
            assertEquals("값이 들어갔으니 다음 칸으로", "problem", asked[2].getString("asked_slot"))
        }
    }

    @Test
    fun aQuestionBackIsAnsweredWithTheServerLineNotTheEasyQuestion() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(emptyList(), "place", "오또는 이야기를 듣는 걸 좋아해!", "오늘은 어디에 갔었어?", act = "answer_back")
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            d.answer("오또는 뭐 좋아해?") { asked.size == 1 && s.diaryDay.voiceGen == 1 }
            d.answer("놀이터") { asked.size == 2 }
            assertEquals("place", asked[1].getString("asked_slot"))
            assertEquals("서버 질문으로 같은 칸을 다시 — 쉬운 질문이 아니다", "오늘은 어디에 갔었어?", asked[1].getString("question"))
            assertEquals("answer_back", asked[1].getJSONArray("history").getJSONObject(0).getString("act"))
        }
    }

    @Test
    fun pastTheCapAQuestionBackGetsTheBakedEasyQuestion() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(emptyList(), "place", "오또는 이야기를 듣는 걸 좋아해!", "오늘은 어디에 갔었어?", act = "answer_back")
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            s.diaryDay.voiceGen = VOICE_GEN_CAP
            d.answer("오또는 뭐 좋아해?") { asked.size == 1 }
            d.answer("놀이터") { asked.size == 2 }
            assertEquals("상한을 넘으면 구운 쉬운 질문", "아침 먹고 어디 갔어?", asked[1].getString("question"))
            assertEquals(VOICE_GEN_CAP, s.diaryDay.voiceGen)
        }
    }

    @Test
    fun aFailedLineStillAsksAgainWithTheBakedLines() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> turn(emptyList(), "place", null, null, act = "rephrase")      // line 이 없다 — act 는 남는다
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            d.answer("뭐라고?") { asked.size == 1 && s.line == ASK_AGAIN_LINE }
            d.answer("놀이터") { asked.size == 2 }
            assertEquals("아침 먹고 어디 갔어?", asked[1].getString("question"))
            assertEquals("구운 대사는 새 음성으로 세지 않는다", 0, s.diaryDay.voiceGen)
        }
    }

    @Test
    fun pastTheRepairCapTheSlotIsStillFixedWithTheBakedOpener() {
        val asked = mutableListOf<JSONObject>()
        live({ t ->
            asked += t
            when (asked.size) {
                1 -> placeFirst()
                2 -> turn(listOf("place" to "수영장"), "problem", "앗, 내가 잘못 알았구나! 수영장이었구나.", "수영장에서 뭐 했어?",
                    act = "repair", retract = listOf("place"))
                else -> end()
            }
        }) { d ->
            val s = d.s
            d.toQuestions()
            d.answer("놀이터 갔어") { asked.size == 1 && s.slots["place"] != null }
            s.diaryDay.repairVoice = REPAIR_VOICE_CAP
            d.answer("놀이터 아니야, 수영장이야") { asked.size == 2 && s.place == "수영장" }
            assertTrue("구운 앞말", await { s.line == SORRY_LINE || s.micEnabled } != null)
            assertEquals("칸은 그래도 고친다", "수영장", s.slots["place"])
            assertEquals(REPAIR_VOICE_CAP, s.diaryDay.repairVoice)
        }
    }
}
