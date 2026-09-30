package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLiveFlowTest {
    @Test
    fun actualStoryEntryFollowsVerdictsAndStopsAsSoonAsTheServerIsReady() = runBlocking {
        val server = StoryTestServer { path, body ->
            when (path) {
                "/turn" -> {
                    val slot = body.getString("asked_slot")
                    val ready = slot == "reaction"
                    JSONObject().put("judge", JSONObject()
                        .put("reason", "ok").put("slot_1", slot)
                        .put("value_1", body.getString("utterance"))
                        .put("next_slot", if (slot == "place") "problem" else "reaction")
                        .put("story_ready", ready))
                        .put("line", JSONObject().put("ack", "들려줘서 고마워!")
                            .put("question", if (ready) JSONObject.NULL else "그때 어떻게 했어?"))
                }
                "/story" -> JSONObject().put("scenes", JSONArray().apply {
                    val pages = body.getJSONArray("pages")
                    repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                        .put("kind", pages.getJSONObject(i).getString("kind"))
                        .put("caption", "${i + 1}번째 실제 생성 문장이에요.")) }
                })
                else -> JSONObject().put("preset", true)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.PLACE)
            val feeder = launch {
                while (isActive) {
                    if (d.s.micEnabled) d.send(Reply.Spoke("숲에서 친구를 만나 같이 놀았어"))
                    delay(40)
                }
            }
            withTimeout(10_000) { while (d.s.scene != Scene.BOOK) delay(10) }
            feeder.cancelAndJoin()
            val turns = server.requests.filter { it.first == "/turn" }.map { it.second }
            assertEquals(listOf("place", "problem", "reaction"), turns.map { it.getString("asked_slot") })
            assertEquals("story_ready", d.s.endReason)
            assertTrue(d.s.bookCaption(1).contains("실제 생성 문장"))
            assertEquals(3, d.s.notes.size)
            assertNull("scene skipping must not invent a cause", d.s.cause)
        } finally {
            scope.cancel()
            Server.base = null
            Server.liveModes = emptySet()
            server.close()
        }
    }
}
