package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.MASCOT_NAME
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.m2Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10-09 user — the child sees and hears 「오또」, never the word 「마스코트」; internal ids (by=mascot) stay */
class MascotNameTest {

    @Test
    fun aDayWithNobodyGivesToOtto() {
        val s = DemoState().apply { mode = StoryMode.DIARY }
        assertEquals("오또", MASCOT_NAME)
        assertEquals(MASCOT_NAME, s.giveTargetName)
        listOf(true, false).forEach { easy ->
            val line = s.m2Line(easy)
            assertFalse(line, "마스코트" in line)
        }
        // the page log names the same receiver, not a script default (laya 5th device round)
        val log = s.m2Log(5)
        assertTrue(log, log.endsWith("${MASCOT_NAME}에게"))
    }
}
