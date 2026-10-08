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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * In:  mic → Silero VAD cuts 500 ms after the child stops (measured on this phone in
 *      DiagnosticsActivity: the built-in endpointer held the mic ~4.8 s on 14% of short answers)
 *      → WAV → `/stt` on our server. The recording never leaves our server (differentiator 1).
 * Out: text → `NameMask.speakable` → a line baked into the app ([baked]) or `/tts` → played here.
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
    @Volatile var transcribe: suspend (ByteArray) -> String? = { audio ->
        val loudest = loudestDbfs(audio)
        if (loudest != null && loudest < SILENCE_DBFS) {
            // a level only — no audio, no words
            Log.i(TAG, "silent recording (loudest %.0f dBFS) — not sent to /stt".format(loudest))
            Trace.line("heard", "(empty — silent on the phone, loudest %.0f dBFS, not sent)".format(loudest))
            ""
        } else Server.stt(audio)
    }

    // ── silence stays on the phone (#331) ──────────────────────────
    //
    // 10-08 device (민우): on a quiet mic, ~1 s recordings came back as 「자막을 키고 해줘」 — whisper writes a
    // line when it is given nothing to hear. A recording with no frame anywhere near a voice is answered here
    // as "" (the same path as an empty transcript: 「한 번 더 말해 줄래?」), without a server call.
    // The floor is the server's own pause gate (`backend/app/audio_level.py` GATE_DBFS: 20 ms frames quieter
    // than -45 dBFS are pauses, not voice), and it judges the LOUDEST frame, so one syllable keeps the clip.
    // Rule 7 — a quiet child is still an answer. Measured on the same mic source (VOICE_RECOGNITION, SM-G977N
    // 10-07, ui/missions/BlowDetector.kt): speech at 30 cm averages 0.10~0.21 of 6,000 per 64 ms frame, i.e.
    // about -33 dBFS RMS at its quietest — 12 dB above this line. Room noise there was 0.02~0.1 (-47~-33 dBFS),
    // so a normal room keeps its clips and the server's phrase list stays the net for them; this catches the
    // near-silent mic of #331. Not measured with children yet: OttoTrace keeps the level of every clip held back.

    /** Below this, the loudest 20 ms frame of a recording is no voice at all (dBFS). */
    const val SILENCE_DBFS = -45.0

    /**
     * The loudest 20 ms frame of a recording, in dBFS — or null when [wav] is not the 16-bit mono WAV [wav]
     * writes (then nothing is judged here and the server hears it as before).
     */
    fun loudestDbfs(wav: ByteArray): Double? {
        if (wav.size <= 44) return null
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(wav, at, 4, Charsets.US_ASCII)
        if (tag(0) != "RIFF" || tag(8) != "WAVE" || tag(36) != "data" || b.getShort(22).toInt() != 1 ||
            b.getShort(34).toInt() != 16) return null
        val rate = b.getInt(24).takeIf { it > 0 } ?: return null
        val samples = (wav.size - 44) / 2
        val frame = maxOf(1, rate / 50)
        var loudest = 0.0
        var i = 0
        while (i < samples) {
            val n = minOf(frame, samples - i)
            if (n < frame && i > 0) break                  // a few samples at the end are not a frame
            var sum = 0.0
            for (k in 0 until n) { val v = b.getShort(44 + (i + k) * 2).toDouble(); sum += v * v }
            loudest = maxOf(loudest, kotlin.math.sqrt(sum / n))
            i += n
        }
        return 20 * kotlin.math.log10(maxOf(loudest, 1e-3) / 32768.0)
    }

    // ── in ─────────────────────────────────────────────────────────

    /**
     * Records until VAD hears the end of speech (500 ms of silence after at least 50 ms of speech),
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
    private var lineFile: File? = null
    private val speaking = MutableStateFlow(false)

    /**
     * The mascot's voice is going out of the speaker now (#258 · 10-07 device). The blow and sound missions leave these
     * frames out — Otto reading the page aloud into the same phone's mic finished C1 without a breath.
     */
    val playing: StateFlow<Boolean> = speaking
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
                    lineFile = f
                    player = MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { it.release(); f.delete(); if (player === it) { player = null; speaking.value = false }; finish() }
                        setOnErrorListener { mp, _, _ -> mp.release(); f.delete(); if (player === mp) { player = null; speaking.value = false }; finish(); true }
                        prepare(); start()
                    }
                    speaking.value = true
                    done = finish
                }.onFailure { Log.w(TAG, "play failed: ${it.message}"); stopPlaying(); finish() }
                // cancelled from any thread (a tap · 🎤): the player lives on Main
                cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { stopPlaying() } }
            }
        }
    }

    /** Stop the voice now — 🎤 (the mascot must not be recorded) or a tap that cuts the line. */
    /**
     * The line playing now starts again from its beginning (10-07 · a tap on a character in the book).
     * False when nothing is playing — the caller then says the last line again.
     */
    fun restartPlaying(): Boolean {
        val p = player ?: return false
        return runCatching { p.seekTo(0); if (!p.isPlaying) p.start(); true }.getOrDefault(false)
    }

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        speaking.value = false
        // a cut line never reaches its completion listener, so its temp file is removed here (10-02)
        lineFile?.delete()
        lineFile = null
        done?.invoke()
        done = null
    }
}
