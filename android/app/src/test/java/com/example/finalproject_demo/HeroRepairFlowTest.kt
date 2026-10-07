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
@Config(sdk = [28])
class HeroRepairFlowTest {
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

    private suspend fun enterRepair(d: Director) {
        enterVoice(d)
        for (answer in listOf("뾰족한 머리", "빨간 망토", "동그란 안경")) {
            d.send(Reply.Spoke(answer))
            waitFor(d) { d.s.stage is Stage.HeroAnswer }
            d.send(Reply.Tapped("ok", "맞아"))
            waitFor(d) { d.s.stage !is Stage.HeroAnswer }
            delay(30)
        }
        waitFor(d) { d.s.stage is Stage.Confirm }
        delay(30)
        d.send(Reply.Tapped("no", "싫어"))
        waitFor(d) { d.s.micEnabled && "바꾸면" in d.s.line }
        delay(30)
    }

    @Test
    fun aRepairAnswerWaitsForConfirmationBeforeGeneratingAgain() = runBlocking {
        val beforeBase = Server.base
        val beforeModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            enterRepair(d)
            val imagesBefore = d.s.images
            d.send(Reply.Spoke("머리가 마음에 안 들어"))
            delay(200)
            assertTrue("the repair answer must stay visible for confirmation", d.s.stage is Stage.HeroAnswer)
            assertTrue("머리가 마음에 안 들어" in d.s.line && "맞아" in d.s.line)
            assertEquals("unconfirmed changes must not start a new image", imagesBefore, d.s.images)
            d.send(Reply.Tapped("ok", "맞아"))
            waitFor(d) { d.s.stage is Stage.Confirm && d.s.images == imagesBefore + 1 }
        } finally { scope.cancel(); Server.base = beforeBase; Server.liveModes = beforeModes }
    }

    @Test
    fun rejectingARepairAnswerAsksWhatToChangeAgainWithoutUsingIt() = runBlocking {
        val beforeBase = Server.base
        val beforeModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
        Server.base = "http://127.0.0.1:1"
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            enterRepair(d)
            val imagesBefore = d.s.images
            d.send(Reply.Spoke("노란 옷"))
            delay(200)
            assertTrue("repair recognition must be confirmed before regeneration", d.s.stage is Stage.HeroAnswer)
            d.send(Reply.Tapped("no", "다시 말할래"))
            waitFor(d) { d.s.micEnabled && "바꾸면" in d.s.line }
            assertEquals(imagesBefore, d.s.images)
            delay(30)
            d.send(Reply.Spoke("파란 망토"))
            waitFor(d) { d.s.stage is Stage.HeroAnswer && "파란 망토" in d.s.line }
            d.send(Reply.Spoke("맞아"))
            waitFor(d) { d.s.stage is Stage.Confirm && d.s.images == imagesBefore + 1 }
        } finally { scope.cancel(); Server.base = beforeBase; Server.liveModes = beforeModes }
    }

}
