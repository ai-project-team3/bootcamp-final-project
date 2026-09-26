package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.StoryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일기 화이트보드 그림(「오늘 있었던 일」)이 「오늘 만난 사람」 그림과 **섞이지 않는가** (09-26).
 *
 * 둘이 한 칸(`drawing`)을 쓰면 책의 「오늘 만난 사람」 쪽과 부모 리포트의 「친구를 직접 그렸어요」가
 * 화이트보드 그림을 친구 그림이라고 말한다. 그래서 화이트보드 걸음이 끝나면 `keepSceneDrawing()` 으로 옮긴다.
 */
class SceneDrawingTest {
    private fun stroke() = Stroke(Color.Black, listOf(Offset(0f, 0f), Offset(1f, 1f)))

    @Test
    fun keepingTheWhiteboardEmptiesTheFriendDrawing() {
        val s = DemoState()
        s.drawing += stroke()
        s.drawing += stroke()
        s.drawingAspect = 1.6f

        s.keepSceneDrawing()

        assertEquals("화이트보드 획이 옮겨 가야 한다", 2, s.sceneDrawing.size)
        assertEquals(1.6f, s.sceneDrawingAspect)
        assertTrue("「오늘 만난 사람」 칸은 비어야 한다 — 안 비면 친구 그림으로 읽힌다", s.drawing.isEmpty())
    }

    @Test
    fun aWhiteboardDrawingAloneStillEarnsTheCrayon() {
        val s = DemoState()
        s.mode = StoryMode.DIARY
        s.drawing += stroke()
        s.keepSceneDrawing()                 // 친구는 안 그렸고 화이트보드만 그렸다
        assertTrue("그린 날이므로 크레파스를 받는다", s.earnedCrayon)
        assertEquals(2, s.giftCount)
    }

    @Test
    fun aNewStoryStartsWithAnEmptyWhiteboard() {
        val s = DemoState()
        s.drawing += stroke()
        s.keepSceneDrawing()
        s.resetStory()
        assertTrue(s.sceneDrawing.isEmpty())
        assertEquals(1f, s.sceneDrawingAspect)
    }
}
