package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryCapacityTest {
    private fun fullStore(): LocalStoryBookStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        return LocalStoryBookStore(context).apply {
            repeat(12) { i -> save(SavedStoryBook("book-$i", "동화 $i", "sea", "bg_sea",
                listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요.")))) }
        }
    }

    @Test fun theThirteenthStoryCannotDeleteOrAddABookWithoutAChoice() {
        val books = fullStore()
        val original = books.load()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, books)
            d.s.templateKey = "C"
            assertFalse("a full shelf requires the child's choice", d.saveFinishedStory())
            assertEquals(original, books.load())
            assertEquals(12, d.s.shelf.size)
        } finally { scope.cancel() }
    }

    @Test fun onlyTheChosenBookIsReplacedAndReloadingStillFindsTwelve() {
        val books = fullStore()
        val original = books.load()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, books)
            d.s.templateKey = "C"
            assertFalse(d.saveFinishedStory("not-a-book"))
            assertEquals(original, books.load())
            assertTrue(d.saveFinishedStory("book-5"))
            val reloaded = books.load()
            assertEquals(12, reloaded.size)
            assertEquals(original.filterNot { it.id == "book-5" }, reloaded.drop(1))
            assertNull(d.savedStory("book-5"))
            assertEquals(12, d.s.shelf.size)
            assertTrue("saving the same completed story again is idempotent", d.saveFinishedStory())
            assertEquals(reloaded, books.load())
            assertEquals(12, d.s.shelf.size)
        } finally { scope.cancel() }
    }

    @Test fun cancelAndDeclinedConfirmationLeaveEveryBookUntouched() = runBlocking {
        val books = fullStore()
        val original = books.load()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, books)
        d.s.speed = 0.01
        d.s.templateKey = "C"
        try {
            val save = async { d.saveStoryWithChoice() }
            withTimeout(5_000) {
                while (d.s.stage !is Stage.CardsRow) delay(5)
                d.send(Reply.Tapped("replace:choose", "고르기"))
                while (d.s.stage !is Stage.Confirm) delay(5)
                d.send(Reply.Tapped("no", "다시 고르기"))
                while (d.s.stage !is Stage.CardsRow) delay(5)
                d.send(Reply.Tapped("replace:cancel", "취소"))
                assertFalse(save.await())
            }
            assertEquals(original, books.load())
        } finally { scope.cancel() }
    }

    @Test fun failedReplacementDoesNotRemoveTheChosenStory() {
        val books = fullStore()
        val original = books.load()
        val failing = object : StoryBookStore {
            override fun load() = books.load()
            override fun save(book: SavedStoryBook) = books.save(book)
            override fun replace(book: SavedStoryBook, oldId: String) { error("disk unavailable") }
        }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, failing)
            d.s.templateKey = "C"
            assertFalse(d.saveFinishedStory("book-5"))
            assertEquals(original, books.load())
            assertNotNull(d.savedStory("book-5"))
            assertEquals(12, d.s.shelf.size)
        } finally { scope.cancel() }
    }

    @Test fun replacementDeletesOnlyTheSelectedBooksRecordingAfterSaving() {
        val oldRoot = ChildSound.root
        val folder = Files.createTempDirectory("story-replacement").toFile()
        ChildSound.root = folder
        val scope = CoroutineScope(SupervisorJob())
        try {
            val books = fullStore()
            val selected = File(folder, "books/book-5/old.wav").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            val retained = File(folder, "books/book-6/other.wav").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
            val d = Director(scope, books)
            d.s.templateKey = "C"
            assertFalse(d.saveFinishedStory())
            assertTrue(selected.exists())
            assertTrue(d.saveFinishedStory("book-5"))
            assertFalse(selected.exists())
            assertTrue(retained.exists())
            assertEquals(12, books.load().size)
        } finally { scope.cancel(); ChildSound.root = oldRoot; folder.deleteRecursively() }
    }

    @Test fun pickingAnotherCoverAndConfirmingReplacesThatBook() = runBlocking {
        val books = fullStore()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, books)
        d.s.speed = 0.01
        d.s.templateKey = "C"
        try {
            val save = async { d.saveStoryWithChoice() }
            withTimeout(5_000) {
                while (d.s.stage !is Stage.CardsRow) delay(5)
                d.send(Reply.Tapped("replace:next", "다음 책"))
                while ((d.s.stage as? Stage.CardsRow)?.cards?.firstOrNull()?.label != "동화 10") delay(5)
                d.send(Reply.Tapped("replace:choose", "고르기"))
                while (d.s.stage !is Stage.Confirm) delay(5)
                d.send(Reply.Tapped("ok", "바꾸기"))
                assertTrue(save.await())
            }
            assertNull(d.savedStory("book-10"))
            assertNotNull(d.savedStory("book-11"))
            assertEquals(12, books.load().size)
        } finally { scope.cancel() }
    }
}
