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
class HeroCreationResumeTest {
    private suspend fun await(condition: () -> Boolean) = withTimeout(3_000) {
        while (!condition()) delay(5)
    }

    private suspend fun resume(d: Director) {
        d.leaveToRoom()
        await { d.s.scene == Scene.ADULT && d.s.stage == Stage.Adult }
        delay(30)
        d.send(Reply.Tapped("resume", "이어서 하기"))
        await { d.s.scene == Scene.MAKEHERO && d.s.stage != Stage.Empty }
        delay(100)
    }

    private suspend fun createFirst(d: Director) {
        d.go(Scene.MAKEHERO)
        await { d.s.stage is Stage.CardsRow }
        delay(30)
        d.send(Reply.Tapped("voice", "말로 만들기"))
        await { d.s.micEnabled && "머리가" in d.s.line }
        for (answer in listOf("짧은 머리", "빨간 옷", "안경 없어")) {
            delay(30)
            d.send(Reply.Spoke(answer))
            await { d.s.stage is Stage.HeroAnswer }
            d.send(Reply.Tapped("ok", "맞아"))
            await { d.s.stage !is Stage.HeroAnswer }
        }
        await { d.s.stage is Stage.Confirm }
        delay(30)
    }

    private suspend fun withHero(check: suspend (Director) -> Unit) = coroutineScope {
        val base = Server.base
        val modes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            Server.base = "http://127.0.0.1:1"
            Server.liveModes = setOf(StoryMode.STORY)
            val d = Director(scope).apply { s.speed = 0.01; s.timerOn = false }
            check(d)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = base
            Server.liveModes = modes
        }
    }

    @Test fun resumeKeepsTheGeneratedCandidateInsteadOfStartingAgain() = runBlocking {
        withHero { d ->
            createFirst(d)
            val before = d.s.images
            resume(d)
            assertTrue("Continue must restore the generated candidate", d.s.stage is Stage.Confirm)
            assertEquals(before, d.s.images)
        }
    }

    @Test fun resumeDuringRepairKeepsTheSpentRetryAndPreviouslyMadeCandidate() = runBlocking {
        withHero { d ->
            createFirst(d)
            d.send(Reply.Tapped("no", "싫어"))
            await { d.s.micEnabled && "바꾸면" in d.s.line }
            resume(d)
            assertTrue("Continue must ask for the pending repair", "바꾸면" in d.s.line)
            d.send(Reply.Spoke("노란 모자"))
            await { d.s.stage is Stage.HeroAnswer }
            d.send(Reply.Tapped("ok", "맞아"))
            await { d.s.stage is Stage.Confirm && d.s.images == 2 }
            assertEquals(1, (d.s.stage as Stage.Confirm).redraws)
            assertEquals(2, d.s.heroTries.size)
        }
    }

    @Test fun resumeAtNamingDoesNotGenerateAnotherHero() = runBlocking {
        withHero { d ->
            createFirst(d)
            d.send(Reply.Tapped("ok", "좋아"))
            await { d.s.stage is Stage.NameEntry && d.s.micEnabled }
            resume(d)
            assertTrue("The accepted hero must remain at naming", d.s.stage is Stage.NameEntry)
            assertEquals(1, d.s.images)
            d.send(Reply.Tapped(NAME_TYPED, "콩이"))
            await { d.s.scene == Scene.PLACE }
            assertEquals("콩이", d.s.storyHeroCall)
            d.s.resetStory()
            d.go(Scene.MAKEHERO)
            await { d.s.scene == Scene.MAKEHERO && d.s.stage is Stage.CardsRow }
            assertTrue("A deliberately new hero still starts a separate creation",
                (d.s.stage as Stage.CardsRow).cards.any { it.value == "voice" })
        }
    }

    @Test fun leavingDuringGenerationDoesNotBuyAnotherAttempt() = runBlocking {
        withHero { d ->
            createFirst(d)
            d.send(Reply.Tapped("no", "싫어"))
            await { d.s.micEnabled && "바꾸면" in d.s.line }
            // Use the local generation delay so cancellation has a deterministic window.
            Server.liveModes = emptySet()
            d.s.speed = 1.0
            d.send(Reply.Spoke("긴 머리", "hair:long"))
            await { d.s.stage is Stage.Making && d.s.images == 2 }
            d.s.speed = 0.01
            resume(d)
            assertTrue("Continue restores this reserved attempt, not a new generation", d.s.stage is Stage.Confirm)
            assertEquals(2, d.s.images)
            assertEquals(1, (d.s.stage as Stage.Confirm).redraws)
            assertEquals(2, d.s.heroTries.size)
        }
    }

    @Test fun resumeAfterAllRetriesKeepsTheSameThreeChoices() = runBlocking {
        withHero { d ->
            createFirst(d)
            repeat(2) { attempt ->
                d.send(Reply.Tapped("no", "싫어"))
                await { d.s.micEnabled && "바꾸면" in d.s.line }
                delay(30)
                d.send(Reply.Spoke("빨간 모자"))
                await { d.s.stage is Stage.HeroAnswer }
                d.send(Reply.Tapped("ok", "맞아"))
                await { d.s.stage is Stage.Confirm && d.s.images == attempt + 2 }
                delay(30)
            }
            d.send(Reply.Tapped("no", "싫어"))
            await { d.s.stage is Stage.CardsRow }
            val cards = (d.s.stage as Stage.CardsRow).cards
            resume(d)
            assertEquals("Continue must restore the exhausted creation's choices", cards,
                (d.s.stage as? Stage.CardsRow)?.cards)
            assertEquals(3, d.s.images)
            assertTrue("The picker must not offer a fresh creation", "여태 만든" in d.s.line)
            delay(30)
            d.send(Reply.Tapped("0", "1번"))
            await { d.s.stage is Stage.NameEntry }
            delay(30)
            d.send(Reply.Tapped(NAME_TYPED, "첫콩이"))
            await { d.s.scene == Scene.PLACE }
            assertTrue("The first candidate's original words must survive", "짧은 머리" in d.s.storyHeroDescription)
            assertFalse("A rejected later candidate must not describe the selected picture", "빨간 모자" in d.s.storyHeroDescription)
        }
    }
}
