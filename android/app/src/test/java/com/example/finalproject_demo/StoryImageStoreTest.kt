package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.HeroCreationDraft
import com.example.finalproject_demo.demo.StoryImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryImageStoreTest {
    @Test fun recoveryKeepsAllUnfinishedHeroCandidatesUntilTheDraftIsDiscarded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoryImageStore(context)
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val candidates = List(3) { store.save(png)!! }
        val inProgress = store.save(png)!!
        val orphan = store.save(png)!!
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val d = Director(scope, storyImageStore = store)
            CoopShelf.attach(context, d.s)
            d.s.heroCreationDraft = HeroCreationDraft().apply {
                phase = HeroCreationDraft.Phase.PICK
                generatedTries.addAll(candidates.map { it to null })
                generatedImage = inProgress
            }
            d.recoverStoryImages()
            (candidates + inProgress).forEach {
                assertTrue("An unfinished creation still owns this image", File(it.removePrefix("local:")).exists())
            }
            assertFalse(File(orphan.removePrefix("local:")).exists())
            d.s.heroCreationDraft = null
            d.recoverStoryImages()
            (candidates + inProgress).forEach {
                assertFalse("Discarded candidates must not be retained forever", File(it.removePrefix("local:")).exists())
            }
        } finally { scope.cancel() }
    }

    @Test fun recoveryOnlyDeletesUnreferencedManagedPngs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StoryImageStore(context)
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val kept = store.save(png)!!
        val orphan = store.save(png)!!
        val root = File(kept.removePrefix("local:")).parentFile!!
        val other = File(root, "not-managed.png").apply { writeBytes(png) }
        val nested = File(root, "child/11111111-1111-1111-1111-111111111111.png").apply {
            parentFile!!.mkdirs(); writeBytes(png)
        }
        store.recover(listOf(kept))
        assertTrue(File(kept.removePrefix("local:")).exists())
        assertFalse(File(orphan.removePrefix("local:")).exists())
        assertTrue(other.exists())
        assertTrue(nested.exists())
    }

    @Test
    fun generatedPngCanBeUsedAsBackgroundAfterItIsStored() {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = StoryImageStore(context).save(png)
        assertNotNull(name)
        val file = File(name!!.removePrefix("local:"))
        assertNotNull(BitmapFactory.decodeFile(file.absolutePath))

        val state = DemoState()
        state.generatedBg = true
        state.storyBackground = name
        assertEquals(name, state.bgName)
    }

    @Test
    fun invalidBytesNeverBecomeAnImage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNull(StoryImageStore(context).save("not a png".toByteArray()))
    }
}
