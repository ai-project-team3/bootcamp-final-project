package com.example.finalproject_demo.net

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.finalproject_demo.demo.StoryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one network client. Every entry point talks to the server through here (guidelines/3 §3).
 *
 * **It never throws.** A failure returns null and the caller goes on with its script —
 * spec §3-0: *the app does not stop on an error.* A dead server must feel like a quiet
 * mascot, not a crash.
 *
 * **Off until an address is set** ([base] = null) **and** a mode is switched on ([liveModes]).
 * The demo, the tests and every screenshot run without a server exactly as before. Turn it on:
 * - emulator: `adb shell am start -n kr.clap.otto/com.example.finalproject_demo.MainActivity -e server http://10.0.2.2:8010 -e live all`
 * - phone on USB: `adb reverse tcp:8010 tcp:8010`, then `-e server http://127.0.0.1:8010 -e live story`
 * - or open the demo drawer and tap 「서버 연결」 per mode
 *
 * Owners of what goes through it: input `/stt` (조장 — 09-28 · was 민우), judge `/judge` (치영 · 민우 for the diary),
 * voice `/tts` (진웅), book `/story` (조장). This file is 조장's: ask before changing its shape.
 */
object Server {
    @Volatile var base: String? = null
    val on: Boolean get() = base != null

    // ── mode switches (09-29) ──────────────────────────────────────
    //
    // Each mode owner guards every server call with `Server.liveFor(s.mode)`. A mode that is
    // not switched on runs its script exactly as before — so a half-wired mode can be merged
    // without breaking the other two ("merge regardless of quality", 09-29 mentoring).
    // **Default: all off.** Turn on with `-e live story,diary,coop` (or `all`) next to
    // `-e server …`, or per mode from the demo drawer.

    /** Modes that go through the server. Compose state so the demo drawer redraws. */
    var liveModes: Set<StoryMode> by mutableStateOf(emptySet())

    /** True only when there is an address **and** this mode is switched on. */
    fun liveFor(mode: StoryMode): Boolean = on && mode in liveModes

    fun toggle(mode: StoryMode) {
        liveModes = if (mode in liveModes) liveModes - mode else liveModes + mode
    }

    /** `"story,diary"` · `"all"` · null → the set. Unknown words are ignored (a typo must not switch a mode on). */
    fun parseLive(arg: String?): Set<StoryMode> {
        val words = arg.orEmpty().split(',', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if ("all" in words) return StoryMode.entries.toSet()
        return StoryMode.entries.filter { it.name.lowercase() in words }.toSet()
    }

    /** Slot names, closed list (guidelines/2 §1-1). The server gets all twelve, empty ones as null. */
    val SLOTS = listOf(
        "place", "problem", "reaction", "cause", "newcomer", "name",
        "companion", "sound", "adult", "solution", "title", "extra",
    )

    private const val TAG = "Server"

    // ── /judge ─────────────────────────────────────────────────────

    /** One turn. [utterance] must already be name-masked (rule 6). */
    data class Turn(
        val mode: String,                       // story · diary · coop
        val slots: Map<String, String?>,
        val askedSlot: String?,
        val question: String,
        val utterance: String,
        val turn: Int = 0,
        val template: String? = null,
        val level: String? = null,
        /** coop only: the reason the parent picked — "done" · "soon" · "dream" decides the question's tense (#53 C) */
        val reason: String? = null,
    )

    /** The verdict fields the app reads (guidelines/2 §2). Slot names are already checked against the 12 by the server. */
    data class Verdict(
        val reason: String,
        val fills: List<Pair<String, String>>,   // (slot, value) — slot_1 · slot_2, the empty ones dropped
        val nextSlot: String?,
        val noLongerNeeded: String?,
        val storyReady: Boolean,
        val unclear: Boolean,
        val unclearOf: String?,
        val contradiction: Boolean,
        val s1Reason: Boolean,
        val s2Addition: Boolean,
        val emotion: String?,
    )

    suspend fun judge(t: Turn): Verdict? {
        val body = JSONObject()
            .put("mode", t.mode)
            .put("slots", slotsJson(t.slots))
            .put("asked_slot", t.askedSlot ?: JSONObject.NULL)
            .put("template", t.template ?: JSONObject.NULL)
            .put("level", t.level ?: JSONObject.NULL)
            .put("turn", t.turn)
            .put("question", t.question)
            .put("utterance", t.utterance)
        val j = postJson("/judge", body) ?: return null
        return try { parseVerdict(j) } catch (e: Exception) { warn("/judge parse", e); null }
    }

    internal fun parseVerdict(j: JSONObject) = Verdict(
        reason = j.optString("reason"),
        fills = (1..2).mapNotNull { i -> str(j, "slot_$i")?.let { s -> str(j, "value_$i")?.let { v -> s to v } } },
        nextSlot = str(j, "next_slot"),
        noLongerNeeded = str(j, "no_longer_needed"),
        storyReady = j.optBoolean("story_ready"),
        unclear = j.optBoolean("unclear"),
        unclearOf = str(j, "unclear_of"),
        contradiction = j.optBoolean("contradiction"),
        s1Reason = j.optBoolean("s1_reason"),
        s2Addition = j.optBoolean("s2_addition"),
        emotion = str(j, "emotion"),
    )

    // ── /turn ──────────────────────────────────────────────────────

    /** What the mascot says after the child (guidelines/7 §3). Placeholders `{주인공}` · `{친구n}` stay — unmask on the phone. */
    /** [options]: up to 3 short answers for the slot [question] asks — cards, then the mascot's pick (#79). Null in diary. */
    data class Line(val ack: String, val expand: String?, val question: String?, val options: List<String>? = null)

    /** Either half may be null — fill it from the script. [question] is null when [ask] was false or the story is ready. */
    data class TurnResult(val verdict: Verdict?, val line: Line?)

    /**
     * One turn: the verdict, then the mascot's three pieces, in one round trip (~3.3s, 09-29).
     * [ask] = false when the parent wrote the next question (coop) — the mascot only reacts.
     */
    suspend fun turn(t: Turn, ask: Boolean = true): TurnResult? {
        val body = JSONObject()
            .put("mode", t.mode)
            .put("slots", slotsJson(t.slots))
            .put("asked_slot", t.askedSlot ?: JSONObject.NULL)
            .put("template", t.template ?: JSONObject.NULL)
            .put("level", t.level ?: JSONObject.NULL)
            .put("turn", t.turn)
            .put("question", t.question)
            .put("utterance", t.utterance)
            .put("reason", t.reason ?: JSONObject.NULL)
            .put("ask", ask)
        val j = postJson("/turn", body, readMs = 30_000) ?: return null   // server answers within 25 s (turn_deadline_s)
        return try {
            TurnResult(
                verdict = j.optJSONObject("judge")?.let { parseVerdict(it) },
                line = j.optJSONObject("line")?.let { l ->
                    val options = l.optJSONArray("options")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }
                    Line(l.getString("ack"), str(l, "expand"), str(l, "question"), options?.takeIf { it.isNotEmpty() })
                },
            )
        } catch (e: Exception) { warn("/turn parse", e); null }
    }

    // ── /story ─────────────────────────────────────────────────────

    /** One page of the book plan: a `PageKind` name and the mission on it (`docs/미션_구상.md` §3 id), if any. */
    data class Page(val kind: String, val mission: String? = null, val prop: String? = null)

    /** Title is optional for compatibility with deployed servers that return captions only. */
    data class StoryBook(val captions: List<String>, val title: String? = null)

    /**
     * Book text. Null = keep the app's own template book. Captions still carry `{주인공}` · `{친구n}` — unmask on the phone.
     *
     * [pages] (09-29): the template's pages in order. When given, the answer has exactly one caption
     * per page, in that order — so a mission stays on the page the app put it. Mission pages end on the
     * setup ("불이 번졌어요"); the result line is still the app's to add.
     */
    suspend fun story(
        mode: String, slots: Map<String, String?>, slotBy: Map<String, String> = emptyMap(),
        keep: String? = null, template: String? = null, level: String? = null,
        pages: List<Page>? = null,
        /** coop only: the reason the parent picked — "done" · "soon" · "dream" (#52). null = a day that happened */
        reason: String? = null,
        /** coop only: the spots inside the picked item, scenery for the pages (#113). null = not sent */
        stage: List<String>? = null,
    ): List<String>? = storyBook(mode, slots, slotBy, keep, template, level, pages, reason, stage)?.captions

    /** The story mode consumes the same book response together with its optional title. */
    suspend fun storyBook(
        mode: String, slots: Map<String, String?>, slotBy: Map<String, String> = emptyMap(),
        keep: String? = null, template: String? = null, level: String? = null,
        pages: List<Page>? = null, reason: String? = null, stage: List<String>? = null,
    ): StoryBook? {
        val body = JSONObject()
            .put("mode", mode)
            .put("slots", slotsJson(slots))
            .put("slot_by", JSONObject().apply { slotBy.filterKeys { it in SLOTS }.forEach { (k, v) -> put(k, v) } })
            .put("keep", keep ?: JSONObject.NULL)
            .put("template", template ?: JSONObject.NULL)
            .put("level", level ?: JSONObject.NULL)
            .put("reason", reason ?: JSONObject.NULL)
        // the server refuses more than 5 or a spot over 12 chars (422) — trim here so a long list never costs the book
        stage?.map(String::trim)?.filter { it.isNotEmpty() && it.length <= 12 }?.take(5)?.takeIf(List<String>::isNotEmpty)
            ?.let { body.put("stage", JSONArray(it)) }
        if (pages != null) body.put("pages", JSONArray().apply {
            pages.forEach { put(JSONObject().put("kind", it.kind).put("mission", it.mission ?: JSONObject.NULL).put("prop", it.prop ?: JSONObject.NULL)) }
        })
        val j = postJson("/story", body, readMs = 60_000) ?: return null
        return try {
            val a = j.getJSONArray("scenes")
            val caps = List(a.length()) { a.getJSONObject(it).getString("caption") }
            // the server already refuses a wrong count; checking again costs nothing and keeps missions in place
            if (pages != null && caps.size != pages.size) {
                Log.w(TAG, "/story ${caps.size} pages, want ${pages.size}")
                null
            } else StoryBook(caps, (j.opt("title") as? String)?.trim()?.takeIf(String::isNotBlank))
        } catch (e: Exception) { warn("/story parse", e); null }
    }

    // ── /image ─────────────────────────────────────────────────────

    /**
     * A background for the place the child named, drawn and safety-checked on our GPU (rule 8).
     * PNG bytes, or **null = use the preset** — the server said preset (blocked, not a place, slow,
     * flagged), or the call failed. Call it the moment the place slot fills, in the background;
     * what to show while waiting and the 15 s preset line stay the caller's. [place] must be name-masked.
     */
    suspend fun image(place: String, mode: String = "story"): ByteArray? {
        val body = JSONObject().put("kind", "background").put("place", place).put("mode", mode)
        return postImage(body, "background") { png, _ -> png }
    }

    /**
     * One `/image` call → [made] with the PNG and the answer, or **null = preset** (the server said so,
     * the call failed, or the answer did not parse). The three kinds differ only in what they build.
     * The server gives up at 13 s and answers preset, so 16 s only covers the network.
     * A redraw waits behind the story pictures and gives up at 45 s — its caller passes 50 s (#32).
     */
    private suspend fun <T> postImage(body: JSONObject, kind: String, readMs: Int = 16_000, made: (ByteArray, JSONObject) -> T): T? {
        val j = postJson("/image", body, readMs = readMs) ?: return null
        return try {
            if (j.optBoolean("preset", true)) { Log.i(TAG, "/image $kind preset: ${j.optString("reason")}"); null }
            else made(android.util.Base64.decode(j.getString("png_base64"), android.util.Base64.DEFAULT), j)
        } catch (e: Exception) { warn("/image $kind parse", e); null }
    }

    /** A generated character: 640² PNG with a transparent background, feet on the 93% line, and its skeleton kind. */
    data class Character(val png: ByteArray, val rig: String)   // rig: human · quad · blob

    /**
     * A character from what the child said ("빨간 드레스 입은 공주"), posed so it can be rigged
     * (docs/캐릭터_생성_규격.md) and safety-checked (rule 8). **Null = use a preset doll.**
     * About 7-8 s warm — start it the moment the description is known, not when it is needed.
     * [description] must be name-masked.
     */
    suspend fun character(description: String, mode: String = "story"): Character? {
        val body = JSONObject().put("kind", "character").put("description", description).put("mode", mode)
        return postImage(body, "character") { png, j -> Character(png, j.getString("rig")) }
    }

    /**
     * 「오또가 대신 그려 주기」(issue #32): one piece the child **chose** to have redrawn → an Otto
     * drawing, 640² PNG, transparent, centred, safety-checked (rule 8). **Null = keep the child's own.**
     * [png] = the piece with a transparent background, cropped to what was drawn. It goes to our
     * server only — never to an outside company, never kept (guidelines/1 §1-5). Call it only when
     * the child picks it; the original stays the default. About 4 s warm. [description] must be name-masked.
     */
    suspend fun redraw(png: ByteArray, description: String, mode: String = "diary"): ByteArray? {
        val body = JSONObject().put("kind", "redraw").put("description", description).put("mode", mode)
            .put("png_base64", android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP))
        // nobody waits on it — the diary shows it at the next brush pause — so it can queue behind story pictures
        return postImage(body, "redraw", readMs = 50_000) { png, _ -> png }
    }

    // ── /stt ───────────────────────────────────────────────────────

    /**
     * Speech segment → text. "" means the server heard nothing usable (silence, a whisper
     * hallucination) — treat it as no answer. Null means the call failed.
     * ⚠️ The text still holds real names. Mask before it goes anywhere else (rule 6).
     */
    suspend fun stt(audio: ByteArray, fileName: String = "turn.wav", mime: String = "audio/wav"): String? {
        val boundary = "otto${System.nanoTime()}"
        val out = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(audio)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val (code, bytes) = post("/stt", out, "multipart/form-data; boundary=$boundary", readMs = 30_000) ?: return null
        if (code != 200) { Log.w(TAG, "/stt $code ${bytes.decodeToString()}"); return null }
        return try {
            JSONObject(bytes.decodeToString()).getString("text").also { Trace.line("heard", it.ifBlank { "(empty — no speech or a dropped hallucination)" }) }
        } catch (e: Exception) { warn("/stt parse", e); null }
    }

    // ── /tts ───────────────────────────────────────────────────────

    /** The mascot's line as audio bytes (mp3; wav in mock mode). Never send a real name (it leaves for TypeCast). */
    suspend fun tts(text: String, voiceId: String? = null, previous: String? = null, next: String? = null): ByteArray? {
        val body = JSONObject().put("text", text)
            .put("voice_id", voiceId ?: JSONObject.NULL)
            .put("previous_text", previous ?: JSONObject.NULL)
            .put("next_text", next ?: JSONObject.NULL)
        val (code, bytes) = post("/tts", body.toString().toByteArray(), JSON, readMs = 20_000) ?: return null   // server gives up at 15 s
        if (code != 200) { Log.w(TAG, "/tts $code ${bytes.decodeToString()}"); return null }
        return bytes
    }

    /** Is the server there, and is it the mock? Null = unreachable. */
    suspend fun health(): Boolean? = withContext(Dispatchers.IO) {
        val b = base ?: return@withContext null
        try {
            val c = URL("$b/health").openConnection() as HttpURLConnection
            c.connectTimeout = 3_000; c.readTimeout = 3_000
            if (c.responseCode != 200) null
            else JSONObject(c.inputStream.use { it.readBytes() }.decodeToString()).optBoolean("mock")
        } catch (e: Exception) { warn("/health", e); null }
    }

    // ── plumbing ───────────────────────────────────────────────────

    private const val JSON = "application/json; charset=utf-8"

    private fun slotsJson(slots: Map<String, String?>) =
        JSONObject().apply { SLOTS.forEach { put(it, slots[it] ?: JSONObject.NULL) } }

    private fun str(j: JSONObject, key: String): String? =
        if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotEmpty() }

    private suspend fun postJson(path: String, body: JSONObject, readMs: Int = 15_000): JSONObject? {
        val (code, bytes) = post(path, body.toString().toByteArray(), JSON, readMs) ?: return null
        // capped: a 422 echoes the request back, and a redraw request holds the child's drawing
        if (code != 200) { Log.w(TAG, "$path $code ${bytes.decodeToString().take(300)}"); return null }
        return try { JSONObject(bytes.decodeToString()) } catch (e: Exception) { warn("$path json", e); null }
    }

    // ── calls per session (10-06) ──────────────────────────────────
    //
    // The per-device limit for the Play build is set from what one real session costs, per mode.
    // The shared server's /stats mixes everyone's calls, so the phone counts its own: every POST
    // attempt by path, failures included (they still cost a request). Read at the end of a book.

    private val calls = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile private var callsSince = System.currentTimeMillis()

    /** "total 73 · /stt 24 · /turn 24 · /tts 22 · /story 1 · /image 2 · 18 min" — empty when nothing was called. */
    fun callSummary(): String {
        val snap = calls.toMap()
        if (snap.isEmpty()) return ""
        val min = (System.currentTimeMillis() - callsSince) / 60_000
        return (listOf("total ${snap.values.sum()}") + snap.entries.sortedByDescending { it.value }.map { "${it.key} ${it.value}" } + "$min min")
            .joinToString(" · ")
    }

    fun resetCalls() { calls.clear(); callsSince = System.currentTimeMillis() }

    private suspend fun post(path: String, body: ByteArray, type: String, readMs: Int = 15_000): Pair<Int, ByteArray>? =
        withContext(Dispatchers.IO) {
            val b = base ?: return@withContext null
            calls.merge(path, 1, Int::plus)
            val t0 = System.nanoTime()
            try {
                val c = URL(b + path).openConnection() as HttpURLConnection
                c.connectTimeout = 6_000   // 10-01: the public https address goes through Cloudflare; 4 s was tight on mobile data
                c.readTimeout = readMs
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body) }
                val code = c.responseCode
                val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                Trace.line("server", "$path $code ${(System.nanoTime() - t0) / 1_000_000} ms")
                code to bytes
            } catch (e: Exception) { Trace.line("server", "$path failed ${(System.nanoTime() - t0) / 1_000_000} ms"); warn(path, e); null }
        }

    private fun warn(what: String, e: Exception) = Log.w(TAG, "$what failed: ${e.javaClass.simpleName} ${e.message}")
}
