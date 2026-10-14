package com.example.finalproject_demo.demo

import org.json.JSONObject
import java.util.UUID

/** An opt-in debug transport; it owns no files and never creates IDs when disabled. */
internal class StoryEvalRecorder(
    private val debugBuild: Boolean,
    private val output: (String) -> Unit,
    private val id: () -> String = { UUID.randomUUID().toString() },
) {
    @Volatile var enabled = false
    val active: Boolean get() = debugBuild && enabled
    private val sequences = mutableMapOf<String, Long>()
    fun newSession(): String? = if (active) runCatching(id).getOrNull() else null
    @Synchronized
    fun emit(session: String?, event: String, attempt: String? = null, data: () -> JSONObject) {
        if (!active || session == null) return
        runCatching {
            var failed = false
            val capturedData = runCatching(data).getOrElse {
                failed = true
                JSONObject().put("status", "observation_failed").put("missing_source", true)
            }
            var payload = JSONObject().put("event", event).put("attempt_id", attempt ?: JSONObject.NULL)
                .put("data", capturedData).toString()
            val bytes = payload.toByteArray(Charsets.UTF_8).size
            val omission = if (failed) "observation_failed" else if (bytes > 65536) "payload_limit" else null
            val truncated = omission != null
            if (bytes > 65536) payload = JSONObject().put("event", event).put("attempt_id", attempt ?: JSONObject.NULL)
                .put("data", JSONObject().put("status", "payload_limit").put("original_payload_bytes", bytes)).toString()
            val pieces = mutableListOf<String>()
            var offset = 0
            while (offset < payload.length) {
                val count = minOf(350, payload.codePointCount(offset, payload.length))
                val end = payload.offsetByCodePoints(offset, count)
                pieces += payload.substring(offset, end)
                offset = end
            }
            val sequence = (sequences[session] ?: 0L) + 1
            sequences[session] = sequence
            pieces.forEachIndexed { index, piece ->
                output(JSONObject().put("schema_version", 1).put("session_id", session).put("sequence", sequence)
                    .put("part", index + 1).put("parts", pieces.size).put("payload", piece).put("truncated", truncated)
                    .apply { omission?.let { put("omission_reason", it) } }.toString())
            }
        }
    }
}
