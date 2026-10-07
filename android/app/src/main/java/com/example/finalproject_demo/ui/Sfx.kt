package com.example.finalproject_demo.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 *
 * ## 진동도 같이 (9/25)
 *
 * 소리가 나는 순간이 곧 **손끝에 느낌이 필요한 순간**이다 — 눌렀다 · 놓였다 · 해냈다.
 * 그래서 진동을 따로 흩어 넣지 않고 [Sfx.play] 에 화면(`View`)을 넘기면 같이 울린다.
 * 권한이 필요 없는 `performHapticFeedback` 을 쓴다 — **폰 설정에서 진동을 끈 부모의 선택도 그대로 따른다.**
 *
 * 둘 다 부모 설정에서 끌 수 있다 ([FeelPrefs]). 카페 · 버스 · 잠들기 전 — 아이 앱이면 다 있는 스위치다.
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

/**
 * 부모 설정 — 효과음 · 진동. 폰에 적어 둔다.
 *
 * 앱이 켜질 때 [load] 가 한 번 읽는다 (`MainActivity.onCreate`). [load] 전에는 둘 다 켜진 채 메모리로만 돈다.
 */
object FeelPrefs {
    private var prefs: SharedPreferences? = null

    /** 효과음을 낼까 */
    var soundOn by mutableStateOf(true)
        private set

    /** 진동을 낼까 */
    var buzzOn by mutableStateOf(true)
        private set

    /** 동화책을 읽을 때 배경음악 (#221) */
    var musicOn by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences("feel", Context.MODE_PRIVATE)
        prefs = p
        soundOn = p.getBoolean("sound", true)
        buzzOn = p.getBoolean("buzz", true)
        musicOn = p.getBoolean("music", true)
        com.example.finalproject_demo.net.Bgm.setEnabled(musicOn)
    }

    fun setSound(on: Boolean) {
        soundOn = on
        prefs?.edit()?.putBoolean("sound", on)?.apply()
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        prefs?.edit()?.putBoolean("music", on)?.apply()
        com.example.finalproject_demo.net.Bgm.setEnabled(on)
    }

    fun setBuzz(on: Boolean) {
        buzzOn = on
        prefs?.edit()?.putBoolean("buzz", on)?.apply()
    }

    /** 검사용 — 저장소를 놓고 처음(둘 다 켬)으로 (폰에 적힌 것은 그대로) */
    internal fun unload() {
        prefs = null
        soundOn = true
        buzzOn = true
        musicOn = true
    }
}

object Sfx {
    private const val RATE = 22050
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "sfx").apply { isDaemon = true } }

    /** 같은 소리가 겹쳐 터지지 않게 — 문지르는 동안 초당 수십 번 불린다 */
    private val lastAt = HashMap<Sound, Long>()

    /**
     * 소리를 낸다. [view] 를 넘기면 진동도 같이 울린다.
     *
     * @param minGapMs 이 소리를 다시 내기까지 최소 간격. 연속 입력에서 소리가 뭉개지는 것을 막는다
     * @param view 진동을 낼 화면 (`LocalView.current`). 없으면 소리만
     */
    fun play(kind: Sound, minGapMs: Long = 90L, view: View? = null) {
        // 검사에서는 소리를 내지 않는다 — Robolectric 에는 오디오 장치가 없다
        if (motionFrozen) return
        val now = System.currentTimeMillis()
        synchronized(lastAt) {
            if (now - (lastAt[kind] ?: 0L) < minGapMs) return
            lastAt[kind] = now
        }
        if (view != null && FeelPrefs.buzzOn) buzz(kind)?.let { runCatching { view.performHapticFeedback(it) } }
        if (!FeelPrefs.soundOn) return
        pool.execute {
            runCatching { blast(render(kind)) }   // 기기에 따라 오디오가 막혀 있을 수 있다 — 소리 때문에 앱이 죽으면 안 된다
        }
    }

    /** 소리 한 조각을 계산해서 만든다 */
    /**
     * 소리마다 어떤 떨림인가. 치익은 **뿌리는 동안 계속** 나므로 떨지 않는다 — 계속 울리면 거슬린다.
     */
    internal fun buzz(kind: Sound): Int? = when (kind) {
        Sound.POP -> HapticFeedbackConstants.VIRTUAL_KEY          // 톡 — 가볍게
        Sound.THUD -> HapticFeedbackConstants.CONTEXT_CLICK       // 툭 — 조금 무겁게
        Sound.SPARKLE ->                                          // 반짝 — 해냈다
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        Sound.HISS -> null
    }

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
