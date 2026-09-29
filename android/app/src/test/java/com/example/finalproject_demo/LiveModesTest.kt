package com.example.finalproject_demo

import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode switches (09-29): a mode reaches the server only with an address **and** its switch on. */
class LiveModesTest {
    @After
    fun reset() { Server.base = null; Server.liveModes = emptySet() }

    @Test
    fun parse() {
        assertEquals(setOf(StoryMode.STORY, StoryMode.DIARY), Server.parseLive("story,diary"))
        assertEquals(setOf(StoryMode.COOP), Server.parseLive(" COOP "))
        assertEquals(StoryMode.entries.toSet(), Server.parseLive("all"))
        assertEquals(emptySet<StoryMode>(), Server.parseLive(null))
        assertEquals(emptySet<StoryMode>(), Server.parseLive("stroy"))   // a typo switches nothing on
    }

    @Test
    fun defaultIsAllOff() {
        Server.base = "http://127.0.0.1:8010"
        StoryMode.entries.forEach { assertFalse(Server.liveFor(it)) }
    }

    @Test
    fun needsAddressAndSwitch() {
        Server.liveModes = setOf(StoryMode.STORY)
        assertFalse(Server.liveFor(StoryMode.STORY))          // no address
        Server.base = "http://127.0.0.1:8010"
        assertTrue(Server.liveFor(StoryMode.STORY))
        assertFalse(Server.liveFor(StoryMode.DIARY))
        Server.toggle(StoryMode.STORY)
        assertFalse(Server.liveFor(StoryMode.STORY))
        Server.toggle(StoryMode.DIARY)
        assertTrue(Server.liveFor(StoryMode.DIARY))
    }
}
