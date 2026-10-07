package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PICTURE_QUESTIONS
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.cropFor
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.pictureCrop
import com.example.finalproject_demo.demo.wishEcho
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #220 실기기(10-07 · 진웅 · main 02153051)에서 나온 것.
 * 3 배경을 보고 물은 「여기는 어디야?」의 답이 배경 조각 이름이 되지 않아 다 그린 뒤 「이건 뭐 그린 거야?」로 또 물었다
 * 2·4·5 조각 이야기가 판정에 가지 않아 누구랑 · 결말을 또 묻고, 오또는 고정 「그랬구나!」만 했다 · 그림 고르는 중 한 이야기가 사라졌다
 * 6 일어난 일을 들었는데 기분을 묻지 않았다 · 1·7 오또가 그린 배경이 판 전체인데 그림 칸은 그린 부분만 잘라 배경이 밀렸다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryDevice1007Test {
    private suspend fun await(ms: Long = 6_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(5); true }

    private fun turn(fills: List<Pair<String, String>>, ack: String): String {
        val judge = JSONObject().put("reason", "ok").put("next_slot", JSONObject.NULL).put("story_ready", false).put("s1_reason", false)
        fills.forEachIndexed { i, (slot, value) -> judge.put("slot_${i + 1}", slot).put("value_${i + 1}", value) }
        val line = JSONObject().put("ack", ack).put("expand", JSONObject.NULL).put("question", JSONObject.NULL)
        return JSONObject().put("judge", judge).put("line", line).toString()
    }

    private fun run(live: ((JSONObject) -> String?)? = null, block: suspend (Director, MutableList<JSONObject>) -> Unit) = runBlocking {
        val asked = mutableListOf<JSONObject>()
        val http = live?.let { f -> FakeHttp { path, body -> if (path == "/turn") { val t = JSONObject(body); asked += t; f(t) } else null } }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        if (http != null) { Server.base = http.base; Server.liveModes = setOf(StoryMode.DIARY) }
        try { block(d, asked) } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            Server.liveModes = emptySet(); Server.base = null
            http?.close()
        }
    }

    private suspend fun Director.board() {
        go(Scene.DIARY)
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
    }

    private suspend fun Director.tell(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled }
            delay(80)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    private fun Director.pause() { if (s.diaryDay.watching) send(Reply.Tapped("pause", "붓 멈춤")) }

    /** 3 — 배경을 보고 물은 장소의 답은 그 배경 조각의 이름이다. 다 그린 뒤 「이건 뭐 그린 거야?」로 배경을 다시 묻지 않는다 */
    @Test
    fun thePlaceSaidForABackgroundNamesIt() = run { d, _ ->
        d.board()
        d.s.drawing += (0..2).map { k -> Stroke(Color.Blue, listOf(Offset(0.02f, 0.6f + k * 0.05f), Offset(0.98f, 0.62f + k * 0.05f))) }
        d.s.diaryDay.catchUp(d.s.drawing)
        assertEquals(PieceRole.BACKGROUND, d.s.diaryDay.pieces.single().role)
        assertTrue(await { d.pause(); d.s.line == "여기는 어디야?" } != null)
        d.tell("바닷가 갔어") { d.s.diaryDay.pieces.single().name != null }
        assertEquals("바닷가", d.s.diaryDay.pieces.single().name)
    }

    /** 4 — 오또 그림을 고르는 중에 한 이야기는 고르기 답이 아니다. 버리지 않고 묻지 않은 이야기로 받는다 */
    @Test
    fun aStoryToldWhilePickingOttosDrawingIsKept() = run { d, _ ->
        d.board()
        d.s.slots["place"] = "바닷가"; d.s.slotBy["place"] = "child"
        d.s.drawing += Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))
        d.s.diaryDay.catchUp(d.s.drawing)
        d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "아빠")
        d.tell("그려줘") { "그려 볼게" in d.s.line }
        assertTrue("오또 그림을 고르게 하지 않았다 — 말=${d.s.line}", await { d.pause(); d.s.line == "짠! 나도 그려 봤어! 어떤 게 좋아?" } != null)
        d.tell("아빠가 모래성 만드는 거 도와줬어") { d.s.slots["extra"] != null }
        assertEquals("아빠가 모래성 만드는 거 도와줬어", d.s.slots["extra"])
        assertEquals("고르기는 아직 기다린다", PieceLook.ORIGINAL, d.s.diaryDay.pieces[0].look)
        d.send(Reply.Tapped("otto", "오또 그림"))
        assertTrue(await { d.s.diaryDay.pieces[0].look == PieceLook.OTTO } != null)
    }

    /** 2·4·5 — 다 그린 뒤 조각 이야기는 판정에 보내 칸(누구랑 · 결말)을 채우고, 오또는 판정이 쓴 받아 주기로 대답한다 */
    @Test
    fun aPieceStoryAfterDrawingGoesToTheJudgeAndOttoAnswersIt() = run({ turn(listOf("companion" to "아빠", "solution" to "다시 만들었다"), "아빠랑 같이 다시 만들었구나!") }) { d, asked ->
        d.board()
        d.s.slots["place"] = "바닷가"; d.s.slotBy["place"] = "child"
        d.s.drawing += Stroke(Color.Red, listOf(Offset(.4f, .3f), Offset(.42f, .6f)))
        d.s.diaryDay.catchUp(d.s.drawing)
        d.s.diaryDay.pieces[0] = d.s.diaryDay.pieces[0].copy(name = "아빠")
        d.s.diaryDay.pieceStoryAsked.clear()
        assertTrue(await { if (d.s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); d.s.stage is DiaryAsk } != null)
        assertTrue(await { d.s.line == "아빠도 그렸네! 아빠는 오늘 뭐 했어?" } != null)
        assertTrue(await { d.s.micEnabled } != null)
        delay(80)
        d.send(Reply.Spoke("모래성이 무너져서 아빠랑 같이 다시 만들었어"))                  // 한 번만 — 다음 질문의 답으로 새지 않게
        assertTrue("조각 이야기를 판정에 보내지 않았다", await { asked.isNotEmpty() } != null)
        assertEquals("아빠도 그렸네! 아빠는 오늘 뭐 했어?", asked.first().getString("question"))
        assertTrue(await { d.s.slots["companion"] != null } != null)
        assertEquals("child", d.s.slotBy["companion"])
        assertTrue("오또가 판정의 받아 주기로 대답하지 않았다 — 말=${d.s.line}", await { d.s.line == "아빠랑 같이 다시 만들었구나!" } != null)
        assertTrue("조각 이야기가 책 재료에 없다", d.s.slots["extra"]?.startsWith("아빠: ") == true)
    }

    /** 6 — 일어난 일 다음에 그때 기분을 묻는다 */
    @Test
    fun theFeelingIsAskedRightAfterWhatHappened() {
        assertEquals(listOf("place", "companion", "problem", "reaction", "solution", "keep"), PICTURE_QUESTIONS.map { it.key })
    }

    /** 2 — 판정이 받아 주기를 안 줬을 때 「내일」의 바람은 그 말로 받는다(「그랬구나!」만 하지 않는다) */
    @Test
    fun aWishIsEchoedWhenTheServerGivesNoLine() {
        assertEquals("탕수육 먹고 싶구나!", wishEcho("탕수육 먹고 싶어."))
        assertEquals("또 바다 갈 거구나!", wishEcho("또 바다 갈 거야"))
        assertNull("모양을 모르면 되받지 않는다", wishEcho("자전거"))
    }

    /** 1·7 — 오또가 그린 배경은 판 전체 그림이다. 그 배경이 있으면 그림 칸도 판 전체를 기준으로 — 배경이 밀리지 않는다 */
    @Test
    fun withOttosBackgroundThePictureFramesTheWholeBoard() {
        fun st(x: Float, y: Float) = Stroke(Color.Blue, listOf(Offset(x, y), Offset(x + .1f, y + .1f)))
        val bg = DiaryPiece(0, listOf(Stroke(Color.Blue, listOf(Offset(0f, .7f), Offset(1f, .75f)))), "바닷가", role = PieceRole.BACKGROUND)
        val dad = DiaryPiece(1, listOf(st(.4f, .4f)), "아빠")
        assertEquals("오또 배경이 없으면 그린 부분만", cropFor((bg.strokes + dad.strokes), 1.7f, 3f), pictureCrop(listOf(bg, dad), 1.7f, 3f))
        val crop = pictureCrop(listOf(bg.copy(look = PieceLook.OTTO), dad), 1.7f, 3f)
        assertTrue("판 전체가 안 들어간다: $crop", crop.left <= 0f && crop.right >= 1f && crop.top <= 0f && crop.bottom >= 1f)
    }
}
