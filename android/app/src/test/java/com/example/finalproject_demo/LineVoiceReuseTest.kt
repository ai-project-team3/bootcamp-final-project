package com.example.finalproject_demo

import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-14 — 이번 판에 이미 받은 목소리는 다시 말할 때 그대로 쓴다. 오또 얼굴 다시 듣기 · 책 ◀ 앞 쪽 · 「말하기」 · 미션 뒤 다시 읽기가
 * 같은 줄을 /tts 에 다시 보내 값을 또 냈다(받은 소리는 [Director.voiceOf] 에 남아 있었는데 꺼내 쓰지 않았다)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LineVoiceReuseTest {

    @Test
    fun aLineSaidAgainIsNotSentToTtsAgain() = runBlocking {
        var calls = 0
        val http = FakeHttp { path, _ -> if (path == "/tts") { calls++; "mp3-$calls" } else null }
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.mode = StoryMode.STORY
        Server.base = http.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            val first = d.lineVoice("그러자 콩이는 고개를 숙였어요.")
            val again = d.lineVoice("그러자 콩이는 고개를 숙였어요.")
            assertEquals("같은 줄을 두 번 샀다", 1, calls)
            assertArrayEquals(first, again)
            d.lineVoice("다음 쪽 문장이에요.")
            assertEquals("다른 줄은 받는다", 2, calls)
        } finally {
            Server.liveModes = emptySet()
            Server.base = null
            http.close()
        }
    }
}
