package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * #149 (10-06) — 받아쓰기가 자신 없어 한 말(웅얼거림을 「친구」로 지어 적은 것)은 칸에 바로 넣지 않는다.
 * 질문 하나에 **한 번만** 되묻고, 두 번째도 자신 없으면 그대로 받는다 — 같은 발음을 끝없이 되묻지 않는다.
 */
class UnsureTranscriptTest {
    @Test
    fun anUnsureTranscriptIsAskedAgainOnceThenTaken() = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val previousListen = Voice.listen
        val previousTranscribe = Voice.transcribe
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        var transcriptions = 0
        d.s.speed = 0.01
        d.s.timerOn = false
        d.s.mode = StoryMode.STORY
        Server.base = "http://127.0.0.1:1"                // the live mic path; nothing is actually called
        Server.liveModes = setOf(StoryMode.STORY)
        Voice.listen = { byteArrayOf(1) }
        Voice.transcribe = { transcriptions++; Server.lastSttUnsure = true; "친구" }
        suspend fun until(condition: () -> Boolean) = withTimeout(3_000) { while (!condition()) delay(1) }
        try {
            val answer = async { d.ask(Question("거기서 누굴 만났어?", Kind.EASY, noCards = true)) }
            until { d.s.micEnabled }
            d.toggleMic()
            until { transcriptions == 1 && d.s.line.contains("한 번 더") }
            assertFalse("the first unsure answer must not be taken", answer.isCompleted)

            d.toggleMic()
            val r = withTimeout(3_000) { answer.await() }
            assertEquals(2, transcriptions)
            assertEquals("친구", (r as Reply.Spoke).text)
        } finally {
            Server.base = previousBase
            Server.liveModes = previousModes
            Voice.listen = previousListen
            Voice.transcribe = previousTranscribe
            Server.lastSttUnsure = false
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
