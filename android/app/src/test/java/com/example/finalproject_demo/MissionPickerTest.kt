package com.example.finalproject_demo

import com.example.finalproject_demo.demo.storyPagePlan
import com.example.finalproject_demo.demo.storyMissionResult
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopPageMission
import com.example.finalproject_demo.demo.missionFor
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.StoryFacts
import com.example.finalproject_demo.demo.missions.pickMissions
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.m2Done
import com.example.finalproject_demo.demo.m2Before
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.missions.slot2Prop
import com.example.finalproject_demo.demo.missions.FixProp
import com.example.finalproject_demo.demo.missions.SERVER_KNOWS_C3
import com.example.finalproject_demo.demo.missions.soundPropIn
import com.example.finalproject_demo.demo.missions.SoundProp
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
            coop("케이크에 촛불이 켜져 있었어") to BlowProp.CANDLE,
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

    /**
     * #105 리뷰 — 부분 일치로 촛불 · 바람이 생기던 말들. 실제 일에 아이가 말하지 않은 촛불이 책에 들어갔다(설계 §3-8).
     * 모두 C1 이 아니라 A6 로 남는다
     */
    @Test
    fun wordsThatOnlyContainABlowingWordStayRubbing() {
        listOf("식초를 쏟았어", "풍선을 불었어", "비눗방울 불었어", "나팔 불었어", "케이크 먹었어", "생일이었어", "초가 녹았어").forEach { said ->
            assertEquals(said, MissionId.A6, pickMissions(coop(said)).slot1)
            assertNull(said, blowPropIn(said, realDay = true))
        }
    }

    @Test
    fun withoutBlowingWordsOrInTheDiarySlot1StillRubs() {
        assertEquals(MissionId.A6, pickMissions(coop("블록이 무너졌어")).slot1)
        assertEquals(MissionId.A6, pickMissions(coop(null)).slot1)
        // 그림일기는 자기 책(퍼즐 쪽)이라 말 신호를 보지 않는다
        assertEquals(MissionId.A6, pickMissions(StoryFacts(StoryMode.DIARY, null, "생일 촛불", null, null, true)).slot1)
    }

    /** 10-06 조장: 협업에만 있던 미션을 동화도 쓴다 — 같은 말 신호 */
    @Test
    fun aStoryPicksTheCoopMissionsFromTheChildsWords() {
        fun story(problem: String?, solution: String? = null) = StoryFacts(StoryMode.STORY, "C", problem, null, solution, false)
        assertEquals(MissionId.C1, pickMissions(story("생일 촛불")).slot1)
        assertEquals(MissionId.A1, pickMissions(story("숲에 불이 났어", "불을 껐어")).slot2)
        assertEquals(MissionId.A6, pickMissions(story("바위가 굴러왔어")).slot1)
        assertEquals(MissionId.E1, pickMissions(story("바위가 굴러왔어")).slot2)
    }

    /** 동화 책 쪽 계획 · 결과 문장도 그 미션의 물건으로 */
    @Test
    fun aStoryBookCarriesTheCoopMissionAndItsLines() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            templateKey = "C"
            problem = "생일 촛불이 너무 많았어"
        }
        val rub = s.storyPagePlan().first { it.kind == "RUB" }
        assertEquals("C1", rub.mission)
        assertEquals("촛불", rub.prop)
        s.storyCaptions = List(s.template!!.pages.size) { "쪽" }
        val i = s.template!!.pages.indexOfFirst { it.kind == PageKind.RUB } + 1
        assertNull(s.storyMissionResult(i))
        s.m1Result = "solo"
        assertEquals("촛불이 다 꺼졌어요.", s.storyMissionResult(i))
    }

    /** 협업 책 쪽 계획도 C1 을 서버에 보낸다(서버 `MissionId` 에 있다) · 책 문장은 미션 전후로 */
    @Test
    fun aCoopBookWithCandlesCarriesC1AndItsLines() {
        val s = DemoState().apply {
            mode = StoryMode.COOP
            problem = "생일 촛불이 너무 많았어"
            placeLabel = "우리집"
            slotBy["problem"] = "child"                                    // 아이가 한 말 — 실제 하루 책은 아이가 이름을 말한 물건만(#134)
        }
        assertEquals("C1", s.coopPageMission(PageKind.RUB))
        assertEquals(BlowProp.CANDLE, s.blowProp())
        assertEquals("촛불을 후~ 불어서 꺼 볼래?", s.m1Line())
        assertEquals("우리집에 촛불이 활활 타고 있어요.", s.m1Before())
        s.m1Result = "solo"
        assertEquals("촛불이 다 꺼졌어요.", s.coopMissionResult(PageKind.RUB))
        assertEquals("후~ 촛불이 다 꺼졌어!", s.m1Done())
    }

    // ── 1-b C3 소리 흉내 (#101) ─────────────────────────────────────

    @Test
    fun soundWordsFindTheirProp() {
        mapOf(
            "소방차가 삐뽀삐뽀 왔어" to SoundProp.SIREN,
            "자동차가 부릉부릉" to SoundProp.CAR,
            "기차를 탔어" to SoundProp.TRAIN,
            "사자가 어흥 했어" to SoundProp.LION,
            "강아지가 멍멍 짖었어" to SoundProp.DOG,
            "내가 슛 했어" to SoundProp.CHEER,
        ).forEach { (said, prop) -> assertEquals(said, prop, soundPropIn(said)) }
        assertNull(soundPropIn("블록이 무너졌어"))
    }

    /** 서버 `MissionId` 에 C3 가 들어갔다(main `0dfa664`) — 이제 소리 낱말이면 C3 를 고른다 */
    @Test
    fun c3IsPickedNowThatTheServerKnowsIt() {
        assertTrue(SERVER_KNOWS_C3)
        assertEquals(MissionId.C3, pickMissions(coop("소방차가 삐뽀삐뽀 왔어")).slot1)
        assertEquals("불기 낱말이 같이 있으면 C1 이 먼저", MissionId.C1, pickMissions(coop("소방차가 와서 촛불을 껐어")).slot1)
        val s = DemoState().apply { mode = StoryMode.COOP; problem = "강아지가 멍멍 짖었어" }
        assertEquals("C3", s.coopPageMission(PageKind.RUB))
        assertEquals("강아지처럼 「멍멍!」 소리 내 볼래?", s.m1Line())
    }

    // ── 1-b A1 물대포 · A4 돌려 잠그기 (#101) — 자리 2 ─────────────────

    private fun coop2(problem: String?, solution: String?, tried: String? = null) =
        StoryFacts(StoryMode.COOP, null, problem, cause = null, solution = solution, realDay = true, tried = tried)

    /** 설계 §6-2 표 그대로 — 해결 동사가 문제 칸의 사물보다 먼저 */
    @Test
    fun solutionVerbsPickTheSlot2Mission() {
        val table = listOf(
            coop2("생일 촛불이 너무 많았어", "물을 부어서 껐어") to (MissionId.C1 to MissionId.A1),
            coop2("수도꼭지에서 물이 샜어", "꽉 잠갔어") to (MissionId.A6 to MissionId.A4),
            coop2("불이 났어", "다 같이 도망갔어") to (MissionId.A6 to MissionId.A1),           // 해결에 동사가 없으면 문제 칸의 사물
            coop2("물이 넘쳤어", null, tried = "수건으로 닦았어") to (MissionId.A6 to MissionId.A4),
            coop2("블록이 무너졌어", "다시 쌓았어") to (MissionId.A6 to MissionId.A5),
            coop2(null, null) to (MissionId.A6 to MissionId.E1),
            coop2("친구가 밀었어", "같이 미끄럼틀을 탔어") to (MissionId.A6 to MissionId.E1),   // 「미끄럼틀」의 「끄」는 불 끄기가 아니다
        )
        table.forEach { (f, want) ->
            val got = pickMissions(f)
            assertEquals("${f.slot1Words} / ${f.slot2Words}", want, got.slot1 to got.slot2)
            assertTrue(got.slot1 != got.slot2)
        }
        // 동화도 같은 말 신호로 고른다 (10-06 조장)
        assertEquals(MissionId.A1, pickMissions(StoryFacts(StoryMode.STORY, null, "불이 났어", null, "물을 뿌려 껐어", false)).slot2)
    }

    @Test
    fun aCoopBookThatTurnedTheTapCarriesA4AndItsLines() {
        val s = DemoState().apply {
            mode = StoryMode.COOP
            problem = "수도꼭지에서 물이 샜어"
            solution = "꽉 잠갔어"
            slotBy["problem"] = "child"; slotBy["solution"] = "child"     // 아이가 한 말 — 실제 하루 책은 아이가 이름을 말한 물건만(#134)
        }
        assertEquals("A4", s.coopPageMission(PageKind.DRAG))
        assertEquals(FixProp.FAUCET, s.slot2Prop())
        assertEquals("수도꼭지를 빙글빙글 돌려서 잠가 볼래?", s.m2Line(false))
        assertEquals("수도꼭지에서 물이 졸졸 새고 있어요.", s.m2Before())
        s.m2Result = "solo"
        assertEquals("물이 딱 멈췄어요.", s.coopMissionResult(PageKind.DRAG))
        assertEquals("꽉! 물이 딱 멈췄어!", s.m2Done())
    }

    // ── 1-b D4 기울여 굴리기 (#101) ───────────────────────────────────

    @Test
    fun rollingWordsPickD4() {
        mapOf(
            coop2("친구랑 축구했어", "공을 데굴데굴 굴렸어") to MissionId.D4,
            coop2("축구 시합을 했어", "내가 골인 시켰어") to MissionId.D4,
            coop2("공이 굴러가서 없어졌어", "다 같이 찾았어") to MissionId.D4,      // 해결에 동사가 없으면 문제 칸
            coop2("공이 굴러갔어", "물을 뿌려서 껐어") to MissionId.A1,               // 해결 동사가 먼저
        ).forEach { (f, want) -> assertEquals("${f.slot1Words} / ${f.slot2Words}", want, pickMissions(f).slot2) }
    }

    // ── 1-b E2 고쳐 주기 · A5 쌓기 (#101) ─────────────────────────────

    @Test
    fun fixingAndStackingWordsPickE2AndA5() {
        mapOf(
            coop2("장난감이 부서졌어", "아빠가 고쳐 줬어") to MissionId.E2,
            coop2("로봇이 망가졌어", "같이 찾아봤어") to MissionId.E2,                 // 해결에 동사가 없으면 문제 칸
            coop2("탑이 와르르 무너졌어", null) to MissionId.A5,
            coop2("친구가 블록을 쳤어", "다시 쌓았어") to MissionId.A5,
            coop2("장난감이 부서졌어", "다시 쌓았어") to MissionId.A5,                 // 해결 동사가 먼저
        ).forEach { (f, want) -> assertEquals("${f.slot1Words} / ${f.slot2Words}", want, pickMissions(f).slot2) }
    }

    /** 10-05 실기기 — 자리 2 해결 미션 쪽 문장이 「물을 뿌려서 껐어. 그리고 불이 활활 타오르고 있어요.」로 앞뒤가 어긋났다 */
    @Test
    fun theSlot2PageSaysOnlyBeforeThenWhatTheChildDid() {
        val s = DemoState().apply {
            mode = StoryMode.COOP
            problem = "부엌에서 불이 났어"; slots["problem"] = problem!!
            solution = "물을 뿌려서 껐어"; slots["solution"] = solution!!
            slotBy["solution"] = "child"
        }
        val drag = (1..s.pageCount).first { s.pageKind(it) == PageKind.DRAG }
        assertEquals("불이 활활 타오르고 있어요.", s.bookCaption(drag))
        s.m2Result = "solo"
        assertEquals("${s.childName}${if (com.example.finalproject_demo.demo.bat(s.childName)) "은" else "는"} 물대포로 치익~ 불을 다 껐어요. 불이 다 꺼졌어요.", s.bookCaption(drag))
    }
}
