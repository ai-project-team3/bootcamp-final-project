package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.applyStoryVerdict
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
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

    @Test
    fun oneLiveTurnMasksTheRequestAndRestoresTheVerdictAndMascotLine() = runBlocking {
        val s = DemoState()
        s.slots["place"] = "지호의 숲"
        var sent: Server.Turn? = null
        val result = s.exchangeStoryTurn("problem", "지호야, 무슨 일이야?", "지호가 길을 잃었어") {
            sent = it
            Server.TurnResult(
                verdict(fills = listOf("problem" to "{주인공}이 길을 잃었다"), next = "reaction"),
                Server.Line("{주인공}이 길을 잃었구나", null, "그다음에는 어떻게 했어?"),
            )
        }

        assertEquals("{주인공}의 숲", sent!!.slots["place"])
        assertEquals("{주인공}야, 무슨 일이야?", sent!!.question)
        assertEquals("{주인공}가 길을 잃었어", sent!!.utterance)
        assertEquals("지호가 길을 잃었다", s.slots["problem"])
        assertEquals("child", s.slotBy["problem"])
        assertEquals("reaction", s.storyNextSlot)
        assertEquals("지호가 길을 잃었구나", result?.line?.ack)
    }

    @Test
    fun failedLiveTurnDoesNotInventAnAnswerOrAdvanceTheQuestion() = runBlocking {
        val s = DemoState()
        val result = s.exchangeStoryTurn("problem", "무슨 일이야?", "몰라") { null }
        assertNull(result)
        assertNull(s.storyNextSlot)
        assertTrue(s.slots.isEmpty())
    }
}
