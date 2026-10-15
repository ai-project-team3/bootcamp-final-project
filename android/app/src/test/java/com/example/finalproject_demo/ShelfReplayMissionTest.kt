package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.withOpenMissions
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 책장에서 다시 읽을 때 미션을 다시 할 수 있다 (앱_품질향상_설계 ⑤-1 J1 · 10-14 실기기 「삐뽀삐뽀」 6쪽).
 * - 미션 쪽은 열린 채로 시작하고, 자막에서 첫 읽기 때 붙은 결과 문장을 뺀다
 * - ▶ 는 잠그지 않는다 — 「그냥 읽기」로 넘어갈 수 있다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class ShelfReplayMissionTest {
    @get:Rule val compose = createComposeRule()

    private fun savedBook(): SavedStoryBook {
        val state = DemoState().apply {
            templateKey = "C"
            useGeneratedStory(template!!.pages.indices.map { "${it + 1}쪽 기억할 문장" })
            slots["problem"] = "삐뽀삐뽀 소방차가 지나갔어"
            m1Result = "solo"; m2Result = "solo"
        }
        return state.completedStoryBook()!!
    }

    @Test
    fun anOpenMissionPageLosesOnlyItsResultSentence() {
        val book = savedBook()
        val reader = Director(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).apply { s.restoreStoryBook(book) }
        val open = book.withOpenMissions(reader.s, m1Done = false, m2Done = false)
        book.pages.zip(open.pages).forEachIndexed { i, (saved, shown) ->
            if (saved.kind == PageKind.RUB || saved.kind == PageKind.DRAG) assertTrue("page ${i + 1}", saved.caption.startsWith(shown.caption))
            else assertEquals("page ${i + 1}", saved.caption, shown.caption)
        }
        assertTrue("a mission page with a result sentence is opened", book.pages.zip(open.pages).any { (a, b) -> a.caption != b.caption })
        assertSame(book, book.withOpenMissions(reader.s, m1Done = true, m2Done = true))
    }

    @Test
    fun theMissionPageIsPlayableAndSkippable() {
        val book = savedBook()
        val rubPage = book.pages.indexOfFirst { it.kind == PageKind.RUB } + 1
        assertTrue(rubPage > 0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val frozen = motionFrozen
        try {
            motionFrozen = true
            compose.setContent { SavedStoryView(Director(scope), Stage.SavedStory(book, rubPage)) }
            compose.onNodeWithText("그냥 읽기").assertExists()
        } finally {
            motionFrozen = frozen
            scope.cancel()
        }
    }
}
