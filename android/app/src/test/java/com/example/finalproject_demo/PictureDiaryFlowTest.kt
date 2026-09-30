package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.ASK_AFTER_DRAWING
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.NOT_HEARD_AFTER
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 그림일기 한 바퀴를 **실제로 돌려 본다** — docs/일기모드_흐름.html D0 → D1 → D3 → D4 → D5 → D6.
 * 감독을 그대로 띄우고 시연 버튼을 누른다. 기다리는 시간만 줄인다.
 */
class PictureDiaryFlowTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    /** 버튼을 누르되 화면이 움직일 때까지 다시 누른다 — 감독은 말이 끝나기 전 입력을 버린다 */
    private suspend fun Director.push(part: String): Boolean {
        if (await(3_000) { s.buttons.any { part in it.label } } == null) {
            println("[버튼 없음] \"$part\" · 말=\"${s.line}\" · 버튼=${s.buttons.map { it.label }}")
            return false
        }
        repeat(12) {
            val b = s.buttons.firstOrNull { part in it.label } ?: return true
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || s.buttons.none { x -> part in x.label } } != null) return true
        }
        return false
    }

    /** 서버 모드처럼 **값 없는** 말을 보낸다 — 질문이 뜬 뒤 먹힐 때까지 */
    private suspend fun Director.speak(text: String) {
        val before = s.lineId
        repeat(20) {
            send(Reply.Spoke(text))
            if (await(400) { s.lineId != before && s.line != "" } != null && s.line == text) return
        }
    }

    private suspend fun Director.readToTheEnd() {
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 30) {
            val b = s.buttons.firstOrNull { "😄" in it.label } ?: s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }
            if (b == null) { delay(20); continue }
            b.onClick(); delay(30)
        }
    }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        try { block(d) } finally { sup.cancel() }
    }

    private fun stroke(x: Float) = Stroke(Color.Blue, listOf(Offset(x, 0.3f), Offset(x + 0.1f, 0.6f)))

    @Test
    fun aTalkingDayAsksAtMostThreeThingsAndBecomesAPictureDiary() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue("D0 에 그림판이 먼저 떠야 한다", await { s.stage is DiaryBoard } != null)
        assertTrue(d.push("그림 없이 이야기할래"))

        // 남아 있는 옛 버튼을 한 번 더 누를 수 있어 누른 수가 아니라 **물은 수**(stepsDone)로 센다
        var guard = 0
        while (s.stage !is DiaryPaper && guard++ < 20) {
            if (await(2_000) { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } == null) break
            d.push("🎬 오늘 이야기 시연 답")
        }
        assertEquals("다 그린 뒤 묻는 것은 세 번까지", ASK_AFTER_DRAWING, s.stepsDone)
        assertEquals("story_ready", s.endReason)
        assertEquals(listOf("child", "child", "child"), listOf("place", "problem", "solution").map { s.slotBy[it] })
        assertTrue("빈 칸을 마스코트가 메웠다: ${s.slotBy}", s.slotBy.values.none { it == "mascot" })
        assertNull("세 번을 넘겨 내일 이야기까지 물었다", s.slots["keep"])

        assertTrue("그림일기로 안 왔다", await { s.stage is DiaryPaper && "나는 오늘" in s.line } != null)
        val book = buildDiaryBook(s.diaryBookInput())
        assertEquals(
            listOf("나는 오늘 어린이집에 갔어요.", "높이 쌓은 블록이 와르르 무너졌어요.", "마침내 다시 쌓은 블록은 이번엔 무너지지 않았어요."),
            book.map { it.text },
        )
        assertTrue("오늘 기분을 묻지 않았다", book.last().asksFeel)

        d.readToTheEnd()
        assertTrue("선물 · 책장으로 안 갔다 (장면=${s.scene})", await { s.scene == Scene.END } != null)
        assertEquals("얼굴로 고른 기분", "오늘은 참 신났어요.", s.diaryDay.feel?.line)
    }

    @Test
    fun dontKnowIsAskedOnceMoreThenLeftEmptyNotInvented() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))

        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        d.speak("놀이터 갔어")                       // 서버 모드의 답 — 대본 값이 없다
        assertTrue(await { s.line == "거기서 무슨 일이 있었어?" } != null)
        d.speak("몰라")
        assertTrue("「몰라」에 쉬운 말로 한 번 더 묻지 않았다", await { s.line == "거기서 뭐 했어?" } != null)
        d.speak("몰라")
        assertTrue(await { s.line == "괜찮아, 생각 안 나도 돼." } != null)

        assertEquals("놀이터 갔어", s.slots["place"])
        assertEquals("child", s.slotBy["place"])
        assertNull("모르는 칸을 지어냈다", s.slots["problem"])
        assertTrue(s.slotBy.values.none { it == "mascot" })
        assertTrue(s.log.none { "혹시" in it })
    }

    @Test
    fun aSilentChildWithNoDrawingEndsQuietlyWithoutABook() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        var guard = 0
        while (s.scene == Scene.DIARY && guard++ < 30) {
            if (await(1_500) { s.buttons.any { "대답 없음" in it.label } } == null) break
            d.push("대답 없음")
        }
        assertTrue("책 없이 처음 화면으로 가지 않았다 (장면=${s.scene})", await(8_000) { s.scene == Scene.ADULT } != null)
        assertTrue(s.slotBy.isEmpty())
        assertTrue("아무 말도 안 했는데 인용이 생겼다", s.quotes.isEmpty())
        assertTrue("동화 모드로 넘겼다", s.log.none { "동화 모드로 넘어간다" in it })
        assertEquals("말 없는 날인데 모드가 바뀌었다", StoryMode.DIARY, s.mode)
    }

    @Test
    fun whileDrawingOttoAsksWhatItIsAndTheChildPicksOttosDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))

        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertTrue("그림판을 떠났다 — 아이가 그리던 획이 사라진다", s.stage is DiaryBoard)
        assertTrue(d.push("우리 집이야"))
        assertTrue(await { "우리 집이구나" in s.line } != null)
        assertEquals("우리 집", s.diaryDay.pieces.single().name)
        assertEquals("child", s.slotBy["whiteboard"])

        assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
        assertTrue(d.push("응!"))
        s.drawing += stroke(0.5f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue("오또 그림이 오지 않았다", await { "짠!" in s.line } != null)
        assertTrue(d.push("오또 그림으로"))
        assertEquals(PieceLook.OTTO, s.diaryDay.pieces.first().look)
        assertTrue(s.stage is DiaryBoard)

        assertTrue(d.push("다 그렸어"))
        assertTrue("다 그린 뒤 빈 칸을 묻지 않았다", await { s.line == "오늘 어디 갔었어?" } != null)
        assertTrue("그림이 책에 쓸 자리로 옮겨지지 않았다", s.sceneDrawing.size == 2 && s.drawing.isEmpty())
    }

    /** 그림판의 [그리기 싫어]는 그리는 도중에도 먹어야 한다 (09-22 일기 · 협업에서 눌러도 멎던 것과 같은 자리) */
    @Test
    fun theDrawPadsNoMoreDrawingButtonWorksWhileDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
        assertTrue(await {
            d.send(Reply.Tapped("skip", "안 그릴래"))
            s.line == "오늘 어디 갔었어?"
        } != null)
    }

    @Test
    fun onlyTwoQuestionsWhileDrawingThenOttoJustWatches() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        repeat(2) { i ->
            s.drawing += stroke(0.1f + 0.3f * i)
            assertTrue(d.push("붓이 멈춤"))
            assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
            assertTrue(d.push("대답 없음"))
            if (await(1_500) { s.buttons.any { "대답 없음" in it.label } } != null) d.push("대답 없음")
            assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
        }
        val lines = s.lineId
        s.drawing += stroke(0.75f)
        // 이번에는 오또가 말을 걸지 않는 것이 맞다 — 말이 바뀌기를 기다리는 push 대신 기록이 남을 때까지 누른다
        assertTrue(await {
            s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
            s.log.any { "물을 만큼 물었다" in it }
        } != null)
        assertEquals("세 번째 멈춤에도 말을 걸었다", lines, s.lineId)
        assertEquals(3, s.diaryDay.pieces.size)
        assertTrue("아이가 말하지 않은 이름이 붙었다", s.diaryDay.pieces.all { it.name == null })
    }

    @Test
    fun aDrawingWithNoWordsIsStillAOnePageBook() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.2f)
        assertTrue(d.push("다 그렸어"))
        var guard = 0
        while (s.scene == Scene.DIARY && s.stage !is DiaryPaper && guard++ < 30) {
            if (await(1_500) { s.buttons.any { "대답 없음" in it.label } } == null) continue
            d.push("대답 없음")
        }
        assertTrue(await { s.stage is DiaryPaper && s.line.startsWith("내가 오늘 그린 그림이에요.") } != null)
        val book = buildDiaryBook(s.diaryBookInput())
        assertEquals(listOf(DiaryPageKind.DRAWING), book.map { it.kind })
        assertFalse("그림만 있는 날에 「아직 듣지 못했어요」를 붙였다", book.single().tail == NOT_HEARD_AFTER)
        d.readToTheEnd()
        assertTrue(await { s.scene == Scene.END } != null)
    }
}
