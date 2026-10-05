package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.ui.reportMade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #65 2번 — 그림일기 아이가 화이트보드에 그렸는데 부모 리포트 「만들기」 축이 「오늘은 프리셋을 골랐어요」였다.
 * 그림일기는 그림을 `sceneDrawing` 으로 옮기고 `drawing` 을 비운다(`keepSceneDrawing`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportMadeTest {

    private fun stroke() = Stroke(androidx.compose.ui.graphics.Color.Black, listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.1f), androidx.compose.ui.geometry.Offset(0.5f, 0.5f)))

    @Test
    fun aPictureDiaryDrawingCountsAsMade() {
        val s = DemoState().apply { mode = StoryMode.DIARY }
        s.drawing += stroke()
        s.keepSceneDrawing()                  // 화이트보드 걸음이 끝날 때 앱이 하는 일 그대로
        assertTrue("그림이 drawing 에 남아 있다 — 검사 전제가 틀렸다", s.drawing.isEmpty())
        assertEquals(listOf("오늘 있었던 일을 직접 그렸어요"), reportMade(s))
    }

    @Test
    fun withNothingDrawnTheAxisStaysEmpty() {
        assertEquals(emptyList<String>(), reportMade(DemoState().apply { mode = StoryMode.DIARY }))
        assertEquals(emptyList<String>(), reportMade(DemoState().apply { mode = StoryMode.STORY }))
    }
}
