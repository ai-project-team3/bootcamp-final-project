package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class StoryLiveRubTest {
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
    private fun story() = DemoState().apply {
        templateKey = "C"
        themeKey = "dino"
        newcomerKind = "고양이"
        placeLabel = "숲"
    }

    @Test fun liveInstructionsAndCompletionDoNotNameTheHiddenTrain() {
        val s = story()
        for (line in listOf(s.m1Line(), s.m1Caption(), s.m1Done())) {
            assertFalse("hidden ride in live mission: $line", s.rideName in line)
            assertFalse("invented shaking in live mission: $line", "흔들" in line)
        }
        // 모르는 손님(고양이)이면 이름 없는 반짝이 가루 — 무엇이 묻었는지 말하지 않고 동작만(#259 · 전에는 「먼지」)
        assertFalse(s.mission1().named)
        assertTrue(s.m1Line(), "문질러" in s.m1Line() && "먼지" !in s.m1Line())
    }

    @Test fun fallbackRubCaptionMatchesTheVisibleMissionBeforeAndAfterPlaying() {
        for (template in TEMPLATES) {
            val s = story().apply { templateKey = template.key }
            val page = (1..s.pageCount).first { s.pageKind(it) == PageKind.RUB }
            assertFalse("${template.key}: ${s.bookCaption(page)}", s.rideName in s.bookCaption(page))
            assertTrue(s.bookCaption(page), "먼지" !in s.bookCaption(page) && "닦아 줄 자리" in s.bookCaption(page))
            s.m1Result = "done"
            assertFalse(s.rideName in s.bookCaption(page))
            assertTrue(s.bookCaption(page).contains(s.mission1().done))
        }
    }

    @Test fun scriptedMissionStillNamesItsVisibleRideAndGeneratedCaptionIsPreserved() {
        val s = story()
        s.storyCaptions = s.template!!.pages.map { "아이의 이야기 장면" }
        val page = (1..s.pageCount).first { s.pageKind(it) == PageKind.RUB }
        assertEquals("아이의 이야기 장면", s.bookCaption(page))
        Server.base = null
        s.storyCaptions = null
        assertTrue(s.m1Line().contains(s.rideName))
        assertTrue(s.bookCaption(page).contains(s.rideName))
    }
}
