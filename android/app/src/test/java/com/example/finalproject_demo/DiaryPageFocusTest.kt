package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryPage
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryPlanPage
import com.example.finalproject_demo.demo.PieceMove
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.pageFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #220 ④ — 쪽마다 그림이 달라 보이게. 전에는 쪽마다 아이 그림 전체에 그 쪽 글에 나온 조각만 진하게라 다섯 쪽이 거의 같았다.
 * 조각 이야기 쪽은 그 조각이, 다른 쪽은 문장에 나온 조각이 가운데로 나온다(장소 · 그림 · 맞추기 쪽은 없음 · 배경은 그대로). 문장에 움직임 말이 없으면 쪽 종류대로 움직인다.
 * 아이 그림은 바꾸지 않는다 — 보는 자리만 바뀐다(차별점 1)
 */
class DiaryPageFocusTest {
    private fun piece(id: Int, name: String?, x: Float, y: Float, w: Float = 0.1f, h: Float = 0.2f, role: PieceRole = PieceRole.OBJECT) =
        DiaryPiece(id, listOf(Stroke(Color.Blue, listOf(Offset(x, y), Offset(x + w, y + h)))), name, role = role)

    private val sea = piece(0, "바다", 0f, 0f, 1f, 0.5f, PieceRole.BACKGROUND)
    private val castle = piece(1, "모래성", 0.2f, 0.5f)
    private val bucket = piece(2, "양동이", 0.7f, 0.6f, 0.08f, 0.1f)
    private val dad = piece(3, "아빠", 0.4f, 0.4f)
    private val all = listOf(sea, castle, bucket, dad)

    private fun page(kind: DiaryPageKind, cast: List<String> = emptyList(), item: String? = null) =
        DiaryPage(kind, "글", "child", cast, PieceMove.BOB, item = item)

    @Test
    fun aPieceStoryPageLooksAtThatPiece() {
        assertEquals(listOf(bucket), pageFocus(page(DiaryPageKind.PROBLEM, item = "양동이: 물 떠 왔어"), all))
    }

    @Test
    fun anotherPageLooksAtThePiecesItsSentenceNamesButNotTheBackground() {
        assertEquals(listOf(castle, dad), pageFocus(page(DiaryPageKind.PROBLEM, cast = listOf("아빠", "모래성", "바다")), all))
        assertTrue("말한 조각이 없으면 전체", pageFocus(page(DiaryPageKind.SOLUTION), all).isEmpty())
        assertTrue("말이 「조각: 말」 꼴이 아니면 문장으로", pageFocus(page(DiaryPageKind.PROBLEM, cast = listOf("아빠"), item = "아빠가 밀어 줬어"), all) == listOf(dad))
    }

    @Test
    fun placeDrawingAndPuzzlePagesShowTheWholePicture() {
        listOf(DiaryPageKind.PLACE, DiaryPageKind.DRAWING, DiaryPageKind.PUZZLE).forEach {
            assertTrue("$it", pageFocus(page(it, cast = listOf("모래성")), all).isEmpty())
        }
    }

    @Test
    fun aSentenceWithNoMoveMovesByItsPageKind() {
        val book = buildDiaryBook(DiaryBookInput(
            lines = mapOf("place" to "바닷가", "problem" to "모래성", "reaction" to "그랬어", "keep" to "바다"),
            by = emptyMap(), hasDrawing = false, missions = false, puzzle = false,
            written = listOf("나는 오늘 바닷가에 있었어요.", "모래성이 있었어요.", "그랬어요.", "바다예요."),
            plan = listOf(DiaryPlanPage("DEPART", DiaryPageKind.PLACE), DiaryPlanPage("SHAKE", DiaryPageKind.PROBLEM),
                DiaryPlanPage("FAIL", DiaryPageKind.REACTION), DiaryPlanPage("TOGETHER", DiaryPageKind.KEEP)),
        ))
        assertEquals(listOf(PieceMove.BOB, PieceMove.WALK, PieceMove.HOP, PieceMove.BOB), book.take(4).map { it.move })
        assertEquals("움직임 말이 있으면 그 말대로", PieceMove.TOPPLE,
            buildDiaryBook(DiaryBookInput(lines = mapOf("problem" to "무너졌어"), by = emptyMap(), hasDrawing = false, missions = false, puzzle = false)).first().move)
    }
}
