package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryImageStore
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-05 — 협업도 동화처럼 아이가 말한 곳으로 배경을 그린다. 그리는 말에는 고른 요소를 붙이고(「사자 우리 (동물원 이야기)」),
 * 못 그리면 고른 요소의 미리 만든 배경(`bg_zoo`) 그대로다. 대화는 기다리지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopBackgroundTest {

    private val zoo = CoopPick("place", "동물원", "done")

    private suspend fun await(ms: Long = 8_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = Director(CoroutineScope(coroutineContext + sup), storyImageStore = StoryImageStore(context))
        d.s.speed = 0.01
        try { block(d) } finally { sup.cancel(); Server.liveModes = emptySet(); Server.base = null }
    }

    /** 저장소가 디코드해 보는 진짜 PNG 한 장 (StoryImageStoreTest 와 같은 방법) */
    private fun tinyPng(): String {
        val out = java.io.ByteArrayOutputStream()
        android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /** 가짜 서버 — /turn 은 물은 칸을 아이 말로 채우고, /image 는 [drawn] 이면 그림을, 아니면 preset 을 준다 */
    private fun server(drawn: Boolean) = StoryTestServer { path, body ->
        when (path) {
            "/turn" -> {
                val judge = JSONObject().put("reason", "ok")
                val asked = body.optString("asked_slot")
                if (asked.isNotBlank() && asked != "null") judge.put("slot_1", asked).put("value_1", body.optString("utterance"))
                JSONObject().put("judge", judge)
            }
            "/image" -> if (drawn) JSONObject().put("preset", false).put("reason", "ok").put("scene", "a lion enclosure")
                .put("png_base64", tinyPng())
            else JSONObject().put("preset", true).put("reason", "comfy down")
            else -> JSONObject()
        }
    }

    private suspend fun Director.toFirstQuestionAndSayThePlace(said: String) {
        go(Scene.ADULT)
        assertNotNull(await(4_000) { s.buttons.any { "같이 만들기" in it.label } })
        s.buttons.first { "같이 만들기" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.BESTIARY })
        s.coopPick = zoo
        assertNotNull(await(4_000) { s.buttons.any { "카드를 탭" in it.label } })
        s.buttons.first { "카드를 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.DIARY })
        assertNotNull(await { s.micEnabled })
        withTimeoutOrNull(8_000) { while (s.place == null) { send(Reply.Spoke(said)); delay(30) } }
        assertEquals(said, s.place)
    }

    @Test
    fun thePlaceTheChildSaidBecomesTheBackdrop() = run { d ->
        val server = server(drawn = true)
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionAndSayThePlace("사자 우리")
            assertNotNull("생성 배경으로 안 바뀌었다: ${d.s.bgName}", await { d.s.bgName != "bg_zoo" })
            val image = server.requests.first { it.first == "/image" }.second
            assertEquals("사자 우리 (동물원 이야기)", image.optString("place"))
            assertEquals("coop", image.optString("mode"))
            assertEquals("그림 요청은 한 번", 1, server.requests.count { it.first == "/image" })
        } finally { server.close() }
    }

    @Test
    fun withoutAPictureThePickedItemsBackdropStays() = run { d ->
        val server = server(drawn = false)
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.toFirstQuestionAndSayThePlace("사자 우리")
            assertNotNull("그림을 요청하지 않았다", await { server.requests.any { it.first == "/image" } })
            delay(300)
            assertEquals("bg_zoo", d.s.bgName)
        } finally { server.close() }
    }

    @Test
    fun withTheServerOffNothingIsDrawn() = run { d ->
        d.toFirstQuestionAndSayThePlace("사자 우리")
        delay(300)
        assertEquals("bg_zoo", d.s.bgName)
        assertTrue(Server.liveModes.isEmpty())
    }
}
