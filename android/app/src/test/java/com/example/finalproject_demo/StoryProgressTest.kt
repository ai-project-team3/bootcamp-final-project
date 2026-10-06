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

    @Test fun readyFinishesConversationWithoutClaimingTheBookIsComplete() {
        val s = DemoState().apply { endReason = "story_ready" }
        assertEquals(10, s.askTotal)
        assertEquals(7, s.askDone)
        assertNull(s.nextStoryPrompt())
    }

    @Test fun finalVerdictThenBookWritingAndCompletionHaveSeparateMilestones() {
        val s = DemoState().apply {
            listOf("place", "problem", "reaction", "newcomer", "solution").forEach { slots[it] = "아이의 답" }
            syncStoryPresentation()
        }
        assertEquals(5, s.askDone)
        s.applyStoryVerdict(Server.Verdict("ready", emptyList(), null, null, true, false, null, false, false, false, null), "child")
        assertEquals("the last answer must not fill the whole track", 7, s.askDone)
        s.scene = Scene.MAKING
        s.stage = Stage.Making("이야기 문장을 쓰는 중…")
        assertEquals(9, s.askDone)
        s.stage = Stage.Making("『완성된 책』", 1f)
        assertEquals(s.askTotal, s.askDone)
    }

    @Test fun extraQuestionsAndTemplateChoiceDoNotInventProgressOrChangeTheScale() {
        val s = filledStory()
        val before = s.askDone to s.askTotal
        assertEquals(6 to 10, before)
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
        assertEquals(6, s.askDone)
        assertNull(s.storyEndCondition())
        s.endReason = "story_ready"
        assertEquals(7, s.askDone)
        s.scene = Scene.MAKING
        s.stage = Stage.Making("준비", 0.95f)
        assertEquals(9, s.askDone)
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
}
