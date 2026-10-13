package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DailyStartAllowanceTest {
    private var oldBase: String? = null
    private var oldModes: Set<StoryMode> = emptySet()

    @Before fun prepare() {
        oldBase = Server.base; oldModes = Server.liveModes
        Server.base = "http://127.0.0.1:1"; Server.liveModes = setOf(StoryMode.STORY)
        CallLimits.reset(); CallLimits.enabled = true
    }

    @After fun clean() { CallLimits.reset(); Server.base = oldBase; Server.liveModes = oldModes }

    @Test fun parentThreeDoesNotPromiseThreeServiceStarts() {
        val s = DemoState().apply { dailyLimit = 3; usedToday = 0 }
        assertEquals(2, s.newStoryAllowance().remaining)
        repeat(2) { CallLimits.bookStarted(); s.usedToday++ }
        assertEquals(1, s.dayStars)
        assertEquals(0, s.newStoryAllowance().remaining)
    }

    @Test fun parentUnlimitedCannotOverrideTheServiceLimit() {
        repeat(2) { CallLimits.bookStarted() }
        val s = DemoState().apply { limitOn = false }
        assertEquals(0, s.newStoryAllowance().remaining)
        assertFalse(s.allowExtraParentStart())
        assertEquals(0, CallLimits.booksLeftToday())
    }

    @Test fun devUnlimitedAndAnOfflineModeRemainUnlimited() {
        val s = DemoState().apply { limitOn = false }
        assertNull(s.newStoryAllowance(StoryMode.DIARY).remaining)
        CallLimits.enabled = false
        assertNull(s.newStoryAllowance().remaining)
    }

    @Test fun guardianExtraRestoresOnlyTheParentAllowance() {
        CallLimits.bookStarted()
        val s = DemoState().apply { dailyLimit = 1; usedToday = 1; noticeMode = StoryMode.STORY }
        assertTrue(s.allowExtraParentStart())
        assertEquals(0, s.usedToday)
        assertEquals(1, CallLimits.booksLeftToday())
        assertEquals(1, s.newStoryAllowance().remaining)
    }

    @Test fun anExhaustedServiceCannotBeExtendedEvenWhenTheParentLimitIsAlsoZero() {
        repeat(2) { CallLimits.bookStarted() }
        val s = DemoState().apply { dailyLimit = 1; usedToday = 1; noticeMode = StoryMode.STORY }
        assertFalse(s.allowExtraParentStart())
        assertEquals(1, s.usedToday)
        assertEquals(0, CallLimits.booksLeftToday())
    }

    @Test fun aNonLiveModeUsesOnlyItsParentAllowance() {
        repeat(2) { CallLimits.bookStarted() }
        val s = DemoState().apply { dailyLimit = 1; usedToday = 1; noticeMode = StoryMode.DIARY }
        assertTrue(s.allowExtraParentStart())
        assertEquals(1, s.newStoryAllowance(StoryMode.DIARY).remaining)
        assertEquals(0, CallLimits.booksLeftToday())
    }

    @Test fun refusingANewStartKeepsTheDraftAndTheNoticeCanResumeItWithoutAnotherDebit() = runBlocking {
        repeat(2) { CallLimits.bookStarted() }
        val scope = CoroutineScope(SupervisorJob())
        val d = Director(scope).apply {
            s.limitOn = false; s.speed = 0.01
            s.paused = Scene.MAKEHERO
            s.images = 2
            s.heroCreationDraft = HeroCreationDraft().apply { generatedDescription = "red cape" }
        }
        suspend fun await(condition: () -> Boolean) = withTimeout(3_000) {
            while (!condition()) delay(5)
        }
        try {
            d.go(Scene.ADULT)
            await { d.s.stage == Stage.Adult && d.s.buttons.isNotEmpty() }
            d.send(Reply.Tapped("start", "new"))
            await { d.s.notice != null && d.s.buttons.any { "책장" in it.label } }
            assertEquals(Scene.MAKEHERO, d.s.paused)
            assertEquals(2, d.s.images)
            assertEquals("red cape", d.s.heroCreationDraft?.generatedDescription)
            d.send(Reply.Tapped("resume", "continue"))
            await { d.s.scene == Scene.MAKEHERO && d.s.stage is Stage.CardsRow }
            assertNull(d.s.notice)
            assertNull(d.s.noticeMode)
            assertEquals(2, d.s.images)
            assertEquals(0, d.s.usedToday)
            assertEquals(0, CallLimits.booksLeftToday())
        } finally { scope.cancel() }
    }
}
