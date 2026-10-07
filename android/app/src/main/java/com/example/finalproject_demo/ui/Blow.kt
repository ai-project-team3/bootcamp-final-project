package com.example.finalproject_demo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.finalproject_demo.net.Trace
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.ui.missions.BlowDetector
import kotlin.concurrent.thread

/*
 * 후~ 불기 — 마이크를 **음량으로만** 쓴다 (미션 구상 C1 · 9/23).
 *
 * 「말로 짓는」 앱에서 **처음으로 목소리가 미션이 된다.** 발달 목표는 호흡 조절과 발성이다.
 *
 * ⚠️ 받아쓰기가 아니다. 무슨 말을 했는지는 보지 않고 **얼마나 센가**만 본다.
 *    읽은 소리는 그 자리에서 크기 한 숫자로 바뀌고 버려진다 — 저장하지도, 폰 밖으로 내보내지도 않는다.
 *
 * ⚠️ 불기는 **덤이지 대신이 아니다.** 손으로 문질러도 똑같이 된다 (실패 없는 설계 · §1-3).
 *    마이크가 막혀 있거나 권한을 안 주면 조용히 0을 돌려주고 아무 일도 없다.
 */

/** 마이크로 읽은 것 — [level] 소리 세기(0~1, 스피커가 소리를 내는 동안 0) · [blowing] 「후~」가 이어지는 중 ([BlowDetector]) */
class BlowReading {
    var level by mutableFloatStateOf(0f)
        internal set
    var blowing by mutableStateOf(false)
        internal set
}

/** 스피커가 오또 목소리 · 효과음을 내는 중인가 — 그동안 들어온 소리는 아이 소리가 아니다 (#258) */
internal fun speakerBusy(now: Long = System.currentTimeMillis()) = Voice.playing.value || now < Sfx.soundingUntil

/** 소리 세기만 (C3 소리 흉내) — [rememberBlow] 의 [BlowReading.level] */
@Composable
fun rememberBlowLevel(active: Boolean, beats: androidx.compose.runtime.MutableIntState? = null): Float =
    rememberBlow(active, beats).level

/**
 * 마이크로 들어오는 소리 (#258 부터 판정은 [BlowDetector] 한 곳). 듣지 않거나 못 들으면 0 · false.
 *
 * @param active 지금 이 화면이 불기를 받는가
 * @param beats 주면 소리 덩어리(음절)가 하나 시작될 때마다 1 씩 올린다 — C3 소리 흉내가 「삐-뽀-삐-뽀」를 센다([VoiceOnsets])
 */
@Composable
fun rememberBlow(active: Boolean, beats: androidx.compose.runtime.MutableIntState? = null): BlowReading {
    val ctx = LocalContext.current
    val reading = remember { BlowReading() }
    val idle = remember { BlowReading() }
    var granted by remember {
        mutableFloatStateOf(
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) 1f else 0f,
        )
    }
    // ⚠️ **여기서는 권한을 묻지 않는다 (09-25).** 전에는 미션 화면에 들어오는 것만으로
    //    시스템 권한 창을 띄웠다 — **마이크 고지(`MicNoticeSheet`) 없이.** 🎤 버튼 경로는
    //    고지 → 권한 순서를 지키는데 이 경로만 건너뛰었다. 고지 없는 마이크 요청은
    //    Play 가족 정책의 「권한 요청 전에 이유를 알린다」에 걸린다.
    //
    //    그래서 **이미 받은 권한만 쓴다.** 🎤 에서 고지를 보고 허락했으면 불기가 되고,
    //    아니면 조용히 0 — 불기는 **덤이지 대신이 아니다**(위 머리말). 손으로 문질러도 똑같이 끝난다.
    //    불기 때문에 고지 화면을 한 번 더 띄우는 것은 미션 한가운데서 아이에게 어른용 글을
    //    들이미는 일이라 하지 않는다.

    DisposableEffect(active, granted) {
        if (!active || granted == 0f || motionFrozen) return@DisposableEffect onDispose { }
        var running = true
        val t = thread(isDaemon = true, name = "blow") {
            var rec: AudioRecord? = null
            val effects = mutableListOf<AudioEffect>()
            try {
                val rate = 16000
                val frame = 1024
                val min = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, frame * 8),
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) return@thread
                // 스피커에서 나온 오또 목소리를 마이크에서 빼 준다 — 있는 기기만. 없으면 [speakerBusy] 문이 막는다 (#258)
                val session = rec.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { it.enabled = true; effects += it }
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { it.enabled = true; effects += it }
                val buf = ShortArray(frame)
                val onsets = VoiceOnsets()
                val detector = BlowDetector(rate)
                var traceAt = 0L
                var blowFrames = 0
                var frames = 0
                Trace.line("blow", "mic open · echo canceller ${effects.any { it is AcousticEchoCanceler }} · noise suppressor ${effects.any { it is NoiseSuppressor }}")
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, frame)
                    if (n <= 0) continue
                    // 숫자 몇 개만 뽑는다. 무슨 소리였는지는 보지 않는다
                    val now = System.currentTimeMillis()
                    val f = detector.feed(buf, n, now, speakerBusy(now))
                    reading.level = f.level
                    reading.blowing = f.blowing
                    // 음절 세기는 다듬지 않은 크기로 — 다듬으면 음절 사이 끊김이 메워진다. 스피커가 울리는 동안은 0
                    if (beats != null && onsets.feed(if (f.gated) 0f else f.loud)) beats.intValue += 1
                    frames++; if (f.blowing) blowFrames++
                    // 실기기에서 문턱을 고치는 줄 — 1초에 한 줄 (설계 §3-3)
                    if (now - traceAt >= 1000L) {
                        Trace.line("blow", "level %.2f · zcr %.2f · high %.2f · gated %b · blowing %d/%d".format(f.level, f.zcr, f.high, f.gated, blowFrames, frames))
                        traceAt = now; blowFrames = 0; frames = 0
                    }
                }
            } catch (_: Throwable) {
                // 마이크를 못 열면 조용히 포기한다 — 손으로 하면 된다
            } finally {
                effects.forEach { runCatching { it.release() } }
                runCatching { rec?.stop() }
                runCatching { rec?.release() }
                reading.level = 0f
                reading.blowing = false
            }
        }
        onDispose {
            running = false
            runCatching { t.interrupt() }
        }
    }

    // 듣지 않는 화면이면 남은 값을 보지 않는다
    return if (active) reading else idle
}

/**
 * 소리 덩어리(음절) 세기 — 「삐-뽀-삐-뽀」는 넷, 길게 이어지는 「아아아아」는 하나 (C3 소리 흉내 · 10-05 사용자 결정).
 *
 * 무슨 말인지는 보지 않는다(받아쓰기 아님) — **끊겼다가 다시 커지는 횟수**만 센다. 그래서 발음이 서툰 3~4세도
 * 리듬만 맞으면 된다. 한 덩어리로 치려면:
 * - 크기가 [on] 위로 **[hold] 칸(한 칸 64ms) 이상 이어져야** 한다 — 키보드 · 물건 소리처럼 짧게 튀는 소리는 세지 않는다
 * - 그 전에 [off] 밑으로 한 번은 내려갔어야 한다 — 이어지는 소리는 처음 한 번만
 * 띄엄띄엄 들어오는 방 안 소리는 C3 화면이 덩어리 사이 간격으로 한 번 더 거른다(`beatStreak`)
 *
 * 값은 실기기(SM-G977N)에서 잰 것: 방 소음 0.02~0.1, 말소리 꼭대기 0.12~0.6
 */
class VoiceOnsets(private val on: Float = 0.12f, private val off: Float = 0.06f, private val hold: Int = 2) {
    private var quiet = true
    private var run = 0

    /** 이번 칸의 크기를 넣는다 — 새 덩어리가 시작됐으면 true */
    fun feed(loud: Float): Boolean {
        if (loud < off) { quiet = true; run = 0; return false }
        if (loud < on) { run = 0; return false }
        run++
        if (quiet && run >= hold) { quiet = false; return true }
        return false
    }
}
