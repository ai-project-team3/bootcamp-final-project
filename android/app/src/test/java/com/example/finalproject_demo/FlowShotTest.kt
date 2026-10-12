package com.example.finalproject_demo

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.example.finalproject_demo.demo.DemoBtn
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.Shell
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **세 모드를 방에서 책까지 실제로 돌리며 장면마다 화면을 찍는다** (09-29 사용자 요청 — 「겹치는 요소가 많아, 전부 확인해 봐」).
 *
 * 한 장면씩 상태를 꾸며 찍으면 실제 흐름에서만 생기는 조합(녹음 뒤 「이 소리로」 + 나레이션 칸 등)을 놓친다.
 * 그래서 앱 틀까지 다 띄운 채 대본 버튼으로 흐름을 밀고, 화면이 바뀔 때마다 `build/flow/<모드>/` 에 떨군다.
 * 그림은 기준 대조가 아니라 눈으로 보는 용도다. 검사가 보는 것은 **책까지 막히지 않고 가는가** 하나다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class FlowShotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun ready() { Shell.resetToFirstRun(); ConsentStore.agree(); Shell.finishOnboarding() }
    @After fun clean() { Shell.resetToFirstRun(); ConsentStore.withdraw() }

    private fun pick(bs: List<DemoBtn>): DemoBtn? =
        bs.firstOrNull { "🎲" in it.label }
            ?: bs.firstOrNull { "🖐" in it.label }
            ?: bs.firstOrNull { "✅" in it.label }
            ?: bs.firstOrNull { "▶" in it.label }
            ?: bs.firstOrNull { "🗣" in it.label }
            ?: bs.firstOrNull { "🤐" !in it.label && "😶" !in it.label && "처음으로" !in it.label }

    private fun tap(t: String) {
        compose.waitUntil(8_000) { compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).onLast().performClick()
    }

    private fun lap(dir: String, thing: String, prep: (com.example.finalproject_demo.demo.DemoState) -> Unit = {}) {
        compose.waitUntil(10_000) { compose.activity.director != null }
        val d = compose.activity.director!!
        compose.runOnIdle { d.s.speed = 0.02; prep(d.s) }
        tap(thing)
        runCatching { tap("응!") }.onFailure {
            captureScreenRoboImage(File("build/flow/$dir/fail_room.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))
            throw it
        }
        compose.waitUntil(8_000) { d.s.scene != Scene.ADULT }
        // 그림일기(09-30)는 일기 장면 안에서 책을 끝까지 읽고 책 선물(D6)에서 멈춘다 — 동화 · 협업은 책(BOOK)에서 멈춘다
        val atEnd = { if (dir == "diary") d.s.stage is com.example.finalproject_demo.demo.DiaryGift else d.s.scene == Scene.BOOK }
        var last = ""
        var n = 0
        var micShot = false
        for (i in 0 until 400) {
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val (sig, bs) = compose.runOnIdle {
                val st = d.s.stage
                "${d.s.scene}|${st::class.simpleName}|${(st as? Stage.World)?.retry}|${d.s.micEnabled}|${d.s.buttons.joinToString { it.label.take(6) }}" to d.s.buttons.toList()
            }
            if (sig != last) {
                last = sig
                compose.mainClock.advanceTimeBy(1_500)   // 글자가 다 써지고 튀어나옴이 끝난 뒤에 찍는다
                compose.waitForIdle()
                captureScreenRoboImage(File("build/flow/$dir/%03d_%s.png".format(n++, d.s.scene.name.lowercase())).path, RoborazziOptions(taskType = RoborazziTaskType.Record))
                // 처음 녹음할 수 있는 곳에서 녹음 중 모습도 한 장
                if (!micShot && d.s.micEnabled) {
                    micShot = true
                    compose.runOnIdle { d.s.micOn = true }
                    compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                    captureScreenRoboImage(File("build/flow/$dir/%03d_mic_on.png".format(n++)).path, RoborazziOptions(taskType = RoborazziTaskType.Record))
                    compose.runOnIdle { d.s.micOn = false }
                }
            }
            if (atEnd()) break
            // Story creation now runs on the first book too. Confirm a stable typed name instead
            // of repeatedly feeding different random names into the recognition confirmation.
            if (d.s.stage is Stage.NameEntry) {
                compose.runOnIdle { d.send(com.example.finalproject_demo.demo.Reply.Tapped(
                    com.example.finalproject_demo.demo.NAME_TYPED, "콩이")) }
                continue
            }
            val b = pick(bs) ?: continue
            compose.runOnIdle { b.onClick() }
        }
        assertTrue("$dir: 책까지 못 갔다 — ${d.s.scene} · ${d.s.buttons.map { it.label }}", atEnd())
    }

    @Test fun storyLap() = lap("story", "동화 만들기")
    @Test fun diaryLap() = lap("diary", "그림일기")
    // 같이 만들기는 부모님이 먼저 템플릿으로 이야기를 준비해 둬야 소파가 열린다 (09-29) — 부모 모드에서 고른 것과 같게 채운다
    @Test fun coopLap() = lap("coop", "같이 만들기") { s ->
        s.coopPick = com.example.finalproject_demo.demo.CoopPick("job", "소방관", "soon")
    }
}
