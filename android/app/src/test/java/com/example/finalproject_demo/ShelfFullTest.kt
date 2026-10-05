package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 책장이 꽉 차면 시작 전에 알린다 (10-02 조장 #80 · `guidelines/3` §3-5).
 * 모드를 고를 때 12권이면 들어가지 않고 알림 → [닫기] 면 방 · [부모 모드로] → PIN → 그 모드 책장 정리 →
 * 한 권 빼면 다시 들어갈 수 있다 · 다른 모드는 영향 없음. 빼기는 부모 모드에서만, 저장소에서 먼저 지운다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShelfFullTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clean() {
        listOf("story_books", "coop_books", "diary_books").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.filesDir, "story_images").deleteRecursively()
    }

    private fun story(i: Int, bg: String = "bg_park", madeAt: String = "2026-10-0${i % 9 + 1}") = SavedStoryBook(
        "s$i", "동화 $i", "A", bg, listOf(SavedStoryPage(PageKind.COVER, "첫 쪽"), SavedStoryPage(PageKind.DEPART, "둘째 쪽")), madeAt = madeAt,
    )

    private class MemoryStories(val books: MutableList<SavedStoryBook>) : StoryBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedStoryBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.id == id }
    }

    private class MemoryDiaries(val books: MutableList<SavedDiaryBook>) : DiaryBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedDiaryBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.id == id }
    }

    private class MemoryCoop(val books: MutableList<SavedCoopBook>, val unreadable: Boolean = false) : CoopBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedCoopBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.book.id == id }
        override fun imageReferences(): Set<String>? = if (unreadable) null else super.imageReferences()
    }

    private fun diary(i: Int) = SavedDiaryBook("d$i", "일기 $i", "2026-10-01", 3, DiaryBookInput(lines = mapOf("place" to "놀이터")), emptyList())

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(stories: Int = 0, diaries: Int = 0, block: suspend (Director, MemoryStories) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val store = MemoryStories((1..stories).map { story(it) }.toMutableList())
        val d = Director(CoroutineScope(coroutineContext + sup), store)
        d.s.speed = 0.01
        DiaryShelf.attach(d.s, MemoryDiaries((1..diaries).map { diary(it) }.toMutableList()))
        CoopShelf.attach(d.s, MemoryCoop(mutableListOf()))
        try { block(d, store) } finally { sup.cancel() }
    }

    private fun Director.tap(value: String) = send(Reply.Tapped(value, value))

    @Test
    fun aFullStoryShelfWarnsInsteadOfStartingAndCloseGoesBackToTheRoom() = run(stories = 12) { d, _ ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(await { s.stage == Stage.Adult } != null)
        val used = s.usedToday
        d.tap("start")
        assertTrue("12권인데 알림이 안 떴다", await { s.shelfFull == StoryMode.STORY } != null)
        assertEquals(Scene.ADULT, s.scene)
        d.tap("shelf:close")
        assertTrue("[닫기] 뒤에 알림이 남았다", await { s.shelfFull == null && s.stage == Stage.Adult } != null)
        assertEquals("시작하지 않았는데 하루 별을 썼다", used, s.usedToday)
        assertEquals(12, d.storyBookCount())
    }

    @Test
    fun parentModeOpensTheFullModeShelfAndOneRemovalLetsTheChildStart() = run(stories = 12) { d, store ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(await { s.stage == Stage.Adult } != null)
        d.tap("start")
        assertTrue(await { s.shelfFull == StoryMode.STORY } != null)
        d.tap("shelf:tidy")
        assertTrue("PIN 이 안 떴다", await { s.stage is Stage.Pin } != null)
        d.tap("pin:ok")
        assertTrue("책장 정리로 안 갔다 — ${s.stage}", await { s.stage == Stage.Parent("shelf") } != null)
        assertEquals(StoryMode.STORY, s.shelfTidyMode)
        assertEquals(12, d.shelfEntries(StoryMode.STORY).size)

        d.tap(shelfDeleteSignal(StoryMode.STORY, "s3"))
        assertTrue("빼지 못했다", await { d.storyBookCount() == 11 } != null)
        assertTrue("저장소에서 먼저 지우지 않았다", store.books.none { it.id == "s3" })
        assertTrue("책장 그림에 남았다", s.shelf.none { it.savedStoryId == "s3" })

        d.tap("home")
        assertTrue(await { s.stage == Stage.Adult } != null)
        assertNull("부모 모드를 나왔는데 정리 모드가 남았다", s.shelfTidyMode)
        d.tap("start")
        assertTrue("한 권 뺐는데도 시작하지 못했다 — ${s.scene}", await { s.scene != Scene.ADULT } != null)
        assertNull(s.shelfFull)
    }

    @Test
    fun aFullDiaryShelfBlocksOnlyTheDiary() = run(diaries = 12) { d, _ ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(await { s.stage == Stage.Adult } != null)
        d.tap("diary")
        assertTrue(await { s.shelfFull == StoryMode.DIARY } != null)
        d.tap("shelf:close")
        assertTrue(await { s.shelfFull == null && s.stage == Stage.Adult } != null)
        d.tap("start")
        assertTrue("일기 책장이 찼다고 동화까지 막았다", await { s.scene != Scene.ADULT } != null)
    }

    @Test
    fun booksAreRemovedOnlyInParentMode() = run(stories = 2, diaries = 2) { d, _ ->
        assertFalse(d.removeShelfBook(StoryMode.STORY, "s1"))
        assertFalse(d.removeShelfBook(StoryMode.DIARY, "d1"))
        assertEquals(2, d.storyBookCount())
        assertEquals(2, d.shelfCount(StoryMode.DIARY))
    }

    @Test
    fun aRemovedDiaryLeavesTheShelfAndItsCover() = run(diaries = 3) { d, _ ->
        val s = d.s
        d.go(Scene.ADULT)
        assertTrue(await { s.stage == Stage.Adult } != null)
        d.tap("shelf:tidy")
        assertTrue(await { s.stage is Stage.Pin } != null)
        d.tap("pin:ok")
        assertTrue(await { s.scene == Scene.PARENT } != null)
        d.tap(shelfDeleteSignal(StoryMode.DIARY, "d2"))
        assertTrue(await { d.shelfCount(StoryMode.DIARY) == 2 } != null)
        assertTrue(s.shelf.none { it.savedStoryId == DIARY_SHELF_ID + "d2" })
        assertEquals(listOf("d1", "d3"), d.shelfEntries(StoryMode.DIARY).map { it.id }.sorted())
    }

    // ── 저장소 ─────────────────────────────────────────────

    @Test
    fun storyStoreKeepsMadeAtAndDeletesOneBook() {
        val store = LocalStoryBookStore(context)
        store.save(story(1))
        store.save(story(2))
        assertEquals(2, store.count())
        assertEquals("2026-10-03", store.load().first { it.id == "s2" }.madeAt)
        assertTrue(store.delete("s1"))
        assertFalse("없는 책을 지웠다고 했다", store.delete("s1"))
        assertEquals(listOf("s2"), LocalStoryBookStore(context).load().map { it.id })
    }

    @Test
    fun aStoryBookSavedBeforeMadeAtStillLoads() {
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().putString("books",
            """[{"id":"old","title":"옛 책","themeKey":"A","bgName":"bg_park","pages":[{"kind":"COVER","caption":"첫 쪽"}],"visuals":null,"soundClipId":null}]""",
        ).commit()
        val book = LocalStoryBookStore(context).load().single()
        assertEquals("", book.madeAt)
    }

    @Test
    fun coopStoreDeletesOneBookAndKeepsMadeAt() {
        val store = LocalCoopBookStore(context)
        store.save(SavedCoopBook(story(1).copy(id = "c1"), null))
        store.save(SavedCoopBook(story(2).copy(id = "c2"), null))
        assertEquals("2026-10-03", store.load().first { it.book.id == "c2" }.book.madeAt)
        assertTrue(store.delete("c1"))
        assertEquals(listOf("c2"), LocalCoopBookStore(context).load().map { it.book.id })
    }

    @Test
    fun diaryStoreDeletesTheBookAndItsOttoPictures() {
        val store = LocalDiaryBookStore(context)
        val piece = DiaryPiece(0, emptyList(), "뽀삐", PieceLook.OTTO, byteArrayOf(1, 2, 3))
        store.save(diary(1).copy(pieces = listOf(piece)))
        store.save(diary(2))
        val pngs = File(context.filesDir, "diary_images")
        assertTrue(pngs.listFiles()!!.any { it.name.startsWith("d1_") })
        assertTrue(store.delete("d1"))
        assertEquals(listOf("d2"), LocalDiaryBookStore(context).load().map { it.id })
        assertTrue("오또 그림이 남았다", pngs.listFiles().orEmpty().none { it.name.startsWith("d1_") })
    }

    // ── 그림 정리 (#61) ─────────────────────────────────────

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun imageCleanupKeepsStoryAndCoopPicturesAndDropsOrphans() {
        val images = StoryImageStore(context)
        val storyBg = images.save(png())!!
        val coopBg = images.save(png())!!
        val orphan = images.save(png())!!
        val d = Director(CoroutineScope(SupervisorJob()), MemoryStories(mutableListOf(story(1, bg = storyBg))), images)
        CoopShelf.attach(d.s, MemoryCoop(mutableListOf(SavedCoopBook(story(2, bg = coopBg).copy(id = "c2"), null))))
        d.recoverStoryImages()
        fun exists(ref: String) = File(ref.removePrefix("local:")).exists()
        assertTrue("동화 책 그림을 지웠다", exists(storyBg))
        assertTrue("같이 만들기 책 그림을 지웠다", exists(coopBg))
        assertFalse("아무 책도 안 쓰는 그림이 남았다", exists(orphan))
    }

    @Test
    fun imageCleanupStopsWhenCoopBooksCannotBeRead() {
        val images = StoryImageStore(context)
        val coopBg = images.save(png())!!
        val d = Director(CoroutineScope(SupervisorJob()), MemoryStories(mutableListOf()), images)
        CoopShelf.attach(d.s, MemoryCoop(mutableListOf(), unreadable = true))
        d.recoverStoryImages()
        assertTrue("같이 만들기 책을 못 읽었는데 그림을 지웠다", File(coopBg.removePrefix("local:")).exists())
    }

    @Test
    fun deleteSignalsRoundTrip() {
        assertEquals(StoryMode.COOP to "a:b", parseShelfDelete(shelfDeleteSignal(StoryMode.COOP, "a:b")))
        assertNull(parseShelfDelete("shelf:del:STORY:"))
        assertNull(parseShelfDelete("shelf:del:NOPE:x"))
    }
}
