package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExchangeTurnTest {
    private fun response() = Server.TurnResult(Server.Verdict(
        "ok", listOf("problem" to "{주인공}이 길을 잃었다"), "reaction", null,
        true, false, null, false, true, false, null,
    ), Server.Line("{주인공}이 길을 잃었구나", null, "{주인공}은 어떻게 했어?"))

    @Test
    fun diaryUsesSharedMaskingWithoutApplyingTheStoryVerdict() = runBlocking {
        val s = DemoState().apply { mode = StoryMode.DIARY; templateKey = "C"; turn = 4 }
        s.slots["place"] = "지호의 숲"
        var sent: Server.Turn? = null
        val result = s.exchangeTurn("diary", "problem", "지호야, 무슨 일이야?", "지호가 길을 잃었어") {
            sent = it; response()
        }
        assertEquals("diary", sent?.mode)
        assertNull("diary has its own page plan, not a story template", sent?.template)
        assertEquals(4, sent?.turn)
        assertEquals("{주인공}의 숲", sent?.slots?.get("place"))
        assertEquals("{주인공}가 길을 잃었어", sent?.utterance)
        assertEquals("지호가 길을 잃었다", result?.verdict?.fills?.single()?.second)
        assertEquals("지호는 어떻게 했어?", result?.line?.question)
        assertEquals(mapOf("place" to "지호의 숲"), s.slots.toMap())
        assertTrue(s.slotBy.isEmpty())
        assertNull(s.endReason)
        assertNull(s.storyNextSlot)
    }

    @Test
    fun sharedStoryExchangeAlsoLeavesStateForTheModeOwnerToApply() = runBlocking {
        val s = DemoState().apply { templateKey = "C" }
        val result = s.exchangeTurn("story", "problem", "무슨 일이야?", "지호가 길을 잃었어") { response() }
        assertNotNull(result)
        assertTrue(s.slots.isEmpty())
        assertNull(s.endReason)
        assertNull(s.storyNextSlot)
    }
}
