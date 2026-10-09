package com.example.finalproject_demo.ui.shell

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/** Screen-owned, offline players. They never share or interrupt the story narration player. */
internal class OpeningAudio(private val context: Context) {
    private var disposed = false
    private var active = true
    private inner class Clip(path: String, volume: Float) {
        private val player = MediaPlayer()
        private var ready = false
        private var wanted = false
        private var failed = false
        var ended: () -> Unit = {}
        init {
            try {
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                player.setVolume(volume, volume)
                player.setOnPreparedListener { ready = true; startIfReady() }
                player.setOnCompletionListener { wanted = false; ended() }
                player.setOnErrorListener { _, _, _ -> failed = true; wanted = false; ended(); true }
                context.assets.openFd("opening/$path").use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                player.prepareAsync()
            } catch (e: Exception) { failed = true; Log.w("OttoOpening", "Bundled audio unavailable: $path", e) }
        }
        private fun startIfReady() {
            if (disposed || !active || !ready || !wanted) return
            runCatching { player.start() }.onFailure { failed = true; wanted = false; ended() }
        }
        fun play() {
            if (disposed) return
            if (failed) { ended(); return }
            wanted = true
            if (ready) runCatching { player.seekTo(0) }
            startIfReady()
        }
        fun pause() { if (ready) runCatching { if (player.isPlaying) player.pause() } }
        fun resume() = startIfReady()
        fun stop() { wanted = false; pause() }
        fun release() { wanted = false; runCatching { player.release() } }
    }
    private val intro = Clip("otto-intro.mp3", .8f)
    private val hop = Clip("hop-jelly.wav", .45f)
    fun introduce(onEnd: () -> Unit) { intro.ended = { if (!disposed) onEnd() }; intro.play() }
    fun hop() = hop.play()
    fun setActive(value: Boolean) { active = value; if (value) { intro.resume(); hop.resume() } else { intro.pause(); hop.pause() } }
    fun silence() { intro.stop(); hop.stop() }
    fun close() { disposed = true; intro.release(); hop.release() }
}
