package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.diaryTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryWhiteboardFlowTest {
    private suspend fun waitFor(d: Director, test: () -> Boolean) = withTimeoutOrNull(5_000) {
        while (!test()) delay(5)
        true
    }

    private suspend fun tap(d: Director, label: String) {
        assertTrue("버튼을 찾지 못함: $label", waitFor(d) { d.s.buttons.any { label in it.label } } == true)
        d.s.buttons.first { label in it.label }.onClick()
    }

    @Test
    fun recognitionOnlySuggestsAQuestionAndTheChildsCorrectionReachesTheBook() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope)
            val s = d.s
            s.speed = 0.01
            s.mode = StoryMode.DIARY
            d.go(Scene.DIARY)
            assertTrue(waitFor(d) { s.stage is Stage.DrawPad && s.buttons.any { "그림 없이 이야기할래" in it.label } } == true)
            s.drawing += Stroke(Color.Blue, listOf(Offset(0.1f, 0.2f), Offset(0.8f, 0.7f)))
            tap(d, "공룡으로 알아봄")
            assertTrue(waitFor(d) { "공룡처럼 보이네" in s.line } == true)
            assertTrue("인식 결과가 슬롯에 들어갔다", s.slotBy.isEmpty())

            delay(30)
            tap(d, "블록을 쌓은 걸 그렸어")
            assertTrue(waitFor(d) { s.line == "블록을 쌓은 걸 그렸어." } == true)
            delay(30)
            d.send(Reply.Tapped("done", "완료"))
            assertTrue(waitFor(d) { s.sceneDrawing.isNotEmpty() && s.drawing.isEmpty() } == true)
            assertTrue(waitFor(d) { s.slotBy["whiteboard"] == "child" } == true)
            assertEquals("블록을 쌓은 걸 그렸어.", s.slots["whiteboard"])
            assertTrue("인식 낱말이 아이 답을 덮었다", s.slots.values.none { "공룡" in it })
            assertTrue(waitFor(d) { "블록 그림을 그렸구나" in s.line } == true)
            assertTrue("첫 인터뷰 질문이 아이의 정정을 따르지 않았다", "오늘은 어디에 있었어?" in s.line)
            assertTrue("책 첫 쪽에 아이 말이 빠졌다", "블록을 쌓은 걸 그렸어" in diaryTemplate(s).pages.first().text(s))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unknownDrawingGetsAnOpenQuestionAndNoInventedSlot() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope)
            val s = d.s
            s.speed = 0.01
            s.mode = StoryMode.DIARY
            d.go(Scene.DIARY)
            assertTrue(waitFor(d) { s.stage is Stage.DrawPad && s.buttons.any { "알아보지 못함" in it.label } } == true)
            s.drawing += Stroke(Color.Red, listOf(Offset(0.2f, 0.2f), Offset(0.7f, 0.7f)))
            tap(d, "알아보지 못함")
            assertTrue(waitFor(d) { s.line == "무엇을 그렸어?" } == true)
            delay(30)
            d.send(Reply.Silent)
            assertTrue(waitFor(d) { s.buttons.any { "그림 없이 이야기할래" in it.label } } == true)
            delay(30)
            d.send(Reply.Tapped("done", "완료"))
            assertTrue(waitFor(d) { s.sceneDrawing.isNotEmpty() } == true)
            assertTrue("아이가 말하지 않았는데 그림 낱말을 채웠다", s.slotBy["whiteboard"] == null)
        } finally {
            scope.cancel()
        }
    }
}
