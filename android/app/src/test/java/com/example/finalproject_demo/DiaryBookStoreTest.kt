package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DIARY_BG_FALLBACK
import com.example.finalproject_demo.demo.onShelf
import com.example.finalproject_demo.demo.DIARY_SHELF_ID
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryBookStore
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryShelf
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalDiaryBookStore
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.hasDiaryCover
import com.example.finalproject_demo.demo.coverKey
import com.example.finalproject_demo.demo.diaryCovers
import com.example.finalproject_demo.demo.openSavedDiary
import com.example.finalproject_demo.demo.readingDiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
 * 그림일기 책장 저장 (#37) — 앱을 꺼도 남고 「다시 읽기」로 연다. 동화 `LocalStoryBookStore` 와 같은 길,
 * 겉(`id · title · madeAt · pages · mode`)은 세 모드 공통 · 속은 일기 것. 폰 안에만 둔다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryBookStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun line(c: Color, vararg xy: Float) = Stroke(c, xy.toList().chunked(2).map { Offset(it[0], it[1]) })

    private val sample = SavedDiaryBook(
        id = "b1", title = "놀이터에서 뽀삐랑", madeAt = "2026-10-02", pages = 3,
        input = DiaryBookInput(
            lines = mapOf("place" to "놀이터 갔어", "problem" to "뽀삐가 넘어졌어"),
            by = mapOf("place" to "child", "problem" to "child", "title" to "child"),
            pieceNames = listOf("뽀삐"), hasDrawing = true, feel = DiaryFeel.GOOD,
            written = listOf("나는 오늘 놀이터에 갔어요.", "뽀삐가 넘어졌어요."), missions = true, puzzle = false,
        ),
        pieces = listOf(
            DiaryPiece(0, listOf(line(Color.Red, .1f, .2f, .3f, .4f)), "뽀삐", PieceLook.OTTO, byteArrayOf(9, 8, 7)),
            DiaryPiece(1, listOf(line(Color.Blue, .6f, .6f, .7f, .7f))),
        ),
        weather = DiaryWeather.SUN, weatherBy = "card", aspect = 2.2f,
    )

    @Test
    fun aSavedDiaryComesBackWithItsDrawingWordsAndOttosPicture() {
        val store = LocalDiaryBookStore(context)
        store.save(sample)
        val back = LocalDiaryBookStore(context).load().single()        // 앱을 다시 켠 것처럼 새로 읽는다
        assertEquals(sample.title, back.title)
        assertEquals("2026-10-02", back.madeAt)
        assertEquals(3, back.pages)
        assertEquals(sample.input, back.input)
        assertEquals(DiaryWeather.SUN, back.weather)
        assertEquals("card", back.weatherBy)
        assertEquals(2.2f, back.aspect, 0.001f)
        assertEquals(sample.pieces[0].strokes, back.pieces[0].strokes)
        assertEquals("뽀삐", back.pieces[0].name)
        assertEquals(PieceLook.OTTO, back.pieces[0].look)
        assertArrayEquals("오또 그림이 돌아오지 않았다", byteArrayOf(9, 8, 7), back.pieces[0].ottoPng)
        assertNull("받지 않은 오또 그림이 생겼다", back.pieces[1].ottoPng)
        assertTrue("오또 그림이 앱 내부 파일이 아니다", File(context.filesDir, "diary_images/b1_0.png").exists())
    }

    /** 그림 없이 만든 일기의 책장 표지 — 아이가 말한 곳의 펠트 그림. 전에는 빈 노란 표지였다 (#98) */
    @Test
    fun aDiaryWithoutDrawingIsCoveredWithThePlace() {
        val talked = sample.copy(pieces = emptyList(), input = sample.input.copy(hasDrawing = false))
        assertEquals("bg_playground", talked.onShelf().bgName)
        val nowhere = talked.copy(input = talked.input.copy(lines = mapOf("problem" to "그네 탔어")))
        assertEquals("어디라고 말하지 않은 날은 어디라고 말하지 않는 그림", DIARY_BG_FALLBACK, nowhere.onShelf().bgName)
    }

    /** 겉은 세 모드 공통 — 서버 책장 API(`book_id · title · made_at · pages`)와 같은 모양 */
    @Test
    fun theEnvelopeIsTheSameForEveryMode() {
        LocalDiaryBookStore(context).save(sample)
        val raw = context.getSharedPreferences("diary_books", android.content.Context.MODE_PRIVATE).getString("books", null)!!
        val obj = JSONArray(raw).getJSONObject(0)
        listOf("id", "title", "madeAt", "pages", "mode").forEach { assertTrue("겉 칸 「$it」이 없다", obj.has(it)) }
        assertEquals("diary", obj.getString("mode"))
        assertTrue("일기 속은 따로 둔다", obj.has("diary"))
    }

    @Test
    fun aNewerBookGoesFirstAndBothStay() {
        val store = LocalDiaryBookStore(context)
        store.save(sample)
        store.save(sample.copy(id = "b2", title = "둘째 날"))
        assertEquals(listOf("둘째 날", sample.title), store.load().map { it.title })
    }

    private class MemoryStore(val books: MutableList<SavedDiaryBook> = mutableListOf()) : DiaryBookStore {
        override fun load() = books.toList()
        override fun save(book: SavedDiaryBook) { books.add(0, book) }
    }

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    /** 앱을 켤 때 저장된 그림일기가 책장 · 표지에 올라온다 */
    @Test
    fun savedDiariesComeBackOnTheShelfWhenTheAppStarts() {
        val d = Director(CoroutineScope(SupervisorJob()))
        DiaryShelf.attach(d.s, MemoryStore(mutableListOf(sample)))
        val shelf = d.s.shelf.first()
        assertEquals(sample.title, shelf.title)
        assertEquals(DIARY_SHELF_ID + "b1", shelf.savedStoryId)
        assertTrue("표지가 아이 그림이 아니다", d.s.hasDiaryCover(shelf.coverKey()))
    }

    /** 같은 날 제목이 같은 두 권(「10월 2일 그림일기」)도 표지는 따로 (#64-2) */
    @Test
    fun twoDiariesWithTheSameTitleKeepTheirOwnCovers() {
        val d = Director(CoroutineScope(SupervisorJob()))
        val other = sample.copy(id = "b2", pieces = listOf(DiaryPiece(0, listOf(line(Color.Green, .5f, .5f, .9f, .9f)))))
        DiaryShelf.attach(d.s, MemoryStore(mutableListOf(other, sample)))
        val (a, b) = d.s.shelf.take(2)
        assertEquals("제목은 같다", a.title, b.title)
        assertTrue("표지 열쇠가 같다", a.coverKey() != b.coverKey())
        assertEquals(Color.Green, d.s.diaryCovers[a.coverKey()]!!.pieces.single().strokes.single().color)
        assertEquals(Color.Red, d.s.diaryCovers[b.coverKey()]!!.pieces.first().strokes.single().color)
    }

    /** 「책장에 꽂기」 → 저장소에 한 권 · 책장에 「다시 읽기」 표시 */
    @Test
    fun puttingTheDiaryOnTheShelfSavesIt() = runBlocking {
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
            assertTrue(await { d.s.buttons.any { "책장에 꽂기" in it.label } } != null)
            d.s.buttons.first { "책장에 꽂기" in it.label }.onClick()
            assertTrue("책장으로 안 갔다", await { d.s.scene == Scene.SHELF } != null)
            val saved = store.books.single()
            assertEquals(DIARY_SHELF_ID + saved.id, d.s.shelf.first().savedStoryId)
            assertTrue("쪽이 없는 책을 저장했다", saved.pages > 0)
        } finally { scope.cancel() }
    }

    /** 책장에서 다시 열면 그 책의 쪽 · 날짜로 읽고, 다 읽으면 지금 판의 상태로 돌아온다 */
    @Test
    fun aSavedDiaryOpensFromTheShelfAndLeavesNothingBehind() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        DiaryShelf.attach(d.s, MemoryStore(mutableListOf(sample)))
        d.s.title = "지금 판"
        try {
            val reading = scope.async { d.openSavedDiary(DIARY_SHELF_ID + "b1") }
            assertTrue(await { d.s.stage is DiaryPaper && "나는 오늘 뽀삐를 그렸어요." in d.s.line } != null)
            assertEquals("그 책의 제목이 아니다", sample.title, d.s.title)
            assertEquals("그 책의 날짜가 아니다", java.time.LocalDate.of(2026, 10, 2), d.s.diaryDay.madeOn)
            var guard = 0
            while (!reading.isCompleted && guard++ < 40) {
                d.s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }?.onClick()
                delay(30)
            }
            assertTrue("그림일기가 아닌 것으로 읽었다", reading.await())
            assertNull("읽은 책이 지금 판에 남았다", d.s.readingDiary)
            assertEquals("지금 판", d.s.title)
            assertTrue("동화 책 id 를 일기로 열었다", !d.openSavedDiary("story-123"))
        } finally { scope.cancel() }
    }
}
