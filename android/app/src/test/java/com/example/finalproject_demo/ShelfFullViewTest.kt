package com.example.finalproject_demo

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.ParentView
import com.example.finalproject_demo.ui.shell.OttoRoom
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 책장 꽉 참 알림(방) · 책장 정리(부모 모드)를 실제로 눌러 본다 (#80) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class ShelfFullViewTest {
    @get:Rule val compose = createComposeRule()

    private val sup = SupervisorJob()
    @After fun stop() = sup.cancel()

    private class Stories(n: Int) : StoryBookStore {
        val books = (1..n).map {
            SavedStoryBook("s$it", "동화 $it", "A", "bg_park", listOf(SavedStoryPage(PageKind.COVER, "첫 쪽")), madeAt = "2026-10-0${it % 9 + 1}")
        }.toMutableList()
        override fun load() = books.toList()
        override fun save(book: SavedStoryBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.id == id }
    }

    private fun director(n: Int) = Director(CoroutineScope(sup), Stories(n)).also { it.s.speed = 0.01 }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File("build/review/$name.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    private fun waitText(t: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(t: String) {
        compose.waitUntil(8_000) { compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).onLast().performClick()
    }

    @Test
    fun theRoomShowsTheFullShelfAlertAndCloseDismissesIt() {
        val d = director(12)
        compose.setContent { OttoRoom(d) }
        d.go(Scene.ADULT)
        compose.waitUntil(5_000) { d.s.stage == Stage.Adult }
        d.send(Reply.Tapped("start", "동화 만들기"))
        waitText("책장이 꽉 찼어!")
        shot("shelf_full_room")
        tap("닫기")
        compose.waitUntil(5_000) { d.s.shelfFull == null }
        assertEquals(Scene.ADULT, d.s.scene)
    }

    @Test
    fun parentShelfTidyRemovesABookAfterAskingAgain() {
        val d = director(12)
        d.s.shelfTidyMode = StoryMode.STORY
        compose.setContent { ParentView(d, (d.s.stage as? Stage.Parent)?.tab ?: "shelf") }
        d.go(Scene.PARENT)
        compose.waitUntil(5_000) { d.s.stage == Stage.Parent("shelf") }
        waitText("동화 12/12")
        waitText("책장이 꽉 찼어요")
        shot("shelf_tidy_parent")
        compose.onAllNodes(hasText("빼기") and hasClickAction())[0].performClick()
        waitText("이 책을 뺄까요?")
        shot("shelf_tidy_confirm")
        tap("네, 뺄게요")
        compose.waitUntil(5_000) { d.storyBookCount() == 11 }
        waitText("동화 11/12")
        assertNull(d.storyBooks().firstOrNull { it.id == "s1" })
    }
}
