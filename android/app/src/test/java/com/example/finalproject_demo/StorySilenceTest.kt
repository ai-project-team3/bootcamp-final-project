package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorySilenceTest {
    @Test
    fun silenceNeverBecomesAPlaceOrAnImageRequest() = runBlocking {
        val server = StoryTestServer { _, _ ->
            JSONObject().put("judge", JSONObject().put("reason", "ok")
                .put("story_ready", false).put("next_slot", "place"))
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.timerOn = true
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.PLACE)
            withTimeout(5_000) {
                while (d.s.events.none { "mode=silent" in it }) delay(5)
                // Observe a complete no-answer fallback, including the next question.
                while (d.s.events.count { "mode=silent" in it } < 2) delay(5)
            }
            assertTrue("silence is not a filled slot", d.s.slots.isEmpty())
            assertTrue("silence must not be sent as invented speech", server.requests.isEmpty())
            assertFalse("do not propose a placeholder as a real choice",
                d.s.log.any { "혹시 아직 정하지 않았어요" in it })
            assertNull(d.s.storyBackground)
        } finally {
            scope.cancel()
            Server.base = null
            Server.liveModes = emptySet()
            server.close()
        }
    }
}
