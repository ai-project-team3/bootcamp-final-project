package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Kind
import com.example.finalproject_demo.demo.Question
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🎤 두 번 누름 · 받아쓰기 기다리는 중 누름 (#178 · #203 · 10-06 실기기).
 * 받아쓰기를 기다리는 동안 누르면 녹음이 겹쳐 셋이 한꺼번에 서버로 갔고, 늦게 온 답이 다음 질문의 답이 됐다.
 * 켜자마자 다시 누르면 0.3~0.5초 녹음이 받아쓰기로 가 「못 들었어」 되묻기가 세 번 연달아 나왔다.
 */
class MicGuardTest {

    private fun live(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val previousListen = Voice.listen
        val previousTranscribe = Voice.transcribe
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.timerOn = false
        d.s.mode = StoryMode.STORY
        Server.base = "http://127.0.0.1:1"                // the live mic path; nothing is actually called
        Server.liveModes = setOf(StoryMode.STORY)
        try { block(d) } finally {
            // a question left waiting on purpose must not keep runBlocking alive
            coroutineContext.cancelChildren()
            Server.base = previousBase
            Server.liveModes = previousModes
            Voice.listen = previousListen
            Voice.transcribe = previousTranscribe
            scope.coroutineContext[Job]?.cancel()
        }
    }

    // CI runners are slower than a laptop — 10 s, the conditions themselves are quick
    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(1) }

    private val speech = ByteArray(64_000)               // two seconds of 16 kHz audio

    @Test
    fun aPressWhileTranscribingStartsNoSecondRecording() = live { d ->
        var recordings = 0
        val heard = CompletableDeferred<String?>()
        Voice.listen = { recordings++; speech }
        Voice.transcribe = { heard.await() }
        val answer = async { d.ask(Question("거기서 누굴 만났어?", Kind.EASY, noCards = true)) }
        until { d.s.micEnabled }
        d.toggleMic()
        until { d.s.transcribing }
        delay(50)                                        // past the double-tap window
        d.toggleMic()
        d.toggleMic()
        delay(50)
        assertEquals("받아쓰기를 기다리는 동안 새 녹음이 시작됐다", 1, recordings)
        heard.complete("공룡")
        assertEquals("공룡", (withTimeout(10_000) { answer.await() } as Reply.Spoke).text)
        assertFalse(d.s.transcribing)
    }

    @Test
    fun aRecordingStoppedRightAwayIsNotTranscribedNorAskedAgain() = live { d ->
        var transcriptions = 0
        var stoppedEarly = true
        Voice.listen = { stop -> if (stoppedEarly) { until { stop() }; ByteArray(14_000) } else speech }
        Voice.transcribe = { transcriptions++; "공룡" }
        val answer = async { d.ask(Question("거기서 누굴 만났어?", Kind.EASY, noCards = true)) }
        until { d.s.micEnabled }
        d.toggleMic()
        until { d.s.micOn }
        delay(50)
        d.toggleMic()                                    // ⏹ right after starting — a mistaken press
        until { !d.s.micOn }
        delay(50)
        assertEquals("켜자마자 끊은 녹음을 받아쓰기로 보냈다", 0, transcriptions)
        assertFalse("잘못 누른 것에 되물었다 — 말=${d.s.line}", "한 번 더" in d.s.line)
        assertFalse(answer.isCompleted)

        stoppedEarly = false
        d.toggleMic()                                    // the mic still works for the real answer
        assertEquals("공룡", (withTimeout(10_000) { answer.await() } as Reply.Spoke).text)
        assertEquals(1, transcriptions)
    }

    @Test
    fun aTranscriptThatArrivesAfterTheQuestionMovedOnIsDropped() = live { d ->
        val heard = CompletableDeferred<String?>()
        Voice.listen = { speech }
        Voice.transcribe = { heard.await() }
        // each step says where it stopped — this case timed out on CI only (10-06), never on a laptop
        suspend fun step(what: String, condition: () -> Boolean) {
            if (withTimeoutOrNull(10_000) { while (!condition()) delay(1); true } == null)
                throw AssertionError("$what — mic=${d.s.micEnabled}/${d.s.micOn} transcribing=${d.s.transcribing} log=${d.s.log.take(8)}")
        }
        val first = async { d.ask(Question("거기서 누굴 만났어?", Kind.EASY, noCards = true)) }
        step("마이크가 켜질 차례가 안 왔다") { d.s.micEnabled }
        d.toggleMic()
        step("녹음 뒤 받아쓰기 중이 아니다") { d.s.transcribing }
        // the question moved on — a card was picked meanwhile. Sent until taken: a question drops input that came
        // before it started waiting (`awaitReply`), and on a slow runner the mic is enabled a little before that
        repeat(50) { if (!first.isCompleted) { d.send(Reply.Tapped("lion", "사자")); delay(200) } }
        step("카드를 골랐는데 질문이 끝나지 않았다") { first.isCompleted }
        heard.complete("친구들에서 봤더니")              // the late transcript of that question
        step("늦은 받아쓰기를 버리지 않았다") { d.s.log.any { "늦게 왔다" in it } }
        assertFalse(d.s.transcribing)
    }
}
