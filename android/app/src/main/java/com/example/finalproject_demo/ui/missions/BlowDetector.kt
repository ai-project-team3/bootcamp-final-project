package com.example.finalproject_demo.ui.missions

import kotlin.math.PI
import kotlin.math.abs

/**
 * 「후~」인가 — 불기 · 소리 미션의 판정을 한 곳에 (#258 · `docs/실기기수정_설계_1007.md` §3).
 *
 * 10-07 실기기: 불지 않아도 C1 이 저절로 끝났다. 판정이 마이크 **크기 하나**(문턱 0.22)라
 * 미션 쪽이 열리며 나오는 오또 낭독 · 옆에서 하는 말소리가 그대로 넘었고, 넘는 동안 소품 셋에 함께 쌓였다.
 * 그래서 여기서는:
 *
 * 1. **스피커가 소리를 내는 동안과 끝난 뒤 [TAIL_MS] 는 듣지 않는다** — 오또 목소리(`Voice.playing`) · 효과음(`Sfx.soundingUntil`)
 * 2. **크기 · 영점 교차율 · 고음 비율이 함께 [HOLD_MS] 넘게 이어져야** 「부는 중」 — 「후~」는 마찰 잡음이라
 *    영점 교차가 잦고 2kHz 위가 두껍다. 모음은 둘 다 낮고, 「스 · 후」 같은 마찰음은 [HOLD_MS] 보다 짧다
 *
 * 무슨 말인지는 보지 않는다(받아쓰기 아님) — 프레임마다 숫자 셋만 뽑고 소리는 버린다.
 * 문턱은 출발값이다. 실기기에서 OttoTrace 「blow」 줄(1초에 한 줄)을 보고 고친다.
 */
class BlowDetector(private val rate: Int = 16000) {
    companion object {
        /** 다듬은 크기가 이만큼은 돼야 — 말소리 아래쪽(0.12~)을 뺀다. 지금까지는 0.22 */
        const val LOUD_ON = 0.30f
        /** 프레임 안 영점 교차 비율 — 마찰 잡음은 높고 모음은 낮다 */
        const val ZCR_ON = 0.25f
        /**
         * 2kHz 1차 고역 필터를 지난 에너지 / 전체 — 숨 · 바람 쪽. 설계 출발값은 0.45 였지만 1차 필터로는 흰 잡음도 0.40 이라
         * 넘을 수 없다. 계산해 보니 모음(기본음 150~400Hz · 배음 10개)은 0.03~0.10, 1~4kHz 에서 꺾인 잡음은 0.19~0.31 → 0.18
         */
        const val HIGH_ON = 0.18f
        const val HIGH_HZ = 2000.0
        /** 세 조건이 이만큼 이어져야 「부는 중」 */
        const val HOLD_MS = 300L
        /** 스피커가 멈춘 뒤에도 이만큼은 듣지 않는다 — 방 울림 · 출력 지연 */
        const val TAIL_MS = 500L
        /** 「부는 중」이 이만큼 쌓이면 소품 하나가 날아간다 — 셋이면 2.4초는 실제로 불어야 끝난다 */
        const val PER_PROP_MS = 800L
        /** 크기 눈금 — 평균 진폭을 이 값으로 나눈다(전부터 쓰던 값) */
        private const val FULL_SCALE = 6000f
    }

    /** 한 프레임을 읽은 것 — [level] 은 다듬은 크기(듣지 않는 동안 0) */
    data class Frame(val level: Float, val loud: Float, val zcr: Float, val high: Float, val gated: Boolean, val blowing: Boolean)

    private var level = 0f
    private var since = -1L
    private var gateUntil = Long.MIN_VALUE
    // 1차 고역 필터 — y[n] = a·(y[n-1] + x[n] − x[n-1])
    private val a = run { val rc = 1.0 / (2 * PI * HIGH_HZ); (rc / (rc + 1.0 / rate)).toFloat() }
    private var px = 0f
    private var py = 0f

    /**
     * 프레임 하나를 넣는다.
     * @param speakerBusy 지금 스피커가 오또 목소리 · 효과음을 내는 중인가
     */
    fun feed(buf: ShortArray, n: Int, nowMs: Long, speakerBusy: Boolean): Frame {
        if (speakerBusy) gateUntil = nowMs + TAIL_MS
        var sum = 0L
        var cross = 0
        var all = 0.0
        var hi = 0.0
        var prev = if (n > 0) buf[0].toInt() else 0
        for (i in 0 until n) {
            val v = buf[i].toInt()
            sum += abs(v)
            if ((v >= 0) != (prev >= 0)) cross++
            prev = v
            val x = v.toFloat()
            val y = a * (py + x - px)
            px = x; py = y
            all += x.toDouble() * x
            hi += y.toDouble() * y
        }
        val loud = if (n > 0) (sum.toFloat() / n / FULL_SCALE).coerceIn(0f, 1f) else 0f
        val zcr = if (n > 1) cross.toFloat() / (n - 1) else 0f
        val high = if (all > 0) (hi / all).toFloat().coerceIn(0f, 1f) else 0f
        if (nowMs < gateUntil) {
            // 오또가 말하는 동안 들어온 소리는 아이 소리가 아니다 — 다듬던 값도 버린다
            level = 0f; since = -1L
            return Frame(0f, loud, zcr, high, gated = true, blowing = false)
        }
        // 갑자기 튀지 않게 이어 준다(전과 같은 0.6 / 0.4)
        level = level * 0.6f + loud * 0.4f
        val windy = level >= LOUD_ON && zcr >= ZCR_ON && high >= HIGH_ON
        if (!windy) since = -1L else if (since < 0) since = nowMs
        return Frame(level, loud, zcr, high, gated = false, blowing = windy && nowMs - since >= HOLD_MS)
    }
}
