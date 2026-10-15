package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.DiaryWrapUp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-14 실기기 — 30분쯤 마무리 제안(「이제 그림일기로 만들어 볼까?」)의 답이 시연 서랍에만 있어, 30분을 넘긴 판(하다 만 일기를
 * 이어 연 판 포함)이 여기서 멈췄다. 이제 화면에 [응, 만들자!] · [더 할래]를 띄우고 마이크는 닫는다(말을 뜻으로 읽지 않는다)
 */
class DiaryWrapUpTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    @Test
    fun afterThirtyMinutesTheOfferIsAnsweredOnScreenAndTheTalkGoesOn() = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        try {
            d.go(Scene.DIARY)
            assertTrue(await { d.s.stage is DiaryStart } != null)
            assertTrue(await { if (d.s.stage is DiaryStart) d.send(Reply.Tapped("draw", "그릴래")); d.s.stage is DiaryBoard } != null)
            d.s.drawing += Stroke(Color.Blue, listOf(Offset(.2f, .3f), Offset(.3f, .4f)))
            d.s.diaryDay.catchUp(d.s.drawing)
            d.s.diaryTimeUp = true                                    // 30분이 지났다
            assertTrue("마무리 제안 화면이 안 떴다 — 말=${d.s.line}",
                await { if (d.s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); d.s.stage is DiaryWrapUp } != null)
            assertFalse("마무리 제안에 마이크가 열렸다 — 답은 화면 버튼으로", d.s.micEnabled)
            d.send(Reply.Tapped("more", "더 할래"))                    // 화면의 [더 할래]
            assertTrue("더 하겠다는데 질문 화면으로 안 돌아갔다", await { d.s.stage is DiaryAsk && d.s.line == "좋아, 더 이야기해 줘!" } != null)
        } finally { sup.cancel() }
    }
}
