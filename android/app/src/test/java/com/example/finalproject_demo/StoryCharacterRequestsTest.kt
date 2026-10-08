package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryCharacterRequestsTest {
    private suspend fun requests(setup: DemoState.() -> Unit): List<String> = coroutineScope {
        val base = Server.base
        val modes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", true) }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.STORY)
            val d = Director(scope)
            d.s.mode = StoryMode.STORY
            d.s.slots["newcomer"] = "토끼"
            d.s.newcomerKind = "토끼"
            d.s.setup()
            coroutineScope { d.drawFriend() }
            server.requests.filter { it.first == "/image" }.map { it.second.getString("description") }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            server.close(); Server.base = base; Server.liveModes = modes
        }
    }

    @Test fun childDrawingChoiceMustHappenBeforeAnyNewcomerImageRequest() = runBlocking {
        assertEquals(emptyList<String>(), requests { })
    }

    @Test fun childDrawnFriendDoesNotSuppressADifferentProblemCharacter() = runBlocking {
        assertEquals(listOf("괴물"), requests {
            drawing += Stroke(Color.Red, listOf(Offset(.1f, .2f)), 4f)
            done += "draw"
            slots["problem"] = "괴물이 길을 막았어"
            slotBy["problem"] = "child"
        })
    }

    @Test fun theCharacterInTheChildDrawingIsNeverRequestedAgainAsTheProblem() = runBlocking {
        assertEquals(emptyList<String>(), requests {
            drawing += Stroke(Color.Red, listOf(Offset(.1f, .2f)), 4f)
            done += "draw"
            slots["problem"] = "토끼가 길을 막았어"
            slotBy["problem"] = "child"
        })
    }

    @Test fun successfulProblemImageIsStoredOnceAndDoesNotReplaceTheChildDrawing() = runBlocking {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", false).put("rig", "blob")
            .put("png_base64", Base64.getEncoder().encodeToString(png)) }
        withServer(server) { d ->
            d.s.slots["problem"] = "괴물이 길을 막았어"; d.s.slotBy["problem"] = "child"
            val strokes = listOf(Stroke(Color.Red, listOf(Offset(.1f, .2f)), 4f))
            d.s.drawing.addAll(strokes)
            d.s.done += "draw"
            coroutineScope { d.drawFriend(); d.drawFriend() }
            coroutineScope { d.drawFriend() }
            assertEquals(listOf("괴물"), server.requests.filter { it.first == "/image" }.map { it.second.getString("description") })
            val actor = d.s.generatedCharacters.single()
            assertEquals("problem", actor.role)
            assertEquals("blob", actor.rig)
            assertArrayEquals(png, File(actor.image.removePrefix("local:")).readBytes())
            assertEquals(strokes, d.s.drawing.toList())
            assertTrue(d.s.friendArt is Art.ChildDrawing)
            assertEquals(actor.image, (d.s.storyProblemArt() as Art.Img).name)
        }
    }

    @Test fun actualDrawingFlowSendsNoRequestForTheDrawnFriend() = runBlocking {
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", true) }
        withServer(server) { d ->
            val flow = launch { d.drawFriend(); d.prepareStoryFriendDrawing(); d.drawFriend() }
            withTimeout(3_000) { while (d.s.stage !is Stage.DrawPad || d.s.buttons.isEmpty()) delay(5) }
            assertTrue(server.requests.none { it.first == "/image" })
            d.s.drawing += Stroke(Color.Blue, listOf(Offset(.2f, .2f)), 4f)
            d.send(Reply.Tapped("done", "완료"))
            withTimeout(3_000) { flow.join() }
            assertTrue(server.requests.none { it.first == "/image" })
            assertTrue("draw" in d.s.done)
        }
    }

    @Test fun aLateResponseForAnUndoneProblemNeverBecomesAnActor() = runBlocking {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val release = CountDownLatch(1)
        val server = StoryTestServer { _, _ ->
            release.await(3, TimeUnit.SECONDS)
            JSONObject().put("preset", false).put("rig", "blob").put("png_base64", Base64.getEncoder().encodeToString(png))
        }
        try {
            withServer(server) { d ->
                d.s.slots["problem"] = "괴물이 왔어"; d.s.slotBy["problem"] = "child"
                val pending = launch { coroutineScope { d.drawFriend() } }
                withTimeout(3_000) { while (server.requests.none { it.first == "/image" }) delay(5) }
                d.s.slots.remove("problem")
                release.countDown()
                pending.join()
                assertTrue(d.s.generatedCharacters.isEmpty())
                assertTrue(d.s.characterRequests.isEmpty())
                assertTrue("Redoing an undone actor may request its image again", d.s.characterAttempts.isEmpty())
            }
        } finally { release.countDown() }
    }

    @Test fun returningToTheFirstActorWhileTwoRequestsArePendingKeepsItsResult() = runBlocking {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val release = CountDownLatch(1)
        val server = StoryTestServer { _, _ ->
            release.await(5, TimeUnit.SECONDS)
            JSONObject().put("preset", false).put("rig", "blob").put("png_base64", Base64.getEncoder().encodeToString(png))
        }
        try {
            withServer(server) { d ->
                d.s.slotBy["problem"] = "child"
                coroutineScope {
                    d.s.slots["problem"] = "괴물이 왔어"
                    d.drawFriend()
                    withTimeout(3_000) { while (server.requests.count { it.first == "/image" } < 1) delay(5) }
                    d.s.slots["problem"] = "사자가 왔어"
                    d.drawFriend()
                    withTimeout(3_000) { while (server.requests.count { it.first == "/image" } < 2) delay(5) }
                    d.s.slots["problem"] = "괴물이 왔어"
                    d.drawFriend()
                    release.countDown()
                }
                assertEquals("괴물", d.s.generatedCharacters.single().words)
                assertEquals(2, server.requests.count { it.first == "/image" })
            }
        } finally { release.countDown() }
    }

    @Test fun coopStillRetriesAFailedCompanionRequestOnTheNextTurn() = runBlocking {
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", true) }
        withServer(server) { d ->
            Server.liveModes = setOf(StoryMode.COOP)
            d.s.mode = StoryMode.COOP; d.s.companionKind = "강아지"
            coroutineScope { d.drawFriend() }
            coroutineScope { d.drawFriend() }
            assertEquals(2, server.requests.count { it.first == "/image" })
        }
    }

    @Test fun returningToAnEarlierCompletedActorDoesNotRemainSuppressed() = runBlocking {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", false).put("rig", "blob")
            .put("png_base64", Base64.getEncoder().encodeToString(png)) }
        withServer(server) { d ->
            d.s.slotBy["problem"] = "child"
            for (actor in listOf("괴물", "사자", "괴물")) {
                d.s.slots["problem"] = "${actor}가 길을 막았어"
                coroutineScope { d.drawFriend() }
                assertEquals(actor, d.s.storyProblemDoll()?.words)
            }
        }
    }

    private suspend fun withServer(server: StoryTestServer, check: suspend CoroutineScope.(Director) -> Unit) = coroutineScope {
        val base = Server.base
        val modes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            Server.base = server.base; Server.liveModes = setOf(StoryMode.STORY)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val d = Director(scope, storyImageStore = StoryImageStore(context))
            d.s.mode = StoryMode.STORY; d.s.speed = .001; d.s.timerOn = false
            d.s.slots["newcomer"] = "토끼"; d.s.newcomerKind = "토끼"
            check(d)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            server.close(); Server.base = base; Server.liveModes = modes
        }
    }
}
