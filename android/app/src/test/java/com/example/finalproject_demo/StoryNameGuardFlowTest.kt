package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryImageStore
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #375 review P1 — through the live conversation: asked the cause, the judge also put the sentence in `newcomer` and
 * its next question was 「<sentence>는 어떻게 생겼어?」. The appearance path used to take that question's head and write the
 * sentence back into the newcomer, and the drawing prompt called it a friend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryNameGuardFlowTest {
    @get:org.junit.Rule val sceneKitOff = SceneKitOff()

    @Test
    fun anAppearanceQuestionNamingTheSentenceNeverRefillsTheNewcomer() = runBlocking {
        val said = "바람이 불어서 나무가 쓰러졌어"
        val server = StoryTestServer { path, body ->
            when (path) {
                "/turn" -> if (body.optString("asked_slot") == "cause") JSONObject()
                    .put("judge", JSONObject().put("reason", "ok")
                        .put("slot_1", "cause").put("value_1", said)
                        .put("slot_2", "newcomer").put("value_2", said)
                        .put("next_slot", "newcomer").put("story_ready", false))
                    .put("line", JSONObject().put("ack", "그랬구나!").put("question", "${said}는 어떻게 생겼어?"))
                else JSONObject()
                    .put("judge", JSONObject().put("reason", "ok")
                        .put("slot_1", "newcomer").put("value_1", body.getString("utterance"))
                        .put("next_slot", JSONObject.NULL).put("story_ready", true))
                    .put("line", JSONObject().put("ack", "좋아!"))
                else -> JSONObject().put("preset", true)
            }
        }
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
        d.s.apply {
            speed = 0.01
            turn = 8
            templateKey = "E"
            slots["place"] = "숲"; slots["problem"] = "길이 막혔어"; slots["reaction"] = "깜짝 놀랐어"
            storyNextSlot = "cause"
            storyServerQuestion = "왜 그랬을까?"
        }
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        val newcomers = mutableListOf<String?>()
        try {
            d.go(Scene.PLACE)
            val feeder = launch {
                var turns = 0
                while (isActive) {
                    newcomers += d.s.slots["newcomer"]
                    val sent = server.requests.count { it.first == "/turn" }
                    if (d.s.micEnabled && sent == turns) {
                        d.send(Reply.Spoke(if (turns == 0) said else "토끼"))
                        turns++
                    }
                    delay(20)
                }
            }
            withTimeout(10_000) { while (server.requests.count { it.first == "/turn" } < 2) delay(10) }
            delay(300)
            feeder.cancelAndJoin()
            val turns = server.requests.filter { it.first == "/turn" }.map { it.second }
            assertEquals("newcomer", turns[1].getString("asked_slot"))
            assertFalse("the question never names the sentence", said in turns[1].getString("question"))
            assertFalse("the sentence never became the newcomer", newcomers.any { it != null && said in it })
            assertEquals("토끼", d.s.slots["newcomer"])
        } finally {
            scope.cancel()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
