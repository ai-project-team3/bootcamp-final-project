package com.example.finalproject_demo

import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopMissionProp
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.BookMissions
import com.example.finalproject_demo.demo.missions.MissionHistory
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.blowProp
import com.example.finalproject_demo.demo.missions.missions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 협업 책 점검(10-14 · A1) — 실제 하루(다녀왔어요 · 곧 해요) 책의 미션 자리 1 이 늘 문지르기(A6)였다.
 * 실제 하루도 불기(C1)로 돌되, 아이가 말하지 않은 이름(나뭇잎)은 빌리지 않고 이름 없는 바람(BREEZE)을 분다.
 * 그 바람은 서버에 이름으로 보내지 않는다 — 그 쪽은 상상 쪽이다(#340)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BreezeOnARealDayTest {
    @Before fun attach() = MissionHistory.attach(ApplicationProvider.getApplicationContext())
    @After fun detach() = MissionHistory.detach()

    private fun day(reason: String) = DemoState().apply {
        mode = StoryMode.COOP; coopPick = CoopPick("place", "동물원", reason); problem = "기린을 봤어"; solution = "집에 왔어"
        listOf("problem", "solution").forEach { slotBy[it] = "child" }
    }

    @Test
    fun aRealDayBlowsTheNamelessBreezeAndSendsNoName() {
        MissionHistory.record(StoryMode.COOP, "e1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        val s = day("done")
        assertEquals(MissionId.C1, s.missions().slot1)
        assertEquals(BlowProp.BREEZE, s.blowProp())
        assertNull(s.coopMissionProp(PageKind.RUB))
        assertNull(BlowProp.BREEZE.serverWord)
    }

    @Test
    fun aCandleTheChildSaidIsStillTheCandle() {
        val s = day("done").apply { problem = "생일 촛불을 껐어" }
        assertEquals(MissionId.C1, s.missions().slot1)
        assertEquals(BlowProp.CANDLE, s.blowProp())
        assertEquals("촛불", BlowProp.CANDLE.serverWord)
    }

    @Test
    fun anImaginedStoryStillBorrowsTheLeaf() {
        MissionHistory.record(StoryMode.COOP, "e1", BookMissions(MissionId.A6, MissionId.E1, false, false))
        val s = day("dream")
        assertEquals(MissionId.C1, s.missions().slot1)
        assertEquals(BlowProp.LEAF, s.blowProp())
        assertEquals("나뭇잎", s.coopMissionProp(PageKind.RUB))
    }
}
