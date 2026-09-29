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
    )

    /** The 16-field verdict (guidelines/2 §2). Slot names are already checked against the 12 by the server. */
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
    data class Line(val ack: String, val expand: String?, val question: String?)

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
            .put("ask", ask)
        val j = postJson("/turn", body, readMs = 20_000) ?: return null
        return try {
            TurnResult(
                verdict = j.optJSONObject("judge")?.let { parseVerdict(it) },
                line = j.optJSONObject("line")?.let { Line(it.getString("ack"), str(it, "expand"), str(it, "question")) },
            )
        } catch (e: Exception) { warn("/turn parse", e); null }
    }

    // ── /story ─────────────────────────────────────────────────────

    /** One page of the book plan: a `PageKind` name and the mission on it (`docs/미션_구상.md` §3 id), if any. */
    data class Page(val kind: String, val mission: String? = null)

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
    ): List<String>? {
        val body = JSONObject()
            .put("mode", mode)
            .put("slots", slotsJson(slots))
            .put("slot_by", JSONObject().apply { slotBy.filterKeys { it in SLOTS }.forEach { (k, v) -> put(k, v) } })
            .put("keep", keep ?: JSONObject.NULL)
            .put("template", template ?: JSONObject.NULL)
            .put("level", level ?: JSONObject.NULL)
        if (pages != null) body.put("pages", JSONArray().apply {
            pages.forEach { put(JSONObject().put("kind", it.kind).put("mission", it.mission ?: JSONObject.NULL)) }
        })
        val j = postJson("/story", body, readMs = 60_000) ?: return null
        return try {
            val a = j.getJSONArray("scenes")
            val caps = List(a.length()) { a.getJSONObject(it).getString("caption") }
            // the server already refuses a wrong count; checking again costs nothing and keeps missions in place
            if (pages != null && caps.size != pages.size) { Log.w(TAG, "/story ${caps.size} pages, want ${pages.size}"); null } else caps
        } catch (e: Exception) { warn("/story parse", e); null }
    }

    // ── /image ─────────────────────────────────────────────────────

    /**
     * A background for the place the child named, drawn and safety-checked on our GPU (rule 8).
     * PNG bytes, or **null = use the preset** — the server said preset (blocked, not a place, slow,
     * flagged), or the call failed. Call it the moment the place slot fills, in the background;
     * the 8 s "조금 뒤에 올 거야" and the 15 s preset line stay the caller's. [place] must be name-masked.
     */
    suspend fun image(place: String, mode: String = "story"): ByteArray? {
        val body = JSONObject().put("kind", "background").put("place", place).put("mode", mode)
        // the server gives up at 13 s and answers preset, so 16 s only covers the network
        val j = postJson("/image", body, readMs = 16_000) ?: return null
        return try {
            if (j.optBoolean("preset", true)) { Log.i(TAG, "/image preset: ${j.optString("reason")}"); null }
            else android.util.Base64.decode(j.getString("png_base64"), android.util.Base64.DEFAULT)
        } catch (e: Exception) { warn("/image parse", e); null }
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
        val j = postJson("/image", body, readMs = 16_000) ?: return null
        return try {
            if (j.optBoolean("preset", true)) { Log.i(TAG, "/image character preset: ${j.optString("reason")}"); null }
            else Character(android.util.Base64.decode(j.getString("png_base64"), android.util.Base64.DEFAULT), j.getString("rig"))
        } catch (e: Exception) { warn("/image character parse", e); null }
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
        return try { JSONObject(bytes.decodeToString()).getString("text") } catch (e: Exception) { warn("/stt parse", e); null }
    }

    // ── /tts ───────────────────────────────────────────────────────

    /** The mascot's line as audio bytes (mp3; wav in mock mode). Never send a real name (it leaves for TypeCast). */
    suspend fun tts(text: String, voiceId: String? = null, previous: String? = null, next: String? = null): ByteArray? {
        val body = JSONObject().put("text", text)
            .put("voice_id", voiceId ?: JSONObject.NULL)
            .put("previous_text", previous ?: JSONObject.NULL)
            .put("next_text", next ?: JSONObject.NULL)
        val (code, bytes) = post("/tts", body.toString().toByteArray(), JSON) ?: return null
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
        if (code != 200) { Log.w(TAG, "$path $code ${bytes.decodeToString()}"); return null }
        return try { JSONObject(bytes.decodeToString()) } catch (e: Exception) { warn("$path json", e); null }
    }

    private suspend fun post(path: String, body: ByteArray, type: String, readMs: Int = 15_000): Pair<Int, ByteArray>? =
        withContext(Dispatchers.IO) {
            val b = base ?: return@withContext null
            try {
                val c = URL(b + path).openConnection() as HttpURLConnection
                c.connectTimeout = 4_000
                c.readTimeout = readMs
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body) }
                val code = c.responseCode
                val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                code to bytes
            } catch (e: Exception) { warn(path, e); null }
        }

    private fun warn(what: String, e: Exception) = Log.w(TAG, "$what failed: ${e.javaClass.simpleName} ${e.message}")
}
