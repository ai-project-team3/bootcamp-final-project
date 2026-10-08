package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reward
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.ShelfBook
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 업적이 새로 열리면 짧은 팡파르 한 번 — 그냥 책장에 꽂을 때는 울리지 않는다 (#295 리뷰 3) */
class RewardFanfareTest {
    private val played = mutableListOf<Sound>()

    @After fun reset() { Sfx.onPlay = {} }

    private fun run(block: suspend (Director) -> Unit) = runBlocking {
        Sfx.onPlay = { synchronized(played) { played += it } }
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel() }
    }

    private suspend fun Director.shelve() {
        s.shelf.add(0, ShelfBook("곰돌이의 소풍", "forest", "bg_forest", fresh = true))
        go(Scene.SHELF)
        assertNotNull(withTimeoutOrNull(5_000) { while (s.stage !is Stage.Shelf) delay(3); true })
    }

    @Test fun aNewRewardPlaysTheFanfareOnce() = run { d ->
        d.s.rewardNews += Reward.RAINBOW
        d.shelve()
        assertNotNull("보상 대사가 나오지 않았다", withTimeoutOrNull(5_000) { while ("생겼어" !in d.s.line) delay(3); true })
        assertEquals(listOf(Sound.FANFARE), synchronized(played) { played.toList() })
    }

    @Test fun shelvingWithoutARewardIsQuiet() = run { d ->
        d.shelve()
        delay(300)
        assertTrue(synchronized(played) { Sound.FANFARE !in played })
    }
}
