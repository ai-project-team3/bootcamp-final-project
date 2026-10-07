package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.LocalWorldStyle
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedDiaryBook
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.WorldStyle
import com.example.finalproject_demo.demo.diaryBookFromJson
import com.example.finalproject_demo.demo.toJson
import com.example.finalproject_demo.demo.withSavedDiary
import com.example.finalproject_demo.ui.HazedPieces
import com.example.finalproject_demo.ui.assetId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A saved book keeps its own art style (#253 review · 민우): a felt book read while a crayon book is being made stays
 * felt, a crayon book stays crayon after an app restart, and a far piece's hazed copy never crosses styles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookStyleTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @After fun reset() { WorldStyle.current = "felt"; WorldStyle.reading = null }

    private fun book(id: String, style: String) = SavedStoryBook(id, "책 $id", "space", "bg_space",
        listOf(SavedStoryPage(PageKind.DEPART, "떠났어요")), madeAt = "2026-10-07", artStyle = style)

    @Test fun storyBooksKeepTheirStyleAcrossARestart() {
        ctx.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        LocalStoryBookStore(ctx).apply { save(book("felt1", "felt")); save(book("crayon1", "crayon")) }
        val reloaded = LocalStoryBookStore(ctx).load().associate { it.id to it.artStyle }      // a new store = the app started again
        assertEquals(mapOf("felt1" to "felt", "crayon1" to "crayon"), reloaded)
    }

    @Test fun aBookSavedBefore1007ReadsAsFelt() {
        val raw = SavedDiaryBook("d1", "일기", "2026-10-01", 1, DiaryBookInput(mapOf("place" to "공원")), emptyList())
        val json = raw.toJson().apply { remove("artStyle") }
        assertEquals("felt", diaryBookFromJson(json) { _, _ -> null }.artStyle)
        val crayon = raw.copy(artStyle = "crayon")
        assertEquals("crayon", diaryBookFromJson(JSONObject(crayon.toJson().toString())) { _, _ -> null }.artStyle)
    }

    @Test fun aSettingChangedMidBookAppliesFromTheNextBook() {
        val s = DemoState()
        s.artStyle = "crayon"; s.resetStory()
        assertEquals("crayon", s.bookStyle)
        s.artStyle = "felt"                       // the parent changes it during this book
        assertEquals("crayon", s.bookStyle)
        assertEquals("crayon", WorldStyle.current)
        s.resetStory()
        assertEquals("felt", s.bookStyle)
    }

    /**
     * The shelf reader resolves with the book's own style, whatever the current book is. `kit_forest_bird_fly` is a real
     * bundled picture, so the style "fly" stands in for a baked style: `<name>_<style>` exists for it.
     */
    @Test fun aSavedBookIsDrawnInItsOwnStyle() {
        WorldStyle.current = "fly"
        var inFelt = -1; var inStyle = -1; var outside = -1
        compose.setContent {
            CompositionLocalProvider(LocalWorldStyle provides "felt") { inFelt = assetId("kit_forest_bird") }
            CompositionLocalProvider(LocalWorldStyle provides "fly") { inStyle = assetId("kit_forest_bird") }
            outside = assetId("kit_forest_bird")
        }
        compose.waitForIdle()
        @Suppress("DiscouragedApi") fun id(n: String) = ctx.resources.getIdentifier(n, "drawable", ctx.packageName)
        assertEquals("a felt book under a styled current book stays felt", id("kit_forest_bird"), inFelt)
        assertEquals(id("kit_forest_bird_fly"), inStyle)
        assertEquals("outside a saved book: the book being made", id("kit_forest_bird_fly"), outside)
    }

    @Test fun aDiaryRereadUsesItsStyleAndPutsTheCurrentOneBack() = runBlocking {
        val s = DemoState()
        WorldStyle.current = "crayon"
        val diary = SavedDiaryBook("d2", "일기", "2026-10-07", 1, DiaryBookInput(mapOf("place" to "공원")), emptyList(), artStyle = "felt")
        val during = s.withSavedDiary(diary) { WorldStyle.active }
        assertEquals("felt", during)
        assertNull(WorldStyle.reading)
        assertEquals("crayon", WorldStyle.active)
    }

    @Test fun aHazedFarPieceIsNeverReusedAcrossStyles() {
        fun pic(c: Int) = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }.asImageBitmap()
        val felt = pic(0xFF00FF00.toInt())
        val crayon = pic(0xFFFF0000.toInt())
        val sky = Color(0xFFBBDDFF)
        // felt → crayon and crayon → felt: the same piece name, two pictures, two hazed copies
        val a = HazedPieces.of(felt, 0.5f, sky)
        val b = HazedPieces.of(crayon, 0.5f, sky)
        assertNotSame(a, b)
        assertEquals(HazedPieces.of(crayon, 0.5f, sky).getPixel(4, 4), b.getPixel(4, 4))
        assertSame("the same picture is made once", a, HazedPieces.of(felt, 0.5f, sky))
    }
}
