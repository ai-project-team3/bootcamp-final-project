package com.example.finalproject_demo

import com.example.finalproject_demo.demo.missions.FixProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 10-06 실기기 — 물대포로 불을 껐는데 부모 화면 「받은 선물」에 「별 건넨 손」이 남았다.
 * 아이 말에서 고른 자리 2 미션은 그 미션의 이름으로 남는다.
 */
class CoopMissionBadgeTest {
    @Test
    fun everyFixMissionHasItsOwnBadge() {
        val badges = FixProp.entries.map { it.badge }
        assertEquals("미션마다 다른 이름", badges.size, badges.toSet().size)
        badges.forEach { assertFalse("「$it」", it.isBlank() || "건넨" in it || "별" in it) }
        assertEquals("불 끈 물대포", FixProp.FIRE.badge)
    }
}
