package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.TurnHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10-02 (#87): a misheard answer is taken back whole — every slot it filled, the next question, the counts. */
class TurnHistoryTest {

    private fun DemoState.answer(apply: DemoState.() -> Unit, h: TurnHistory) {
        h.before(); apply(); h.done()
    }

    @Test
    fun undoTakesBackTheWholeTurnAndRedoPutsItBack() {
        val s = DemoState()
        val h = TurnHistory(s)
        s.answer({ slots["place"] = "바닷속"; slotBy["place"] = "child"; place = "바닷속"; turn = 1; storyServerQuestion = "누굴 만났어?" }, h)
        // the misheard turn: one answer filled two slots, dropped one, moved the level and the counts
        s.answer({
            slots["companion"] = "친구"; slots["newcomer"] = "문어"; slotBy["companion"] = "child"
            storyUnneededSlots += "adult"; storyNextSlot = "problem"; storyServerQuestion = "무슨 일이 생겼어?"
            turn = 2; level = Level.REASON; reactions = 3; modeVoice = 2
        }, h)
        assertTrue(s.canUndo); assertFalse(s.canRedo)

        assertTrue(h.undo())
        assertEquals(mapOf("place" to "바닷속"), s.slots.toMap())
        assertTrue(s.storyUnneededSlots.isEmpty())
        assertNull(s.storyNextSlot)
        assertEquals("누굴 만났어?", s.storyServerQuestion)     // the same question is asked again
        assertEquals(1, s.turn); assertEquals(Level.CHAIN, s.level)
        assertEquals(0, s.reactions); assertEquals(0, s.modeVoice)
        assertTrue(s.canUndo); assertTrue(s.canRedo)

        assertTrue(h.redo())
        assertEquals("문어", s.slots["newcomer"]); assertEquals("problem", s.storyNextSlot)
        assertEquals(Level.REASON, s.level); assertFalse(s.canRedo)
    }

    @Test
    fun aNewAnswerAfterUndoDropsTheForwardTurn() {
        val s = DemoState()
        val h = TurnHistory(s)
        s.answer({ slots["place"] = "우주" }, h)
        h.undo()
        assertTrue(s.canRedo)
        s.answer({ slots["place"] = "바닷속" }, h)
        assertFalse("a new path forgets the undone one", s.canRedo)
        assertFalse(h.redo())
        assertEquals("바닷속", s.slots["place"])
    }

    @Test
    fun nothingToUndoAtTheStart() {
        val s = DemoState()
        assertFalse(TurnHistory(s).undo())
        assertFalse(s.canUndo)
    }

    @Test
    fun theButtonsAreRecognisedAndOrdinaryTapsAreNot() {
        assertTrue(TurnHistory.isNav(Reply.Tapped(TurnHistory.UNDO, "되돌리기")))
        assertTrue(TurnHistory.isNav(Reply.Tapped(TurnHistory.REDO, "앞으로")))
        assertFalse(TurnHistory.isNav(Reply.Tapped("keep:friend", "콩이")))
        assertFalse(TurnHistory.isNav(Reply.Silent))
    }
}
