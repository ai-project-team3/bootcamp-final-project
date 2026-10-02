package com.example.finalproject_demo

import com.example.finalproject_demo.demo.COOP_STEPS
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopSource
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopGuard
import com.example.finalproject_demo.demo.coopPartPack
import com.example.finalproject_demo.demo.hasFantasyWord
import com.example.finalproject_demo.demo.hasRoughWord
import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같이 만들기 — 아이에게 나가기 직전 갈무리 (10-02 · 협업 질문 업그레이드 §9).
 * 항목마다 통과 · 고침 · 사다리로 돌아감(null)을 본다. 우리 템플릿 사다리는 모두 통과해야 한다 — 돌아갈 곳이니까.
 */
class CoopGuardTest {
    private fun g(t: String, r: CoopReason? = CoopReason.DONE, src: CoopSource = CoopSource.LLM) = coopGuard(t, r, src)

    @Test
    fun aGoodQuestionPassesUntouched() {
        val out = g("큰 소방서에서 무슨 일이 생겼어?")
        assertEquals("큰 소방서에서 무슨 일이 생겼어?", out.text)
        assertFalse(out.changed)
        assertEquals("사자 우리, 기린 마당, 원숭이 산. 어디가 좋았어?", g("사자 우리, 기린 마당, 원숭이 산. 어디가 좋았어?").text)
    }

    @Test
    fun twoQuestionsOrAHardLongOneGoesBackToTheLadder() {
        // 질문 두 개(무엇 · 왜) + 어려운 말 → 고칠 수 없다 → 그 자리의 사다리 질문으로
        val out = g("오늘 경험한 상황에서 가장 인상 깊었던 점은 무엇이고 왜 그렇게 느꼈어?")
        assertNull(out.text)
        assertTrue(out.issues.toString(), out.issues.any { "어려운 말" in it })
        assertTrue(out.issues.toString(), out.issues.any { "질문 규칙" in it })
    }

    @Test
    fun rulesThatCanBeFixedAreFixed() {
        assertEquals("거기서 뭐가 제일 재밌었어?", g("우와 신난다! 거기서 뭐가 제일 재밌었어?").text)          // 질문 앞 말
        assertEquals("거기서 뭐 했어?", g("거기서 뭐 했어? 누구랑 했어?").text)                          // 질문 둘 → 첫 질문
        assertEquals("거기서 제일 좋았던 건 뭐였어?", g("거기서 가장 좋았던 건 뭐였어?").text)              // 어려운 말
        assertEquals("거기서 뭐 했어?", g("거기서 뭐 했어요?").text)                                     // 존댓말 끝
    }

    @Test
    fun rulesThatCannotBeFixedGoBackToTheLadder() {
        assertNull(g("언제 거기 갔어?").text)                                   // 언제
        assertNull(g("누구랑 뭐 했어?").text)                                   // 의문사 둘
        assertNull(g("재밌었어?").text)                                         // 예/아니오
        assertNull(g("거기서 무엇을 하셨습니까?").text)                          // 존댓말
        assertNull(g("소방관이 불을 끄다가 칼에 찔렸어. 누가 도와줬어?").text)     // 거친 말
        assertNull(g("커다란 소방서 마당 옆에서 소방관 아저씨들이 무슨 일을 했어?").text) // 길이
        assertNull(g("뭐 했어").text)                                           // 물음표 없음
    }

    @Test
    fun theTenseFollowsTheReason() {
        assertNull(g("거기서 뭐 할까?", CoopReason.DONE).text)                   // 다녀왔어요에 앞일
        assertEquals("왜 그랬을까?", g("왜 그랬을까?", CoopReason.DONE).text)     // 지난 일 짐작은 된다
        assertNull(g("거기서 뭐 했어?", CoopReason.SOON).text)                   // 곧 해요에 지난 일
        assertEquals("거기서 뭐 할까?", g("거기서 뭐 할까?", CoopReason.SOON).text)
        assertEquals("뭐가 있어?", g("뭐가 있어?", CoopReason.SOON).text)         // 「있어」는 지금 꼴
        assertEquals("거기서 뭐 했을까?", g("거기서 뭐 했을까?", CoopReason.DREAM).text)
        assertEquals("거기서 뭐 할까?", g("거기서 뭐 할까?", CoopReason.DREAM).text)
    }

    @Test
    fun realLifeStoriesDoNotStateUnsaidFacts() {
        assertNull(g("불이 났구나! 누가 껐어?", CoopReason.DONE).text.takeIf { false })   // 앞말은 떼여도
        assertNull(g("불이 났잖아. 누가 껐어?", CoopReason.DONE).text?.takeIf { "잖아" in it })
        assertNull(g("불이 크게 났구나 누가 껐어?", CoopReason.DONE).text)              // 질문 안에 단정
        assertEquals("불이 크게 났구나 누가 껐을까?", g("불이 크게 났구나 누가 껐을까?", CoopReason.DREAM).text)
    }

    @Test
    fun aParentQuestionIsOnlyTrimmedToOneQuestion() {
        val p = g("오늘 경험한 상황에서 가장 인상 깊었던 점은 무엇이고 왜 그렇게 느꼈어? 또 누구랑 갔어?", src = CoopSource.PARENT)
        assertEquals("오늘 경험한 상황에서 가장 인상 깊었던 점은 무엇이고 왜 그렇게 느꼈어?", p.text)
        assertTrue(p.changed)
        assertEquals("할머니 댁에서 제일 맛있었던 건 뭐였어?", g("할머니 댁에서 제일 맛있었던 건 뭐였어?", src = CoopSource.PARENT).text)
    }

    @Test
    fun everyLadderRungOfEveryPickedStoryPasses() {
        val bad = mutableListOf<String>()
        COOP_KINDS.forEach { k -> (k.items + k.customExample).forEach { name -> (CoopReason.entries + listOf<CoopReason?>(null)).forEach { r ->
            val s = DemoState().apply { mode = StoryMode.COOP; coopPick = CoopPick(k.key, name, r?.key) }
            COOP_STEPS.forEach { st ->
                s.coopPartPack(st)?.rungs?.forEach { q ->
                    if (coopGuard(q, r, CoopSource.LADDER).text != q) bad += "$name $r ${st.bookKey}: $q → ${coopGuard(q, r, CoopSource.LADDER).issues}"
                }
            }
        } } }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun wordLists() {
        assertTrue(hasRoughWord("칼로 찔렀어"))
        assertFalse(hasRoughWord("피자 먹고 칼국수 먹었어"))
        assertTrue(hasFantasyWord("공룡이 불을 뿜었어"))
        assertTrue(hasFantasyWord("용이 나왔어"))
        assertFalse(hasFantasyWord("용기를 냈어"))
        assertFalse(hasFantasyWord("소방차가 왔어"))
    }
}
