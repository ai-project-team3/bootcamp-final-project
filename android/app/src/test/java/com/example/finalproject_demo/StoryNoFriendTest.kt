package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.recordStorySound
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 device (Laya v5) — a live story where the child met nobody: Otto asked 「친구의 소리를 직접 만들어 볼까?」 and the
 * script's default alien stood on the cover and took mission 2. The app does not make up a friend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryNoFriendTest {
    private var base: String? = null
    private var modes: Set<StoryMode> = emptySet()

    @Before fun live() { base = Server.base; modes = Server.liveModes; Server.base = "http://127.0.0.1:9"; Server.liveModes = setOf(StoryMode.STORY) }
    @After fun restore() { Server.base = base; Server.liveModes = modes }

    private fun story() = DemoState().apply { slots["place"] = "얼음 산"; slots["problem"] = "아기 펭귄이 엄마를 잃어버렸어" }

    @Test
    fun nobodyMetMeansNoFriendAndMissionTwoGoesToTheMascot() {
        val s = story()
        assertTrue(s.liveStoryWithoutFriend)
        assertFalse(s.hasCompanion)
        assertEquals("마스코트", s.giveTargetName)
    }

    @Test
    fun aNewcomerOrADrawingIsAFriend() {
        assertFalse(story().apply { slots["newcomer"] = "북극곰" }.liveStoryWithoutFriend)
        assertTrue(story().apply { slots["newcomer"] = "북극곰"; friendName = "{친구1}" }.hasCompanion)
        Server.liveModes = emptySet()
        assertFalse("a scripted story keeps its newcomer", story().liveStoryWithoutFriend)
    }

    @Test
    fun theFriendSoundIsNotAskedWithoutAFriend() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.slots["place"] = "얼음 산"
        try {
            d.recordStorySound()
            assertTrue(d.s.storySoundAttempted)
            assertTrue("sound" in d.s.storyUnneededSlots)
            assertFalse("Otto never asked for the friend's sound", d.s.log.any { "소리를 직접" in it })
        } finally { scope.cancel() }
    }
}
