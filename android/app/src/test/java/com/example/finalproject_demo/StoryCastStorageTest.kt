package com.example.finalproject_demo

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryCastStorageTest {
    private fun visuals() = DemoState().apply {
        templateKey = "A"
        drawing.add(Stroke(Color.Red, listOf(Offset(.1f, .2f), Offset(.3f, .4f)), 4f))
        generatedFriend = GeneratedFriend("토끼", "local:/friend.png", "quad")
    }.captureStoryVisuals()

    private fun withCast() = visuals().toJson().put("characters", JSONArray().put(
        JSONObject().put("role", "problem").put("words", "괴물")
            .put("image", "local:/monster.png").put("rig", "biped")
    ))

    @Test fun additionalCharacterAndChildDrawingSurviveReopening() {
        val saved = storyVisualsFromJson(withCast())
        assertTrue(saved.images.containsAll(listOf("local:/friend.png", "local:/monster.png")))
        val book = SavedStoryBook("cast", "이야기", "dino", "bg_snow", listOf(SavedStoryPage(PageKind.TALK, "괴물이 왔어")), saved)
        val restored = DemoState()
        assertTrue(restored.restoreStoryBook(book))
        assertEquals(saved.drawing, restored.drawing.toList())
        assertEquals(saved.images.toSet(), restored.captureStoryVisuals().images.toSet())
        assertEquals(saved, storyVisualsFromJson(saved.toJson()))
    }

    @Test fun imageCleanupRetainsEveryCharacterAndStopsOnMalformedReferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        val entry = JSONObject().put("bgName", "local:/background.png").put("visuals", withCast())
        try {
            prefs.edit().putString("books", JSONArray().put(entry).toString()).commit()
            assertTrue(LocalStoryBookStore(context).imageReferences()!!.contains("local:/monster.png"))
            entry.getJSONObject("visuals").getJSONArray("characters").getJSONObject(0).put("image", 42)
            prefs.edit().putString("books", JSONArray().put(entry).toString()).commit()
            assertNull("An unreadable cast must prevent destructive image cleanup", LocalStoryBookStore(context).imageReferences())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun oldSingleFriendBookRemainsReadable() {
        val old = visuals().toJson().apply { remove("characters") }
        val restored = storyVisualsFromJson(old)
        assertEquals("토끼", restored.friend?.words)
        assertTrue("local:/friend.png" in restored.images)
    }
}
