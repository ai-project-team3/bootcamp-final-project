package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPageMission
import com.example.finalproject_demo.demo.missionFor
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.StoryFacts
import com.example.finalproject_demo.demo.missions.pickMissions
import com.example.finalproject_demo.demo.coopMissionResult
import com.example.finalproject_demo.demo.m1Done
import com.example.finalproject_demo.demo.m1Before
import com.example.finalproject_demo.demo.m1Line
import com.example.finalproject_demo.demo.missions.blowProp
import com.example.finalproject_demo.demo.missions.blowPropIn
import com.example.finalproject_demo.demo.missions.BlowProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10-03 · 맞춤미션 설계 §6-2: the picker is checked as a table, story → missions.
 * Stage 1-a keeps today's behaviour, so the table is today's rule; new rows come with new screens.
 */
class MissionPickerTest {

    private fun facts(template: String?, mode: StoryMode = StoryMode.STORY) =
        StoryFacts(mode, template, problem = null, cause = null, solution = null, realDay = false)

    @Test
    fun todaysRuleAsATable() {
        val table = mapOf(
            "A" to (MissionId.A6 to MissionId.A3),   // 도전-성취: put it back together
            "G" to (MissionId.A6 to MissionId.A3),   // 우화-교훈
            "B" to (MissionId.A6 to MissionId.E1),
            "C" to (MissionId.A6 to MissionId.E1),
            null to (MissionId.A6 to MissionId.E1),  // no frame yet: the defaults
        )
        table.forEach { (frame, want) ->
            val got = pickMissions(facts(frame))
            assertEquals("frame $frame", want, got.slot1 to got.slot2)
        }
    }

    @Test
    fun onlyBuiltMissionsInTheirOwnSlotAndNeverTheSameTwice() {
        listOf("A", "B", "C", "D", "E", "F", "G", null).forEach { frame ->
            val m = pickMissions(facts(frame))
            assertTrue(m.slot1.built && m.slot2.built)
            assertTrue(1 in m.slot1.slots && 2 in m.slot2.slots)
            assertTrue(m.slot1 != m.slot2)
        }
    }

    /** The server page plan, co-op's page plan and the book screen used to copy the rule; now they ask the picker */
    @Test
    fun storyAndCoopPagesCarryThePickedIds() {
        val s = DemoState().apply { templateKey = "G" }
        assertEquals(MissionId.A6, s.missionFor(PageKind.RUB))
        assertEquals(MissionId.A3, s.missionFor(PageKind.DRAG))
        assertNull(s.missionFor(PageKind.MEET))
        s.mode = StoryMode.entries.first { it != StoryMode.STORY && it.usesDiaryQuestions }
        s.templateKey = "B"
        assertEquals("A6", s.coopPageMission(PageKind.RUB))
        assertEquals("E1", s.coopPageMission(PageKind.DRAG))
    }

    // ── 1-b C1 불기 (#101) ──────────────────────────────────────────

    private fun coop(problem: String?, cause: String? = null, detail: String? = null, realDay: Boolean = true) =
        StoryFacts(StoryMode.COOP, null, problem, cause, solution = null, realDay = realDay, detail = detail)

    /** 설계 §6-2 표 — 아이 말에 불 것이 있으면 자리 1 은 C1, 소품은 그 말에서 */
    @Test
    fun blowingWordsPickC1InCoop() {
        val table = listOf(
            coop("생일 촛불이 너무 많았어") to BlowProp.CANDLE,
            coop("케이크에 불이 켜져 있었어") to BlowProp.CANDLE,
            coop("먼지가 쌓였어") to BlowProp.DUST,
            coop(null, detail = "민들레가 잔뜩 있었어") to BlowProp.DANDELION,
            coop("바람이 많이 불었어", realDay = false) to BlowProp.LEAF,     // 상상 — 나뭇잎을 빌려 쓴다
            coop("바람이 많이 불었어", realDay = true) to BlowProp.DUST,      // 실제 일 — 말하지 않은 나뭇잎은 넣지 않는다 (§3-8)
        )
        table.forEach { (f, prop) ->
            assertEquals(f.slot1Words, MissionId.C1, pickMissions(f).slot1)
            assertEquals(f.slot1Words, prop, blowPropIn(f.slot1Words, f.realDay))
        }
    }

    @Test
    fun withoutBlowingWordsOrOutsideCoopSlot1StillRubs() {
        assertEquals(MissionId.A6, pickMissions(coop("블록이 무너졌어")).slot1)
        assertEquals(MissionId.A6, pickMissions(coop(null)).slot1)
        // 동화는 협업 미션이 몇 개 생긴 뒤에 따라온다 (설계 §7-3)
        assertEquals(MissionId.A6, pickMissions(StoryFacts(StoryMode.STORY, null, "생일 촛불", null, null, false)).slot1)
    }

    /** 협업 책 쪽 계획도 C1 을 서버에 보낸다(서버 `MissionId` 에 있다) · 책 문장은 미션 전후로 */
    @Test
    fun aCoopBookWithCandlesCarriesC1AndItsLines() {
        val s = DemoState().apply {
            mode = StoryMode.COOP
            problem = "생일 촛불이 너무 많았어"
            placeLabel = "우리집"
        }
        assertEquals("C1", s.coopPageMission(PageKind.RUB))
        assertEquals(BlowProp.CANDLE, s.blowProp())
        assertEquals("촛불을 후~ 불어서 꺼 볼래?", s.m1Line())
        assertEquals("우리집에 촛불이 활활 타고 있어요.", s.m1Before())
        s.m1Result = "solo"
        assertEquals("촛불이 다 꺼졌어요.", s.coopMissionResult(PageKind.RUB))
        assertEquals("후~ 촛불이 다 꺼졌어!", s.m1Done())
    }
}
