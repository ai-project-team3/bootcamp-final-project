package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryPendingBackgroundTest {
    // the generated-background path; the felt scene kit (on by default) would skip /image
    @get:org.junit.Rule val sceneKitOff = SceneKitOff()

    @Test fun firstQuestionDoesNotShowAPlaceTheChildHasNotChosen() = runBlocking {
        withLiveStory { d, _ ->
            await { d.s.micEnabled }
            assertTrue("Before a place is chosen, use the neutral stage", d.s.stage is Stage.Show)
        }
    }

    @Test fun pendingImageUsesNeutralStageAndArrivalUpdatesTheSameQuestion() = runBlocking {
        withLiveStory { d, fixture ->
            answerPlace(d, fixture)
            await { fixture.imageStarted.count == 0L && d.s.micEnabled && d.s.turn == 1 }
            assertTrue("Pending images must not display the snow fallback", d.s.stage is Stage.Show)

            fixture.releaseImage.countDown()
            await { d.s.storyBackground != null }
            assertTrue("The image should appear without another answer", d.s.stage is Stage.World)
            assertEquals(1, d.s.turn)
        }
    }

    @Test fun backgroundArrivalDoesNotReplaceTheChildsDrawingPad() = runBlocking {
        withLiveStory(newcomer = true) { d, fixture ->
            answerPlace(d, fixture)
            await { fixture.imageStarted.count == 0L && d.s.stage is Stage.DrawPad }
            val drawingStage = d.s.stage

            fixture.releaseImage.countDown()
            await { d.s.storyBackground != null }
            assertSame("Receiving a background must not interrupt drawing", drawingStage, d.s.stage)
        }
    }

    @Test fun finishingTheConversationStillUsesNeutralStageWhileTheImageIsPending() = runBlocking {
        withLiveStory(ready = true) { d, fixture ->
            d.s.storySoundAttempted = true
            answerPlace(d, fixture)
            await {
                fixture.imageStarted.count == 0L &&
                    ((d.s.stage as? Stage.Making)?.label == "이야기 그림을 마무리하는 중…" ||
                        (d.s.stage as? Stage.Show)?.caption == "이야기 그림을 마무리하는 중…")
            }
            assertTrue("The final pending stage must not render the snow fallback", d.s.stage is Stage.Show)
        }
    }

    private suspend fun answerPlace(d: Director, fixture: HoldingBackground) = coroutineScope {
        val feeder = launch {
            while (fixture.server.requests.none { it.first == "/turn" }) {
                if (d.s.micEnabled) d.send(Reply.Spoke("바닷속 연구소로 갈래"))
                delay(20)
            }
        }
        try { withTimeout(5_000) { feeder.join() } } finally { feeder.cancelAndJoin() }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(5)
    }

    private suspend fun withLiveStory(
        newcomer: Boolean = false, ready: Boolean = false,
        check: suspend (Director, HoldingBackground) -> Unit,
    ) = coroutineScope {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val fixture = HoldingBackground(newcomer, ready)
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            Server.base = fixture.server.base
            Server.liveModes = setOf(StoryMode.STORY)
            val d = Director(scope, LocalStoryBookStore(context), StoryImageStore(context))
            d.s.speed = 0.01
            d.s.timerOn = false
            d.go(Scene.PLACE)
            check(d, fixture)
        } finally {
            fixture.releaseImage.countDown()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            fixture.server.close()
        }
    }

    private class HoldingBackground(newcomer: Boolean, ready: Boolean) {
        val imageStarted = CountDownLatch(1)
        val releaseImage = CountDownLatch(1)
        private val png = ByteArrayOutputStream().apply output@{
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GREEN)
                compress(Bitmap.CompressFormat.PNG, 100, this@output)
            }
        }.toByteArray()
        val server = StoryTestServer { path, _ ->
            when (path) {
                "/turn" -> JSONObject().put("judge", JSONObject()
                    .put("reason", "ok").put("slot_1", "place").put("value_1", "바닷속 연구소")
                    .put("slot_2", if (newcomer) "newcomer" else JSONObject.NULL)
                    .put("value_2", if (newcomer) "문어" else JSONObject.NULL)
                    .put("next_slot", if (ready) JSONObject.NULL else "problem")
                    .put("story_ready", ready))
                    .put("line", JSONObject().put("ack", "그곳으로 가 보자!")
                        .put("question", if (ready) JSONObject.NULL else "무슨 일이 생겼어?"))
                "/image" -> {
                    imageStarted.countDown()
                    releaseImage.await(10, TimeUnit.SECONDS)
                    JSONObject().put("preset", false)
                        .put("png_base64", Base64.getEncoder().encodeToString(png))
                }
                else -> JSONObject().put("preset", true)
            }
        }
    }
}
