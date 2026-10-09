package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.applyStoryVerdict
import com.example.finalproject_demo.demo.storyNameWithoutCopula
import com.example.finalproject_demo.demo.syncStoryPresentation
import com.example.finalproject_demo.net.Server
import org.junit.Assert.assertEquals
import org.junit.Test

/** 10-09 device — 「아기 펭귄 이름은 뭐야?」 → 「뽀뽀야」 was kept as the name 「뽀뽀야」 */
class StoryNameCallTest {

    @Test
    fun theCopulaIsTakenOffOnlyWhenANameIsLeft() {
        assertEquals("뽀뽀", storyNameWithoutCopula("뽀뽀야"))
        assertEquals("뽀뽀", storyNameWithoutCopula("뽀뽀예요"))
        assertEquals("뽀뽀", storyNameWithoutCopula("뽀뽀에요."))
        assertEquals("뽀야", storyNameWithoutCopula("뽀야야"))
        assertEquals("a one-syllable rest is not a name — 「뽀야」 is", "뽀야", storyNameWithoutCopula("뽀야"))
        assertEquals("a final 이 is kept", "콩콩이", storyNameWithoutCopula("콩콩이야"))
        assertEquals("곰돌아", storyNameWithoutCopula("곰돌아"))
        assertEquals("우가우가", storyNameWithoutCopula("우가우가"))
        assertEquals("more than one word is left alone", "아기 펭귄야", storyNameWithoutCopula("아기 펭귄야"))
    }

    /** #380 review — a picked name keeps its 야: 「카구야」 from a card or the mascot is 카구야 */
    @Test
    fun aPickedNameIsKeptAsItIs() {
        listOf("card", "mascot").forEach { by ->
            val s = DemoState().apply { slots["newcomer"] = "토끼" }
            s.applyStoryVerdict(Server.Verdict(
                reason = "test", fills = listOf("name" to "카구야"), nextSlot = null, noLongerNeeded = null,
                storyReady = false, unclear = false, unclearOf = null, contradiction = false,
                s1Reason = false, s2Addition = false, emotion = null,
            ), by)
            assertEquals(by, "카구야", s.slots["name"])
        }
    }

    @Test
    fun theNameSlotKeepsTheNameOnly() {
        val s = DemoState().apply { slots["newcomer"] = "아기 펭귄" }
        s.applyStoryVerdict(Server.Verdict(
            reason = "test", fills = listOf("name" to "뽀뽀야"), nextSlot = null, noLongerNeeded = null,
            storyReady = false, unclear = false, unclearOf = null, contradiction = false,
            s1Reason = false, s2Addition = false, emotion = null,
        ), "child")
        s.syncStoryPresentation()
        assertEquals("뽀뽀", s.slots["name"])
        assertEquals("뽀뽀", s.friendName)
    }
}
