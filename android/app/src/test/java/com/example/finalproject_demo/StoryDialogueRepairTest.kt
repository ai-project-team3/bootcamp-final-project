package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.TurnHistory
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The story reads 「그거 아니야」 · 「오또는?」 · 「뭐라고?」 as the diary does (#323): spoken answers carry the story so far,
 * and a slot the child says was wrong goes back before the verdict lands.
 */
class StoryDialogueRepairTest {

    private fun verdict(fills: List<Pair<String, String>> = emptyList(), next: String? = null) = Server.Verdict(
        reason = "test", fills = fills, nextSlot = next, noLongerNeeded = null,
        storyReady = false, unclear = false, unclearOf = null, contradiction = false,
        s1Reason = false, s2Addition = false, emotion = null,
    )

    private val sent = mutableListOf<Server.Turn>()

    private suspend fun DemoState.answer(
        slot: String, question: String, said: String, result: Server.TurnResult, spoken: Boolean = true,
    ) = exchangeStoryTurn(slot, question, said, spoken = spoken) { sent += it; result }

    @Test
    fun aSpokenAnswerCarriesTheStorySoFar() = runBlocking {
        val s = DemoState().apply { turn = 1 }
        s.answer("place", "어디로 갈까?", "바다", Server.TurnResult(verdict(listOf("place" to "바다"), "problem"),
            Server.Line("바다로 가자!", null, "바다에서 무슨 일이 생겼어?")))
        s.answer("problem", "바다에서 무슨 일이 생겼어?", "아니야 숲이야", Server.TurnResult(verdict(), null))

        assertEquals("the first answer turns the reading on with an empty story", emptyList<Server.HistoryTurn>(), sent[0].history)
        val first = sent[1].history!!.single()
        assertEquals("place", first.askedSlot)
        assertEquals("어디로 갈까?", first.otto.question)
        assertEquals("바다로 가자!", first.otto.ack)
        assertEquals("the child's words only (rule 5)", "바다", first.child)
        assertEquals(listOf(Server.HistoryFill("place", "바다", null)), first.fills)
    }

    @Test
    fun aCardOrAnAppNoteSendsNoHistory() = runBlocking {
        val s = DemoState().apply { turn = 1 }
        s.answer("place", "어디로 갈까?", "바다", Server.TurnResult(verdict(listOf("place" to "바다")), null), spoken = false)
        assertNull(sent.single().history)
        assertTrue("nothing the child said is kept", s.storyTalk.isEmpty())
    }

    @Test
    fun aRetractedSlotIsEmptiedAndAskedAgain() = runBlocking {
        val s = DemoState().apply { turn = 1 }
        s.answer("place", "어디로 갈까?", "바다", Server.TurnResult(verdict(listOf("place" to "바다"), "problem"), null))
        assertEquals("바다", s.slots["place"])

        s.answer("problem", "바다에서 무슨 일이 생겼어?", "그거 아니야",
            Server.TurnResult(verdict(next = "place"), Server.Line("앗, 내가 잘못 알았구나!", null, "어디로 갔어?"),
                act = "repair", retract = listOf("place")))
        assertNull(s.slots["place"])
        assertNull(s.slotBy["place"])
        assertEquals("the emptied slot is the next question", "place", s.storyNextSlot)
        assertEquals("어디로 갔어?", s.storyServerQuestion)
        assertEquals("repair", s.storyTalk.last().act)
    }

    @Test
    fun aRetractedSlotGoesBackToItsEarlierValue() = runBlocking {
        val s = DemoState().apply { turn = 1 }
        s.answer("place", "어디로 갈까?", "숲", Server.TurnResult(verdict(listOf("place" to "숲")), null))
        s.answer("place", "거기는 어디야?", "바다", Server.TurnResult(verdict(listOf("place" to "바다")), null))
        assertEquals(Server.HistoryFill("place", "바다", "숲"), s.storyTalk.last().fills.single())

        s.answer("problem", "무슨 일이 생겼어?", "바다 아니고 숲",
            Server.TurnResult(verdict(listOf("place" to "숲"), "problem"), null, act = "repair", retract = emptyList()))
        assertEquals("a repair with its value fills like an answer", "숲", s.slots["place"])

        s.answer("problem", "무슨 일이 생겼어?", "그거 아니야",
            Server.TurnResult(verdict(next = "place"), null, act = "repair", retract = listOf("place")))
        assertEquals("back to the value before the last fill", "바다", s.slots["place"])
        assertEquals("child", s.slotBy["place"])
    }

    @Test
    fun anUndoneAnswerLeavesTheStorySoFar() = runBlocking {
        val s = DemoState().apply { turn = 1 }
        val history = TurnHistory(s)
        s.answer("place", "어디로 갈까?", "바다", Server.TurnResult(verdict(listOf("place" to "바다")), null))
        history.before()
        s.answer("problem", "무슨 일이 생겼어?", "길을 잃었어", Server.TurnResult(verdict(listOf("problem" to "길을 잃었어")), null))
        history.done()
        assertEquals(2, s.storyTalk.size)

        assertTrue(history.undo())
        assertEquals("the server must not read back an undone answer", listOf("바다"), s.storyTalk.map { it.child })
    }
}
