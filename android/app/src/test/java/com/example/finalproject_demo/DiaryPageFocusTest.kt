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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #220 ④ — 쪽마다 그림이 달라 보이게. 전에는 쪽마다 아이 그림 전체에 그 쪽 글에 나온 조각만 진하게라 다섯 쪽이 거의 같았다.
 * 조각 이야기 쪽은 그 조각으로, 다른 쪽은 문장에 나온 조각으로 다가가고(장소 · 그림 · 맞추기 쪽은 전체), 문장에 움직임 말이 없으면 쪽 종류대로 움직인다.
 * 아이 그림은 바꾸지 않는다 — 보는 자리만 바뀐다(차별점 1)
 */
class DiaryPageFocusTest {
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
