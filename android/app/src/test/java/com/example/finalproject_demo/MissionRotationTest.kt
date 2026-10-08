package com.example.finalproject_demo

import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.captureStoryVisuals
import com.example.finalproject_demo.demo.m1Badge
import com.example.finalproject_demo.demo.m2Badge
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.BookMissions
import com.example.finalproject_demo.demo.missions.FixProp
import com.example.finalproject_demo.demo.missions.MissionHistory
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.StoryFacts
import com.example.finalproject_demo.demo.missions.blowPropIn
import com.example.finalproject_demo.demo.missions.fixPropIn
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.demo.missions.pickMissions
import com.example.finalproject_demo.demo.missions.slot2PlayProp
import com.example.finalproject_demo.demo.missions.slot2Prop
import com.example.finalproject_demo.demo.missions.soundPropIn
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.storyPagePlan
import com.example.finalproject_demo.demo.storyVisualsFromJson
import com.example.finalproject_demo.demo.toJson
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #259 (device 10-07) — co-op and story books always had 「먼지 문지르기 · 별 건네기」. Lead decision (10-07): app only —
 * match words per eojeol · widen the tables · rotate when nothing fits · avoid the previous two books' combo · do not treat default props as the child's words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionRotationTest {
    @Before fun attach() { MissionHistory.attach(ApplicationProvider.getApplicationContext()); MissionHistory.detach()
        MissionHistory.attach(ApplicationProvider.getApplicationContext()) }

    @After fun detach() = MissionHistory.detach()

    private fun story(problem: String?, solution: String? = null, cause: String? = null) =
        StoryFacts(StoryMode.STORY, "C", problem, cause, solution, realDay = false)

    private fun coopDay(problem: String?, solution: String? = null) =
        StoryFacts(StoryMode.COOP, null, problem, null, solution, realDay = true)

    /** Words match only at the start of an eojeol — design §4-1's three false positives and mid-word matches are gone */
    @Test
    fun wordsMatchAtTheStartOfAnEojeolOnly() {
        // must not match
        assertNull("까닭의 「바람에」", blowPropIn("놀다가 넘어지는 바람에 무릎을 다쳤어", realDay = false))
        assertNull("까닭의 「바람에」 · ㄴ 받침", blowPropIn("서두른 바람에 떨어뜨렸어", realDay = false))
        assertNull("「식초를」의 「초」", blowPropIn("식초를 쏟았어", realDay = false))
        assertNull("「흙먼지」", blowPropIn("흙먼지가 많았어", realDay = true))
        assertNull("마음이 「넘쳤어」", fixPropIn("", "기쁨이 넘쳤어"))
        assertNull("연극 「연기」", fixPropIn("", "학예회에서 연기를 했어"))
        assertNull("「미끄럼틀」의 「끄」", fixPropIn("미끄럼틀을 탔어", ""))
        assertNull("「끄덕였어」", fixPropIn("기린이 고개를 끄덕였어", ""))
        // must match — with particles · endings attached
        assertEquals(BlowProp.LEAF, blowPropIn("바람에 모자가 날아갔어", realDay = false))
        assertEquals(BlowProp.CANDLE, blowPropIn("생일 촛불을 켰어", realDay = true))
        assertEquals(BlowProp.DANDELION, blowPropIn("홀씨가 날아갔어", realDay = true))
        assertEquals(FixProp.FIRE, fixPropIn("", "연기가 났어"))
        assertEquals(FixProp.FIRE, fixPropIn("물을 뿌려서 껐어", ""))
        assertEquals(FixProp.FAUCET, fixPropIn("", "물이 넘쳤어"))
    }

    /** Words added to the tables — one sentence each (events with no mission, 「나타났어 · 길을 잃었어」, were not added) */
    @Test
    fun theWiderTableHasASentenceForEachNewWord() {
        listOf(
            "트럭이 지나갔어" to "CAR", "택시를 탔어" to "CAR", "오토바이 소리가 났어" to "CAR", "지하철을 탔어" to "TRAIN",
        ).forEach { (t, want) -> assertEquals(t, want, soundPropIn(t)?.name) }
        listOf(
            ("공을 찼어" to "") to FixProp.BALL, ("친구랑 축구했어" to "") to FixProp.BALL, ("공놀이를 했어" to "") to FixProp.BALL,
            ("구멍 난 곳을 꿰맸어" to "") to FixProp.PIECES,
            ("" to "동생이 탑을 넘어뜨렸어") to FixProp.BLOCKS, ("" to "연필이 부러졌어") to FixProp.PIECES,
            ("" to "컵이 깨져 버렸어") to FixProp.PIECES, ("" to "공이 날아가 버렸어") to FixProp.BALL,
            ("" to "물이 졸졸 흘렀어") to FixProp.FAUCET,
        ).forEach { (t, want) -> assertEquals(t.toString(), want, fixPropIn(t.first, t.second)) }
        // Slot 2 is picked by the solution **verb** — 「블록 놀이도 했어」 is not stacking but a day to give the blocks the child named
        assertNull(fixPropIn("같이 미끄럼틀을 타고 블록 놀이도 했어", ""))
    }

    /** The issue's four stories — made in turn, none repeats the combo of the previous two books */
    @Test
    fun fourStoriesInARowNeverRepeatTheLastTwo() {
        val books = listOf(
            "공룡" to story("공원에서 갑자기 공룡이 나타났어", "공룡이랑 친구가 됐어"),
            "개발자" to coopDay("컴퓨터가 멈췄어", "다시 켰어"),
            "소방관" to coopDay("소방서에서 불이 났어", "물을 뿌려서 껐어"),
            "생일" to story("생일 케이크에 촛불을 켰어", "다 같이 노래를 불렀어"),
        )
        val made = mutableMapOf<StoryMode, MutableList<BookMissions>>()
        books.forEachIndexed { i, (name, f) ->
            val m = pickMissions(f, MissionHistory.recent(f.mode))
            val before = made[f.mode].orEmpty()
            assertFalse("$name ${m.combo} 가 앞 두 권과 같다 ${before.map { it.combo }}", m.combo in before.takeLast(2).map { it.combo })
            MissionHistory.record(f.mode, "b$i", m)
            made.getOrPut(f.mode) { mutableListOf() } += m
        }
        assertEquals("소방관은 아이 말대로 물대포", MissionId.A1, made[StoryMode.COOP]!![1].slot2)
        assertEquals("생일은 아이 말대로 불기", MissionId.C1, made[StoryMode.STORY]!![1].slot1)
    }

    /** The same slots twice in a row — the second is a different combo, the third differs from the previous two */
    @Test
    fun theSameStoryTwiceGetsADifferentPairEachTime() {
        for (f in listOf(story("공룡이 나타났어"), coopDay("개발자 일을 해 봤어"))) {
            val seen = mutableListOf<String>()
            repeat(5) { n ->
                val m = pickMissions(f, MissionHistory.recent(f.mode))
                assertFalse("${f.mode} ${n + 1}번째 ${m.combo} · 앞 $seen", m.combo in seen.takeLast(2))
                MissionHistory.record(f.mode, "${f.mode}$n", m)
                seen += m.combo
            }
            assertTrue("${f.mode}: 셋 이상 돌았다 $seen", seen.toSet().size >= 3)
        }
    }

    /** Same input → same missions (with the same history) */
    @Test
    fun theSameFactsAndHistoryGiveTheSameMissions() {
        val f = story("공룡이 나타났어")
        val h = listOf("A6:E1", "C1:E1")
        assertEquals(pickMissions(f, h), pickMissions(f, h))
        assertEquals(pickMissions(f, h), pickMissions(f.copy(), h.toList()))
    }

    /** A slot the child's words picked does not rotate — the child's words come first even if the last book had it */
    @Test
    fun theChildsWordsWinOverRotation() {
        val fire = coopDay("불이 났어", "물을 뿌려서 껐어")
        val m = pickMissions(fire, listOf("A6:A1", "A6:A1"))
        assertEquals(MissionId.A1, m.slot2)
        assertTrue(m.slot2FromChild)
        val candle = story("촛불이 켜져 있었어", "끄고 노래했어")
        assertEquals(MissionId.C1, pickMissions(candle, listOf("C1:E1", "C1:A5")).slot1)
    }

    /** A real day borrows no prop the child did not say — no rotation to blowing (leaves), and a rotated slot 2 is not in the book's sentences */
    @Test
    fun aRealDayRotatesOnlyWhatItCanPlayWithoutInventing() {
        val f = coopDay("동물원에 갔어", "기린을 봤어")
        val combos = (0 until 6).map { n -> pickMissions(f, MissionHistory.recent(f.mode)).also { MissionHistory.record(f.mode, "d$n", it) } }
        assertTrue("실제 하루는 자리 1 이 늘 문지르기", combos.all { it.slot1 == MissionId.A6 })
        assertTrue(combos.map { it.slot2 }.toSet().size >= 3)

        MissionHistory.detach(); MissionHistory.attach(ApplicationProvider.getApplicationContext())
        MissionHistory.record(StoryMode.COOP, "e1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        val s = DemoState().apply { mode = StoryMode.COOP; problem = "동물원에 갔어"; solution = "기린을 봤어" }
        assertEquals(MissionId.A5, s.missions().slot2)
        assertNull("책 문장에는 없다 — 아이가 블록을 말하지 않았다", s.slot2Prop())
        assertEquals("화면 · 안내에는 있다", FixProp.BLOCKS, s.slot2PlayProp())
    }

    /** An imagined story borrows the rotated mission's prop and its sentences match (design §7-2) */
    @Test
    fun anImaginedStoryBorrowsTheRotatedProp() {
        MissionHistory.record(StoryMode.STORY, "x1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        MissionHistory.record(StoryMode.STORY, "x2", BookMissions(MissionId.C1, MissionId.E1, false, false))
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어" }
        val m = s.missions()
        assertEquals(MissionId.A5, m.slot2)
        assertFalse(m.slot2FromChild)
        assertEquals(FixProp.BLOCKS, s.slot2Prop())
        assertEquals("블록", s.storyPagePlan().first { it.kind == PageKind.DRAG.name }.prop)
    }

    /** Default props (dust · star) are not sent to the server and the badge does not name them */
    @Test
    fun defaultPropsAreNotSentOrNamed() {
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어"; newcomerKind = "고양이" }
        val base = Server.base; val modes = Server.liveModes
        Server.base = "http://localhost:1"; Server.liveModes = setOf(StoryMode.STORY)   // live story — an unknown guest gives dust
        try {
            val plan = s.storyPagePlan()
            assertNull("기본 먼지", plan.first { it.kind == PageKind.RUB.name }.prop)
            assertNull("기본 별", plan.first { it.kind == PageKind.DRAG.name }.prop)
            assertEquals("깨끗하게 치운 손", s.m1Badge())
            assertEquals("마음을 건넨 손", s.m2Badge())
        } finally {
            Server.base = base; Server.liveModes = modes
        }
    }

    /** A re-read uses the missions saved in the book — even if the history changed since */
    @Test
    fun aSavedBookIsReReadWithTheSameMissions() {
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어" }
        val made = s.missions()
        val visuals = s.captureStoryVisuals()
        assertEquals(made, com.example.finalproject_demo.demo.missions.PinnedMissions.decode(visuals.missions)?.missions)
        MissionHistory.record(StoryMode.STORY, "other1", made)
        MissionHistory.record(StoryMode.STORY, "other2", made)

        val back = storyVisualsFromJson(visuals.toJson())
        val reread = DemoState()
        reread.restoreStoryBook(SavedStoryBook("b", "책", "park", "bg_park", listOf(SavedStoryPage(PageKind.DEPART, "쪽")), back))
        assertEquals(made, reread.missions())
        assertEquals(made, BookMissions.decode(made.encode()))
        assertNull("옛 책(미션 없음)", BookMissions.decode(null))
    }

    /** Four history rows per mode · re-adding the same book changes only its row · nothing is recorded when not attached */
    @Test
    fun theHistoryKeepsFourPerModeAndOneLinePerBook() {
        val m = BookMissions(MissionId.A6, MissionId.E1, false, false)
        repeat(6) { MissionHistory.record(StoryMode.COOP, "c$it", m.copy(slot2 = listOf(MissionId.E1, MissionId.A5, MissionId.E2)[it % 3])) }
        assertEquals(4, MissionHistory.recent(StoryMode.COOP).size)
        MissionHistory.record(StoryMode.COOP, "c5", m.copy(slot2 = MissionId.D4))
        assertEquals("A6:D4", MissionHistory.recent(StoryMode.COOP).first())
        assertEquals(4, MissionHistory.recent(StoryMode.COOP).size)
        assertTrue(MissionHistory.recent(StoryMode.STORY).isEmpty())
        MissionHistory.reload()
        assertEquals("폰에 남는다", "A6:D4", MissionHistory.recent(StoryMode.COOP).first())
        MissionHistory.detach()
        MissionHistory.record(StoryMode.COOP, "x", m)
        assertTrue("붙이지 않으면 기록하지 않는다", MissionHistory.recent(StoryMode.COOP).isEmpty())
    }
}
