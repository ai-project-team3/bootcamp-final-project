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
}
