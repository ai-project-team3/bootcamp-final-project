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

    /** `MainActivity.onCreate`, next to `Server.base`. */
    fun attach(context: Context) { ctx = context.applicationContext }

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
        var vad: VadSilero? = null
        val pcm = ArrayList<Short>(RATE * 5)
        var heard = false
        try {
            vad = Vad.builder().setContext(c).setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_512).setMode(Mode.NORMAL)
                .setSpeechDurationMs(50).setSilenceDurationMs(300).build()
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 8))
            if (rec.state != AudioRecord.STATE_INITIALIZED) return@withContext null
            val buf = ShortArray(frame)
            val started = System.currentTimeMillis()
            rec.startRecording()
            while (coroutineContext.isActive && !stop() && System.currentTimeMillis() - started < maxMs) {
                if (rec.read(buf, 0, frame) != frame) continue
                buf.forEach { pcm += it }
                val speech = vad.isSpeech(buf)
                if (speech) heard = true
                else if (heard) break                    // VAD already waited 300 ms of silence
            }
        } catch (e: Throwable) {
            Log.w(TAG, "record failed: ${e.javaClass.simpleName} ${e.message}")
            return@withContext null
        } finally {
            rec?.let { runCatching { it.stop() }; it.release() }
            runCatching { vad?.close() }
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

    /** Play TTS bytes (mp3, wav in mock). Stops whatever was playing — one mascot, one voice. */
    fun play(audio: ByteArray) {
        val c = ctx ?: return
        runCatching {
            stopPlaying()
            val f = File(c.cacheDir, "mascot_line").apply { writeBytes(audio) }
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare(); start()
            }
        }.onFailure { Log.w(TAG, "play failed: ${it.message}") }
    }

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}
