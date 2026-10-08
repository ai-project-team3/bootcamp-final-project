package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SoloStoryPromptTest {
    private fun verdict() = Server.Verdict(
        reason = "ok", fills = listOf("adult" to "엄마가 응원했어", "companion" to "이야기 속 마법사"),
        nextSlot = "adult", noLongerNeeded = null, storyReady = false, unclear = false,
        unclearOf = null, contradiction = false, s1Reason = false, s2Addition = false, emotion = null,
    )

    @Test fun soloAndUnknownSkipAdultQuestionsIncludingTheLastFallback() = runBlocking {
        for (key in listOf("solo", "unknown")) {
            val s = DemoState().apply {
                partnerKey = key; turn = 4
                listOf("place", "problem", "reaction", "cause", "solution").forEach { slots[it] = "이미 말했어" }
            }
            s.exchangeStoryTurn("reaction", "어땠어?", "재밌었어") {
                Server.TurnResult(verdict(), Server.Line("그랬구나", null, "함께 있는 사람은 뭐라고 했어?"))
            }
            assertFalse(s.slots.containsKey("adult"))
            assertFalse(s.slotBy.containsKey("adult"))
            assertEquals("이야기 속 마법사", s.slots["companion"])
            assertNull(s.storyServerQuestion)
            val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
            assertNotEquals("adult", prompt.slot)
            assertFalse(prompt.text.contains("함께 있는 사람"))
            // A restored/stale question must not escape through the final fallback either.
            assertFalse(s.nextStoryPrompt("함께 있는 사람은 뭐라고 했어?")!!.text.contains("함께 있는 사람"))
        }
    }

    @Test fun aConfirmedPartnerStillGetsTheServersAdultQuestion() = runBlocking {
        val s = DemoState().apply { partnerKey = "dad"; turn = 4 }
        s.exchangeStoryTurn("reaction", "어땠어?", "재밌었어") {
            Server.TurnResult(verdict().copy(fills = emptyList()), Server.Line("그랬구나", null, "아빠는 뭐라고 했어?"))
        }
        assertEquals(StoryPrompt("adult", "아빠는 뭐라고 했어?"), s.nextStoryPrompt(s.storyServerQuestion))
    }
}
