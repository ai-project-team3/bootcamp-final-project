package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import java.io.ByteArrayOutputStream
import java.util.Base64
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
class StoryMissingPlaceTest {
    // the generated-background path; the felt scene kit (on by default) would skip /image
    @get:org.junit.Rule val sceneKitOff = SceneKitOff()

    @Test fun aReadyStoryWithNoPlaceAsksOnceMoreAndUsesTheConfirmedPlace() =
        finishStory(finalAnswer = "숲속 호수", confirmedPlace = "숲속 호수") { d, requests ->
            assertEquals(listOf("place", "place"), requests.filter { it.first == "/turn" }
                .map { it.second.getString("asked_slot") })
            assertEquals("숲속 호수", d.s.slots["place"])
            assertEquals("child", d.s.slotBy["place"])
            assertEquals(listOf("숲속 호수"), backgroundPrompts(requests))
        }

    @Test fun silenceAtTheFinalPlaceCheckUsesTheFirstSceneWithoutInventingAPlace() =
        finishStory(finalAnswer = null, confirmedPlace = null) { d, requests ->
            assertEquals(1, requests.count { it.first == "/turn" })
            assertNull(d.s.slots["place"])
            assertNull(d.s.slotBy["place"])
            assertEquals(listOf("문어는 바닷속 정원에서 길을 찾았어요."), backgroundPrompts(requests))
            assertEquals(1, d.s.modeSilent)
        }

    @Test fun anUnresolvedSpokenPlaceAlsoUsesTheSceneAndKeepsTheSlotEmpty() =
        finishStory(finalAnswer = "모르겠어", confirmedPlace = null) { d, requests ->
            assertEquals(2, requests.count { it.first == "/turn" })
            assertNull(d.s.slots["place"])
            assertEquals(listOf("문어는 바닷속 정원에서 길을 찾았어요."), backgroundPrompts(requests))
        }

    private fun backgroundPrompts(requests: List<Pair<String, JSONObject>>) = requests
        .filter { it.first == "/image" && it.second.optString("kind") == "background" }
        .map { it.second.getString("place") }

    private fun finishStory(
        finalAnswer: String?, confirmedPlace: String?,
        check: (Director, List<Pair<String, JSONObject>>) -> Unit,
    ) = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GREEN)
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        var turnRequests = 0
        val server = StoryTestServer { path, body -> when (path) {
            "/turn" -> {
                turnRequests++
                val place = confirmedPlace?.takeIf { turnRequests > 1 }
                JSONObject().put("judge", JSONObject().put("reason", "ok")
                    .put("slot_1", if (place != null) "place" else "problem")
                    .put("value_1", place ?: "문어가 길을 찾았어")
                    .put("next_slot", JSONObject.NULL).put("story_ready", true))
                    .put("line", JSONObject().put("ack", "들려줘서 고마워!"))
            }
            "/story" -> JSONObject().put("scenes", JSONArray().apply {
                val pages = body.getJSONArray("pages")
                repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                    .put("kind", pages.getJSONObject(i).getString("kind"))
                    .put("caption", if (i == 0) "문어는 바닷속 정원에서 길을 찾았어요." else "친구와 같이 돌아왔어요.")) }
            })
            "/image" -> JSONObject().put("preset", false)
                .put("png_base64", Base64.getEncoder().encodeToString(png))
            else -> JSONObject().put("preset", true)
        } }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.STORY)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val d = Director(scope, storyImageStore = StoryImageStore(context))
            d.s.speed = 0.01
            d.s.timerOn = false
            d.s.storySoundAttempted = true
            d.go(Scene.PLACE)
            val feeder = launch {
                while (isActive) {
                    if (d.s.micEnabled) {
                        val replied = server.requests.any { it.first == "/turn" }
                        d.send(if (!replied) Reply.Spoke("문어가 길을 찾았어")
                            else finalAnswer?.let { Reply.Spoke(it) } ?: Reply.Silent)
                    }
                    delay(30)
                }
            }
            withTimeout(8_000) { while (d.s.scene != Scene.BOOK) delay(5) }
            feeder.cancelAndJoin()
            assertEquals("story_ready", d.s.endReason)
            check(d, server.requests.toList())
            assertNotNull("the book must retain its generated background", d.s.storyBackground)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
