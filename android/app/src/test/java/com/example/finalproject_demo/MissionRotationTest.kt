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
 * #259 (10-07 실기기) — 협업 책도 동화 책도 미션이 늘 「먼지 문지르기 · 별 건네기」였다. 조장 결정(10-07): 앱만 —
 * 어절 기준 낱말 맞추기 · 표 넓히기 · 맞는 게 없으면 돌려 쓰기 · 직전 두 권과 같은 조합 피하기 · 기본 소품을 아이 말처럼 다루지 않기.
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

    /** 낱말은 어절 처음에서만 — 설계 §4-1 의 오탐 셋과 낱말 가운데 걸림이 없어진다 */
    @Test
    fun wordsMatchAtTheStartOfAnEojeolOnly() {
        // 걸리지 않아야 하는 것
        assertNull("까닭의 「바람에」", blowPropIn("놀다가 넘어지는 바람에 무릎을 다쳤어", realDay = false))
        assertNull("까닭의 「바람에」 · ㄴ 받침", blowPropIn("서두른 바람에 떨어뜨렸어", realDay = false))
        assertNull("「식초를」의 「초」", blowPropIn("식초를 쏟았어", realDay = false))
        assertNull("「흙먼지」", blowPropIn("흙먼지가 많았어", realDay = true))
        assertNull("마음이 「넘쳤어」", fixPropIn("", "기쁨이 넘쳤어"))
        assertNull("연극 「연기」", fixPropIn("", "학예회에서 연기를 했어"))
        assertNull("「미끄럼틀」의 「끄」", fixPropIn("미끄럼틀을 탔어", ""))
        assertNull("「끄덕였어」", fixPropIn("기린이 고개를 끄덕였어", ""))
        // 걸려야 하는 것 — 조사 · 어미가 붙어도
        assertEquals(BlowProp.LEAF, blowPropIn("바람에 모자가 날아갔어", realDay = false))
        assertEquals(BlowProp.CANDLE, blowPropIn("생일 촛불을 켰어", realDay = true))
        assertEquals(BlowProp.DANDELION, blowPropIn("홀씨가 날아갔어", realDay = true))
        assertEquals(FixProp.FIRE, fixPropIn("", "연기가 났어"))
        assertEquals(FixProp.FIRE, fixPropIn("물을 뿌려서 껐어", ""))
        assertEquals(FixProp.FAUCET, fixPropIn("", "물이 넘쳤어"))
    }

    /** 표를 넓힌 낱말 — 낱말마다 걸리는 문장 하나 (맞는 미션이 없는 사건 「나타났어 · 길을 잃었어」는 넣지 않았다) */
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
        // 자리 2 는 해결 **동사**로 고른다 — 「블록 놀이도 했어」는 쌓기가 아니라 아이가 말한 블록을 건네는 날이다
        assertNull(fixPropIn("같이 미끄럼틀을 타고 블록 놀이도 했어", ""))
    }

    /** 이슈의 네 이야기 — 차례로 꽂으면 바로 앞 두 권과 같은 조합이 하나도 없다 */
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

    /** 같은 칸으로 두 번 연속 만들면 두 번째는 다른 조합 · 세 번째는 앞 두 권과 다른 조합 */
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

    /** 같은 입력 → 같은 미션 (기록도 같을 때) */
    @Test
    fun theSameFactsAndHistoryGiveTheSameMissions() {
        val f = story("공룡이 나타났어")
        val h = listOf("A6:E1", "C1:E1")
        assertEquals(pickMissions(f, h), pickMissions(f, h))
        assertEquals(pickMissions(f, h), pickMissions(f.copy(), h.toList()))
    }

    /** 아이 말이 고른 자리는 돌리지 않는다 — 앞 책과 같아도 아이 말이 먼저 */
    @Test
    fun theChildsWordsWinOverRotation() {
        val fire = coopDay("불이 났어", "물을 뿌려서 껐어")
        val m = pickMissions(fire, listOf("A6:A1", "A6:A1"))
        assertEquals(MissionId.A1, m.slot2)
        assertTrue(m.slot2FromChild)
        val candle = story("촛불이 켜져 있었어", "끄고 노래했어")
        assertEquals(MissionId.C1, pickMissions(candle, listOf("C1:E1", "C1:A5")).slot1)
    }

    /** 실제 하루는 아이가 말하지 않은 소품을 빌리지 않는다 — 불기(나뭇잎)로 돌리지 않고, 돌려 쓴 자리 2 는 책 문장에 없다 */
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

    /** 상상 이야기는 돌려 쓴 미션의 소품을 빌려 와 책 문장도 맞춘다(설계 §7-2) */
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

    /** 기본 소품(먼지 · 별)은 서버에 보내지 않고 · 배지에 이름을 쓰지 않는다 */
    @Test
    fun defaultPropsAreNotSentOrNamed() {
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어"; newcomerKind = "고양이" }
        val base = Server.base; val modes = Server.liveModes
        Server.base = "http://localhost:1"; Server.liveModes = setOf(StoryMode.STORY)   // 실시간 동화 — 모르는 손님이면 먼지
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

    /** 책에 저장한 미션으로 다시 읽는다 — 그사이 기록이 바뀌어도 */
    @Test
    fun aSavedBookIsReReadWithTheSameMissions() {
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어" }
        val made = s.missions()
        val visuals = s.captureStoryVisuals()
        assertEquals(made.encode(), visuals.missions)
        MissionHistory.record(StoryMode.STORY, "other1", made)
        MissionHistory.record(StoryMode.STORY, "other2", made)

        val back = storyVisualsFromJson(visuals.toJson())
        val reread = DemoState()
        reread.restoreStoryBook(SavedStoryBook("b", "책", "park", "bg_park", listOf(SavedStoryPage(PageKind.DEPART, "쪽")), back))
        assertEquals(made, reread.missions())
        assertEquals(made, BookMissions.decode(made.encode()))
        assertNull("옛 책(미션 없음)", BookMissions.decode(null))
    }

    /** 기록은 모드마다 넷 · 같은 책을 다시 꽂으면 그 줄만 바뀐다 · 붙이지 않으면 기록하지 않는다 */
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
