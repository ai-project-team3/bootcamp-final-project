package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-05 실기기 — 협업 「동물원 다녀왔어요」 책 8쪽 중 3쪽이 「…은 아직 듣지 못했어요」였다.
 * 재료가 없는 쪽은 세우지 않는다: 까닭도 누가 한 말도 없으면 까닭 쪽(TALK)을 빼고, 마음이 없으면 마음 쪽(FAIL)을 뺀다.
 * 미션 쪽(RUB · DRAG)은 그대로다. 그림일기 책은 바꾸지 않는다.
 */
class CoopBookPagesTest {

    private fun state(mode: StoryMode) = DemoState().apply {
        this.mode = mode
        place = "놀이공원"; problem = "회전목마를 탔어"; solution = "솜사탕을 먹고 집에 왔어"
    }

    private fun DemoState.kinds() = template!!.pages.map { it.kind }

    @Test
    fun aCoopBookWithNoWhyHasNoWhyPage() {
        val s = state(StoryMode.COOP)
        assertFalse(PageKind.TALK in s.kinds())
        assertFalse(PageKind.FAIL in s.kinds())
        assertTrue(PageKind.RUB in s.kinds() && PageKind.DRAG in s.kinds())
    }

    @Test
    fun aCauseOrWhatSomeoneSaidBringsTheWhyPageBack() {
        val withCause = state(StoryMode.COOP).apply { cause = "당근이 먹고 싶어서" }
        assertTrue(PageKind.TALK in withCause.kinds())
        val withSaid = state(StoryMode.COOP).apply { slots["said"] = "아빠가 괜찮다고 했어" }
        assertTrue(PageKind.TALK in withSaid.kinds())
    }

    @Test
    fun theMissionPagesAreStillFoundAfterAPageIsDropped() {
        val s = state(StoryMode.COOP)
        val kinds = s.kinds()
        assertEquals(kinds.size, s.template!!.pages.size)
        assertEquals(PageKind.RUB, kinds[kinds.indexOf(PageKind.DRAG) - 1])
        assertEquals(PageKind.TOGETHER, kinds.last())
    }

    @Test
    fun theDiaryBookKeepsItsWhyPage() {
        assertTrue(PageKind.TALK in state(StoryMode.DIARY).kinds())
    }
}
