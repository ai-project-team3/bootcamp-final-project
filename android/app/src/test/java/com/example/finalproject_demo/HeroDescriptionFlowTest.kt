package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HeroDescriptionFlowTest {
    private suspend fun waitFor(d: Director, predicate: () -> Boolean) {
        withTimeout(3_000) { while (!predicate()) delay(5) }
    }

    private suspend fun enterVoice(d: Director) {
        d.go(Scene.MAKEHERO)
        waitFor(d) { d.s.stage is Stage.CardsRow }
        delay(30)
        d.send(Reply.Tapped("voice", "말로 만들기"))
        waitFor(d) { d.s.micEnabled && "머리가" in d.s.line }
        delay(30)
    }

    @Test
    fun aHeardDescriptionRemainsUntilTheChildConfirmsBeforeTheNextQuestion() = runBlocking {
        val beforeBase = Server.base
        val beforeModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            enterVoice(d)
            d.send(Reply.Spoke("뾰족한 머리"))
            delay(200)
            assertTrue("the full heard answer must still be visible, not replaced by clothes", "뾰족한 머리" in d.s.line)
            assertTrue("the child must be asked to confirm recognition", "맞아" in d.s.line)
            assertFalse("confirmation must not expire into the next question", "어떤 옷" in d.s.line)
            d.send(Reply.Spoke("맞아"))
            waitFor(d) { "어떤 옷" in d.s.line }
        } finally { scope.cancel(); Server.base = beforeBase; Server.liveModes = beforeModes }
    }

    @Test
    fun rejectingTheHeardDescriptionRepeatsThatAttributeRatherThanAdvancing() = runBlocking {
        val beforeBase = Server.base
        val beforeModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            enterVoice(d)
            d.send(Reply.Spoke("긴 머리"))
            delay(200)
            assertTrue("recognition must be confirmed before proceeding", "긴 머리" in d.s.line && "맞아" in d.s.line)
            d.send(Reply.Tapped("no", "다시 말할래"))
            waitFor(d) { d.s.micEnabled && "머리가" in d.s.line }
            delay(30)
            d.send(Reply.Spoke("뾰족한 머리"))
            waitFor(d) { "뾰족한 머리" in d.s.line && "맞아" in d.s.line }
            d.send(Reply.Tapped("ok", "맞아"))
            waitFor(d) { "어떤 옷" in d.s.line }
        } finally { scope.cancel(); Server.base = beforeBase; Server.liveModes = beforeModes }
    }
}
