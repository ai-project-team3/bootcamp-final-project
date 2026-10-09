package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.STORY_NEW_FRIEND
import com.example.finalproject_demo.demo.TurnHistory
import com.example.finalproject_demo.demo.exchangeStoryTurn
import com.example.finalproject_demo.demo.nextStoryPrompt
import com.example.finalproject_demo.demo.storyLooksLikeSentence
import com.example.finalproject_demo.demo.storyNameInSentence
import com.example.finalproject_demo.demo.storyServerInput
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
            "친구가 없었지", "곰이 보여요", "눈보라가 내려요", "친구가 가요", "같이 놀아요", "집에 갑니다")
            .forEach { assertTrue(it, storyLooksLikeSentence(it)) }
        listOf("보라색 문어", "고등어", "상어", "판다", "고슴도치처럼 생긴 바늘괴물", "하얀 북극곰", "우가우가", "외계인 친구",
            "요요", "고양이", "호랑이")
            .forEach { assertFalse(it, storyLooksLikeSentence(it)) }
    }

    @Test
    fun theOneMetIsTakenFromAMeetingSentence() {
        assertEquals("하얀 북극곰", storyNameInSentence("하얀 북극곰을 만났어"))
        assertEquals("곰", storyNameInSentence("숲에서 곰이 나타났어"))
        assertEquals("토끼", storyNameInSentence("엄마는 토끼를 봤어"))
        // a noun's own final 이 is not a particle (#375 review)
        assertEquals("고양이", storyNameInSentence("고양이 봤어"))
        assertEquals("호랑이", storyNameInSentence("호랑이 만났어"))
        assertEquals("고양이", storyNameInSentence("고양이를 만났어"))
        assertEquals("원숭이", storyNameInSentence("원숭이랑 만났어"))
        assertEquals("곰", storyNameInSentence("곰이랑 만났어"))
        assertEquals("펭귄", storyNameInSentence("펭귄이 나타났어"))
        assertEquals("토끼", storyNameInSentence("토끼가 나타났어"))
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
        assertTrue("the sentence stays in the story", said in s.storyServerInput().slots["extra"].orEmpty())
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
        assertEquals("the app chose 「새 친구」 — not the child (rule 5)", "mascot", s.slotBy["newcomer"])
        val input = s.storyServerInput()
        assertEquals(said, input.slots["extra"])
        assertEquals("the kept sentence is the child's", "child", input.sources["extra"])
        assertEquals("cause", s.storyNextSlot)
    }

    /** Undo takes back the one more ask with the answer — the same sentence again is asked again, not 「새 친구」 */
    @Test
    fun undoTakesBackTheOneMoreAsk() = runBlocking {
        val s = started()
        val history = TurnHistory(s)
        val said = "눈보라가 불어서 길을 잃었어"
        suspend fun answer() = s.exchangeStoryTurn("newcomer", "그때 누구를 만났어?", said) {
            Server.TurnResult(verdict("newcomer" to said, next = "cause"), Server.Line("그랬구나", null, "왜 그랬을까?"))
        }
        history.before(); answer(); history.done()
        assertTrue("newcomer" in s.storyWhoReasked)
        assertTrue(history.undo())
        assertTrue("the one more ask is taken back too", s.storyWhoReasked.isEmpty())
        assertNull(s.storyServerInput().slots["extra"])

        history.before(); answer(); history.done()
        assertNull("asked again, not 「새 친구」", s.slots["newcomer"])
        assertEquals("newcomer", s.storyNextSlot)
        assertTrue(history.undo()); assertTrue(history.redo())
        assertTrue("redo brings the one more ask back", "newcomer" in s.storyWhoReasked)
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
        assertNull("already in the cause — not repeated as extra", s.storyServerInput().slots["extra"])
        assertEquals("solution", s.storyNextSlot)
    }

    /** P1 (#375 review) — the server's next question names the turned-away sentence: the app asks its own question */
    @Test
    fun aNextQuestionNamingTheSentenceIsReplaced() = runBlocking {
        val s = started()
        val said = "바람이 불어서 나무가 쓰러졌어"
        s.exchangeStoryTurn("cause", "왜 그랬을까?", said) {
            Server.TurnResult(verdict("newcomer" to said, next = "newcomer"), Server.Line("그랬구나", null, "${said}는 어떻게 생겼어?"))
        }
        assertNull(s.slots["newcomer"])
        val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
        assertEquals("newcomer", prompt.slot)
        assertFalse(said in prompt.text)
    }

    /** P2 (#375 review) — after the one more ask, the judge choosing the empty name again does not ask it again */
    @Test
    fun anExhaustedNameIsNotAskedAgain() = runBlocking {
        val s = started().apply { slots["newcomer"] = "북극곰"; slotBy["newcomer"] = "child" }
        val said = "같이 눈사람을 만들었어"
        s.exchangeStoryTurn("name", "그 친구를 뭐라고 부를까?", said) {
            Server.TurnResult(verdict("name" to said, next = "name"), Server.Line("그랬구나", null, "이름은 뭐야?"))
        }
        assertEquals("name", s.nextStoryPrompt(s.storyServerQuestion)?.slot)
        repeat(2) { attempt ->
            val prompt = s.nextStoryPrompt(s.storyServerQuestion)!!
            s.exchangeStoryTurn(prompt.slot, prompt.text, said) {
                Server.TurnResult(verdict("name" to said, next = "name"), Server.Line("그랬구나", null, "이름은 뭐야?"))
            }
            assertNull(s.slots["name"])
            assertEquals("answer ${attempt + 2}: name is not chosen again", null, s.storyNextSlot)
            assertFalse("name" == s.nextStoryPrompt(s.storyServerQuestion)?.slot)
        }
    }

    /** P2 (#375 review) — a mascot sentence kept beside a child extra keeps its own source */
    @Test
    fun keptSentencesKeepTheirOwnSources() = runBlocking {
        val s = started().apply { slots["extra"] = "엄마랑 같이 갔어"; slotBy["extra"] = "child" }
        val said = "눈보라가 불어서 길을 잃었어"
        s.exchangeStoryTurn("newcomer", "그때 누구를 만났어?", said, by = "mascot") {
            Server.TurnResult(verdict("newcomer" to said, next = "cause"), null)
        }
        assertEquals("child", s.slotBy["extra"])
        assertEquals("the mascot sentence is not the child's", "mascot",
            s.slotBy.entries.single { it.key.startsWith("whoSaid:") }.value)
        val input = s.storyServerInput()
        assertTrue(said in input.slots["extra"].orEmpty())
        assertNull("mixed child · mascot contributions are not one speaker", input.sources["extra"])
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
