package com.example.finalproject_demo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.finalproject_demo.demo.BRUSH_PAUSE_MS
import com.example.finalproject_demo.demo.DiaryAsk
import com.example.finalproject_demo.demo.DiaryBoard
import com.example.finalproject_demo.demo.DiaryFeel
import com.example.finalproject_demo.demo.DiaryGift
import com.example.finalproject_demo.demo.DiaryStart
import com.example.finalproject_demo.demo.DiaryStitch
import com.example.finalproject_demo.demo.DiaryPaper
import com.example.finalproject_demo.demo.DiaryWeather
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.catchUp
import com.example.finalproject_demo.demo.cropFor
import com.example.finalproject_demo.demo.diaryBookInput
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.diaryAskPose
import com.example.finalproject_demo.ui.shell.Pose
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
            // 일기 화면은 대사 칸을 스스로 그린다(D1 작은 말풍선 · D5 없음) — 앱 틀의 칸은 얹지 않는다
            Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) }
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

    /**
     * D1 말풍선 — 묻는 말은 답이 올 때까지 펼쳐 둔다. 10-01 실기기: 「나도 강아를 그려볼까?」가 4초 뒤 접혀
     * 무엇을 기다리는지 안 보였다. 그냥 하는 말은 전처럼 접힌다
     */
    @Test
    fun aQuestionStaysOpenUntilItIsAnswered() {
        val d = director()
        drawDay(d)
        d.s.stage = DiaryBoard()
        d.say("나도 우리 집을 그려볼까?")
        show(d)
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("나도 우리 집을 그려볼까?").assertExists()
        d.say("좋아, 네 그림이 최고야!")
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("좋아, 네 그림이 최고야!").assertDoesNotExist()
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
        d.s.newDiaryDay().watching = true          // 오또가 그리기를 지켜보는 중 — 이때만 붓 멈춤이 간다
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

    /** D3 — 다 그린 뒤: 엎드린 오또 옆에 아이 그림을 꽂아 두고 아래 대사 칸으로 묻는다 (docs/review/일기모드_0930 A) */
    @Test
    fun afterDrawingOttoLiesBesideThePinnedDrawing() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.pieces[2] = d.s.diaryDay.pieces[2].copy(look = PieceLook.OTTO)
        d.s.keepSceneDrawing()
        d.s.stage = DiaryAsk
        d.say("오늘 어디 갔었어?")
        show(d)
        compose.onNodeWithTag("d3-otto").assertExists()
        compose.onNodeWithTag("d3-card").assertExists()
        snap("diary_ask")
    }

    @Test
    fun aDayWithoutDrawingHasNoEmptyCard() {
        val d = director()
        d.s.newDiaryDay()
        d.s.stage = DiaryAsk
        d.say("오늘 어디 갔었어?")
        show(d)
        compose.onNodeWithTag("d3-otto").assertExists()
        compose.onNodeWithTag("d3-card").assertDoesNotExist()
        snap("diary_ask_no_drawing")
    }

    @Test
    fun ottoLooksUpWhileAskingAndWritesWhileListening() {
        assertEquals(Pose.LIE_LOOK, diaryAskPose(listening = false))
        assertEquals(Pose.LIE_WRITE, diaryAskPose(listening = true))
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

    /** 서버가 준 오또 그림(정사각형 PNG)은 납작한 선 조각에 붙어도 쪼그라들지 않는다 — 조각의 긴 변만 한 정사각형 */
    @Test
    fun ottosDrawingOnAFlatPieceIsNotSquashed() {
        val d = director()
        d.s.drawing += line(Color(0xFFE8604C), .60f, .60f, .85f, .66f)       // 선 하나 — 폭은 넓고 높이는 거의 없다
        val day = d.s.newDiaryDay()
        day.catchUp(d.s.drawing)
        val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawCircle(32f, 32f, 28f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(242, 149, 90) })
        val png = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        day.pieces[0] = day.pieces[0].copy(name = "엄마", look = PieceLook.OTTO, ottoPng = png)
        d.s.stage = DiaryBoard()
        show(d)
        snap("diary_board_otto_flat_piece")
    }

    /** D0 — 방에서 손 흔드는 오또 · [그릴래!] · [그림 없이 말할래] (docs/일기모드_UI.html) */
    @Test
    fun theStartAsksToDrawOrTalkInTheRoom() {
        val d = director()
        d.s.newDiaryDay()
        d.s.stage = DiaryStart
        d.say("오늘 있었던 일을 그려 볼래? 생각나는 것부터 그려 줘.")
        show(d)
        snap("diary_start")
        assertEquals("draw", (d.replyTo { compose.onNodeWithTag("diary-draw").performClick() } as? Reply.Tapped)?.value)
        assertEquals("skip", (d.replyTo { compose.onNodeWithTag("diary-talk").performClick() } as? Reply.Tapped)?.value)
    }

    /** D4 — 흐린 아이 그림 위 「그림일기를 꿰매는 중…」 */
    @Test
    fun theBookIsStitchedOverTheDrawing() {
        val d = director()
        drawDay(d)
        d.s.keepSceneDrawing()
        d.s.stage = DiaryStitch
        d.say("그림일기를 만들고 있어. 조금만 기다려 줘!")
        show(d)
        compose.onNodeWithText("그림일기를 꿰매는 중…").assertExists()
        snap("diary_stitch")
    }

    /** D6 — 아이 그림이 표지인 책 · [책장에 꽂기] */
    @Test
    fun theDiaryIsGivenAsABookWithTheChildsDrawingOnTheCover() {
        val d = director()
        drawDay(d)
        d.s.title = "우리 집 앞에서"
        d.s.stage = DiaryGift
        d.say("오늘 그림일기가 완성됐어! 책장에 꽂아 줄래?")
        show(d)
        compose.onNodeWithTag("d6-book").assertExists()
        snap("diary_gift")
        assertEquals("shelf", (d.replyTo { compose.onNodeWithTag("d6-shelf").performClick() } as? Reply.Tapped)?.value)
    }

    /** D5 — 조각을 톡 하면 도구대로 한마디(✋ 「우리 집 톡!」), 날씨를 누르면 그 날씨를 그린 조각이 「☀️ 해!」, 제목을 누르면 오또가 묻는다 */
    @Test
    fun aPagesPiecesTitleAndWeatherAnswerTaps() {
        val d = director()
        drawDay(d)
        d.s.title = "우리 집 앞에서"
        d.s.stage = DiaryPaper(0)
        d.say("나는 오늘 우리 집, 나, 해를 그렸어요.")
        show(d)
        // 집 가운데(판 좌표 0.2 · 0.55)를 그림 칸 좌표로 — 그림 칸은 그린 부분만 3:1 로 잘라 보인다
        val crop = cropFor(d.s.diaryDay.pieces.flatMap { it.strokes }, d.s.drawingAspect)
        val fx = (0.2f - crop.left) / crop.width
        val fy = (0.55f - crop.top) / crop.height
        compose.onNodeWithTag("d5-picture").performTouchInput { click(Offset(width * fx, height * fy)) }
        compose.mainClock.advanceTimeBy(200)                     // 한마디는 1.5초 떠 있다 — 그 안에 본다
        compose.onNodeWithTag("d5-said").assertTextContains("우리 집 톡!")
        assertEquals("wx:SUN", (d.replyTo { compose.onNodeWithTag("wx-${DiaryWeather.SUN.name}").performClick() } as? Reply.Tapped)?.value)
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("d5-said").assertTextContains("☀️ 해!")
        assertEquals("title", (d.replyTo { compose.onNodeWithTag("d5-title").performClick() } as? Reply.Tapped)?.value)
        compose.onNodeWithTag("d5-lines").performClick()          // 다시 쓰고 다시 움직인다 — 멈추지 않으면 된다
        compose.mainClock.advanceTimeBy(500)
    }

    /** 🧩 — 조각을 톡 · 자리를 톡. 틀린 자리면 「다른 자리에 맞춰 볼까?」, 셋 다 맞추면 「✨ 다 맞췄다!」 */
    @Test
    fun thePuzzleIsSolvedByTappingAPieceThenItsPlace() {
        val d = director()
        drawDay(d)
        d.s.slots["place"] = "우리 집 앞에서 놀았어"; d.s.slotBy["place"] = "child"
        val pages = com.example.finalproject_demo.demo.buildDiaryBook(d.s.diaryBookInput())
        val at = pages.indexOfFirst { it.kind == com.example.finalproject_demo.demo.DiaryPageKind.PUZZLE }
        assertTrue("놀이 쪽이 없다", at >= 0)
        d.s.stage = DiaryPaper(at)
        show(d)
        snap("diary_puzzle")
        compose.onNodeWithTag("puzzle-piece-2").performClick()
        compose.onNodeWithTag("puzzle-slot-0").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("puzzle-hint").assertTextContains("다른 자리에 맞춰 볼까?", substring = true)
        for (i in 0 until 3) {
            compose.onNodeWithTag("puzzle-piece-$i").performClick()
            compose.onNodeWithTag("puzzle-slot-$i").performClick()
            compose.mainClock.advanceTimeBy(100)
        }
        compose.onNodeWithTag("puzzle-hint").assertTextContains("다 맞췄다", substring = true)
    }
}
