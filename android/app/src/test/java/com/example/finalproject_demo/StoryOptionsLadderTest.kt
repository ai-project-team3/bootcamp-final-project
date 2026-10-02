package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryOptionsLadderTest {
    private fun verdict(next: String?, fills: List<Pair<String, String>> = emptyList()) = Server.Verdict(
        "ok", fills, next, null, false, false, null, false, true, true, "기쁨",
    )

    @Test fun twoSilentAnswersShowServerCardsAndKeepTheCardSource() = exercise(select = true)
    @Test fun noCardSelectionUsesTheFirstServerOptionWithoutChildSignals() = exercise(select = false)

    @Test fun cardSpeechWaitsForTranscriptionAfterTheMicrophoneStops() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        val originalListen = Voice.listen
        val originalTranscribe = Voice.transcribe
        val recording = CompletableDeferred<Unit>()
        val stopRecording = CompletableDeferred<Unit>()
        val transcribing = CompletableDeferred<Unit>()
        val finishTranscription = CompletableDeferred<Unit>()
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        d.s.speed = 0.01
        d.s.timerOn = true
        Voice.listen = { recording.complete(Unit); stopRecording.await(); byteArrayOf(1) }
        Voice.transcribe = { transcribing.complete(Unit); finishTranscription.await(); "바닷가" }
        try {
            d.s.exchangeStoryTurn("problem", "무슨 일이야?", "길을 떠났어") {
                Server.TurnResult(verdict("place"), Server.Line("그랬구나!", null, "어디로 갈까?", listOf("숲속", "바닷가", "구름 위")))
            }
            val reply = async {
                d.askStory(Question("어디로 갈까?", Kind.EASY), "place") {
                    Server.TurnResult(verdict("problem", listOf("place" to it.utterance)), null)
                }
            }
            withTimeout(5_000) { while (d.s.stage !is Stage.CardsRow) delay(5) }
            d.toggleMic()
            recording.await()
            delay(150)
            stopRecording.complete(Unit)
            transcribing.await()
            assertFalse(d.s.micOn)
            delay(2_000)
            assertEquals("transcription is not silence or a request to reshuffle", 1,
                d.s.log.count { "서버 답 후보 카드" in it })
            assertFalse(reply.isCompleted)
            finishTranscription.complete(Unit)
            assertEquals("바닷가", (withTimeout(2_000) { reply.await() } as Reply.Spoke).text)
            assertEquals("child", d.s.slotBy["place"])
        } finally {
            scope.cancel()
            Voice.listen = originalListen
            Voice.transcribe = originalTranscribe
            Server.liveModes = emptySet()
            Server.base = null
        }
    }

    private fun exercise(select: Boolean) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.timerOn = true
        d.s.turn = 3
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        var sent: Server.Turn? = null
        var cardsSeen = false
        val candidates = listOf("숲속", "바닷가", "구름 위")
        try {
            d.s.exchangeStoryTurn("problem", "무슨 일이야?", "길을 떠났어") {
                Server.TurnResult(verdict("place"), Server.Line("그랬구나!", null, "어디로 갈까?", candidates))
            }
            val job = async {
                d.askStory(Question("어디로 갈까?", Kind.EASY, easierText = "가고 싶은 곳이 있어?"), "place") {
                    sent = it
                    Server.TurnResult(verdict("problem", listOf("place" to it.utterance)), null)
                }
            }
            val feeder = launch {
                while (job.isActive) {
                    val cards = d.s.stage as? Stage.CardsRow
                    if (cards != null) {
                        cardsSeen = true
                        assertEquals(candidates.toSet(), cards.cards.map { it.label }.toSet())
                        if (select) d.send(Reply.Tapped("바닷가", "바닷가")) else d.send(Reply.Silent)
                    }
                    delay(5)
                }
            }
            val reply = withTimeout(5_000) { job.await() }
            feeder.cancelAndJoin()
            assertTrue("server candidates must reach the card stage", cardsSeen)
            assertTrue(reply is Reply.Tapped)
            assertEquals(!select, (reply as Reply.Tapped).byMascot)
            assertEquals(if (select) "바닷가" else "숲속", reply.label)
            assertEquals(reply.label, sent?.utterance)
            assertEquals(if (select) "card" else "mascot", d.s.slotBy["place"])
            if (!select) {
                assertEquals(0, d.s.reactions)
                assertEquals(0, d.s.modeVoice)
                assertEquals(0, d.s.modeCard)
                assertTrue(d.s.quotes.isEmpty())
                assertTrue(d.s.signals.isEmpty())
                assertTrue(d.s.log.any { "숲속" in it && "마스코트" in it })
            }
        } finally {
            scope.cancel()
            Server.liveModes = emptySet()
            Server.base = null
        }
    }
}
