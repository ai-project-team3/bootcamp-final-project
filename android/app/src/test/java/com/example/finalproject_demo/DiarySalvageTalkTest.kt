package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.DiaryBookInput
import com.example.finalproject_demo.demo.DiaryPageKind
import com.example.finalproject_demo.demo.buildDiaryBook
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.isStoryTalk
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #220 ① — 그리는 중에 **묻지 않았는데** 아이가 한 이야기는 버리지 않는다.
 * 전에는 조각 이름 · 「그려줘」 · 「다 그렸어」가 아니면 「그렇구나! 계속 그려 봐.」로만 받고 어디에도 남지 않았다.
 * 이제 문장이면 `/turn` 판정에 보내 칸에 **덧붙이고**(덮어쓰지 않는다), 맞는 칸이 없으면 `extra` 에 쌓는다.
 * 오또는 앱에 구운 짧은 말로만 받는다(서버 목소리 없음).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiarySalvageTalkTest {

    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun turn(fills: List<Pair<String, String>>, ack: String = "아빠가 도와줬구나!"): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", JSONObject.NULL).put("story_ready", false).put("s1_reason", false)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = JSONObject().put("ack", ack).put("expand", JSONObject.NULL).put("question", JSONObject.NULL)
        return JSONObject().put("judge", judge).put("line", line).toString()
    }

    /** 서버 모드 그림판 — 조각 하나를 이미 이름 붙여 두어(물을 조각이 없다) 아이가 먼저 하는 말만 본다 */
    private fun drawing(fake: (JSONObject) -> String?, block: suspend (Director, MutableList<JSONObject>) -> Unit) = runBlocking {
        val asked = mutableListOf<JSONObject>()
        val http = FakeHttp { path, body -> if (path == "/turn") { val t = JSONObject(body); asked += t; fake(t) } else null }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        Server.base = http.base
        Server.liveModes = setOf(StoryMode.DIARY)
        try {
            val s = d.s
            d.go(Scene.DIARY)
            assertTrue(await { s.stage is DiaryStart } != null)
            assertTrue(await { if (s.stage is DiaryStart) d.send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
            s.drawing += Stroke(Color.Blue, listOf(Offset(.1f, .3f), Offset(.2f, .6f)))
            s.diaryDay.catchUp(s.drawing)
            s.diaryDay.pieces[0] = s.diaryDay.pieces[0].copy(name = "모래성")
            s.quotes += "모래성"
            block(d, asked)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            Server.liveModes = emptySet()
            Server.base = null
            http.close()
        }
    }

    /** 오또가 듣고 있을 때 묻지 않은 말을 한 번 보낸다 */
    private suspend fun Director.tell(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled && s.diaryDay.watching }
            delay(100)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    @Test
    fun aSentenceIsAStoryAndAShortWordIsNot() {
        assertTrue(isStoryTalk("아빠가 도와줬어"))
        assertTrue(isStoryTalk("파도가 와서 무너졌어"))
        assertTrue("움직임 말 하나여도 이야기다", isStoryTalk("넘어졌어"))
        assertFalse("한 낱말은 조각 이름일 수 있다", isStoryTalk("양동이"))
        assertFalse(isStoryTalk("응"))
        assertFalse(isStoryTalk("몰라"))
        assertFalse(isStoryTalk(""))
    }

    @Test
    fun anUntoldStoryWhileDrawingGoesToTheJudgeAndFillsAsTheChilds() = drawing({ turn(listOf("companion" to "아빠")) }) { d, asked ->
        d.tell("아빠가 도와줬어") { asked.isNotEmpty() }
        assertEquals("diary", asked[0].getString("mode"))
        assertEquals("아빠가 도와줬어", asked[0].getString("utterance"))
        assertTrue(await { d.s.slots["companion"] != null } != null)
        assertEquals("아빠가 도와줬어", d.s.slots["companion"])
        assertEquals("child", d.s.slotBy["companion"])
        assertTrue("부모 리포트 인용에 없다", "아빠가 도와줬어" in d.s.quotes)
        assertEquals("오또는 구운 짧은 말로만 받는다", "그렇구나! 계속 그려 봐.", d.s.line)
    }

    @Test
    fun aSecondEventIsAddedToTheSlotNotWrittenOver() = drawing({ turn(listOf("problem" to "파도에 무너졌다")) }) { d, asked ->
        d.s.slots["problem"] = "모래성 만들었어"; d.s.slotBy["problem"] = "child"; d.s.problem = "모래성 만들었어"
        d.tell("파도가 와서 무너졌어") { asked.isNotEmpty() }
        assertTrue(await { d.s.slots["problem"] != "모래성 만들었어" } != null)
        assertEquals("모래성 만들었어 / 파도가 와서 무너졌어", d.s.slots["problem"])
    }

    @Test
    fun aStoryNoSlotFitsIsKeptInExtraOneAfterAnother() = drawing({ turn(emptyList()) }) { d, asked ->
        d.tell("나 이거 진짜 좋아해") { asked.size == 1 }
        assertTrue(await { d.s.slots["extra"] != null } != null)
        d.tell("엄마도 같이 했어") { asked.size == 2 }
        assertTrue(await { d.s.slots["extra"]?.contains("/") == true } != null)
        assertEquals("나 이거 진짜 좋아해 / 엄마도 같이 했어", d.s.slots["extra"])
        assertEquals("child", d.s.slotBy["extra"])
    }

    @Test
    fun aShortWordNobodyAskedForIsNotSentToTheJudge() = drawing({ turn(emptyList()) }) { d, asked ->
        d.tell("응") { d.s.line == "그렇구나! 계속 그려 봐." }
        delay(500)
        assertTrue("짧은 말까지 판정에 보냈다 — ${asked.size}번", asked.isEmpty())
    }

    /** 서버가 책을 못 쓰면 앱이 쓴다 — 덧붙은 칸이 「… / …」 그대로 나오지 않고 문장마다 「~요」 */
    @Test
    fun theAppsOwnBookWritesEachAddedEventAsItsOwnSentence() {
        val book = buildDiaryBook(DiaryBookInput(
            lines = mapOf("place" to "바닷가", "problem" to "모래성 만들었어 / 파도가 와서 무너졌어"),
            by = mapOf("place" to "child", "problem" to "child"),
            pieceNames = emptyList(), hasDrawing = false, feel = null, written = null, missions = false, puzzle = false,
        ))
        val text = book.first { it.kind == DiaryPageKind.PROBLEM }.text
        assertFalse("「/」가 책에 나왔다: $text", "/" in text)
        assertTrue(text, "모래성" in text && "무너졌" in text)
    }
}
