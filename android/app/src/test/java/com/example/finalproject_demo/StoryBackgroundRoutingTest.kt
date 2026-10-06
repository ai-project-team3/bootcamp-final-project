package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/** Exercise the default kit switch and the real image HTTP boundary together (#218). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryBackgroundRoutingTest {
    @Test fun unknownCityGeneratesItsOwnBackground() = checkPlace("미래 도시", false)
    @Test fun imaginaryPlaceDoesNotBecomeThePark() = checkPlace("음식나라", false)
    @Test fun imagePresetRemainsAnExplicitFallback() = checkPlace("미래 도시", true)

    private fun checkPlace(place: String, preset: Boolean) = runBlocking {
        val png = ByteArrayOutputStream().apply {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
            bitmap.recycle()
        }.toByteArray()
        val server = StoryTestServer { path, body ->
            when {
                path == "/turn" -> JSONObject().put("judge", JSONObject()
                    .put("reason", "ok").put("slot_1", "place").put("value_1", place)
                    .put("next_slot", "problem").put("story_ready", false))
                    .put("line", JSONObject().put("ack", "그곳으로 가 보자!")
                        .put("question", "무슨 일이 생겼어?"))
                path == "/image" && body.optString("kind") == "background" ->
                    JSONObject().put("preset", preset).apply {
                        if (!preset) put("png_base64", Base64.getEncoder().encodeToString(png))
                    }
                else -> JSONObject().put("preset", true)
            }
        }
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val previousKitSwitch = SceneKits.liveStory
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.STORY)
            SceneKits.liveStory = true
            d.s.speed = 0.01
            d.s.timerOn = false
            d.go(Scene.PLACE)
            withTimeout(5_000) {
                while (server.requests.none { it.first == "/turn" }) {
                    if (d.s.micEnabled) d.send(Reply.Spoke("${place}로 가고 싶어"))
                    delay(20)
                }
                while (!(d.s.micEnabled && d.s.turn == 1 && d.s.stage is Stage.World)) delay(10)
            }
            val images = server.requests.filter { it.first == "/image" && it.second.optString("kind") == "background" }
            assertEquals(1, images.size)
            assertEquals(place, images.single().second.getString("place"))
            assertEquals("story", images.single().second.getString("mode"))
            assertNull("an unmatched place must not silently select park", d.s.sceneKit)
            assertTrue("the chosen route must be diagnosable", d.s.log.any { it == "background route=generated place=$place" })
            if (preset) {
                assertNull(d.s.storyBackground)
                assertTrue(d.s.log.any { it == "background result=preset place=$place" })
            } else {
                assertNotNull(d.s.storyBackground)
                assertEquals(d.s.storyBackground, d.s.bgName)
                assertArrayEquals(png, File(d.s.storyBackground!!.removePrefix("local:")).readBytes())
                assertTrue(d.s.log.any { it == "background result=generated place=$place" })
            }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            SceneKits.liveStory = previousKitSwitch
            server.close()
        }
    }
}
