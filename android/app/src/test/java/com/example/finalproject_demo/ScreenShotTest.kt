package com.example.finalproject_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.ui.HeroImage
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.performTouchInput
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.HeroAttr
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.finalproject_demo.ui.FloatingControls
import com.example.finalproject_demo.ui.MascotBubble
import com.example.finalproject_demo.ui.ParentBand
import com.example.finalproject_demo.ui.StageView
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
import android.graphics.BitmapFactory
import java.io.File

/**
 * 화면을 **에뮬레이터 없이** 그림으로 떨궈 눈으로 본다.
 *
 * 왜 이게 있나 — 2026-09-21에 이 기기에서 에뮬레이터가 **하루 네 번** 컴퓨터를 얼렸다
 * (트러블슈팅 6-10 · 6-12). 그런데 그날 찾은 가장 값진 버그 둘은 화면을 봐야만 보이는 것이었다:
 * [이걸로 할래] 버튼이 말풍선에 가려진 것, 안경 쓴 주인공의 눈 패치가 원래 눈을 못 덮은 것.
 * 단위 검사로는 **픽셀이 겹치는지**를 못 본다.
 *
 * Robolectric이 안드로이드 화면을 JVM 안에 만들고 Roborazzi가 그것을 PNG로 떨군다.
 * 하이퍼바이저를 쓰지 않으므로 기계가 얼지 않는다.
 *
 * 쓰는 법
 *   ./gradlew :app:recordRoborazziDebug   → 기준 그림을 `app/screens` 에 찍는다 (화면을 일부러 바꿨을 때)
 *   ./gradlew :app:test                   → 기준과 달라지면 **실패**한다. 무엇이 달라졌는지는
 *                                            `app/build/outputs/roborazzi` 의 비교 그림으로 본다
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// ⚠️ **dp 를 픽셀로 착각하지 말 것.** Pixel 3a 는 2220×1080 px · 440dpi 이므로 density 2.75,
//    즉 화면은 **807 × 393 dp** 다. 처음에 `w2220dp` 로 적었더니 실제의 2.75배짜리 화면이 되어
//    레이아웃이 실제와 달라졌다(버튼이 잘려 보이는 가짜 문제가 났다) — 9/21.
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class ScreenShotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Bg)) { content() }
        }
        // 기준 그림은 `app/screens` 에 둔다 (build/ 는 clean 하면 날아간다)
        compose.onRoot().captureRoboImage(File("screens/$name.png").path)
    }

    private fun director(block: Director.() -> Unit): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.speed = 0.0
        d.block()
        return d
    }

    /** 「골라서 만들기」 — 성별 줄을 뺀 다섯 줄 · [이걸로 할래]가 말풍선에 가리지 않는가 */
    @Test
    fun heroBuilder() {
        val d = director { s.stage = Stage.HeroBuilder(HeroAttr()) }
        shot("hero_builder") { StageView(d) }
    }

    /** 안경 + 별 눈 — 원래 눈이 별 아래 남지 않는가 (9/21 버그) */
    @Test
    fun heroBuilderGlassesStarEyes() {
        val d = director {
            s.stage = Stage.HeroBuilder(HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt"))
        }
        shot("hero_glasses_star") { StageView(d) }
    }

    /**
     * **토글 하나를 바꾸면 그 하나만 바뀌는가** (9/21).
     *
     * 전에는 완성본 81장을 갈아 끼워서, 머리를 바꾸면 얼굴이 바뀌고 옷 색을 바꾸면 하의가 바뀌었다.
     * 지금은 몸 27장 + 안경을 얹는 구조다. 다섯 줄을 한 장에 그려 **줄마다 한 가지만** 달라지는지 눈으로 본다.
     *
     * ⚠️ `setContent` 는 한 검사에 **한 번만** 부를 수 있다 — 그래서 여러 번 찍지 않고 한 장에 담는다.
     */
    @Test
    fun changingOneToggleChangesOnlyThatThing() {
        val base = HeroAttr()
        val rows: List<List<HeroAttr>> = listOf(
            listOf("short", "long", "tied").map { base.copy(hair = it) },
            listOf(0xFFF25C4C, 0xFF3F7BD9, 0xFFF9B233).map { base.copy(shirt = Color(it)) },
            listOf("pants", "skirt", "shorts").map { base.copy(bottom = it) },
            listOf("none", "round", "square").map { base.copy(glasses = it) },
            listOf("round", "smile", "star").map { base.copy(eyes = it, glasses = "round") },
        )
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Bg)) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxSize()) {
                        row.forEach { a ->
                            Box(Modifier.weight(1f).fillMaxSize()) { HeroImage(a, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(File("screens/vary_all.png").path)
    }

    /** 부모 띠 — 왼쪽 마스코트 · 버튼 없음 · `coop:` 값이 새지 않는가 */
    @Test
    fun parentBand() {
        val d = director {
            s.mode = StoryMode.COOP
            s.scene = Scene.DIARY
            s.parentCard = "놀이터에서 무슨 일이 있었어?"
        }
        // 앱과 같은 자리에 — 화면 아래에 띠로 붙는다 (MainActivity 와 같은 배치)
        shot("parent_band") {
            Box(Modifier.fillMaxSize()) {
                ParentBand(d, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
    }

    /**
     * 협업 모드 화면 **아래쪽 전체** — 말풍선 · 부모 띠 · 🎤 ➡️ 가 서로 겹치지 않는가 (9/22).
     *
     * 전에는 말풍선과 띠가 둘 다 화면 맨 아래에 놓여 겹쳤고, 그걸 피하려고 띠가 떠 있는 동안
     * 말풍선을 통째로 숨겼다. 그 바람에 **아이가 답한 뒤 마스코트가 받아주는 말이 사라졌다.**
     * 버튼도 띠를 피해 172dp 위로 올라가 화면 중간에 붕 떠 있었다.
     *
     * [parentBand] 는 띠 하나만 찍어서 이 배치를 지켜 주지 못한다(띠 글이 짧으면 픽셀이 같다).
     * 여기는 `MainActivity` 의 아래쪽 배치를 그대로 옮긴 것이다 — 거기를 바꾸면 여기도 바꾼다.
     */
    @Test
    fun coopBottomArea() {
        val d = director {
            s.mode = StoryMode.COOP
            s.scene = Scene.DIARY
            // 9/22부터 실제로는 둘이 **번갈아** 뜬다 (Director.say / askSay 가 서로를 비운다).
            // 여기서는 일부러 둘 다 켜 **가장 나쁜 경우**를 찍는다 — 그래도 겹치지 않아야 배치가 안전하다
            // 아이가 답한 직후를 가정한다 — 마스코트가 받아주고, 띠에는 다음 질문이 올라와 있다
            s.line = "우와, 블록을 그렇게 높이 쌓았구나!"
            s.parentCard = "그런데 블록이 왜 무너졌을까?"
            s.micEnabled = true
            s.nextEnabled = true
        }
        shot("coop_bottom") {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth()
                ) {
                    MascotBubble(d, Modifier.padding(start = 8.dp, bottom = 6.dp))
                    ParentBand(d)
                }
                FloatingControls(
                    d,
                    Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 10.dp),
                )
            }
        }
    }

    /** 그림판 — 크레용 12색 · 지우개 · 모두 지우기가 다 보이는가 */
    @Test
    fun drawPad() {
        val d = director { s.stage = Stage.DrawPad() }
        shot("draw_pad") { StageView(d) }
    }

    /** 도감 — 네 칸이 다 찼을 때 🗑 가 보이는가 */
    @Test
    fun bestiaryFull() {
        val d = director {
            val four = s.heroes.toList().let { it + it }.take(4)
            s.stage = Stage.Bestiary(four, plus = false)
        }
        shot("bestiary_full") { StageView(d) }
    }
}

/**
 * **손가락으로 만지는 것**까지 확인한다 — 에뮬레이터 없이.
 *
 * 스크린샷만으로는 "그려진 모습"밖에 못 본다. 미션의 문지르기 · 끌어다 놓기는 **드래그**라서
 * 실제로 손가락을 움직여 봐야 한다. Compose 검사 도구가 그 터치를 흉내내 주고,
 * Robolectric이 JVM 안에서 받는다. 하이퍼바이저를 쓰지 않으므로 기계가 얼지 않는다 (9/21).
 *
 * 애니메이션도 `mainClock` 으로 시간을 밀어 확인한다 — 기다리지 않고 원하는 순간을 본다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class TouchTest {

    @get:Rule
    val compose = createComposeRule()

    private fun diaryBook(): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.speed = 0.0
        d.s.mode = StoryMode.DIARY
        d.s.scene = Scene.BOOK
        d.s.placeLabel = "놀이터"
        d.s.place = "놀이터"
        d.s.slots["place"] = "놀이터에 갔어요"
        d.s.problem = "블록이 무너짐"
        d.s.slots["problem"] = "높이 쌓은 블록이 와르르 무너졌어요"
        d.s.solution = "다시 쌓았어"
        d.s.solutionLine = "다시 쌓은 블록은 이번엔 무너지지 않았어요"
        d.s.slots["solution"] = d.s.solutionLine
        d.s.cause = "같이 놀고 싶었어"
        d.s.slots["cause"] = "같이 놀고 싶어서 그랬대요"
        d.s.title = "놀이터에서 만난 민준이"
        return d
    }

    /**
     * 두 그림이 **얼마나 다른가** (0~1). 화면이 실제로 바뀌었는지 보는 데 쓴다.
     *
     * 감독(Director)이 돌고 있지 않으면 미션 완료 신호가 채널에 남아 상태에 안 보인다.
     * 그래서 "이벤트가 늘었나" 대신 **눈에 보이는 것이 달라졌나**로 판단한다 (9/21).
     */
    /**
     * 비교용 그림을 **기준 대조 없이** 저장한다.
     *
     * 그냥 `captureRoboImage` 로 찍으면 그 파일까지 "기준 그림" 으로 여겨 다음 검사에서 달라졌다고 실패한다.
     * 이 둘은 **이번 검사 안에서만 쓰는 중간 산출물**이다.
     *
     * ⚠️ `captureToImage()` 로는 안 된다 — 화면이 다시 그려지기를 기다리는데
     *    `mainClock.autoAdvance = false` 라 시계가 안 흘러 시간 초과가 난다 (9/21).
     *    Roborazzi 에 **기록만 하라(Record)** 고 일러 주는 쪽이 맞다.
     */
    private fun snap(path: String) {
        compose.onRoot().captureRoboImage(
            File(path).path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private fun diff(a: String, b: String): Double {
        // ⚠️ `javax.imageio` 는 안드로이드 유닛 테스트 classpath 에 없다 — 안드로이드 쪽 디코더를 쓴다
        val x = BitmapFactory.decodeFile(a)
        val y = BitmapFactory.decodeFile(b)
        require(x.width == y.width && x.height == y.height) { "크기가 다르다" }
        var n = 0L
        var same = 0L
        var i = 0
        while (i < x.width) {
            var j = 0
            while (j < x.height) {
                if (x.getPixel(i, j) == y.getPixel(i, j)) same++
                n++
                j += 4
            }
            i += 4
        }
        return 1.0 - same.toDouble() / n
    }

    /** 미션 1 — **문질러서** 흔적이 없어지는가 (드래그) */
    @Test
    fun rubbingTheRideClearsTheMission() {
        val d = diaryBook()
        val rubPage = (1..d.s.pageCount).first { d.s.pageKind(it) == PageKind.RUB }
        d.s.stage = Stage.BookPage(rubPage)

        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/rub_before.png")

        // 흔적 세 개가 흩어진 **바닥**을 가로로 여러 번 문지른다.
        // 9/22에 높이를 0.50 → 0.68로 내렸다. 일기·협업에는 탈것이 없어서 흔적이 가방이 아니라
        // 놀던 자리에 떨어지도록 바꿨기 때문이다 (Book.kt RubPage). 판정은 |dy| < 0.14h 다
        repeat(14) {
            compose.onRoot().performTouchInput {
                val y = height * 0.68f
                down(androidx.compose.ui.geometry.Offset(width * 0.28f, y))
                moveTo(androidx.compose.ui.geometry.Offset(width * 0.46f, y))
                moveTo(androidx.compose.ui.geometry.Offset(width * 0.64f, y))
                moveTo(androidx.compose.ui.geometry.Offset(width * 0.28f, y))
                up()
            }
            compose.mainClock.advanceTimeBy(80)
        }
        compose.mainClock.advanceTimeBy(1500)
        snap("build/touch/rub_after.png")
        compose.onRoot().captureRoboImage(File("screens/touch_rub.png").path)

        val changed = diff("build/touch/rub_before.png", "build/touch/rub_after.png")
        assertTrue("문질렀는데 화면이 그대로다 (다른 화소 ${"%.1f".format(changed * 100)}%)", changed > 0.005)
    }

    /** 미션 2 — **끌어다 놓아** 친구에게 건네지는가 (드래그) */
    @Test
    fun draggingTheGiftReachesTheFriend() {
        val d = diaryBook()
        d.s.friend = "민준이"
        d.s.companionKind = "민준이"
        d.s.friendName = "민준이"
        val dragPage = (1..d.s.pageCount).first { d.s.pageKind(it) == PageKind.DRAG }
        d.s.stage = Stage.BookPage(dragPage)

        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) } }
        compose.mainClock.advanceTimeBy(600)
        snap("build/touch/drag_before.png")

        // 물건은 (0.30W, 0.22H) 에서 시작하고 친구는 (0.695W, 0.26H+) 에 있다 — 그 사이를 끈다
        compose.onRoot().performTouchInput {
            val from = androidx.compose.ui.geometry.Offset(width * 0.355f, height * 0.33f)
            val to = androidx.compose.ui.geometry.Offset(width * 0.695f, height * 0.46f)
            down(from)
            for (k in 1..8) {
                moveTo(
                    androidx.compose.ui.geometry.Offset(
                        from.x + (to.x - from.x) * k / 8f,
                        from.y + (to.y - from.y) * k / 8f,
                    )
                )
            }
            up()
        }
        compose.mainClock.advanceTimeBy(2000)
        snap("build/touch/drag_after.png")
        compose.onRoot().captureRoboImage(File("screens/touch_drag.png").path)

        val changed = diff("build/touch/drag_before.png", "build/touch/drag_after.png")
        assertTrue("끌어다 놨는데 화면이 그대로다 (다른 화소 ${"%.1f".format(changed * 100)}%)", changed > 0.005)
    }

    /** 애니메이션 — 시간을 밀면 실제로 움직이는가 (같은 쪽인데 그림이 달라져야 한다) */
    @Test
    fun theBookPageActuallyAnimates() {
        val d = diaryBook()
        d.s.stage = Stage.BookPage(1)
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Bg)) { StageView(d) } }

        compose.mainClock.advanceTimeBy(100)
        snap("build/touch/anim_t0.png")
        compose.mainClock.advanceTimeBy(450)          // 흔들림 주기의 반대편으로
        snap("build/touch/anim_t1.png")

        val moved = diff("build/touch/anim_t0.png", "build/touch/anim_t1.png")
        assertTrue("시간을 밀었는데 아무것도 안 움직였다 (다른 화소 ${"%.2f".format(moved * 100)}%)", moved > 0.0005)
    }
}
