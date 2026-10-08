package com.example.finalproject_demo

import com.example.finalproject_demo.ui.shell.TERMS_CHANGES
import com.example.finalproject_demo.ui.shell.TERMS_VERSION
import com.example.finalproject_demo.ui.shell.termsChangesSince
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 약관 판 규칙 (#256 · 10-07 조장 결정) — 판을 올리면 [TERMS_CHANGES] 에 그 판의 줄을 같이 적는다.
 * 안 적으면 다시 동의 화면이 「무엇이 바뀌었는지」 말하지 못하니 여기서 막는다.
 */
class TermsTest {
    @Test
    fun theCurrentVersionSaysWhatChanged() {
        val now = TERMS_CHANGES.firstOrNull { it.first == TERMS_VERSION }
        assertTrue("TERMS_VERSION $TERMS_VERSION 의 줄이 TERMS_CHANGES 에 없다", now != null && now.second.isNotEmpty())
        assertEquals("지금 판이 표의 마지막이 아니다", TERMS_VERSION, TERMS_CHANGES.last().first)
    }

    @Test
    fun versionsLookLikeDatesAndGoForward() {
        val form = Regex("""\d{4}-\d{2}-\d{2}(\.\d+)?""")
        TERMS_CHANGES.forEach { (v, _) -> assertTrue("판 모양이 아니다: $v", form.matches(v)) }
        assertEquals("판이 겹친다", TERMS_CHANGES.size, TERMS_CHANGES.map { it.first }.toSet().size)
        fun key(v: String) = v.substringBefore('.') to (v.substringAfter('.', "0").toInt())
        TERMS_CHANGES.zipWithNext().forEach { (a, b) ->
            val (ad, an) = key(a.first); val (bd, bn) = key(b.first)
            assertTrue("${a.first} 뒤에 ${b.first} — 오래된 판부터 적는다", ad < bd || (ad == bd && an < bn))
        }
    }

    @Test
    fun onlyTheLinesAfterTheAgreedVersion() {
        assertEquals(TERMS_CHANGES.last().second, termsChangesSince(TERMS_CHANGES[TERMS_CHANGES.size - 2].first))
        assertEquals(TERMS_CHANGES.drop(1).flatMap { it.second }, termsChangesSince(TERMS_CHANGES.first().first))
        // 표보다 오래된 동의 · 모르는 판 — 표의 줄 전부
        assertEquals(TERMS_CHANGES.flatMap { it.second }, termsChangesSince(null))
        assertEquals(TERMS_CHANGES.flatMap { it.second }, termsChangesSince("2026-09-01"))
        // 지금 판에 동의했으면 바뀐 게 없지만 빈 화면 대신 한 줄
        assertEquals(listOf("약관 내용이 바뀌었어요"), termsChangesSince(TERMS_VERSION))
    }
}
