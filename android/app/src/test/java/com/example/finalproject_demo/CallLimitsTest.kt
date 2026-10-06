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
    fun twoServerBooksADayThenTomorrowAgain() {
        assertEquals(2, CallLimits.booksLeftToday())
        CallLimits.bookStarted(); CallLimits.bookStarted()
        assertEquals("세 번째 이야기는 시작하지 않는다", 0, CallLimits.booksLeftToday())
        day = "2026-10-07"
        assertEquals("날짜가 바뀌면 다시 두 권", 2, CallLimits.booksLeftToday())
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
        repeat(5) { CallLimits.bookStarted() }
        assertEquals(CallLimits.BOOKS_PER_DAY, CallLimits.booksLeftToday())
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
