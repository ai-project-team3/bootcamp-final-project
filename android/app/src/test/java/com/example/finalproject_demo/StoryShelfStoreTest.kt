package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryShelfStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun freshStore(): LocalStoryBookStore {
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        return LocalStoryBookStore(context)
    }
    private fun book(id: String) = SavedStoryBook(id, "이야기 $id", "sea", "bg_sea",
        listOf(SavedStoryPage(PageKind.TOGETHER, "함께 놀았어요.")))

    @Test fun parentDeletionFreesExactlyOnePlaceAndSurvivesReopening() {
        val store = freshStore()
        repeat(STORY_SHELF_CAPACITY) { store.save(book("$it")) }
        assertEquals(12, store.count())
        assertTrue(store.delete("4"))
        assertEquals(11, LocalStoryBookStore(context).count())
        assertFalse(store.delete("not-a-book"))
        store.save(book("new"))
        assertEquals(12, store.count())
        assertFalse(store.load().any { it.id == "4" })
        assertEquals((0 until 12).map { "$it" }.filterNot { it == "4" }.toSet(),
            store.load().map { it.id }.filterNot { it == "new" }.toSet())
    }

    @Test fun damagedMetadataCannotBecomeAnEmptyShelfOrBeErasedByDeletion() {
        val store = freshStore()
        store.save(book("kept"))
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        val raw = JSONArray(prefs.getString("books", null)).put(JSONObject().put("id", "broken")).toString()
        prefs.edit().putString("books", raw).commit()
        assertTrue(runCatching { store.count() }.isFailure)
        assertTrue(runCatching { store.delete("kept") }.isFailure)
        assertEquals(raw, prefs.getString("books", null))
    }

    @Test fun legacyBooksReopenAndExposeModeAndDateWithoutInventingADate() {
        val store = freshStore()
        store.save(book("legacy"))
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        val json = JSONArray(prefs.getString("books", null))
        json.getJSONObject(0).remove("madeAt")
        json.getJSONObject(0).remove("mode")
        prefs.edit().putString("books", json.toString()).commit()
        val legacy = store.load().single()
        assertEquals("story", legacy.mode)
        assertEquals("", legacy.madeAt)
        val state = DemoState().apply { templateKey = "C" }
        val complete = state.completedStoryBook()!!
        store.save(complete)
        assertEquals(complete.madeAt, store.load().first().madeAt)
        assertTrue(complete.madeAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
