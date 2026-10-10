package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StoryBookReadingTest {
    private suspend fun waitFor(predicate: () -> Boolean) {
        withTimeout(3_000) { while (!predicate()) delay(5) }
    }

    @Test
    fun theSpeakerInTheMakingBookActuallyRepeatsTheCurrentCaption() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false; s.templateKey = "C" }
        try {
            d.go(Scene.BOOK)
            waitFor { d.s.stage is Stage.BookPage && d.s.buttons.isNotEmpty() }
            d.send(Reply.Tapped("next", "다음"))
            waitFor { (d.s.stage as? Stage.BookPage)?.index == 1 }
            delay(30)
            val caption = d.s.bookCaption(1)
            val lineId = d.s.lineId
            d.send(Reply.Tapped("speak", "낭독"))
            delay(100)
            assertTrue("the speaker must call narration, not just log the tap", d.s.lineId > lineId)
            assertEquals(caption, d.s.line)
        } finally { scope.cancel() }
    }

    @Test
    fun aSavedStoryReadsItsCoverAndPagesAndCanRepeatThem() = runBlocking {
        val book = SavedStoryBook("reading-book", "우리가 만든 이야기", "space", "bg_space",
            listOf(SavedStoryPage(PageKind.DEPART, "우리 인형은 길을 떠났어요."),
                SavedStoryPage(PageKind.TOGETHER, "친구와 함께 집으로 돌아왔어요.")))
        val store = object : StoryBookStore {
            override fun load() = listOf(book)
            override fun save(book: SavedStoryBook) = error("Reading must not save")
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, store).apply {
            s.speed = 0.01; s.timerOn = false; s.mode = StoryMode.COOP
        }
        try {
            d.go(Scene.SHELF)
            waitFor { d.s.stage is Stage.Shelf }
            delay(30)
            d.send(Reply.Tapped("book", book.id))
            waitFor { d.s.stage is Stage.SavedStory }
            assertEquals("the reopened story needs its own voice mode", StoryMode.STORY, d.s.mode)
            assertEquals("『${book.title}』", d.s.line)
            for (page in 1..book.pages.size) {
                d.send(Reply.Tapped("next", "다음"))
                waitFor { (d.s.stage as? Stage.SavedStory)?.index == page }
                assertEquals(book.pages[page - 1].caption, d.s.line)
                val lineId = d.s.lineId
                d.send(Reply.Tapped("speak", "낭독"))
                delay(50)
                assertTrue("repeat must read the stored caption", d.s.lineId > lineId)
            }
            val queuedVoice = d.queueVoice(Job())
            d.send(Reply.Tapped("close", "책장"))
            waitFor { d.s.stage is Stage.Shelf }
            assertFalse("closing the reader must stop its narration", queuedVoice.isActive)
            assertEquals("reading must restore the previous conversation mode", StoryMode.COOP, d.s.mode)
            assertEquals(book, d.savedStory(book.id))
        } finally { scope.cancel() }
    }
}
