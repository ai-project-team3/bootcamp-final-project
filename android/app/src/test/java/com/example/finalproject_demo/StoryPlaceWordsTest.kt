package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.applyStoryVerdict
import com.example.finalproject_demo.demo.storyPlaceFromSentence
import com.example.finalproject_demo.net.Server
import org.junit.Assert.assertEquals
import org.junit.Test

/** 10-09 device — the place 「숲속에 갔어」 went into page 1 as 「‘숲속에 갔어’에서 친구는 숲길을 걸어요.」 */
class StoryPlaceWordsTest {

    @Test
    fun thePlaceIsTakenFromASentence() {
        assertEquals("숲속", storyPlaceFromSentence("숲속에 갔어"))
        assertEquals("얼음 산", storyPlaceFromSentence("얼음 산으로 갈래"))
        assertEquals("동물원", storyPlaceFromSentence("엄마랑 동물원에 갔어"))
        assertEquals("바닷가", storyPlaceFromSentence("오늘 바닷가에서 놀았어요"))
        assertEquals("놀이터", storyPlaceFromSentence("놀이터로 가자"))
        // the whole place phrase (#390 review)
        assertEquals("바다 속 궁전", storyPlaceFromSentence("바다 속 궁전으로 갔어"))
        assertEquals("구름 위 성", storyPlaceFromSentence("구름 위 성에 갔어"))
        assertEquals("작은 숲", storyPlaceFromSentence("작은 숲에 갔어"))
        assertEquals("높은 산", storyPlaceFromSentence("높은 산으로 갈래"))
        assertEquals("하와이 해변", storyPlaceFromSentence("하와이 해변에 갔어"))
        // the bare verb 가 · 와 (#390 review)
        assertEquals("동물원", storyPlaceFromSentence("동물원으로 가"))
        assertEquals("놀이터", storyPlaceFromSentence("놀이터로 와"))
        assertEquals("동물원", storyPlaceFromSentence("나 동물원에 갔어"))
        assertEquals("동물원", storyPlaceFromSentence("엄마가 동물원에 갔어"))
        assertEquals("숲", storyPlaceFromSentence("곰이 숲에 갔어"))
    }

    @Test
    fun aPlaceNameStaysAsItIs() {
        listOf("우리 집", "놀이공원", "바다 속 궁전", "숲", "구름 위 성").forEach { assertEquals(it, storyPlaceFromSentence(it)) }
        assertEquals("not a place sentence — kept", "공룡이 나타났어", storyPlaceFromSentence("공룡이 나타났어"))
    }

    @Test
    fun thePlaceSlotKeepsThePlaceOnly() {
        val s = DemoState()
        s.applyStoryVerdict(Server.Verdict(
            reason = "test", fills = listOf("place" to "숲속에 갔어", "problem" to "숲속에 갔어"), nextSlot = null,
            noLongerNeeded = null, storyReady = false, unclear = false, unclearOf = null, contradiction = false,
            s1Reason = false, s2Addition = false, emotion = null,
        ), "child")
        assertEquals("숲속", s.slots["place"])
        assertEquals("other slots keep the sentence", "숲속에 갔어", s.slots["problem"])
    }
}
