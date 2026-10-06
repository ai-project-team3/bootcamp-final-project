package com.example.finalproject_demo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
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
import com.example.finalproject_demo.demo.COLOR_PAUSE_MS
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
import org.junit.Assert.assertFalse
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
        // 그림일기 머리의 날짜는 오늘 — 기준 그림이 날마다 달라지지 않게 날을 박는다
        if (d.s.diaryDay.madeOn == null) d.s.diaryDay.madeOn = java.time.LocalDate.of(2026, 10, 2)
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

    /** 오또가 듣기 시작한 뒤 [act] — 끝나고 아직 답이 없으면 null (기다리는 중인 것을 돌려준다) */
    private fun Director.listening() = CoroutineScope(Dispatchers.Default).async { withTimeoutOrNull(10_000) { awaitReply() } }
        .also { Thread.sleep(100) }

    /**
     * 한 조각을 그리다 크레용을 바꾸러 가면 1.6초가 지나도 묻지 않는다 — 크레용 뒤로는 3초 (10-01 실기기:
     * 색을 바꾸는 사이 다 그린 조각으로 알고 물었다)
     */
    @Test
    fun pickingACrayonWaitsLongerBeforeAsking() {
        val d = director()
        d.s.newDiaryDay().watching = true
        d.s.stage = DiaryBoard()
        show(d)
        val got = d.listening()
        compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(100f, 100f), Offset(300f, 200f), 200) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("crayon-3").performClick()
        compose.mainClock.advanceTimeBy(BRUSH_PAUSE_MS + 400)        // 획 뒤 1.6초는 지났지만 크레용 뒤 3초는 아직
        Thread.sleep(300)
        assertFalse("크레용을 고르는 사이 물었다", got.isCompleted)
        compose.mainClock.advanceTimeBy(COLOR_PAUSE_MS)
        assertEquals("pause", (runBlocking { got.await() } as? Reply.Tapped)?.value)
    }

    /** 오또가 지켜보지 않을 때(말하는 중 · 묻는 중) 온 붓 멈춤은 버리지 않고 남겨 둔다 — 흐름이 돌아오면 받는다 (10-02 실기기) */
    @Test
    fun aPauseWhileOttoIsBusyIsKeptForLater() {
        val d = director()
        val day = d.s.newDiaryDay()
        day.watching = false                                         // 오또가 말하는 중
        d.s.stage = DiaryBoard()
        show(d)
        compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(100f, 100f), Offset(300f, 200f), 200) }
        compose.mainClock.advanceTimeBy(BRUSH_PAUSE_MS + 300)
        assertEquals("말하는 사이 온 붓 멈춤을 버렸다", "붓 멈춤", day.pendingPause)
    }

    /** 팔레트 아래 ↶ · ↷ — 할 것이 없으면 눌리지 않는다. 지우면 되살릴 수 있고, 새로 그으면 되살릴 것이 사라진다 */
    @Test
    fun undoAndRedoButtonsUnderThePalette() {
        val d = director()
        d.s.newDiaryDay()
        d.s.stage = DiaryBoard()
        show(d)
        compose.onNodeWithTag("stroke-undo").assertIsNotEnabled()
        compose.onNodeWithTag("stroke-redo").assertIsNotEnabled()
        compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(100f, 100f), Offset(300f, 200f), 200) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("stroke-undo").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals("획이 안 지워졌다", 0, d.s.drawing.size)
        compose.onNodeWithTag("stroke-undo").assertIsNotEnabled()
        compose.onNodeWithTag("stroke-redo").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals("획이 안 되살아났다", 1, d.s.drawing.size)
        assertEquals(1, d.s.diaryDay.pieces.sumOf { it.strokes.size })
        compose.onNodeWithTag("stroke-undo").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(400f, 100f), Offset(500f, 200f), 200) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("stroke-redo").assertIsNotEnabled()
        snap("diary_board_undo")
    }

    /**
     * 그림판 자리 (#98 · #46 · 10-05 진웅) — 판은 앱 틀의 🏠(오른쪽 끝 68dp) 오른쪽에서 시작한다(일기 화면엔 🔒 가 없다).
     * 오른쪽 좁은 띠에 [다 그렸어] · [그려 줘] · 🎤(아래). 말풍선은 화면 왼쪽 끝이고 크레용 아래 ↶ ↷ 를 가리지 않는다
     */
    @Test
    fun theBoardLeavesRoomForTheTopButtonsAndTheTools() = checkBoardLayout()

    /** 20:9 처럼 가로로 긴 화면 — 크레용 칸이 아래 끝까지 내려와도 말풍선이 ↶ ↷ 를 가리지 않는다 (10-05 · 에뮬레이터에서 겹쳤다) */
    @Test
    @Config(qualifiers = "w891dp-h411dp-land-420dpi")
    fun onAWideScreenTheBubbleStillClearsTheArrows() = checkBoardLayout()

    private fun checkBoardLayout() {
        val d = director()
        d.s.newDiaryDay()
        d.s.stage = DiaryBoard()
        d.say("우와, 지금 그리는 건 뭐야? 천천히 생각해서 말해 줘도 돼!")     // 두 줄이 되는 긴 말 — 가장 높은 말풍선
        d.inputs(mic = true, next = false)
        show(d)
        val board = compose.onNodeWithTag("diary-board").getUnclippedBoundsInRoot()
        val mic = compose.onNodeWithTag("diary-mic").getUnclippedBoundsInRoot()
        val done = compose.onNodeWithTag("rail-done").getUnclippedBoundsInRoot()
        val bubble = compose.onNodeWithTag("diary-bubble").getUnclippedBoundsInRoot()
        val undo = compose.onNodeWithTag("stroke-undo").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("🏠 가 판 위에 얹힌다 — 판 왼쪽 ${board.left}", board.left >= 68.dp)
        assertTrue("도구 띠가 판을 가린다", mic.left >= board.right && done.left >= board.right)
        // 오른쪽 위 48dp 는 앱 틀의 시연 서랍(길게 누르기)이 먹는다 — [다 그렸어]가 거기 있으면 눌러도 안 된다 (10-05 실기기)
        assertTrue("[다 그렸어]가 오른쪽 위 서랍 자리에 걸렸다 — 위 ${done.top}", done.top >= 48.dp)
        assertTrue("도구 띠가 넓다 — ${root.right - board.right}", root.right - board.right <= 80.dp)
        assertTrue("🎤 가 오른쪽 아래가 아니다", root.right - mic.right < 24.dp && root.bottom - mic.bottom < 24.dp)
        assertTrue("말풍선이 화면 왼쪽 끝이 아니다 — ${bubble.left}", bubble.left < 24.dp)
        assertTrue("말풍선이 ↶ 를 가린다 — 말풍선 위 ${bubble.top} · ↶ 아래 ${undo.bottom}", bubble.top >= undo.bottom)
    }

    /** 오른쪽 띠 [다 그렸어] · [그려 줘] — 말로 「다 그렸어」 · 「그려줘」와 같은 신호. [그려 줘]는 그리기 단계(D1) 안에서 받는다 */
    @Test
    fun theRailButtonsSendDoneAndDrawMe() {
        val d = director()
        d.s.newDiaryDay().apply { watching = true; drawingTalk = true }
        d.s.drawing += Stroke(Color.Red, listOf(Offset(.1f, .3f), Offset(.2f, .6f)))
        d.s.stage = DiaryBoard()
        d.inputs(mic = true, next = false)
        show(d)
        assertEquals("drawme", (d.replyTo { compose.onNodeWithTag("rail-drawme").onChildren().onFirst().performClick() } as? Reply.Tapped)?.value)
        assertEquals("done", (d.replyTo { compose.onNodeWithTag("rail-done").onChildren().onFirst().performClick() } as? Reply.Tapped)?.value)
    }

    /** 오또가 말하거나 묻는 중에도 [그려 줘] · [이름 고치기]가 눌린다 — 전에는 지켜볼 때만이라 흐리게 꺼졌다 (10-05 진웅) */
    @Test
    fun theRailToolsWorkWhileOttoTalks() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.apply { watching = false; drawingTalk = true }     // 오또가 묻는 중
        d.s.stage = DiaryBoard()
        d.say("우와, 지금 그리는 건 뭐야?")
        show(d)
        assertEquals("drawme", (d.replyTo { compose.onNodeWithTag("rail-drawme").onChildren().onFirst().performClick() } as? Reply.Tapped)?.value)
        assertEquals("rename", (d.replyTo { compose.onNodeWithTag("rail-rename").onChildren().onFirst().performClick() } as? Reply.Tapped)?.value)
    }

    /** 다 그린 뒤(D1 밖)에는 꺼 둔다 — 다음 질문의 답으로 섞이지 않게 */
    @Test
    fun theRailToolsAreOffOnceDrawingIsOver() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.drawingTalk = false
        d.s.stage = DiaryBoard()
        show(d)
        compose.onNodeWithTag("rail-drawme").onChildren().onFirst().assertIsNotEnabled()
        compose.onNodeWithTag("rail-rename").onChildren().onFirst().assertIsNotEnabled()
    }

    /** 이름표를 누르면 그 조각이 골라지고(청록 이름표) 오또가 묻는 중에도 받는다. 새 획을 그으면 고른 것이 풀린다 (10-05 진웅) */
    @Test
    fun tappingATagWhileOttoAsksSelectsThatPiece() {
        val d = director()
        drawDay(d)
        val day = d.s.diaryDay.apply { watching = false; drawingTalk = true }
        d.s.stage = DiaryBoard()
        d.say("우와, 지금 그리는 건 뭐야?")
        show(d)
        val sun = day.pieces.first { it.name == "해" }.id
        assertEquals("name:$sun", (d.replyTo { compose.onNodeWithTag("tag-$sun").performClick() } as? Reply.Tapped)?.value)
        assertEquals(sun to d.s.drawing.size, day.focus)
        compose.mainClock.advanceTimeBy(300)
        snap("diary_board_tag_selected")
    }

    /** 천천히 긋는 둘째 획 — 앞 획 뒤 1.6초가 지나도 손가락이 판에 있으면 묻지 않는다. 손을 떼고 조용하면 묻는다 */
    @Test
    fun aSlowStrokeIsNotCutOffByThePause() {
        val d = director()
        d.s.newDiaryDay().watching = true
        d.s.stage = DiaryBoard()
        show(d)
        val got = d.listening()
        compose.onNodeWithTag("diary-board").performTouchInput { swipe(Offset(100f, 100f), Offset(300f, 200f), 200) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("diary-board").performTouchInput { down(Offset(400f, 300f)); moveBy(Offset(60f, 0f)) }
        compose.mainClock.advanceTimeBy(BRUSH_PAUSE_MS * 2)            // 손가락을 댄 채
        Thread.sleep(300)
        assertFalse("긋는 중에 물었다", got.isCompleted)
        compose.onNodeWithTag("diary-board").performTouchInput { moveBy(Offset(60f, 40f)); up() }
        compose.mainClock.advanceTimeBy(BRUSH_PAUSE_MS + 300)
        assertEquals("pause", (runBlocking { got.await() } as? Reply.Tapped)?.value)
        assertEquals(2, d.s.drawing.size)
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

    /** 그림 없이 만든 일기 — 그림 칸에 아이가 말한 곳의 펠트 그림. 전에는 ✏️ 하나였다 (#98) */
    @Test
    fun aDiaryWithoutDrawingShowsThePlaceItWasAbout() {
        val d = director()
        d.s.newDiaryDay()
        d.s.slots["place"] = "놀이터 갔어"; d.s.slotBy["place"] = "child"
        d.s.slots["problem"] = "그네 탔어"; d.s.slotBy["problem"] = "child"
        d.s.stage = DiaryPaper(0)
        show(d)
        compose.onNodeWithTag("d5-place").assertExists()
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

    /** 1쪽(「○○을 그렸어요」)은 오또 그림을 골랐어도 아이 그림 그대로 — 다른 쪽에서는 고른 모습 (10-01) */
    @Test
    fun theFirstPageKeepsTheChildsOwnLines() {
        val d = director()
        drawDay(d)
        d.s.diaryDay.pieces[2] = d.s.diaryDay.pieces[2].copy(look = PieceLook.OTTO)
        val sun = d.s.diaryDay.pieces[2].id
        d.s.slots["place"] = "우리 집 앞에서 놀았어"; d.s.slotBy["place"] = "child"
        d.s.stage = DiaryPaper(0)
        show(d)
        compose.onNodeWithTag("otto-look-$sun").assertDoesNotExist()
        d.s.stage = DiaryPaper(1)
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithTag("otto-look-$sun").assertExists()
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

    /**
     * 오또 그림이 온 자리에 새로 그은 선은 오또 그림 위에 보인다 — 아래에 깔려 어떻게 그리는지 안 보였다
     * (10-06 진웅 실기기). 오또 그림으로 바꾼 조각의 원래 선은 그대로 숨는다
     */
    @Test
    fun aNewStrokeOverOttosDrawingStaysOnTop() {
        val d = director()
        d.s.drawing += line(Color(0xFF3060C0), .30f, .30f, .50f, .30f, .50f, .70f, .30f, .70f, .30f, .30f)
        val day = d.s.newDiaryDay()
        day.catchUp(d.s.drawing)
        val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(android.graphics.Color.rgb(40, 90, 230))     // 꽉 찬 파랑
        val png = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        day.pieces[0] = day.pieces[0].copy(name = "집", look = PieceLook.OTTO, ottoPng = png)
        d.s.drawing += line(Color(0xFFE8604C), .25f, .50f, .55f, .50f)        // 오또 그림 위를 가로지르는 새 선
        d.s.stage = DiaryBoard()
        show(d)
        // 기준 대조 없이 기록만 — captureToImage 는 멈춘 시계 때문에 시간 초과가 난다 (ScreenShotTest.snap)
        val shot = File("build/tmp/diary_stroke_over_otto.png")
        compose.onNodeWithTag("diary-board").captureRoboImage(
            shot.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val board = android.graphics.BitmapFactory.decodeFile(shot.path)
        val px = board.getPixel((board.width * 0.40f).toInt(), (board.height * 0.50f).toInt())
        val (r, b) = android.graphics.Color.red(px) to android.graphics.Color.blue(px)
        assertTrue("새 선이 오또 그림 아래에 깔렸다 — rgb(${r}, ${android.graphics.Color.green(px)}, ${b})", r > 180 && b < 140)
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
