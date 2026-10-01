package com.example.finalproject_demo

import com.example.finalproject_demo.demo.PUZZLE_TEXT
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.NOT_HEARD_AFTER
import com.example.finalproject_demo.demo.NOT_HEARD_THERE
import com.example.finalproject_demo.demo.PieceMove
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 그림일기 책 짜기 — `docs/일기모드_흐름.html` 「엔드 픽처」의 두 날을 그대로 만들어 읽는다.
 * 쪽은 칸이 찬 만큼 · 주어는 「나는」 · 빈 칸은 지어내지 않는다.
 */
class DiaryBookTest {

    /** 많이 말한 날 — 필수 두 칸 + 선택 칸이 다 찼다 (엔드 픽처 6쪽) */
    private val bigDay = DiaryBookInput(
        lines = mapOf(
            "place" to "어린이집",
            "problem" to "블록을 높이 쌓았는데 탑이 와르르 무너졌어",
            "reaction" to "너무 속상했어",
            "solution" to "다시 쌓았어",
            "keep" to "내일은 더 높이 쌓을 거야",
        ),
        by = mapOf("place" to "child", "problem" to "child", "reaction" to "child", "solution" to "child", "keep" to "child"),
        pieceNames = listOf("블록", "탑", "나"),
        hasDrawing = true,
    )

    /** 조금 말한 날 — 필수 두 칸만 (엔드 픽처 3쪽) */
    private val smallDay = DiaryBookInput(
        lines = mapOf("place" to "놀이터!", "problem" to "미끄럼틀을 탔어"),
        by = mapOf("place" to "child", "problem" to "child"),
        pieceNames = listOf("미끄럼틀"),
        hasDrawing = true,
    )

    @Test
    fun aBigDayIsSixPagesInTheOrderOfTheEndPicture() {
        val book = buildDiaryBook(bigDay)
        assertEquals(
            listOf(DiaryPageKind.DRAWING, DiaryPageKind.PLACE, DiaryPageKind.PROBLEM, DiaryPageKind.REACTION, DiaryPageKind.SOLUTION, DiaryPageKind.KEEP),
            book.map { it.kind },
        )
        assertEquals(
            listOf(
                "나는 오늘 블록, 탑, 나를 그렸어요.",
                "나는 오늘 어린이집에 갔어요.",
                "블록을 높이 쌓았는데 탑이 와르르 무너졌어요.",
                "그때 너무 속상했어요.",
                "마침내 다시 쌓았어요.",
                "내일은 더 높이 쌓을 거예요.",
            ),
            book.map { it.text },
        )
    }

    @Test
    fun aSmallDayIsThreePagesAndSaysTheEndWasNotHeard() {
        val book = buildDiaryBook(smallDay)
        assertEquals(listOf("나는 오늘 미끄럼틀을 그렸어요.", "나는 오늘 놀이터에 갔어요.", "미끄럼틀을 탔어요."), book.map { it.text })
        assertEquals("an empty ending is said to be empty, never invented", NOT_HEARD_AFTER, book.last().tail)
        assertTrue(book.none { "마침내" in it.text || "알고 보니" in it.text })
    }

    @Test
    fun onlyATroubledDayEndsWithFinally() {
        val calm = smallDay.copy(lines = smallDay.lines + ("solution" to "또 탔어"))
        assertEquals("그러고 나서 또 탔어요.", buildDiaryBook(calm).last { it.kind == DiaryPageKind.SOLUTION }.text)

        // the child said a troubled feeling though the event itself sounds calm
        val upset = calm.copy(lines = calm.lines + ("reaction" to "속상했어"))
        assertEquals("마침내 또 탔어요.", buildDiaryBook(upset).last { it.kind == DiaryPageKind.SOLUTION }.text)
    }

    @Test
    fun anEndingTheMascotFilledIsNotDressedAsTheChilds() {
        val filled = bigDay.copy(by = bigDay.by + ("solution" to "mascot"))
        val solution = buildDiaryBook(filled).single { it.kind == DiaryPageKind.SOLUTION }
        assertEquals("다시 쌓았어요.", solution.text)
        assertEquals("mascot", solution.by)
    }

    @Test
    fun pagesExistOnlyForFilledSlots() {
        val onlyPlace = DiaryBookInput(lines = mapOf("place" to "할머니 집"))
        val book = buildDiaryBook(onlyPlace)
        assertEquals(listOf(DiaryPageKind.PLACE), book.map { it.kind })
        assertEquals(NOT_HEARD_THERE, book.single().tail)
        assertTrue("nothing drawn, nothing said — no book (D6)", buildDiaryBook(DiaryBookInput(emptyMap())).isEmpty())
        assertTrue("never more than six pages", buildDiaryBook(bigDay).size <= 6)
    }

    @Test
    fun aDrawingWithoutNamesIsStillTheFirstPage() {
        val book = buildDiaryBook(DiaryBookInput(emptyMap(), hasDrawing = true))
        assertEquals("내가 오늘 그린 그림이에요.", book.single().text)
        assertNull("no words from the child on this page", book.single().by)
    }

    @Test
    fun aPlaceAlreadySaidAsASentenceIsNotSaidTwice() {
        val book = buildDiaryBook(DiaryBookInput(mapOf("place" to "어린이집에 갔어요")))
        assertEquals("나는 오늘 어린이집에 갔어요.", book.single().text)
        val own = buildDiaryBook(DiaryBookInput(mapOf("place" to "나는 우리 집 앞에서 놀았어")))
        assertEquals("나는 우리 집 앞에서 놀았어요.", own.single().text)
    }

    @Test
    fun theLastLineIsTodaysFeelingUnlessTheChildAlreadySaidOne() {
        val asks = buildDiaryBook(smallDay).last()
        assertTrue("no feeling said — the page asks with faces", asks.asksFeel)
        assertNull(asks.closing)

        val picked = buildDiaryBook(smallDay.copy(feel = DiaryFeel.EXCITED)).last()
        assertFalse(picked.asksFeel)
        assertEquals("오늘은 참 신났어요.", picked.closing)

        val said = buildDiaryBook(bigDay)
        assertTrue("the reaction page already is the feeling", said.none { it.asksFeel || it.closing != null })
    }

    @Test
    fun piecesNamedInTheSentenceComeForwardAndMoveTheWayItSays() {
        val book = buildDiaryBook(bigDay)
        assertEquals("the drawing page and the place page show every piece", listOf("블록", "탑", "나"), book[0].cast)
        assertEquals(listOf("블록", "탑", "나"), book[1].cast)
        val problem = book.single { it.kind == DiaryPageKind.PROBLEM }
        assertEquals(listOf("블록", "탑"), problem.cast)
        assertEquals(PieceMove.TOPPLE, problem.move)
        assertEquals(PieceMove.BUILD, book.single { it.kind == DiaryPageKind.SOLUTION }.move)
    }

    @Test
    fun aPieceIsMatchedAsAWordNotInsideAnotherWord() {
        val day = DiaryBookInput(mapOf("problem" to "나무 아래에서 하나 주웠어"), pieceNames = listOf("나", "나무"))
        assertEquals(listOf("나무"), buildDiaryBook(day).single().cast)
        val me = DiaryBookInput(mapOf("problem" to "내가 먼저 뛰었어"), pieceNames = listOf("나"))
        assertEquals(listOf("나"), buildDiaryBook(me).single().cast)
    }

    @Test
    fun aPieceUsedAsThePlaceStaysPut() {
        val day = DiaryBookInput(mapOf("problem" to "우리 집 앞에서 엄마랑 놀았어"), pieceNames = listOf("우리 집", "엄마"))
        val page = buildDiaryBook(day).single()
        assertEquals(setOf("우리 집"), page.still)
        assertTrue("엄마" in page.cast)
    }

    @Test
    fun theBookReadsFromTheSessionState() {
        val s = DemoState().apply { mode = StoryMode.DIARY }
        val d = s.newDiaryDay()
        d.pieces += DiaryPiece(0, emptyList(), "해")
        s.slots["place"] = "놀이터"; s.slotBy["place"] = "child"
        s.slots["problem"] = "그네 탔어"; s.slotBy["problem"] = "child"
        s.slots["detail"] = "the old eleven-step keys are not pages"
        val book = buildDiaryBook(s.diaryBookInput())
        // 그네를 「탔어」 — 한 행동을 말한 날이라 맨 뒤에 놀이(퍼즐) 쪽이 붙는다
        assertEquals(listOf("나는 오늘 해를 그렸어요.", "나는 오늘 놀이터에 갔어요.", "그네 탔어요.", PUZZLE_TEXT), book.map { it.text })
        assertEquals("child", book[1].by)
    }

    @Test
    fun noPageCarriesBrokenText() {
        val bad = listOf("{", "}", "null", "  ", "..", "요요", "!.", "?.", ".!", "에에", "를를", "요.요")
        for (day in listOf(bigDay, smallDay, smallDay.copy(feel = DiaryFeel.TIRED))) {
            buildDiaryBook(day).forEach { p ->
                val all = listOfNotNull(p.text, p.tail, p.closing).joinToString(" ")
                bad.forEach { assertFalse("\"$it\" in \"$all\"", it in all) }
            }
        }
    }

    /** 서버(`/story` diary)가 쓴 쪽 — 그림 쪽 뒤를 서버 쪽으로 짠다. 서버가 「일 · 마음」을 합쳐 4쪽이어도 맺음은 끝 */
    @Test
    fun theServersPagesFollowTheDrawingPage() {
        val written = listOf(
            "나는 오늘 어린이집에 갔어요.",
            "블록을 높이 쌓았는데 탑이 와르르 무너졌어요. 너무 속상했어요.",
            "마침내 다시 쌓았어요.",
            "내일은 더 높이 쌓을 거예요.",
        )
        val book = buildDiaryBook(bigDay.copy(written = written))
        assertEquals(listOf("나는 오늘 블록, 탑, 나를 그렸어요.") + written, book.map { it.text })
        assertEquals(
            listOf(DiaryPageKind.DRAWING, DiaryPageKind.PLACE, DiaryPageKind.PROBLEM, DiaryPageKind.REACTION, DiaryPageKind.KEEP),
            book.map { it.kind },
        )
        assertTrue("서버 쪽에 앱의 「아직 듣지 못했어요」를 덧붙였다", book.none { it.tail != null })
        assertFalse("마음을 말한 날인데 오늘 기분을 또 묻는다", book.last().asksFeel)
    }

    @Test
    fun aDayWithoutFeelingsStillAsksTheFeelingAfterTheServersPages() {
        val book = buildDiaryBook(smallDay.copy(written = listOf("나는 오늘 놀이터에 갔어요.", "미끄럼틀을 탔어요.", "그 뒤에 어떻게 되었는지는 아직 듣지 못했어요.")))
        assertEquals(4, book.size)
        assertTrue(book.last().asksFeel)
    }

    /** 🧩 놀이 — 그림이 있고 아이가 한 행동을 말한 날에만 맨 뒤에. 기분 줄은 그 앞 쪽에 남는다 */
    @Test
    fun aPuzzlePageFollowsADayTheChildDidSomething() {
        val book = buildDiaryBook(smallDay.copy(missions = true))
        assertEquals(DiaryPageKind.PUZZLE, book.last().kind)
        assertEquals(PUZZLE_TEXT, book.last().text)
        assertTrue("기분 줄이 놀이 쪽으로 갔다", book[book.lastIndex - 1].asksFeel)
        assertFalse(book.last().asksFeel)
        val quiet = buildDiaryBook(smallDay.copy(missions = true, lines = mapOf("place" to "놀이터")))
        assertTrue("한 행동을 말하지 않은 날에 놀이를 붙였다", quiet.none { it.kind == DiaryPageKind.PUZZLE })
        val noDrawing = buildDiaryBook(smallDay.copy(missions = true, hasDrawing = false))
        assertTrue("그림 없는 날에 그림 맞추기를 붙였다", noDrawing.none { it.kind == DiaryPageKind.PUZZLE })
    }
}
