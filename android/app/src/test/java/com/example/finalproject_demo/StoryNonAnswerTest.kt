package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryNonAnswerTest {
    private val question = "어떻게 끝났어?"
    private val easier = "마지막에는 어떻게 됐을까?"
    private val candidates = listOf("집에 돌아왔어", "친구를 도왔어", "함께 쉬었어")

    private fun verdict(next: String?, ready: Boolean = false, fills: List<Pair<String, String>> = emptyList()) =
        Server.Verdict("ok", fills, next, null, storyReady = ready, unclear = false,
            unclearOf = null, contradiction = false, s1Reason = false, s2Addition = false, emotion = "")

    private fun exercise(block: suspend (Director) -> Unit) = runBlocking {
        val base = Server.base
        val modes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        d.s.speed = 0.01
        d.s.timerOn = false
        try { block(d) } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = base
            Server.liveModes = modes
        }
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(3_000) {
        while (!condition()) delay(1)
    }

    private suspend fun seed(d: Director) {
        d.s.exchangeStoryTurn("reaction", "그다음에는?", "집으로 향했어") {
            Server.TurnResult(verdict("solution"), Server.Line("그랬구나!", null, question, candidates))
        }
    }

    @Test fun classificationOnlyAcceptsStandaloneNonAnswers() {
        listOf("몰라", "몰라 이제!", "더 없어...", "모르겠어요", "생각이 안 나").forEach {
            assertTrue(it, storyNonAnswer(it))
        }
        listOf("더 없어, 집에 갔어", "몰라서 엄마에게 물어봤어", "친구가 없어서 슬펐어",
            "인형", "없어", "싫어", "응", "몰라라는 친구를 만났어").forEach {
            assertFalse(it, storyNonAnswer(it))
        }
    }

    @Test fun undoDuringTheEasierQuestionDoesNotBecomeAnAnswerOrShowCards() = exercise { d ->
        seed(d)
        var submissions = 0
        val answer = CoroutineScope(currentCoroutineContext()).async {
            d.askStory(Question(question, Kind.EASY, easierText = easier), "solution") {
                submissions++
                Server.TurnResult(verdict(null), null)
            }
        }
        until { d.s.micEnabled }
        d.send(Reply.Spoke("몰라"))
        until { d.s.line == easier && d.s.micEnabled }
        val undo = Reply.Tapped(TurnHistory.UNDO, "되돌리기")
        d.send(undo)
        assertEquals(undo, withTimeout(3_000) { answer.await() })
        assertEquals(0, submissions)
        assertFalse(d.s.stage is Stage.CardsRow)
        assertNull(d.s.slots["solution"])
    }

    @Test fun shortNonAnswersReachTheSameQuestionsCardsAndKeepCardProvenance() = exercise { d ->
        seed(d)
        var submitted: Server.Turn? = null
        val answer = CoroutineScope(currentCoroutineContext()).async {
            d.askStory(Question(question, Kind.EASY, easierText = easier), "solution") {
                submitted = it
                Server.TurnResult(verdict(null, ready = true, fills = listOf("solution" to it.utterance)), null)
            }
        }
        until { d.s.micEnabled }
        d.send(Reply.Spoke("몰라 이제"))
        until { d.s.line == easier && d.s.micEnabled }
        assertNull(submitted)
        d.send(Reply.Spoke("더 없어"))
        until { d.s.stage is Stage.CardsRow && d.s.micEnabled }
        assertEquals(candidates.toSet(), (d.s.stage as Stage.CardsRow).cards.map { it.label }.toSet())
        d.send(Reply.Spoke("모르겠어"))
        until { d.s.log.count { "서버 답 후보 카드" in it } == 2 }
        d.send(Reply.Tapped(candidates[0], candidates[0]))
        assertTrue(withTimeout(3_000) { answer.await() } is Reply.Tapped)
        assertEquals(candidates[0], submitted!!.utterance)
        assertEquals("card", d.s.slotBy["solution"])
        assertTrue(d.s.storyReady)
        assertTrue(d.s.quotes.isEmpty())
        assertTrue(d.s.signals.isEmpty())
    }

    @Test fun repeatedNonAnswersLetTheMascotSupplyAnEndingWithoutInventingChildWords() = exercise { d ->
        seed(d)
        val answer = CoroutineScope(currentCoroutineContext()).async {
            d.askStory(Question(question, Kind.EASY, easierText = easier), "solution") {
                Server.TurnResult(verdict(null, ready = true, fills = listOf("solution" to it.utterance)), null)
            }
        }
        until { d.s.micEnabled }
        d.send(Reply.Spoke("몰라"))
        until { d.s.line == easier && d.s.micEnabled }
        d.send(Reply.Spoke("더 없어"))
        repeat(4) { round ->
            until { d.s.log.count { "서버 답 후보 카드" in it } == round + 1 && d.s.micEnabled }
            d.send(Reply.Spoke("몰라"))
        }
        val result = withTimeout(3_000) { answer.await() } as Reply.Tapped
        assertTrue(result.byMascot)
        assertEquals("mascot", d.s.slotBy["solution"])
        assertEquals(candidates[0], d.s.slots["solution"])
        assertTrue(d.s.storyReady)
        assertTrue(d.s.quotes.isEmpty())
        assertTrue(d.s.signals.isEmpty())
        assertEquals(0, d.s.modeCard)
    }

    @Test fun aRealSentenceContainingNonAnswerWordsStillReachesTheServer() = exercise { d ->
        seed(d)
        var sent: String? = null
        val answer = CoroutineScope(currentCoroutineContext()).async {
            d.askStory(Question(question, Kind.EASY), "solution") {
                sent = it.utterance
                Server.TurnResult(verdict(null), null)
            }
        }
        until { d.s.micEnabled }
        val text = "몰라서 엄마에게 물어봤어"
        d.send(Reply.Spoke(text))
        assertEquals(text, (withTimeout(3_000) { answer.await() } as Reply.Spoke).text)
        assertEquals(text, sent)
    }

    @Test fun stoppingAnAlreadySettledStoryIsStillJudgedByTheServer() = exercise { d ->
        d.s.slots["solution"] = "집에 돌아왔어"
        var sent: Server.Turn? = null
        val answer = CoroutineScope(currentCoroutineContext()).async {
            d.askStory(Question("더 하고 싶은 이야기가 있어?", Kind.EASY), null) {
                sent = it
                Server.TurnResult(verdict(null, ready = true), null)
            }
        }
        until { d.s.micEnabled }
        d.send(Reply.Spoke("더 없어"))
        assertTrue(withTimeout(3_000) { answer.await() } is Reply.Spoke)
        assertEquals("더 없어", sent!!.utterance)
        assertNull(sent!!.askedSlot)
        assertTrue(d.s.storyReady)
    }
}
