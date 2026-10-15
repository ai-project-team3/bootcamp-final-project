package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.seedFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실기기(10-14 laya) — 이어 한 동물원 책 7쪽에 그 판에 없던 「선생님과 함께 블록을 다시 쌓았어요」가 나왔다.
 * 시연 서랍용 기본 칸(seedDiary)이 아이가 답한 책의 빈 칸까지 채운 것이다. 아이가 답한 책은 채우지 않는다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopNoDemoSeedTest {
    private fun director() = Director(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test
    fun aBookTheChildAnsweredGetsNoDemoSolution() {
        val d = director()
        d.s.mode = StoryMode.COOP; d.s.coopPick = CoopPick("place", "동물원", "")
        d.s.place = "기린 있는 곳"; d.s.slots["place"] = "기린 있는 곳"; d.s.slotBy["place"] = "child"
        d.seedFor(Scene.BOOK)
        assertNull(d.s.solution)
        assertNull(d.s.slots["solution"])
        assertNull(d.s.problem)
        assertEquals("기린 있는 곳", d.s.place)
    }

    @Test
    fun anEmptyDrawerJumpIsStillSeeded() {
        val d = director()
        d.s.mode = StoryMode.DIARY
        d.seedFor(Scene.BOOK)
        assertEquals("선생님과 함께 블록을 다시 쌓았어요", d.s.slots["solution"])
    }
}
