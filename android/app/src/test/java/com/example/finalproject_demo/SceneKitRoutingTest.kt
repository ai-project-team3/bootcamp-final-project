package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.demo.syncStoryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #222 (10-06) — the eight kits on the stage. Device-checked: every place below reached its kit and no `/image`
 * background was asked for (`실기기_확인_1006/키트/`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SceneKitRoutingTest {
    private fun kitFor(place: String) = DemoState().apply {
        mode = StoryMode.STORY; slots["place"] = place
    }.also { it.syncStoryPresentation() }.sceneKit

    /** The three app themes go to their kits too — before, the theme check ran first and they never reached one */
    @Test
    fun theThreeThemePlacesReachTheirKits() {
        assertEquals("dino", kitFor("공룡 나라"))
        assertEquals("space", kitFor("우주"))
        assertEquals("sea", kitFor("바닷속"))
    }

    @Test
    fun theOtherFiveKitsAsOnThePhone() {
        assertEquals("park", kitFor("공원"))
        assertEquals("indoor", kitFor("우리 집"))
        assertEquals("forest", kitFor("할머니 집"))
        assertEquals("beach", kitFor("바닷가"))
        assertEquals("snow", kitFor("눈 나라"))
        assertNull("미래 도시 is still generated", kitFor("과학이 발전한 미래 도시"))
    }

    /** 「산」 as a word only — 「우산」 · 「산타」 went to the forest (d3 · 10-06) */
    @Test
    fun mountainIsAWordNotASyllable() {
        listOf("산에 갔어", "뒷산", "등산", "높은 산 꼭대기", "산속 오두막").forEach { assertEquals(it, "forest", SceneKits.matching(it)?.key) }
        assertNull(SceneKits.matching("우산 나라"))
        assertNull(SceneKits.matching("산타 마을"))
        assertEquals("a walk (산책) is the park", "park", SceneKits.matching("산책길")?.key)
    }

    /** 「방」 as a word only — 10-07 device: 「소방서 차고」 drew the indoor kit (bed, bookshelf) */
    @Test
    fun roomIsAWordNotASyllable() {
        listOf("내 방", "방에서 놀았어", "공부방", "놀이방 안", "우리 방").forEach { assertEquals(it, "indoor", SceneKits.matching(it)?.key) }
        listOf("소방서 차고에 갔어", "방송국", "방학 캠프").forEach { assertNull(it, SceneKits.matching(it)) }
    }

    /** Indoors the sky is a wall — no hills there; every other kit keeps them */
    @Test
    fun onlyIndoorDropsTheHills() {
        SceneKits.all.values.forEach { k -> assertEquals(k.key, k.key != "indoor", k.hills) }
    }

    /** The snow sky is blue-grey, darker than the snow ground, so white flakes show */
    @Test
    fun theSnowSkyIsDarkerThanTheSnow() {
        val snow = SceneKits.all.getValue("snow")
        fun lum(c: Long) = ((c shr 16) and 0xFF) * 0.3 + ((c shr 8) and 0xFF) * 0.59 + (c and 0xFF) * 0.11
        assertTrue(lum(snow.skyTop) < lum(snow.ground) - 40)
        assertFalse(lum(snow.skyBottom) >= lum(snow.ground))
    }
}
