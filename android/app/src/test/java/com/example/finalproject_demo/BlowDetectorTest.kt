package com.example.finalproject_demo

import com.example.finalproject_demo.ui.missions.BlowDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * #258 (10-07 실기기) — 불지 않아도 불기가 끝났다. 오또 낭독 · 말소리는 「부는 중」이 아니고, 「후~」만 그렇다.
 *
 * 소리는 여기서 만든다: 모음(기본음 + 배음) · 「후~」(넓은 잡음) · 말소리(짧은 마찰음과 모음이 번갈아) ·
 * 마이크에 대고 분 숨(낮은 바람 울림 · 꽉 찬 크기). 크기는 10-07 실기기(S10) 기록에 맞췄다 — 30cm 말소리 0.10~0.21 ·
 * 마이크에 대고 분 「후~」 0.85~1.00 · 영점 교차율 0.04~0.19 · 고음 비율 0.05~0.16.
 */
class BlowDetectorTest {
    private val rate = 16000
    private val frame = 1024
    private val frameMs = frame * 1000L / rate          // 64

    /** 기본 크기는 다듬은 크기 ≈ 0.25 — 30cm 말소리(0.10~0.21)보다 조금 크게 */
    private fun vowel(ms: Long, f0: Double = 220.0, amp: Double = 3600.0): ShortArray {
        val n = (rate * ms / 1000).toInt()
        return ShortArray(n) { i ->
            var v = 0.0
            for (k in 1..10) v += sin(2 * PI * f0 * k * i / rate) / k
            (v * amp / 2.0).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun breath(ms: Long, amp: Int = 9000, seed: Int = 7): ShortArray {
        val rnd = Random(seed)
        return ShortArray((rate * ms / 1000).toInt()) { rnd.nextInt(-amp, amp + 1).toShort() }
    }

    /** 프레임마다 판정 — 「부는 중」이었던 시간(ms) */
    private fun blowingMs(d: BlowDetector, pcm: ShortArray, start: Long = 0L, busy: (Long) -> Boolean = { false }): Long {
        var t = start
        var on = 0L
        var i = 0
        while (i + frame <= pcm.size) {
            val f = d.feed(pcm.copyOfRange(i, i + frame), frame, t, busy(t))
            if (f.blowing) on += frameMs
            t += frameMs; i += frame
        }
        return on
    }

    @Test
    fun aLongBreathIsBlowing() {
        val d = BlowDetector(rate)
        val on = blowingMs(d, breath(2000))
        assertTrue("2초 「후~」 중 부는 중이 ${on}ms", on >= 1400)
    }

    /** 숨은 흰 잡음보다 낮은 쪽이 두껍다 — 1.5kHz 에서 꺾인 잡음 */
    private fun softBreath(ms: Long, amp: Double = 30000.0, corner: Double = 1500.0): ShortArray {
        val rnd = Random(3)
        val a = (1.0 / rate) / (1.0 / (2 * PI * corner) + 1.0 / rate)
        var y = 0.0
        return ShortArray((rate * ms / 1000).toInt()) { y += a * (rnd.nextDouble(-1.0, 1.0) - y); (y * amp).toInt().coerceIn(-32768, 32767).toShort() }
    }

    @Test
    fun aSofterBreathIsBlowingToo() {
        val on = blowingMs(BlowDetector(rate), softBreath(2000))
        assertTrue("2초 숨 중 부는 중이 ${on}ms", on >= 1400)
    }

    /** 마이크에 대고 분 숨 — 300Hz 아래 바람 울림, 크기가 꽉 찬다 */
    private fun rumble(ms: Long): ShortArray {
        val rnd = Random(5)
        val a = (1.0 / rate) / (1.0 / (2 * PI * 300.0) + 1.0 / rate)
        var y = 0.0
        return ShortArray((rate * ms / 1000).toInt()) { y += a * (rnd.nextDouble(-1.0, 1.0) - y); (y * 120000).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun join(parts: List<ShortArray>) =
        ShortArray(parts.sumOf { it.size }).also { out -> var o = 0; parts.forEach { it.copyInto(out, o); o += it.size } }

    @Test
    fun aBreathIntoTheMicIsBlowing() {
        val d = BlowDetector(rate)
        val on = blowingMs(d, rumble(2500))
        assertTrue("2.5초 마이크에 분 숨 중 부는 중이 ${on}ms", on >= 1800)
    }

    @Test
    fun loudSpeechCloseToTheMicIsNotBlowing() {
        // 10cm 앞에서 크게 말해도 음절(≈200ms) 사이가 끊긴다 — [HOLD_MS] 까지 이어지지 않는다
        val parts = (0 until 12).flatMap { listOf(vowel(200, amp = 12000.0), ShortArray(rate * 150 / 1000)) }
        assertEquals(0L, blowingMs(BlowDetector(rate), join(parts)))
    }

    @Test
    fun aLoudVowelIsNotBlowing() {
        // 오또 낭독 · 아이 말소리의 모음 — 크기는 문턱을 넘어도 부는 것이 아니다
        assertEquals(0L, blowingMs(BlowDetector(rate), vowel(3000)))
        assertEquals(0L, blowingMs(BlowDetector(rate), vowel(3000, f0 = 300.0)))
    }

    @Test
    fun speechWithShortFricativesIsNotBlowing() {
        // 「후~ 불어 볼게」를 말로 — 마찰음(≈130ms)과 모음(≈200ms)이 번갈아 3초
        val parts = (0 until 10).flatMap { listOf(breath(130, amp = 3000, seed = it), vowel(200)) }
        val pcm = join(parts)
        assertEquals(0L, blowingMs(BlowDetector(rate), pcm))
    }

    @Test
    fun aQuietBreathIsNotBlowing() {
        assertEquals("방 소음 크기의 잡음", 0L, blowingMs(BlowDetector(rate), breath(2000, amp = 1500)))
    }

    @Test
    fun notWhileTheSpeakerPlaysAndHalfASecondAfter() {
        val d = BlowDetector(rate)
        // 처음 1초(프레임 16개)는 오또가 말하는 중 — 그동안의 「후~」는 버린다. 마지막으로 바빴던 프레임은 960ms
        val busyUntil = 1024L
        val lastBusy = busyUntil - frameMs
        var firstOn = -1L
        var t = 0L
        val pcm = breath(3000)
        var i = 0
        while (i + frame <= pcm.size) {
            val f = d.feed(pcm.copyOfRange(i, i + frame), frame, t, t < busyUntil)
            if (t < lastBusy + BlowDetector.TAIL_MS) assertTrue("스피커 · 꼬리 동안은 듣지 않는다 (t=$t)", f.gated && !f.blowing && f.level == 0f)
            if (f.blowing && firstOn < 0) firstOn = t
            t += frameMs; i += frame
        }
        assertTrue("꼬리가 끝나고 ${BlowDetector.HOLD_MS}ms 쯤 뒤부터 쌓인다 (처음 $firstOn)",
            firstOn >= lastBusy + BlowDetector.TAIL_MS + BlowDetector.HOLD_MS &&
                firstOn <= lastBusy + BlowDetector.TAIL_MS + BlowDetector.HOLD_MS + 3 * frameMs)
    }

    /**
     * C3 소리 흉내 — 오또가 「삐-뽀-삐-뽀」를 읽는 동안 들어온 음절은 세지 않는다(#293 리뷰 ③). 스피커가 멈추고
     * 꼬리가 지나면 아이의 같은 소리는 센다
     */
    @Test
    fun ottosOwnSyllablesAreNotCounted() {
        val onsets = com.example.finalproject_demo.ui.VoiceOnsets()
        val d = BlowDetector(rate)
        fun beats(pcm: ShortArray, start: Long, busy: Boolean): Pair<Int, Long> {
            var t = start; var n = 0; var i = 0
            while (i + frame <= pcm.size) {
                if (onsets.feed(d.feed(pcm.copyOfRange(i, i + frame), frame, t, busy).beatLoud)) n++
                t += frameMs; i += frame
            }
            return n to t
        }
        val syllables = vowel(300) + ShortArray(rate * 300 / 1000) + vowel(300) + ShortArray(rate * 300 / 1000) +
            vowel(300) + ShortArray(rate * 300 / 1000) + vowel(300) + ShortArray(rate * 300 / 1000)
        val (otto, end) = beats(syllables, 0L, busy = true)
        assertEquals("오또 목소리는 0", 0, otto)
        val (child, _) = beats(syllables, end + BlowDetector.TAIL_MS + frameMs, busy = false)
        assertTrue("아이 소리는 센다 ($child)", child >= 3)
    }

    @Test
    fun aShortPuffIsNotEnough() {
        assertFalse(blowingMs(BlowDetector(rate), breath(250)) > 0)
    }
}
