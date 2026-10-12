package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLanguageSignalsTest {
    private var previousBase: String? = null
    private var previousModes: Set<StoryMode> = emptySet()
    @Before fun prepare() {
        previousBase = Server.base; previousModes = Server.liveModes
        Server.base = null; Server.liveModes = emptySet()
    }
    @After fun clean() { Server.base = previousBase; Server.liveModes = previousModes }
    @Test fun liveCauseAndExplicitLinkingCanReachTheExistingReasonQuestionRule() = runBlocking {
        val oldBase = Server.base
        val oldModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01; d.s.level = Level.CHAIN; d.s.templateKey = "E"
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        try {
            repeat(2) {
                val job = async {
                    d.askStory(Question("왜 집에 갔어?", Kind.HARD), "cause") {
                        Server.TurnResult(Server.Verdict("ok", emptyList(), null, null, false,
                            false, null, false, true, false, null), null)
                    }
                }
                val feeder = launch {
                    while (job.isActive) { d.send(Reply.Spoke("추워서 울었어. 그래서 집에 갔어.")); delay(40) }
                }
                val reply = withTimeout(8_000) { job.await() } as Reply.Spoke
                feeder.cancelAndJoin()
                assertTrue("A1 must come from the spoken connective", reply.answer!!.con)
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, reply)
            }
            assertEquals(Level.REASON, d.s.level)
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); Server.base = oldBase; Server.liveModes = oldModes }
    }

    @Test fun spokenChoicesDoNotPromoteQuestionStyleDespiteStrongServerSignals() {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        d.s.level = Level.CHAIN; d.s.templateKey = "E"
        try {
            repeat(2) {
                d.setListening(Question("공룡이야, 토끼야?", Kind.CHOICE))
                d.say("공룡이야, 토끼야?")
                d.talk("child", "공룡이라서 그래서 좋아")
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                    Reply.Spoke("공룡이라서 그래서 좋아", answer = Answer("공룡이라서 그래서 좋아", reason = true, con = true)))
            }
            assertEquals(Level.CHAIN, d.s.level)
            assertTrue(d.s.notes.all { !it.s1 && !it.a1 && it.el.isEmpty() })
            assertEquals("Story turn tracking still advances", 2, d.s.turn)
        } finally { scope.cancel() }
    }

    @Test fun repeatedCardOnlyAnswersKeepEachStartingLevelAndResetLanguageStreaks() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            for (initial in Level.entries) {
                val d = Director(scope)
                d.s.level = initial
                d.s.s1streak = 1; d.s.noAnswerStreak = 1
                repeat(4) { index ->
                    d.setListening(Question("어디로 갈까?", Kind.CHOICE))
                    d.say("어디로 갈까?")
                    d.talk("card", "숲")
                    d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, Reply.Tapped("숲", "숲"))
                    assertEquals("Card turn ${index + 1} from $initial", initial, d.s.level)
                    assertEquals(0, d.s.s1streak)
                    assertEquals(0, d.s.noAnswerStreak)
                    assertEquals(index + 1, d.s.turn)
                }
                assertNotNull("The third counted turn still fixes a template", d.s.templateKey)
                assertTrue(d.s.notes.all { !it.languageEligible && !it.s1 && !it.a1 && it.el.isEmpty() })
                assertTrue(d.s.talk.filter { it.who == "card" }.all { it.languageEvidence == null })
            }
        } finally { scope.cancel() }
    }

    @Test fun aKnownOtherSpeakerCannotSupplyLanguageSignalsThroughASpokenReply() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            for (who in listOf("adult", "draw", "mascot", "card")) {
                val d = Director(scope)
                d.s.level = Level.CHAIN; d.s.templateKey = "E"
                repeat(2) {
                    d.setListening(Question("왜 집에 갔어?", Kind.HARD))
                    d.say("왜 집에 갔어?")
                    d.talk(who, "추웠어. 그래서 집에 갔어")
                    d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                        Reply.Spoke("추웠어. 그래서 집에 갔어", answer = Answer("추웠어. 그래서 집에 갔어", reason = true, con = true)))
                }
                assertEquals(who, Level.CHAIN, d.s.level)
            }
        } finally { scope.cancel() }
    }

    @Test fun connectivesNeedTheirOwnWordAndDoNotInventCauseSignals() {
        val cases = listOf("추웠어. 그래서 집에 갔어" to true, "그리고 밥을 먹었어" to true,
            "그런데 비가 왔어" to true, "숲에서 놀았어" to false, "가서 먹었어" to false,
            "그리고싶어" to false, "그래서집에갔어" to false)
        cases.forEach { (text, expected) -> assertEquals(text, expected, storyHasLinkingExpression(text)) }
    }

    @Test fun undoAndRedoRestoreTheConsecutiveQuestionSignalState() {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        d.s.level = Level.CHAIN; d.s.templateKey = "E"
        fun reply() {
            d.setListening(Question("왜 집에 갔어?", Kind.HARD))
            d.say("왜 집에 갔어?")
            d.talk("child", "추웠어. 그래서 집에 갔어")
            d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion },
                Reply.Spoke("추웠어. 그래서 집에 갔어", answer = Answer("추웠어. 그래서 집에 갔어", reason = true, con = true)))
        }
        try {
            reply()
            val history = TurnHistory(d.s)
            history.before(); reply(); history.done()
            assertEquals(Level.REASON, d.s.level)
            assertTrue(history.undo())
            assertEquals(Level.CHAIN, d.s.level)
            assertEquals("Undo must retain the first of two consecutive signals", 1, d.s.s1streak)
            assertTrue(history.redo())
            assertEquals(Level.REASON, d.s.level)
            assertEquals(0, d.s.s1streak)
        } finally { scope.cancel() }
    }
}
