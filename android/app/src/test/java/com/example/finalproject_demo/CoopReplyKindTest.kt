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
 * #327 §3-3 — 아이 말을 오또 질문과 견줘 가른다(앱 규칙). 받아쓰기라 「?」가 없을 수 있고, 「음…」으로 시작할 수 있다.
 */
class CoopReplyKindTest {
    private fun kind(q: String, said: String, partner: Boolean = true) = classifyCoopReply(said, q, partner)

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
        assertEquals(CoopReply.ToPartner("엄마 우리 뭐 먹었지", "엄마"), kind("거기서 뭐 먹었어?", "엄마 우리 뭐 먹었지"))
        assertTrue(kind("거기서 누굴 만났어?", "기린은 뭐 먹어?") is CoopReply.World)
        assertEquals(CoopReply.Answer, kind("그때 뭐 하고 있었어?", "뭐 그냥 놀았어"))
        assertEquals(CoopReply.Answer, kind("그때 뭐 하고 있었어?", "츄러스 사 먹었어"))
        assertEquals(CoopReply.NonAnswer, kind("그때 뭐 하고 있었어?", "몰라"))
        assertEquals("있었나를 묻는 질문의 「없어」는 답", CoopReply.Answer, kind("누구랑 갔어?", "없어"))
        assertEquals(CoopReply.NonAnswer, kind("왜 그랬을까?", "아니"))
    }

    /** 받아쓰기 변형 — 「?」 있음 · 없음 · 「음…」 머리 */
    @Test
    fun transcriptionVariants() {
        listOf("그게 뭐야?", "그게 뭐야", "음… 그게 뭐야", "어, 그게 뭐야?").forEach {
            assertTrue(it, kind("거기서 무슨 일이 있었어?", it) is CoopReply.AboutQuestion)
        }
        listOf("오또는 어디 살아?", "오또는 어디 살아", "음 오또는 어디 살아").forEach {
            assertTrue(it, kind("거기서 뭐 봤어?", it) is CoopReply.World)
        }
        assertTrue(kind("거기서 뭐 먹었어?", "아빠, 우리 뭐 먹었더라?") is CoopReply.ToPartner)
    }

    /** 옆에 어른이 없으면(#303 partner none) 부르는 말이 있어도 세상 질문으로 본다 */
    @Test
    fun withoutAPartnerACallIsAWorldQuestion() {
        assertTrue(kind("거기서 뭐 먹었어?", "엄마 우리 뭐 먹었지", partner = false) is CoopReply.World)
    }

    /** 대답 속 묻는 말 · 모르겠다는 말은 질문이 아니다 */
    @Test
    fun answersThatOnlyContainAnAskWordStayAnswers() {
        listOf("뭐더라 아 츄러스 먹었어", "어디 갔는지 기억 안 나", "엄마랑 놀이공원 갔어", "아빠가 왜 그랬는지 몰라").forEach {
            assertFalse(it, kind("그때 뭐 하고 있었어?", it).isQuestion)
        }
        assertTrue("일곱 어절 넘는 긴 말 속 「뭐」는 답", !kind("거기서 뭐 했어?", "동생이랑 같이 미끄럼틀 타고 그네 타고 모래로 뭐 만들었어").isQuestion)
    }

    /** 받아쓰기가 「?」를 빼면 「뭐 먹었어」가 「뭐 먹었구나!」가 됐다 — 묻는 말이 든 말은 되비추지 않는다 */
    @Test
    fun aStatedQuestionIsNotEchoed() {
        assertNull(pastEcho("뭐 먹었어"))
        assertNull(pastEcho("어디 갔었어"))
        assertEquals("츄러스 사 먹었구나!", pastEcho("츄러스 사 먹었어"))
    }
}
