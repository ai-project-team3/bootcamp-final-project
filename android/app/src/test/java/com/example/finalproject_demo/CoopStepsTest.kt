package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_REQUIRED
import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.DIARY_STEPS
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Co-op walks its own step list (`CoopSteps.kt` · #36), so the diary rebuild can change `DIARY_STEPS` freely.
 */
class CoopStepsTest {

    @Test
    fun coopHasItsOwnListNotTheDiaryOne() {
        assertNotSame(DIARY_STEPS, COOP_STEPS)
        COOP_STEPS.forEach { c -> assertTrue(c.bookKey, DIARY_STEPS.none { it === c }) }
    }

    @Test
    fun theFourRequiredStepsMatchTheParentsFourTemplateLines() {
        // CoopScenes.COOP_PART_SLOTS pairs template lines 0..3 with these, by position
        assertEquals(listOf("place", "problem", "cause", "solution"), COOP_REQUIRED.map { it.bookKey })
    }

    @Test
    fun elevenStepsWithUniqueIdsThatCoopScenesCanRead() {
        assertEquals(11, COOP_STEPS.size)
        val ids = COOP_STEPS.map { it.variant.id }
        assertEquals(ids.size, ids.toSet().size)
        // CoopScenes.partIndexOf strips this prefix to find the part
        assertTrue(ids.all { it.startsWith("diary_") })
    }

    @Test
    fun theProgressBarCountsTheCoopListInCoop() {
        val s = DemoState().apply { mode = StoryMode.COOP }
        assertEquals(COOP_STEPS.count { it.ask(s) }.coerceAtLeast(s.reqCount), s.askTotal)
    }
}
