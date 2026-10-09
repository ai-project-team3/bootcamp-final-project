package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.storyEndIntent
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10-09 device — 「이제 끝이야」 got fills=[] ready=false next=sound, and Otto kept asking for more */
class StoryEndIntentTest {

    @Test
    fun endingWordsAreToldFromStories() {
        listOf("이제 끝이야", "끝!", "이제 그만할래", "다 했어", "이야기는 끝났어", "그만", "이제 됐어요")
            .forEach { assertTrue(it, storyEndIntent(it)) }
        listOf("끝까지 달려갔어", "그만 울었어", "다 했어 그리고 집에 갔어", "엄마랑 끝말잇기 했어")
            .forEach { assertFalse(it, storyEndIntent(it)) }
    }

    private fun verdict(next: String? = "sound") = Server.Verdict(
        reason = "test", fills = emptyList(), nextSlot = next, noLongerNeeded = null,
        storyReady = false, unclear = false, unclearOf = null, contradiction = false,
        s1Reason = false, s2Addition = false, emotion = null,
    )

    private suspend fun DemoState.say(text: String) = exchangeStoryTurn("solution", "이야기는 어떻게 끝났어?", text) {
        Server.TurnResult(verdict(), Server.Line("그랬구나", null, "이야기를 조금 더 들려줄래?"))
    }

    @Test
    fun theChildEndsAStoryThatHasItsCore() = runBlocking {
        val s = DemoState().apply {
            turn = 6; slots["place"] = "북극"; slots["problem"] = "길을 잃었어"; slots["solution"] = "북극곰이 엄마한테 데려다줬어"
        }
        s.say("이제 끝이야")
        assertTrue(s.storyReady)
    }

    @Test
    fun withoutASolutionTheClosingQuestionComesOnceThenTheChildDecides() = runBlocking {
        val s = DemoState().apply { turn = 6; slots["problem"] = "길을 잃었어" }
        s.say("이제 끝이야")
        assertFalse(s.storyReady)
        val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
        assertEquals("solution", prompt.slot)
        assertEquals("좋아, 그럼 마지막으로! 이야기는 어떻게 끝났어?", prompt.text)
        s.say("그만할래")
        assertTrue("asked to end twice — the child's wish wins", s.storyReady)
    }

    @Test
    fun aStorySentenceOrAMascotPickDoesNotEnd() = runBlocking {
        val s = DemoState().apply { turn = 6; slots["problem"] = "길을 잃었어"; slots["solution"] = "집에 갔어" }
        s.say("끝까지 달려갔어")
        assertFalse(s.storyReady)
        s.exchangeStoryTurn("solution", "어떻게 끝났어?", "끝", by = "mascot") { Server.TurnResult(verdict(), null) }
        assertFalse(s.storyReady)
    }
}
