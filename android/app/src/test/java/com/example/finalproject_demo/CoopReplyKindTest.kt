package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopReply
import com.example.finalproject_demo.demo.classifyCoopReply
import com.example.finalproject_demo.demo.isQuestion
import com.example.finalproject_demo.demo.pastEcho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #327 §3-3 · #341 — sorts the child's reply against Otto's question (app rules). Speech-to-text may drop the 「?」 and may start with 「음…」.
 */
class CoopReplyKindTest {
    private fun kind(q: String, said: String) = classifyCoopReply(said, q)

    @Test
    fun theDesignTable() {
        assertEquals(CoopReply.PremiseDenied("줬", null), kind("기린한테 뭐 줬어?", "아니, 안 줬어"))
        assertEquals(CoopReply.PremiseDenied("없", "기린"), kind("기린한테 뭐 줬어?", "기린 없었어"))
        assertEquals(CoopReply.Corrected("사과 줬어", null), kind("기린한테 뭐 줬어?", "아니 사과 줬어"))
        assertEquals(CoopReply.Corrected("수영장 갔어", "놀이터"), kind("놀이터에서 뭐 했어?", "놀이터 말고 수영장 갔어"))
        assertEquals(CoopReply.Answer, kind("기분이 어땠어?", "안 무서웠어"))
        assertEquals(CoopReply.Answer, kind("누구랑 갔어?", "친구가 없어서 혼자 갔어"))
        assertTrue(kind("그다음엔 뭐 했어?", "뭐를 넣어?") is CoopReply.AboutQuestion)
        assertTrue("「?」 없이도", kind("까닭이 뭘까?", "까닭이 뭐야") is CoopReply.AboutQuestion)
        assertEquals(CoopReply.Recall("엄마 우리 뭐 먹었지", "엄마"), kind("거기서 뭐 먹었어?", "엄마 우리 뭐 먹었지"))
        assertTrue(kind("거기서 누굴 만났어?", "기린은 뭐 먹어?") is CoopReply.World)
        assertEquals(CoopReply.Answer, kind("그때 뭐 하고 있었어?", "뭐 그냥 놀았어"))
        assertEquals(CoopReply.Answer, kind("그때 뭐 하고 있었어?", "츄러스 사 먹었어"))
        assertEquals(CoopReply.NonAnswer, kind("그때 뭐 하고 있었어?", "몰라"))
        assertEquals("있었나를 묻는 질문의 「없어」는 답", CoopReply.Answer, kind("누구랑 갔어?", "없어"))
        assertEquals(CoopReply.NonAnswer, kind("왜 그랬을까?", "아니"))
    }

    /** #341 — recalling (even when calling the adult nearby) · an answer found after recalling */
    @Test
    fun recallingIsNotAskingTheParent() {
        assertTrue(kind("누구랑 갔어?", "누구랑 갔더라") is CoopReply.Recall)
        assertEquals(CoopReply.AnswerAfterRecall("할머니"), kind("누구랑 갔어?", "엄마 우리 누구랑 갔지? 아 할머니!"))
        assertEquals(CoopReply.AnswerAfterRecall("츄러스"), kind("거기서 뭐 먹었어?", "뭐 먹었더라 츄러스"))
        assertTrue(kind("거기서 뭐 먹었어?", "아빠, 우리 뭐 먹었더라?") is CoopReply.Recall)
    }

    /**
     * #332 lead review P1 — casual statements were sorted as questions (9 of 18 real answers). 「왜냐면 …」 is the cause step's most common answer.
     * Without 「?」 it is a question only if an ask word comes first · three eojeols or fewer · not ending in the past
     */
    @Test
    fun plainStatementsWithAnAskWordAreAnswers() {
        listOf(
            "왜냐하면 사자가 커서 무서웠어", "왜냐면 배고파서 그랬어", "어느 날 공룡이 나타났어", "언제나처럼 그네 탔어",
            "무슨 소리가 났어", "누가 내 사탕 먹었어", "어디 멀리 갔어", "어떻게 했냐면 물로 껐어", "뭘 했냐면 수영했어",
            "뭐더라 아 츄러스 먹었어", "어디 갔는지 기억 안 나", "엄마랑 놀이공원 갔어", "아빠가 왜 그랬는지 몰라",
            "동생이랑 같이 미끄럼틀 타고 그네 타고 모래로 뭐 만들었어",
        ).forEach { assertFalse(it, kind("그때 뭐 하고 있었어?", it).isQuestion) }
    }

    /** Speech-to-text variants — with and without 「?」 · a 「음…」 head */
    @Test
    fun transcriptionVariants() {
        listOf("그게 뭐야?", "그게 뭐야", "음… 그게 뭐야", "어, 그게 뭐야?").forEach {
            assertTrue(it, kind("거기서 무슨 일이 있었어?", it) is CoopReply.AboutQuestion)
        }
        listOf("오또는 어디 살아?", "어디 살아", "음 왜 그래").forEach {
            assertTrue(it, kind("거기서 뭐 봤어?", it) is CoopReply.World)
        }
    }

    /** When speech-to-text dropped the 「?」, 「뭐 먹었어」 became 「뭐 먹었구나!」 — a reply with an ask word is not echoed */
    @Test
    fun aStatedQuestionIsNotEchoed() {
        assertNull(pastEcho("뭐 먹었어"))
        assertNull(pastEcho("어디 갔었어"))
        assertEquals("츄러스 사 먹었구나!", pastEcho("츄러스 사 먹었어"))
    }
}
