package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.missions.SoundProp
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.missions.FixMission
import com.example.finalproject_demo.ui.missions.GiveMission
import com.example.finalproject_demo.ui.missions.HINT_AFTER_MS
import com.example.finalproject_demo.ui.missions.HINT_LINE
import com.example.finalproject_demo.ui.missions.HINT_SHOW_MS
import com.example.finalproject_demo.ui.missions.HoseMission
import com.example.finalproject_demo.ui.missions.RollMission
import com.example.finalproject_demo.ui.missions.SoundMission
import com.example.finalproject_demo.ui.missions.StackMission
import com.example.finalproject_demo.ui.missions.TurnMission
import com.example.finalproject_demo.ui.motionFrozen
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * #260 ②~④ — 미션마다 「반쯤(15초 도움 뒤)」과 「다 한 뒤」의 장면을 `build/review/missions/` 에 찍는다(눈으로 보는 용 · 비교 안 함).
 * 반쯤 장면은 오또가 반만 해 준 상태라 아이 행동 없이도 진행 중 연출(작아진 불 · 궤적 · 기운 친구 …)이 보인다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class MissionSceneShotTest {
    @get:Rule val compose = createComposeRule()
    private val frozen = motionFrozen
    @After fun restore() { motionFrozen = frozen }

    private val hero = Art.Emoji("🧒")

    private fun book(): Director = Director(CoroutineScope(SupervisorJob())).also { d ->
        d.s.speed = 0.0
        d.s.mode = StoryMode.COOP
        d.s.scene = Scene.BOOK
        d.s.placeLabel = "우리집"; d.s.place = "우리집"
        d.s.title = "우리집 이야기"
    }

    private fun snap(name: String) = compose.onRoot().captureRoboImage(
        File("build/review/missions/$name.png").path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    /** 처음 장면 + 15초 흐릿한 예시가 한가운데쯤 지나는 장면(오또는 대신 하지 않는다 · #293 리뷰) */
    private fun hint(name: String, content: @Composable (Director) -> Unit) {
        motionFrozen = false
        val d = book()
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { content(d) } }
        compose.mainClock.advanceTimeBy(600)
        snap("${name}_0_start")
        compose.mainClock.advanceTimeBy(HINT_AFTER_MS + HINT_SHOW_MS / 2)
        assertEquals("$name: 15초에 예시를 보이지 않았다", HINT_LINE, d.s.line)
        snap("${name}_1_hint")
    }

    private fun done(name: String, content: @Composable (Director) -> Unit) {
        motionFrozen = true
        val d = book()
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { content(d) } }
        compose.waitForIdle()
        snap("${name}_2_done")
    }

    @Test fun a1HoseHint() = hint("a1_hose") { HoseMission(it, false, hero) }
    @Test fun a1HoseDone() = done("a1_hose") { HoseMission(it, true, hero) }
    @Test fun a4TurnHint() = hint("a4_turn") { TurnMission(it, false, hero) }
    @Test fun d4RollHint() = hint("d4_roll") { RollMission(it, false, hero) }
    @Test fun d4RollDone() = done("d4_roll") { RollMission(it, true, hero) }
    @Test fun c3LionHint() = hint("c3_lion") { SoundMission(it, false, hero, SoundProp.LION) }
    @Test fun c3LionDone() = done("c3_lion") { SoundMission(it, true, hero, SoundProp.LION) }
    @Test fun c3DogDone() = done("c3_dog") { SoundMission(it, true, hero, SoundProp.DOG) }
    @Test fun c3SirenHint() = hint("c3_siren") { SoundMission(it, false, hero, SoundProp.SIREN) }
    @Test fun a5StackHint() = hint("a5_stack") { StackMission(it, false, hero) }
    @Test fun a5StackDone() = done("a5_stack") { StackMission(it, true, hero) }
    @Test fun e1GiveHint() = hint("e1_give") { GiveMission(it, false, hero, "hand") }
    @Test fun e2FixHint() = hint("e2_fix") { FixMission(it, false, hero) }
}
