package com.example.finalproject_demo

import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StoryBookDiagnosticTest {
    @Test fun cancellingAStoryWithDiagnosticsStillStopsPromptlyAndReportsCancellation() = runBlocking {
        val previous = Server.base
        val entered = java.util.concurrent.CountDownLatch(1)
        val statuses = java.util.Collections.synchronizedList(mutableListOf<String>())
        val http = StoryTestServer { _, _ -> entered.countDown(); Thread.sleep(3000); JSONObject() }
        try {
            Server.base = http.base
            val call = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                Server.storyBook("story", emptyMap(), diagnostic = { _, data -> statuses += data.optString("status") })
            }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val started = System.nanoTime()
            call.cancelAndJoin()
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2000)
            assertTrue("cancelled" in statuses)
        } finally { Server.base = previous; http.close() }
    }

    @Test fun diagnosticCopiesCannotRewriteTheActualRequestOrAcceptedCaptions() = runBlocking {
        val previous = Server.base
        val sent = java.util.concurrent.atomic.AtomicReference<String>()
        try {
            FakeHttp { _, body -> sent.set(body); "{\"scenes\":[{\"caption\":\"함께 피자를 만들었어요.\"}]}" }.use { http ->
                Server.base = http.base
                val book = Server.storyBook("story", mapOf("solution" to "함께 피자를 만들었어"), pages = listOf(Server.Page("TOGETHER")), diagnostic = { phase, data ->
                    if (phase == "request") data.getJSONObject("request").getJSONObject("slots").put("solution", "observer replacement")
                    else if (data.has("captions")) data.getJSONArray("captions").put(0, "observer replacement")
                })
                assertEquals("함께 피자를 만들었어", JSONObject(sent.get()).getJSONObject("slots").getString("solution"))
                assertEquals(listOf("함께 피자를 만들었어요."), book!!.captions)
            }
        } finally { Server.base = previous }
    }

    @Test fun bookDiagnosticsDistinguishFailureStagesWithoutRecordingErrorBodies() = runBlocking {
        val previous = Server.base
        try {
            for ((reply, wanted) in listOf(null to "http_error", "not-json" to "invalid_json", "{}" to "invalid_scene_shape",
                "{\"scenes\":[{\"caption\":\"한 쪽\"}]}" to "invalid_count")) {
                FakeHttp { _, _ -> reply }.use { http ->
                    Server.base = http.base
                    val records = mutableListOf<JSONObject>()
                    val result = Server.storyBook("story", mapOf("solution" to "피자를 만들었어"), pages = listOf(Server.Page("TALK"), Server.Page("TOGETHER")),
                        diagnostic = { _, data -> records += JSONObject(data.toString()) })
                    assertNull(result)
                    assertTrue(wanted, records.any { it.optString("status") == wanted })
                    assertTrue(records.all { !it.has("headers") && !it.has("url") && !it.has("error_body") })
                }
            }
            Server.base = null
            val statuses = mutableListOf<String>()
            assertNull(Server.storyBook("story", emptyMap(), diagnostic = { _, data -> statuses += data.optString("status") }))
            assertTrue("missing_server", "missing_server" in statuses)
        } finally { Server.base = previous }
    }

    @Test fun failingObserverDoesNotChangeSuccessfulCaptionParsingOrMakeAnotherRequest() = runBlocking {
        val previous = Server.base
        var requests = 0
        try {
            FakeHttp { _, _ -> requests++; "{\"title\":\"피자 놀이\",\"scenes\":[{\"caption\":\"함께 피자를 만들었어요.\"}]}" }.use { http ->
                Server.base = http.base
                val book = Server.storyBook("story", emptyMap(), pages = listOf(Server.Page("TOGETHER")), diagnostic = { _, _ -> error("synthetic observer failure") })
                assertEquals(listOf("함께 피자를 만들었어요."), book!!.captions)
                assertEquals(1, requests)
            }
        } finally { Server.base = previous }
    }
}
