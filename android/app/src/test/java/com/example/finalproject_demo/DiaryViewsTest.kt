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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.BRUSH_PAUSE_MS
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.MascotBubble
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
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
 * 그림일기 화면 (ui/DiaryViews.kt) — 모양은 사진으로 남기고(`screens/diary_*.png`),
 * 아이가 **실제로 누르는 것**이 감독에게 가는지를 검사한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class DiaryViewsTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String) = compose.onRoot().captureRoboImage(
        File("screens/$name.png").path,
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    private fun director() = Director(CoroutineScope(SupervisorJob())).apply {
        s.speed = 0.0
        s.mode = StoryMode.DIARY
        s.scene = Scene.DIARY
        s.drawingAspect = 2.2f
    }

    private fun line(c: Color, vararg xy: Float) = Stroke(c, xy.toList().chunked(2).map { Offset(it[0], it[1]) })

    /** 집 · 나 · 해 — 해는 이름이 붙어 날씨가 저절로 켜진다 */
    private fun drawDay(d: Director) {
        val blue = Color(0xFF3F7BD9); val red = Color(0xFFE8604C); val sun = Color(0xFFF3C33C)
        d.s.drawing += line(blue, .10f, .45f, .30f, .45f, .30f, .85f, .10f, .85f, .10f, .45f)
        d.s.drawing += line(red, .08f, .47f, .20f, .25f, .32f, .47f)
        d.s.drawing += line(Color(0xFF3A2A1E), .50f, .45f, .52f, .75f)
        d.s.drawing += line(Color(0xFF3A2A1E), .45f, .55f, .58f, .55f)
        d.s.drawing += line(sun, .80f, .10f, .88f, .10f, .88f, .25f, .80f, .25f, .80f, .10f)
        val day = d.s.newDiaryDay()
        day.catchUp(d.s.drawing)
        day.pieces[0] = day.pieces[0].copy(name = "우리 집")
        day.pieces[1] = day.pieces[1].copy(name = "나")
        day.pieces[2] = day.pieces[2].copy(name = "해")
    }

    private fun show(d: Director) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) {
                StageView(d)
                MascotBubble(d, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        compose.mainClock.advanceTimeBy(2_500)
    }

    /**
     * 감독이 **먼저 듣기 시작한 뒤** [act] 를 한다 — `awaitReply` 는 듣기 전에 온 입력을 버리기 때문이다(앞 장면 입력 섞임 방지).
     * 듣는 쪽은 다른 스레드에 둔다 — 테스트 스레드는 화면을 움직이는 데 쓴다.
     */
    private fun Director.replyTo(act: () -> Unit): Reply? {
        val r = CoroutineScope(Dispatchers.Default).async { withTimeoutOrNull(5_000) { awaitReply() } }
        Thread.sleep(100)                     // let it start listening (drain, then wait)
        act()
        return runBlocking { r.await() }
    }

    @Test
    fun theBoardShowsNameTagsOnNamedPieces() {
        val d = director()
        drawDay(d)
        d.s.stage = DiaryBoard()
        d.say("우와, 지금 그리는 건 뭐야?")
        show(d)
        compose.onNodeWithText("우리 집").assertExists()
        compose.onNodeWithText("해").assertExists()
        snap("diary_board_pieces")
    }

    @Test
    fun ottosDrawingIsPickedBesideTheOriginal() {
        val d = director()
        drawDay(d)
        d.s.stage = DiaryBoard(pick = 0)
        d.say("짠! 나도 우리 집을 그려 봤어! 어떤 게 좋아?")
        show(d)
        snap("diary_board_otto_pick")
        assertEquals("otto", (d.replyTo { compose.onNodeWithText("오또 그림 ✨").performClick() } as? Reply.Tapped)?.value)
    }

    @Test
    fun aStrokeThenAQuietBrushTellsOttoToAsk() {
        val d = director()
        d.s.newDiaryDay()
        d.s.stage = DiaryBoard()
        show(d)
        // a stopped test clock never recomposes after the gesture, so the pause timer never starts — let it run
        compose.mainClock.autoAdvance = true
        val r = d.replyTo {
            compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(100f, 100f), Offset(300f, 200f), 200) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(BRUSH_PAUSE_MS + 200)   // then the brush stays quiet
        }
        assertEquals("a stroke goes into the drawing as soon as it is drawn", 1, d.s.drawing.size)
        assertEquals("pause", (r as? Reply.Tapped)?.value)
    }

    @Test
    fun aPictureDiaryPageWithTheFeelingStillToPick() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.pieces[2] = d.s.diaryDay.pieces[2].copy(look = PieceLook.OTTO)
        d.s.diaryDay.weatherFromDrawing()
        d.s.title = "우리 집 앞에서"
        d.s.slots["place"] = "우리 집 앞에서 놀았어"; d.s.slotBy["place"] = "child"
        d.s.slots["problem"] = "나 혼자 뛰다가 넘어졌어"; d.s.slotBy["problem"] = "child"
        d.s.stage = DiaryPaper(2)
        d.say("나 혼자 뛰다가 넘어졌어요.")
        show(d)
        compose.mainClock.advanceTimeBy(6_000)
        snap("diary_paper_problem")

        assertEquals("feel:EXCITED", (d.replyTo { compose.onNodeWithTag("feel-${DiaryFeel.EXCITED.name}").performClick() } as? Reply.Tapped)?.value)
        assertEquals("wx:RAIN", (d.replyTo { compose.onNodeWithTag("wx-${DiaryWeather.RAIN.name}").performClick() } as? Reply.Tapped)?.value)
    }

    @Test
    fun theFirstPageIsTheWholeDrawing() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.weatherFromDrawing()
        d.s.title = "우리 집 앞에서"
        d.s.slots["place"] = "우리 집 앞에서 놀았어"; d.s.slotBy["place"] = "child"
        d.s.stage = DiaryPaper(0)
        d.say("나는 오늘 우리 집, 나, 해를 그렸어요.")
        show(d)
        compose.mainClock.advanceTimeBy(6_000)
        snap("diary_paper_drawing")
        assertTrue("날씨가 그림의 해에서 켜지지 않았다", d.s.diaryDay.weather == DiaryWeather.SUN)
    }
}
