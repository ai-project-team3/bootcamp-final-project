package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.COOP_SHELF_CAPACITY
import com.example.finalproject_demo.demo.LocalCoopBookStore
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.PageKind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.CoopBookSnapshot
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.SavedCoopBook
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.SavedStoryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun book(id: String) = SavedCoopBook(
        SavedStoryBook(id, "책 $id", "space", "bg_space",
            listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요."), SavedStoryPage(PageKind.JOURNEY, "집에 왔어요."))),
        CoopBookSnapshot(
            "지호", mapOf("place" to "사자 우리", "problem" to "사자가 나왔어"), "사자 우리", "엄마", "{친구1}",
            listOf(Stroke(Color(0xFF3366FF.toInt()), listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.25f)), 0.02f)), 1.5f,
            "mom", "엄마", null, "또 가고 싶어요", "bg_zoo",
            fields = mapOf("place" to "사자 우리", "reaction" to "깜짝 놀랐어"),
            slotBy = mapOf("place" to "child", "solution" to "mascot"),
            feelings = listOf("놀랐"),
        ),
    )

    @Test
    fun aSavedBookComesBackAfterARestartNewestFirst() {
        LocalCoopBookStore(context).save(book("a"))
        LocalCoopBookStore(context).save(book("b"))
        val again = LocalCoopBookStore(context).load()
        assertEquals(listOf("b", "a"), again.map { it.book.id })
        assertEquals(book("a").book.pages, again.last().book.pages)
        // 다시 그리는 재료(이야기 칸 · 화이트보드 그림 · 함께한 사람 · 배경)도 그대로 돌아온다
        assertEquals(book("a").snapshot, again.last().snapshot)
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
        assertTrue(store.load().none { it.book.id == "new" })
    }

    /** 협업모드_확장_설계 §2-3 ① — 고른 이야기 · 짝이 다시 켜도 남는다 */
    @Test
    fun thePickedStoryAndThePairSurviveARestart() {
        val store = LocalCoopBookStore(context)
        val before = book("before").let { it.copy(snapshot = it.snapshot!!.copy(pick = CoopPick("place", "동물원", "soon"))) }
        val after = book("after").let { it.copy(snapshot = it.snapshot!!.copy(pick = CoopPick("place", "동물원", "done"))) }
        store.save(before); store.save(after)
        assertTrue(store.linkPair("before", "after"))
        val again = LocalCoopBookStore(context).load().associateBy { it.book.id }
        assertEquals(CoopPick("place", "동물원", "soon"), again.getValue("before").snapshot!!.pick)
        assertEquals("after", again.getValue("before").snapshot!!.pairId)
        assertEquals("before", again.getValue("after").snapshot!!.pairId)
        // 이유를 안 고른 이야기(상상)도 null 그대로 돌아온다
        store.save(book("dream").let { it.copy(snapshot = it.snapshot!!.copy(pick = CoopPick("job", "소방관", null))) })
        assertNull(LocalCoopBookStore(context).load().first().snapshot!!.pick!!.reason)
    }

    /** 10-06 전 저장 모양 — 두 키가 아예 없어도 읽고, 고른 이야기 모름 · 짝 없음이다 */
    @Test
    fun aBookSavedBeforeTheseFieldsReadsAsNoPickAndNoPair() {
        LocalCoopBookStore(context).save(book("old"))
        val prefs = context.getSharedPreferences("coop_books", Context.MODE_PRIVATE)
        val old = org.json.JSONArray(prefs.getString("books", null)).also { a ->
            a.getJSONObject(0).getJSONObject("coop").apply { remove("pick"); remove("pairId") }
        }
        prefs.edit().putString("books", old.toString()).commit()
        val snap = LocalCoopBookStore(context).load().single().snapshot!!
        assertNull(snap.pick); assertNull(snap.pairId)
        assertEquals(book("old").snapshot!!.slots, snap.slots)
    }

    @Test
    fun aPairNeedsBothBooksAndChangesNothingOtherwise() {
        val store = LocalCoopBookStore(context)
        store.save(book("a"))
        val prefs = context.getSharedPreferences("coop_books", Context.MODE_PRIVATE)
        val before = prefs.getString("books", null)
        assertFalse(store.linkPair("a", "gone"))
        assertFalse(store.linkPair("a", "a"))
        assertEquals(before, prefs.getString("books", null))
    }

    @Test
    fun theShelfFindsThePairAndForgetsItWhenOneIsRemoved() {
        val store = LocalCoopBookStore(context)
        store.save(book("before")); store.save(book("after"))
        val s = DemoState()
        CoopShelf.attach(s, store)
        assertNull(CoopShelf.pairOf(s, "before"))
        assertTrue(CoopShelf.linkPair(s, "before", "after"))
        assertEquals("after", CoopShelf.pairOf(s, "before")?.id)
        assertEquals("before", CoopShelf.pairOf(s, "after")?.id)
        assertTrue(CoopShelf.delete(s, "after"))
        assertNull("지운 책을 짝으로 돌려줬다", CoopShelf.pairOf(s, "before"))
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
