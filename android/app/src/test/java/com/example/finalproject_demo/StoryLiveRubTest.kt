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
        assertTrue(s.m1Line().contains(s.mission1().blobName))
    }

    @Test fun fallbackRubCaptionMatchesTheVisibleMissionBeforeAndAfterPlaying() {
        for (template in TEMPLATES) {
            val s = story().apply { templateKey = template.key }
            val page = (1..s.pageCount).first { s.pageKind(it) == PageKind.RUB }
            assertFalse("${template.key}: ${s.bookCaption(page)}", s.rideName in s.bookCaption(page))
            assertTrue(s.bookCaption(page).contains(s.mission1().blobName))
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
