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
    internal var output: BgmOutput = BgmOutput { track -> open(track) }
    internal var mixer = BgmMixer { output.open(it) }
        private set

    fun attach(context: Context) { ctx = context.applicationContext }

    fun play(track: String) = onMain { mixer.play(track, clock()); log("play $track") }
    fun stop() = onMain { mixer.stop(clock()) }
    fun duck(on: Boolean) = onMain { mixer.duck(on, clock()) }
    fun hold(reason: String) = onMain { mixer.hold(reason) }
    fun resume(reason: String) = onMain { mixer.resume(reason, clock()) }
    fun setEnabled(on: Boolean) = onMain { mixer.setEnabled(on, clock()) }
    fun release() = onMain { mixer.release() }

    private val ticker = object : Runnable {
        override fun run() {
            mixer.tick(clock())
            if (mixer.active) main.postDelayed(this, TICK_MS)
        }
    }

    private fun onMain(block: () -> Unit) {
        // not attached (tests without Android): run in place, no ticker — the mixer is ticked by hand there
        if (ctx == null) { block(); return }
        val run = { block(); main.removeCallbacks(ticker); if (mixer.active) main.post(ticker) }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    /** A packed asset cannot be opened by fd, so the track is copied to the cache once */
    private fun open(track: String): BgmChannel? {
        val c = ctx ?: return null
        return runCatching {
            val f = File(c.cacheDir, "bgm/$track")
            if (!f.isFile) { f.parentFile?.mkdirs(); c.assets.open("bgm/$track").use { i -> f.outputStream().use { i.copyTo(it) } } }
            val mp = MediaPlayer().apply { setDataSource(f.absolutePath); isLooping = true; prepare() }
            object : BgmChannel {
                // <= 0 means unknown → 0: the mixer never runs its loop seam and the player loops by itself
                override val durationMs get() = mp.duration.toLong().coerceAtLeast(0)
                override val positionMs get() = mp.currentPosition.toLong()
                override fun start() = mp.start()
                override fun pause() { if (mp.isPlaying) mp.pause() }
                override fun setVolume(v: Float) = mp.setVolume(v, v)
                override fun release() = mp.release()
            }
        }.onFailure { Log.w(TAG, "cannot play $track: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
    }

    private fun log(s: String) { runCatching { Log.i(TAG, s) } }

    internal fun resetForTest() {
        mixer.release()
        if (ctx != null) main.removeCallbacks(ticker)
        ctx = null
        output = BgmOutput { track -> open(track) }
        mixer = BgmMixer { output.open(it) }
        clock = { System.nanoTime() / 1_000_000 }
    }

    /** a mixer on the current [output] — tests set a fake output first */
    internal fun resetMixerForTest() { mixer = BgmMixer { output.open(it) } }
}
