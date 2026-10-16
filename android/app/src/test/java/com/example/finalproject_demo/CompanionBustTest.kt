package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.WorldStyle
import com.example.finalproject_demo.demo.coopCompanionPreset
import com.example.finalproject_demo.demo.isBust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실기기(10-14 시연 캡처 cp_t5) — 같이 만들기 대화 무대의 아빠가 허리에서 잘린 채 크게 떠서 여자아이 인형과 겹쳤다.
 * 엄마 · 아빠 · 할머니 · 할아버지는 전신 인형이 없어 부모 화면의 상반신 그림(ic_p_)이 섰다.
 * - 상반신 그림은 [isBust] — 무대 · 책이 작게 세운다
 * - 전신 인형(dp_<who>)이 구워지면 그것이 먼저다
 */
class CompanionBustTest {

    @Test
    fun familyPortraitsAreBustsUntilTheDollIsBaked() {
        listOf("아빠", "엄마", "할머니", "할아버지").forEach { assertTrue(it, coopCompanionPreset(it)!!.isBust) }
        // the friend and teacher dolls are full-body
        listOf("동생", "선생님").forEach { assertFalse(it, coopCompanionPreset(it)!!.isBust) }
        assertFalse(Art.Emoji("👨").isBust)
    }

    @Test
    fun aBakedDollComesFirst() {
        val before = WorldStyle.has
        try {
            WorldStyle.has = { it == "dp_dad" }
            val dad = coopCompanionPreset("아빠") as Art.Img
            assertEquals("dp_dad", dad.name)
            assertFalse(dad.isBust)
            assertEquals("ic_p_dad", (dad.fallback as Art.Img).name)
            // 엄마 has no baked doll yet — still the portrait
            assertTrue(coopCompanionPreset("엄마")!!.isBust)
        } finally {
            WorldStyle.has = before
        }
    }
}
