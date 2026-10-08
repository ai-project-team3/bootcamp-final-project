package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PICTURE_QUESTIONS
import com.example.finalproject_demo.demo.PIECE_STORY_D1
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #220 ② — 그린 사람 · 물건마다 이야기를 묻는다(그리는 중 최대 [PIECE_STORY_D1] · 다 그린 뒤 못 물은 것 2개까지).
 * 말투는 그림 실마리(`DiaryClue`)와 같다 — 그린 사실만 말하고 열린 질문(있었던 일로 단정하지 않는다).
 * 답은 조각과 짝지어 두고 책 재료(`extra`)에 「이름: 말」로 쌓는다. 「누구랑」은 다 그린 뒤 장소 다음에.
 */
class DiaryPieceStoryTest {

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun run(block: suspend CoroutineScope.(Director) -> Unit) = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        d.s.speed = 0.01
        d.s.mode = StoryMode.DIARY
        try { block(d) } finally { sup.cancel() }
    }

    /** 그림판에 이름 붙은 조각들을 놓는다 — 장소는 이미 들었다(「여기는 어디야?」가 먼저 나오지 않게) */
    private suspend fun Director.board(vararg names: String) {
        go(Scene.DIARY)
        assertTrue(await { s.stage is DiaryStart } != null)
        assertTrue(await { if (s.stage is DiaryStart) send(Reply.Tapped("draw", "그릴래")); s.stage is DiaryBoard } != null)
        s.slots["place"] = "바닷가"; s.slotBy["place"] = "child"
        names.forEachIndexed { i, _ ->
            val x = 0.1f + i * 0.2f
            val colour = listOf(Color.Blue, Color.Red, Color.Green, Color.Magenta)[i % 4]   // 같은 색 작은 것 셋은 무리로 묶인다
            s.drawing += Stroke(colour, listOf(Offset(x, .3f), Offset(x + .05f, .4f)))
        }
        s.diaryDay.catchUp(s.drawing)
        names.forEachIndexed { i, n -> s.diaryDay.pieces[i] = s.diaryDay.pieces[i].copy(name = n) }
    }

    private suspend fun Director.pauseUntil(cond: () -> Boolean) =
        assertTrue("붓을 멈춰도 오지 않았다 — 말=${s.line}", await { if (s.diaryDay.watching) send(Reply.Tapped("pause", "붓 멈춤")); cond() } != null)

    private suspend fun Director.say(text: String, until: () -> Boolean) {
        repeat(4) {
            await { s.micEnabled }
            delay(50)
            send(Reply.Spoke(text))
            if (await(1_500) { until() } != null) return
        }
        assertTrue("「$text」 뒤가 오지 않았다 — 말=${s.line}", until())
    }

    @Test
    fun aDrawnThingIsAskedAboutAndItsStoryIsKeptWithIt() = run { d ->
        d.board("양동이")
        d.pauseUntil { d.s.line == "양동이도 그렸네! 양동이 이야기 해 줄래?" }
        d.say("물 떠 왔어") { d.s.diaryDay.pieceStories.isNotEmpty() }
        val id = d.s.diaryDay.pieces.first { it.name == "양동이" }.id
        assertEquals("물 떠 왔어", d.s.diaryDay.pieceStories[id])
        assertEquals("양동이: 물 떠 왔어", d.s.slots["extra"])
        assertEquals("child", d.s.slotBy["extra"])
        assertTrue("부모 리포트 인용에 없다", "물 떠 왔어" in d.s.quotes)
    }

    @Test
    fun aDrawnPersonIsAskedWhatTheyDidNotWhatYouDidTogether() = run { d ->
        d.board("아빠")
        d.pauseUntil { d.s.line == "아빠도 그렸네! 아빠는 오늘 뭐 했어?" }
    }

    @Test
    fun atMostThreeWhileDrawingAndTheRestAfter() = run { d ->
        d.board("양동이", "삽", "조개", "공")
        val asked = mutableListOf<String>()
        repeat(PIECE_STORY_D1) {
            d.pauseUntil { d.s.line.endsWith("이야기 해 줄래?") && d.s.line !in asked }
            asked += d.s.line
            d.say("재밌었어") { d.s.diaryDay.pieceStories.size == asked.size }
        }
        // 넷째는 그리는 중에 묻지 않는다 — 다 그린 뒤 첫 질문으로
        assertTrue(await { if (d.s.diaryDay.watching) d.send(Reply.Tapped("done", "완료")); d.s.stage is DiaryAsk } != null)
        assertTrue("다 그린 뒤 못 물은 조각을 묻지 않았다 — 말=${d.s.line}", await { d.s.line.endsWith("이야기 해 줄래?") && d.s.line !in asked } != null)
        assertEquals("그리는 중에 넷 다 물었다", PIECE_STORY_D1, asked.size)
    }

    @Test
    fun whoWasThereIsAskedRightAfterWhere() {
        assertEquals(listOf("place", "companion", "problem", "reaction", "solution", "keep"), PICTURE_QUESTIONS.map { it.key })
    }
}
