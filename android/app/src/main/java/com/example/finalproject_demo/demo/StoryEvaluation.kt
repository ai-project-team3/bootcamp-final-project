package com.example.finalproject_demo.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.BuildConfig
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Trace
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

/** Runtime-only evaluation evidence, separate from reports, drafts and saved books. */
internal object StoryEvaluation {
    private class Session(val id: String) {
        var attempt: String? = null
        var bookAttempt: String? = null
        var generating = false
        var closed = false
    }
    data class Attempt(val sessionId: String, val id: String)
    data class SaveSnapshot(val sessionId: String, val attemptId: String?, val contents: String?)
    private val recorder = StoryEvalRecorder(BuildConfig.DEBUG, { Trace.line("story_eval", it) })
    private val sessions = WeakHashMap<DemoState, Session>()
    var enabled by mutableStateOf(false)
        private set

    @Synchronized fun setEnabled(s: DemoState, value: Boolean) = safe {
        if (!BuildConfig.DEBUG || value == enabled) return@safe
        if (!value) {
            sessions.values.forEach { session ->
                cancel(session, "capture_disabled")
                recorder.emit(session.id, "capture_disabled", session.attempt) { JSONObject().put("status", "disabled") }
                session.closed = true
            }
            sessions.clear()
        }
        enabled = value
        recorder.enabled = value
        if (value && (s.scene !in setOf(Scene.ADULT, Scene.PARENT, Scene.SHELF) || s.paused != null || s.talk.isNotEmpty())) ensure(s)
    }

    private fun ensure(s: DemoState, mid: Boolean = true): Session? {
        if (!recorder.active || s.mode != StoryMode.STORY) return null
        sessions[s]?.let { return it }
        val session = Session(recorder.newSession() ?: return null)
        sessions[s] = session
        recorder.emit(session.id, "session_started") {
            JSONObject().put("scope", "story").put("live_enabled", Server.liveFor(s.mode))
                .put("started_mid_session", mid).put("missing_source", mid)
                .put("source_continuity", if (mid) "unknown" else "runtime_session_start")
                .put("restored_or_paused", s.paused != null)
                .put("app_version_name", BuildConfig.VERSION_NAME).put("app_version_code", BuildConfig.VERSION_CODE)
                .put("app_git_sha", "unknown").put("server_version", "unknown")
        }
        return session
    }

    @Synchronized fun begin(s: DemoState) = safe { ensure(s, mid = false) }
    @Synchronized fun restored(s: DemoState) = safe {
        if (!recorder.active) return@safe
        reset(s)
        state(s, "session_restored") { JSONObject().put("missing_source", true) }
    }
    @Synchronized fun reset(s: DemoState) = safe {
        if (!recorder.active) return@safe
        val session = sessions.remove(s) ?: return@safe
        cancel(session, "session_reset")
        recorder.emit(session.id, "session_reset", session.attempt) { JSONObject().put("status", "reset") }
        session.closed = true
    }

    @Synchronized fun observe(s: DemoState, event: String, data: () -> JSONObject = { JSONObject() }) = safe {
        val session = ensure(s) ?: return@safe
        recorder.emit(session.id, event, session.attempt, data)
    }

    fun state(s: DemoState, event: String, data: () -> JSONObject = { JSONObject() }) =
        observe(s, event) { data().put("state", snapshot(s)) }

    /** Freeze before the director records mission history; later selection may rotate missions. */
    @Synchronized fun savingSnapshot(s: DemoState, book: SavedStoryBook?): SaveSnapshot? {
        if (!recorder.active) return null
        return runCatching {
            val session = ensure(s) ?: return@runCatching null
            val contents = runCatching {
                JSONObject().put("book_id", book?.id ?: JSONObject.NULL)
            .put("title", book?.title ?: JSONObject.NULL)
            .put("pages", book?.let { b -> JSONArray(b.pages.map { JSONObject().put("kind", it.kind.name).put("caption", it.caption) }) } ?: JSONObject.NULL)
            .put("generated_captions", s.storyCaptions?.let(::JSONArray) ?: JSONObject.NULL)
            .put("mission_results", JSONObject().put("m1_result", s.m1Result ?: JSONObject.NULL).put("m2_result", s.m2Result ?: JSONObject.NULL)
                .put("additions", JSONArray((1..s.pageCount).map { i -> JSONObject().put("page", i).put("text", s.storyMissionResult(i) ?: JSONObject.NULL) })))
                    .toString()
            }.getOrNull()
            SaveSnapshot(session.id, session.bookAttempt, contents)
        }.getOrNull()
    }

    @Synchronized fun saving(s: DemoState, event: String, snapshot: SaveSnapshot?, status: String) = safe {
        if (!recorder.active || snapshot == null || sessions[s]?.id != snapshot.sessionId) return@safe
        recorder.emit(snapshot.sessionId, event, snapshot.attemptId) {
            JSONObject(snapshot.contents ?: error("observation unavailable")).put("status", status)
        }
    }

    @Synchronized fun generation(s: DemoState): Attempt? {
        if (!recorder.active) return null
        return runCatching {
            val session = ensure(s) ?: return@runCatching null
            cancel(session, "replaced_attempt")
            val id = recorder.newSession() ?: return@runCatching null
            session.attempt = id
            session.generating = true
            recorder.emit(session.id, "generation_started", id) {
                JSONObject().put("state", snapshot(s)).put("live_enabled", Server.liveFor(s.mode))
                    .put("plan", JSONArray(s.storyPagePlan().map { p ->
                        JSONObject().put("kind", p.kind).put("mission", p.mission ?: JSONObject.NULL)
                            .put("prop", p.prop ?: JSONObject.NULL).put("mission_source", p.missionSource ?: JSONObject.NULL)
                    }))
            }
            Attempt(session.id, id)
        }.getOrNull()
    }

    fun diagnostic(s: DemoState, attempt: Attempt?): ((String, JSONObject) -> Unit)? {
        if (!recorder.active || attempt == null) return null
        return { phase, data -> synchronized(this) {
            safe {
                val session = matching(s, attempt) ?: return@safe
                recorder.emit(session.id, if (phase == "request") "generation_request" else "generation_response", attempt.id) { JSONObject(data.toString()) }
            }
        } }
    }

    @Synchronized fun generated(s: DemoState, attempt: Attempt?, book: Server.StoryBook?, captions: List<String>?, accepted: Boolean, reason: String? = null) = safe {
        val session = matching(s, attempt) ?: return@safe
        session.generating = false
        session.bookAttempt = attempt!!.id
        recorder.emit(session.id, if (accepted) "generation_adopted" else "generation_fallback", attempt.id) {
            JSONObject().put("status", reason ?: when {
                accepted -> "accepted"
                book == null -> "no_generated_book"
                captions?.size != s.bookPages.size -> "invalid_count"
                else -> "blank_caption"
            }).put("parsed_server_captions", book?.captions?.let(::JSONArray) ?: JSONObject.NULL)
                .put("unmasked_captions", captions?.let(::JSONArray) ?: JSONObject.NULL).put("expected_count", s.bookPages.size)
        }
    }

    @Synchronized fun cancelled(s: DemoState, attempt: Attempt?) = safe { matching(s, attempt)?.let { cancel(it, "cancelled") } }

    private fun matching(s: DemoState, attempt: Attempt?): Session? {
        if (!recorder.active || attempt == null) return null
        return sessions[s]?.takeIf { !it.closed && it.id == attempt.sessionId && it.attempt == attempt.id }
    }

    private fun cancel(session: Session, reason: String) {
        if (!session.generating) return
        session.generating = false
        recorder.emit(session.id, "generation_cancelled", session.attempt) { JSONObject().put("status", reason) }
    }

    /** Explicit text-only allowlist. JSON construction copies mutable maps/lists at observation time. */
    private fun snapshot(s: DemoState): JSONObject {
        val projected = s.storyServerInput()
        return JSONObject().put("turn", s.turn).put("mode", s.mode.name.lowercase())
            .put("template", s.templateKey ?: JSONObject.NULL).put("level", s.level.name.lowercase())
            .put("end_reason", s.endReason ?: JSONObject.NULL).put("end_asked", s.storyEndAsked)
            .put("slots", JSONObject(s.slots.toMap())).put("slot_by", JSONObject(s.slotBy.toMap()))
            .put("projected_slots", JSONObject(projected.slots.toMap())).put("projected_slot_by", JSONObject(projected.sources.toMap()))
            .put("talk", JSONArray(s.talk.toList().map { line ->
                JSONObject().put("who", line.who).put("stored_text", line.text)
                    .put("question", line.question ?: JSONObject.NULL)
            }))
            .put("missions", if (s.template == null) JSONObject.NULL else s.missions().let { m ->
                JSONObject().put("slot1", m.slot1.name).put("slot2", m.slot2.name)
                    .put("slot1_from_child", m.slot1FromChild).put("slot2_from_child", m.slot2FromChild)
            })
    }

    private inline fun safe(action: () -> Unit) { runCatching(action) }
}
