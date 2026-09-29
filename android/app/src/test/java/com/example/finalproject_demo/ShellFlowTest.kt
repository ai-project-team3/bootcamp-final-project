package com.example.finalproject_demo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.shell.Shell
import com.example.finalproject_demo.ui.shell.Step
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
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
 * 앱 틀을 **처음부터 끝까지 눌러 본다** (09-29 사용자 요청 검토).
 *
 *  - 처음 설정은 끝까지 해야 방에 들어간다 — 건너뛰는 길(샘플 · 「나중에」)이 없다
 *  - 맞춤 설정은 셋 다 골라야 「설정 끝」이 눌린다 · 고른 값이 흐름 상태에 들어간다
 *  - 방 물건마다 다른 알림 · 알림이 떠 있으면 뒤의 물건이 눌리지 않는다
 *  - 🔒 한 번 누르면 어른 확인이 뜬다
 *  - 책장에서 🏠 로 방에 돌아온다
 *
 * 화면은 `build/review/` 에 떨군다(기준 그림이 아니라 눈으로 보는 용도).
 * Pixel 크기가 아니라 **사용자 폰(갤럭시 S10 5G · 1080×2280 · 420dpi = 868×411dp)** 크기로 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w868dp-h411dp-land-420dpi")
class ShellFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun fresh() { Shell.resetToFirstRun(); ConsentStore.withdraw() }
    @After fun clean() { Shell.resetToFirstRun(); ConsentStore.withdraw() }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File("build/review/$name.png").path, RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    private fun waitText(t: String, ms: Long = 8_000) =
        compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** 그 글자를 가진 **누를 수 있는** 것 가운데 맨 위(나중에 그려진 것)를 누른다 — 창 뒤의 흐름 화면 글자와 헷갈리지 않게 */
    private fun tap(t: String) {
        compose.waitUntil(8_000) { compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).onLast().performClick()
    }

    private fun count(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().size

    private val director get() = compose.activity.director!!

    /** 처음 설정을 끝까지 — 방에 들어오기까지 */
    private fun onboard() {
        waitText("눌러서 시작", 10_000); shot("01_title")
        tap("눌러서 시작")
        waitText("카카오로 시작하기"); shot("02_login")
        assertEquals("로그인 없이 둘러보는 길이 남아 있다", 0, count("샘플 책 보기"))
        tap("카카오로 시작하기")
        waitText("이렇게만 써요"); shot("03_consent")
        tap("모두 동의해요"); tap("동의하고 계속")
        waitText("마이크를 켜 주세요"); shot("04_mic")
        assertEquals("마이크를 건너뛰는 「나중에」가 남아 있다", 0, count("나중에"))
        tap("마이크 켜기")
        waitText("우리 아이에게 맞춰요"); shot("05_setup_empty")
        compose.onNode(hasText("설정 끝") and hasClickAction()).assertIsNotEnabled()
        tap("2권"); tap("양모"); tap("받지 않아요")
        shot("06_setup_done")
        tap("설정 끝")
        waitText("준비 끝!"); shot("07_handoff")
        tap("아이 차례 시작")
        waitText("여기를 눌러 봐!"); shot("08_tutorial_tap")
        tap("동화 만들기")
        waitText("좋아하는 동물"); shot("09_tutorial_talk")
        compose.onNode(hasContentDescription("연습 말하기")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("다음")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("다음")).performClick()
        waitText("오또랑 이렇게 놀아요"); shot("10_features")
        tap("오또의 방으로")
        compose.waitUntil(5_000) { Shell.step == Step.APP }
        waitText("그림일기"); shot("11_room")
    }

    @Test
    fun onboardingMustBeFinishedAndSetupReachesTheFlow() {
        onboard()
        assertTrue(Shell.onboarded)
        val s = director.s
        assertEquals("맞춤 설정의 하루 책 수가 흐름에 안 들어갔다", 2, s.dailyLimit)
        assertTrue(s.limitOn)
        assertEquals(false, s.pinToStart)
    }

    @Test
    fun eachThingAsksItsOwnQuestionAndTheRoomIsLockedBehindIt() {
        onboard()
        tap("그림일기")
        waitText("오늘 있었던 일로 그림일기 만들래?"); shot("12_room_ask_diary")
        // 알림이 떠 있는 동안 뒤의 물건(책장)을 눌러도 알림이 바뀌지 않는다
        compose.onAllNodes(hasText("내 책장") and hasClickAction()).onLast().performClick()
        compose.waitForIdle()
        assertEquals("알림이 떠 있는데 뒤의 물건이 눌렸다", 1, count("오늘 있었던 일로 그림일기 만들래?"))
        assertEquals(0, count("내가 만든 책 보러 갈래?"))
        tap("아니")
        compose.waitUntil(5_000) { count("오늘 있었던 일로 그림일기 만들래?") == 0 }
        tap("동화 만들기")
        waitText("오또랑 새 동화 만들래?"); shot("13_room_ask_story")
        tap("아니")
        compose.waitUntil(5_000) { count("오또랑 새 동화 만들래?") == 0 }
        tap("같이 만들기")
        waitText("엄마 아빠랑 같이 책 만들래?"); shot("14_room_ask_coop")
    }

    @Test
    fun lockOpensTheAdultCheckWithOneTap() {
        onboard()
        tap("🔒")
        compose.waitUntil(5_000) { director.s.stage is Stage.Pin }
        waitText("보호자님이 태어난 해"); shot("15_pin")
    }

    @Test
    fun shelfHasAWayHome() {
        onboard()
        tap("내 책장")
        waitText("내가 만든 책 보러 갈래?")
        tap("응!")
        waitText("방으로"); shot("16_shelf")
        tap("🏠 방으로")
        compose.waitUntil(5_000) { director.s.stage == Stage.Adult }
        waitText("그림일기")
    }

    /**
     * 이야기 도중 화면을 **앱 틀과 함께** 그린다 — 🏠 · 🔒 · 진행 막대 · 오또 말풍선 · 🎤 가 그림 위 버튼과 겹치는지 눈으로 본다.
     * 흐름을 끝까지 돌리지 않고 상태만 넣는다(흐름은 첫 화면에서 신호를 기다리는 중이라 덮어쓰지 않는다)
     */
    @Test
    fun storyScreensWithChrome() {
        ConsentStore.agree(); Shell.finishOnboarding()
        compose.waitUntil(8_000) { compose.activity.director != null }
        val d = director
        compose.runOnIdle {
            d.s.themeKey = "dino"; d.s.templateKey = "A"; d.s.level = com.example.finalproject_demo.demo.Level.REASON
            d.s.heroAttr = com.example.finalproject_demo.ui.HeroAttr(hair = "short", glasses = "none", eyes = "round", bottom = "pants")
            d.s.friendName = "뭉치"; d.s.causeLine = "친구가 없어서 심심했어"; d.s.solutionLine = "같이 별을 땄어요"; d.s.solutionItem = "star"
            d.s.title = "뭉치와 반짝이는 별"
            d.s.scene = com.example.finalproject_demo.demo.Scene.PLACE
            d.s.progressVisible = true
            d.s.line = "여기는 어디일까? 말해 줄래?"
            d.s.stage = Stage.World(listOf(
                com.example.finalproject_demo.demo.WorldItem(d.s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
                com.example.finalproject_demo.demo.WorldItem(com.example.finalproject_demo.demo.Art.HeroArt(d.s.heroAttr!!), 0.30f, 0.30f, 0.11f),
            ))
        }
        shot("20_story_world")
        compose.runOnIdle { d.s.stage = Stage.HeroBuilder(com.example.finalproject_demo.ui.HeroAttr()); d.s.scene = com.example.finalproject_demo.demo.Scene.PARTNER }
        shot("21_story_hero")
        val pages = d.s.pageCount
        for (p in 0..pages) {
            compose.runOnIdle { d.s.scene = com.example.finalproject_demo.demo.Scene.BOOK; d.s.progressVisible = false; d.s.line = ""; d.s.stage = Stage.BookPage(p) }
            shot("3${p}_book_page_$p")
        }
    }
}
