package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.SavedStoryPage
import com.example.finalproject_demo.demo.completedStoryBook
import com.example.finalproject_demo.demo.useGeneratedStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryBookStoreTest {
    @Test
    fun finishedGeneratedBookKeepsEveryFinalCaptionAndPageKind() {
        val s = DemoState()
        s.templateKey = "A"
        s.title = "우리의 책"
        val captions = s.template!!.pages.indices.map { "${it + 1}쪽 이야기" }
        s.useGeneratedStory(captions)
        s.m1Result = "solo"
        s.m2Result = "solo"

        val book = s.completedStoryBook()
        assertNotNull(book)
        assertEquals("우리의 책", book!!.title)
        assertEquals(s.template!!.pages.map { it.kind }, book.pages.map { it.kind })
        val puzzle = book.pages.first { it.kind == PageKind.DRAG }
        assertEquals(true, puzzle.caption.contains("그림 조각을 모두 맞춰"))
    }

    @Test
    fun localBookSurvivesNewStoreInstanceAndKeepsOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val first = SavedStoryBook("one", "첫 책", "sea", "bg_sea", listOf(SavedStoryPage(PageKind.DEPART, "첫 문장")))
        val second = SavedStoryBook("two", "둘째 책", "space", "bg_space", listOf(SavedStoryPage(PageKind.RUB, "미션 후 결과")))
        LocalStoryBookStore(context).save(first)
        LocalStoryBookStore(context).save(second)

        assertEquals(listOf(second, first), LocalStoryBookStore(context).load())
    }

    @Test
    fun incompleteOrOtherModeIsNotSaved() {
        val s = DemoState()
        assertNull(s.completedStoryBook())
        s.templateKey = "A"
        s.mode = com.example.finalproject_demo.demo.StoryMode.DIARY
        assertNull(s.completedStoryBook())
    }
}
