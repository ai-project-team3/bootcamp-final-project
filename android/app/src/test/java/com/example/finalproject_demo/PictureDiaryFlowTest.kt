package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.atPlace
import com.example.finalproject_demo.demo.diaryPlaceWord
import com.example.finalproject_demo.demo.renameTarget
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.DONE_CHECK_EVERY
import com.example.finalproject_demo.demo.CRAYON_PAUSE
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
import com.example.finalproject_demo.demo.WENT_QUIET
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.dateTitle
import com.example.finalproject_demo.demo.drawWords
import com.example.finalproject_demo.demo.echoBack
import com.example.finalproject_demo.demo.hasDiaryCover
import com.example.finalproject_demo.demo.coverKey
import com.example.finalproject_demo.demo.pieceNameFrom
import com.example.finalproject_demo.demo.namesIn
import com.example.finalproject_demo.demo.splitAcross
import com.example.finalproject_demo.demo.saysNothing
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.praiseFor
import com.example.finalproject_demo.demo.sendBoardTool
import com.example.finalproject_demo.demo.soundsLikeAName
import com.example.finalproject_demo.demo.yesNoOf
import com.example.finalproject_demo.demo.you
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                ?: s.buttons.firstOrNull { "대답 없음" in it.label }      // 다 읽은 뒤 제목을 묻는다 — 말 없이 넘긴다
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
    fun aTalkingDayAsksEveryEmptySlotAndBecomesAPictureDiary() = run { d ->
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
        assertEquals("다 그린 뒤 빈 칸 넷(어디 · 무슨 일 · 결말 · 내일)을 다 묻는다 — 전체 상한 없음 (#89)", 4, s.stepsDone)
        assertEquals("story_ready", s.endReason)
        assertEquals(listOf("child", "child", "child"), listOf("place", "problem", "solution").map { s.slotBy[it] })
        assertTrue("빈 칸을 마스코트가 메웠다: ${s.slotBy}", s.slotBy.values.none { it == "mascot" })
        assertEquals("「내일」까지 묻는다", "child", s.slotBy["keep"])

        assertTrue("그림일기로 안 왔다", await { s.stage is DiaryPaper && "나는 오늘" in s.line } != null)
        val book = buildDiaryBook(s.diaryBookInput())
        assertEquals(
            listOf("나는 오늘 어린이집에 갔어요.", "높이 쌓은 블록이 와르르 무너졌어요.", "마침내 다시 쌓은 블록은 이번엔 무너지지 않았어요.",
                "내일은 아래를 튼튼하게 해서 쌓기로 마음먹었어요."),
            book.map { it.text },
        )
        assertTrue("오늘 기분을 묻지 않았다", book.last().asksFeel)

        d.readToTheEnd()
        assertTrue("그림일기 책 선물(D6)로 안 갔다 (장면=${s.scene} · ${s.stage})", await { s.stage is DiaryGift } != null)
        assertEquals("얼굴로 고른 기분", "오늘은 참 신났어요.", s.diaryDay.feel?.line)
        assertTrue(d.push("책장에 꽂기"))
        assertTrue("책장으로 안 갔다", await { s.scene == Scene.SHELF } != null)
        assertEquals("오늘 그림일기가 책장 맨 앞에 꽂히지 않았다", 4, s.shelf.first().pages)
    }

    @Test
    fun dontKnowIsAskedOnceMoreThenLeftEmptyNotInvented() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))

        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        d.speak("놀이터 갔어")                       // 서버 모드의 답 — 대본 값이 없다
        assertTrue(await { s.line == "놀이터에서 무슨 일이 있었어?" } != null)
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
    fun everyPieceIsAskedWhileDrawingWithNoCap() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        // 상한 없음 — 세 번째 · 네 번째 조각도 묻는다 (10-02 실기기: 두 번 묻고 나면 새 조각을 그려도 안 물었다)
        repeat(4) { i ->
            s.drawing += stroke(0.05f + 0.25f * i)
            assertTrue("${i + 1}번째 조각을 묻지 않았다", d.push("붓이 멈춤") && await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
            assertTrue(d.push("대답 없음"))
            assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
        }
        assertEquals(4, s.diaryDay.pieces.size)
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
        assertTrue("책장 표지가 아이 그림이 아니다", s.hasDiaryCover(s.shelf.first().coverKey()))
    }

    /** 그림판 옆 버튼이 없다 — 물을 것이 떨어지면 오또가 「다 그렸어?」라고 묻고, 말로 답해 끝낸다 (docs/일기모드_UI.html) */
    @Test
    fun whenNothingIsLeftToAskOttoAsksIfDoneAndAVoiceYesEndsDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        repeat(2) { i ->
            s.drawing += stroke(0.1f + 0.3f * i)
            assertTrue(d.push("붓이 멈춤"))
            assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
            assertTrue(d.push("대답 없음"))
            assertTrue(await { s.buttons.any { "붓이 멈춤" in it.label } } != null)
        }
        // 물을 것 없는 멈춤(새 조각 없이) — 처음 몇 번은 지켜보기만 하고, [DONE_CHECK_EVERY] 번째에 묻는다
        repeat(DONE_CHECK_EVERY - 1) {
            val watched = s.log.count { "이미 물었다" in it }
            assertTrue(await {
                s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
                s.log.count { "이미 물었다" in it } > watched
            } != null)
        }
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
        assertTrue("묻지 않고 들은 이름에도 「나도 그려볼까?」가 와야 한다", await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
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

    /**
     * 잘못 들은 이름은 고친다 (10-02 진웅) — 「나도 ○○ 그려볼까?」에 「아니, 집이야」, 그리는 중 아무 때나 「집 아니야, 나무야」,
     * 이름표를 길게 누르면 다시 묻는다. 고친 이름도 아이 말이다
     */
    @Test
    fun aMisheardPieceNameCanBeCorrected() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("해야")                                               // 잘못 들었다
        assertTrue(await { s.line == "나도 해를 그려볼까?" } != null)
        d.tell("아니, 집이야") { s.line == "나도 집을 그려볼까?" }
        assertEquals("집", s.diaryDay.pieces.single().name)
        assertTrue(d.push("아니"))
        d.tell("집 아니야, 나무야") { s.line == "아, 나무구나!" }
        assertEquals("나무", s.diaryDay.pieces.single().name)
        val id = s.diaryDay.pieces.single().id
        assertTrue(await { s.diaryDay.watching } != null)
        d.send(Reply.Tapped("rename:$id", "이름 고치기"))
        assertTrue(await { s.line == "이건 뭐야? 다시 말해 줘!" } != null)
        d.speak("사과나무야")
        assertTrue(await { s.line == "아, 사과나무구나!" } != null)
        assertEquals("사과나무", s.diaryDay.pieces.single().name)
        assertEquals("child", s.slotBy["whiteboard"])
    }

    /** 다른 조각의 이름표를 누르고 「그려줘」 — 마지막에 그린 조각이 아니라 누른 조각을. 새 획을 그으면 다시 방금 그린 조각 (10-02 실기기) */
    @Test
    fun drawMeAfterTappingATagAimsAtThatPiece() = run { d ->
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
        val dog = s.diaryDay.pieces.first { it.name == "강아지" }.id
        assertTrue(await { s.diaryDay.watching } != null)
        d.send(Reply.Tapped("name:$dog", "이름 부르기"))
        assertTrue(await { s.line == "강아지!" } != null)
        d.tell("그려줘") { s.line == "나도 강아지를 그려볼게! 더 그리고 있어!" }
    }

    /**
     * 오또가 「뭐 그린 거야?」라고 묻는 사이 이름표를 누르고 [그려 줘] — 질문의 답으로 섞이지 않는다.
     * 질문을 거두고 누른 조각을 부른 뒤 그 조각을 그린다. 묻던 조각은 이름 없이 남는다 (10-05 진웅)
     */
    @Test
    fun boardToolsWhileOttoAsksAreNotTakenAsTheAnswer() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue(await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        s.drawing += stroke(0.7f)
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertTrue(s.diaryDay.drawingTalk && !s.diaryDay.watching)            // 묻는 중 — 전에는 그림판 조작이 꺼졌다
        val dog = s.diaryDay.pieces.first { it.name == "강아지" }.id
        s.diaryDay.focus = dog to s.drawing.size                            // 화면이 이름표를 누를 때 하는 일
        d.sendBoardTool(Reply.Tapped("name:$dog", "이름 부르기"))
        assertTrue("말=${s.line}", await { s.line == "강아지!" } != null)
        assertNull("이름표 누름이 묻던 조각의 답이 됐다", s.diaryDay.pieces.last().name)
        d.sendBoardTool(Reply.Tapped("drawme", "그려 줘"))
        assertTrue("말=${s.line}", await { s.line == "나도 강아지를 그려볼게! 더 그리고 있어!" } != null)
        assertNull(s.diaryDay.pendingTap)
    }

    /**
     * 「나도 해를 그려볼까?」 사이 [이름 고치기]로 집으로 고치고 [그려 줘]로 주문했다 — 미뤄 둔 제안이
     * 옛 이름으로 다시 나오면 안 된다(10-05 실기기: 「나도 너 안먹어를 그려볼까?」가 오또 그림을 받은 뒤 다시 나왔다)
     */
    @Test
    fun aHeldOfferIsDroppedOnceThePieceIsRenamedAndOrdered() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("해야")                                               // 잘못 들었다
        assertTrue(await { s.line == "나도 해를 그려볼까?" } != null)
        delay(300)                                                     // 제안이 답을 기다리기 시작한 뒤에 누른다(폰: → rename)
        d.sendBoardTool(Reply.Tapped("rename", "이름 고치기"))
        assertTrue("말=${s.line}", await { s.line == "이건 뭐야? 다시 말해 줘!" } != null)
        d.speak("집이야")
        assertTrue(await { s.line == "아, 집이구나!" } != null)
        d.sendBoardTool(Reply.Tapped("drawme", "그려 줘"))
        assertTrue("말=${s.line}", await { s.line == "나도 집을 그려볼게! 더 그리고 있어!" } != null)
        val said = mutableListOf<String>()
        val ear = launch { while (true) { if (said.lastOrNull() != s.line) said += s.line; delay(3) } }
        // 오또 그림이 오면 고르고(폰에서는 고른 뒤 다음 멈춤에 옛 제안이 나왔다), 아니면 붓을 멈춘다
        repeat(6) {
            await(1_000) { (s.buttons.firstOrNull { "오또 그림으로" in it.label } ?: s.buttons.firstOrNull { "붓이 멈춤" in it.label })?.onClick(); false }
        }
        ear.cancel()
        assertFalse("미뤄 둔 옛 제안이 나왔다 — $said", said.any { "해를 그려볼까" in it })
        assertFalse("주문한 조각을 또 그려 줄까 물었다 — $said", said.any { "집을 그려볼까" in it })
    }

    /** 그림판 오른쪽 [그려 줘] — 「그려줘」라고 말한 것과 같다. 방금 그린 조각을 오또가 그린다 (10-05 진웅) */
    @Test
    fun theDrawMeButtonOrdersOttosDrawingOfTheLastPiece() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue(await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue(await { s.diaryDay.watching } != null)
        d.send(Reply.Tapped("drawme", "그려 줘"))
        assertTrue("[그려 줘]에 그리지 않았다 — 말=${s.line}", await { s.line == "나도 강아지를 그려볼게! 더 그리고 있어!" } != null)
    }

    /** 그림판 오른쪽 [이름 고치기] — 방금 그린 조각의 이름을 다시 묻고 고친다. 고친 이름도 아이 말이다 (10-05 진웅) */
    @Test
    fun theRenameButtonAsksAgainForTheLastNamedPiece() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("해야")                                               // 잘못 들었다
        assertTrue(await { s.line == "나도 해를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue(await { s.diaryDay.watching } != null)
        d.send(Reply.Tapped("rename", "이름 고치기"))
        assertTrue(await { s.line == "이건 뭐야? 다시 말해 줘!" } != null)
        d.speak("집이야")
        assertTrue(await { s.line == "아, 집이구나!" } != null)
        assertEquals("집", s.diaryDay.pieces.single().name)
        assertEquals("child", s.slotBy["whiteboard"])
    }

    /** [이름 고치기]가 가리키는 조각 — 방금 누른 이름표 → 방금 그린 이름 조각 → 마지막 이름 조각 */
    @Test
    fun theRenameButtonPicksTheTappedThenTheLastDrawnPiece() {
        val s = Director(CoroutineScope(SupervisorJob())).s
        s.newDiaryDay()
        s.drawing += stroke(0.1f); s.drawing += stroke(0.8f)
        val day = s.diaryDay
        day.catchUp(s.drawing)
        day.pieces[0] = day.pieces[0].copy(name = "집"); day.pieces[1] = day.pieces[1].copy(name = "해")
        assertEquals("해", s.renameTarget(day)?.name)                   // 방금 그린 조각
        day.focus = day.pieces[0].id to s.drawing.size
        assertEquals("집", s.renameTarget(day)?.name)                   // 방금 누른 이름표
        s.drawing += stroke(0.45f)
        day.catchUp(s.drawing)                                         // 새 획 — 누른 이름표는 잊는다 · 새 조각엔 이름이 없다
        assertEquals("해", s.renameTarget(day)?.name)
    }

    /** 아이가 말한 곳의 낱말 — 「에」 없이 말해도(「놀이터 갔어」) 뗀다. 못 떼면 null (10-05 진웅) */
    @Test
    fun thePlaceWordComesOutOfTheChildsAnswer() {
        mapOf(
            "놀이터 갔어" to "놀이터", "놀이터에 갔어" to "놀이터", "할머니 집에 다녀왔어" to "할머니 집",
            "놀이터에서 놀았어" to "놀이터", "어린이집" to "어린이집", "바다야" to "바다",
        ).forEach { (said, place) -> assertEquals("「$said」", place, diaryPlaceWord(said)) }
        listOf("몰라", "응", "", null).forEach { assertNull("「$it」은 곳이 아니다", diaryPlaceWord(it)) }
    }

    /** 고정 질문이 아이가 말한 곳을 받아 묻는다 — 「거기서」 대신 「놀이터에서」. 곳을 못 떼면 「거기서」 그대로 */
    @Test
    fun theScriptedQuestionNamesThePlace() = run { d ->
        val s = d.s
        s.slots["place"] = "놀이터 갔어"
        assertEquals("놀이터에서 무슨 일이 있었어?", atPlace(s, "무슨 일이 있었어?"))
        s.slots["place"] = "몰라"
        assertEquals("거기서 무슨 일이 있었어?", atPlace(s, "무슨 일이 있었어?"))
    }

    /** 받아 주기는 낱말 하나 — 「놀이터 갔어」 → 「놀이터구나!」(문장을 통째로 되받지 않는다) */
    @Test
    fun ottoEchoesOneWordNotTheWholeSentence() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        d.speak("놀이터 갔어")
        assertTrue("낱말 하나로 받지 않았다 — 말=${s.line}", await { s.line == "놀이터구나!" } != null)
    }

    /**
     * 질문 목소리가 끝나기 전 🎤 로 한 답도 그 질문의 답이다 — 마이크가 열린 뒤 온 말은 버리지 않는다.
     * 질문 목소리가 늦게 오면(10-05 실기기 /tts 11.6초) 화면에 뜬 질문을 보고 먼저 답했고, 그 답이 버려져 다시 말해야 했다
     */
    @Test
    fun anAnswerGivenBeforeTheQuestionsVoiceEndsIsKept() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        assertTrue(await { s.line == "오늘 어디 갔었어?" && s.micEnabled } != null)
        s.speed = 1.0                                                  // 말이 끝나길 기다리는 1.2초 — 그 사이에 답한다
        d.send(Reply.Spoke("놀이터 갔어"))                               // 한 번만 — 다시 보내지 않는다
        assertTrue("질문 목소리가 끝나기 전 한 답이 버려졌다 — 말=${s.line}", await(4_000) { s.slots["place"] != null } != null)
    }

    /**
     * 오또가 묻자마자(마이크가 열리자마자) 한 답도 받는다 — 전에는 마이크를 연 뒤 오또 말이 끝나길 기다렸다가 앞 입력을 비워서,
     * 오또가 말하는 사이 한 답이 버려지고 10초 뒤 「말이 없었다」로 거뒀다 (10-05 실기기 · VoiceInbox)
     */
    @Test
    fun anAnswerGivenTheMomentTheMicOpensIsKept() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" && s.micEnabled } != null)
        d.send(Reply.Spoke("미끄럼틀"))                                   // 열리자마자 — 다시 보내지 않는다
        assertTrue("마이크가 열리자마자 한 답이 버려졌다 — 말=${s.line}", await { s.diaryDay.pieces.any { it.name == "미끄럼틀" } } != null)
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

    /**
     * 「나도 그려볼까?」는 이름을 들으면 바로 — 다만 답하는 사이 새 선을 긋기 시작했으면 그리기를 끊지 않고,
     * 새 조각을 먼저 묻고, 물을 것 없는 멈춤에 미뤄 둔 제안을 한다 (10-01 진웅)
     */
    @Test
    fun theOfferWaitsWhileTheChildIsDrawingSomethingNew() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        s.drawing += stroke(0.12f)                                   // 답하기 전에 그 조각에 더 그린다
        d.speak("강아지")
        assertTrue(await { s.line == "강아지구나!" } != null)
        delay(300)
        assertTrue("그리는 중인데 바로 제안했다", s.line != "나도 강아지를 그려볼까?")
        assertTrue("미뤄 둔 제안이 오지 않았다 — 말=${s.line}",
            await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "나도 강아지를 그려볼까?" } != null)
    }

    /**
     * 아이가 말하는 중(녹음 중)이면 오또가 묻지 않는다 — 오또 목소리가 아이 말과 같이 녹음됐다(10-01 실기기).
     * 그 말이 이름이면 이름으로 받고, 「나도 그려볼까?」는 그 뒤에
     */
    @Test
    fun ottoDoesNotAskOverTheChildTalking() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(await { s.diaryDay.watching } != null)
        s.micOn = true                                               // 아이가 🎤를 누르고 말하는 중
        d.send(Reply.Tapped("pause", "멈춤"))                        // 그 사이 붓 멈춤이 먼저 와 있었다
        delay(300)
        assertTrue("말하는 중에 물었다 — 말=${s.line}", s.line != "우와, 지금 그리는 건 뭐야?")
        s.micOn = false
        d.tell("강아지야") { s.diaryDay.pieces.single().name == "강아지" }
        assertTrue("말=${s.line}", await { s.line == "나도 강아지를 그려볼까?" } != null)
    }

    /** 판을 가로지르는 땅선(배경)을 그리면 「뭐 그린 거야?」 대신 「여기는 어디야?」 — 답은 장소 칸으로, 오또가 다시 그려 주겠다고 하지 않는다 */
    @Test
    fun drawingTheGroundAsksWhereThisIs() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += Stroke(Color.Green, listOf(Offset(.05f, .85f), Offset(.50f, .88f), Offset(.95f, .86f)))
        assertTrue(d.push("붓이 멈춤"))
        assertTrue("배경인데 장소를 묻지 않았다 — 말=${s.line}", await { s.line == "여기는 어디야?" } != null)
        assertEquals(com.example.finalproject_demo.demo.PieceRole.BACKGROUND, s.diaryDay.pieces.single().role)
        d.speak("놀이터")
        assertTrue(await { s.slots["place"] == "놀이터" } != null)
        delay(300)
        assertTrue("배경을 다시 그려 주겠다고 했다", "그려볼까" !in s.line)
    }

    /**
     * 그림 실마리 (DiaryClue.kt · 10-05) — 그린 것 중 아이가 아직 말하지 않은 것이 있으면 둘째 이야기 질문이 그것을 짚는다.
     * 「미끄럼틀에서 무슨 일이 있었어?」(탄 일을 전제)가 아니라 「미끄럼틀 이야기 해 줄래?」
     */
    @Test
    fun theSecondStoryQuestionWhileDrawingTakesAnUnsaidDrawingAsItsClue() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        s.diaryDay.catchUp(s.drawing)
        s.diaryDay.pieces[0] = s.diaryDay.pieces[0].copy(name = "미끄럼틀")       // 이름은 붙었고 아이 말에는 아직 없다
        assertTrue("물을 조각이 없는데 이야기를 묻지 않았다 — 말=${s.line}",
            await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "여기는 어디야?" } != null)
        d.speak("놀이터 갔어")
        assertTrue("그린 것을 실마리로 묻지 않았다 — 말=${s.line}",
            await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "미끄럼틀도 그렸네! 미끄럼틀 이야기 해 줄래?" } != null)
    }

    /**
     * 물을 조각이 없는 붓 멈춤 — 그리는 중에 이야기를 묻는다(어디 → 무슨 일). 답은 아이 말 그대로 그 칸에(아이 출처),
     * 다 그린 뒤에는 그 칸을 다시 묻지 않는다 (10-02 진웅 · 프로토타입)
     */
    @Test
    fun withNothingToAskOttoAsksAboutTheDayWhileDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("미끄럼틀")
        assertTrue(await { s.line == "나도 미끄럼틀을 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue("물을 조각이 없는데 이야기를 묻지 않았다 — 말=${s.line}",
            await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "여기는 어디야?" } != null)
        d.speak("놀이터 갔어")
        assertTrue(await { s.slots["place"] == "놀이터 갔어" } != null)
        assertEquals("child", s.slotBy["place"])
        assertTrue("둘째 이야기를 묻지 않았다 — 말=${s.line}",
            await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.line == "놀이터에서 무슨 일이 있었어?" } != null)
        d.speak("몰라")
        assertTrue(await { s.line == "괜찮아, 계속 그려 봐!" } != null)
        assertTrue(d.push("✅ 다 그렸어"))
        assertTrue("다 그린 뒤 이미 답한 「어디」를 또 물었다 — 말=${s.line}",
            await { s.stage is DiaryAsk && s.line == "놀이터에서 무슨 일이 있었어?" } != null)
    }

    /**
     * 크레용을 고른 뒤의 멈춤은 「다 그렸어?」로 세지 않는다 · 말없이 거둔 「다 그렸어?」는 말풍선에 남기지 않는다 (10-02 실기기)
     */
    @Test
    fun aCrayonPauseIsNotCountedAndAWithdrawnQuestionLeavesTheBubble() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue(await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue(await { s.diaryDay.watching } != null)
        // 이야기 칸은 이미 찼다고 둔다 — 그리는 중 이야기 질문 없이 「다 그렸어?」 세기만 본다
        s.slots["place"] = "놀이터"; s.slots["problem"] = "넘어졌어"
        repeat(3) {                                                  // 크레용 뒤 멈춤 셋 — 세지 않는다
            assertTrue(await { s.diaryDay.watching } != null)
            d.send(Reply.Tapped("pause", CRAYON_PAUSE)); delay(80)
        }
        assertTrue("크레용 멈춤을 「다 그렸어?」로 셌다", s.line != "다 그렸어? 더 그릴 거 있어?")
        repeat(DONE_CHECK_EVERY) {
            assertTrue(await { s.diaryDay.watching } != null)
            d.send(Reply.Tapped("pause", "붓 멈춤")); delay(80)
        }
        assertTrue(await { s.line == "다 그렸어? 더 그릴 거 있어?" } != null)
        assertTrue("말없이 거둔 질문이 말풍선에 남았다 — 말=${s.line}", await { s.line.isEmpty() } != null)
    }

    /**
     * 받아쓰기가 잘못 들어 이름이 안 나오면 한 번 더 묻는다. 그래도 안 되면 「계속 그려 봐」로 두되,
     * 아이가 다시 말하면 「○○야」 꼴이 아니어도 그 조각 이름으로 받는다 — 새 조각을 그려야만 진행되던 막힘 (10-02 실기기)
     */
    @Test
    fun aMisheardNameIsAskedAgainAndCanStillBeGiven() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("배고파")                                             // 잘못 알아들은 말
        assertTrue("다시 묻지 않았다 — 말=${s.line}", await { s.line == "잘 못 들었어. 뭐 그린 거야?" } != null)
        d.speak("배고파")
        assertTrue(await { s.line == "그래, 계속 그려 봐." } != null)
        assertNull(s.diaryDay.pieces.single().name)
        d.tell("로켓") { s.diaryDay.pieces.single().name == "로켓" }   // 「로켓이야」 꼴이 아니어도
        assertTrue("말=${s.line}", await { s.line == "나도 로켓을 그려볼까?" } != null)
    }

    /**
     * 오또가 말하는 사이(「좋아, 네 그림이 최고야!」) 새로 그린 조각 — 그 사이 온 붓 멈춤을 남겨 두었다가,
     * 말이 끝나고 흐름이 돌아오면 새 획 없이도 그 조각을 묻는다 (10-02 실기기)
     */
    @Test
    fun aPieceDrawnWhileOttoTalksIsAskedAfter() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("강아지")
        assertTrue(await { s.line == "나도 강아지를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue(await { s.line == "좋아, 네 그림이 최고야!" } != null)
        s.drawing += stroke(0.7f)                                    // 오또가 말하는 사이 멀리 새로 그렸다
        s.diaryDay.pendingPause = "붓 멈춤"                           // 그림판이 남겨 둔 붓 멈춤
        assertTrue("말이 끝난 뒤 새 조각을 묻지 않았다 — 말=${s.line}",
            await { s.line == "우와, 지금 그리는 건 뭐야?" && s.diaryDay.askingPiece != s.diaryDay.pieces.first().id } != null)
        assertNull(s.diaryDay.pendingPause)
    }

    /** D1 질문 중 다른 조각을 그리기 시작하면 조용히 거두고, 다음 멈춤에 지금 그리는 조각을 먼저 묻는다 (10-01 안 A) */
    @Test
    fun drawingSomethingElseWithdrawsTheQuestion() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        val first = s.diaryDay.askingPiece
        s.drawing += stroke(0.7f)                                    // 멀리 — 다른 조각
        assertTrue("다른 조각을 그리는데 질문을 거두지 않았다", await { s.diaryDay.askingPiece == null } != null)
        assertTrue("거둘 때 말을 했다 — 말=${s.line}", s.line.isEmpty())
        assertTrue(await { s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick(); s.diaryDay.askingPiece != null } != null)
        assertTrue("지금 그리는 조각을 먼저 묻지 않았다", s.diaryDay.askingPiece != first)
        assertEquals("우와, 지금 그리는 건 뭐야?", s.line)
        assertTrue("앞 조각에 이름이 붙었다", s.diaryDay.pieces.first { it.id == first }.name == null)
    }

    /** 묻는 조각을 계속 그리는 동안에는 질문을 열어 두고 시간을 세지 않는다 · 손을 놓고 조용하면 거둔다 (10-01 안 A) */
    @Test
    fun drawingTheSamePieceKeepsTheQuestionOpen() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        // 기다림 10초 = 시험 속도로 약 0.1초 · 그 세 배 동안 같은 조각에 계속 그린다
        repeat(6) { i -> s.drawing += stroke(0.1f + i * 0.005f); delay(50) }
        assertTrue("같은 조각을 그리는데 질문을 거뒀다", s.diaryDay.askingPiece != null)
        // 긴 선을 긋는 중(손가락이 판에 닿아 있음) — 획은 손을 떼야 들어오지만, 그 사이도 조용한 게 아니다 (10-02 실기기)
        s.diaryDay.penDown = true
        delay(300)
        assertTrue("선을 긋는 중인데 질문을 거뒀다", s.diaryDay.askingPiece != null)
        s.diaryDay.penDown = false
        // 손을 놓고 조용하면 거둔다
        assertTrue("조용한데 거두지 않았다", await { s.diaryDay.askingPiece == null } != null)
        assertTrue(await { s.line == "계속 그려 봐!" } != null)
    }

    /** 그냥 이름표를 톡 — 오또가 이름을 불러 준다(「나」면 「너!」) · 그리기는 그대로 (프로토타입 tapTag) */
    @Test
    fun tappingANameTagCallsTheName() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("나야")
        assertTrue(await { s.line == "나도 너를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        val id = s.diaryDay.pieces.single().id
        assertTrue("말=${s.line}", await { if (s.diaryDay.watching) d.send(Reply.Tapped("name:$id", "이름 부르기")); s.line == "너!" } != null)
        assertTrue("그리기가 끝났다", s.stage is DiaryBoard)
    }

    /** 그림 주문에는 아이 자신을 「아이」로 — 서버가 「나」만 오면 거절했다(10-01 도메인 서버) */
    @Test
    fun theDrawingOrderCallsTheChildAChild() {
        assertEquals("아이", drawWords("나"))
        assertEquals("아이", drawWords("저"))
        assertEquals("엄마랑 아이", drawWords("엄마랑 나"))
        assertEquals("우리 집", drawWords("우리 집"))
        assertEquals("나비", drawWords("나비"))
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
        // 「조개 그렸어」 — 무엇을 그렸는지 말한 것도 이름이다 (10-05 실기기)
        assertTrue(soundsLikeAName("조개 그렸어."))
        assertTrue(soundsLikeAName("조개를 그렸어"))
        assertFalse(soundsLikeAName("놀이터에서 그네 탔어"))
        assertFalse(soundsLikeAName("새로 그렸어"))
    }

    /** 「내일 또 하고 싶은 거 있어?」에 「없어」 — 하고 싶은 게 없다는 말이라 칸을 채우지 않는다 (10-05 실기기 · 「없어」가 내일 칸에 들어갔다) */
    @Test
    fun nothingForTomorrowIsNotAnAnswer() {
        listOf("없어", "없어요.", "음 없어", "하고 싶은 거 없어", "아니", "아니요", "없는데").forEach { assertTrue("「$it」", saysNothing(it)) }
        listOf("놀이터 갈래", "또 바다 가고 싶어", "내일도 놀이터 갈래.", "강아지가 없어서 찾을 거야").forEach { assertFalse("「$it」", saysNothing(it)) }
    }

    @Test
    fun nothingForTomorrowLeavesTheSlotEmpty() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        d.speak("놀이터 갔어")
        assertTrue(await { s.line == "놀이터에서 무슨 일이 있었어?" } != null)
        d.speak("그네 탔어")
        assertTrue(await { s.line == "그래서 어떻게 됐어?" } != null)
        d.speak("집에 왔어")
        assertTrue(await { s.line == "내일 또 하고 싶은 거 있어?" } != null)
        d.speak("없어")
        assertTrue("「없어」 뒤 책으로 가지 않았다 — 말=${s.line}", await { s.stage is DiaryPaper } != null)
        assertTrue("「없어」가 내일 칸에 들어갔다 — ${s.slots["keep"]}", s.slots["keep"].isNullOrBlank())
    }

    /**
     * 이름 붙은 조각에 이어 그리고 「조개 그렸어」 — 조각은 하나로 그대로 두고, 그린 것 목록에 조개를 더한다.
     * 전에는 「그렇구나! 계속 그려 봐」로 넘겨 책 첫 쪽에서 조개가 빠졌다 (10-05 실기기 · 바다에 붙여 그린 조개)
     */
    @Test
    fun sayingWhatWasDrawnOnANamedPieceAddsItToTheDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("바다")
        assertTrue(await { s.line == "나도 바다를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        s.drawing += Stroke(Color.Red, listOf(Offset(0.12f, 0.3f), Offset(0.2f, 0.5f)))   // 바다에 다른 색으로 이어 그린다 — 같은 조각
        d.tell("조개 그렸어") { "조개" in s.diaryDay.pieceNames }
        assertTrue("받아 주기 — 말=${s.line}", await { s.line == "조개도 그렸구나!" } != null)
        assertEquals(listOf("바다", "조개"), s.diaryDay.pieceNames)
        assertEquals(1, s.diaryDay.pieces.size)
    }

    /** 이름 없는 새 조각을 그리며 「조개 그렸어」 — 그 조각의 이름이다(「조개야」 꼴만 받던 것을 넓혔다) */
    @Test
    fun sayingWhatWasDrawnNamesTheNewPiece() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.speak("바다")
        assertTrue(await { s.line == "나도 바다를 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        s.drawing += stroke(0.12f)                                   // 같은 색으로 옆에 — 새 조각
        d.tell("조개 그렸어") { "조개" in s.diaryDay.pieceNames }
        assertEquals(2, s.diaryDay.pieces.size)
        assertEquals("조개", s.diaryDay.pieces.last().name)
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
            "그네" to "그네", "모래" to "모래", "의자" to "의자",
            // 받아쓰기가 말을 되풀이해 적을 때 (10-02 실기기 「나무 나무」)
            "나무 나무" to "나무", "나무 나무야" to "나무", "강아지 강아지" to "강아지", "우리 집 우리 집" to "우리 집",
            "이건 꽃 꽃이야" to "꽃", "새로 그렸어, 땅이야" to "땅", "새로 그린 거요, 집이에요" to "집",
        )
        named.forEach { (said, name) -> assertEquals("「$said」", name, pieceNameFrom(Reply.Spoke(said))) }
        listOf(
            "몰라", "응", "아니", "새로 그렸어", "배고파", "선생님 보고 싶어", "놀이터에서 그네 탔어",
            "엄마랑 나랑 놀이터에서 노는 거야", "엄마가 그러는데 내일 비 온대",
            // 「새로 그린 거야」가 「새로 그린 거요」로 들렸다 — 「요」가 이름이 됐다 (10-02 실기기)
            "새로 그린 거요", "새로 그린 거예요", "새로 그린 거야", "요", "야",
        ).forEach { assertEquals("「$it」은 이름이 아니다", null, pieceNameFrom(Reply.Spoke(it))) }
    }

    /** 「해랑 구름」 — 이어 말한 이름을 낱낱이. 「고양이랑」의 「이」는 낱말이라 남긴다 */
    @Test
    fun namesSaidTogetherComeApart() {
        mapOf(
            "해랑 구름" to listOf("해", "구름"), "미끄럼틀이랑 해" to listOf("미끄럼틀", "해"),
            "고양이랑 강아지" to listOf("고양이", "강아지"), "엄마하고 아빠" to listOf("엄마", "아빠"),
            "집이랑 나무 그리고 해" to listOf("집", "나무", "해"), "우리 집이랑 나무" to listOf("우리 집", "나무"),
            "호랑이" to listOf("호랑이"), "해랑" to listOf("해랑"), "엄마랑 나" to listOf("엄마", "나"),
        ).forEach { (name, parts) -> assertEquals("「$name」", parts, namesIn(name)) }
    }

    /**
     * 「해랑 구름」을 한 번에 말해도 앞에 이름 없는 조각이 있으면 그린 차례대로 나눠 붙인다 — 지금 조각이 마지막 이름.
     * 이름 없는 조각이 모자라면 한 조각에 둘을 그린 것이다 — 「엄마랑 나」처럼 통째로 (10-05 실기기 「해랑 구름」 · 「미끄럼틀이랑 해」)
     */
    @Test
    fun twoNamesAtOnceGoToTheUnnamedPiecesInDrawingOrder() {
        fun p(id: Int, name: String? = null) = DiaryPiece(id, listOf(stroke(id * 0.1f)), name)
        val sun = p(1); val cloud = p(2)
        assertEquals(listOf(1 to "해", 2 to "구름"), splitAcross("해랑 구름", cloud, listOf(sun, cloud)))
        assertEquals(listOf(2 to "해", 3 to "구름"), splitAcross("해랑 구름", p(3), listOf(p(1, "미끄럼틀"), p(2), p(3))))
        assertEquals("한 조각뿐 — 둘을 한 조각에 그렸다", null, splitAcross("엄마랑 나", cloud, listOf(cloud)))
        assertEquals("앞 조각은 이미 이름이 있다", null, splitAcross("해랑 구름", cloud, listOf(p(1, "미끄럼틀"), cloud)))
        assertEquals("이름 셋에 이름 없는 조각 둘", null, splitAcross("집이랑 나무 그리고 해", cloud, listOf(sun, cloud)))
        assertEquals("뒤에 그린 조각은 앞 이름을 받지 않는다", null, splitAcross("해랑 구름", sun, listOf(sun, cloud)))
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
        // 그림 질문은 칸 채우기 질문 수를 깎지 않는다 — 세 번째 칸 질문이 그대로 온다 (10-02)
        assertTrue("조각 질문이 칸 질문 한 번을 썼다 — 말=${s.line}", await { s.line == "그래서 어떻게 됐어?" } != null)
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

    /** 아이가 제목을 말하기 전에는 날짜 제목 — 아이가 하지 않은 말(「○○에서 만난 ○○」)을 넣지 않는다 */
    @Test
    fun beforeTheChildNamesItTheTitleIsJustTheDate() = run { d ->
        assertEquals("10월 2일 그림일기", dateTitle(java.time.LocalDate.of(2026, 10, 2)))
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        var guard = 0
        while (s.stage !is DiaryPaper && guard++ < 20) {
            if (await(2_000) { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } == null) break
            d.push("🎬 오늘 이야기 시연 답")
        }
        assertTrue(await { s.stage is DiaryPaper } != null)
        assertEquals(dateTitle(), s.title)
    }

    /** 시작하고 30분이 지나면 시연 버튼 없이도 마무리를 한 번 제안한다 (#64-1 — 전에는 시연 버튼만 켰다) */
    @Test
    fun thirtyMinutesInOttoOffersToWrapUp() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        assertTrue(await { s.line == "오늘 어디 갔었어?" } != null)
        s.diaryStart = System.currentTimeMillis() - 31L * 60 * 1000          // 31분 전에 시작했다
        assertTrue(d.push("🎬 오늘 이야기 시연 답"))
        assertTrue("30분이 지났는데 마무리를 제안하지 않았다 — 말=${s.line}",
            await { s.line == "오늘 이야기 정말 많이 했다! 이제 그림일기로 만들어 볼까?" } != null)
        assertTrue(d.push("응, 만들자"))
        assertTrue(await { s.stage is DiaryPaper } != null)
    }

    /** 제목을 안 붙였으면 다 읽은 뒤 한 번 묻는다 · 붙였으면 묻지 않고 바로 책을 준다 (10-01 안 2) */
    @Test
    fun theTitleIsAskedOnceAfterReading() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그림 없이 이야기할래"))
        var guard = 0
        while (s.stage !is DiaryPaper && guard++ < 20) {
            if (await(2_000) { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } == null) break
            d.push("🎬 오늘 이야기 시연 답")
        }
        assertTrue(await { s.stage is DiaryPaper } != null)
        guard = 0
        while (s.line != "이 일기 제목은 뭐로 할까?" && guard++ < 30) {
            val b = s.buttons.firstOrNull { "😄" in it.label } ?: s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }
            if (b == null) { delay(20); continue }
            b.onClick(); delay(30)
        }
        assertEquals("다 읽고 제목을 묻지 않았다", "이 일기 제목은 뭐로 할까?", s.line)
        assertTrue(d.push("신나는 하루"))
        assertTrue(await { s.title == "신나는 하루" && s.stage is DiaryGift } != null)
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
        // 합친 뒤 「나도 그려볼까?」에 「응」 — 합쳐져 사라진 조각을 찾다가 죽지 않고, 합친 조각을 그린다
        assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
        assertTrue(d.push("응"))
        assertTrue("말=${s.line}", await { "나도 그려 볼게" in s.line } != null)
        assertTrue("오또 그림을 주문하다 흐름이 멈췄다", d.push("✅ 다 그렸어") && await { s.stage is DiaryAsk } != null)
    }

    /**
     * 오또가 이미 이야기한 조각 위에 **크레용을 바꿔** 새로 그려도 말없이 합치지 않는다 — 그린 것을 묻는다.
     * 10-06 실기기(11:04): 「나무들」 이름을 듣고 30초 뒤 다른 색으로 그 위에 그린 것이 「나무들」에 합쳐지고
     * 「여기는 어디야?」가 나왔다
     */
    @Test
    fun aNewCrayonOverAPieceOttoAlreadyTalkedAboutIsAskedAbout() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        assertTrue(d.push("우리 집이야"))
        assertTrue(await { s.line == "나도 우리 집을 그려볼까?" } != null)
        assertTrue(d.push("아니"))
        assertTrue(await { s.diaryDay.watching } != null)
        s.drawing += Stroke(Color.Red, listOf(Offset(0.12f, 0.35f), Offset(0.2f, 0.55f)))   // 다른 색 · 집 위에
        assertTrue("말=${s.line}", await {
            s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
            s.line == "우리 집에 더 그린 거야, 새로 그린 거야?" || s.line == "여기는 어디야?"
        } != null)
        assertEquals("새로 그린 것을 묻지 않았다", "우리 집에 더 그린 거야, 새로 그린 거야?", s.line)
    }

    /**
     * 답을 못 들은 조각 곁에 나중에 그린 것도 그 조각에 몰래 붙이지 않는다 — 새로 그린 것을 묻는다.
     * 10-06 실기기(10:44): 대답 없이 지나간 조각 옆에 그린 점들이 합쳐지고 「여기는 어디야?」가 나왔다
     */
    @Test
    fun drawingNextToAnUnansweredPieceAsksAboutTheNewDrawing() = run { d ->
        val s = d.s
        d.go(Scene.DIARY)
        assertTrue(d.push("그릴래"))
        s.drawing += stroke(0.1f)
        assertTrue(d.push("붓이 멈춤"))
        assertTrue(await { s.line == "우와, 지금 그리는 건 뭐야?" } != null)
        d.send(Reply.Tapped(WENT_QUIET, "조용함"))
        assertTrue("말=${s.line}", await { s.diaryDay.watching && s.line != "우와, 지금 그리는 건 뭐야?" } != null)
        assertTrue(s.diaryDay.pieces.single().name == null)
        s.drawing += stroke(0.12f)                                   // 그 조각에 닿게
        assertTrue("말=${s.line}", await {
            s.buttons.firstOrNull { "붓이 멈춤" in it.label }?.onClick()
            s.line == "우와, 지금 그리는 건 뭐야?" || s.line == "여기는 어디야?"
        } != null)
        assertEquals("새로 그린 것을 묻지 않았다", "우와, 지금 그리는 건 뭐야?", s.line)
        assertEquals("말없이 합쳤다", 2, s.diaryDay.pieces.size)
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
