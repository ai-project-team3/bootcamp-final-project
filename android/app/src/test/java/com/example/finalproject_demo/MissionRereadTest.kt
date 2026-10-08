package com.example.finalproject_demo

import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.captureStoryVisuals
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.BookMissions
import com.example.finalproject_demo.demo.missions.MissionHistory
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.demo.missions.blowProp
import com.example.finalproject_demo.demo.missions.blowPropIn
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.demo.missions.slot1Prop
import com.example.finalproject_demo.demo.missions.soundProp
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.storyPagePlan
import com.example.finalproject_demo.demo.storyVisualsFromJson
import com.example.finalproject_demo.demo.toJson
import com.example.finalproject_demo.demo.useGeneratedStory
import com.example.finalproject_demo.demo.useCoopCaptions
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.net.Server
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #321 리뷰(민우 P2) — 다시 읽은 책은 만들 때와 **같은 미션 · 같은 소품**으로 그려져야 한다. 전에는 미션 ID 만 저장해
 * 소방차 책(C3)을 다시 열면 아이 말이 없어 소방차가 사라지고, 같이 만들기 좋아해요 책의 빌린 나뭇잎(C1)은 문지르기가 됐다.
 * 기본 먼지 · 별의 완료 문장도 책에 남지 않는다. 조장 결정(10-08): 「바람이 불어서」(까닭)로 불기를 고르지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissionRereadTest {
    @Before fun attach() { MissionHistory.attach(ApplicationProvider.getApplicationContext()); MissionHistory.detach()
        MissionHistory.attach(ApplicationProvider.getApplicationContext()) }
    @After fun detach() { MissionHistory.detach(); Server.base = null; Server.liveModes = emptySet() }

    private fun reread(made: DemoState, mode: StoryMode): DemoState {
        val back = storyVisualsFromJson(made.captureStoryVisuals().toJson())
        return DemoState().apply {
            restoreStoryBook(SavedStoryBook("b", "책", "park", "bg_park", listOf(SavedStoryPage(PageKind.DEPART, "쪽")), back))
            this.mode = mode                       // a co-op re-read does not get its pick back
        }
    }

    @Test
    fun aFireTruckBookIsReReadWithTheFireTruck() {
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "소방차가 삐뽀삐뽀 달려왔어" }
        assertEquals(MissionId.C3, s.missions().slot1)
        assertEquals(SoundProp.SIREN, s.soundProp())
        val made = s.missions()
        val again = reread(s, StoryMode.STORY)
        MissionHistory.record(StoryMode.STORY, "x", made)          // the history moves on after the book is shelved
        assertEquals(made, again.missions())
        assertEquals("다시 읽어도 소방차", SoundProp.SIREN, again.soundProp())
        assertEquals(SoundProp.SIREN, again.slot1Prop())
    }

    @Test
    fun aDreamCoopBookKeepsItsBorrowedLeaf() {
        MissionHistory.record(StoryMode.COOP, "e1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        val s = DemoState().apply {
            mode = StoryMode.COOP; templateKey = "C"; coopPick = CoopPick("place", "우주", "dream"); problem = "외계인을 만났어"
        }
        assertEquals(MissionId.C1, s.missions().slot1)
        assertEquals(BlowProp.LEAF, s.blowProp())
        val again = reread(s, StoryMode.COOP)
        assertEquals("다시 읽어도 나뭇잎 불기 — 문지르기가 아니다", BlowProp.LEAF, again.blowProp())
        assertEquals(MissionId.C1, again.missions().slot1)
    }

    /** 기본 먼지 · 별 — 완료 문장이 붙지 않고, 저장한 문장 · 다시 연 문장에도 없다 */
    @Test
    fun defaultDustAndStarGetNoCompletionLine() {
        Server.base = "http://localhost:1"; Server.liveModes = setOf(StoryMode.STORY)
        val s = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; newcomerKind = "고양이" }
        val pages = s.template!!.pages
        s.useGeneratedStory(pages.indices.map { "서버 문장 ${it + 1}" })
        val rub = pages.indexOfFirst { it.kind == PageKind.RUB } + 1
        val drag = pages.indexOfFirst { it.kind == PageKind.DRAG } + 1
        s.m1Result = "solo"; s.m2Result = "solo"
        assertEquals("기본 먼지의 완료 문장이 붙었다", "서버 문장 $rub", s.bookCaption(rub))
        assertEquals("기본 별의 완료 문장이 붙었다", "서버 문장 $drag", s.bookCaption(drag))
    }

    /**
     * 조장 10-08(#340) — 실제 하루에 아이가 말하지 않은 미션 쪽은 서버가 「오또가 상상해 봤어!」로 쓴다. 앱이 미션 뒤에 붙이는
     * 결과도 상상 말투(「상상 속에서 …」). 저장한 문장 · 다시 연 문장도 같다. 좋아해요 책 · 아이가 말한 소품의 쪽은 그대로
     */
    @Test
    fun anImaginedPageOnARealDayClosesInImagination() {
        MissionHistory.record(StoryMode.COOP, "e1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        val s = DemoState().apply {
            mode = StoryMode.COOP; coopPick = CoopPick("place", "놀이공원", "done"); problem = "풍선을 놓쳤어"; solution = "괜찮다고 했어"
        }
        assertEquals(MissionId.A5, s.missions().slot2)
        val pages = s.template!!.pages
        assertTrue(s.useCoopCaptions(pages.indices.map { "서버 문장 ${it + 1}" }))
        val drag = pages.indexOfFirst { it.kind == PageKind.DRAG } + 1
        assertEquals("서버 문장 $drag", s.bookCaption(drag))
        s.m2Result = "solo"
        val done = s.bookCaption(drag)
        assertEquals("서버 문장 $drag 상상 속에서 블록 탑이 높이 섰어!", done)
        // 저장한 문장으로 다시 열면 같은 문장 — 결과가 두 번 붙지 않는다
        val saved = (1..s.pageCount).map { s.bookCaption(it) }
        s.templateKey = "N"                                   // a co-op book is saved under the diary frame 「N」 (completedCoopBook)
        val again = DemoState().apply {
            restoreStoryBook(SavedStoryBook("b", "책", "park", "bg_park", pages.mapIndexed { i, p -> SavedStoryPage(p.kind, saved[i]) }, s.captureStoryVisuals()))
            mode = StoryMode.COOP
        }
        assertEquals(done, again.bookCaption(drag))
        // 좋아해요(상상 책 전체)는 상상 표시를 붙이지 않는다
        val dream = DemoState().apply { mode = StoryMode.COOP; coopPick = CoopPick("place", "놀이공원", "dream"); problem = "풍선을 놓쳤어" }
        dream.useCoopCaptions(dream.template!!.pages.indices.map { "서버 문장 ${it + 1}" })
        dream.m2Result = "solo"
        assertTrue(dream.bookCaption(drag), "상상 속에서" !in dream.bookCaption(drag))
        // 아이가 말한 소품의 쪽(「불을 껐어」)은 그날의 일 그대로
        val said = DemoState().apply {
            mode = StoryMode.COOP; coopPick = CoopPick("place", "소방서", "done"); problem = "불이 났어"; solution = "물을 뿌려서 불을 껐어"
            listOf("problem", "solution").forEach { slotBy[it] = "child" }
        }
        said.useCoopCaptions(said.template!!.pages.indices.map { "서버 문장 ${it + 1}" })
        said.m2Result = "solo"
        assertTrue(said.bookCaption(drag), "상상 속에서" !in said.bookCaption(drag))
    }

    /** 조장 결정 — 까닭으로 말한 바람은 불기가 아니다. 바람에 날아간 일은 그대로 */
    @Test
    fun windAsAReasonDoesNotPickBlowing() {
        assertNull(blowPropIn("바람이 세게 불어서 풍선을 놓쳤어", realDay = true))
        assertNull(blowPropIn("바람이 불어서", realDay = false))
        assertNull(blowPropIn("바람 때문에 모자가 날아갔어", realDay = false))
        assertEquals(BlowProp.LEAF, blowPropIn("바람에 모자가 날아갔어", realDay = false))
        assertEquals("실제 하루의 바람은 이름 없는 반짝이 가루", BlowProp.BREEZE, blowPropIn("바람이 많이 불었어", realDay = true))
        val s = DemoState().apply { mode = StoryMode.COOP; coopPick = CoopPick("place", "놀이공원", "done"); problem = "풍선을 놓쳤어"; cause = "바람이 세게 불어서" }
        assertEquals("10-08 실기기 책 — 까닭의 바람으로 불기를 골랐다", MissionId.A6, s.missions().slot1)
    }

    /** /story 쪽마다 미션이 어디서 왔는지 — child · rotated · default (조장 결정 10-08) */
    @Test
    fun pagesSayWhereTheirMissionCameFrom() {
        val plain = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어" }
        val p = plain.storyPagePlan()
        assertEquals("default", p.first { it.kind == "RUB" }.missionSource)
        assertEquals("default", p.first { it.kind == "DRAG" }.missionSource)
        val said = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "촛불이 켜져 있었어"; solution = "물을 뿌려서 껐어" }
        val q = said.storyPagePlan()
        assertEquals("child", q.first { it.kind == "RUB" }.missionSource)
        assertEquals("child", q.first { it.kind == "DRAG" }.missionSource)
        MissionHistory.record(StoryMode.STORY, "r1", plain.missions())
        val rotated = DemoState().apply { mode = StoryMode.STORY; templateKey = "C"; problem = "공룡이 나타났어" }.storyPagePlan()
        assertEquals("rotated", rotated.first { it.kind == "RUB" }.missionSource)
        assertNull(rotated.first { it.kind == "DEPART" }.missionSource)
    }
}
