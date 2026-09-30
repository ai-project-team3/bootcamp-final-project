package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.junit.Assert.*
import org.junit.Test

class StoryProjectionTest {
    @Test fun confirmedSolutionSelectsTheSamePropForMissionAndResultCaption() {
        val s = DemoState()
        s.slots["solution"] = "딸기를 친구에게 나눠 줬어"
        s.syncStoryPresentation()
        assertEquals("strawberry", s.solutionItem)
        assertTrue(s.mission2().done.contains("딸기"))
        s.slots["solution"] = "친구를 초대했어"
        s.syncStoryPresentation()
        assertEquals("invite", s.solutionItem)
    }
    @Test fun confirmedCauseSelectsAHelpingTemplateInsteadOfTheDefaultLonelyCause() {
        val s = DemoState().apply { level = Level.CHAIN }
        s.slots["cause"] = "배가 고파서"
        s.syncStoryPresentation()
        assertEquals("hungry", s.causeKind)
        assertEquals("D", chooseTemplate(s.level, s.causeKind).first)
    }
}
