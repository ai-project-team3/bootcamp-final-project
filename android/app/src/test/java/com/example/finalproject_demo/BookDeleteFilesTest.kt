package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.sound.ChildSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 「만든 책 지우기」 (#223 · 10-06 종훈) — 부모 모드에서 책을 빼면 **그 책만 쓰던 것**은 폰에서 다 지우고,
 * 다른 책이 같이 쓰는 그림은 남긴다. 모드마다: 동화(그림 · 아이가 녹음한 소리) · 그림일기(오또 그림 · 쪽 목소리) ·
 * 같이 만들기(그림 · 「다녀온 뒤」 상자) · 세 모드 공통(그 책의 부모 리포트).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookDeleteFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var sounds: File

    @Before fun clean() {
        listOf("story_books", "coop_books", "diary_books", "session_reports").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        listOf("story_images", "diary_images", "diary_voices").forEach { File(context.filesDir, it).deleteRecursively() }
        sounds = File(context.cacheDir, "sounds_test").apply { deleteRecursively(); mkdirs() }
        ChildSound.root = sounds
        SessionReports.clear()
    }

    @After fun done() = SessionReports.clear()

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun exists(ref: String) = File(ref.removePrefix("local:")).exists()

    private fun story(id: String, bg: String, sound: Boolean = false) = SavedStoryBook(
        id, "동화 $id", "A", bg, listOf(SavedStoryPage(PageKind.COVER, "첫 쪽")),
        soundClipId = if (sound) "clip-$id" else null, madeAt = "2026-10-06",
    )

    private class MemoryStories(val books: MutableList<SavedStoryBook>) : StoryBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedStoryBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.id == id }
    }

    private class MemoryCoop(val books: MutableList<SavedCoopBook>) : CoopBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedCoopBook) { books.add(0, book) }
        override fun delete(id: String) = books.removeAll { it.book.id == id }
    }

    private class MemoryPlan(var extras: CoopPlanExtras) : CoopPlanStore {
        override fun load(): Pair<CoopPick?, List<String>>? = null
        override fun save(pick: CoopPick?, questions: List<String>) {}
        override fun loadExtras() = extras
        override fun saveExtras(extras: CoopPlanExtras) { this.extras = extras }
    }

    private fun director(stories: List<SavedStoryBook>, coop: List<SavedCoopBook> = emptyList()): Director {
        val d = Director(CoroutineScope(SupervisorJob()), MemoryStories(stories.toMutableList()), StoryImageStore(context))
        CoopShelf.attach(d.s, MemoryCoop(coop.toMutableList()))
        DiaryShelf.attach(d.s, LocalDiaryBookStore(context))
        d.s.scene = Scene.PARENT
        return d
    }

    @Test
    fun aRemovedStoryTakesItsOwnPictureAndSoundButNotAPictureAnotherBookUses() {
        val images = StoryImageStore(context)
        val own = images.save(png())!!
        val shared = images.save(png())!!
        val d = director(listOf(story("s1", own, sound = true), story("s2", shared, sound = true), story("s3", shared)))
        val s1Sound = File(sounds, "books/s1").apply { mkdirs(); File(this, "clip-s1.wav").writeBytes(byteArrayOf(1)) }
        val s2Sound = File(sounds, "books/s2").apply { mkdirs(); File(this, "clip-s2.wav").writeBytes(byteArrayOf(1)) }

        assertTrue(d.removeShelfBook(StoryMode.STORY, "s1"))
        assertFalse("its picture stayed", exists(own))
        assertFalse("the child's recording stayed", s1Sound.exists())
        assertTrue("another book's recording went", s2Sound.exists())

        assertTrue(d.removeShelfBook(StoryMode.STORY, "s2"))
        assertTrue("a picture s3 still uses was deleted", exists(shared))
        assertFalse(s2Sound.exists())
    }

    @Test
    fun aRemovedDiaryTakesItsOttoPicturesPageVoicesAndCover() {
        val store = LocalDiaryBookStore(context)
        fun diary(id: String) = SavedDiaryBook(
            id, "일기 $id", "2026-10-06", 3, DiaryBookInput(lines = mapOf("place" to "놀이터")),
            listOf(DiaryPiece(0, emptyList(), "뽀삐", PieceLook.OTTO, byteArrayOf(1, 2, 3))),
        )
        store.save(diary("d1")); store.save(diary("d2"))
        store.keepVoice("d1", "나는 오늘 놀이터에 갔어요.", byteArrayOf(9))
        store.keepVoice("d2", "나는 오늘 놀이터에 갔어요.", byteArrayOf(9))
        val d = director(emptyList())
        d.s.diaryCovers[DIARY_SHELF_ID + "d1"] = DiaryCover(emptyList(), 1f)
        fun files(dir: String, id: String) = File(context.filesDir, dir).listFiles().orEmpty().filter { it.name.startsWith("${id}_") }

        assertTrue(d.removeShelfBook(StoryMode.DIARY, "d1"))
        assertTrue("Otto pictures stayed", files("diary_images", "d1").isEmpty())
        assertTrue("page voices stayed", files("diary_voices", "d1").isEmpty())
        assertNull("the cover stayed", d.s.diaryCovers[DIARY_SHELF_ID + "d1"])
        assertEquals("the other diary lost its files", 1, files("diary_images", "d2").size)
        assertEquals(1, files("diary_voices", "d2").size)
    }

    @Test
    fun aRemovedCoopBookTakesItsPictureAndLeavesTheAfterBox() {
        val images = StoryImageStore(context)
        val c1Bg = images.save(png())!!
        val c2Bg = images.save(png())!!
        val d = director(emptyList(), listOf(
            SavedCoopBook(story("c1", c1Bg), null), SavedCoopBook(story("c2", c2Bg), null),
        ))
        val after = CoopAfter("place", "동물원", "c1", "동물원에 가요", "2026-10-06")
        val other = CoopAfter("place", "바다", "c2", "바다에 가요", "2026-10-06")
        val plan = MemoryPlan(CoopPlanExtras(before = "c1", after = listOf(after, other)))
        CoopPlan.attach(d.s, plan)

        assertTrue(d.removeShelfBook(StoryMode.COOP, "c1"))
        assertFalse("its picture stayed", exists(c1Bg))
        assertTrue("another book's picture went", exists(c2Bg))
        assertEquals("the box still offers the removed book", listOf(other), CoopPlan.after(d.s))
        assertNull("the plan still waits to pair with the removed book", CoopPlan.beforeBookId(d.s))
        assertEquals("kept on the phone too", listOf(other), plan.extras.after)
    }

    @Test
    fun aRemovedBookTakesItsReportAndTodaysRecordWhenItIsTheBookJustMade() {
        val images = StoryImageStore(context)
        val d = director(listOf(story("s1", images.save(png())!!), story("s2", images.save(png())!!)))
        d.s.talk += TalkLine("child", "토끼가 울었어")
        SessionReports.keep(d.s, "s2")
        SessionReports.keep(d.s, "s1")
        assertEquals("s1", d.s.lastReport?.bookId)

        assertTrue(d.removeShelfBook(StoryMode.STORY, "s1"))
        assertNull(SessionReports.of("s1"))
        assertNull("today's record still shows the removed book", d.s.lastReport)
        assertTrue(d.s.talk.isEmpty())
        assertNotNull("another book's report went", SessionReports.of("s2"))
    }
}
