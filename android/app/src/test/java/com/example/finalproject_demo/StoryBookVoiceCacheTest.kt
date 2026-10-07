package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryBookVoiceCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun secondReadAfterRestartGeneratesOnlyMissingOrChangedPagesInBothModes() = runBlocking {
        for (mode in listOf(StoryMode.STORY, StoryMode.COOP)) {
            val scope = CoroutineScope(SupervisorJob())
            val store = LocalStoryBookStore(context)
            val book = SavedStoryBook("cache-${mode.name}", "조개", "sea", "bg_sea",
                listOf(SavedStoryPage(PageKind.DEPART, "조개를 찾았어요."),
                    SavedStoryPage(PageKind.TOGETHER, "집에 왔어요.")))
            var calls = 0
            suspend fun read(d: Director, line: String) =
                d.savedBookVoice(book, mode, line) { calls++; byteArrayOf(1, 2, 3) }
            try {
                val first = Director(scope, store)
                store.voices.keep(mode, book.id, book.pages[0].caption, byteArrayOf(9))
                assertArrayEquals(byteArrayOf(9), read(first, book.pages[0].caption))
                read(first, book.pages[1].caption)
                assertEquals("only the absent page needs synthesis", 1, calls)
                val restarted = Director(scope, LocalStoryBookStore(context))
                calls = 0
                book.pages.forEach { read(restarted, it.caption) }
                assertEquals("a second read must not request TTS", 0, calls)
                read(restarted, "새로 바뀐 문장이에요.")
                assertEquals(1, calls)
                read(restarted, "새로 바뀐 문장이에요.")
                assertEquals(1, calls)
            } finally { scope.cancel() }
        }
    }

    @Test fun deletingBooksRemovesOnlyTheirOwnNarrationAndWipeRemovesTheRest() {
        val book = SavedStoryBook("delete-voice", "조개", "sea", "bg_sea",
            listOf(SavedStoryPage(PageKind.TOGETHER, "집에 왔어요.")))
        val story = LocalStoryBookStore(context)
        val coop = LocalCoopBookStore(context)
        story.save(book)
        coop.save(SavedCoopBook(book, null))
        val voices = story.voices
        for (mode in listOf(StoryMode.STORY, StoryMode.COOP)) {
            voices.keep(mode, book.id, "집에 왔어요.", byteArrayOf(1))
        }
        assertTrue(story.delete(book.id))
        assertNull(voices.read(StoryMode.STORY, book.id, "집에 왔어요."))
        assertNotNull(voices.read(StoryMode.COOP, book.id, "집에 왔어요."))
        assertTrue(coop.delete(book.id))
        assertNull(voices.read(StoryMode.COOP, book.id, "집에 왔어요."))
        voices.keep(StoryMode.STORY, "other", "다른 책", byteArrayOf(2))
        assertTrue(com.example.finalproject_demo.ui.shell.LocalWipe.wipe(context))
        assertFalse(File(context.filesDir, "book_voices").exists())
    }

    @Test fun shelvingKeepsPreviouslyGeneratedPageVoices() {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope, LocalStoryBookStore(context))
        try {
            d.s.templateKey = "E"
            d.s.title = "조개 이야기"
            d.s.useGeneratedStory(List(6) { "조개 ${it + 1}개를 찾았어요." })
            (1..6).forEach { d.rememberVoice(d.s.bookCaption(it), byteArrayOf(1, 2, it.toByte())) }
            assertTrue(d.saveFinishedStory())
            assertEquals(6, File(context.filesDir, "book_voices").walkTopDown().count { it.extension == "mp3" })
        } finally { scope.cancel() }
    }
}
