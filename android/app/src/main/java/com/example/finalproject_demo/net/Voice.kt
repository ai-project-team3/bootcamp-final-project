package com.example.finalproject_demo.net

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * The shared way in and out for all three modes (오케스트레이터 ①, 09-29): the child's voice in,
 * the mascot's voice out. Only used when `Server.liveFor(mode)` — otherwise the script runs as before.
 *
 * In:  mic → Silero VAD cuts 300 ms after the child stops (measured on this phone in
 *      DiagnosticsActivity: the built-in endpointer held the mic ~4.8 s on 14% of short answers)
 *      → WAV → `/stt` on our server. The recording never leaves our server (differentiator 1).
 * Out: text → `NameMask.speakable` → `/tts` → played here.
 *
 * [listen] and [transcribe] are swappable so the flow can be tested without a microphone.
 */
object Voice {
    private const val TAG = "Voice"
    const val RATE = 16_000

    @Volatile private var ctx: Context? = null

    /**
     * Can this process make sound? False in unit tests — then no `/tts` is even asked for.
     * Screen tests start MainActivity, which attaches; Robolectric's MediaPlayer never reports the
     * end, so a baked line (no server in the way) waited forever and hung the suite (10-01 #50 민우).
     */
    val canSpeak: Boolean get() = ctx != null && android.os.Build.FINGERPRINT != "robolectric"

    // ── lines baked into the app (10-01) ──────────────────────────
    //
    // Lines written in the app never change, so they are spoken once with the server's voice
    // (eval/bake_lines.py → assets/voice/<key>.mp3) and played from here: no wait, no `/tts` call.
    // Lines the server writes, and any line with a real name in it, still go to `/tts`.

    private val bakedNames: Set<String> by lazy {
        ctx?.assets?.list("voice")?.toSet() ?: emptySet()
    }

    /** The baked audio for exactly this line, or null — then the caller asks `/tts`. */
    fun baked(line: String): ByteArray? {
        val name = bakedKey(line) + ".mp3"
        if (name !in bakedNames) return null
        return runCatching { ctx?.assets?.open("voice/$name")?.use { it.readBytes() } }.getOrNull()
    }

    private var lastNeutral = -1

    /**
     * A content-neutral sound for the moment the child stops talking (「음~」 · 「응응.」 · 「응, 그랬구나.」 ·
     * eval/bake_neutral.py). The answer is not transcribed yet, so it says nothing about it; never the
     * same one twice in a row. Null when none are bundled.
     */
    fun neutral(): ByteArray? {
        val names = bakedNames.filter { it.startsWith("neutral_") }.sorted()
        if (names.isEmpty()) return null
        val i = names.indices.filter { it != lastNeutral || names.size == 1 }.random()
        lastNeutral = i
        return runCatching { ctx?.assets?.open("voice/${names[i]}")?.use { it.readBytes() } }.getOrNull()
    }

    /** Same key as eval/bake_lines.py: sha1 of the line with spaces collapsed, first 16 hex. */
    fun bakedKey(line: String): String {
        val norm = line.trim().replace(Regex("\\s+"), " ")
        return java.security.MessageDigest.getInstance("SHA-1").digest(norm.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    /** `MainActivity.onCreate`, next to `Server.base`. Loads the VAD model in the background right away. */
    fun attach(context: Context) {
        ctx = context.applicationContext
        Thread({ runCatching { vad() } }, "vad-warm").apply { isDaemon = true }.start()
    }

    // ── the VAD is built once ─────────────────────────────────────
    //
    // 09-29 S25+: 「로봇나라」→「롯나라」, 「블랙홀」→「쇠콜」 — the first syllable went missing.
    // Every 🎤 press used to load the Silero ONNX model *before* opening the mic, so a child who
    // spoke the moment they pressed lost the start. Now the model is loaded once (at app start)
    // and the mic opens first; the recorder buffer (2 s) holds the audio until the loop reads it.
    private var vadInstance: VadSilero? = null
    private val vadLock = Any()

    private fun vad(): VadSilero = synchronized(vadLock) {
        vadInstance ?: Vad.builder().setContext(ctx!!).setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(FrameSize.FRAME_SIZE_512).setMode(Mode.NORMAL)
            // 500 ms, not the 300 ms measured for adults: a child pauses mid-sentence
            // ("외계인이 … 창문에서") and 300 ms cut them off
            .setSpeechDurationMs(50).setSilenceDurationMs(500).build()
            .also { vadInstance = it }
    }

    /** Record one answer. `stop()` true = the child pressed ⏹. Null = nothing heard or no mic. */
    @Volatile var listen: suspend (stop: () -> Boolean) -> ByteArray? = { stop -> record(stop) }

    /** Audio → text. "" = nothing usable, null = the call failed. Real names still inside — mask before sending on. */
    @Volatile var transcribe: suspend (ByteArray) -> String? = { Server.stt(it) }

    // ── in ─────────────────────────────────────────────────────────

    /**
     * Records until VAD hears the end of speech (300 ms of silence after at least 50 ms of speech),
     * the child presses ⏹, or [maxMs]. Nothing heard at all → null.
     */
    @SuppressLint("MissingPermission")   // checked on the first line
    suspend fun record(stop: () -> Boolean, maxMs: Long = 15_000): ByteArray? = withContext(Dispatchers.IO) {
        val c = ctx ?: return@withContext null
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no RECORD_AUDIO"); return@withContext null
        }
        val frame = FrameSize.FRAME_SIZE_512.value
        var rec: AudioRecord? = null
        val pcm = ArrayList<Short>(RATE * 5)
        var heard = false
        try {
            // mic first — every millisecond before startRecording() is a lost start of a word
            val pressed = System.currentTimeMillis()
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE * 2 * 2))
            if (rec.state != AudioRecord.STATE_INITIALIZED) return@withContext null
            recording = true
            // nothing of the mascot may be in the child's answer — the player lives on the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post { stopPlaying() }
            rec.startRecording()
            val opened = System.currentTimeMillis()
            val vad = vad()                                  // normally already loaded at app start
            Log.i(TAG, "mic open ${opened - pressed} ms after press · vad ready ${System.currentTimeMillis() - pressed} ms")
            val buf = ShortArray(frame)
            val started = opened
            while (coroutineContext.isActive && !stop() && System.currentTimeMillis() - started < maxMs) {
                if (rec.read(buf, 0, frame) != frame) continue
                buf.forEach { pcm += it }
                val speech = vad.isSpeech(buf)
                if (speech) heard = true
                else if (heard) break                    // VAD already waited 500 ms of silence
            }
            // how long and what ended it — 10-01: "STT got worse" could not be told apart from a cut recording
            val ended = when { stop() -> "button"; System.currentTimeMillis() - started >= maxMs -> "max"; heard -> "vad"; else -> "cancel" }
            Log.i(TAG, "recorded %.1f s · ended by %s · speech %s".format(pcm.size / RATE.toFloat(), ended, if (heard) "heard" else "not heard"))
        } catch (e: Throwable) {
            Log.w(TAG, "record failed: ${e.javaClass.simpleName} ${e.message}")
            return@withContext null
        } finally {
            recording = false
            rec?.let { runCatching { it.stop() }; it.release() }
            // the VAD stays loaded for the next press
        }
        // ⏹ before VAD caught anything still sends what was recorded — a quiet child is still an answer
        if (pcm.isEmpty() || (!heard && !stop())) null else wav(pcm.toShortArray())
    }

    /** 16-bit mono PCM → a WAV file in memory (44-byte header). */
    fun wav(pcm: ShortArray, rate: Int = RATE): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val body = data.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + body.size).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(body.size)
        return ByteArrayOutputStream().apply { write(h.array()); write(body) }.toByteArray()
    }

    // ── out ────────────────────────────────────────────────────────

    private var player: MediaPlayer? = null
    @Volatile private var recording = false
    private var done: (() -> Unit)? = null

    /**
     * Play TTS bytes (mp3, wav in mock) and **return when it has finished** (or was stopped).
     * The caller queues lines one after another — 09-29 on the S25+: cutting the previous line
     * made the mascot skip half of what it said.
     */
    suspend fun playAndWait(audio: ByteArray) {
        val c = ctx ?: return
        // 10-01 evening: a line queued before 🎤 (fetched late from /tts) started playing *during* the
        // recording, so the mascot's voice went into the child's answer and the transcript fell apart.
        // While the mic is open, nothing plays.
        if (recording) { Log.i(TAG, "a mascot line arrived while recording — not played"); return }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val finish = { if (cont.isActive) cont.resume(Unit) }
                runCatching {
                    stopPlaying()
                    val f = File(c.cacheDir, "mascot_line_${System.nanoTime()}").apply { writeBytes(audio) }
                    player = MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { it.release(); f.delete(); if (player === it) player = null; finish() }
                        setOnErrorListener { mp, _, _ -> mp.release(); f.delete(); if (player === mp) player = null; finish(); true }
                        prepare(); start()
                    }
                    done = finish
                }.onFailure { Log.w(TAG, "play failed: ${it.message}"); finish() }
                cont.invokeOnCancellation { stopPlaying() }
            }
        }
    }

    /** Stop the voice now — the child pressed 🎤, so the mascot must not be recorded. */
    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        done?.invoke()
        done = null
    }
}
