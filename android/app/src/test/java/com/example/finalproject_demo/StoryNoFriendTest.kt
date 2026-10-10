package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.captureStoryVisuals
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.storyVisualsFromJson
import com.example.finalproject_demo.demo.toJson
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.recordStorySound
import com.example.finalproject_demo.demo.syncStoryPresentation
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 10-09 device second round — the missions still showed the alien, and mission 2 put the cause on the mascot */
    @Test
    fun theMissionsHaveNoFriendAndNoCauseOnTheMascot() {
        val s = story().apply { slots["cause"] = "무서워서 울었어"; syncStoryPresentation() }
        assertNull("mission 2 receiver — the mascot (E1Give falls back to it)", s.friendOrPartnerArt)
        val line = s.m2Line(easy = false)
        assertFalse(line, "울었대" in line)
    }

    /** 「그 친구 이름은 뭐야?」 with no newcomer — not asked, and a name answered anyway names nobody */
    @Test
    fun aNameWithoutANewcomerIsNotAskedOrUsed() {
        val s = story().apply { turn = 5; storyNextSlot = "name"; storyServerQuestion = "그 친구 이름은 뭐야?" }
        assertFalse("name" == s.nextStoryPrompt(s.storyServerQuestion)?.slot)
        s.slots["name"] = "뽀뽀"
        s.syncStoryPresentation()
        assertFalse("뽀뽀" == s.friendName)
        s.slots["newcomer"] = "아기 펭귄"
        s.syncStoryPresentation()
        assertEquals("뽀뽀", s.friendName)
    }

    /** #387 review — saved and reopened, a story with nobody met still has no friend; an older book is as it was */
    @Test
    fun aReopenedBookKeepsNoFriend() {
        fun reopen(made: DemoState) = DemoState().apply {
            restoreStoryBook(SavedStoryBook("b", "책", "forest", "bg_forest", listOf(SavedStoryPage(PageKind.DEPART, "쪽")),
                storyVisualsFromJson(made.captureStoryVisuals().toJson())))
        }
        val lonely = story().apply { templateKey = "C" }
        assertTrue(reopen(lonely).liveStoryWithoutFriend)
        assertFalse(reopen(lonely).hasCompanion)
        val withFriend = story().apply { templateKey = "C"; slots["newcomer"] = "북극곰"; newcomerKind = "북극곰" }
        assertFalse(reopen(withFriend).liveStoryWithoutFriend)
        val old = storyVisualsFromJson(lonely.captureStoryVisuals().toJson().apply { remove("noFriend") })
        assertFalse("an older book has no flag and keeps its old look", old.noFriend)
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
