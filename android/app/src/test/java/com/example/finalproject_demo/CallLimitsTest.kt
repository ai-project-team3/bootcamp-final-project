package com.example.finalproject_demo

import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 서버 연결판 호출 한도 (10-06 팀 합의 · #30 · #172) — 기기 하루 두 권, 한 세션 목소리 60줄 · 판정 40번, 기기 하루 목소리 120줄.
 * 턴 상한이 아니다(규칙 3): 넘으면 그 호출만 null 이고, 이야기는 계속된다. 스토어 빌드에서만 켠다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallLimitsTest {
    private var day = "2026-10-06"

    @Before fun on() { CallLimits.reset(); CallLimits.enabled = true; CallLimits.today = { day } }
    @After fun off() { CallLimits.reset(); Server.resetCalls() }

    @Test
    fun aNewDeviceHasFiveStarsOnceAndNoneComeBackWithTheDay() {
        // #445 · 10-14 — one star starts one server book: five stars at install, none per day
        CallLimits.testerDailyRefill = false     // the real rule, once payment or ads are wired
        assertEquals(5, CallLimits.starsLeft())
        CallLimits.starSpent()
        assertEquals(4, CallLimits.starsLeft())
        day = "2026-10-07"
        assertEquals("남겨 둔 별은 날이 바뀌어도 그대로다", 4, CallLimits.starsLeft())
        repeat(4) { CallLimits.starSpent() }
        assertEquals("별이 없으면 새 이야기를 시작하지 않는다", 0, CallLimits.starsLeft())
        CallLimits.starSpent()
        assertEquals("없는 별은 더 줄지 않는다", 0, CallLimits.starsLeft())
        day = "2026-10-08"
        assertEquals("날짜가 바뀌어도 별은 새로 생기지 않는다", 0, CallLimits.starsLeft())
    }

    @Test
    fun whileTestingANewDayTopsTheStarsBackUpToFiveAndNeverAbove() {
        // closed testing: payment and ads are not wired, so a tester must not stop for good after five books
        repeat(5) { CallLimits.starSpent() }
        assertEquals(0, CallLimits.starsLeft())
        day = "2026-10-07"
        assertEquals("새 날에는 다섯 개로 다시 채운다", 5, CallLimits.starsLeft())
        CallLimits.starSpent()
        day = "2026-10-08"
        assertEquals("쌓이지 않는다 — 다섯 개까지만", 5, CallLimits.starsLeft())
    }

    @Test
    fun aSessionStopsAskingForVoiceAt60AndTheDayAt120() {
        assertNull(CallLimits.blocks("/tts", mapOf("/tts" to 59)))
        assertNotNull("한 세션 60줄", CallLimits.blocks("/tts", mapOf("/tts" to 60)))
        assertNotNull("한 세션 판정 40번", CallLimits.blocks("/turn", mapOf("/turn" to 40)))
        assertNull("받아쓰기는 우리 GPU — 세지 않는다", CallLimits.blocks("/stt", mapOf("/stt" to 500)))
        repeat(120) { CallLimits.counted("/tts") }
        assertNotNull("기기 하루 120줄", CallLimits.blocks("/tts", emptyMap()))
        day = "2026-10-07"
        assertNull(CallLimits.blocks("/tts", emptyMap()))
    }

    @Test
    fun theTeamsDevAppIsNotLimited() {
        CallLimits.enabled = false
        repeat(5) { CallLimits.starSpent() }
        assertEquals(CallLimits.WELCOME_STARS, CallLimits.starsLeft())
        assertNull(CallLimits.blocks("/tts", mapOf("/tts" to 1_000)))
    }

    @Test
    fun pastTheLimitTheVoiceCallIsNotMadeAtAll() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> JSONObject() }
        try {
            Server.base = server.base
            Server.resetCalls()
            repeat(CallLimits.DAY_TTS) { CallLimits.counted("/tts") }
            assertNull(Server.tts("안녕!"))
            assertEquals("한도를 넘은 목소리를 서버에 청했다", 0, server.requests.count { it.first.contains("/tts") })
        } finally {
            Server.base = previous
            server.close()
        }
    }
}
