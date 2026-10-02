package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookShowsDino
import com.example.finalproject_demo.demo.soundHolderName
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #50 2번 — 서버로 진행한 동화 책에 **아이가 정하지 않은 공룡**(테마 기본 공룡 · 트리케라톱스)이 끼어들었다.
 * 표지 · 미션 1 쪽 · 마지막 쪽이 `!isDiary` 만 보고 공룡을 그렸다. 친구 평가(`sceneFriends`)와 같은 기준으로 뺀다.
 */
class StoryBookDinoTest {

    private var base: String? = null
    private var modes: Set<StoryMode> = emptySet()

    @Before fun keep() { base = Server.base; modes = Server.liveModes }
    @After fun restore() { Server.base = base; Server.liveModes = modes }

    private fun state(mode: StoryMode, live: Boolean) = DemoState().apply {
        this.mode = mode
        Server.base = if (live) "http://127.0.0.1:9" else null
        Server.liveModes = if (live) setOf(mode) else emptySet()
    }

    @Test
    fun aLiveStoryBookHasNoDefaultDinosaur() {
        assertFalse("서버 동화 책에 기본 공룡이 섰다", state(StoryMode.STORY, live = true).bookShowsDino)
    }

    @Test
    fun aScriptedStoryBookKeepsItsDinosaur() {
        // 대본 동화는 장면 「공룡」에서 아이가 공룡을 만난다 — 그대로 둔다
        assertTrue(state(StoryMode.STORY, live = false).bookShowsDino)
    }

    @Test
    fun diaryAndCoopBooksNeverHadADinosaur() {
        listOf(true, false).forEach { live ->
            assertFalse(state(StoryMode.DIARY, live).bookShowsDino)
            assertFalse(state(StoryMode.COOP, live).bookShowsDino)
        }
    }

    /** 마지막 쪽에서 녹음한 소리를 들으려고 누르는 인물 — 서버 동화는 이야기에서 정한 새 친구 */
    @Test
    fun theRecordedSoundBelongsToTheChosenFriendInALiveStory() {
        val s = state(StoryMode.STORY, live = true).apply {
            slots["newcomer"] = "문어"; newcomer = "문어"; friendName = "도리"
        }
        assertEquals("도리", s.soundHolderName)
    }

    @Test
    fun aLiveStoryWithoutAChosenFriendHasNobodyToTap() {
        assertNull(state(StoryMode.STORY, live = true).soundHolderName)
    }

    @Test
    fun aScriptedStoryStillTapsTheDinosaur() {
        val s = state(StoryMode.STORY, live = false)
        assertEquals(s.dino.name, s.soundHolderName)
    }
}
