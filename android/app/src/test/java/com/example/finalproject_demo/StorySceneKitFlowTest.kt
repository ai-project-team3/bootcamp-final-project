package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 10-05: with the felt scene kit on, a non-theme place shows the park kit at once and asks no /image */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorySceneKitFlowTest {
    @Test fun nonThemePlaceUsesTheParkKitWithoutAnImageRequest() = kitPlace("놀이터", "park")

    /**
     * #222 (10-06) — the book draws one picture, not pieces. A 바닷가 story's book fell back to `bg_snow`; now the kit is
     * saved as that picture (a local file), still with no /image request
     */
    @Test fun aBeachStorysBookShowsTheBeachKitNotSnow() = kitPlace("바닷가", "beach")

    private fun kitPlace(place: String, kit: String) = runBlocking {
        assertTrue("the kit is the default", SceneKits.liveStory)
        val server = StoryTestServer { path, _ ->
            when (path) {
                "/turn" -> JSONObject().put("judge", JSONObject()
                    .put("reason", "ok").put("slot_1", "place").put("value_1", place)
                    .put("next_slot", "problem").put("story_ready", false))
                    .put("line", JSONObject().put("ack", "그곳으로 가 보자!").put("question", "무슨 일이 생겼어?"))
                else -> JSONObject().put("preset", true)
            }
        }
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.STORY)
            val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
            d.s.speed = 0.01
            d.s.timerOn = false
            d.go(Scene.PLACE)
            withTimeout(5_000) {
                while (server.requests.none { it.first == "/turn" }) {
                    if (d.s.micEnabled) d.send(Reply.Spoke("${place}에 갔어"))
                    delay(20)
                }
                while (!(d.s.micEnabled && d.s.turn == 1)) delay(5)
            }
            assertEquals(kit, d.s.sceneKit)
            assertTrue("the kit stage shows at once, no waiting", d.s.stage is Stage.World)
            // the kit saved as the book's one picture, a moment later
            withTimeout(10_000) { while (d.s.storyBackground == null) delay(20) }
            assertTrue("the book picture is the saved kit: ${d.s.storyBackground}", d.s.storyBackground!!.startsWith("local:"))
            assertEquals(d.s.storyBackground, d.s.bgName)
            assertNotEquals("bg_snow", d.s.bgName)
            assertTrue("no background generation", server.requests.none { it.first == "/image" })
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
