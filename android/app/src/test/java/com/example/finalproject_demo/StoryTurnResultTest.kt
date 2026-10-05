package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.applyStoryVerdict
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.demo.nextStoryPrompt
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
    fun unclearFilledSlotKeepsTheServersClarifyingQuestionUntilResolved() {
        val s = DemoState()
        s.turn = 3
        s.slots["newcomer"] = "문어"
        s.applyStoryVerdict(verdict(next = "newcomer").copy(unclear = true, unclearOf = "모습"), "child")

        assertEquals("newcomer", s.nextStoryPrompt("문어는 어떤 모습일까?")?.slot)
        assertEquals("문어는 어떤 모습일까?", s.nextStoryPrompt("문어는 어떤 모습일까?")?.text)
        s.applyStoryVerdict(verdict(fills = listOf("newcomer" to "보라색 문어"), next = "cause"), "child")
        assertEquals("cause", s.nextStoryPrompt()?.slot)
        s.storyNextSlot = "newcomer"
        assertFalse("resolved clarification must not reopen a filled slot", s.nextStoryPrompt()?.slot == "newcomer")
    }

    @Test
    fun clarificationCannotReopenAnUnneededSlotAndResetClearsIt() {
        val s = DemoState()
        s.turn = 3
        s.slots["newcomer"] = "문어"
        s.applyStoryVerdict(verdict(next = "newcomer").copy(unclear = true), "child")
        assertEquals("newcomer", s.nextStoryPrompt()?.slot)
        s.applyStoryVerdict(verdict(next = "newcomer", noLongerNeeded = "newcomer").copy(unclear = true), "child")
        assertFalse(s.nextStoryPrompt()?.slot == "newcomer")
        s.storyUnneededSlots.clear()
        s.applyStoryVerdict(verdict(next = "newcomer").copy(unclear = true), "child")
        s.resetStory()
        s.turn = 3
        s.slots["newcomer"] = "문어"
        s.storyNextSlot = "newcomer"
        assertFalse(s.nextStoryPrompt()?.slot == "newcomer")
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
    fun oneLiveTurnSendsNamesAndRestoresPlaceholdersInTheVerdictAndMascotLine() = runBlocking {
        val s = DemoState()
        s.slots["place"] = "친구의 숲"
        var sent: Server.Turn? = null
        val result = s.exchangeStoryTurn("problem", "친구야, 무슨 일이야?", "친구가 길을 잃었어") {
            sent = it
            Server.TurnResult(
                verdict(fills = listOf("problem" to "{주인공}이 길을 잃었다"), next = "reaction"),
                Server.Line("{주인공}이 길을 잃었구나", null, "그다음에는 어떻게 했어?",
                    listOf("{주인공}는 친구를 불렀어", "길을 찾아봤어", " ")),
            )
        }

        assertEquals("친구의 숲", sent!!.slots["place"])   // names go as they are (10-02)
        assertEquals("친구야, 무슨 일이야?", sent!!.question)
        assertEquals("친구가 길을 잃었어", sent!!.utterance)
        assertEquals("친구가 길을 잃었다", s.slots["problem"])
        assertEquals("child", s.slotBy["problem"])
        assertEquals("reaction", s.storyNextSlot)
        assertEquals("친구가 길을 잃었구나", result?.line?.ack)
        assertEquals(listOf("친구는 친구를 불렀어", "길을 찾아봤어"), result?.line?.options)
    }

    @Test
    fun serverExtraQuestionAndAnswerReachTheNextTurnBeforeBookReadiness() = runBlocking {
        val s = DemoState().apply {
            turn = 3
            templateKey = "C"
            slots["place"] = "숲"
            slots["problem"] = "길을 잃었어"
        }
        s.exchangeStoryTurn("reaction", "그다음에는 어떻게 했어?", "곰인형을 안고 쉬었어") {
            Server.TurnResult(
                verdict(fills = listOf("reaction" to it.utterance), next = "extra"),
                Server.Line("곰인형을 안고 쉬었구나!", null, "쉬고 난 뒤에는 어떻게 돌아왔어?"),
            )
        }
        val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
        assertEquals("extra", prompt.slot)
        assertEquals("쉬고 난 뒤에는 어떻게 돌아왔어?", prompt.text)
        assertFalse(prompt.templateOnly)
        assertFalse(s.storyReady)

        var sent: Server.Turn? = null
        s.exchangeStoryTurn(prompt.slot, prompt.text, "엄마와 집에 돌아왔어", by = "child") {
            sent = it
            Server.TurnResult(verdict(fills = listOf("extra" to it.utterance), ready = true), null)
        }
        assertEquals("extra", sent!!.askedSlot)
        assertEquals(prompt.text, sent!!.question)
        assertEquals("엄마와 집에 돌아왔어", s.slots["extra"])
        assertEquals("child", s.slotBy["extra"])
        assertTrue(s.storyReady)
        assertNull(s.nextStoryPrompt())
    }

    @Test
    fun filledExtraIsOnlyReopenedForAnExplicitClarification() {
        val s = DemoState().apply {
            turn = 3
            slots["extra"] = "곰인형을 안고 쉬었어"
        }
        s.applyStoryVerdict(verdict(next = "extra"), "child")
        assertNull(s.storyNextSlot)
        s.applyStoryVerdict(verdict(next = "extra").copy(unclear = true, unclearOf = "돌아온 방법"), "child")
        assertEquals("extra", s.nextStoryPrompt("집에는 어떻게 돌아왔어?")?.slot)
        assertEquals("집에는 어떻게 돌아왔어?", s.nextStoryPrompt("집에는 어떻게 돌아왔어?")?.text)
    }

    @Test
    fun extraDeclaredUnneededCannotBeReopenedByTheServer() {
        val s = DemoState().apply { turn = 3 }
        s.applyStoryVerdict(verdict(next = "extra", noLongerNeeded = "extra").copy(unclear = true), "child")
        assertTrue("extra" in s.storyUnneededSlots)
        assertNull(s.storyNextSlot)
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
