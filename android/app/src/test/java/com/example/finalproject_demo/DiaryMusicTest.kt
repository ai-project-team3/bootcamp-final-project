package com.example.finalproject_demo

import com.example.finalproject_demo.demo.BgmMood
import com.example.finalproject_demo.demo.DIARY_SHELF_ID
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryBookStore
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryShelf
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryBookKey
import com.example.finalproject_demo.demo.diaryMoodOf
import com.example.finalproject_demo.demo.openSavedDiary
import com.example.finalproject_demo.demo.trackOf
import com.example.finalproject_demo.net.Bgm
import com.example.finalproject_demo.net.BgmChannel
import com.example.finalproject_demo.net.BgmOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

/** 그림일기도 쪽마다 분위기 곡 · 다 읽은 선물 화면은 밤 곡 · 책장 다시 읽기도 (#221 확장) */
class DiaryMusicTest {
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

    // ── 그림일기 쪽 → 분위기 ──

    @Test fun theDrawingPageIsDiscoveryAndThePlaceIsAnOuting() {
        assertEquals(BgmMood.DISCOVERY, diaryMoodOf(DiaryPageKind.DRAWING, troubled = false))
        assertEquals(BgmMood.ADVENTURE, diaryMoodOf(DiaryPageKind.PLACE, troubled = false))
    }

    /** 어긋난 일이 있던 날만 긴장 — 그냥 구경한 날의 「일」 쪽은 신나는 곡이다 */
    @Test fun whatHappenedIsTenseOnlyOnATroubledDay() {
        assertEquals(BgmMood.PLAYFUL, diaryMoodOf(DiaryPageKind.PROBLEM, troubled = false))
        assertEquals(BgmMood.PLAYFUL, diaryMoodOf(DiaryPageKind.REACTION, troubled = false))
        assertEquals(BgmMood.TENSE, diaryMoodOf(DiaryPageKind.PROBLEM, troubled = true))
        assertEquals(BgmMood.TENSE, diaryMoodOf(DiaryPageKind.REACTION, troubled = true))
    }

    @Test fun theDayEndsWarm() {
        assertEquals(BgmMood.ENDING, diaryMoodOf(DiaryPageKind.SOLUTION, troubled = true))
        assertEquals(BgmMood.ENDING, diaryMoodOf(DiaryPageKind.KEEP, troubled = false))
        assertEquals(BgmMood.PLAYFUL, diaryMoodOf(DiaryPageKind.PUZZLE, troubled = false))
    }

    // ── 그림일기 읽기 (PictureDiary.kt) ──

    private val sample = SavedDiaryBook(
        id = "m1", title = "식물원", madeAt = "2026-10-07", pages = 2,
        input = DiaryBookInput(
            lines = mapOf("place" to "식물원", "problem" to "구경했어"),
            by = mapOf("place" to "child", "problem" to "child"),
            written = listOf("나는 오늘 식물원에 갔어요.", "꽃을 구경했어요."), missions = false, puzzle = false,
        ),
        pieces = emptyList(),
    )

    private class MemoryStore(val books: MutableList<SavedDiaryBook> = mutableListOf()) : DiaryBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedDiaryBook) { books.add(0, book) }
    }

    @Test fun aDiaryRereadPlaysEachPagesMoodAndStopsAfter() = run { d ->
        d.s.mode = StoryMode.DIARY
        DiaryShelf.attach(d.s, MemoryStore(mutableListOf(sample)))
        val key = diaryBookKey(buildDiaryBook(sample.input))
        val reading = async { d.openSavedDiary(DIARY_SHELF_ID + "m1") }
        assertNotNull(await { d.s.stage == DiaryPaper(0) })
        assertEquals(trackOf(BgmMood.ADVENTURE, key), Bgm.mixer.playing)                     // 식물원에 갔어요
        d.tapUntil("next", "다음") { d.s.stage == DiaryPaper(1) }
        assertEquals(trackOf(BgmMood.PLAYFUL, key), Bgm.mixer.playing)                       // 구경했어요
        d.tapUntil("next", "다 읽었어") { reading.isCompleted }
        assertNull("다 읽고 책장으로 왔는데 음악이 남았다", Bgm.mixer.playing)
    }

    /** 다 읽은 뒤 제목을 붙여도(첫 읽기 끝) 곡이 바뀌지 않게 — 키는 제목이 아니라 첫 쪽 문장이다 */
    @Test fun theDiaryKeyDoesNotDependOnTheTitle() {
        val pages = buildDiaryBook(sample.input)
        assertEquals(diaryBookKey(pages), diaryBookKey(buildDiaryBook(sample.copy(title = "꽃 구경").input)))
    }

    /** 처음 읽을 때도 쪽마다 곡 · 다 읽은 선물 화면은 동화의 끝 화면처럼 밤 곡 · 책장으로 가면 끈다 */
    @Test fun aFirstDiaryReadPlaysAndTheGiftIsNight() = run { d ->
        d.s.mode = StoryMode.DIARY
        DiaryShelf.attach(d.s, MemoryStore())
        d.go(Scene.DIARY)
        assertNotNull(await { d.s.buttons.any { "그림 없이 이야기할래" in it.label } })
        d.s.buttons.first { "그림 없이 이야기할래" in it.label }.onClick()
        var heard: String? = null
        var guard = 0
        while (d.s.stage !is DiaryGift && guard++ < 80) {
            if (d.s.stage is DiaryPaper && heard == null) heard = Bgm.mixer.playing
            val b = d.s.buttons.firstOrNull { "🎬 오늘 이야기 시연 답" in it.label }
                ?: d.s.buttons.firstOrNull { "😄" in it.label }
                ?: d.s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }
                ?: d.s.buttons.firstOrNull { "대답 없음" in it.label }
            if (b == null) { delay(20); continue }
            b.onClick(); delay(30)
        }
        assertNotNull("그림일기를 다 읽지 못했다", await { d.s.stage is DiaryGift })
        assertNotNull("그림일기를 읽는 동안 음악이 없었다", heard)
        val key = diaryBookKey(buildDiaryBook(d.s.diaryBookInput()))
        assertEquals(trackOf(BgmMood.NIGHT, key), Bgm.mixer.playing)
        assertNotNull(await { d.s.buttons.any { "책장에 꽂기" in it.label } })
        d.s.buttons.first { "책장에 꽂기" in it.label }.onClick()
        assertNotNull(await { d.s.scene == Scene.SHELF })
        assertNull(Bgm.mixer.playing)
    }
}
