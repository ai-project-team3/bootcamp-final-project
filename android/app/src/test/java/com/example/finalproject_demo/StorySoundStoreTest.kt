package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorySoundStoreTest {
    private val originalRoot = ChildSound.root
    private val originalCapture = ChildSound.capture
    private lateinit var folder: java.io.File
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        folder = Files.createTempDirectory("story_sound_store").toFile()
        ChildSound.root = folder
        ChildSound.capture = { ShortArray(ChildSound.RATE / 2) { if (it % 2 == 0) 6000 else -6000 } }
    }

    @After fun cleanup() {
        ChildSound.capture = originalCapture
        ChildSound.root = originalRoot
        folder.deleteRecursively()
    }

    @Test fun savedSoundSurvivesNewStoryAndAppSessionCleanup() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope, LocalStoryBookStore(context))
            d.s.templateKey = "C"
            val clip = ChildSound.record()!!
            val original = clip.file.readBytes()
            d.s.storySoundClip = clip

            assertTrue(d.saveFinishedStory())
            val saved = LocalStoryBookStore(context).load().single()
            assertEquals(clip.id, saved.soundClipId)
            d.s.resetStory()
            ChildSound.discardSession()

            val reloaded = LocalStoryBookStore(context).load().single()
            val kept = ChildSound.find(reloaded.id, reloaded.soundClipId!!)
            assertNotNull("The completed book must own the recording before session cleanup", kept)
            assertArrayEquals(original, kept!!.file.readBytes())
            assertNull(d.s.storySoundClip)
        } finally { scope.cancel() }
    }

    @Test fun failedBookSaveRetriesWithTheSameSoundAndBookId() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var first = true
        val attemptedIds = mutableListOf<String>()
        val backing = LocalStoryBookStore(context)
        val store = object : StoryBookStore {
            override fun load() = backing.load()
            override fun save(book: SavedStoryBook) {
                attemptedIds += book.id
                if (first) { first = false; error("disk unavailable") }
                backing.save(book)
            }
        }
        try {
            val d = Director(scope, store)
            d.s.templateKey = "C"
            val clip = ChildSound.record()!!
            d.s.storySoundClip = clip
            assertFalse(d.saveFinishedStory())
            assertTrue("A failed save must not add a shelf book", d.s.shelf.isEmpty())
            assertTrue(d.saveFinishedStory())
            val saved = backing.load().single()
            assertEquals(attemptedIds.first(), saved.id)
            assertNotNull(ChildSound.find(saved.id, clip.id))
            assertEquals(1, d.s.shelf.size)
        } finally { scope.cancel() }
    }

    @Test fun startingAnotherStoryDeletesOnlyTheUnkeptRecording() = runBlocking {
        val state = DemoState()
        val clip = ChildSound.record()!!
        state.storySoundClip = clip
        state.resetStory()
        assertFalse("Abandoned story audio must not accumulate in the session folder", clip.file.exists())
        assertNull(state.storySoundClip)
    }

    @Test fun abandoningAFailedBookSaveRemovesItsRecording() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope, failingStore())
            d.s.templateKey = "C"
            d.s.storySoundClip = ChildSound.record()!!
            assertFalse(d.saveFinishedStory())
            val kept = d.s.storySoundClip!!
            d.s.resetStory()
            assertFalse("A failed book must not retain raw audio after abandonment", kept.file.exists())
        } finally { scope.cancel() }
    }

    @Test fun restartingAfterFailedSaveCleansOnlyThePendingStoryRecording() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val otherMode = ChildSound.keep(ChildSound.record()!!, "coop-book")!!
            val d = Director(scope, failingStore())
            d.s.templateKey = "C"
            d.s.storySoundClip = ChildSound.record()!!
            assertFalse(d.saveFinishedStory())
            val orphan = d.s.storySoundClip!!
            ChildSound.discardSession()
            assertTrue(LocalStoryBookStore(context).load().isEmpty())
            assertFalse("Startup must recover an interrupted story save", orphan.file.exists())
            assertTrue("Other modes own their recordings independently", otherMode.file.exists())
        } finally { scope.cancel() }
    }

    @Test fun restartingAfterMetadataWasCommittedPreservesTheRecording() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val backing = LocalStoryBookStore(context)
            val store = object : StoryBookStore {
                override fun load() = backing.load()
                override fun save(book: SavedStoryBook) {
                    backing.save(book)
                    error("process stopped after metadata commit")
                }
            }
            val d = Director(scope, store)
            d.s.templateKey = "C"
            d.s.storySoundClip = ChildSound.record()!!
            assertFalse(d.saveFinishedStory())
            ChildSound.discardSession()
            val saved = LocalStoryBookStore(context).load().single()
            assertNotNull(ChildSound.find(saved.id, saved.soundClipId!!))
        } finally { scope.cancel() }
    }

    private fun failingStore() = object : StoryBookStore {
        override fun load() = emptyList<SavedStoryBook>()
        override fun save(book: SavedStoryBook) { error("disk unavailable") }
    }
}
