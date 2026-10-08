package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.STORY_NEW_FRIEND
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.storyLooksLikeSentence
import com.example.finalproject_demo.demo.storyNameInSentence
import com.example.finalproject_demo.demo.syncStoryPresentation
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-09 device (Laya v5) — asked 「그때 누구를 만났어?」, the child said 「눈보라가 불어서 길을 잃었어」 and the judge filled
 * `newcomer` with the whole sentence. The app then drew and voiced the sentence as a friend's name.
 */
class StoryNameGuardTest {

    @Test
    fun sentencesAreToldFromNames() {
        listOf("눈보라가 불어서 길을 잃었어", "하얀 북극곰을 만났어", "곰이 있어요", "토끼가 나타났다", "눈보라가 불어서 길을 잃어",
            "친구가 없었지").forEach { assertTrue(it, storyLooksLikeSentence(it)) }
        listOf("보라색 문어", "고등어", "상어", "판다", "고슴도치처럼 생긴 바늘괴물", "하얀 북극곰", "우가우가", "외계인 친구")
            .forEach { assertFalse(it, storyLooksLikeSentence(it)) }
    }

    @Test
    fun theOneMetIsTakenFromAMeetingSentence() {
        assertEquals("하얀 북극곰", storyNameInSentence("하얀 북극곰을 만났어"))
        assertEquals("곰", storyNameInSentence("숲에서 곰이 나타났어"))
        assertEquals("토끼", storyNameInSentence("엄마는 토끼를 봤어"))
        assertNull(storyNameInSentence("눈보라가 불어서 길을 잃었어"))
        assertNull(storyNameInSentence("만났어"))
    }

    private fun verdict(vararg fills: Pair<String, String>, next: String? = null) = Server.Verdict(
        reason = "test", fills = fills.toList(), nextSlot = next, noLongerNeeded = null,
        storyReady = false, unclear = false, unclearOf = null, contradiction = false,
        s1Reason = false, s2Addition = false, emotion = null,
    )

    private fun started() = DemoState().apply { turn = 4; slots["problem"] = "길이 막혔어" }

    @Test
    fun aSentenceIsNotTheNewcomerAndTheQuestionComesOnceMore() = runBlocking {
        val s = started()
        val said = "눈보라가 불어서 길을 잃었어"
        s.exchangeStoryTurn("newcomer", "그때 누구를 만났어?", said) {
            Server.TurnResult(verdict("newcomer" to said, next = "cause"), Server.Line("그랬구나", null, "왜 그랬을까?"))
        }
        s.syncStoryPresentation()
        assertNull("the sentence is not the newcomer", s.slots["newcomer"])
        assertEquals("외계인", s.newcomerKind)
        assertTrue("the sentence stays in the story", said in s.slots["extra"].orEmpty())
        val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
        assertEquals("newcomer", prompt.slot)
        assertFalse("the question never names the sentence", said in prompt.text)

        // a sentence again — the newcomer is just 「새 친구」, and the sentence is kept once
        s.exchangeStoryTurn("newcomer", prompt.text, said) {
            Server.TurnResult(verdict("newcomer" to said, next = "cause"), Server.Line("그랬구나", null, "왜 그랬을까?"))
        }
        s.syncStoryPresentation()
        assertEquals(STORY_NEW_FRIEND, s.slots["newcomer"])
        assertEquals(STORY_NEW_FRIEND, s.newcomerKind)
        assertEquals(said, s.slots["extra"])
        assertEquals("cause", s.storyNextSlot)
    }

    @Test
    fun aMeetingSentenceGivesTheOneMet() = runBlocking {
        val s = started()
        s.exchangeStoryTurn("newcomer", "그때 누구를 만났어?", "하얀 북극곰을 만났어") {
            Server.TurnResult(verdict("newcomer" to it.utterance, next = "cause"), Server.Line("그랬구나", null, "왜 그랬을까?"))
        }
        assertEquals("하얀 북극곰", s.slots["newcomer"])
        assertEquals("cause", s.storyNextSlot)
    }

    @Test
    fun aSentenceInTheNewcomerWhileAnotherSlotWasAskedFillsNothing() = runBlocking {
        val s = started()
        val said = "바람이 불어서 나무가 쓰러졌어"
        s.exchangeStoryTurn("cause", "왜 그랬을까?", said) {
            Server.TurnResult(verdict("cause" to said, "newcomer" to said, next = "solution"), Server.Line("그랬구나", null, "어떻게 됐어?"))
        }
        assertEquals(said, s.slots["cause"])
        assertNull(s.slots["newcomer"])
        assertNull("already in the cause — not repeated as extra", s.slots["extra"])
        assertEquals("solution", s.storyNextSlot)
    }

    @Test
    fun aNameSlotWithASentenceKeepsTheFriendsName() = runBlocking {
        val s = started().apply { slots["newcomer"] = "북극곰"; friendName = "북극곰" }
        s.exchangeStoryTurn("name", "그 친구를 뭐라고 부를까?", "같이 눈사람을 만들었어") {
            Server.TurnResult(verdict("name" to it.utterance), Server.Line("그랬구나", null, null))
        }
        s.syncStoryPresentation()
        assertNull(s.slots["name"])
        assertEquals("북극곰", s.friendName)
        assertEquals("name", s.nextStoryPrompt(s.storyServerQuestion)?.slot)
    }

    @Test
    fun anOrdinaryNameIsKept() = runBlocking {
        val s = started()
        s.exchangeStoryTurn("newcomer", "그때 누구를 만났어?", "보라색 문어") {
            Server.TurnResult(verdict("newcomer" to it.utterance, next = "cause"), null)
        }
        assertEquals("보라색 문어", s.slots["newcomer"])
    }
}
