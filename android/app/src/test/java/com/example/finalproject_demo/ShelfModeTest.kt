package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.ShelfView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The visible shelf is per mode, like storage and the 12-book limit (#154 · guidelines/3 §3-5) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class ShelfModeTest {
    @get:Rule val compose = createComposeRule()

    private val sup = SupervisorJob()
    @After fun stop() = sup.cancel()

    private val story = ShelfBook("공룡 동화", "A", "bg_park", savedStoryId = "s1")
    private val diary = ShelfBook("소풍 일기", "diary", "bg_park", savedStoryId = DIARY_SHELF_ID + "d1")
    private val coop = ShelfBook("같이 만든 책", "A", "bg_park", savedStoryId = COOP_SHELF_ID + "c1")

    @Test
    fun savedBooksAreSortedByIdPrefix() {
        assertEquals(StoryMode.STORY, story.shelfMode(StoryMode.DIARY))
        assertEquals(StoryMode.DIARY, diary.shelfMode(StoryMode.STORY))
        assertEquals(StoryMode.COOP, coop.shelfMode(StoryMode.STORY))
    }

    @Test
    fun anUnsavedBookBelongsToTheModeThatMadeIt() {
        val unsaved = ShelfBook("저장 못 한 책", "A", "bg_park", fresh = true)
        assertEquals(StoryMode.DIARY, unsaved.shelfMode(StoryMode.DIARY))
    }

    @Test
    fun theShelfOpensOnTheFreshBooksModeElseTheFirstModeWithBooks() {
        assertEquals(StoryMode.COOP, openShelfMode(listOf(story, coop.copy(fresh = true)), StoryMode.STORY))
        assertEquals(StoryMode.DIARY, openShelfMode(listOf(coop, diary), StoryMode.STORY))
        assertEquals(StoryMode.STORY, openShelfMode(emptyList(), StoryMode.COOP))
    }

    @Test
    fun tappingAModeTabShowsOnlyThatModesBooks() {
        val d = Director(CoroutineScope(sup))
        d.s.shelf.addAll(listOf(story, diary, coop))
        compose.setContent { ShelfView(d, Stage.Shelf(fromEnd = false)) }

        fun shown(title: String) = compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        compose.waitForIdle()
        assertEquals(listOf(true, false, false), listOf(shown(story.title), shown(diary.title), shown(coop.title)))

        compose.onRoot().captureRoboImage(File("build/review/shelf_per_mode.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.onNodeWithContentDescription("그림일기 책장", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, true, false), listOf(shown(story.title), shown(diary.title), shown(coop.title)))

        compose.onNodeWithContentDescription("같이 만들기 책장", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, false, true), listOf(shown(story.title), shown(diary.title), shown(coop.title)))
    }
}
