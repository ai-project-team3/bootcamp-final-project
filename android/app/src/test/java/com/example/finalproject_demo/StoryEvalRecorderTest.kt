package com.example.finalproject_demo

import com.example.finalproject_demo.demo.StoryEvalRecorder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StoryEvalRecorderTest {
    @Test fun payloadFailureIsReportedAsIncompleteWithoutTheExceptionDetail() {
        val output = mutableListOf<String>()
        val capture = StoryEvalRecorder(true, output::add).apply { enabled = true }
        capture.emit(capture.newSession(), "generation_started", "synthetic-attempt") { error("synthetic private failure detail") }
        val envelope = JSONObject(output.single())
        assertTrue(envelope.getBoolean("truncated"))
        assertEquals("observation_failed", envelope.getString("omission_reason"))
        val event = JSONObject(envelope.getString("payload"))
        assertEquals("generation_started", event.getString("event"))
        assertEquals("synthetic-attempt", event.getString("attempt_id"))
        assertEquals("observation_failed", event.getJSONObject("data").getString("status"))
        assertTrue(event.getJSONObject("data").getBoolean("missing_source"))
        assertFalse(event.toString().contains("synthetic private failure detail"))
    }

    @Test fun concurrentEventsKeepDistinctSequencesAndCompleteParts() {
        val output = mutableListOf<String>()
        val capture = StoryEvalRecorder(true, output::add).apply { enabled = true }
        val session = capture.newSession()
        val threads = (1..8).map { n -> kotlin.concurrent.thread {
            capture.emit(session, "utterance") { JSONObject().put("raw_text", "$n" + "🙂".repeat(500)) }
        } }
        threads.forEach(Thread::join)
        val groups = output.map(::JSONObject).groupBy { it.getLong("sequence") }
        assertEquals((1L..8L).toSet(), groups.keys)
        val words = groups.values.map { parts ->
            assertEquals(parts.first().getInt("parts"), parts.size)
            JSONObject(parts.sortedBy { it.getInt("part") }.joinToString("") { it.getString("payload") }).getJSONObject("data").getString("raw_text")
        }
        assertEquals((1..8).map { "$it" + "🙂".repeat(500) }.toSet(), words.toSet())
    }

    @Test fun oversizedEvidenceIsMarkedIncompleteInsteadOfLookingLikeACompleteSnapshot() {
        val output = mutableListOf<String>()
        val capture = StoryEvalRecorder(true, output::add).apply { enabled = true }
        capture.emit(capture.newSession(), "generation_started") { JSONObject().put("raw_text", "가".repeat(30000)) }
        val envelope = JSONObject(output.single())
        assertTrue(envelope.getBoolean("truncated"))
        assertEquals("payload_limit", JSONObject(envelope.getString("payload")).getJSONObject("data").getString("status"))
        assertEquals("payload_limit", envelope.getString("omission_reason"))
    }

    @Test fun observerExceptionsNeverEscapeOrCauseDisabledPayloadEvaluation() {
        val capture = StoryEvalRecorder(true, { error("synthetic sink failure") }).apply { enabled = true }
        val session = capture.newSession()
        capture.emit(session, "utterance") { JSONObject().put("raw_text", "합성 원문") }
        capture.emit(session, "utterance") { error("synthetic builder failure") }
        capture.enabled = false
        capture.emit(session, "utterance") { fail("disabled capture evaluated a payload"); JSONObject() }
    }
    @Test fun optedInUnicodeEventsReassembleWithoutBreakingEmojiOrLogcatLines() {
        val output = mutableListOf<String>()
        val capture = StoryEvalRecorder(true, output::add) { "synthetic-session" }.apply { enabled = true }
        val text = "가🙂\"\\\n".repeat(1000)
        capture.emit(capture.newSession(), "utterance") { JSONObject().put("raw_text", text) }
        assertTrue(output.size > 1)
        val envelopes = output.map(::JSONObject)
        val payload = JSONObject(envelopes.joinToString("") { it.getString("payload") })
        assertEquals(text, payload.getJSONObject("data").getString("raw_text"))
        assertEquals("utterance", payload.getString("event"))
        assertTrue(envelopes.all { it.getString("session_id") == "synthetic-session" && !it.getBoolean("truncated") })
        assertEquals((1..output.size).toList(), envelopes.map { it.getInt("part") })
        assertTrue(output.all { it.toByteArray(Charsets.UTF_8).size < 4000 })
        assertTrue(envelopes.all { e -> e.getString("payload").let { !Character.isLowSurrogate(it.first()) && !Character.isHighSurrogate(it.last()) } })
    }

    @Test fun disabledAndReleaseCaptureNeverCreateIdsOrEvaluatePayloads() {
        for (debug in listOf(false, true)) {
            val output = mutableListOf<String>()
            var ids = 0
            var payloads = 0
            val capture = StoryEvalRecorder(debug, output::add) { ids++; "id-$ids" }
            if (!debug) capture.enabled = true
            val session = capture.newSession()
            capture.emit(session, "utterance") { payloads++; JSONObject().put("text", "비공개 원문") }
            assertNull(session)
            assertEquals(0, ids)
            assertEquals(0, payloads)
            assertTrue(output.isEmpty())
        }
    }
}
