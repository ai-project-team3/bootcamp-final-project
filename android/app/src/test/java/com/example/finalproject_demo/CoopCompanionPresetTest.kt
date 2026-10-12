package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.COOP_DRAW_DECIDED
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.companionName
import com.example.finalproject_demo.demo.companionPreset
import com.example.finalproject_demo.demo.coopDrawLine
import com.example.finalproject_demo.demo.coopFill
import com.example.finalproject_demo.demo.m2Line
import com.example.finalproject_demo.demo.spokenPeople
import com.example.finalproject_demo.demo.spokenWithPeople
import com.example.finalproject_demo.demo.coopCompanionPreset
import com.example.finalproject_demo.demo.drawFriend
import com.example.finalproject_demo.demo.finishDiary
import com.example.finalproject_demo.demo.friendToDraw
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #339 co-op ① — the last word of the name is who it is (「엄마 친구 강아지」 is a dog), and the companion doll is asked for only
 * after the child chose whether to draw (a drawn companion: zero server requests · design §3 · §5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopCompanionPresetTest {

    private fun name(a: Art?) = (a as? Art.Img)?.name

    @Test
    fun theLastWordIsWhoItIs() {
        assertEquals("ic_p_mom", name(coopCompanionPreset("엄마")))
        assertEquals("ic_p_grandma", name(coopCompanionPreset("우리 할머니")))
        assertEquals("ic_p_dad", name(coopCompanionPreset("엄마랑 아빠")))
        assertEquals("dp_teacher", name(coopCompanionPreset("유치원 선생님")))
        assertEquals("dp_teacher", name(coopCompanionPreset("선생님이랑")))
        assertNull(coopCompanionPreset("엄마 친구 강아지"))
        assertNull(coopCompanionPreset("할머니 고양이"))
        assertNull(coopCompanionPreset("선생님 강아지"))
        assertEquals("⚖️3 엄마 기본 그림", "ic_p_mom", name(coopCompanionPreset("친구 엄마")))
        assertNull("「친구」는 기본 친구(GENERIC_FRIEND)로", coopCompanionPreset("친구"))
        assertEquals("ic_p_grandma", name(coopCompanionPreset("할머니하고")))
        assertEquals("dp_friend_g", name(coopCompanionPreset("동생도")))
        assertNull(coopCompanionPreset(""))
    }

    /**
     * 10-12 user — 「엄마 아빠」 is mom **and** dad (only 아빠 stood); 「친구 엄마」 · 「친구의 엄마」 is one person, a friend's mom
     */
    @Test
    fun momAndDadAreTwoButAFriendsMomIsOne() {
        assertEquals("엄마, 아빠", companionName("엄마 아빠"))
        assertEquals("엄마, 아빠", companionName("엄마랑 아빠랑"))
        assertEquals("할머니, 할아버지", companionName("할머니 할아버지랑"))
        assertEquals("친구 엄마", companionName("친구 엄마"))
        assertEquals("친구의 엄마", companionName("친구의 엄마"))
        assertEquals("우리 엄마", companionName("우리 엄마"))
        assertEquals("엄마 친구", companionName("엄마 친구"))

        fun arts(said: String) = DemoState().apply { mode = StoryMode.COOP; companionKind = companionName(said) }.companionArts.map(::name)
        assertEquals(listOf("ic_p_mom", "ic_p_dad"), arts("엄마 아빠"))
        assertEquals(listOf("ic_p_mom", "ic_p_dad"), arts("엄마랑 아빠"))
        assertEquals(listOf("ic_p_mom"), arts("친구의 엄마"))
        assertEquals(listOf("ic_p_mom"), arts("친구 엄마"))
        assertEquals(listOf("ic_p_dad"), arts("아빠"))
    }

    /** 10-12 device review — 「엄마, 아빠」 stays in the slot, but Otto reads it 「엄마 아빠」, not with a pause */
    @Test
    fun momAndDadAreReadAloudWithRang() {
        assertEquals("엄마 아빠", spokenPeople("엄마, 아빠"))
        assertEquals("동생 형", spokenPeople("동생, 형"))
        assertEquals("민수랑 지호", spokenPeople("민수, 지호"))
        assertEquals("곰돌이랑 엄마", spokenPeople("곰돌이, 엄마"))
        assertEquals("할머니 할아버지 삼촌", spokenPeople("할머니, 할아버지, 삼촌"))
        assertEquals("엄마 아빠와 사자 우리에 가서 설렐 것 같아.", spokenWithPeople("엄마, 아빠와 사자 우리에 가서 설렐 것 같아.", "엄마, 아빠"))
        assertEquals("친구 엄마", spokenPeople("친구 엄마"))

        val s = DemoState().apply {
            mode = StoryMode.COOP; companionKind = companionName("엄마 아빠"); coopPick = CoopPick("place", "동물원", "soon")
        }
        assertEquals("엄마, 아빠", s.companionKind)
        assertEquals("같이 갈 엄마 아빠를 그려 줄래?", s.coopDrawLine(s.companionKind))
        val give = s.m2Line(easy = false)
        assertTrue(give, "엄마 아빠한테" in give && "," !in give)
        assertEquals("the people slot is read with 랑", "엄마 아빠가 뭐라고 했어?",
            coopFill("{who:가} 뭐라고 했어?", mapOf("who" to s.companionKind)))
        assertEquals("a place keeps its comma", "사자 우리, 기린 마당에서 놀았어?",
            coopFill("{place:에서} 놀았어?", mapOf("place" to "사자 우리, 기린 마당")))
    }

    /** The diary is unchanged (⚖️4 · Jinwoong) — a name containing a preset word keeps that preset */
    @Test
    fun theDiaryKeepsItsPresets() {
        assertEquals("ic_p_mom", name(companionPreset("엄마 친구 강아지")))
        assertEquals("ic_p_grandma", name(companionPreset("할머니 고양이")))
        assertEquals("dp_teacher", name(companionPreset("선생님 강아지")))
    }

    @Test
    fun aCoopCompanionWithAnotherPersonsNameIsDrawnByTheServer() {
        val s = com.example.finalproject_demo.demo.DemoState().apply {
            mode = StoryMode.COOP; companionKind = "엄마 친구 강아지"; done += COOP_DRAW_DECIDED
        }
        assertEquals("엄마 친구 강아지", s.friendToDraw())
        assertEquals("기본 친구 그림이 선다 — 엄마 그림이 아니다", "dp_friend_b", name(s.companionArt))
        s.companionKind = "혼자"
        assertNull("「혼자」는 인형이 없다", s.friendToDraw())
    }

    // ── No request before the drawing choice (§5) ──────────────────────────────────

    private fun run(block: suspend CoroutineScope.(Director, StoryTestServer) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", true) }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            block(d, server)
        } finally { sup.cancel(); server.close(); Server.liveModes = emptySet(); Server.base = null }
    }

    private fun StoryTestServer.characterAsks() = requests.count { it.first == "/image" && it.second.optString("kind") == "character" }

    private fun Director.filledCoop(companion: String) {
        s.mode = StoryMode.COOP
        s.coopPick = CoopPick("place", "동물원", "done")
        s.place = "기린 마당"; s.problem = "기린이 사과를 먹었어"; s.cause = "배가 고파서"; s.solution = "같이 사과를 줬어"
        listOf("place", "problem", "cause", "solution").forEach { s.slots[it] = it; s.slotBy[it] = "child" }
        s.companionKind = companion; s.friend = companion.takeIf { it.isNotEmpty() }
    }

    /** Let it run to making the book, and give the doll request time to go out */
    private suspend fun CoroutineScope.finish(d: Director) {
        launch { d.finishDiary() }
        delay(1_500)
    }

    @Test
    fun noDollIsAskedForBeforeTheDrawingChoice() = run { d, server ->
        d.filledCoop("기린 인형")
        d.drawFriend()
        delay(300)
        assertEquals("그리기를 정하기 전에 인형을 맡겼다", 0, server.characterAsks())
    }

    @Test
    fun aCompanionTheChildDrewIsNeverAskedFor() = run { d, server ->
        d.filledCoop("기린 인형")
        d.s.drawing.add(Stroke(Color.Red, listOf(Offset(0.1f, 0.1f), Offset(0.4f, 0.5f)), 4f))
        finish(d)
        assertTrue(COOP_DRAW_DECIDED in d.s.done)
        assertEquals("아이가 그린 인물을 서버에 맡겼다", 0, server.characterAsks())
    }

    @Test
    fun aCompanionTheChildDidNotDrawIsAskedForOnce() = run { d, server ->
        d.filledCoop("기린 인형")
        finish(d)
        assertEquals(1, server.characterAsks())
    }

    @Test
    fun noCompanionNoRequest() = run { d, server ->
        d.filledCoop("")
        finish(d)
        assertEquals(0, server.characterAsks())
    }
}
