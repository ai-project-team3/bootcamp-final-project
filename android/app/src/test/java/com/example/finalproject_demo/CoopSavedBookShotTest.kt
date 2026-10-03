package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 같이 만들기 책을 책장에서 다시 열면 **만들 때와 같은 책 화면**(배경 · 함께한 사람 · 쪽 문장)으로 그려진다 (#83).
 * 전에는 배경 위에 글자만 있는 읽기 전용 화면이었다. 찍은 그림은 확인용(`build/review`)이라 기준 그림으로 남기지 않는다
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class CoopSavedBookShotTest {
    @get:Rule val compose = createComposeRule()

    private class Memory : CoopBookStore {
        val books = mutableListOf<SavedCoopBook>()
        override fun load() = books.toList()
        override fun save(book: SavedCoopBook) { books.add(0, book) }
    }

    @Test
    fun aSavedCoopBookOpensAsTheBookItWas() {
        val made = DemoState().apply {
            mode = StoryMode.COOP
            coopPick = CoopPick("place", "동물원", "done")
            place = "사자 우리"; placeLabel = "사자 우리"; slots["place"] = "사자 우리에 갔어요"
            companionKind = "엄마"; friend = "엄마"; slots["companion"] = "엄마랑 같이 갔어요"
            problem = "사자가 문을 열고 나왔어"; slots["problem"] = "사자가 문을 열고 나왔어요"
            reaction = "깜짝 놀랐어"; slots["reaction"] = "깜짝 놀랐어요"
            title = "사자 우리 대소동"
        }
        made.storyCaptions = (1..made.pageCount).map { "${it}쪽 — 같이 만든 이야기" }
        val store = Memory()
        CoopShelf.attach(made, store)
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(made))
        val book = store.books.single().book
        assertNotNull("그림 정보 없이 저장됐다", book.visuals)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val director = Director(scope)
        val frozenBefore = motionFrozen
        try {
            motionFrozen = true
            val page = androidx.compose.runtime.mutableStateOf(1)
            compose.setContent { SavedStoryView(director, Stage.SavedStory(book, page.value)) }
            for (p in listOf(1, 2, 4)) {
                page.value = p
                compose.waitForIdle()
                compose.onNodeWithText(book.pages[p - 1].caption, substring = true).assertExists()
                compose.onRoot().captureRoboImage("build/review/coop_saved_book_p$p.png",
                    roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            }
        } finally {
            motionFrozen = frozenBefore
            scope.cancel()
        }
    }
}
