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
    @Test fun nonThemePlaceUsesTheParkKitWithoutAnImageRequest() = runBlocking {
        assertTrue("the kit is the default", SceneKits.liveStory)
        val server = StoryTestServer { path, _ ->
            when (path) {
                "/turn" -> JSONObject().put("judge", JSONObject()
                    .put("reason", "ok").put("slot_1", "place").put("value_1", "놀이터")
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
                    if (d.s.micEnabled) d.send(Reply.Spoke("놀이터에 갔어"))
                    delay(20)
                }
                while (!(d.s.micEnabled && d.s.turn == 1)) delay(5)
            }
            assertEquals("park", d.s.sceneKit)
            assertTrue("the kit stage shows at once, no waiting", d.s.stage is Stage.World)
            assertNull(d.s.storyBackground)
            assertTrue("no background generation", server.requests.none { it.first == "/image" })
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
