package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.sound.ChildSound
import java.nio.file.Files
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
class StoryLiveSoundTurnTest {
    @Test fun skippingSoundReachesTheServerAndCanFinishAfterAConnectionRetry() = runBlocking {
        verifySoundFinish(record = false, failFirst = true)
    }

    @Test fun acceptedSoundSendsOnlyAnActivityDescriptionWithoutInventingSpeech() = runBlocking {
        verifySoundFinish(record = true, failFirst = false)
    }

    private suspend fun CoroutineScope.verifySoundFinish(record: Boolean, failFirst: Boolean) {
        val originalBase = Server.base
        val originalModes = Server.liveModes
        val originalRoot = ChildSound.root
        val originalCapture = ChildSound.capture
        val folder = Files.createTempDirectory("live_story_sound").toFile()
        var soundCalls = 0
        var retryShown = false
        ChildSound.root = folder
        ChildSound.capture = { ShortArray(ChildSound.RATE / 2) { if (it % 2 == 0) 5000 else -5000 } }
        val server = StoryTestServer { path, body ->
            when (path) {
                "/turn" -> {
                    val sound = body.optString("asked_slot") == "sound"
                    if (sound) soundCalls++
                    if (sound && failFirst && soundCalls == 1) JSONObject()
                    else JSONObject().put("judge", JSONObject().put("reason", "ok")
                        .put("slot_1", if (sound) JSONObject.NULL else "place")
                        .put("value_1", if (sound) JSONObject.NULL else "숲")
                        .put("slot_2", if (sound) JSONObject.NULL else "problem")
                        .put("value_2", if (sound) JSONObject.NULL else "친구와 놀았어")
                        .put("no_longer_needed", if (sound && !record) "sound" else JSONObject.NULL)
                        .put("next_slot", if (sound) JSONObject.NULL else "sound")
                        .put("story_ready", sound))
                        .put("line", JSONObject().put("ack", "좋아!")
                            .put("question", if (sound) JSONObject.NULL else "친구의 소리를 만들어 볼까?"))
                }
                "/story" -> JSONObject().put("scenes", JSONArray().apply {
                    val pages = body.getJSONArray("pages")
                    repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                        .put("kind", pages.getJSONObject(i).getString("kind"))
                        .put("caption", "친구와 놀았어요.")) }
                })
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
            val feeder = scope.launch {
                while (isActive) {
                    val choices = (d.s.stage as? Stage.CardsRow)?.cards?.map { it.value }.orEmpty()
                    when {
                        d.s.stage is Stage.Confirm -> {
                            retryShown = true
                            d.send(Reply.Tapped("ok", "다시 연결"))
                        }
                        "sound:ok" in choices -> d.send(Reply.Tapped("sound:ok", "이 소리로"))
                        "sound:record" in choices -> d.send(Reply.Tapped(
                            if (record) "sound:record" else "sound:skip", "선택"))
                        d.s.micEnabled -> d.send(Reply.Spoke("숲에서 친구와 놀았어"))
                    }
                    delay(30)
                }
            }
            withTimeout(8_000) { while (d.s.scene != Scene.BOOK) delay(10) }
            feeder.cancelAndJoin()
            val turns = server.requests.filter { it.first == "/turn" }.map { it.second }
            assertEquals(if (failFirst) listOf("place", "sound", "sound") else listOf("place", "sound"),
                turns.map { it.getString("asked_slot") })
            val choice = turns.last()
            assertTrue(choice.getString("utterance").contains(if (record) "직접" else "소리 없이"))
            assertFalse("audio and local paths must stay on the device", choice.toString().contains(folder.path))
            assertFalse(choice.has("audio"))
            assertFalse(choice.has("png_base64"))
            assertTrue("the session names go along so the server can hide them from Jev (10-06)", choice.has("names"))
            assertEquals("story_ready", d.s.endReason)
            assertEquals(1, d.s.notes.size)
            assertEquals("숲에서 친구와 놀았어", d.s.notes.single().a)
            assertEquals(1, d.s.modeVoice)
            assertEquals(1, d.s.turn)
            assertEquals(failFirst, retryShown)
            if (record) assertNotNull(d.s.storySoundClip)
            else {
                assertNull(d.s.storySoundClip)
                assertNull(d.s.slots["sound"])
            }
        } finally {
            scope.cancel()
            server.close()
            Server.base = originalBase
            Server.liveModes = originalModes
            ChildSound.root = originalRoot
            ChildSound.capture = originalCapture
            folder.deleteRecursively()
        }
    }
}
