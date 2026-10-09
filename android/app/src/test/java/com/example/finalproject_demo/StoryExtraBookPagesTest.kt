package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryExtraBookPagesTest {
    @Test
    fun expandedBookReopensWithItsSavedPageKindsAndFinalCaptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
        val state = DemoState().apply {
            templateKey = "E"
            Server.SLOTS.filter { it != "title" }.forEach { slots[it] = "내용 $it" }
            prepareStoryBookPages()
            useGeneratedStory(List(8) { "${it + 1}쪽의 저장된 문장" })
            m1Result = "solo"
            m2Result = "solo"
        }
        val book = state.completedStoryBook()!!
        LocalStoryBookStore(context).save(book)
        val restored = DemoState()
        assertTrue(restored.restoreStoryBook(LocalStoryBookStore(context).load().single()))
        assertEquals("E", restored.templateKey)
        assertEquals(book.pages.map { it.kind }, (1..restored.pageCount).map(restored::pageKind))
        assertEquals(book.pages.map { it.caption }, (1..restored.pageCount).map(restored::bookCaption))
        assertEquals(book.pages, restored.completedStoryBook()!!.pages)
    }

    @Test
    fun finalContentAddsAtMostTwoPagesWithoutMovingMissionsInAnyTemplate() {
        for (key in listOf("A", "B", "C", "D", "E", "F", "G")) {
            val state = DemoState().apply {
                templateKey = key
                Server.SLOTS.filter { it != "title" }.forEach { slots[it] = "내용 $it" }
            }
            val original = state.template!!.pages.map { it.kind }
            state.prepareStoryBookPages()
            state.prepareStoryBookPages()
            assertEquals(key, state.templateKey)
            assertEquals(8, state.pageCount)
            for (mission in listOf(PageKind.RUB, PageKind.DRAG)) {
                assertEquals(original.indexOf(mission), state.bookPages.indexOfFirst { it.kind == mission })
            }
            assertEquals(original.dropLast(1), state.bookPages.take(original.size - 1).map { it.kind })
            assertEquals(PageKind.TOGETHER, state.pageKind(8))
        }
    }

    @Test
    fun shortConversationAndRecordedSoundNoteDoNotAddPagesButSeventhContentSlotDoes() {
        val state = DemoState().apply {
            templateKey = "E"
            turn = 20
            listOf("place", "problem", "newcomer", "name", "cause", "solution")
                .forEach { slots[it] = "내용 $it" }
            slots["title"] = "우리 책"
            slots["sound"] = RECORDED_SOUND_NOTE
            slots["reaction"] = "  "
        }
        state.prepareStoryBookPages()
        assertEquals(6, state.pageCount)
        state.slots["reaction"] = "재미있었어"
        state.prepareStoryBookPages()
        assertEquals(7, state.pageCount)
        assertEquals(PageKind.JOURNEY, state.pageKind(6))
        assertFalse(state.useGeneratedStory(List(6) { "짧은 응답" }))
        assertEquals("재미있었어", state.bookCaption(6))
        assertTrue(state.useGeneratedStory(List(7) { "일곱 쪽 응답" }))
        state.resetStory()
        state.templateKey = "E"
        assertEquals(6, state.pageCount)
        assertNull(state.storyBookPages)
    }

    @Test
    fun otherModesKeepTheirOwnPagePlans() {
        for (mode in listOf(StoryMode.DIARY, StoryMode.COOP)) {
            val state = DemoState().apply {
                this.mode = mode
                Server.SLOTS.forEach { slots[it] = "내용 $it" }
            }
            val original = state.bookPages.map { it.kind }
            state.prepareStoryBookPages()
            assertEquals(original, state.bookPages.map { it.kind })
            assertNull(state.storyBookPages)
        }
    }

    @Test
    fun tenTurnsWithRichSlotsKeepTheEarlyTemplateAndMakeEightPlayablePages() = runBlocking {
        val server = StoryTestServer { path, body ->
            if (path == "/story") JSONObject().put("scenes", JSONArray().apply {
                val pages = body.getJSONArray("pages")
                repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                    .put("kind", pages.getJSONObject(i).getString("kind"))
                    .put("caption", "${i + 1}쪽에 모은 이야기예요.")) }
            }) else JSONObject().put("preset", true)
        }
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.apply {
            speed = 0.001
            templateKey = "E"
            turn = 10
            endReason = "story_ready"
            listOf("place", "problem", "newcomer", "name", "cause", "solution",
                "reaction", "companion", "adult", "extra").forEach { slots[it] = "기억할 $it" }
        }
        Server.base = server.base
        Server.liveModes = setOf(StoryMode.STORY)
        try {
            d.go(Scene.MAKING)
            withTimeout(10_000) { while (d.s.scene != Scene.BOOK) delay(10) }
            val request = server.requests.single { it.first == "/story" }.second
            assertEquals(8, request.getJSONArray("pages").length())
            assertEquals("E", d.s.templateKey)
            assertEquals(8, d.s.pageCount)
            assertEquals(PageKind.RUB, d.s.pageKind(4))
            assertEquals(PageKind.DRAG, d.s.pageKind(5))
            assertEquals(PageKind.JOURNEY, d.s.pageKind(6))
            assertEquals(PageKind.TALK, d.s.pageKind(7))
            assertEquals(PageKind.TOGETHER, d.s.pageKind(8))
            assertEquals("8쪽에 모은 이야기예요.", d.s.bookCaption(8))
        } finally {
            scope.cancel()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
