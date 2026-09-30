package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.TEMPLATES
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.mission2
import com.example.finalproject_demo.demo.storyPagePlan
import com.example.finalproject_demo.demo.useGeneratedStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBookPlanTest {
    @Test
    fun eachTemplateSendsItsOwnPageKindsAndOnlyImplementedMissionIds() {
        val s = DemoState()
        for (template in TEMPLATES) {
            s.templateKey = template.key
            val plan = s.storyPagePlan()
            assertEquals(template.key, template.pages.map { it.kind.name }, plan.map { it.kind })
            assertEquals(template.key, 1, plan.count { it.mission == "A6" })
            assertEquals(template.key, 1, plan.count { it.mission == if (template.key in setOf("A", "G")) "A3" else "E1" })
            assertEquals(template.key, "A6", plan.first { it.kind == PageKind.RUB.name }.mission)
            assertEquals(template.key, if (template.key in setOf("A", "G")) "A3" else "E1", plan.first { it.kind == PageKind.DRAG.name }.mission)
            assertTrue(template.key, plan.filter { it.kind !in setOf(PageKind.RUB.name, PageKind.DRAG.name) }.all { it.mission == null })
        }
    }

    @Test
    fun onlyACompleteGeneratedBookReplacesTheTemplate() {
        val s = DemoState()
        s.templateKey = "C"
        val original = s.bookCaption(1)
        assertFalse(s.useGeneratedStory(null))
        assertFalse(s.useGeneratedStory(listOf("한 쪽뿐이에요.")))
        assertFalse(s.useGeneratedStory(List(s.template!!.pages.size) { "" }))
        assertNull(s.storyCaptions)
        assertEquals(original, s.bookCaption(1))

        val captions = s.template!!.pages.indices.map { "서버가 쓴 ${it + 1}쪽이에요." }
        assertTrue(s.useGeneratedStory(captions))
        assertEquals(captions[0], s.bookCaption(1))
        assertEquals(captions.last(), s.bookCaption(captions.size))
    }

    @Test
    fun diaryAndCoopDoNotUseTheStoryPagePlan() {
        val s = DemoState()
        s.mode = StoryMode.DIARY
        assertTrue(s.storyPagePlan().isEmpty())
        assertFalse(s.useGeneratedStory(listOf("동화 서버의 문장")))
        s.mode = StoryMode.COOP
        assertTrue(s.storyPagePlan().isEmpty())
    }

    @Test
    fun missionResultAppearsInGeneratedCaptionOnlyAfterPlay() {
        val s = DemoState()
        s.templateKey = "C"
        s.useGeneratedStory(s.template!!.pages.indices.map { "생성된 ${it + 1}쪽이에요." })
        val rub = s.template!!.pages.indexOfFirst { it.kind == PageKind.RUB } + 1
        val drag = s.template!!.pages.indexOfFirst { it.kind == PageKind.DRAG } + 1
        assertEquals("생성된 ${rub}쪽이에요.", s.bookCaption(rub))
        assertEquals("생성된 ${drag}쪽이에요.", s.bookCaption(drag))

        s.m1Result = "solo"
        s.m2Result = "solo"
        assertTrue(s.bookCaption(rub).contains("사라졌어요"))
        assertTrue(s.bookCaption(drag).contains(s.mission2().give))
    }

    @Test
    fun puzzleCompletionDoesNotClaimAnItemWasGiven() {
        val s = DemoState()
        s.templateKey = "A"
        s.useGeneratedStory(s.template!!.pages.indices.map { "생성된 ${it + 1}쪽이에요." })
        val drag = s.template!!.pages.indexOfFirst { it.kind == PageKind.DRAG } + 1
        s.m2Result = "solo"

        assertTrue(s.bookCaption(drag).contains("그림 조각을 모두 맞춰"))
        assertFalse(s.bookCaption(drag).contains(s.mission2().give))
    }
}
