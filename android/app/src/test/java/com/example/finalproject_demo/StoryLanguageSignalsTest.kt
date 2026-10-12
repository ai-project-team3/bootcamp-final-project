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
class StoryLanguageSignalsTest {
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
        } finally { scope.cancel(); Server.base = oldBase; Server.liveModes = oldModes }
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

    @Test fun pickedCardsDoNotCountAsShortLanguageAnswers() {
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope)
        d.s.level = Level.CHAIN; d.s.templateKey = "E"
        try {
            repeat(2) {
                d.setListening(Question("어디로 갈까?", Kind.CHOICE))
                d.say("어디로 갈까?")
                d.talk("card", "숲")
                d.judge(BANK.first { it.slot == "cause" && it.counts && !it.emotion }, Reply.Tapped("숲", "숲"))
            }
            assertEquals(Level.CHAIN, d.s.level)
            assertEquals(2, d.s.turn)
        } finally { scope.cancel() }
    }

    @Test fun connectivesNeedTheirOwnWordAndDoNotInventCauseSignals() {
        val cases = listOf("추웠어. 그래서 집에 갔어" to true, "그리고 밥을 먹었어" to true,
            "그런데 비가 왔어" to true, "숲에서 놀았어" to false, "가서 먹었어" to false,
            "그리고싶어" to false, "그래서집에갔어" to false)
        cases.forEach { (text, expected) -> assertEquals(text, expected, storyHasLinkingExpression(text)) }
    }
}
