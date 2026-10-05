package com.example.finalproject_demo

import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.coopWriteBook
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #113 — 협업 책을 쓸 때 고른 요소 안의 자리(spots)를 `/story` 의 `stage` 로 보낸다.
 * 요소의 선택지 후보(무슨 일 · 까닭 · 해결)는 아이가 말한 것이 아니라서 보내지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopStoryStageTest {

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    private fun storyServer() = StoryTestServer { path, body ->
        if (path != "/story") JSONObject()
        else JSONObject().put("scenes", JSONArray().apply {
            repeat(body.optJSONArray("pages")?.length() ?: 0) { put(JSONObject().put("index", it + 1).put("caption", "서버 문장 ${it + 1}")) }
        })
    }

    /** 고른 이야기로 책을 쓰고, 서버가 받은 /story 요청 */
    private suspend fun Director.storyRequestFor(pick: CoopPick): JSONObject {
        val server = storyServer()
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            s.mode = StoryMode.COOP
            s.coopPick = pick
            s.place = "소방서"; s.problem = "소방차를 봤어"; s.cause = "멋있어서"; s.solution = "사진을 찍었어"
            listOf("place", "problem", "cause", "solution").forEach { s.slotBy[it] = "child" }
            coopWriteBook()
            val req = server.requests.firstOrNull { it.first == "/story" }?.second
            assertNotNull("/story 를 안 불렀다", req)
            return req!!
        } finally { server.close() }
    }

    @Test
    fun aListedItemSendsItsSpotsAsTheStage() = run { d ->
        val req = d.storyRequestFor(CoopPick("job", "소방관", "done"))
        val stage = req.getJSONArray("stage")
        assertEquals(listOf("소방차 차고", "출동 준비실", "훈련장"), List(stage.length()) { stage.getString(it) })
        // 선택지 후보는 책 재료로 가지 않는다
        assertFalse("큰불이 난 집" in req.toString())
    }

    @Test
    fun aCustomItemSendsNoStage() = run { d ->
        val req = d.storyRequestFor(CoopPick("job", "제빵사", "dream"))
        assertFalse("직접 쓴 요소에 stage 를 보냈다", req.has("stage"))
    }
}
