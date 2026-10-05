package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.CLUE_MAX
import com.example.finalproject_demo.demo.ClueKind
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.clueKindOf
import com.example.finalproject_demo.demo.clueQuestion
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.demo.unsaidClues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 그림 실마리 (DiaryClue.kt · 10-05) — 그린 것 중 아이가 아직 말하지 않은 것을, 단정하지 않는 틀로 묻는다 */
class DiaryClueTest {

    private fun piece(id: Int, name: String?, role: PieceRole = PieceRole.OBJECT) =
        DiaryPiece(id, listOf(Stroke(Color.Red, listOf(Offset(.1f * id, .2f), Offset(.1f * id + .05f, .3f)))), name, role = role)

    private fun state(vararg names: String?): DemoState = DemoState().apply {
        newDiaryDay()
        names.forEachIndexed { i, n -> diaryDay.pieces += piece(i + 1, n) }
    }

    @Test
    fun piecesAreSortedIntoPlacesPeopleAndThings() {
        mapOf(
            "놀이터" to ClueKind.PLACE, "할머니 집" to ClueKind.PLACE, "우리 집" to ClueKind.PLACE, "동네놀이터" to ClueKind.PLACE,
            "엄마" to ClueKind.PERSON, "우리 엄마" to ClueKind.PERSON, "친구" to ClueKind.PERSON,
            "미끄럼틀" to ClueKind.THING, "그네" to ClueKind.THING, "가방" to ClueKind.THING, "우산" to ClueKind.THING, "해" to ClueKind.THING,
        ).forEach { (name, kind) -> assertEquals("「$name」", kind, clueKindOf(piece(1, name))) }
        assertEquals("배경 조각은 곳", ClueKind.PLACE, clueKindOf(piece(1, "초록 땅", PieceRole.BACKGROUND)))
        assertNull("아이 자신은 실마리가 아니다", clueKindOf(piece(1, "나")))
        assertNull("이름 없는 조각은 실마리가 아니다", clueKindOf(piece(1, null)))
    }

    @Test
    fun onlyWhatTheChildHasNotSaidYetIsAClue() {
        val s = state("놀이터", "미끄럼틀", "엄마")
        s.slots["place"] = "놀이터 갔어"
        s.quotes += "미끄럼틀 탔어"
        assertEquals(listOf("엄마"), s.unsaidClues(s.diaryDay).map { it.name })
    }

    @Test
    fun theFramesSayItWasDrawnAndAskOpenly() {
        val s = state("놀이터", "할머니 집", "미끄럼틀", "엄마")
        val day = s.diaryDay
        assertEquals("할머니 집, 놀이터. 오늘 어디 있었어?", s.clueQuestion(day, "place")?.second)
        day.cluesUsed.clear()
        assertEquals("미끄럼틀도 그렸네! 미끄럼틀 이야기 해 줄래?", s.clueQuestion(day, "problem")?.second)
        day.cluesUsed.clear()
        assertEquals("엄마도 그렸네! 엄마는 오늘 뭐 했어?", s.clueQuestion(day, "companion")?.second)
        assertNull("결말 · 내일은 실마리로 묻지 않는다", s.clueQuestion(day, "solution"))
    }

    @Test
    fun aClueIsUsedOnceAndAtMostTwiceADay() {
        val s = state("미끄럼틀", "그네", "자전거")
        val day = s.diaryDay
        val first = s.clueQuestion(day, "problem")?.second
        val second = s.clueQuestion(day, "problem")?.second
        assertEquals("자전거도 그렸네! 자전거 이야기 해 줄래?", first)              // 나중에 그린 것부터
        assertEquals("그네도 그렸네! 그네 이야기 해 줄래?", second)                  // 짚은 것은 다시 짚지 않는다
        assertEquals(CLUE_MAX, day.cluesUsed.size)
        assertNull("한 판에 ${CLUE_MAX}번까지", s.clueQuestion(day, "problem"))
    }
}
