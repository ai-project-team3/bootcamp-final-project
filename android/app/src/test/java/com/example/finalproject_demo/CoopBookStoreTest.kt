package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.COOP_SHELF_CAPACITY
import com.example.finalproject_demo.demo.LocalCoopBookStore
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 같이 만들기 책장 저장소 (#83) — 동화 보관함과 따로, 12권까지, 몰래 지우지 않는다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopBookStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        listOf("coop_books", "story_books").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun book(id: String) = SavedStoryBook(id, "책 $id", "space", "bg_space",
        listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요."), SavedStoryPage(PageKind.JOURNEY, "집에 왔어요.")))

    @Test
    fun aSavedBookComesBackAfterARestartNewestFirst() {
        LocalCoopBookStore(context).save(book("a"))
        LocalCoopBookStore(context).save(book("b"))
        val again = LocalCoopBookStore(context).load()
        assertEquals(listOf("b", "a"), again.map { it.id })
        assertEquals(book("a").pages, again.last().pages)
    }

    @Test
    fun coopBooksDoNotGoIntoTheStoryShelf() {
        LocalCoopBookStore(context).save(book("a"))
        assertTrue(LocalStoryBookStore(context).load().isEmpty())
    }

    @Test
    fun aThirteenthBookIsRefusedAndNothingIsDeleted() {
        val store = LocalCoopBookStore(context)
        (1..COOP_SHELF_CAPACITY).forEach { store.save(book("b$it")) }
        val refused = runCatching { store.save(book("new")) }.isFailure
        assertTrue("13권째가 들어갔다", refused)
        assertEquals(COOP_SHELF_CAPACITY, store.load().size)
        assertTrue(store.load().none { it.id == "new" })
    }

    @Test
    fun anUnreadableEntryIsNotOverwritten() {
        val prefs = context.getSharedPreferences("coop_books", Context.MODE_PRIVATE)
        prefs.edit().putString("books", """[{"id":"broken"}]""").commit()
        val refused = runCatching { LocalCoopBookStore(context).save(book("a")) }.isFailure
        assertTrue("읽지 못한 책을 덮어썼다", refused)
        assertEquals("""[{"id":"broken"}]""", prefs.getString("books", null))
    }
}
