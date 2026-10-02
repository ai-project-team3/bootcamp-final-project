package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.HeroAttr
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryImageRecoveryTest {
    private fun png() = ByteArrayOutputStream().also {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    private fun file(ref: String) = File(ref.removePrefix("local:"))

    @Test fun startupKeepsBookBackgroundAndHeroButRemovesAbandonedImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val images = StoryImageStore(context)
        val background = images.save(png())!!
        val hero = images.save(png())!!
        val orphan = images.save(png())!!
        val state = DemoState().apply { templateKey = "C"; storyBackground = background; storyHeroImage = hero }
        val books = LocalStoryBookStore(context)
        books.save(state.completedStoryBook()!!)
        val scope = CoroutineScope(SupervisorJob())
        try {
            Director(scope, books, images)
            assertTrue(file(background).exists())
            assertTrue(file(hero).exists())
            assertFalse("an unfinished session must not leave a PNG forever", file(orphan).exists())
        } finally { scope.cancel() }
    }

    @Test fun savingProtectsCurrentImagesAndBestiaryAndNeverTouchesOtherDirectories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val images = StoryImageStore(context)
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, LocalStoryBookStore(context), images)
            val background = images.save(png())!!
            val hero = images.save(png())!!
            val bestiary = images.save(png())!!
            val orphan = images.save(png())!!
            val unrelated = File(context.filesDir, "not-a-story.png").apply { writeBytes(png()) }
            d.s.templateKey = "C"
            d.s.storyBackground = background
            d.s.storyHeroImage = hero
            d.s.heroes += Hero("도감 친구", HeroAttr(), bestiary)
            assertTrue(d.saveFinishedStory())
            listOf(background, hero, bestiary).forEach { assertTrue(file(it).exists()) }
            assertTrue(unrelated.exists())
            assertFalse(file(orphan).exists())
        } finally { scope.cancel() }
    }

    @Test fun corruptBookMetadataNeverTriggersImageDeletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().putString("books", "broken-json").commit()
        val images = StoryImageStore(context)
        val retained = images.save(png())!!
        val scope = CoroutineScope(SupervisorJob())
        try {
            Director(scope, LocalStoryBookStore(context), images)
            assertTrue("unreadable metadata is not proof that a picture is orphaned", file(retained).exists())
        } finally { scope.cancel() }
    }

    @Test fun aNewSaveCannotOverwriteCorruptMetadataAndEraseItsPictures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().putString("books", "broken-json").commit()
        val images = StoryImageStore(context)
        val picture = images.save(png())!!
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, LocalStoryBookStore(context), images)
            d.s.templateKey = "C"
            assertFalse("unreadable metadata must not be replaced with an empty library", d.saveFinishedStory())
            assertEquals("broken-json", prefs.getString("books", null))
            assertTrue(file(picture).exists())
        } finally { scope.cancel() }
    }

    @Test fun aNewSaveCannotEraseAnUnsupportedSavedHeroVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val images = StoryImageStore(context)
        val hero = images.save(png())!!
        val state = DemoState().apply { templateKey = "C"; storyHeroImage = hero }
        val books = LocalStoryBookStore(context)
        books.save(state.completedStoryBook()!!)
        val raw = JSONArray(prefs.getString("books", null))
        raw.getJSONObject(0).getJSONObject("visuals").put("version", 2)
        val original = raw.toString()
        prefs.edit().putString("books", original).commit()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, books, images)
            d.s.templateKey = "C"
            assertFalse("a future art version must not be silently discarded", d.saveFinishedStory())
            assertEquals(original, prefs.getString("books", null))
            assertTrue(file(hero).exists())
        } finally { scope.cancel() }
    }

    @Test fun malformedArtStructureDisablesStartupDeletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val images = StoryImageStore(context)
        val hero = images.save(png())!!
        val state = DemoState().apply { templateKey = "C"; storyHeroImage = hero }
        val books = LocalStoryBookStore(context)
        books.save(state.completedStoryBook()!!)
        val original = prefs.getString("books", null)!!
        val scope = CoroutineScope(SupervisorJob())
        try {
            for (field in listOf("visuals", "hero", "image")) {
                val raw = JSONArray(original)
                val entry = raw.getJSONObject(0)
                when (field) {
                    "visuals" -> entry.put("visuals", "not-an-object")
                    "hero" -> entry.getJSONObject("visuals").put("hero", "not-an-object")
                    "image" -> entry.getJSONObject("visuals").getJSONObject("hero").put("image", JSONArray())
                }
                val damaged = raw.toString()
                prefs.edit().putString("books", damaged).commit()
                val d = Director(scope, books, images)
                assertTrue("unknown $field references are not evidence of an orphan", file(hero).exists())
                d.s.templateKey = "C"
                assertFalse(d.saveFinishedStory())
                assertEquals(damaged, prefs.getString("books", null))
                assertTrue(file(hero).exists())
            }
        } finally { scope.cancel() }
    }

    @Test fun replacementCannotDropAnUnreadableEntryAndItsPicture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val images = StoryImageStore(context)
        val picture = images.save(png())!!
        val books = LocalStoryBookStore(context)
        repeat(12) { i -> books.save(SavedStoryBook("book-$i", "동화 $i", "sea", "bg_sea",
            listOf(SavedStoryPage(PageKind.TOGETHER, "같이 놀았어요.")))) }
        val raw = JSONArray(prefs.getString("books", null))
        val unreadable = JSONObject(raw.getJSONObject(0).toString()).put("id", "future").put("bgName", picture)
        unreadable.getJSONArray("pages").getJSONObject(0).put("kind", "FUTURE_PAGE")
        raw.put(unreadable)
        val original = raw.toString()
        prefs.edit().putString("books", original).commit()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val d = Director(scope, books, images)
            d.s.templateKey = "C"
            assertFalse(d.saveFinishedStory("book-5"))
            assertNotNull(d.savedStory("book-5"))
            assertEquals(original, prefs.getString("books", null))
            assertTrue(file(picture).exists())
        } finally { scope.cancel() }
    }
}
