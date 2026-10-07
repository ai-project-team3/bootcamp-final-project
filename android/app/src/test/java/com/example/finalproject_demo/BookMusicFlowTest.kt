package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BgmMood
import com.example.finalproject_demo.demo.COOP_SHELF_ID
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.TEMPLATES
import com.example.finalproject_demo.demo.bgmBookKey
import com.example.finalproject_demo.demo.completedStoryBook
import com.example.finalproject_demo.demo.moodOf
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.storyBookKey
import com.example.finalproject_demo.demo.trackOf
import com.example.finalproject_demo.net.Bgm
import com.example.finalproject_demo.net.BgmChannel
import com.example.finalproject_demo.net.BgmOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 동화책을 읽는 동안 쪽마다 그 분위기의 곡 · 끝 화면은 밤 · 나가면 끈다 · 다시 읽어도 같은 곡 (#221) */
class BookMusicFlowTest {
    @Before fun fakeOut() {
        Bgm.resetForTest()
        Bgm.output = BgmOutput { _ -> object : BgmChannel {
            override val durationMs = 60_000L; override val positionMs = 0L
            override fun start() {}; override fun pause() {}; override fun setVolume(v: Float) {}; override fun release() {}
        } }
        Bgm.resetMixerForTest()
    }
    @After fun reset() = Bgm.resetForTest()

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean) = withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel() }
    }

    private suspend fun Director.openBook(pages: Int = 6) {
        s.mode = StoryMode.STORY
        s.storyCaptions = (1..pages).map { "아이의 이야기 $it 쪽이에요." }
        go(Scene.BOOK)
        assertNotNull("책 화면이 열리지 않았다", await { s.stage is Stage.BookPage })
    }

    /** 감독은 말이 끝나기 전 입력을 버린다 — 쪽이 바뀔 때까지 누른다 */
    private suspend fun Director.next() {
        val before = s.bookPage; val scene = s.scene
        assertNotNull("다음 쪽으로 넘기지 못했다", await { send(Reply.Tapped("next", "다음")); Thread.sleep(20); s.bookPage != before || s.scene != scene })
    }

    @Test fun eachBookPagePlaysItsMood() = run { d ->
        d.openBook()
        assertEquals(trackOf(BgmMood.DISCOVERY, d.s.storyBookKey()), Bgm.mixer.playing)      // 표지
        d.next()
        assertEquals(trackOf(moodOf(d.s.pageKind(1)), d.s.storyBookKey()), Bgm.mixer.playing)
    }

    @Test fun theEndScreenPlaysTheNightMood() = run { d ->
        d.openBook(pages = 2)
        repeat(3) { d.next() }                                        // 표지 → 1 → 2 → 끝 화면
        assertTrue(await { d.s.scene == Scene.FRIENDS } != null)
        assertEquals(trackOf(BgmMood.NIGHT, d.s.storyBookKey()), Bgm.mixer.playing)
    }

    @Test fun leavingTheBookStopsTheMusic() = run { d ->
        d.openBook()
        d.go(Scene.ADULT)
        assertTrue(await { d.s.scene == Scene.ADULT } != null)
        assertNull(Bgm.mixer.playing)
    }

    /** 다시 읽기의 키는 저장된 책(제목 · 첫 쪽 문장)으로 만든다 — 첫 읽기 키와 같아야 같은 곡이다 */
    @Test fun aRereadPlaysTheSameTrackAsTheFirstRead() = run { d ->
        d.s.templateKey = TEMPLATES.first().key
        d.openBook()
        val saved = d.s.completedStoryBook()
        assertNotNull("저장할 수 있는 책이어야 두 키를 견줄 수 있다", saved)
        assertEquals(bgmBookKey(saved!!.title, saved.pages.first().caption), d.s.storyBookKey())
    }

    // ── 책장 다시 읽기 (Scenes.kt sceneShelf) ──

    private suspend fun Director.shelfWith(book: SavedStoryBook? = null): String {
        if (book == null) {
            s.mode = StoryMode.STORY
            s.templateKey = TEMPLATES.first().key
            s.storyCaptions = (1..3).map { "아이의 이야기 $it 쪽이에요." }
            assertTrue("책을 저장하지 못했다", saveFinishedStory())
        }
        go(Scene.SHELF)
        assertNotNull("책장이 열리지 않았다", await { s.stage is Stage.Shelf })
        return (book ?: s.completedStoryBook()!!).let { b -> if (book == null) storyBooks().first().id else COOP_SHELF_ID + b.id }
    }

    private suspend fun Director.tapUntil(value: String, label: String, cond: () -> Boolean) =
        assertNotNull("'$value' 입력이 먹지 않았다", await { send(Reply.Tapped(value, label)); Thread.sleep(20); cond() })

    @Test fun theShelfRereadPlaysTheBooksMoodsAndStopsOnClose() = run { d ->
        val id = d.shelfWith()
        val book = d.storyBooks().first()
        val key = bgmBookKey(book.title, book.pages.first().caption)
        d.tapUntil("book", id) { d.s.stage is Stage.SavedStory }
        assertNotNull(await { Bgm.mixer.playing != null })
        assertEquals(trackOf(BgmMood.DISCOVERY, key), Bgm.mixer.playing)                       // 표지
        d.tapUntil("next", "다음") { (d.s.stage as? Stage.SavedStory)?.index == 1 }
        assertEquals(trackOf(moodOf(book.pages[0].kind), key), Bgm.mixer.playing)
        d.tapUntil("close", "닫기") { d.s.stage is Stage.Shelf }
        assertNull(Bgm.mixer.playing)
    }
}
