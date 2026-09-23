package com.example.finalproject_demo.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * 효과음 — **파일 없이 계산해서** 낸다 (9/23).
 *
 * 미션 구상 문서가 「모든 미션에 공통으로 넣을 것」에 *"입력마다 효과음 하나"* 를 적어 두었는데,
 * 같은 문서 §6이 소리는 **미결**이라고 남겨 두었다. 녹음한 소리가 한 개도 없다.
 *
 * 그래서 불·물과 같은 길을 간다 — **소리도 그려 넣는 것이 아니라 만들어 내는 것**이다.
 * 사인파와 잡음을 몇 줄로 섞으면 톡 · 치익 · 반짝 정도는 난다. 녹음이 생기면 이걸 갈아 끼우면 된다.
 *
 * ⚠️ 아이용이라 **짧고 작게.** 0.3초를 넘기지 않고, 음량도 절반 아래로 둔다.
 *    귀에 거슬리는 소리는 아이가 화면을 안 만지게 만든다.
 */

/** 낼 수 있는 소리 */
enum class Sound {
    /** 톡 — 무언가를 눌렀다 */
    POP,

    /** 치익 — 물이 불에 닿았다 */
    HISS,

    /** 반짝 — 해냈다 */
    SPARKLE,

    /** 툭 — 물건이 놓였다 */
    THUD,
}

object Sfx {
    private const val RATE = 22050
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "sfx").apply { isDaemon = true } }

    /** 같은 소리가 겹쳐 터지지 않게 — 문지르는 동안 초당 수십 번 불린다 */
    private val lastAt = HashMap<Sound, Long>()

    /**
     * 소리를 낸다.
     *
     * @param minGapMs 이 소리를 다시 내기까지 최소 간격. 연속 입력에서 소리가 뭉개지는 것을 막는다
     */
    fun play(kind: Sound, minGapMs: Long = 90L) {
        // 검사에서는 소리를 내지 않는다 — Robolectric 에는 오디오 장치가 없다
        if (motionFrozen) return
        val now = System.currentTimeMillis()
        synchronized(lastAt) {
            if (now - (lastAt[kind] ?: 0L) < minGapMs) return
            lastAt[kind] = now
        }
        pool.execute {
            runCatching { blast(render(kind)) }   // 기기에 따라 오디오가 막혀 있을 수 있다 — 소리 때문에 앱이 죽으면 안 된다
        }
    }

    /** 소리 한 조각을 계산해서 만든다 */
    private fun render(kind: Sound): ShortArray = when (kind) {
        // 톡 — 높은 음에서 살짝 내려오며 빠르게 사그라진다
        Sound.POP -> tone(0.09f) { t, n ->
            val f = 880f - 300f * (t / n)
            (sin(2.0 * PI * f * t / RATE) * exp(-9.0 * t / n)).toFloat() * 0.35f
        }
        // 치익 — 잡음이 잦아든다. 물이 불에 닿는 소리는 음이 아니라 바람이다
        Sound.HISS -> tone(0.22f) { t, n ->
            val a = exp(-4.5 * t / n).toFloat()
            (Random.nextFloat() * 2f - 1f) * a * 0.22f
        }
        // 반짝 — 세 음이 차례로 올라간다
        Sound.SPARKLE -> tone(0.28f) { t, n ->
            val step = (3f * t / n).toInt().coerceIn(0, 2)
            val f = floatArrayOf(784f, 988f, 1319f)[step]   // 솔 · 시 · 미
            val local = (t - step * n / 3f) / (n / 3f)
            (sin(2.0 * PI * f * t / RATE) * exp(-5.0 * local)).toFloat() * 0.28f
        }
        // 툭 — 낮은 음 한 번
        Sound.THUD -> tone(0.12f) { t, n ->
            val f = 210f - 60f * (t / n)
            (sin(2.0 * PI * f * t / RATE) * exp(-11.0 * t / n)).toFloat() * 0.40f
        }
    }

    /** [seconds] 길이의 소리를 [sample] 로 채운다. `t` 는 샘플 번호, `n` 은 전체 길이 */
    private inline fun tone(seconds: Float, sample: (t: Float, n: Float) -> Float): ShortArray {
        val n = (RATE * seconds).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            // 끝을 부드럽게 — 뚝 끊으면 「딱」 하는 잡음이 붙는다
            val fade = if (i > n - 220) (n - i) / 220f else 1f
            out[i] = (sample(i.toFloat(), n.toFloat()) * fade * Short.MAX_VALUE).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun blast(pcm: ShortArray) {
        val bytes = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { runCatching { t?.release() } }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            },
        )
        track.play()
    }
}
