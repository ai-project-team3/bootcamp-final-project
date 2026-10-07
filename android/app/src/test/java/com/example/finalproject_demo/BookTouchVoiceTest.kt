package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #250 (10-07 S7 · 민우) — 책을 듣다가 주인공을 만지면 낭독이 끊겼다. 만지기(`tool:`)는 화면을 넘기는 선택이 아니라
 * 반응이라 목소리를 끊지 않는다(하던 말은 처음부터 다시 · 종훈 10-07). 「다음」 같은 선택은 예전처럼 끊고 바로 넘어간다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookTouchVoiceTest {
    private fun director() = Director(CoroutineScope(SupervisorJob())).apply { s.speed = 1.0 }

    @Test fun touchingACharacterKeepsTheVoiceGoing() = runBlocking {
        val d = director()
        val line = d.queueVoice(Job())                       // a line still being read
        d.send(Reply.Tapped("tool:hand:hero", "hand"))
        assertTrue("만지기가 낭독을 끊었다", line.isActive)
        line.cancel()
    }

    @Test fun aChoiceStillCutsTheVoice() = runBlocking {
        val d = director()
        val line = d.queueVoice(Job())
        d.send(Reply.Tapped("next", "다음"))
        assertFalse("「다음」이 목소리를 끊지 않았다", line.isActive)
    }
}
