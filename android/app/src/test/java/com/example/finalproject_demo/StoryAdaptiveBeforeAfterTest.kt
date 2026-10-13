package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Preregistered fixed-input ablation, not a simulated improvement in child behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryAdaptiveBeforeAfterTest {
    private var previousBase: String? = null
    private var previousModes: Set<StoryMode> = emptySet()
    private val historyText = "바람이 불었어. 그래서 모자를 잡았어"
    private val texts = listOf("도서관", "추웠어. 그래서 집에 갔어", "따뜻한 집에서 이야기를 했어", "공원")
    private val slots = listOf("place", "cause", "extra", "solution")
    private val variants = listOf("short", "cause_link", "link_only", "single", "choice", "other_child")

    @Before fun prepare() {
        previousBase = Server.base; previousModes = Server.liveModes
        Server.base = null; Server.liveModes = emptySet()
        SessionReports.attach(RuntimeEnvironment.getApplication())
        SessionReports.clear()
    }

    @After fun clean() {
        SessionReports.clear()
        Server.base = previousBase; Server.liveModes = previousModes
    }

    @Test fun pairedReplayMeasuresHistoryAblationWithoutInventingResponseBenefits() = runBlocking {
        val rows = JSONArray()
        for (variant in variants) {
            val before = replay(variant, false)
            val after = replay(variant, true)
            assertEquals(4, before.getInt("round_trips"))
            assertEquals(4, after.getInt("round_trips"))
            assertEquals(4, before.getInt("report_exchanges"))
            assertEquals(4, after.getInt("report_exchanges"))
            assertEquals(3, before.getInt("filled_requests"))
            assertEquals(3, after.getInt("filled_requests"))
            assertEquals(before.getJSONObject("final_slots").toString(), after.getJSONObject("final_slots").toString())
            val expectedStart = when (variant) { "short" -> "pick"; "cause_link" -> "reason"; else -> "chain" }
            assertEquals("chain", before.getJSONArray("levels").getString(0))
            assertEquals(expectedStart, after.getJSONArray("levels").getString(0))
            rows.put(JSONObject().put("scenario", variant).put("before", before).put("after", after))
        }
        val result = JSONObject().put("schema", 1)
            .put("comparison", "history_absent_vs_reloaded_history")
            .put("data", "synthetic_fixed_responses")
            .put("measures_child_effect", false).put("rows", rows)
        val output = File("build/reports/adaptive-before-after/results.json")
        output.parentFile.mkdirs()
        output.writeText(result.toString(2))
    }

    private fun seedHistory(variant: String) {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        try {
            d.s.level = Level.CHAIN
            repeat(if (variant == "single") 1 else 2) {
                val text = if (variant == "short") "책" else historyText
                d.setListening(Question("무슨 일이 있었어?", if (variant == "choice") Kind.CHOICE else Kind.EASY))
                d.say("무슨 일이 있었어?")
                d.talk("child", text)
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                    Reply.Spoke(text, answer = Answer(text,
                        reason = variant != "short" && variant != "link_only", con = variant != "short")))
            }
            SessionReports.keep(d.s, "synthetic-history-$variant")
            AnswerHistory.reload()
            if (variant == "other_child") assertTrue(AnswerHistory.startNewChild())
        } finally { scope.cancel() }
    }

    private suspend fun replay(variant: String, history: Boolean): JSONObject = coroutineScope {
        SessionReports.clear()
        if (history) seedHistory(variant)
        var responseIndex = 0
        val server = StoryTestServer { path, body ->
            require(path == "/turn")
            val index = responseIndex++
            require(index in texts.indices && body.getString("utterance") == texts[index])
            val filled = index != 2
            JSONObject().put("judge", JSONObject()
                .put("reason", "ok").put("story_ready", false)
                .put("slot_1", if (filled) slots[index] else JSONObject.NULL)
                .put("value_1", if (filled) texts[index] else JSONObject.NULL)
                .put("slot_2", JSONObject.NULL).put("value_2", JSONObject.NULL)
                .put("next_slot", JSONObject.NULL).put("no_longer_needed", JSONObject.NULL)
                .put("unclear", false).put("contradiction", false)
                .put("s1_reason", index == 1).put("s2_addition", false))
                .put("line", JSONObject.NULL)
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.level = Level.CHAIN
        d.s.templateKey = "E"
        d.s.initializeStoryQuestionStyle()
        Server.base = server.base; Server.liveModes = setOf(StoryMode.STORY)
        var filledRequests = 0
        try {
            for ((index, text) in texts.withIndex()) {
                val beforeSlots = d.s.slots.toMap()
                val reply = async { d.askStory(Question("무슨 일이 있었어?", Kind.EASY), slots[index], singleAttempt = true) }
                val feeder = launch { while (reply.isActive) { d.send(Reply.Spoke(text)); delay(40) } }
                val answer = try { withTimeout(8_000) { reply.await() } }
                    finally { feeder.cancelAndJoin(); reply.cancelAndJoin() }
                assertTrue(answer is Reply.Spoke && answer.answer != null)
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, answer)
                if (d.s.slots.any { (key, value) -> value.isNotBlank() && beforeSlots[key].isNullOrBlank() }) filledRequests++
            }
            val requests = server.requests.filter { it.first == "/turn" }.map { it.second }
            assertEquals(texts, requests.map { it.getString("utterance") })
            assertEquals(4, d.s.turn)
            val reportExchanges = d.s.buildSessionReport().exchanges
            assertEquals("Use the app report's exchange definition as well as HTTP counts", 4, reportExchanges)
            assertEquals(0, server.requests.count { it.first == "/judge" })
            requests.forEach {
                assertFalse(it.toString().contains(historyText))
                assertFalse(it.has("history")); assertFalse(it.has("age")); assertFalse(it.has("languageEvidence"))
            }
            JSONObject().put("levels", JSONArray(requests.map { it.getString("level") }))
                .put("round_trips", requests.size).put("counted_turns", d.s.turn)
                .put("report_exchanges", reportExchanges)
                .put("filled_requests", filledRequests).put("fill_rate", filledRequests.toDouble() / requests.size)
                .put("extra_judge_calls", 0).put("final_slots", JSONObject(d.s.slots.toSortedMap()))
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); server.close() }
    }
}
