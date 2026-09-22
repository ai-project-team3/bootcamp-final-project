package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.autoTitleFor
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.ParentView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 부모 모드는 **세 모드 중 어느 것을 하고 와도 안 터져야 한다** (`guidelines/9` §9-9의 남은 체크).
 *
 * 각 모드를 실제로 한 바퀴 돌린 뒤(대본 답으로) 부모 화면 세 탭을 그려 본다.
 * 그림은 `app/build/screens/parent/` 에 **기록만** 한다 — 기준 대조는 하지 않는다.
 * 이 검사가 잡는 것은 두 가지다: 그리다가 죽는가, 그리고 모드에 안 맞는 문구가 나오는가(눈으로).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h1700dp-440dpi")
class ParentViewTest {

    @get:Rule
    val compose = createComposeRule()

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) {
            while (!cond()) delay(3)
            true
        }

    private suspend fun Director.tap(part: String): Boolean {
        repeat(14) {
            if (await(2_500) { s.buttons.any { b -> part in b.label } } == null) return false
            s.buttons.first { part in it.label }.onClick()
            if (await(500) { s.buttons.none { b -> part in b.label } } != null) return true
        }
        return false
    }

    private suspend fun Director.push(part: String): Boolean {
        repeat(12) {
            val b = s.buttons.firstOrNull { part in it.label } ?: return false
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || s.buttons.none { x -> part in x.label } } != null) return true
        }
        return false
    }

    private suspend fun Director.answerAll(button: String = "🎲", max: Int = 30): Int {
        var n = 0
        while (s.scene == Scene.DIARY && n < max) {
            if (await(2_000) { s.buttons.any { button in it.label } } == null) break
            if (!push(button)) break
            n++
            delay(40)
        }
        return n
    }

    /**
     * 일기 · 협업 질문을 끝까지 답하고 책까지 간다.
     * 민우님의 「🎬 오늘 이야기 시연 답」(한 아이의 하루로 이어지는 결정적 대본)이 있으면 그것을, 없으면 무작위 답을 쓴다.
     */
    private suspend fun Director.diaryToBook() {
        val demo = "🎬 오늘 이야기 시연 답"
        if (await(2_000) { s.buttons.any { demo in it.label } } != null) answerAll(demo) else answerAll()
        if (await(3_000) { s.buttons.any { "안 그릴래" in it.label } } != null) tap("안 그릴래")
        assertTrue("책까지 못 갔다 scene=${s.scene} end=${s.endReason}", await(20_000) { s.scene == Scene.BOOK } != null)
    }

    /** 감독을 띄워 흐름을 돌리고, 끝난 상태를 그대로 돌려준다. 그 뒤 화면을 그린다. */
    private fun drive(block: suspend (Director) -> Unit): Director {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(sup))
        d.s.speed = 0.01
        runBlocking {
            try {
                block(d)
            } finally {
                sup.cancel()
            }
        }
        return d
    }

    /**
     * 세 탭을 차례로 그린다. `setContent` 는 검사마다 한 번만 부를 수 있어 탭을 상태로 바꾼다.
     * 화면은 스크롤이라 실제 폰 높이로 찍으면 아래가 잘린다 — 검토용이므로 세로를 길게 잡아 전부 보이게 한다.
     */
    private fun shotAll(d: Director, name: String) {
        var tab by mutableStateOf("rec")
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) { ParentView(d, tab) }
        }
        listOf("rec", "coop", "ach", "set").forEach { t ->
            tab = t
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(
                File("build/screens/parent/${name}_$t.png").path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
        }
    }

    /** 아무것도 안 만들고 첫 화면에서 바로 부모 탭을 눌렀을 때 */
    @Test
    fun parentModeDrawsBeforeAnyBookExists() {
        val d = drive { }
        assertEquals(StoryMode.STORY, d.s.mode)
        shotAll(d, "fresh")
    }

    /**
     * 동화 모드로 책 한 권을 만든 뒤.
     * 동화 흐름은 장면마다 버튼이 달라 대본으로 밀기 어렵다 — 시연 서랍이 하는 것처럼 끝 상태를 직접 심는다.
     */
    @Test
    fun parentModeDrawsAfterStory() {
        val d = drive { d ->
            val s = d.s
            s.mode = StoryMode.STORY
            s.partnerKey = "mom"
            s.themeKey = "space"
            s.templateKey = "C"
            s.place = "우주"; s.problem = "외계인이 로켓을 흔들었어"; s.cause = "친구가 없어서 심심했어"
            s.newcomer = "외계인"; s.sound = "뿌우 (원본)"; s.solution = "같이 별을 땄어"
            s.friendName = "뭉치"
            s.slots["response"] = "그만해!"; s.slots["helper"] = "뭉치"; s.slots["resolve"] = "같이 놀았어"
            s.quotes += listOf("우주로 가자!", "외계인이 장난쳐서 흔들었어", "무서워서 도망갔어")
            s.signals += listOf("S1 — \"무서워서 도망갔어\" · 까닭", "S2 — \"뿌뿌도 데려갈래\" · 계기")
            s.s1count = 1
            s.feelings += "무서웠"
            s.modeVoice = 7; s.modeCard = 2; s.modeDraw = 1; s.modeSilent = 1
            s.partnerTurns = 1; s.partnerHelp = "별을 따서 목걸이 만들래"
            s.title = s.autoTitleFor()
            s.scene = Scene.BOOK
        }
        shotAll(d, "story")
    }

    /**
     * 협업 질문 탭 — 부모가 질문을 넣어 둔 상태. 좋은 질문 하나, 귀띔이 붙는 질문 셋, 빈 자리 하나.
     * 귀띔 셋이 다 보이는지, 빈 줄에 귀띔이 없는지 눈으로 본다.
     */
    @Test
    fun coopQuestionTabShowsHintsButNoScores() {
        val d = drive { d ->
            d.s.parentQuestions += listOf(
                "오늘 어디 갔었어?",          // 좋은 질문
                "재밌었어?",                 // 예/아니오
                "누구랑 뭐 하고 놀았어?",     // 의문사 둘
                "언제 갔어?",                // 언제
            )
        }
        shotAll(d, "coop_questions")
    }

    /** 일기 모드를 한 바퀴 돌고 온 뒤 */
    @Test
    fun parentModeDrawsAfterDiary() {
        val d = drive { d ->
            val s = d.s
            d.go(Scene.ADULT)
            assertTrue(d.tap("오늘 있었던 일로"))
            assertTrue(await { s.scene == Scene.BESTIARY } != null)
            assertTrue(d.tap("카드를 탭"))
            assertTrue(await { s.scene == Scene.DIARY } != null)
            d.diaryToBook()
        }
        assertEquals(StoryMode.DIARY, d.s.mode)
        shotAll(d, "diary")
    }

    /** 부모 협업 모드를 한 바퀴 돌고 온 뒤 */
    @Test
    fun parentModeDrawsAfterCoop() {
        val d = drive { d ->
            val s = d.s
            d.go(Scene.ADULT)
            assertTrue(d.tap("같이 만들기"))
            assertTrue(await { s.scene == Scene.BESTIARY } != null)
            assertTrue(d.tap("카드를 탭"))
            assertTrue(await { s.scene == Scene.DIARY } != null)
            d.diaryToBook()
        }
        assertEquals(StoryMode.COOP, d.s.mode)
        shotAll(d, "coop")
    }
}
