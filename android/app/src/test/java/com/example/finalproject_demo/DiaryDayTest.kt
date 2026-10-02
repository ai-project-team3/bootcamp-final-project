package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 그림일기 한 판의 상태 — 조각 · 날씨 · 호출 수 (docs/일기모드_흐름.html) */
class DiaryDayTest {

    @Test
    fun oneDayPerStateAndANewDayForgetsTheLastOne() {
        val s = DemoState()
        val first = s.diaryDay
        assertSame("the same state gives the same day", first, s.diaryDay)
        first.pieces += DiaryPiece(0, emptyList(), "집")
        first.turnCalls = 5
        val next = s.newDiaryDay()
        assertNotSame(first, next)
        assertTrue(s.diaryDay.pieces.isEmpty())
        assertEquals(0, s.diaryDay.turnCalls)
        assertNotSame("two children never share a day", s.diaryDay, DemoState().diaryDay)
    }

    @Test
    fun weatherComesOnlyFromWhatTheChildDrewAndNamed() {
        val d = DemoState().newDiaryDay()
        d.pieces += DiaryPiece(0, emptyList(), "우리 집")
        d.weatherFromDrawing()
        assertNull("nothing about the sky was drawn — the weather is not invented", d.weather)

        d.pieces += DiaryPiece(1, emptyList(), "해")
        d.weatherFromDrawing()
        assertEquals(DiaryWeather.SUN, d.weather)
        assertEquals("drawing", d.weatherBy)
    }

    /** 날씨는 **낱말로** 읽는다 — 「나비」 「비행기」는 비가 아니고 「해바라기」 「해적」은 맑음이 아니다 */
    @Test
    fun weatherIsReadFromWholeWords() {
        mapOf(
            "해" to DiaryWeather.SUN, "해님" to DiaryWeather.SUN, "햇님이랑 구름" to DiaryWeather.SUN, "태양" to DiaryWeather.SUN,
            "구름" to DiaryWeather.CLOUD, "먹구름" to DiaryWeather.CLOUD,
            "비" to DiaryWeather.RAIN, "비 오는 날" to DiaryWeather.RAIN, "우산" to DiaryWeather.RAIN, "빗방울" to DiaryWeather.RAIN,
            "눈사람" to DiaryWeather.SNOW, "눈송이" to DiaryWeather.SNOW,
        ).forEach { (name, w) -> assertEquals("「$name」", w, DiaryWeather.fromPieces(listOf(name))) }
        listOf("나비", "비행기", "비둘기", "해바라기", "해적", "바다", "눈", "놀이터").forEach {
            assertNull("「$it」은 날씨가 아니다", DiaryWeather.fromPieces(listOf(it)))
        }
    }

    @Test
    fun aTappedWeatherIsNotOverwrittenByTheDrawing() {
        val d = DemoState().newDiaryDay()
        d.pieces += DiaryPiece(0, emptyList(), "해")
        d.pickWeather(DiaryWeather.RAIN)
        d.weatherFromDrawing()
        assertEquals(DiaryWeather.RAIN, d.weather)
        assertEquals("card", d.weatherBy)
    }

    @Test
    fun pieceNamesAreTheChildsWordsInDrawingOrderWithoutBlanksOrRepeats() {
        val d = DemoState().newDiaryDay()
        d.pieces += DiaryPiece(0, emptyList(), "집")
        d.pieces += DiaryPiece(1, emptyList(), null)
        d.pieces += DiaryPiece(2, emptyList(), " 엄마 ")
        d.pieces += DiaryPiece(3, emptyList(), "집")
        assertEquals(listOf("집", "엄마"), d.pieceNames)
    }

    @Test
    fun theTurnCounterCountsButDoesNotBlockUntilABudgetIsSet() {
        val d = DemoState().newDiaryDay()
        d.turnCalls = 1_000
        assertTrue("no budget yet (#30) — never blocks", d.canCallTurn())
        d.turnBudget = 40
        d.turnCalls = 39
        assertTrue(d.canCallTurn())
        d.turnCalls = 40
        assertFalse(d.canCallTurn())
    }
}
