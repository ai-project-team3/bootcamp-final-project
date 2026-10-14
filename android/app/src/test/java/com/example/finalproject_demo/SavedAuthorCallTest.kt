package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.ChildCall
import com.example.finalproject_demo.ui.reactionFor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic calls only: a stored author must not follow the current child or the story's actor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SavedAuthorCallTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousCall: String? = null

    @Before fun start() {
        previousCall = ChildCall.name
        ChildCall.set("단아")
        listOf("story_books", "diary_books", "coop_books").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @After fun finish() { ChildCall.set(previousCall.orEmpty()) }

    private fun story() = DemoState().apply {
        resetStory(); templateKey = "C"; title = "콩이의 모험"; storyHeroCall = "콩이"
        storyHeroDescription = "구름 머리"; storyHeroImage = "local:synthetic-hero.png"
        storySoundClip = null
        useGeneratedStory(template!!.pages.indices.map { "${it + 1}쪽 그대로 남길 문장" })
    }

    private fun coop() = DemoState().apply {
        resetStory(); mode = StoryMode.COOP; place = "공원"; slots["place"] = "공원"
        title = "함께한 하루"
        storyCaptions = (1..pageCount).map { "${it}쪽 함께한 이야기" }
    }

    @Test fun storyAndReportKeepTheSameAuthorAfterAnotherChildStarts() {
        AnswerHistory.attach(context)
        val s = story()
        val originalProfile = s.reportProfileId
        val captions = s.storyCaptions
        ChildCall.set("보람")
        assertTrue(AnswerHistory.startNewChild())
        assertNotEquals(originalProfile, AnswerHistory.profileId)
        val book = s.completedStoryBook()!!
        val report = s.buildSessionReport(book.id)
        val store = LocalStoryBookStore(context)
        store.save(book)
        val back = LocalStoryBookStore(context).load().single()
        assertEquals("단아", back.authorCall)
        assertEquals("단아", sessionReportOf(JSONObject(report.toJson().toString())).authorCall)
        assertEquals(originalProfile, report.profileId)
        assertEquals(book, back)
        val evidence = java.io.File("build/review/saved-author-478").apply { mkdirs() }
        java.io.File(evidence, "story_store_synthetic.json").writeText(
            context.getSharedPreferences("story_books", Context.MODE_PRIVATE).getString("books", null)!!)
        java.io.File(evidence, "report_store_synthetic.json").writeText(report.toJson().toString())
        assertEquals(captions, s.storyCaptions)
        val reader = DemoState()
        assertTrue(reader.restoreStoryBook(back))
        assertEquals("단아", reader.sessionAuthorCall)
        assertEquals("콩이", reader.storyHeroCall)
        assertEquals("보람", reader.childName)
        assertEquals("단아의 이야기!", reactionFor(reader, "glass", "hero", liveStory = true))
        assertEquals("보람", ChildCall.call)
        s.resetStory()
        assertEquals("보람", s.sessionAuthorCall)
    }

    @Test fun newReportHomeQuestionUsesTheSessionAuthorAndUnknownNeverUsesCurrentCall() {
        val s = story().apply {
            slots["problem"] = "길을 잃었어"; slotBy["problem"] = "child"
            talk += TalkLine("child", "길을 잃었어")
        }
        val talk = s.talk.toList()
        ChildCall.set("보람")
        val report = s.buildSessionReport()
        assertEquals("단아", report.authorCall)
        assertTrue(report.homeQuestion.contains("단아는"))
        assertFalse(report.homeQuestion.contains("보람"))
        val json = report.toJson().toString()
        assertEquals(json, sessionReportOf(JSONObject(json)).toJson().toString())
        for (unknown in listOf(null, "")) {
            s.sessionAuthorCall = unknown
            val question = s.buildSessionReport().homeQuestion
            assertTrue(question.contains("아이는"))
            assertFalse(question.contains("보람"))
        }
        assertEquals(talk, s.talk.toList())
        assertEquals("보람", s.childName)
    }

    @Test fun draftRelaunchPreservesAuthorAndResetCapturesOnlyTheNewSession() {
        val source = story()
        val draft = SessionDraft.fromJson(JSONObject(source.captureDraft(Scene.CAUSE).toJson().toString()))!!
        ChildCall.set("보람")
        val relaunched = DemoState().apply { applyDraft(draft) }
        assertEquals("단아", relaunched.sessionAuthorCall)
        assertEquals("단아", relaunched.completedStoryBook()!!.authorCall)
        assertEquals("단아", relaunched.buildSessionReport().authorCall)
        assertEquals(source.captureDraft(Scene.CAUSE, 1).toJson().toString(),
            relaunched.captureDraft(Scene.CAUSE, 1).toJson().toString())
        assertEquals("보람", relaunched.childName)
        relaunched.resetStory()
        assertEquals("보람", relaunched.sessionAuthorCall)
    }

    @Test fun legacyDraftStaysUnknownThroughResumeCompletionAndAnotherCapture() {
        val draft = story().captureDraft(Scene.CAUSE)
        draft.state.remove("sessionAuthorCall")
        ChildCall.set("보람")
        val restored = DemoState().apply { applyDraft(draft) }
        assertNull(restored.sessionAuthorCall)
        assertNull(restored.completedStoryBook()!!.authorCall)
        assertNull(restored.buildSessionReport().authorCall)
        assertTrue(restored.captureDraft(Scene.CAUSE).state.isNull("sessionAuthorCall"))
        val again = DemoState().apply { applyDraft(restored.captureDraft(Scene.CAUSE)) }
        assertNull(again.sessionAuthorCall)
        assertEquals("아이의 이야기!", reactionFor(again, "glass", "hero", liveStory = true))
    }

    @Test fun turnUndoAndRedoPreserveTheAuthorIncludingUnknown() {
        val s = story()
        val history = TurnHistory(s)
        history.before()
        s.sessionAuthorCall = null
        s.slots["place"] = "공원"
        history.done()
        ChildCall.set("보람")
        assertTrue(history.undo())
        assertEquals("단아", s.sessionAuthorCall)
        assertTrue(history.redo())
        assertNull(s.sessionAuthorCall)
        assertEquals("보람", ChildCall.call)
    }

    @Test fun legacyStoryAndReportNeverGuessFromCurrentCallOrHero() {
        val store = LocalStoryBookStore(context)
        store.save(story().completedStoryBook()!!)
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString("books", null))
        old.getJSONObject(0).remove("authorCall")
        prefs.edit().putString("books", old.toString()).commit()
        val oldReport = story().buildSessionReport().toJson().apply { remove("authorCall") }
        ChildCall.set("보람")
        val back = LocalStoryBookStore(context).load().single()
        assertNull(back.authorCall)
        assertNull(sessionReportOf(oldReport).authorCall)
        val reader = DemoState().apply { restoreStoryBook(back) }
        assertNull(reader.sessionAuthorCall)
        assertEquals("콩이", reader.storyHeroCall)
        assertEquals("아이의 이야기!", reactionFor(reader, "glass", "hero", liveStory = true))
        store.save(back)
        assertNull(store.load().single().authorCall)
    }

    @Test fun diaryRoundTripUsesSessionAuthorAndReadingRestoresCurrentContext() = runBlocking {
        val s = DemoState().apply {
            resetStory(); mode = StoryMode.DIARY; slots["place"] = "공원"; slots["problem"] = "놀았어"
        }
        val book = s.completedDiaryBook("오늘 일기")!!
        ChildCall.set("보람")
        LocalDiaryBookStore(context).save(book)
        val back = LocalDiaryBookStore(context).load().single()
        assertEquals("단아", back.authorCall)
        assertEquals(book, back)
        s.resetStory()
        s.title = "만들던 이야기"
        s.drawingAspect = 1.7f
        val day = s.diaryDay
        val draft = s.captureDraft(Scene.PLACE, 1).toJson().toString()
        s.withSavedDiary(back) {
            assertEquals("단아", s.sessionAuthorCall)
            assertEquals("보람", s.childName)
        }
        assertSame(day, s.diaryDay)
        assertEquals(draft, s.captureDraft(Scene.PLACE, 1).toJson().toString())
    }

    @Test fun legacyDiaryUnknownReadingRestoresAuthorEvenWhenCancelled() = runBlocking {
        val s = DemoState().apply { mode = StoryMode.DIARY; slots["problem"] = "놀았어" }
        val json = s.completedDiaryBook("옛 일기")!!.toJson().apply { remove("authorCall") }
        ChildCall.set("보람")
        val book = diaryBookFromJson(json) { _, _ -> null }
        assertNull(book.authorCall)
        val current = DemoState().apply { title = "만들던 책" }
        val old = current.captureDraft(Scene.CAUSE, 1).toJson().toString()
        try {
            current.withSavedDiary(book) {
                assertNull(current.sessionAuthorCall)
                assertEquals("보람", current.childName)
                throw kotlinx.coroutines.CancellationException("Synthetic reader cancellation")
            }
            fail("The reader cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(old, current.captureDraft(Scene.CAUSE, 1).toJson().toString())
        assertEquals("보람", ChildCall.call)
    }

    @Test fun coopRoundTripKeepsSessionAuthorEvenWhenSceneUsesCurrentCall() {
        val s = coop()
        ChildCall.set("보람")
        val saved = s.completedCoopBook()!!
        assertEquals("보람", saved.snapshot!!.childName)
        assertEquals("단아", saved.book.authorCall)
        assertEquals("단아", s.buildSessionReport(saved.book.id).authorCall)
        val back = coopBooksFromJson(coopBooksToJson(listOf(saved))).single()
        assertEquals(saved, back)
        val store = object : CoopBookStore {
            override fun load() = listOf(back)
            override fun save(book: SavedCoopBook) = error("Reading does not save")
        }
        CoopShelf.attach(s, store)
        val reader = DemoState()
        assertTrue(reader.restoreCoopBook(back.book))
        assertEquals("단아", reader.sessionAuthorCall)
        assertEquals("보람", reader.childName)
    }

    @Test fun legacyCoopUsesOnlyExplicitStoredCallAndExplicitNullNeverBackfills() {
        val made = coop().completedCoopBook()!!
        val legacy = JSONArray(coopBooksToJson(listOf(made)))
        legacy.getJSONObject(0).remove("authorCall")
        ChildCall.set("보람")
        val back = coopBooksFromJson(legacy.toString()).single()
        assertEquals("단아", back.book.authorCall)
        assertEquals("단아", coopBooksFromJson(coopBooksToJson(listOf(back))).single().book.authorCall)
        legacy.getJSONObject(0).put("authorCall", JSONObject.NULL)
        val unknown = coopBooksFromJson(legacy.toString()).single()
        assertNull(unknown.book.authorCall)
        assertNull(coopBooksFromJson(coopBooksToJson(listOf(unknown))).single().book.authorCall)
        legacy.getJSONObject(0).remove("authorCall")
        legacy.getJSONObject(0).put("coop", JSONObject.NULL)
        assertNull(coopBooksFromJson(legacy.toString()).single().book.authorCall)
    }
}
