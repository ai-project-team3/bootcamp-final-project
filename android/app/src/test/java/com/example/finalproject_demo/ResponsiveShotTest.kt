package com.example.finalproject_demo

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.Shell
import com.example.finalproject_demo.ui.shell.Step
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **여러 기기 크기**에서 같은 화면을 찍는다 (09-30 사용자 요청 — 연결된 폰 말고 모든 기기에 맞게).
 *
 * 크기마다 하위 클래스 하나 — 화면은 `build/responsive/<크기>/` 에 떨군다(눈으로 보는 용도).
 * 처음 설정 → 방 · 알림 → 부모 모드 → 책장 → 이야기 화면(앱 틀과 함께)까지 한 번에 돈다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ResponsiveShotTest(private val size: String) {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun fresh() { Shell.resetToFirstRun(); ConsentStore.withdraw() }
    @After fun clean() { Shell.resetToFirstRun(); ConsentStore.withdraw() }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File("build/responsive/$size/$name.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    private fun waitText(t: String, ms: Long = 8_000) {
        runCatching { compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { shot("zz_stuck"); throw AssertionError("[$size] 안 나타남: $t", it) }
    }

    private fun tap(t: String) {
        runCatching { compose.waitUntil(8_000) { compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { shot("zz_stuck"); throw AssertionError("[$size] 누를 것을 못 찾음: $t", it) }
        // 스크롤 안에 있으면 먼저 보이게 — 화면 밖(고정 줄 아래)을 누르지 않게
        compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).onLast().apply { runCatching { performScrollTo() } }.performClick()
    }

    private fun gone(t: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isEmpty() }

    private val director get() = compose.activity.director!!

    @Test
    fun everyScreen() {
        // ── 처음 설정
        waitText("눌러서 시작", 10_000); shot("01_title")
        tap("눌러서 시작")
        waitText("카카오로 시작하기"); shot("02_login")
        tap("카카오로 시작하기")
        waitText("이렇게만 써요"); shot("03_consent")
        tap("모두 동의해요"); tap("동의하고 계속")
        waitText("마이크를 켜 주세요"); shot("04_mic")
        tap("마이크 켜기")
        waitText("부모 비밀번호를 정해요"); shot("05_pin_make")
        repeat(2) { "1234".forEach { tap(it.toString()) }; compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }
        waitText("우리 아이에게 맞춰요")
        tap("2권"); tap("양모"); tap("받지 않아요")
        shot("06_setup")
        tap("설정 끝")
        // ⑥ 기능 안내 (10-05) — 여섯 장을 넘긴다
        waitText("오또로 이렇게 놀아요"); waitText("상상한 이야기가 그림책이 돼요"); shot("06b_guide_1_story")
        listOf("2_diary", "3_coop", "4_shelf", "5_parent", "6_safe").forEach { tap("다음"); compose.mainClock.advanceTimeBy(400); shot("06b_guide_$it") }
        tap("아이에게 건네기")
        waitText("준비 끝!"); shot("07_handoff")
        tap("아이 차례 시작")
        // ⑧ 방 둘러보기 — 오또가 가리키는 물건 넷을 차례로 누른다
        waitText("여기는 창문이야"); shot("08_tour_1_window")
        tap("그림일기")
        waitText("소파에선"); shot("08_tour_2_sofa")
        tap("같이 만들기")
        waitText("책장엔"); shot("08_tour_3_shelf")
        tap("내 책장")
        waitText("무대에선"); shot("08_tour_4_theater")
        tap("동화 만들기")
        waitText("좋아하는 동물"); shot("09_tutorial_talk")
        compose.onNode(hasContentDescription("연습 말하기")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("다음")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("다음")).performClick()
        compose.waitUntil(5_000) { Shell.step == Step.APP }

        // ── 방 · 알림
        waitText("그림일기"); compose.mainClock.advanceTimeBy(1_500); shot("11_room")
        tap("그림일기")
        waitText("오늘 있었던 일로 그림일기 만들래?"); shot("12_room_ask")
        tap("아니"); gone("오늘 있었던 일로 그림일기 만들래?")
        compose.onNode(hasContentDescription("오또")).performClick()
        compose.mainClock.advanceTimeBy(300); shot("13_room_poke")
        compose.mainClock.advanceTimeBy(3_000)

        // ── 책장
        tap("내 책장")
        waitText("내가 만든 책 보러 갈래?"); tap("응!")
        waitText("방으로"); shot("14_shelf")
        tap("🏠 방으로")
        compose.waitUntil(5_000) { director.s.stage == Stage.Adult }

        // ── 부모 모드
        waitText("그림일기")
        tap("🔒")
        compose.waitUntil(5_000) { director.s.stage is Stage.Pin }
        waitText("부모 비밀번호"); shot("15_pin")
        "1234".forEach { tap(it.toString()) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitUntil(5_000) { director.s.scene == Scene.PARENT }
        shot("16_parent_record")
        tap("같이 만들기"); compose.mainClock.advanceTimeBy(400)
        tap("직업 · 임무 이야기"); tap("소방관"); shot("17_parent_coop_edit")
        tap("저장하기"); compose.mainClock.advanceTimeBy(400); shot("18_parent_coop_saved")
        listOf("업적" to "ach", "설정" to "set", "계정" to "acct").forEachIndexed { i, (t, k) ->
            tap(t); compose.mainClock.advanceTimeBy(400); shot("19${"abc"[i]}_parent_$k")
        }
        tap("아이 화면으로")
        waitText("그림일기"); compose.mainClock.advanceTimeBy(1_000)
        tap("같이 만들기")
        waitText("부모님이 준비한 이야기 들어 볼래?"); shot("20_room_ask_coop")
        tap("아니"); gone("부모님이 준비한 이야기 들어 볼래?")

        // ── 이야기 화면 (흐름을 끝까지 돌리지 않고 상태만 넣는다 — ShellFlowTest.storyScreensWithChrome 과 같은 방식)
        val d = director
        compose.runOnIdle {
            d.s.themeKey = "dino"; d.s.templateKey = "A"; d.s.level = com.example.finalproject_demo.demo.Level.REASON
            d.s.heroAttr = com.example.finalproject_demo.ui.HeroAttr(hair = "short", glasses = "none", eyes = "round", bottom = "pants")
            d.s.friendName = "뭉치"; d.s.causeLine = "친구가 없어서 심심했어"; d.s.solutionLine = "같이 별을 땄어요"; d.s.solutionItem = "star"
            d.s.title = "뭉치와 반짝이는 별"
            d.s.scene = Scene.PLACE
            d.s.progressVisible = true
            d.s.line = "여기는 어디일까? 말해 줄래?"
            d.s.stage = Stage.World(listOf(
                com.example.finalproject_demo.demo.WorldItem(d.s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
                com.example.finalproject_demo.demo.WorldItem(com.example.finalproject_demo.demo.Art.HeroArt(d.s.heroAttr!!), 0.30f, 0.30f, 0.11f),
            ))
        }
        shot("21_story_world")
        compose.runOnIdle { d.s.stage = Stage.HeroBuilder(com.example.finalproject_demo.ui.HeroAttr()); d.s.scene = Scene.PARTNER }
        shot("22_story_hero")
        // 책 모든 쪽 — 4:3 마지막 쪽에서 음수 padding 으로 앱이 죽던 것(Book.kt Stand · 09-30)이 다시 생기지 않게
        for (p in 0..d.s.pageCount) {
            compose.runOnIdle { d.s.scene = Scene.BOOK; d.s.progressVisible = false; d.s.line = ""; d.s.stage = Stage.BookPage(p) }
            shot("23_book_page_$p")
        }
    }
}

/** 작은 폰 — 5인치 720p (xhdpi) */
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-320dpi")
class ResponsiveSmallPhoneTest : ResponsiveShotTest("a_small_640x360")

/** 사용자 폰 — 갤럭시 S10 5G (19:9) */
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class ResponsivePhoneTest : ResponsiveShotTest("b_phone_868x411")

/** 긴 폰 — 20:9 (픽셀 · 갤럭시 S 최근) */
@Config(sdk = [34], qualifiers = "w915dp-h412dp-land-420dpi")
class ResponsiveTallPhoneTest : ResponsiveShotTest("c_tall_915x412")

/** 태블릿 16:10 — 갤럭시 탭 A · S 시리즈 */
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-240dpi")
class ResponsiveTabletTest : ResponsiveShotTest("d_tablet_1280x800")

/** 태블릿 4:3 — 폴드 펼침 · 4:3 태블릿 */
@Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-320dpi")
class ResponsiveSquarishTest : ResponsiveShotTest("e_43_1024x768")
