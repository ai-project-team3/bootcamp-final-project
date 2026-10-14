package com.example.finalproject_demo

import com.example.finalproject_demo.net.VoiceSplice
import com.example.finalproject_demo.net.VoiceSplice.Head
import com.example.finalproject_demo.net.VoiceSplice.Live
import com.example.finalproject_demo.net.VoiceSplice.SENTENCE_GAP_MS
import com.example.finalproject_demo.net.VoiceSplice.Tail
import com.example.finalproject_demo.net.VoiceSplice.WORD_GAP_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-14 (#461 · #502) — 대사를 나눠 바뀌는 곳만 /tts 로. 가리고 듣기(1~5, 5 = 통째와 구분 못 함):
 * 문장 끝에서 나눔 5.0 · 낱말+조사 머리 + 구운 꼬리 4.0~5.0 · 낱말과 조사를 뗌 1.5. 꼭 나뉘지 않거나 아낄 게 없으면 통째로
 */
class VoiceSpliceTest {

    private val baked = setOf(
        "무슨 일이 있었어?", "뭐 했어?", "더 그린 거야, 새로 그린 거야?",
        "다 만들었어!", "천천히 생각해 봐.", "그럼 오또가 고를게!", "그렇구나!",
    )
    private fun plan(line: String, vararg words: String, heard: Set<String> = emptySet()) =
        VoiceSplice.plan(line, words.toList(), { it in baked }, { it in heard })

    @Test
    fun aSentenceThatNeverChangesIsBakedAndOnlyTheOtherIsBought() {
        assertEquals(listOf(Tail("다 만들었어!", 0), Live("제목은 『용감한 콩이와 반짝 별』이야.", SENTENCE_GAP_MS)),
            plan("다 만들었어! 제목은 『용감한 콩이와 반짝 별』이야."))
        assertEquals(listOf(Tail("그럼 오또가 고를게!", 0), Live("숲속!", SENTENCE_GAP_MS)), plan("그럼 오또가 고를게! 숲속!"))
    }

    @Test
    fun aSentenceVoicedAlreadyThisSessionIsReused() {
        assertEquals("방금 한 질문은 다시 쓴다",
            listOf(Tail("천천히 생각해 봐.", 0), Live("어디로 갈까?", SENTENCE_GAP_MS)),
            plan("천천히 생각해 봐. 어디로 갈까?", heard = setOf("어디로 갈까?")))
        assertEquals("구운 것이 없어도 이미 받은 문장이 있으면 나눈다",
            listOf(Live("촛불이 활활 타고 있어요.", 0), Live("촛불이 다 꺼졌어요.", SENTENCE_GAP_MS)),
            plan("촛불이 활활 타고 있어요. 촛불이 다 꺼졌어요.", heard = setOf("촛불이 활활 타고 있어요.")))
    }

    @Test
    fun theHeardWordKeepsItsParticleAndTheRestIsBaked() {
        assertEquals(listOf(Head("놀이터에서", 0), Tail("무슨 일이 있었어?", WORD_GAP_MS)), plan("놀이터에서 무슨 일이 있었어?", "놀이터"))
        assertEquals(listOf(Head("빨간 공에", 0), Tail("더 그린 거야, 새로 그린 거야?", WORD_GAP_MS)),
            plan("빨간 공에 더 그린 거야, 새로 그린 거야?", "빨간 공"))
        assertEquals("가장 긴 낱말", Head("큰 공원에서", 0), plan("큰 공원에서 뭐 했어?", "큰", "큰 공원")!!.first())
        assertEquals("받아 주기 + 질문",
            listOf(Tail("그렇구나!", 0), Head("동물원에서", SENTENCE_GAP_MS), Tail("뭐 했어?", WORD_GAP_MS)),
            plan("그렇구나! 동물원에서 뭐 했어?", "동물원"))
    }

    @Test
    fun nothingToSaveOrNoExactSplitGoesWhole() {
        assertNull("한 문장 · 구운 것도 낱말도 없다", plan("바닷가에서 뭐가 제일 재밌었어?", "놀이터"))
        assertNull("두 문장 다 새 말 — 나눠도 아낄 게 없다", plan("아빠랑 모래성을 만들었구나. 파도가 왔어?"))
        assertNull("낱말 뒤 꼬리가 안 구워졌다", plan("놀이터에서 뭐가 제일 재밌었어?", "놀이터"))
        assertNull("낱말이 앞에 없다", plan("오늘 놀이터에서 무슨 일이 있었어?", "놀이터"))
        assertNull("낱말과 조사를 떼지 않는다 (1.5점)", plan("놀이터구나!", "놀이터"))
        assertNull("구운 줄 그 자체는 나눌 것이 없다", plan("다 만들었어!"))
    }

    @Test
    fun aTitleOrAQuoteIsNeverCut() {
        assertEquals(listOf("다 만들었어!", "제목은 『그만! 용감한 너』야."), VoiceSplice.sentences("다 만들었어! 제목은 『그만! 용감한 너』야."))
        assertEquals(listOf("친구는 \"그만! 무서워!\" 하고 말했어요."), VoiceSplice.sentences("친구는 \"그만! 무서워!\" 하고 말했어요."))
        assertEquals(listOf("「콩이」 맞아?"), VoiceSplice.sentences("「콩이」 맞아?"))
        assertEquals(listOf("숲속, 바닷가, 구름 위."), VoiceSplice.sentences("숲속, 바닷가, 구름 위."))
    }

    @Test
    fun aHeadReadSlowlyOnItsOwnIsNotUsed() {
        val rate = 1000
        fun head(sec: Double) = VoiceSplice.Pcm(ShortArray((sec * rate).toInt()) { 8000 }, rate)
        assertFalse("놀이터에서 0.95초 = 0.19초/자", VoiceSplice.tooSlow("놀이터에서", head(0.95)))
        assertTrue("할머니 집에서 2.27초 = 0.38초/자 (1점)", VoiceSplice.tooSlow("할머니 집에서", head(2.27)))
    }

    @Test
    fun silenceAtTheEdgesIsCutAndTheGapIsWhatWasAsked() {
        val rate = 1000
        fun clip(silent: Int, voiced: Int) = VoiceSplice.Pcm(ShortArray(silent) + ShortArray(voiced) { 8000 } + ShortArray(silent), rate)
        assertEquals("목소리 100 + 앞뒤 15ms", 100 + 2 * 15, VoiceSplice.trim(clip(200, 100)).size)
        assertEquals(130 + 70 + 130, VoiceSplice.join(listOf(clip(200, 100) to 0, clip(200, 100) to 70))!!.samples.size)
        assertEquals("8ms 겹침", 130 + 130 - 8, VoiceSplice.join(listOf(clip(200, 100) to 0, clip(200, 100) to 0))!!.samples.size)
        assertNull("빠르기가 다르면 잇지 않는다", VoiceSplice.join(listOf(clip(0, 100) to 0, VoiceSplice.Pcm(ShortArray(100) { 8000 }, 2000) to 0)))
    }
}
