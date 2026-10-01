package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.ASK_AFTER_DRAWING
import com.example.finalproject_demo.demo.ASK_WHILE_DRAWING
import com.example.finalproject_demo.demo.DONE_CHECK_EVERY
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryStart
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
import com.example.finalproject_demo.demo.echoBack
import com.example.finalproject_demo.demo.hasDiaryCover
import com.example.finalproject_demo.demo.pieceNameFrom
import com.example.finalproject_demo.demo.praiseFor
import com.example.finalproject_demo.demo.soundsLikeAName
import com.example.finalproject_demo.demo.yesNoOf
import com.example.finalproject_demo.demo.you
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

    /** 그리는 중 **묻지 않았는데** 하는 말 — 오또가 듣고 있을 때 한 번 보내고, [until] 이 안 오면 1.5초 뒤에만 다시 */
    private suspend fun Director.tell(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled }
            delay(100)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    private suspend fun Director.readToTheEnd() {
        var guard = 0
        while (s.scene == Scene.DIARY && s.stage !is DiaryGift && guard++ < 30) {
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
        assertTrue("D0 은 방에서 묻는 시작 화면이다", await { s.stage is DiaryStart } != null)
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
        assertTrue("그림일기 책 선물(D6)로 안 갔다 (장면=${s.scene} · ${s.stage})", await { s.stage is DiaryGift } != null)
        assertEquals("얼굴로 고른 기분", "오늘은 참 신났어요.", s.diaryDay.feel?.line)
        assertTrue(d.push("책장에 꽂기"))
        assertTrue("책장으로 안 갔다", await { s.scene == Scene.SHELF } != null)
        assertEquals("오늘 그림일기가 책장 맨 앞에 꽂히지 않았다", 3, s.shelf.first().pages)
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
        assertTrue("D3 에 그림판이 그대로 떠 있다 — 엎드린 오또와 그림 카드여야 한다", s.stage is DiaryAsk)
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
        assertTrue(await { s.stage is DiaryGift } != null)
        assertTrue(d.push("책장에 꽂기"))
        assertTrue(await { s.scene == Scene.SHELF } != null)
        assertTrue("책장 표지가 아이 그림이 아니다", s.hasDiaryCover(s.shelf.first().title))
    }

    /** 그림판 옆 버튼이 없다 — 물을 것이 떨어지면 오또가 「다 그렸어?」라고 묻고, 말로 답해 끝낸다 (docs/일기모드_UI.html) */
    @Test
    fun whenNothingIsLeftToAskOttoAsksIfDoneAndAVoiceYesEndsDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        repeat(ASK_WHILE_DRAWING) { i ->
            s.drawing += stroke(0.1f + 0.3f * i)
            assertTrue(d.push("붓이 멈춤"))
            assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
            assertTrue(d.push("대답 없음"))
            if (await(1_500) { s.buttons.any { "대답 없음" in it.label } } != null) d.push("대답 없음")
            assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
        }
        // 물을 것 없는 멈춤 — 처음 몇 번은 지켜보기만 하고, [DONE_CHECK_EVERY] 번째에 묻는다
        repeat(DONE_CHECK_EVERY - 1) { i ->
            s.drawing += stroke(0.8f + 0.05f * i)
            val watched = s.log.count { "물을 만큼 물었다" in it }
            assertTrue(await {
                s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
                s.log.count { "물을 만큼 물었다" in it } > watched
            } != null)
        }
        s.drawing += stroke(0.9f)
        assertTrue("물을 것이 떨어졌는데 「다 그렸어?」를 묻지 않았다", await {
            s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
            s.line == "다 그렸어? 더 그릴 거 있어?"
        } != null)
        assertTrue("마이크가 열리지 않았다 — 말로 답할 길이 없다", s.micEnabled)
        // 서버 모드처럼 값 없는 말로 답한다
        assertTrue(await { d.send(Reply.Spoke("응, 다 그렸어")); s.stage is DiaryAsk } != null)
        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
    }

    @Test
    fun aChildsWordsAreReadAsYesNoOrDone() {
        assertEquals("yes", yesNoOf("응!"))
        assertEquals("yes", yesNoOf("좋아"))
        assertEquals("yes", yesNoOf("너도 그려줘"))
        assertEquals("no", yesNoOf("아니, 내 그림이 좋아."))
        assertEquals("no", yesNoOf("더 그릴래!"))
        assertEquals("done", yesNoOf("다 그렸어"))
        assertEquals("done", yesNoOf("응, 다 그렸어"))
        assertNull(yesNoOf("강아지"))
    }

    /** 그리는 중에 아이가 먼저 말한다 — 묻지 않아도 이름을 받고, 「너도 그려줘」면 묻지 않고 바로, 「다 그렸어」면 끝 (docs/일기모드_UI.html) */
    @Test
    fun theChildCanSpeakFirstWhileDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue("묻지 않았는데 말한 이름을 안 받았다", d.push("이건 강아지야") && await { "강아지구나" in s.line } != null)
        assertEquals("강아지", s.diaryDay.pieces.single().name)
        assertTrue(await { s.buttons.any { "너도 그려줘" in it.label } } != null)
        assertTrue(d.push("너도 그려줘"))
        assertTrue("「너도 그려줘」에 바로 그리지 않았다", await { "나도 강아지를 그려볼게" in s.line } != null)
        s.drawing += stroke(0.6f)
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); "짠!" in s.line } != null)
        assertTrue(d.push("내 그림으로"))
        assertTrue(await { s.buttons.any { "다 그렸어!" in it.label } } != null)
        assertTrue(d.push("\"다 그렸어!\""))
        assertTrue("다 그렸다고 말했는데 끝나지 않았다", await { s.stage is DiaryAsk } != null)
    }

    @Test
    fun praiseNamesWhatWasDrawn() {
        assertEquals("다 그렸구나! 멋지다!", praiseFor(emptyList()))
        assertEquals("다 그렸구나! 강아지 멋지다!", praiseFor(listOf("강아지")))
        assertEquals("다 그렸구나! 강아지랑 우리 집 멋지다!", praiseFor(listOf("강아지", "우리 집")))
        assertEquals("다 그렸구나! 해, 집이랑 나무 멋지다!", praiseFor(listOf("해", "집", "나무")))
        assertEquals("다 그렸구나! 너랑 엄마 멋지다!", praiseFor(listOf("나", "엄마")))
    }

    /** 오또가 아이의 「나」를 부를 때는 「너」 — 이름(아이 말)은 그대로 두고 대사만 바꾼다 (프로토타입 `you()`) */
    @Test
    fun ottoCallsTheChildsMeYou() {
        assertEquals("너", you("나"))
        assertEquals("엄마랑 너", you("엄마랑 나"))
        assertEquals("네 동생", you("내 동생"))
        assertEquals("너랑 엄마", you("나랑 엄마"))
        assertEquals("나무", you("나무"))
        assertEquals("우리 집", you("우리 집"))
        assertEquals("나비", you("나비"))
    }

    /** 「강아지 그려줘」 — 마지막에 그린 조각이 아니라 부른 조각을. 이미 그리는 중이면 다시 주문하지 않고 그렇다고 말한다 (프로토타입) */
    @Test
    fun drawMeAimsAtThePieceTheChildNames() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue(await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        s.drawing += stroke(0.7f)                                    // 멀리 — 마지막 조각은 이쪽
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("해야")
        assertTrue(await { s.line == "나도 해를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        d.tell("강아지 그려줘") { s.line == "나도 강아지를 그려볼게! 더 그리고 있어!" }
        d.tell("강아지 그려줘") { s.line == "나도 지금 강아지를 그리고 있어! 조금만 기다려 줘." }
    }

    @Test
    fun aPieceNamedMeIsCalledYou() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("나야")
        assertTrue("말=${s.line}", await { s.line == "나도 너를 그려볼까?" } != null)
        assertEquals("이름은 아이 말 그대로", "나", s.diaryDay.pieces.single().name)
    }

    @Test
    fun onlyNameLikeWordsBecomeAPieceName() {
        assertTrue(soundsLikeAName("이건 강아지야"))
        assertTrue(soundsLikeAName("우리 집이야!"))
        assertFalse(soundsLikeAName("나 오늘 너무 배고파"))
        assertFalse(soundsLikeAName("엄마가 그러는데 내일 비 온대"))
    }

    /**
     * 「지금 그리는 건 뭐야?」에 대한 답 → 조각 이름. 10-01 실기기에서 「강아지」가 「강아」가 됐다(끝 「지」를 어미로 읽었다).
     * 낱말 · 문장 · 군말 · 딴말 — 이름이 아닌 말은 null(이름 없이 둔다)
     */
    @Test
    fun pieceNamesComeOutOfWordsAndSentences() {
        val named = mapOf(
            "강아지" to "강아지", "강아지!" to "강아지", "강아지야" to "강아지", "강아지요" to "강아지", "돼지야" to "돼지",
            "우리 집이야" to "우리 집", "집이에요" to "집", "집이요" to "집", "형이야" to "형",
            "고양이야" to "고양이", "고양이에요" to "고양이", "종이야" to "종이", "아이야" to "아이", "원숭이야" to "원숭이", "공이야" to "공",
            "이건 강아지야" to "강아지", "이거는 해야" to "해", "아니, 블록이야" to "블록",
            "공룡 그렸어" to "공룡", "엄마를 그렸어" to "엄마", "강아지 그리는 중이야" to "강아지", "강아지 그렸어요" to "강아지",
            "음… 강아지" to "강아지", "그냥 동그라미" to "동그라미", "이건 엄마랑 나야" to "엄마랑 나",
            "이건 우리 집 강아지 뽀삐야" to "우리 집 강아지 뽀삐", "해님이랑 구름" to "해님이랑 구름",
            "어 그러니까 이거는 자동차인데 빨간 거" to "자동차", "내가 좋아하는 티라노사우루스" to "티라노사우루스",
            "그네" to "그네", "모래" to "모래", "의자" to "의자", "새로 그렸어, 땅이야" to "땅",
        )
        named.forEach { (said, name) -> assertEquals("「$said」", name, pieceNameFrom(Reply.Spoke(said))) }
        listOf(
            "몰라", "응", "아니", "새로 그렸어", "배고파", "선생님 보고 싶어", "놀이터에서 그네 탔어",
            "엄마랑 나랑 놀이터에서 노는 거야", "엄마가 그러는데 내일 비 온대",
        ).forEach { assertEquals("「$it」은 이름이 아니다", null, pieceNameFrom(Reply.Spoke(it))) }
    }

    /** D3 — 필수 두 칸 다음에 이름 없는 조각 하나를 「이건 뭐 그린 거야?」로 묻는다. 카드에는 그 조각만 (프로토타입 nextD3) */
    @Test
    fun afterTheRequiredTwoOttoAsksAboutOneUnnamedPiece() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.2f)
        assertTrue(d.push("✅ 다 그렸어"))
        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        assertTrue(d.push("🎬 오늘 이야기 시연 답"))
        assertTrue(await { "무슨 일이" in s.line } != null)
        assertTrue(d.push("🎬 오늘 이야기 시연 답"))
        assertTrue("필수 두 칸 다음에 조각을 묻지 않았다 — 말=${s.line}", await { s.line == "이건 뭐 그린 거야?" } != null)
        assertTrue("카드가 그 조각만 꽂지 않았다", s.diaryDay.focusPiece != null)
        assertTrue(d.push("우리 집이야"))
        assertTrue(await { s.diaryDay.pieces.single().name == "우리 집" } != null)
        assertTrue(await { s.diaryDay.focusPiece == null } != null)
    }

    @Test
    fun ottoEchoesWhatTheChildSaidAsYou() {
        assertEquals("놀이터 갔구나!", echoBack("놀이터 갔어"))
        assertEquals("너는 블록을 쌓았구나!", echoBack("나는 블록을 쌓았어."))
        assertEquals("우리 집이구나!", echoBack("우리 집이야"))
        assertEquals("네가 먼저 탔구나!", echoBack("내가 먼저 탔어!"))
        assertEquals("재미있어, 그랬구나!", echoBack("재미있어"))
    }

    /** D5 — 제목을 누르면 「이 일기 제목은 뭐로 할까?」, 아이 말 그대로 제목 칸에(아이 출처) */
    @Test
    fun tappingTheTitleAsksForOneInTheChildsWords() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        var guard = 0
        while (s.stage !is DiaryPaper && guard++ < 20) {
            if (await(2_000) { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } == null) break
            d.push("🎬 오늘 이야기 시연 답")
        }
        assertTrue(await { s.stage is DiaryPaper } != null)
        assertTrue(await { d.send(Reply.Tapped("title", "제목")); s.line == "이 일기 제목은 뭐로 할까?" } != null)
        assertTrue(d.push("신나는 하루"))
        assertTrue(await { s.title == "신나는 하루" } != null)
        assertEquals("child", s.slotBy["title"])
    }

    /** 이름 붙은 조각에 닿게 그리면 「우리 집에 더 그린 거야, 새로 그린 거야?」 — 「더 그렸어」면 합치고, 「새로 그렸어, 땅이야」면 새 조각 */
    @Test
    fun drawingOnANamedPieceAsksMoreOrNew() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertTrue(d.push("우리 집이야"))
        assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        s.drawing += stroke(0.12f)                                   // 집에 닿게
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우리 집에 더 그린 거야, 새로 그린 거야?" } != null)
        assertTrue(d.push("더 그렸어"))
        assertTrue(await { "우리 집에 더 그렸구나" in s.line } != null)
        assertEquals("합치지 않았다", 1, s.diaryDay.pieces.size)
        assertEquals(2, s.diaryDay.pieces.single().strokes.size)
    }

    /**
     * 멀리 그린 조각이 다른 조각 이름을 불러도 바로 합치지 않는다 — 먼저 묻고, 「새로 그렸어」면 제 이름을 갖는다.
     * 10-01 실기기: 「이건 우리 집 강아지 뽀삐야」에 멀리 있던 그림이 앞 조각에 말없이 합쳐졌다
     */
    @Test
    fun aFarPieceThatMentionsANamedOneIsAskedBeforeMerging() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue("말=${s.line}", await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertEquals("강아지", s.diaryDay.pieces.single().name)
        assertTrue(d.push("아니"))
        s.drawing += stroke(0.7f)                                    // 멀리
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("이건 우리 강아지 뽀삐야")
        assertTrue("묻지 않고 합쳤다 — 말=${s.line}", await { s.line == "강아지에 더 그린 거야, 새로 그린 거야?" } != null)
        d.speak("새로 그렸어")
        assertTrue(await { s.diaryDay.pieces.size == 2 && s.diaryDay.pieces.last().name != null } != null)
        assertEquals("우리 강아지 뽀삐", s.diaryDay.pieces.last().name)
    }
}
