package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    // the generated-background path; the felt scene kit (on by default) would skip /image
    @get:org.junit.Rule val sceneKitOff = SceneKitOff()

    @Test
    fun actualStoryEntryKeepsTheServerExtraQuestionThroughBookCreation() = runBlocking {
        val followUp = "쉬고 난 뒤에는 어떻게 돌아왔어?"
        val server = StoryTestServer { path, body ->
            when (path) {
                "/turn" -> {
                    val first = body.optString("asked_slot") == "place"
                    JSONObject().put("judge", JSONObject()
                        .put("reason", "ok")
                        .put("slot_1", if (first) "place" else "extra")
                        .put("value_1", body.getString("utterance"))
                        .put("slot_2", if (first) "problem" else JSONObject.NULL)
                        .put("value_2", if (first) "길을 잃었어" else JSONObject.NULL)
                        .put("next_slot", if (first) "extra" else JSONObject.NULL)
                        .put("story_ready", !first))
                        .put("line", JSONObject().put("ack", "들려줘서 고마워!")
                            .put("question", if (first) followUp else JSONObject.NULL))
                }
                "/story" -> JSONObject().put("scenes", JSONArray().apply {
                    val pages = body.getJSONArray("pages")
                    repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                        .put("kind", pages.getJSONObject(i).getString("kind"))
                        .put("caption", "숲에서 쉬고 엄마와 집에 돌아왔어요.")) }
                })
                else -> JSONObject().put("preset", true)
            }
        }
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        d.s.speed = 0.01
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.PLACE)
            val feeder = launch {
                while (isActive) {
                    if ((d.s.stage as? Stage.CardsRow)?.cards?.any { it.value == "sound:skip" } == true) {
                        d.send(Reply.Tapped("sound:skip", "소리 없이 계속"))
                    } else if (d.s.micEnabled) {
                        d.send(Reply.Spoke(if (d.s.storyNextSlot == "extra") "엄마와 집에 돌아왔어" else "숲에서 쉬었어"))
                    }
                    delay(40)
                }
            }
            withTimeout(10_000) { while (d.s.scene != Scene.BOOK) delay(10) }
            feeder.cancelAndJoin()
            val turns = server.requests.filter { it.first == "/turn" }.map { it.second }
            assertEquals(listOf("place", "extra"), turns.map { it.getString("asked_slot") })
            assertEquals(followUp, turns[1].getString("question"))
            assertEquals("엄마와 집에 돌아왔어", d.s.slots["extra"])
            assertEquals("child", d.s.slotBy["extra"])
            assertEquals("story_ready", d.s.endReason)
            val finishLog = d.s.log.single { it.startsWith("Story conversation finished:") }
            assertTrue("The finish trace must identify filled story slots", finishLog.contains("place"))
            assertTrue("The finish trace must identify the accepted follow-up", finishLog.contains("extra"))
            assertFalse("A collection identity cannot diagnose the child's story", finishLog.contains("SnapshotMapKeySet"))
            val bookRequest = server.requests.single { it.first == "/story" }.second
            assertEquals("엄마와 집에 돌아왔어", bookRequest.getJSONObject("slots").getString("extra"))
            assertTrue(d.s.bookCaption(1).contains("엄마와 집에 돌아왔어요"))
        } finally {
            scope.cancel()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }

    @Test
    fun actualStoryEntryFollowsVerdictsAndStopsAsSoonAsTheServerIsReady() = runBlocking {
        val imageStarted = CountDownLatch(1)
        val nextTurnArrived = CountDownLatch(1)
        val overlapped = AtomicBoolean(false)
        var drawingOffered = false
        val original = Stroke(Color.Blue, listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.7f)), 0.02f)
        val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GREEN)
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        val server = StoryTestServer { path, body ->
            when (path) {
                "/turn" -> {
                    val slot = body.getString("asked_slot")
                    if (slot == "problem") {
                        overlapped.set(imageStarted.await(2, TimeUnit.SECONDS))
                        nextTurnArrived.countDown()
                    }
                    val ready = slot == "reaction"
                    JSONObject().put("judge", JSONObject()
                        .put("reason", "ok").put("slot_1", slot)
                        .put("value_1", body.getString("utterance"))
                        .put("slot_2", if (slot == "place") "newcomer" else JSONObject.NULL)
                        .put("value_2", if (slot == "place") "문어" else JSONObject.NULL)
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
                "/image" -> {
                    imageStarted.countDown()
                    nextTurnArrived.await(2, TimeUnit.SECONDS)
                    JSONObject().put("preset", false).put("png_base64", Base64.getEncoder().encodeToString(png))
                }
                else -> JSONObject().put("preset", true)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        d.s.speed = 0.01
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.PLACE)
            val feeder = launch {
                while (isActive) {
                    if (d.s.stage is Stage.DrawPad) {
                        drawingOffered = true
                        if (d.s.drawing.isEmpty()) d.s.drawing += original
                        d.send(Reply.Tapped("done", "완료"))
                    } else if ((d.s.stage as? Stage.CardsRow)?.cards?.any { it.value == "sound:skip" } == true) {
                        d.send(Reply.Tapped("sound:skip", "소리 없이 계속"))
                    } else if (d.s.micEnabled) d.send(Reply.Spoke("숲에서 친구를 만나 같이 놀았어"))
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
            assertNotNull("the generated background must be available in the book", d.s.storyBackground)
            assertArrayEquals(png, File(d.s.storyBackground!!.removePrefix("local:")).readBytes())
            assertTrue("the next conversation turn must proceed while the image request is pending", overlapped.get())
            assertTrue("a named newcomer needs an original drawing or chosen preset", drawingOffered)
            assertEquals(listOf(original), d.s.drawing.toList())
            assertTrue(d.s.friendArt is Art.ChildDrawing)
            assertFalse("original strokes must stay on the device", server.requests.any { it.second.toString().contains("points") })
        } finally {
            scope.cancel()
            Server.base = null
            Server.liveModes = emptySet()
            server.close()
        }
    }
}
