package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.causeSaid
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.syncStoryPresentation
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 device (Laya v5) — the child never told a cause, yet the book log carried 「까닭 "친구가 없어서 심심했어"」: the
 * script default of causeLine. Mission 2 voiced it too (「○○가 친구가 없어서 심심했대」). Only a told cause is used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryCauseSaidTest {
    private var base: String? = null
    private var modes: Set<StoryMode> = emptySet()

    @Before fun live() { base = Server.base; modes = Server.liveModes; Server.base = "http://127.0.0.1:9"; Server.liveModes = setOf(StoryMode.STORY) }
    @After fun restore() { Server.base = base; Server.liveModes = modes }

    @Test
    fun anUntoldCauseIsNeitherLoggedNorVoiced() {
        val s = DemoState().apply { slots["problem"] = "아기 펭귄이 엄마를 잃어버렸어"; slots["newcomer"] = "북극곰" }
        s.syncStoryPresentation()
        assertNull(s.causeSaid)
        assertFalse("mission 2 says no cause nobody told", "심심" in s.m2Line(easy = false))
    }

    @Test
    fun aToldCauseIsUsed() {
        val s = DemoState().apply { slots["cause"] = "배가 고파서"; slots["newcomer"] = "북극곰" }
        s.syncStoryPresentation()
        assertEquals("배가 고파서", s.causeSaid)
        Server.liveModes = emptySet()
        assertEquals("a scripted story keeps its scene cause", "배가 고파서", s.causeSaid)
        assertTrue(DemoState().causeSaid != null)
    }

    /** 10-12 device review — 「뽀삐가 길을 잃어버렸어」 · 「너무 깜깜해서」 is 뽀삐's cause, not the octopus's (「문어가 너무 깜깜해서래」) */
    @Test
    fun theCauseGoesWithWhoeverHadTheProblem() {
        val lost = DemoState().apply {
            slots["problem"] = "뽀삐가 길을 잃어버렸어"; slots["cause"] = "너무 깜깜해서"; slots["newcomer"] = "문어"
        }
        lost.syncStoryPresentation()
        val line = lost.m2Line(easy = false)
        assertFalse(line, "깜깜" in line)
        assertTrue(line, "문어한테 건네줄래?" in line)

        val hurt = DemoState().apply {
            slots["problem"] = "문어가 다리를 다쳤어"; slots["cause"] = "바위에 부딪혀서"; slots["newcomer"] = "문어"
        }
        hurt.syncStoryPresentation()
        assertTrue(hurt.m2Line(easy = false), "문어가 바위에 부딪혀서래." in hurt.m2Line(easy = false))
    }
}
