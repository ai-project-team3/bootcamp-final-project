package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.finalproject_demo.demo.DIARY_SHELF_ID
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryBookStore
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryShelf
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryPageCaption
import com.example.finalproject_demo.demo.openSavedDiary
import com.example.finalproject_demo.demo.readingDiary
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.StageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #262 — 그림일기 책을 읽을 때 목소리가 늦었다(10-07 실기기 · 쪽마다 /tts 1.8~3.5초).
 * 책을 펼칠 때 모든 쪽 목소리를 미리 받고(남긴 목소리가 있으면 그것), 쪽의 글자는 그 문장의 목소리가 나올 때 쓰기 시작한다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryBookVoiceTest {
    @get:Rule val compose = createComposeRule()

    @After fun off() { Server.liveModes = emptySet(); Server.base = null }

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private val book = SavedDiaryBook(
        id = "t1", title = "바닷가", madeAt = "2026-10-07", pages = 3,
        input = DiaryBookInput(
            lines = mapOf("place" to "바닷가", "problem" to "모래성 만들었어", "solution" to "다시 만들었어"),
            by = mapOf("place" to "child", "problem" to "child", "solution" to "child"),
            written = listOf("나는 오늘 바닷가에 갔어요.", "모래성을 만들었어요.", "다시 만들었어요."), missions = false, puzzle = false,
        ),
        pieces = emptyList(),
    )

    private class MemoryStore(val books: MutableList<SavedDiaryBook>) : DiaryBookStore {
        val voices = mutableMapOf<Pair<String, String>, ByteArray>()
        override fun load() = books.toList()
        override fun save(book: SavedDiaryBook) { books.add(0, book) }
        override fun voice(bookId: String, line: String) = voices[bookId to line]
        override fun keepVoice(bookId: String, line: String, mp3: ByteArray) { voices[bookId to line] = mp3 }
    }

    /** 책을 펼치자마자 모든 쪽 목소리가 준비된다 — 전에는 지금 쪽만(넘길 때마다 그제야) */
    @Test
    fun openingTheBookReadiesEveryPagesVoice() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        Server.base = "http://127.0.0.1:9"; Server.liveModes = setOf(StoryMode.DIARY)
        val store = MemoryStore(mutableListOf(book))
        val lines = buildDiaryBook(book.input).map(::diaryPageCaption)
        lines.forEachIndexed { i, l -> store.voices["t1" to l] = byteArrayOf(i.toByte()) }
        DiaryShelf.attach(d.s, store)
        try {
            val reading = scope.async { d.openSavedDiary(DIARY_SHELF_ID + "t1") }
            assertTrue(await { d.s.stage is DiaryPaper } != null)
            // 첫 쪽은 막 말해 넘겨받았다 — 나머지 쪽이 이미 준비돼 있어야 한다
            lines.drop(1).forEach { assertTrue("「$it」 목소리가 펼칠 때 준비되지 않았다", d.voiceReady(it)) }
            reading.cancel()
        } finally { scope.cancel() }
    }

    /** 미리 받아 두는 목소리가 4개를 넘어도 비워지지 않는다 — 책 쪽 수만큼 */
    @Test
    fun aBookFullOfVoicesIsKept() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.DIARY
        Server.base = "http://127.0.0.1:9"; Server.liveModes = setOf(StoryMode.DIARY)
        val lines = (1..8).map { "쪽 $it 문장이에요." }
        lines.forEach { d.offerVoice(it, byteArrayOf(1)) }
        assertEquals(lines.size, lines.count { d.voiceReady(it) })
    }

    /** 쪽의 글자는 그 문장의 목소리가 나오기 시작할 때 쓴다 — 목소리를 기다리는 동안은 비어 있다 */
    @Test
    fun aPagesWritingWaitsForItsVoice() {
        val d = Director(CoroutineScope(SupervisorJob())).apply { s.speed = 0.0; s.mode = StoryMode.DIARY; s.scene = Scene.DIARY }
        d.s.readingDiary = book.input
        d.s.diaryDay.madeOn = java.time.LocalDate.of(2026, 10, 7)
        compose.mainClock.autoAdvance = false
        d.say("나는 오늘 바닷가에 갔어요.")
        d.voicePending = d.s.lineId                       // 이 줄의 목소리를 받는 중
        d.s.stage = DiaryPaper(0)
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) } }
        compose.mainClock.advanceTimeBy(2_500)
        assertTrue("목소리를 기다리는데 글자를 썼다", compose.onAllNodesWithText("닷").fetchSemanticsNodes().isEmpty())
        d.voicePending = null                             // 목소리가 나오기 시작했다
        // 테스트 스레드의 쓰기는 앱(메인 스레드 · GlobalSnapshotManager)처럼 저절로 알려지지 않는다
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(2_500)
        assertTrue("목소리가 나왔는데 글자를 쓰지 않았다", compose.onAllNodesWithText("닷").fetchSemanticsNodes().isNotEmpty())
    }
}
