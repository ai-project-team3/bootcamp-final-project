package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_SHELF_ID
import com.example.finalproject_demo.demo.CoopBookStore
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.SavedCoopBook
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bgmBookKey
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
import org.junit.Before
import org.junit.Test

/** 같이 만들기 책도 동화책처럼 쪽마다 분위기 곡 · 책장 다시 읽기도 (#221 확장) */
class CoopMusicTest {
    @Before fun fakeOut() {
        Bgm.resetForTest()
        Bgm.output = BgmOutput { _ -> object : BgmChannel {
            override val durationMs = 60_000L; override val positionMs = 0L
            override fun start() {}; override fun pause() {}; override fun setVolume(v: Float) {}; override fun release() {}
        } }
        Bgm.resetMixerForTest()
    }
    @After fun reset() = Bgm.resetForTest()

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean) = withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel() }
    }

    private suspend fun Director.tapUntil(value: String, label: String, cond: () -> Boolean) =
        assertNotNull("'$value' 입력이 먹지 않았다", await { send(Reply.Tapped(value, label)); Thread.sleep(20); cond() })

    // ── 같이 만들기 책 (Scenes.kt sceneBook · sceneShelf) ──

    @Test fun aCoopBookPlaysItsPagesMood() = run { d ->
        d.s.mode = StoryMode.COOP
        d.s.storyCaptions = listOf("같이 만든 이야기예요.")
        d.go(Scene.BOOK)
        assertNotNull(await { d.s.stage is Stage.BookPage })
        assertEquals(trackOf(moodOf(d.s.pageKind(0)), d.s.storyBookKey()), Bgm.mixer.playing)
    }

    @Test fun aCoopBookOnTheShelfPlaysAndStopsOnClose() = run { d ->
        val coop = SavedStoryBook("c1", "같이 만든 책", "space", "bg_space",
            listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요."), SavedStoryPage(PageKind.JOURNEY, "집에 왔어요.")))
        CoopShelf.attach(d.s, object : CoopBookStore {
            override fun load() = listOf(SavedCoopBook(coop, null))
            override fun save(book: SavedCoopBook) {}
        })
        val key = bgmBookKey(coop.title, coop.pages.first().caption)
        d.go(Scene.SHELF)
        assertNotNull(await { d.s.stage is Stage.Shelf })
        d.tapUntil("book", COOP_SHELF_ID + coop.id) { d.s.stage is Stage.SavedStory }
        d.tapUntil("next", "다음") { (d.s.stage as? Stage.SavedStory)?.index == 1 }
        assertEquals(trackOf(moodOf(PageKind.TOGETHER), key), Bgm.mixer.playing)
        d.tapUntil("close", "닫기") { d.s.stage is Stage.Shelf }
        assertNull(Bgm.mixer.playing)
    }
}
