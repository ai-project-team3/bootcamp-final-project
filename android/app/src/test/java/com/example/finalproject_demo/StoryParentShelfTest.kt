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
class StoryParentShelfTest {
    private fun fullStore(): LocalStoryBookStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        return LocalStoryBookStore(context).apply {
            repeat(12) { i -> save(SavedStoryBook("book-$i", "동화 $i", "sea", "bg_sea",
                listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요.")))) }
        }
    }

    @Test fun fullShelfNeverAsksTheChildToReplaceABook() {
        val books = fullStore()
        val original = books.load()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, books)
            d.s.templateKey = "C"
            assertEquals(12, d.storyBookCount())
            assertFalse(d.saveFinishedStory())
            assertEquals(original, books.load())
            assertFalse(d.s.stage is Stage.CardsRow)
            assertEquals(12, d.storyBooks().size)
        } finally { scope.cancel() }
    }

    @Test fun parentDeletionUpdatesOnlyTheSelectedBookAndItsRecording() {
        val oldRoot = ChildSound.root
        val folder = Files.createTempDirectory("story-parent-delete").toFile()
        ChildSound.root = folder
        val scope = CoroutineScope(SupervisorJob())
        try {
            val books = fullStore()
            val selected = File(folder, "books/book-5/old.wav").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            val retained = File(folder, "books/book-6/other.wav").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
            val d = Director(scope, books)
            assertFalse(d.deleteStoryBook("not-a-book"))
            assertFalse("a child scene cannot delete a book", d.deleteStoryBook("book-5"))
            d.s.scene = Scene.PARENT
            assertTrue(d.deleteStoryBook("book-5"))
            assertEquals(11, d.storyBookCount())
            assertEquals(11, d.s.shelf.size)
            assertNull(d.savedStory("book-5"))
            assertFalse(selected.exists())
            assertTrue(retained.exists())
            d.s.templateKey = "C"
            assertTrue(d.saveFinishedStory())
            assertEquals(12, books.count())
            assertTrue(d.saveFinishedStory())
            assertEquals(12, d.s.shelf.size)
        } finally { scope.cancel(); ChildSound.root = oldRoot; folder.deleteRecursively() }
    }

    @Test fun persistenceFailureKeepsTheBookShelfAndRecording() {
        val books = fullStore()
        val original = books.load()
        val failing = object : StoryBookStore {
            override fun load() = books.load()
            override fun save(book: SavedStoryBook) = books.save(book)
            override fun delete(id: String): Boolean { error("disk unavailable") }
        }
        val oldRoot = ChildSound.root
        val folder = Files.createTempDirectory("story-failed-delete").toFile()
        ChildSound.root = folder
        val scope = CoroutineScope(SupervisorJob())
        try {
            val recorded = File(folder, "books/book-5/old.wav").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            val d = Director(scope, failing)
            d.s.scene = Scene.PARENT
            assertFalse(d.deleteStoryBook("book-5"))
            assertEquals(original, books.load())
            assertEquals(12, d.s.shelf.size)
            assertTrue(recorded.exists())
        } finally { scope.cancel(); ChildSound.root = oldRoot; folder.deleteRecursively() }
    }
}
