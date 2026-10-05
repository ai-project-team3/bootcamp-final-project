package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.demo.missions.blowProp
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * #101 · 맞춤미션 설계 §9 — C1 불기 화면은 **탭만으로 끝까지 간다**(마이크가 없어도 · 원칙 6).
 * Robolectric 에는 마이크가 없어 불기 세기는 늘 0 — 그래서 이 검사가 곧 「마이크 없는 기기」다.
 * 그림은 `build/touch/` 에 기록만 한다(눈으로 보는 용).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class BlowMissionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun coopBirthdayBook(): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.speed = 0.0
        d.s.mode = StoryMode.COOP
        d.s.scene = Scene.BOOK
        d.s.placeLabel = "우리집"
        d.s.place = "우리집"
        d.s.slots["place"] = "우리집에 있었어요"
        d.s.problem = "생일 촛불이 너무 많았어"
        d.s.slots["problem"] = "생일 케이크에 촛불이 너무 많았어요"
        d.s.solution = "다 같이 불었어"
        d.s.slots["solution"] = "다 같이 후~ 불었어요"
        d.s.title = "우리집 생일 이야기"
        return d
    }

    private fun snap(path: String) = compose.onRoot().captureRoboImage(
        File(path).path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    @Test
    fun tappingTheCandlesEndsTheMissionWithoutAMic() {
        val d = coopBirthdayBook()
        assertEquals("촛불 이야기인데 C1 이 아니다", BlowProp.CANDLE, d.s.blowProp())
        val page = (1..d.s.pageCount).first { d.s.pageKind(it) == PageKind.RUB }
        d.s.stage = Stage.BookPage(page)

        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/blow_before.png")
        // 기다리기를 탭보다 먼저 건다 — awaitReply 는 기다리기 전에 쌓인 입력을 비운다
        val got = kotlinx.coroutines.CompletableDeferred<Reply>()
        CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { got.complete(d.awaitReply()) }
        Thread.sleep(200)

        // 촛불 셋을 톡톡 — 하나에 세 번이면 꺼진다 (BLOW_FULL)
        repeat(4) {
            listOf(0.44f to 0.62f, 0.58f to 0.66f, 0.72f to 0.62f).forEach { (x, y) ->
                compose.onRoot().performTouchInput { click(Offset(width * x, height * y)) }
                compose.mainClock.advanceTimeBy(120)
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        snap("build/touch/blow_after.png")

        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("탭만으로 미션이 안 끝났다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }

    /** C3 소리 흉내 — 마이크 없이 소품을 세 번 누르면 끝난다(탭 길). 서버에 C3 가 들어가기 전이라 화면을 바로 띄운다 */
    @Test
    fun tappingTheFireTruckThreeTimesEndsTheSoundMission() {
        val d = coopBirthdayBook()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) {
                com.example.finalproject_demo.ui.missions.SoundMission(d, false, com.example.finalproject_demo.demo.Art.Emoji("🧒"), SoundProp.SIREN)
            }
        }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/sound_before.png")
        val got = kotlinx.coroutines.CompletableDeferred<Reply>()
        CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { got.complete(d.awaitReply()) }
        Thread.sleep(200)
        repeat(3) {
            compose.onRoot().performTouchInput { click(Offset(width * 0.56f, height * 0.44f)) }
            compose.mainClock.advanceTimeBy(300)
        }
        compose.mainClock.advanceTimeBy(1500)
        snap("build/touch/sound_after.png")
        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("세 번 눌렀는데 소리 미션이 안 끝났다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }

    private fun awaitMission(d: Director): kotlinx.coroutines.CompletableDeferred<Reply> {
        val got = kotlinx.coroutines.CompletableDeferred<Reply>()
        CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { got.complete(d.awaitReply()) }
        Thread.sleep(200)
        return got
    }

    /** A1 물대포 — 불 셋을 톡톡 눌러도(오래 겨누지 못해도) 다 꺼진다 */
    @Test
    fun tappingTheFiresPutsThemOut() {
        val d = coopBirthdayBook()
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { com.example.finalproject_demo.ui.missions.HoseMission(d, false, com.example.finalproject_demo.demo.Art.Emoji("🧒")) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/hose_before.png")
        val got = awaitMission(d)
        repeat(3) {
            listOf(0.46f to 0.56f, 0.61f to 0.62f, 0.76f to 0.55f).forEach { (x, y) ->
                compose.onRoot().performTouchInput { click(Offset(width * x, height * y)) }
                compose.mainClock.advanceTimeBy(150)
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        snap("build/touch/hose_after.png")
        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("불을 다 눌렀는데 물대포 미션이 안 끝났다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }

    /** A4 돌려 잠그기 — 손잡이 둘레를 두 바퀴 돌리면 잠긴다 */
    @Test
    fun turningTheHandleTwiceClosesTheTap() {
        val d = coopBirthdayBook()
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { com.example.finalproject_demo.ui.missions.TurnMission(d, false, com.example.finalproject_demo.demo.Art.Emoji("🧒")) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/turn_before.png")
        val got = awaitMission(d)
        compose.onRoot().performTouchInput {
            val c = Offset(width * (0.58f - 0.17f * 0.26f), height * 0.44f - width * 0.17f * 0.62f)
            val r = width * 0.17f * 0.30f
            fun at(t: Double) = Offset(c.x + (r * kotlin.math.cos(t)).toFloat(), c.y + (r * kotlin.math.sin(t)).toFloat())
            down(at(0.0))
            for (k in 1..60) moveTo(at(k * 5.0 * Math.PI / 60), delayMillis = 16)   // 두 바퀴 반 (5π)
            up()
        }
        // 한 번의 끌기 안에서 바뀐 값은 시계를 손으로 돌리는 동안 다시 그려지지 않았다 — 시계를 풀고 안정될 때까지 기다린다
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        snap("build/touch/turn_after.png")
        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("두 바퀴 돌렸는데 안 잠겼다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }

    /** A4 — 원을 못 그리는 아이도 손잡이를 다섯 번 톡톡 누르면 잠긴다(설계 §10 · 3~4세) */
    @Test
    fun tappingTheHandleFiveTimesAlsoClosesTheTap() {
        val d = coopBirthdayBook()
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { com.example.finalproject_demo.ui.missions.TurnMission(d, false, com.example.finalproject_demo.demo.Art.Emoji("🧒")) } }
        compose.mainClock.advanceTimeBy(600)
        val got = awaitMission(d)
        repeat(5) {
            compose.onRoot().performTouchInput { click(Offset(width * (0.58f - 0.17f * 0.26f), height * 0.44f - width * 0.17f * 0.62f)) }
            compose.mainClock.advanceTimeBy(300)
        }
        compose.mainClock.advanceTimeBy(1000)
        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("다섯 번 눌렀는데 안 잠겼다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }

    /** D4 — 기울기 센서가 없어도(Robolectric) 공을 끌어다 골대에 넣으면 끝난다(탭 길) */
    @Test
    fun draggingTheBallIntoTheGoalEndsTheRollMission() {
        val d = coopBirthdayBook()
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { com.example.finalproject_demo.ui.missions.RollMission(d, false, com.example.finalproject_demo.demo.Art.Emoji("🧒")) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/roll_before.png")
        val got = awaitMission(d)
        compose.onRoot().performTouchInput {
            val from = Offset(width * 0.36f, height * 0.56f)
            val to = Offset(width * 0.82f, height * 0.50f)
            down(from)
            for (k in 1..30) moveTo(from + (to - from) * (k / 30f), delayMillis = 16)
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        snap("build/touch/roll_after.png")
        val reply = runBlocking { withTimeoutOrNull(3_000) { got.await() } }
        assertTrue("공을 골대에 넣었는데 안 끝났다: $reply", reply is Reply.Tapped && reply.value == "mission")
    }
}
