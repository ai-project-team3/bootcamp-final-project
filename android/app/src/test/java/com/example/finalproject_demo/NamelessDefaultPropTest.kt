package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.m1Badge
import com.example.finalproject_demo.demo.m1Done
import com.example.finalproject_demo.demo.m1Line
import com.example.finalproject_demo.demo.m2Badge
import com.example.finalproject_demo.demo.m2Done
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.mission2
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #259 설계 §4-3 4 — 아이 말에서 아무것도 못 찾았을 때의 소품은 이름 없는 그림으로(반짝이 가루 · 작은 선물 상자).
 * 안내 · 완료 · 배지에 「먼지」 · 「별」을 쓰지 않는다 — 아이가 말한 적이 없다. 대본 동화의 별(아이가 10장면에서 고름)은 그대로.
 */
class NamelessDefaultPropTest {
    private val base = Server.base
    private val modes = Server.liveModes

    @Before fun live() { Server.base = "http://localhost:1"; Server.liveModes = setOf(StoryMode.STORY, StoryMode.COOP) }
    @After fun restore() { Server.base = base; Server.liveModes = modes }

    private fun said(vararg lines: String) = lines.joinToString(" / ")

    @Test
    fun nothingFoundMeansANamelessSparkleAndANamelessGift() {
        val states = listOf(
            "실시간 동화 · 모르는 손님" to DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; newcomerKind = "고양이" },
            "같이 만들기 · 할머니 집" to DemoState().apply { mode = StoryMode.COOP; placeLabel = "할머니 집"; problem = "할머니랑 놀았어" },
        )
        for ((name, s) in states) {
            val m1 = s.mission1(); val m2 = s.mission2()
            assertFalse(name, m1.named)
            assertEquals(name, "prop_sparkle_dust", m1.blob)
            assertEquals(name, "prop_gift", m2.item)
            assertFalse(name, m2.fromChild)
            val lines = said(s.m1Line(), s.m1Done(), s.m1Badge(), s.m2Line(easy = false), s.m2Line(easy = true), s.m2Done(), s.m2Badge())
            assertFalse("$name: $lines", "먼지" in lines || "별" in lines)
            assertTrue("$name: 동작으로 말한다 — ${s.m1Line()}", "문질러" in s.m1Line())
        }
    }

    @Test
    fun whatTheChildSaidKeepsItsName() {
        // 아이가 모래 · 블록을 말했으면 그 이름 그대로
        val s = DemoState().apply { mode = StoryMode.COOP; problem = "모래놀이를 했어"; solutionItem = "block" }
        assertTrue(s.mission1().named)
        assertEquals("모래", s.mission1().blobName)
        assertEquals("블록", s.mission2().itemName)
        assertTrue(s.mission2().fromChild)
        // 대본 동화(서버 없음)의 별은 아이가 10장면에서 고른 「별 따기」
        Server.base = null
        val scripted = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; solutionItem = "star" }
        assertEquals("별", scripted.mission2().itemName)
        assertTrue(scripted.mission2().fromChild)
    }
}
