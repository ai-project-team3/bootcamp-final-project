package com.example.finalproject_demo

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
class StoryGeneratedTitleTest {
    @Test fun aServerTitleBecomesTheBookTitleAndCover() = generatedBook("염라대왕과 숲의 약속") { d ->
        assertEquals("염라대왕과 숲의 약속", d.s.title)
        assertEquals("염라대왕과 숲의 약속", d.s.bookCaption(0))
    }

    @Test fun anOldServerWithoutATitleRetainsTheLocalTitle() = generatedBook(null) { d ->
        assertEquals(d.s.autoTitleFor(), d.s.title)
    }

    @Test fun aBlankServerTitleDoesNotEraseTheLocalTitle() = generatedBook("  ") { d ->
        assertEquals(d.s.autoTitleFor(), d.s.title)
    }

    private fun generatedBook(title: String?, check: (Director) -> Unit) = runBlocking {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val server = StoryTestServer { path, body ->
            if (path == "/story") JSONObject().apply {
                title?.let { put("title", it) }
                put("scenes", JSONArray().apply {
                    val pages = body.getJSONArray("pages")
                    repeat(pages.length()) { i -> put(JSONObject().put("index", i + 1)
                        .put("kind", pages.getJSONObject(i).getString("kind"))
                        .put("caption", "${i + 1}번째 숲 이야기예요.")) }
                })
            } else JSONObject().put("preset", true)
        }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.STORY)
            val d = Director(scope)
            d.s.speed = 0.01
            d.s.templateKey = "C"
            d.s.slots["place"] = "숲"
            d.s.endReason = "story_ready"
            d.go(Scene.MAKING)
            withTimeout(5_000) { while (d.s.scene != Scene.BOOK) delay(5) }
            assertNotNull("the title must accompany the accepted story", d.s.storyCaptions)
            assertEquals(1, server.requests.count { it.first == "/story" })
            check(d)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
