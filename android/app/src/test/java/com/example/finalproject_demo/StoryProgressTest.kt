package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class StoryProgressTest {
    private val previousBase = Server.base
    private val previousModes = Server.liveModes

    @Before fun connectStory() {
        Server.base = "http://localhost:1"
        Server.liveModes = setOf(StoryMode.STORY)
    }

    @After fun restoreConnection() {
        Server.base = previousBase
        Server.liveModes = previousModes
    }

    private fun filledStory() = DemoState().apply {
        templateKey = "C"
        level = Level.REASON
        turn = 10
        place = "숲"; problem = "길을 잃었어"; cause = "어두워서"
        newcomer = "고양이"; sound = "야옹"; solution = "함께 돌아왔어"
        (template!!.plot + template!!.ending).forEach { slots[it] = "아이의 답" }
        listOf("place", "problem", "reaction", "cause", "solution", "newcomer", "sound").forEach { slots[it] = "아이의 답" }
    }

    @Test fun fullMaterialsDoNotShowCompletionWhileTheStoryStillAsksForMore() {
        val s = filledStory()
        assertEquals("이야기를 조금 더 들려줄래?", s.nextStoryPrompt()!!.text)
        repeat(3) {
            assertTrue("progress shows complete before story_ready: ${s.askDone}/${s.askTotal}", s.askDone < s.askTotal)
            assertNull(s.storyEndCondition())
            s.turn++
        }
    }

    @Test fun readyLeavesTheLastStarForStartingBookCreation() {
        val s = DemoState().apply { endReason = "story_ready" }
        assertEquals(40, s.askTotal)
        assertEquals(36, s.askDone)
        assertNull(s.nextStoryPrompt())
    }

    @Test fun finalVerdictFinishesPreparationAndBookCreationStartsWithAFullTrack() {
        val s = DemoState().apply {
            listOf("place", "problem", "reaction", "newcomer", "solution").forEach { slots[it] = "아이의 답" }
            syncStoryPresentation()
        }
        assertEquals(21, s.askDone)
        s.applyStoryVerdict(Server.Verdict("ready", emptyList(), null, null, true, false, null, false, false, false, null), "child")
        assertEquals("the last answer leaves the book-start star open", 36, s.askDone)
        s.scene = Scene.MAKING
        s.stage = Stage.Making("이야기 문장을 쓰는 중…")
        assertEquals("book creation starts with the preparation track full", s.askTotal, s.askDone)
        s.stage = Stage.Making("『완성된 책』", 1f)
        assertEquals(s.askTotal, s.askDone)
    }

    @Test fun extraQuestionsAndTemplateChoiceDoNotInventProgressOrChangeTheScale() {
        val s = filledStory()
        val before = s.askDone to s.askTotal
        assertEquals(26 to 40, before)
        repeat(12) {
            s.turn++
            s.slots["place"] = "같은 장소를 다시 말한 답"
            s.templateKey = if (it % 2 == 0) "A" else "C"
            assertEquals(before, s.askDone to s.askTotal)
        }
        assertNull(s.storyEndCondition())
    }

    @Test fun drawingAndSoundAloneDoNotClaimConversationOrBookCompletion() {
        val s = filledStory().apply { storySoundAttempted = true; stage = Stage.DrawPad() }
        assertEquals(26, s.askDone)
        assertNull(s.storyEndCondition())
        s.endReason = "story_ready"
        assertEquals(36, s.askDone)
        s.scene = Scene.MAKING
        s.stage = Stage.Making("준비", 0.95f)
        assertEquals(40, s.askDone)
    }

    @Test fun legacyEndReasonsCannotCompleteALiveStoryTrack() {
        val s = filledStory().apply { endReason = "timeout" }
        assertFalse(s.storyReady)
        assertTrue(s.askDone < s.askTotal)
    }

    @Test fun scriptedStoryAndOtherModesKeepTheirCounts() {
        Server.base = null
        val scripted = filledStory()
        assertEquals(scripted.askTotal, scripted.askDone)
        val diary = DemoState().apply { mode = StoryMode.DIARY; place = "공원"; problem = "놀았어" }
        val before = diary.askDone to diary.askTotal
        Server.base = "http://localhost:1"
        Server.liveModes = StoryMode.entries.toSet()
        assertEquals(before, diary.askDone to diary.askTotal)
    }

    /** 10-06 (종훈): the track sat near half and jumped to 90% on story_ready. It now rises every answer */
    @Test fun aTypicalLiveStoryRisesEveryAnswerAndNeverJumpsAtTheEnd() {
        val s = DemoState()
        val answers = listOf("place", "problem", null, "cause", "newcomer", "solution")
        var last = s.askDone
        answers.forEach { slot ->
            s.storyAnswers++
            if (slot != null) s.slots[slot] = "아이의 답"
            s.holdStoryGauge()
            assertTrue("each answer moves the track: $last -> ${s.askDone}", s.askDone > last)
            last = s.askDone
        }
        assertTrue("about 70% before story_ready, got $last/40", last >= 26)
        s.endReason = "story_ready"
        assertTrue("story_ready jumps ${s.askDone - last} steps", s.askDone - last <= 8)
        assertEquals(36, s.askDone)
    }

    @Test fun undoNeverMovesTheTrackBackAndCollectingNeverFillsIt() {
        val s = DemoState()
        listOf("place", "problem", "cause").forEach { s.storyAnswers++; s.slots[it] = "답"; s.holdStoryGauge() }
        val shown = s.askDone
        s.slots.remove("cause")                 // undo removed the last fact
        s.holdStoryGauge()
        assertEquals(shown, s.askDone)
        repeat(60) { s.storyAnswers++; s.holdStoryGauge() }
        assertTrue("collecting stops short of the finishing star: ${s.askDone}", s.askDone in 30..StoryPreparationProgress.COLLECTING_MAX)
        assertNull("the track never ends the story (rule 3)", s.storyEndCondition())
    }
}
