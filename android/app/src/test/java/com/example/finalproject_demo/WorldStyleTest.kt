package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryImageStore
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.WorldStyle
import com.example.finalproject_demo.demo.scene.SceneKits
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 책의 그림체 (10-07 종훈 · 크레용 아이 그림체 · 결정 27) — 다음 책부터, 세계 그림만, 구운 그림이 없으면 펠트.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorldStyleTest {
    @After fun felt() { WorldStyle.current = "felt"; WorldStyle.has = { false } }

    @Test fun aWorldPictureInTheBooksStyleWhenBundledElseFelt() {
        val bundled = setOf("kit_park_slide_crayon", "bg_snow_crayon")
        val has: (String) -> Boolean = { it in bundled }
        assertEquals("kit_park_slide_crayon", WorldStyle.resolve("kit_park_slide", "crayon", has))
        assertEquals("not baked yet → felt", "kit_park_swing", WorldStyle.resolve("kit_park_swing", "crayon", has))
        assertEquals("felt books never look", "kit_park_slide", WorldStyle.resolve("kit_park_slide", "felt", has))
        assertEquals("the 도감 doll stays felt", "body_girl", WorldStyle.resolve("body_girl", "crayon") { true })
        assertEquals("Otto stays felt", "otto_pose_think", WorldStyle.resolve("otto_pose_think", "crayon") { true })
        assertEquals("the room stays felt", "room_bg", WorldStyle.resolve("room_bg", "crayon") { true })
        assertEquals("a styled name is not styled twice", "bg_snow_crayon", WorldStyle.resolve("bg_snow_crayon", "crayon", has))
        assertEquals("the same world list as tools/rebake_style.py", "rocket_crayon", WorldStyle.resolve("rocket", "crayon") { true })
        assertEquals("the shelf picture is not the world", "bg_shelf", WorldStyle.resolve("bg_shelf", "crayon") { true })
    }

    @Test fun aKitIsUsedInAStyleOnlyWhenEveryPieceIsBaked() {
        val park = SceneKits.all.getValue("park")
        val pieces = park.pieces.map { it.res }.toSet()
        assertTrue(WorldStyle.kitReady(park, "felt") { false })
        assertFalse(WorldStyle.kitReady(park, "crayon") { false })
        assertFalse("one felt piece left", WorldStyle.kitReady(park, "crayon") { n -> n.removeSuffix("_crayon") in pieces.drop(1) })
        assertTrue(WorldStyle.kitReady(park, "crayon") { n -> n.removeSuffix("_crayon") in pieces })
    }

    @Test fun theStyleIsTakenWhenABookStartsAndKeptForThatBook() {
        val s = DemoState()
        s.artStyle = "crayon"
        s.resetStory()                                  // a book starts
        assertEquals("crayon", s.bookStyle)
        assertEquals("crayon", WorldStyle.current)
        s.artStyle = "felt"                             // the parent changes it during the book
        assertEquals("this book stays crayon", "crayon", s.bookStyle)
        s.resetStory()                                  // the next book
        assertEquals("felt", s.bookStyle)
        assertEquals("felt", WorldStyle.current)
    }

    /** No crayon kit pieces are bundled yet → a crayon story asks /image for a crayon background instead of the felt kit */
    @Test fun aCrayonBookWithoutCrayonPiecesDrawsItsBackgroundInCrayon() = runBlocking {
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
            d.s.artStyle = "crayon"
            d.s.resetStory()
            d.go(Scene.PLACE)
            withTimeout(10_000) {
                while (server.requests.none { it.first == "/turn" }) {
                    if (d.s.micEnabled) d.send(Reply.Spoke("놀이터에 갔어"))
                    delay(20)
                }
                while (server.requests.none { it.first == "/image" }) delay(20)
            }
            assertNull("no felt kit under a crayon book", d.s.sceneKit)
            val image = server.requests.first { it.first == "/image" }.second
            assertEquals("crayon", image.getString("style"))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }

    /**
     * The crayon world pictures from #291 · #307 (`res/drawable-nodpi/<name>_crayon.webp`) resolve through the real
     * resource lookup: each has a felt original, is a world picture, and every scene kit is complete in crayon
     */
    @Test
    fun theBundledCrayonPicturesResolve() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DiscouragedApi")
        val has: (String) -> Boolean = { n -> ctx.resources.getIdentifier(n, "drawable", ctx.packageName) != 0 }
        val crayon = R.drawable::class.java.fields.map { it.name }.filter { it.endsWith("_crayon") } - setOf("gift_crayon", "style_crayon")
        assertTrue("${crayon.size} crayon pictures", crayon.size >= 202)
        crayon.forEach { c ->
            val felt = c.removeSuffix("_crayon")
            assertTrue("$felt has a felt original", has(felt))
            assertTrue("$felt is a world picture", WorldStyle.isWorld(felt))
            assertEquals(c, WorldStyle.resolve(felt, "crayon", has))
            assertEquals(felt, WorldStyle.resolve(felt, "felt", has))
        }
        SceneKits.all.values.forEach { assertTrue("${it.key} ready in crayon", WorldStyle.kitReady(it, "crayon", has)) }
    }
}
