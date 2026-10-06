package com.example.finalproject_demo

import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #172 — 그림일기도 끝나면 서버 호출 수를 한 줄로 남긴다(동화의 선물 화면과 같은 줄).
 * 전에는 동화 끝 장면(`sceneEnd`)에서만 남겨, 다 끝낸 일기가 다음 세션 시작 때 `unfinished` 로 찍혔다(10-06 실기기).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryCallCountTest {
    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    /** 버튼을 누르되 화면이 움직일 때까지 다시 누른다 — 감독은 말이 끝나기 전 입력을 버린다 */
    private suspend fun Director.push(part: String): Boolean {
        if (await(3_000) { s.buttons.any { part in it.label } } == null) return false
        repeat(12) {
            val b = s.buttons.firstOrNull { part in it.label } ?: return true
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || s.buttons.none { x -> part in x.label } } != null) return true
        }
        return false
    }

    @Test
    fun aShelvedDiaryLeavesItsCallCountAndStartsTheNextCountFromZero() = runBlocking {
        val previous = Server.base
        val server = StoryTestServer { _, _ -> JSONObject() }
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        try {
            d.s.speed = 0.01
            d.s.mode = StoryMode.DIARY
            val s = d.s
            d.go(Scene.DIARY)
            assertTrue(d.push("그림 없이 이야기할래"))
            var guard = 0
            while (s.stage !is DiaryPaper && guard++ < 20) {
                if (await(2_000) { s.buttons.any { "🎬 오늘 이야기 시연 답" in it.label } } == null) break
                d.push("🎬 오늘 이야기 시연 답")
            }
            guard = 0
            while (s.stage !is DiaryGift && guard++ < 30) {
                val b = s.buttons.firstOrNull { "😄" in it.label } ?: s.buttons.firstOrNull { "다음 쪽" in it.label || "다 읽었어" in it.label }
                    ?: s.buttons.firstOrNull { "대답 없음" in it.label }
                if (b == null) { delay(20); continue }
                b.onClick(); delay(30)
            }
            assertTrue("그림일기 책 선물(D6)로 안 갔다 (${s.stage})", await { s.stage is DiaryGift } != null)

            // 이 세션이 서버를 두 번 불렀다 — 대본 모드라 흐름은 서버를 안 부르니 여기서 직접 부른다
            Server.base = server.base
            Server.tts("오늘 그림일기가 완성됐어!")
            Server.tts("책장에 꽂아 줄래?")
            Server.base = previous

            assertTrue(d.push("책장에 꽂기"))
            assertTrue("책장으로 안 갔다", await { s.scene == Scene.SHELF } != null)
            val line = s.log.firstOrNull { it.startsWith("서버 호출 (이번 세션)") }
            assertTrue("그림일기를 끝내도 서버 호출 수를 남기지 않았다 — ${s.log.take(5)}", line != null && "total 2 · /tts 2" in line)
            assertEquals("남긴 뒤 세기를 처음부터 — 다음 세션이 이 일기를 unfinished 로 다시 찍지 않게", "", Server.callSummary())
        } finally {
            Server.base = previous
            sup.cancel()
            server.close()
        }
    }
}
