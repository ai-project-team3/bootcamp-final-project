package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.StageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every book page composes on screens other than 치영's S10 (#41).
 *
 * 09-30 민우: a 1280×800 · 180 dpi tablet (16:10) closed the app on the first RUB page —
 * `Stand()` put the hero at x = 0.14 of the width minus half its width, went below 0, and a
 * negative `padding` throws. This does not compare pictures; it only proves nothing throws.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookSizesTest {
    @get:Rule val compose = createComposeRule()

    private fun book(mode: StoryMode): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.speed = 0.0
        d.s.mode = mode
        d.s.scene = Scene.BOOK
        d.s.placeLabel = "놀이터"; d.s.place = "놀이터"; d.s.slots["place"] = "놀이터에 갔어요"
        d.s.problem = "블록이 무너짐"; d.s.slots["problem"] = "높이 쌓은 블록이 와르르 무너졌어요"
        d.s.cause = "같이 놀고 싶었어"; d.s.slots["cause"] = "같이 놀고 싶어서 그랬대요"
        d.s.solution = "다시 쌓았어"; d.s.solutionLine = "다시 쌓은 블록은 이번엔 무너지지 않았어요"
        d.s.slots["solution"] = d.s.solutionLine
        d.s.title = "놀이터에서 만난 민준이"
        return d
    }

    private fun everyPageComposes() {
        var sawRub = false
        // one setContent per test: the book in front is swapped through state
        var shown by mutableStateOf(book(StoryMode.STORY))
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(shown) } }
        for (mode in listOf(StoryMode.STORY, StoryMode.DIARY)) {
            val d = book(mode)
            compose.runOnIdle { d.s.stage = Stage.BookPage(1); shown = d }
            for (p in 1..d.s.pageCount) {
                if (d.s.pageKind(p) == PageKind.RUB) sawRub = true
                compose.runOnIdle { d.s.stage = Stage.BookPage(p) }
                compose.mainClock.advanceTimeBy(400)            // throws here if a page cannot lay out
            }
        }
        assertTrue("RUB 쪽이 하나도 없었다 — 이 검사가 #41 을 못 본다", sawRub)
    }

    /** 민우 재현 조건 — 1280×800 px · 180 dpi = 16:10 */
    @Test @Config(sdk = [34], qualifiers = "w1138dp-h711dp-land-180dpi")
    fun tablet16by10() = everyPageComposes()

    /** 치영 S10 기준 폰 */
    @Test @Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
    fun phone() = everyPageComposes()

    /** 4:3 태블릿 · 작은 폰 가로 */
    @Test @Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-160dpi")
    fun tablet4by3() = everyPageComposes()

    @Test @Config(sdk = [34], qualifiers = "w640dp-h360dp-land-320dpi")
    fun smallPhone() = everyPageComposes()
}
