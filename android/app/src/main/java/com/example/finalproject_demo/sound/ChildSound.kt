package com.example.finalproject_demo.sound

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
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * A sound the child makes for the story (a friend's cry, "어흥") — recorded, played and kept
 * **on this phone only** (differentiator 1 · issue #42). Not the talking path: that one is
 * [Voice] → `/stt`. Nothing here talks to the server, and the files live in `noBackupFilesDir`
 * (and the app has `allowBackup="false"`), so no copy leaves the phone.
 *
 * No ⏹ to press: the mic listens up to [MAX_MS], stops [QUIET_END_MS] after the sound ends,
 * and the quiet before and after is cut off. Loudness, not speech detection — Silero only
 * knows speech, and a roar or a whistle is not speech.
 *
 * Life of a clip: [record] → session folder → [keep] into the book's folder when the book is
 * saved, or [discardSession] when the session ends without a book. [deleteBook] with the book.
 */
object ChildSound {
    private const val TAG = "ChildSound"
    const val RATE = Voice.RATE
    const val MAX_MS = 5_000L
    const val QUIET_END_MS = 1_000L
    const val FRAME = RATE / 50                // 20 ms
    const val LOUD_RMS = 700.0                 // 16-bit RMS, about -33 dBFS — device-untested first value
    private const val PAD_FRAMES = 8           // keep 160 ms either side of the sound

    data class SoundClip(val id: String, val file: File)

    @Volatile private var ctx: Context? = null
    /** Where the clips live. Set by [attach]; tests point it at a temp folder. */
    @Volatile var root: File? = null

    /** Mic → 16-bit mono PCM, or null (no mic, no permission). Swappable for tests. */
    @Volatile var capture: suspend (maxMs: Long) -> ShortArray? = { mic(it) }

    fun attach(context: Context) {
        ctx = context.applicationContext
        root = File(context.applicationContext.noBackupFilesDir, "child_sounds")
    }

    private fun session() = File(root ?: error("ChildSound not attached"), "session").apply { mkdirs() }
    private fun book(bookId: String) = File(root ?: error("ChildSound not attached"), "books/${safe(bookId)}")
    private fun safe(s: String) = s.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "book" }

    /** Listen, trim, save to the session folder. Null = nothing loud enough was heard, or no mic. */
    suspend fun record(maxMs: Long = MAX_MS): SoundClip? {
        val pcm = capture(maxMs) ?: return null
        val sound = trim(pcm) ?: return null
        return withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val f = File(session(), "$id.wav").apply { writeBytes(Voice.wav(louder(sound), RATE)) }
            SoundClip(id, f)
        }
    }

    /** Drop one clip the child did not want (e.g. recorded again). */
    fun cancel(clip: SoundClip) { clip.file.delete() }

    /** The book was saved: move the clip under it. Null = the clip is gone (already kept or cancelled). */
    fun keep(clip: SoundClip, bookId: String): SoundClip? {
        if (!clip.file.exists()) return null
        val dir = book(bookId).apply { mkdirs() }
        val to = File(dir, clip.file.name)
        if (!clip.file.renameTo(to)) { clip.file.copyTo(to, overwrite = true); clip.file.delete() }
        return SoundClip(clip.id, to)
    }

    /** A kept clip by id, for playing it back from a reopened book. */
    fun find(bookId: String, id: String): SoundClip? =
        File(book(bookId), "${safe(id)}.wav").takeIf { it.exists() }?.let { SoundClip(id, it) }

    /** The session ended without a book (or the app restarted): nothing un-kept survives. */
    fun discardSession() { root?.let { File(it, "session").deleteRecursively() } }

    fun deleteBook(bookId: String) { book(bookId).deleteRecursively() }

    /** Plays one clip and returns when it ends (or fails). */
    suspend fun play(clip: SoundClip) {
        if (!clip.file.exists()) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val p = MediaPlayer()
                var louder: android.media.audiofx.LoudnessEnhancer? = null
                fun done() { runCatching { louder?.release() }; runCatching { p.release() }; if (cont.isActive) cont.resume(Unit) }
                try {
                    p.setDataSource(clip.file.path)
                    p.setOnCompletionListener { done() }
                    p.setOnErrorListener { _, _, _ -> done(); true }
                    p.prepare()
                    // Louder on playback only — the file stays the child's original (guidelines/1 §1-5).
                    // 10-05 device: 「음성 녹음의 소리가 좀 작다」. +9 dB; a phone without the effect plays as before
                    louder = runCatching {
                        android.media.audiofx.LoudnessEnhancer(p.audioSessionId).apply { setTargetGain(900); enabled = true }
                    }.getOrNull()
                    p.start()
                } catch (e: Exception) { Log.w(TAG, "play failed: ${e.javaClass.simpleName}"); done() }
                cont.invokeOnCancellation { runCatching { p.stop() }; runCatching { p.release() } }
            }
        }
    }

    // ── pure parts (tested without a phone) ─────────────────────────

    fun rms(pcm: ShortArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) { val v = pcm[i].toDouble(); sum += v * v }
        return if (to > from) sqrt(sum / (to - from)) else 0.0
    }

    /** Cut the quiet before and after the sound. Null = nothing was loud enough to be a sound. */
    fun trim(pcm: ShortArray, loud: Double = LOUD_RMS): ShortArray? {
        val frames = pcm.size / FRAME
        var first = -1; var last = -1
        for (f in 0 until frames) {
            if (rms(pcm, f * FRAME, (f + 1) * FRAME) >= loud) { if (first < 0) first = f; last = f }
        }
        if (first < 0) return null
        val from = (first - PAD_FRAMES).coerceAtLeast(0) * FRAME
        val to = ((last + 1 + PAD_FRAMES) * FRAME).coerceAtMost(pcm.size)
        return pcm.copyOfRange(from, to)
    }

    /**
     * The clip at about the mascot's loudness (#42, 10-02: a recorded sound played back much quieter than
     * Otto). The loud frames are brought to [TARGET_RMS]; the gain stops before any sample passes
     * [PEAK] and never goes past [MAX_GAIN], so a whisper does not turn into room noise.
     */
    fun louder(pcm: ShortArray, loud: Double = LOUD_RMS): ShortArray {
        var sum = 0.0; var n = 0; var peak = 1
        for (f in 0 until pcm.size / FRAME) {
            val r = rms(pcm, f * FRAME, (f + 1) * FRAME)
            if (r >= loud) { sum += r * r * FRAME; n += FRAME }
        }
        for (v in pcm) peak = maxOf(peak, kotlin.math.abs(v.toInt()))
        if (n == 0) return pcm
        val gain = minOf(TARGET_RMS / sqrt(sum / n), PEAK / peak, MAX_GAIN)
        if (gain <= 1.0) return pcm                 // already loud enough — never turn a child down
        return ShortArray(pcm.size) { (pcm[it] * gain).toInt().coerceIn(-32768, 32767).toShort() }
    }

    const val TARGET_RMS = 3_277.0             // -20 dBFS, the mascot lines' level (backend/app/audio_level.py)
    const val PEAK = 27_500.0                  // -1.5 dBFS
    const val MAX_GAIN = 8.0                   // +18 dB at most

    /** True once a sound has been heard and [QUIET_END_MS] of quiet has followed it. */
    fun soundEnded(loudSeen: Boolean, quietFrames: Int): Boolean =
        loudSeen && quietFrames * 20L >= QUIET_END_MS

    @SuppressLint("MissingPermission")   // checked on the first line
    private suspend fun mic(maxMs: Long): ShortArray? = withContext(Dispatchers.IO) {
        val c = ctx ?: return@withContext null
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no RECORD_AUDIO"); return@withContext null
        }
        var rec: AudioRecord? = null
        val out = ShortArray((RATE * maxMs / 1000).toInt())
        var n = 0
        try {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // MIC, not VOICE_RECOGNITION: this is played back to the child, so let the phone even the level
            rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE * 2 * 2))
            if (rec.state != AudioRecord.STATE_INITIALIZED) return@withContext null
            rec.startRecording()
            val buf = ShortArray(FRAME)
            var loudSeen = false; var quiet = 0
            while (coroutineContext.isActive && n + FRAME <= out.size) {
                if (rec.read(buf, 0, FRAME) != FRAME) continue
                buf.copyInto(out, n); n += FRAME
                if (rms(buf, 0, FRAME) >= LOUD_RMS) { loudSeen = true; quiet = 0 } else quiet++
                if (soundEnded(loudSeen, quiet)) break
            }
        } catch (e: Throwable) {
            Log.w(TAG, "record failed: ${e.javaClass.simpleName}")
            return@withContext null
        } finally {
            rec?.let { runCatching { it.stop() }; it.release() }
        }
        out.copyOf(n)
    }
}
