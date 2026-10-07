package com.example.finalproject_demo.net

/*
 * Background-music mixer (#221 · docs/superpowers/specs/2026-10-07-bgm-design.md). Pure Kotlin — the clock
 * and the player come in from outside, so every fade can be tested by turning the clock by hand.
 * The tracks carry no fades: start, stop, mood changes and the loop seam are all faded here.
 */

const val BGM_BASE = 0.25f          // under the narration
const val BGM_DUCK = 0.32f          // × while Otto speaks → about 0.08
const val BGM_FADE_MS = 1500L       // start · stop · a new mood
const val BGM_LOOP_MS = 2000L       // the loop seam crossfade
const val BGM_RAMP_MS = 300L        // duck down / back up
const val BGM_UNDUCK_DELAY_MS = 700L // lines come 400 ms apart — do not pump between them

interface BgmChannel {
    val durationMs: Long
    val positionMs: Long
    fun start()
    fun pause()
    fun setVolume(v: Float)
    fun release()
}

fun interface BgmOutput {
    /** null = this track cannot be played here (missing asset · no Opus decoder) — the mixer stays silent */
    fun open(track: String): BgmChannel?
}

class BgmMixer(private val out: BgmOutput) {
    private class Voice(val track: String, val ch: BgmChannel) {
        var from = 0f; var to = 1f; var start = 0L; var len = 0L
        var dying = false; var looped = false
        // read once: the length never changes, and asking every 50 ms was ~2,400 calls in 3.5 min (10-07 device)
        val durationMs = ch.durationMs
        private var applied = Float.NaN
        /** sets the channel volume only when it changed — a steady level is not re-sent every tick */
        fun volume(v: Float) {
            if (applied.isNaN() || kotlin.math.abs(v - applied) > 0.0005f) { ch.setVolume(v); applied = v }
        }
        fun gain(now: Long): Float =
            if (len <= 0) to else from + (to - from) * ((now - start).toFloat() / len).coerceIn(0f, 1f)
        fun fade(now: Long, target: Float, ms: Long) { from = gain(now); to = target; start = now; len = ms }
    }

    private val voices = mutableListOf<Voice>()
    private val holds = mutableSetOf<String>()
    private var enabled = true
    private var wanted: String? = null
    private var duckFrom = 1f; private var duckTo = 1f; private var duckStart = 0L
    private var unduckAt = -1L

    val playing: String? get() = voices.lastOrNull { !it.dying }?.track
    val active: Boolean get() = voices.isNotEmpty()
    /** tick() returns early while held, so a held mixer needs no ticker */
    val held: Boolean get() = holds.isNotEmpty()

    private fun current() = voices.lastOrNull { !it.dying }
    private fun duckGain(now: Long) = duckFrom + (duckTo - duckFrom) * ((now - duckStart).toFloat() / BGM_RAMP_MS).coerceIn(0f, 1f)

    fun play(track: String, now: Long) {
        wanted = track
        if (!enabled) return
        if (current()?.track == track) return
        current()?.let { it.dying = true; it.fade(now, 0f, BGM_FADE_MS) }
        val ch = out.open(track) ?: return
        val v = Voice(track, ch).apply { from = 0f; to = 1f; start = now; len = BGM_FADE_MS }
        voices += v
        v.volume(0f)
        if (holds.isEmpty()) ch.start()
    }

    fun stop(now: Long) {
        wanted = null
        current()?.let { it.dying = true; it.fade(now, 0f, BGM_FADE_MS) }
    }

    fun duck(on: Boolean, now: Long) {
        if (on) {
            unduckAt = -1
            if (duckTo != BGM_DUCK) { duckFrom = duckGain(now); duckTo = BGM_DUCK; duckStart = now }
        } else if (duckTo == BGM_DUCK && unduckAt < 0) unduckAt = now + BGM_UNDUCK_DELAY_MS
    }

    fun hold(reason: String) {
        holds += reason
        voices.filter { it.dying }.forEach { it.ch.release() }
        voices.removeAll { it.dying }
        voices.forEach { it.ch.pause() }
    }

    fun resume(reason: String, now: Long) {
        if (!holds.remove(reason) || holds.isNotEmpty()) return
        voices.filter { it.dying }.forEach { it.ch.release() }
        voices.removeAll { it.dying }
        voices.forEach { v -> v.from = 0f; v.to = 1f; v.start = now; v.len = BGM_FADE_MS; v.volume(0f); v.ch.start() }
    }

    /** For teardown when the player goes away: frees every channel at once and forgets the wanted track. */
    fun release() {
        voices.forEach { it.ch.release() }
        voices.clear(); holds.clear()
        duckFrom = 1f; duckTo = 1f; unduckAt = -1
        wanted = null
    }

    fun setEnabled(on: Boolean, now: Long) {
        if (on == enabled) return
        enabled = on
        if (!on) { val keep = wanted; stop(now); wanted = keep }
        else wanted?.let { w -> wanted = null; play(w, now) }
    }

    fun tick(now: Long) {
        if (holds.isNotEmpty()) return
        if (unduckAt in 0..now) { duckFrom = duckGain(now); duckTo = 1f; duckStart = now; unduckAt = -1 }
        current()?.let { v ->
            val d = v.durationMs
            if (!v.looped && d > BGM_LOOP_MS && v.ch.positionMs >= d - BGM_LOOP_MS) {
                v.looped = true
                out.open(v.track)?.let { ch ->
                    v.dying = true; v.fade(now, 0f, BGM_LOOP_MS)
                    voices += Voice(v.track, ch).apply { from = 0f; to = 1f; start = now; len = BGM_LOOP_MS; volume(0f) }
                    ch.start()
                }
            }
        }
        val duck = duckGain(now)
        val done = voices.filter { it.dying && now - it.start >= it.len }
        done.forEach { it.ch.release() }
        voices.removeAll(done)
        voices.forEach { it.volume(BGM_BASE * duck * it.gain(now)) }
    }
}
