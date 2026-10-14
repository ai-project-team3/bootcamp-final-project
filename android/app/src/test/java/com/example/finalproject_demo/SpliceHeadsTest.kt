package com.example.finalproject_demo

import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.net.VoiceSplice
import com.example.finalproject_demo.net.VoiceSplice.Head
import com.example.finalproject_demo.net.VoiceSplice.Live
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #502 — 동화 · 협업 대사도 들은 낱말(+조사)만 /tts 로, 나머지는 구운 꼬리로. 앱이 실제로 만드는 틀 그대로 써서
 * 꼬리가 하나라도 안 구워졌으면(`eval/bake_tails.py`) 여기서 깨진다
 */
class SpliceHeadsTest {

    private fun baked(t: String) = File("src/main/assets/voice_tail/${Voice.bakedKey(t)}.mp3").isFile ||
        File("src/main/assets/voice/${Voice.bakedKey(t)}.mp3").isFile

    private fun assertSpliced(line: String, words: List<String>) {
        val plan = VoiceSplice.plan(line, words, ::baked)
        assertTrue("「$line」 — 나뉘지 않는다: $plan", plan != null && plan.none { it is Live } && plan.any { it is Head })
    }

    @Test
    fun storyLinesStartingWithAHeardNameSplice() {
        val words = listOf("민준이", "바닷속", "거북이", "문어", "콩이", "엄마")
        listOf(
            "콩이가 뭘 원했을까?", "문어는 기분이 어땠을까?", "문어를 보면 어떤 소리가 떠올라?", "콩이랑 뭐 하고 놀까?",
            "민준이가 놀랐을까?", "민준이가 소리쳤을까, 조용히 있었을까?", "거북이가 괜찮았을까?",
            "엄마랑 함께구나! 좋아!", "콩이의 소리를 직접 만들어 볼까?", "문어는 어떻게 생겼을까? 크레용으로 그려 줄래?",
        ).forEach { assertSpliced(it, words) }
    }

    @Test
    fun coopFollowUpsStartingWithAHeardPlaceOrThingSplice() {
        val words = listOf("소방서", "강아지", "아빠")
        listOf(
            "소방서에서 뭐 봤어?", "소방서에서 무슨 일이 생겼을까?", "강아지가 왜 그랬을까?", "강아지가 뭐 때문에 그랬을까?",
            "강아지는 어떻게 됐어?", "아빠가 뭐라고 했어?", "소방서에서 만난 사람 있어?",
        ).forEach { assertSpliced(it, words) }
    }

    /** 보기 순서는 일부러 섞는다(근거 #10 · #27) — 순서는 그대로 두고, 뒤 질문 문장만 구운 소리로 */
    @Test
    fun theQuestionAfterShuffledChoicesIsBaked() {
        listOf("바다, 구름 위, 숲. 어디부터 갈까?", "놀이터, 할머니 집, 어린이집. 오늘은 어디 있었어?", "울었어, 웃었어, 화났어. 어떤 마음이었을까?")
            .forEach { line ->
                val plan = VoiceSplice.plan(line, emptyList(), ::baked)
                assertTrue("「$line」 — 질문 문장이 안 구워졌다: $plan", plan != null && plan.last() is VoiceSplice.Tail && plan.first() is Live)
            }
    }
}
