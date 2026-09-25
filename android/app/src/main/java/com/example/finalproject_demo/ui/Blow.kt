package com.example.finalproject_demo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.abs

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

/**
 * 마이크로 들어오는 소리의 세기 (0~1). 듣지 않거나 못 들으면 0.
 *
 * @param active 지금 이 화면이 불기를 받는가
 */
@Composable
fun rememberBlowLevel(active: Boolean): Float {
    val ctx = LocalContext.current
    var level by remember { mutableFloatStateOf(0f) }
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
                val buf = ShortArray(frame)
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, frame)
                    if (n <= 0) continue
                    // 평균 크기 하나만 뽑는다. 무슨 소리였는지는 보지 않는다
                    var sum = 0L
                    for (i in 0 until n) sum += abs(buf[i].toInt())
                    val loud = (sum.toFloat() / n / 6000f).coerceIn(0f, 1f)
                    // 갑자기 튀지 않게 이어 준다 — 숫자가 덜덜 떨리면 불꽃도 덜덜 떨린다
                    level = level * 0.6f + loud * 0.4f
                }
            } catch (_: Throwable) {
                // 마이크를 못 열면 조용히 포기한다 — 손으로 하면 된다
            } finally {
                runCatching { rec?.stop() }
                runCatching { rec?.release() }
                level = 0f
            }
        }
        onDispose {
            running = false
            runCatching { t.interrupt() }
        }
    }

    return if (active) level else 0f
}
