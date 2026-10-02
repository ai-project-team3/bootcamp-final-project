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
import com.example.finalproject_demo.demo.restoreStoryBook
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.ui.HeroAttr
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryBookStoreTest {
    @Test
    fun reopenedBookKeepsItsLocalSoundReferenceWhenStoredAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        val rawBook = JSONObject().put("id", "sound-book").put("title", "친구의 소리")
            .put("themeKey", "sea").put("bgName", "bg_sea").put("soundClipId", "local-clip-1")
            .put("pages", JSONArray().put(JSONObject().put("kind", "TOGETHER").put("caption", "같이 놀았어요.")))
        prefs.edit().putString("books", JSONArray().put(rawBook).toString()).commit()

        val reopened = LocalStoryBookStore(context).load().single()
        LocalStoryBookStore(context).save(reopened)

        val stored = JSONArray(prefs.getString("books", null)).getJSONObject(0)
        assertEquals("local-clip-1", stored.optString("soundClipId"))
    }

    @Test
    fun savedBookKeepsGeneratedHeroAndOriginalChildStrokesAfterAnotherSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val state = DemoState().apply {
            templateKey = "C"
            storyHeroImage = "local:my-generated-hero.png"
            storyHeroRig = "human"
            heroAttr = HeroAttr(hair = "long", shirt = Color.Red, glasses = "square", bottom = "skirt")
            drawing += Stroke(Color.Blue, listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.7f)), 0.02f)
            drawingAspect = 1.6f
            dinoKey = "trex"
            dinoColor = Color.Magenta
            newcomerKind = "문어"
            soundLine = "뽀글뽀글!"
            causeLine = "길을 잃어서"
            useGeneratedStory(template!!.pages.indices.map { "${it + 1}쪽 저장할 문장" })
            m1Result = "solo"
            m2Result = "solo"
        }
        val book = state.completedStoryBook()!!
        LocalStoryBookStore(context).save(book)
        state.drawing.clear()
        state.storyHeroImage = "local:other-session.png"
        val reopened = LocalStoryBookStore(context).load().single()
        assertNotNull("art is part of the saved book, not the current session", reopened.visuals)
        assertEquals("local:my-generated-hero.png", reopened.visuals!!.hero.image)
        assertEquals("human", reopened.visuals!!.hero.rig)
        assertEquals("skirt", reopened.visuals!!.hero.attr.bottom)
        assertEquals(listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.7f)), reopened.visuals!!.drawing.single().pts)
        assertEquals(0.02f, reopened.visuals!!.drawing.single().w, 0.00001f)
        assertEquals(Color.Blue, reopened.visuals!!.drawing.single().color)
        assertEquals(1.6f, reopened.visuals!!.drawingAspect, 0.00001f)
        assertEquals(Color.Magenta, reopened.visuals!!.dinoColor)
        assertEquals(book, reopened)
        val reader = DemoState()
        reader.restoreStoryBook(reopened)
        assertEquals(state.mission1(), reader.mission1())
        assertEquals("뽀글뽀글!", reader.soundLine)
        assertEquals("길을 잃어서", reader.causeLine)
    }

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

    /** 같이 만들기 책은 모드까지 남아 같이 만들기로 다시 열린다. 모드가 없던 예전 저장분은 동화다 (10-02) */
    @Test
    fun aCoopBookKeepsItsModeAndOldBooksStayStories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val coop = SavedStoryBook("c", "같이 지은 오늘 이야기", "space", "bg_firestation",
            listOf(SavedStoryPage(PageKind.DEPART, "소방서에 갔어요")), mode = com.example.finalproject_demo.demo.StoryMode.COOP)
        LocalStoryBookStore(context).save(coop)
        assertEquals(coop, LocalStoryBookStore(context).load().single())

        val old = JSONArray().put(JSONObject().put("id", "o").put("title", "옛 책").put("themeKey", "sea")
            .put("bgName", "bg_sea").put("pages", JSONArray().put(JSONObject().put("kind", "DEPART").put("caption", "문장"))))
        prefs.edit().putString("books", old.toString()).commit()
        assertEquals(com.example.finalproject_demo.demo.StoryMode.STORY, LocalStoryBookStore(context).load().single().mode)
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
