package com.example.finalproject_demo

import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DIARY_SHELF_ID
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryBookStore
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryShelf
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalDiaryBookStore
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryPageCaption
import com.example.finalproject_demo.demo.openSavedDiary
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #179 — 책장에서 그림일기를 다시 읽을 때 쪽 목소리를 `/tts` 로 다시 받지 않는다.
 * 책장에 꽂을 때 그 쪽 문장의 오또 목소리를 책 옆(폰 안)에 남기고, 다시 읽을 때 그것을 튼다.
 * 10-06 실기기: 7쪽 일기를 한 번 다시 읽는 데 `/tts` 7번(약 26원)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryReadVoiceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun off() { Server.liveModes = emptySet(); Server.base = null }

    private val sample = SavedDiaryBook(
        id = "v1", title = "식물원", madeAt = "2026-10-06", pages = 2,
        input = DiaryBookInput(
            lines = mapOf("place" to "식물원", "problem" to "구경했어"),
            by = mapOf("place" to "child", "problem" to "child"),
            pieceNames = emptyList(), hasDrawing = false, feel = null,
            written = listOf("나는 오늘 식물원에 갔어요.", "구경했는데 지루했어요."), missions = false, puzzle = false,
        ),
        pieces = emptyList(),
    )

    /** 목소리를 따로 들고 있는 저장소 — 앱의 `LocalDiaryBookStore` 와 같은 약속 */
    private class MemoryStore(val books: MutableList<SavedDiaryBook> = mutableListOf()) : DiaryBookStore {
        val voices = mutableMapOf<Pair<String, String>, ByteArray>()
        override fun load() = books.toList()
        override fun save(book: SavedDiaryBook) { books.add(0, book) }
        override fun voice(bookId: String, line: String) = voices[bookId to line]
        override fun keepVoice(bookId: String, line: String, mp3: ByteArray) { voices[bookId to line] = mp3 }
    }

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun captions(book: SavedDiaryBook) = buildDiaryBook(book.input).map(::diaryPageCaption)

    @Test
    fun aPageVoiceStaysNextToItsBookAndGoesWithIt() {
        val store = LocalDiaryBookStore(context)
        store.save(sample)
        store.keepVoice("v1", "나는 오늘 식물원에 갔어요.", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), LocalDiaryBookStore(context).voice("v1", "나는 오늘 식물원에 갔어요."))
        assertNull("남기지 않은 문장의 목소리가 나왔다", store.voice("v1", "구경했는데 지루했어요."))
        assertNull("다른 책의 목소리가 나왔다", store.voice("v2", "나는 오늘 식물원에 갔어요."))
        val dir = File(context.filesDir, "diary_voices")
        assertTrue("목소리가 앱 내부 파일이 아니다", dir.listFiles().orEmpty().any { it.name.startsWith("v1_") && it.name.endsWith(".mp3") })
        assertTrue(store.delete("v1"))
        assertTrue("책을 뺐는데 목소리가 남았다", dir.listFiles().orEmpty().none { it.name.startsWith("v1_") })
    }

    /** 책장에 꽂을 때, 이번 세션에 받은 쪽 목소리를 그 책 옆에 남긴다 */
    @Test
    fun shelvingKeepsTheVoicesHeardWhileReadingIt() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        val store = MemoryStore()
        DiaryShelf.attach(d.s, store)
        try {
            d.go(Scene.DIARY)
            assertTrue(await { d.s.buttons.any { "그림 없이 이야기할래" in it.label } } != null)
            d.s.buttons.first { "그림 없이 이야기할래" in it.label }.onClick()
            var guard = 0
            while (d.s.stage !is DiaryGift && guard++ < 80) {
                val b = d.s.buttons.firstOrNull { "🎬 오늘 이야기 시연 답" in it.label }
                    ?: d.s.buttons.firstOrNull { "😄" in it.label }
                    ?: d.s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }
                    ?: d.s.buttons.firstOrNull { "대답 없음" in it.label }
                if (b == null) { delay(20); continue }
                b.onClick(); delay(30)
            }
            assertTrue(await { d.s.stage is DiaryGift } != null)
            // 쪽을 읽는 동안 서버에서 받은 목소리 — 소리를 못 내는 단위 테스트라 직접 넣는다
            val lines = buildDiaryBook(d.s.diaryBookInput()).map(::diaryPageCaption)
            lines.forEachIndexed { i, l -> d.rememberVoice(l, byteArrayOf(i.toByte())) }
            assertTrue(await { d.s.buttons.any { "책장에 꽂기" in it.label } } != null)
            d.s.buttons.first { "책장에 꽂기" in it.label }.onClick()
            assertTrue("책장으로 안 갔다", await { d.s.scene == Scene.SHELF } != null)
            val saved = store.books.single()
            lines.forEachIndexed { i, l ->
                assertArrayEquals("「$l」 목소리를 책 옆에 남기지 않았다", byteArrayOf(i.toByte()), store.voices[saved.id to l])
            }
        } finally { scope.cancel() }
    }

    /** 다시 읽을 때 남겨 둔 목소리를 넘겨 `/tts` 를 부르지 않는다 · 없던 쪽은 받은 것을 채워 둔다 */
    @Test
    fun rereadingPlaysTheKeptVoicesAndFillsInTheMissingOne() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        Server.base = "http://127.0.0.1:9"; Server.liveModes = setOf(StoryMode.DIARY)
        val store = MemoryStore(mutableListOf(sample))
        val (first, second) = captions(sample).take(2)
        store.voices["v1" to first] = byteArrayOf(7)               // 앞 쪽은 남아 있다 · 뒤 쪽은 이 기능 전이라 없다
        d.rememberVoice(second, byteArrayOf(8))                    // 뒤 쪽은 다시 읽는 동안 서버에서 받았다
        DiaryShelf.attach(d.s, store)
        try {
            val reading = scope.async { d.openSavedDiary(DIARY_SHELF_ID + "v1") }
            assertTrue(await { d.s.stage is DiaryPaper && first in d.s.line } != null)
            assertTrue("남겨 둔 목소리를 넘기지 않았다 — /tts 를 다시 부른다", d.voiceReady(first))
            var guard = 0
            while (!reading.isCompleted && guard++ < 40) {
                d.s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }?.onClick()
                delay(30)
            }
            assertTrue(reading.await())
            assertArrayEquals("다시 읽으며 받은 목소리를 채워 두지 않았다", byteArrayOf(8), store.voices["v1" to second])
            assertEquals("남아 있던 목소리를 바꿨다", 7.toByte(), store.voices["v1" to first]!!.single())
        } finally { scope.cancel() }
    }
}
