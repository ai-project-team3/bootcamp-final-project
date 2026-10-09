package com.example.finalproject_demo.net

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * The one background-music player (#221). [BgmMixer] decides every volume; this file plays it with
 * [MediaPlayer] and ticks it every 50 ms while something sounds. Callers may come from any thread
 * (the mic loop, the blow mission); everything runs on the main thread.
 */
object Bgm {
    private const val TAG = "Bgm"
    private const val TICK_MS = 50L
    // lazy: the plain-JVM flow tests (no Robolectric) never attach, and Looper is not mocked there
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var ctx: Context? = null

    internal var clock: () -> Long = { System.nanoTime() / 1_000_000 }
    /** test seam: every [duck] call is reported here too */
    internal var onDuck: (Boolean) -> Unit = {}
    internal var output: BgmOutput = BgmOutput { track -> open(track) }
    internal var mixer = BgmMixer { output.open(it) }
        private set

    fun attach(context: Context) { ctx = context.applicationContext }

    fun play(track: String) = onMain { mixer.play(track, clock()); log("play $track") }
    // every scene change calls stop — log it only when a track was actually playing
    fun stop() = onMain { val was = mixer.playing; mixer.stop(clock()); if (was != null) log("stop $was") }
    fun duck(on: Boolean) { onDuck(on); onMain { mixer.duck(on, clock()) } }
    // hold · resume come with every recording — logged only while music is there, so question scenes stay quiet
    fun hold(reason: String) = onMain { mixer.hold(reason); if (mixer.active) log("hold $reason · ${mixer.playing}") }
    /**
     * [hold], and from another thread wait until it is applied — a mic thread calls this right before
     * `startRecording()`, and a plain post let the first tens of ms of music into the recording (#295 review).
     * Capped so a busy main thread never keeps the mic shut.
     */
    fun holdNow(reason: String) {
        if (ctx == null || Looper.myLooper() == Looper.getMainLooper()) { hold(reason); return }
        val applied = java.util.concurrent.CountDownLatch(1)
        onMain { mixer.hold(reason); if (mixer.active) log("hold $reason · ${mixer.playing}"); applied.countDown() }
        if (!applied.await(HOLD_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) log("hold $reason not applied in $HOLD_WAIT_MS ms")
    }
    private const val HOLD_WAIT_MS = 300L
    fun resume(reason: String) = onMain { mixer.resume(reason, clock()); if (mixer.active) log("resume $reason${if (mixer.held) " (still held)" else ""}") }
    fun setEnabled(on: Boolean) = onMain { mixer.setEnabled(on, clock()); log("music ${if (on) "on" else "off"}") }
    fun release() = onMain { mixer.release() }

    /** After [release] (activity teardown): drop the ticker and the context so a destroyed activity is not kept alive */
    fun detach() {
        if (ctx != null) main.removeCallbacks(ticker)
        ctx = null
    }

    private val ticker = object : Runnable {
        override fun run() {
            mixer.tick(clock())
            if (mixer.active && !mixer.held) main.postDelayed(this, TICK_MS)
        }
    }

    private fun onMain(block: () -> Unit) {
        // not attached (tests without Android): run in place, no ticker — the mixer is ticked by hand there
        if (ctx == null) { block(); return }
        val run = { block(); main.removeCallbacks(ticker); if (mixer.active && !mixer.held) main.post(ticker) }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    /** A packed asset cannot be opened by fd, so the track is copied to the cache once (via a temp file, so a partial copy heals) */
    internal fun open(track: String): BgmChannel? {
        val c = ctx ?: return null
        return runCatching {
            val f = File(c.cacheDir, "bgm/$track")
            if (!f.isFile) {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, "$track.tmp")
                try {
                    c.assets.open("bgm/$track").use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    if (!tmp.renameTo(f)) throw java.io.IOException("cannot move $track into place")
                } catch (e: Throwable) { tmp.delete(); throw e }
            }
            val mp = MediaPlayer()
            try { mp.setDataSource(f.absolutePath); mp.isLooping = true; mp.prepare() } catch (e: Throwable) { mp.release(); throw e }
            safeChannel(track, mp)
        }.onFailure { Log.w(TAG, "cannot play $track: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
    }

    /** Every call is guarded: a player in the Error state throws, and playback trouble must stay silent */
    private fun safeChannel(track: String, mp: MediaPlayer): BgmChannel = object : BgmChannel {
        @Volatile private var dead = false
        private var logged = false
        init { mp.setOnErrorListener { _, what, extra -> dead = true; Log.w(TAG, "player error on $track: $what/$extra"); true } }
        private fun <T> guard(fallback: T, f: () -> T): T {
            if (dead) return fallback
            return runCatching(f).getOrElse {
                dead = true
                if (!logged) { logged = true; log("channel $track failed: ${it.javaClass.simpleName}") }
                fallback
            }
        }
        // <= 0 means unknown → 0: the mixer never runs its loop seam and the player loops by itself
        override val durationMs get() = guard(0L) { mp.duration.toLong().coerceAtLeast(0) }
        override val positionMs get() = guard(0L) { mp.currentPosition.toLong() }
        override fun start() = guard(Unit) { mp.start() }
        override fun pause() = guard(Unit) { if (mp.isPlaying) mp.pause() }
        override fun setVolume(v: Float) = guard(Unit) { mp.setVolume(v, v) }
        override fun release() { runCatching { mp.release() }; dead = true }
    }

    private fun log(s: String) { runCatching { Log.i(TAG, s) } }

    internal fun resetForTest() {
        if (ctx != null) main.removeCallbacks(ticker)
        mixer.release()
        ctx = null
        output = BgmOutput { track -> open(track) }
        mixer = BgmMixer { output.open(it) }
        clock = { System.nanoTime() / 1_000_000 }
    }

    /** a mixer on the current [output] — tests set a fake output first */
    internal fun resetMixerForTest() { mixer = BgmMixer { output.open(it) } }
}
