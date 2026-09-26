package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.FloatingControls
import com.example.finalproject_demo.ui.MascotBubble
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.TitleChip
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
@OptIn(ExperimentalRoborazziApi::class)
class DiaryWhiteboardShotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String) = compose.onRoot().captureRoboImage(
        File("screens/$name.png").path,
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    private fun director(): Director = Director(CoroutineScope(SupervisorJob())).apply {
        s.speed = 0.0
        s.mode = StoryMode.DIARY
        s.scene = Scene.DIARY
    }

    private fun drawSample(d: Director) {
        val blue = Color(0xFF3F7BD9)
        val orange = Color(0xFFF08A3C)
        d.s.drawing += Stroke(blue, listOf(Offset(.15f, .62f), Offset(.42f, .62f), Offset(.42f, .88f), Offset(.15f, .88f), Offset(.15f, .62f)))
        d.s.drawing += Stroke(orange, listOf(Offset(.48f, .62f), Offset(.75f, .62f), Offset(.75f, .88f), Offset(.48f, .88f), Offset(.48f, .62f)))
        d.s.drawing += Stroke(blue, listOf(Offset(.32f, .30f), Offset(.59f, .30f), Offset(.59f, .56f), Offset(.32f, .56f), Offset(.32f, .30f)))
        d.s.keepSceneDrawing()
    }

    @Test
    fun whiteboardStart() {
        val d = director().apply {
            s.stage = Stage.DrawPad()
            say("지호야, 오늘 있었던 일 하나를 그려 볼래? 생각나는 것부터 그려 줘.")
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) {
                StageView(d)
                TitleChip("오늘 있었던 일", modifier = Modifier.align(Alignment.TopCenter))
                MascotBubble(d, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        compose.mainClock.advanceTimeBy(2200)
        snap("diary_whiteboard_draw")
    }

    @Test
    fun whiteboardInterview() {
        val d = director()
        drawSample(d)
        d.s.stage = Stage.Show(d.s.sceneArt, "오늘 그린 그림")
        d.say("블록처럼 보이네. 무엇을 그렸어?")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) {
                StageView(d)
                TitleChip("그림 이야기", modifier = Modifier.align(Alignment.TopCenter))
                MascotBubble(d, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        compose.mainClock.advanceTimeBy(2200)
        snap("diary_whiteboard_interview")
    }

    @Test
    fun questionWhileDrawing() {
        val d = director().apply {
            s.stage = Stage.DrawPad()
            say("블록처럼 보이네. 무엇을 그렸어?")
            inputs(mic = true, next = true)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) {
                StageView(d)
                TitleChip("그리며 이야기", modifier = Modifier.align(Alignment.TopCenter))
                MascotBubble(d, Modifier.align(Alignment.BottomStart).padding(8.dp))
                FloatingControls(d, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        compose.mainClock.advanceTimeBy(2200)
        snap("diary_whiteboard_live_question")
    }

    @Test
    fun whiteboardInBook() {
        val d = director()
        drawSample(d)
        d.s.place = "어린이집"
        d.s.placeLabel = "어린이집"
        d.s.slots["place"] = "어린이집에 갔어요"
        d.s.slots["whiteboard"] = "블록을 쌓은 걸 그렸어."
        d.s.stage = Stage.BookPage(1)
        d.s.scene = Scene.BOOK
        compose.mainClock.autoAdvance = false
        compose.setContent { StageView(d) }
        snap("diary_whiteboard_book")
    }
}
