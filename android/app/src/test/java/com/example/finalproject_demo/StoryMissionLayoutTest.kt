package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.BookPageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryMissionLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun nextButtonIsClear() {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(SupervisorJob())
        try {
            Server.base = null
            Server.liveModes = emptySet()
            val d = Director(scope)
            d.s.themeKey = "space"
            d.s.dinoKey = "alienbud"
            d.s.templateKey = "C"
            val page = d.s.template!!.pages.indexOfFirst { it.kind == PageKind.RUB } + 1
            compose.mainClock.autoAdvance = false
            compose.setContent { BookPageView(d, Stage.BookPage(page, m1Done = true)) }
            val figure = compose.onNodeWithContentDescription("동행 인형 ${d.s.dino.name}").fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithText("▶").fetchSemanticsNode().boundsInRoot
            assertTrue("the puppet covers next: figure=$figure next=$next", figure.right < next.left)
        } finally {
            scope.cancel()
            Server.base = previousBase
            Server.liveModes = previousModes
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w807dp-h393dp-land-160dpi")
    fun phoneNextButtonStaysClear() = nextButtonIsClear()

    @Test @Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-160dpi")
    fun tabletNextButtonStaysClear() = nextButtonIsClear()
}
