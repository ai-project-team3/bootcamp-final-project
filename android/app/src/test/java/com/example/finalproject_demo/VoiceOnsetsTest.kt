package com.example.finalproject_demo

import com.example.finalproject_demo.ui.VoiceOnsets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C3 소리 흉내 — 소리 덩어리(음절) 세기 (10-05 실기기 · 사용자 결정).
 * 한 칸 = 마이크 1024 표본(16kHz · 64ms). 크기는 실기기에서 잰 값의 범위로 만든다(방 소음 0.02~0.1 · 말소리 0.12~0.6)
 */
class VoiceOnsetsTest {
    private fun count(frames: List<Float>): Int { val v = VoiceOnsets(); return frames.count { v.feed(it) } }
    private fun syllable(loud: Float = 0.3f, len: Int = 3) = List(len) { loud }
    private fun gap(len: Int = 2) = List(len) { 0.03f }

    @Test
    fun choppedSyllablesCountOneEach() {
        // 「삐-뽀-삐-뽀」 — 소리 덩어리 넷, 사이가 끊긴다
        assertEquals(4, count(gap() + (1..4).flatMap { syllable() + gap() }))
    }

    @Test
    fun aLongUnbrokenSoundCountsOnce() {
        // 「아아아아」 — 2초 내내 이어진 소리는 한 번
        assertEquals(1, count(gap() + syllable(0.35f, 31) + gap()))
    }

    @Test
    fun roomNoiseAndShortClicksDoNotCount() {
        // 방 소음(0.02~0.1)과 한 칸짜리 딸깍 소리(키보드 · 물건)는 세지 않는다
        assertEquals(0, count(List(40) { listOf(0.03f, 0.08f, 0.05f, 0.1f)[it % 4] }))
        assertEquals(0, count((1..6).flatMap { listOf(0.5f) + gap(3) }))
    }

    @Test
    fun aDipThatDoesNotGoQuietStaysOneSyllable() {
        // 크기가 0.06~0.12 사이로만 내려갔다 오르면(이어 말하기) 새 덩어리가 아니다
        assertEquals(1, count(gap() + syllable() + listOf(0.09f) + syllable() + gap()))
    }

    private fun maxStreak(at: List<Long>): Int {
        var s = 0; var last = -100_000L; var best = 0
        at.forEach { now -> s = com.example.finalproject_demo.ui.missions.beatStreak(s, last, now); last = now; best = maxOf(best, s) }
        return best
    }

    /** 10-05 실기기 — 방 안 소리가 10초 동안 띄엄띄엄 네 번 잡혀 말하기도 전에 끝났다(그때 시각 그대로) */
    @Test
    fun scatteredRoomSoundsDoNotAddUp() {
        assertEquals(2, maxStreak(listOf(57_790L, 65_990L, 67_588L, 68_288L)))
    }

    @Test
    fun aQuickPeeppoPeeppoFillsUp() {
        // 「삐-뽀-삐-뽀」 — 1초 남짓에 넷
        assertEquals(4, maxStreak(listOf(0L, 280L, 560L, 840L)))
        // 「멍멍 … 멍멍」 — 사이에 0.9초 쉬어도 이어진다
        assertEquals(4, maxStreak(listOf(0L, 300L, 1_200L, 1_500L)))
    }
}
