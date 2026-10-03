package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPageMission
import com.example.finalproject_demo.demo.missionFor
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.StoryFacts
import com.example.finalproject_demo.demo.missions.pickMissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-03 · 맞춤미션 설계 §6-2: the picker is checked as a table, story → missions.
 * Stage 1-a keeps today's behaviour, so the table is today's rule; new rows come with new screens.
 */
class MissionPickerTest {

    private fun facts(template: String?, mode: StoryMode = StoryMode.STORY) =
        StoryFacts(mode, template, problem = null, cause = null, solution = null, realDay = false)

    @Test
    fun todaysRuleAsATable() {
        val table = mapOf(
            "A" to (MissionId.A6 to MissionId.A3),   // 도전-성취: put it back together
            "G" to (MissionId.A6 to MissionId.A3),   // 우화-교훈
            "B" to (MissionId.A6 to MissionId.E1),
            "C" to (MissionId.A6 to MissionId.E1),
            null to (MissionId.A6 to MissionId.E1),  // no frame yet: the defaults
        )
        table.forEach { (frame, want) ->
            val got = pickMissions(facts(frame))
            assertEquals("frame $frame", want, got.slot1 to got.slot2)
        }
    }

    @Test
    fun onlyBuiltMissionsInTheirOwnSlotAndNeverTheSameTwice() {
        listOf("A", "B", "C", "D", "E", "F", "G", null).forEach { frame ->
            val m = pickMissions(facts(frame))
            assertTrue(m.slot1.built && m.slot2.built)
            assertTrue(1 in m.slot1.slots && 2 in m.slot2.slots)
            assertTrue(m.slot1 != m.slot2)
        }
    }

    /** The server page plan, co-op's page plan and the book screen used to copy the rule; now they ask the picker */
    @Test
    fun storyAndCoopPagesCarryThePickedIds() {
        val s = DemoState().apply { templateKey = "G" }
        assertEquals(MissionId.A6, s.missionFor(PageKind.RUB))
        assertEquals(MissionId.A3, s.missionFor(PageKind.DRAG))
        assertNull(s.missionFor(PageKind.MEET))
        s.mode = StoryMode.entries.first { it != StoryMode.STORY && it.usesDiaryQuestions }
        s.templateKey = "B"
        assertEquals("A6", s.coopPageMission(PageKind.RUB))
        assertEquals("E1", s.coopPageMission(PageKind.DRAG))
    }
}
