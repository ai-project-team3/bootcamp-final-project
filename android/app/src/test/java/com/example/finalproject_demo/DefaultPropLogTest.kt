package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.diaryGiveItem
import com.example.finalproject_demo.demo.m2Log
import com.example.finalproject_demo.demo.mission2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #304 ② — 아이가 말하지 않은 별이 로그에 「4턴째에 말한 별」로 남았다. 해결 물건을 못 찾아 쓴 기본 소품은
 * `fromChild = false` 이고, 로그는 「기본 소품」이라 적는다. 물건 고르기 자체는 #259 몫이라 그대로다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultPropLogTest {
    private fun state(mode: StoryMode, item: String) = DemoState().apply { this.mode = mode; solutionItem = item }

    @Test
    fun aKnownItemIsFromTheChildAndTheStarIsNot() {
        listOf("note", "gem", "strawberry", "snack", "invite", "balloon", "block", "picturebook", "bandaid").forEach {
            assertTrue("$it 는 아이 말에서", state(StoryMode.COOP, it).mission2().fromChild)
        }
        assertFalse(state(StoryMode.COOP, "star").mission2().fromChild)
    }

    @Test
    fun whatTheChildDidNotNameGetsTheDefaultStar() {
        val s = state(StoryMode.COOP, "star")
        s.solutionItem = diaryGiveItem("아빠가 잡아줬어", s)
        val m = s.mission2()
        assertEquals("별", m.itemName)
        assertFalse(m.fromChild)
    }

    @Test
    fun theLogNeverSaysTheChildNamedTheDefaultProp() {
        for (mode in listOf(StoryMode.COOP, StoryMode.DIARY, StoryMode.STORY)) {
            val line = state(mode, "star").m2Log(5)
            assertTrue("$mode: $line", "기본 소품 별" in line)
            assertFalse("$mode: $line", "4턴째" in line || "말한" in line)
        }
        val coop = state(StoryMode.COOP, "block").m2Log(5)
        assertTrue(coop, "해결에서 나온 블록을" in coop)
        assertFalse(coop, "4턴째" in coop)
        val story = state(StoryMode.STORY, "balloon").m2Log(5)
        assertTrue(story, "장면 10에서 말한 풍선을" in story)
    }
}
