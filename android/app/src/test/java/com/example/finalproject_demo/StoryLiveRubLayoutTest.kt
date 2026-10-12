package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.BookPageView
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
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
@OptIn(ExperimentalRoborazziApi::class)
class StoryLiveRubLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun stainsBelongToVisibleGround(label: String) {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val previousFrozen = motionFrozen
        val scope = CoroutineScope(SupervisorJob())
        try {
            Server.base = "http://localhost:1"
            Server.liveModes = setOf(StoryMode.STORY)
            motionFrozen = true
            val d = Director(scope)
            d.s.templateKey = "C"
            d.s.newcomerKind = "고양이"
            d.s.slots["newcomer"] = "고양이"   // a live story's friend is the newcomer the child told (StoryNoFriendTest)
            val page = (1..d.s.pageCount).first { d.s.pageKind(it) == PageKind.RUB }
            compose.mainClock.autoAdvance = false
            compose.setContent { BookPageView(d, Stage.BookPage(page)) }
            val item = d.s.mission1().blobName
            val ground = compose.onNodeWithContentDescription("$item 지우는 자리").fetchSemanticsNode().boundsInRoot
            val hero = compose.onNodeWithContentDescription("미션 주인공 인형").fetchSemanticsNode().boundsInRoot
            val friend = compose.onNodeWithContentDescription("미션 친구 인형").fetchSemanticsNode().boundsInRoot
            repeat(3) { i ->
                val stain = compose.onNodeWithContentDescription("$item 흔적 ${i + 1}").fetchSemanticsNode().boundsInRoot
                assertTrue("stain=$stain outside visible ground=$ground", ground.contains(stain.center))
                assertTrue("stain is clipped at the edge of the ground: $stain / $ground",
                    ground.contains(stain.topLeft) && ground.contains(stain.bottomRight))
                assertTrue("stain overlaps a puppet: $stain / hero=$hero friend=$friend",
                    !stain.overlaps(hero) && !stain.overlaps(friend))
                assertTrue("stains still use the hidden ride's upper-page position", stain.center.y > ground.top)
            }
            val image = File("build/qa/story-rub-$label.png").apply { parentFile?.mkdirs() }
            compose.onRoot().captureRoboImage(image.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally {
            scope.cancel()
            motionFrozen = previousFrozen
            Server.base = previousBase
            Server.liveModes = previousModes
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w807dp-h393dp-land-160dpi")
    fun phoneStainsAreOnTheVisibleGround() = stainsBelongToVisibleGround("phone")

    @Test @Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-160dpi")
    fun tabletStainsAreOnTheVisibleGround() = stainsBelongToVisibleGround("tablet")
}
