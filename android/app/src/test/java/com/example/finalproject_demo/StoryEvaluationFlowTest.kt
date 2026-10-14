package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StoryEvaluationFlowTest {
    private val d = Director(CoroutineScope(Dispatchers.Unconfined))
    @After fun stopCapture() {
        StoryEvaluation.setEnabled(d.s, false)
        com.example.finalproject_demo.demo.missions.MissionHistory.detach()
    }

    @Test fun recordedHistoryCannotRewriteSavedMissionEvidenceAfterTheBookWasBuilt() {
        val context = RuntimeEnvironment.getApplication()
        com.example.finalproject_demo.demo.missions.MissionHistory.attach(context)
        for (frame in listOf("A", "G")) {
            val saving = Director(CoroutineScope(Dispatchers.Unconfined))
            saving.s.scene = Scene.BOOK
            saving.s.templateKey = frame
            saving.s.useGeneratedStory(saving.s.bookPages.indices.map { "합성 ${it + 1}쪽이에요." })
            saving.s.m2Result = "solo"
            StoryEvaluation.setEnabled(saving.s, true)
            ShadowLog.clear()
            assertTrue(saving.saveFinishedStory())
            val started = events().single { it.getString("event") == "book_save_started" }.getJSONObject("data")
            val saved = events().single { it.getString("event") == "book_saved" }.getJSONObject("data")
            assertEquals("frame $frame", started.getJSONObject("mission_results").toString(), saved.getJSONObject("mission_results").toString())
            StoryEvaluation.setEnabled(saving.s, false)
        }
    }

    @Test fun failedStorageIsObservedAsFailureWithoutPublishingTheExceptionDetail() {
        val failing = Director(CoroutineScope(Dispatchers.Unconfined), object : StoryBookStore {
            override fun load() = emptyList<SavedStoryBook>()
            override fun save(book: SavedStoryBook) { error("synthetic storage detail must stay out of evidence") }
        })
        failing.s.scene = Scene.BOOK
        failing.s.templateKey = "C"
        StoryEvaluation.setEnabled(failing.s, true)
        assertFalse(failing.saveFinishedStory())
        assertTrue(failing.s.shelf.isEmpty())
        val failure = events().single { it.getString("event") == "book_save_failed" }.getJSONObject("data")
        assertEquals("save_exception", failure.getString("status"))
        assertFalse(events().any { it.toString().contains("synthetic storage detail") })
    }

    @Test fun lateCallbacksCannotJoinANewAttemptAndSavingKeepsTheAdoptedAttempt() {
        d.s.scene = Scene.PLACE
        d.s.templateKey = "C"
        StoryEvaluation.setEnabled(d.s, true)
        val old = StoryEvaluation.generation(d.s)!!
        val late = StoryEvaluation.diagnostic(d.s, old)!!
        d.s.resetStory()
        d.s.templateKey = "C"
        StoryEvaluation.begin(d.s)
        val current = StoryEvaluation.generation(d.s)!!
        late("response", JSONObject().put("status", "synthetic_late"))
        StoryEvaluation.diagnostic(d.s, current)!!("response", JSONObject().put("status", "synthetic_current"))
        val captions = d.s.bookPages.indices.map { "합성 ${it + 1}쪽이에요." }
        d.s.useGeneratedStory(captions)
        StoryEvaluation.generated(d.s, current, Server.StoryBook(captions), captions, true)
        val pending = StoryEvaluation.generation(d.s)!!
        assertTrue(d.saveFinishedStory())
        val evidence = events()
        assertTrue(evidence.any { it.getString("event") == "generation_cancelled" && it.getString("attempt_id") == old.id })
        assertFalse(evidence.any { it.getJSONObject("data").optString("status") == "synthetic_late" })
        assertEquals(current.id, evidence.single { it.getJSONObject("data").optString("status") == "synthetic_current" }.getString("attempt_id"))
        assertEquals(current.id, evidence.single { it.getString("event") == "book_saved" }.getString("attempt_id"))
        assertNotEquals(current.id, pending.id)
    }

    @Test fun actualMakingSceneCapturesRequestParsedUnmaskedAdoptedAndSavedText() = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val generationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val generating = Director(generationScope, LocalStoryBookStore(RuntimeEnvironment.getApplication()))
        try {
            FakeHttp { _, body ->
                val count = JSONObject(body).optJSONArray("pages")?.length() ?: 1
                JSONObject().put("title", "합성 피자 놀이").put("scenes", org.json.JSONArray((1..count).map {
                    JSONObject().put("caption", "{주인공}이 공원에서 피자를 만들었어요.")
                })).toString()
            }.use { http ->
                Server.base = http.base
                Server.liveModes = setOf(StoryMode.STORY)
                StoryEvaluation.setEnabled(generating.s, true)
                StoryEvaluation.begin(generating.s)
                generating.s.templateKey = "C"
                generating.s.speed = 0.001
                generating.s.storyHeroCall = "토끼친구"
                generating.s.slots["place"] = "공원"
                generating.s.slots["solution"] = "함께 피자를 만들었어"
                generating.s.slots["extra"] = "햄버거는 더 팔지 않았어"
                generating.talk("child", "함께 피자를 만들고 햄버거는 더 팔지 않았어")
                generating.s.scene = Scene.MAKING
                generating.runScene(Scene.MAKING)
                assertTrue(generating.saveFinishedStory())
                val evidence = events()
                val request = evidence.single { it.getString("event") == "generation_request" }.getJSONObject("data")
                assertEquals("JSONObject_reserialized", request.getString("request_format"))
                assertEquals("햄버거는 더 팔지 않았어", request.getJSONObject("request").getJSONObject("slots").getString("extra"))
                val adopted = evidence.single { it.getString("event") == "generation_adopted" }
                assertTrue(adopted.getJSONObject("data").getJSONArray("parsed_server_captions").getString(0).contains("{주인공}"))
                assertFalse(adopted.getJSONObject("data").getJSONArray("unmasked_captions").getString(0).contains("{주인공}"))
                assertEquals(adopted.getString("attempt_id"), evidence.single { it.getString("event") == "book_saved" }.getString("attempt_id"))
            }
        } finally {
            generationScope.cancel()
            StoryEvaluation.setEnabled(generating.s, false)
            Server.base = previousBase
            Server.liveModes = previousModes
        }
    }

    @Test fun resettingTheActualStoryClosesEvidenceAndStartsADistinctCompleteSegment() {
        d.s.scene = Scene.PLACE
        StoryEvaluation.setEnabled(d.s, true)
        d.s.resetStory()
        StoryEvaluation.begin(d.s)
        val starts = events().filter { it.getString("event") == "session_started" }
        assertTrue(starts.first().getJSONObject("data").getBoolean("missing_source"))
        assertFalse(starts.last().getJSONObject("data").getBoolean("started_mid_session"))
        assertFalse(starts.last().getJSONObject("data").getBoolean("missing_source"))
        assertTrue(events().any { it.getString("event") == "session_reset" })
        val ids = ShadowLog.getLogsForTag("OttoTrace").filter { it.msg.startsWith("story_eval | ") }
            .map { JSONObject(it.msg.removePrefix("story_eval | ")).getString("session_id") }.toSet()
        assertEquals(2, ids.size)
    }

    @Test fun savingCapturesTheActualStoredIdAndMissionAugmentedCaptionsOnly() {
        val store = LocalStoryBookStore(RuntimeEnvironment.getApplication())
        val saving = Director(CoroutineScope(Dispatchers.Unconfined), store)
        saving.s.scene = Scene.BOOK
        saving.s.templateKey = "A"
        saving.s.useGeneratedStory(saving.s.bookPages.indices.map { "생성된 ${it + 1}쪽이에요." })
        saving.s.m2Result = "solo"
        StoryEvaluation.setEnabled(saving.s, true)
        assertTrue(saving.saveFinishedStory())
        val actual = store.load().single()
        val saved = events().single { it.getString("event") == "book_saved" }.getJSONObject("data")
        assertEquals(actual.id, saved.getString("book_id"))
        assertTrue(actual.pages.any { "그림 조각을 모두 맞춰" in it.caption })
        val captured = saved.getJSONArray("pages")
        assertEquals(actual.pages.map { it.caption }, (0 until captured.length()).map { captured.getJSONObject(it).getString("caption") })
        assertFalse(saved.toString().contains("soundClipId"))
        assertFalse(saved.has("visuals"))
    }

    @Test fun originalTranscriptAndJudgeOverwriteStaySeparateInCapturedEvidence() = runBlocking {
        d.s.scene = Scene.PLACE
        d.s.templateKey = "C"
        d.s.slots["solution"] = "별을 줬어"
        StoryEvaluation.setEnabled(d.s, true)
        ShadowLog.clear()
        d.talk("child", "  괴물을 붙잡고 함께 피자를 만들었어  ")
        d.s.exchangeStoryTurn("solution", "어떻게 끝났어?", "괴물을 붙잡고 함께 피자를 만들었어") {
            Server.TurnResult(Server.Verdict(reason = "synthetic", fills = listOf("solution" to "괴물을 붙잡았어"),
                nextSlot = null, noLongerNeeded = null, storyReady = true, unclear = false, unclearOf = null,
                contradiction = false, s1Reason = false, s2Addition = false, emotion = null), null)
        }
        d.s.slots["solution"] = "나중의 값"
        val events = events()
        val original = events.first { it.getString("event") == "utterance" }.getJSONObject("data")
        assertEquals("  괴물을 붙잡고 함께 피자를 만들었어  ", original.getString("raw_text"))
        assertEquals("괴물을 붙잡고 함께 피자를 만들었어", original.getString("stored_text"))
        val before = events.first { it.getString("event") == "turn_before" }.getJSONObject("data").getJSONObject("state")
        val after = events.first { it.getString("event") == "turn_after" }.getJSONObject("data").getJSONObject("state")
        assertEquals("별을 줬어", before.getJSONObject("slots").getString("solution"))
        assertEquals("괴물을 붙잡았어", after.getJSONObject("slots").getString("solution"))
        assertEquals("나중의 값", d.s.slots["solution"])
    }

    private fun events(): List<JSONObject> = ShadowLog.getLogsForTag("OttoTrace")
        .filter { it.msg.startsWith("story_eval | ") }
        .map { JSONObject(it.msg.removePrefix("story_eval | ")) }
        .groupBy { it.getString("session_id") to it.getLong("sequence") }
        .values.map { parts -> JSONObject(parts.sortedBy { it.getInt("part") }.joinToString("") { it.getString("payload") }) }
}
