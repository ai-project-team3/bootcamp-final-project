package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.StoryPrompt
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.recordTemplateAnswer
import com.example.finalproject_demo.demo.storyEndCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPromptTest {
    @Test
    fun firstTwoProbesStayFixedButFilledSlotsAreSkipped() {
        val s = DemoState()
        assertEquals("place", s.nextStoryPrompt()?.slot)
        s.slots["place"] = "우주"
        s.slots["problem"] = "배가 흔들렸어"
        s.turn = 1
        s.storyNextSlot = "reaction"
        assertEquals("reaction", s.nextStoryPrompt()?.slot)
    }

    @Test
    fun fixedProblemProbeDoesNotReadAnUnrelatedServerQuestion() {
        val s = DemoState()
        s.turn = 1
        s.storyNextSlot = "cause"
        assertEquals("어떤 일이 생겼어?", s.nextStoryPrompt("왜 그랬어?")?.text)
    }

    @Test
    fun serverChoosesTheNextUnfilledQuestion() {
        val s = DemoState()
        s.turn = 3
        s.templateKey = "C"
        s.storyNextSlot = "cause"
        assertEquals(StoryPrompt("cause", "친구는 왜 그랬을까?"), s.nextStoryPrompt("친구는 왜 그랬을까?"))
        s.slots["cause"] = "외로워서"
        assertFalse(s.nextStoryPrompt()?.slot == "cause")
    }

    @Test
    fun localTemplateQuestionIsUsedOnlyWhenServerHasNoNextSlot() {
        val s = DemoState()
        s.turn = 3
        s.templateKey = "C"
        val first = s.nextStoryPrompt()
        assertTrue(first!!.templateOnly)
        s.recordTemplateAnswer(first, "다리에서 멈췄어", "child")
        assertEquals("child", s.slotBy[first.slot])
        assertEquals("다리에서 멈췄어", s.slots[first.slot])
        assertFalse(s.nextStoryPrompt()?.slot == first.slot)
    }

    @Test
    fun readyOrOtherModeStopsStoryQuestions() {
        val s = DemoState()
        s.endReason = "story_ready"
        assertNull(s.nextStoryPrompt())
        s.endReason = null
        s.mode = StoryMode.DIARY
        assertNull(s.nextStoryPrompt())
    }

    @Test
    fun onlyStoryReadyEndsTheStoryEvenAfterLongPlayAndRepeatedMascotChoices() {
        val s = DemoState()
        s.turn = 40
        s.storyStartedAtMs = System.currentTimeMillis() - 60 * 60 * 1000L
        assertNull(s.storyEndCondition())
        s.mascotPicks = 2
        assertNull(s.storyEndCondition())
        s.endReason = "story_ready"
        assertEquals("story_ready", s.storyEndCondition())
    }
}
