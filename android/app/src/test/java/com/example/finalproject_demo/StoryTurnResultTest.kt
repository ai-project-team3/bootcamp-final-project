package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.applyStoryVerdict
import com.example.finalproject_demo.net.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTurnResultTest {
    private fun verdict(
        fills: List<Pair<String, String>> = emptyList(),
        next: String? = null,
        noLongerNeeded: String? = null,
        ready: Boolean = false,
    ) = Server.Verdict(
        reason = "test", fills = fills, nextSlot = next, noLongerNeeded = noLongerNeeded,
        storyReady = ready, unclear = false, unclearOf = null, contradiction = false,
        s1Reason = false, s2Addition = false, emotion = null,
    )

    @Test
    fun oneAnswerFillsTwoSlotsAndChoosesTheNextUnfilledSlot() {
        val s = DemoState()
        s.applyStoryVerdict(verdict(listOf("problem" to "배가 흔들렸어", "cause" to "친구가 무서워서"), next = "reaction"), "child")

        assertEquals("배가 흔들렸어", s.slots["problem"])
        assertEquals("친구가 무서워서", s.slots["cause"])
        assertEquals("child", s.slotBy["problem"])
        assertEquals("child", s.slotBy["cause"])
        assertEquals("reaction", s.storyNextSlot)
    }

    @Test
    fun filledOrUnneededSlotIsNeverAskedAgain() {
        val s = DemoState()
        s.slots["problem"] = "이미 말한 사건"
        s.applyStoryVerdict(verdict(next = "problem", noLongerNeeded = "companion"), "card")

        assertNull(s.storyNextSlot)
        assertTrue("companion" in s.storyUnneededSlots)
        s.applyStoryVerdict(verdict(next = "companion"), "child")
        assertNull(s.storyNextSlot)
    }

    @Test
    fun storyReadyStopsAskingWithoutAForcedPageCount() {
        val s = DemoState()
        s.applyStoryVerdict(verdict(next = "solution", ready = true), "child")

        assertTrue(s.storyReady)
        assertNull(s.storyNextSlot)
        assertEquals("story_ready", s.endReason)
    }

    @Test
    fun invalidFillsDoNotPolluteTheClosedSlotListOrPretendTheChildSpoke() {
        val s = DemoState()
        s.applyStoryVerdict(verdict(listOf("other" to "잘못된 칸", "place" to "숲"), next = "other"), "mascot")

        assertFalse("other" in s.slots)
        assertEquals("숲", s.slots["place"])
        assertEquals("mascot", s.slotBy["place"])
        assertNull(s.storyNextSlot)
    }
}
