package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.junit.Assert.*
import org.junit.Test

class StoryServerInputTest {
    @Test fun templateAnswersPreserveOriginalExtraAndIndividualSourcesAcrossRepeatedRequests() {
        val s = DemoState().apply { templateKey = "C" }
        val local = s.template!!.plot.first()
        s.slots["extra"] = "다음에 또 오고 싶어"; s.slotBy["extra"] = "child"
        s.recordTemplateAnswer(StoryPrompt(local, "", true), "다리에서 멈췄어", "card")
        val first = s.storyServerInput()
        assertEquals("다음에 또 오고 싶어\n다리에서 멈췄어", first.slots["extra"])
        assertFalse("mixed sources must not pretend all material is child speech", first.sources.containsKey("extra"))
        assertEquals("child", s.slotBy["extra"])
        assertEquals("card", s.slotBy[local])
        assertEquals("다음에 또 오고 싶어", s.slots["extra"])
        assertEquals(first, s.storyServerInput())
    }
}
