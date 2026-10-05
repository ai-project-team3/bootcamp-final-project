package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.reported
import com.example.finalproject_demo.demo.storyReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 10-05 device round with the conversation trace — the fixes that need no device to check */
class StoryRoundFixTest {

    @Test
    fun anExpandThatOnlyRepeatsTheAckIsDropped() {
        assertEquals("또치를 아저씨가 동물원으로 데리고 돌아갔구나.",
            storyReaction("또치를 아저씨가 동물원으로 데리고 돌아갔구나.", "또치와 아저씨가 동물원으로 돌아갔어."))
        assertEquals("맛있는 간식을 주는 거야.",
            storyReaction("맛있는 간식을 주는 거야.", "바늘괴물에게 맛있는 간식을 주는구나."))
        // something new is kept
        assertEquals("풍선마을로 가는구나! 풍선마을에는 알록달록한 풍선이 떠 있어.",
            storyReaction("풍선마을로 가는구나!", "풍선마을에는 알록달록한 풍선이 떠 있어."))
        assertEquals("좋아!", storyReaction("좋아!", null))
    }

    @Test
    fun reportedSpeechKeepsTheEnding() {
        assertEquals("심심했대", reported("심심했어"))
        assertEquals("터뜨리는 게 재밌어서래", reported("터뜨리는 게 재밌어서"))   // was 「재밌어서대」
        assertEquals("친구가 없어서래", reported("친구가 없어서."))
    }

    @Test
    fun soundAfterRecordingAndCompanionAreNotAsked() {
        val s = DemoState().apply {
            mode = StoryMode.STORY; turn = 5
            slots["place"] = "풍선마을"; slots["problem"] = "바늘괴물이 풍선을 터뜨렸어"
            slots["sound"] = "친구의 소리를 직접 만들었어요"
            storySoundAttempted = true
        }
        s.storyNextSlot = "sound"; s.storyClarificationSlot = "sound"
        assertNotEquals("sound", s.nextStoryPrompt("사육사 아저씨는 어떤 소리를 낼까?")?.slot)
        s.storyNextSlot = "companion"; s.storyClarificationSlot = null
        assertNotEquals("companion", s.nextStoryPrompt("또치와 누가 함께할까?")?.slot)
    }
}
