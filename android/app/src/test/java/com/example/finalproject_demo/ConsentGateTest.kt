package com.example.finalproject_demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import com.example.finalproject_demo.ui.ConsentStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 첫 실행 보호자 동의가 **정말로 앞을 막는가** (09-25).
 *
 * 왜 화면 검사(스크린샷)로는 부족한가 — 09-25 에 발견한 구멍이 **보이는 것이 아니라 만져지는 것**이었다.
 * 동의 화면은 제대로 그려지고 있었다. 문제는 그 화면이 `Dialog` 가 아니라 `MainActivity` 의 같은
 * `Box` 안 마지막 자식이었고, 루트가 `background` 만 갖고 있어 **터치를 소비하지 않은** 것이다.
 * 카드가 화면의 78%×92% 라 남은 여백 아래로 **먼저 그려진** 🎤 버튼(우하단)과 시연 서랍
 * 롱프레스 핫스팟(우상단)이 그대로 닿았다. 즉 **아이가 동의 없이 마이크를 켤 수 있었다.**
 *
 * 그래서 이 검사는 「보이는가」가 아니라 **「눌러도 아무 일이 없는가」**를 본다.
 * 개인정보보호법 제22조의2 는 만 14세 미만 아동의 개인정보를 **처리하기 전에** 법정대리인 동의를
 * 받으라고 하고, 처리가 시작되는 순간은 **아이가 말하는 순간**이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h393dp-land-320dpi")
class ConsentGateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** 스플래시가 지나가고 동의 화면이 올라오기를 기다린다 */
    private fun awaitConsent() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("시작하기 전에").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun countOf(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Before
    fun resetConsent() {
        // `ConsentStore` 는 메모리뿐인 object 라 검사 사이에 값이 남는다. (그 자체가 미해결 과제 —
        // 앱을 껐다 켜면 동의가 초기화된다. 여기서는 「막는가」만 보므로 되돌려 두고 시작한다)
        ConsentStore.withdraw()
    }

    /**
     * ⚠️ **끝나고도 되돌린다.** 이걸 빼먹어서 09-25 에 `ScreenShotTest.parentNoticeSectionsDraw`
     * 가 깨졌다 — 이 클래스가 `guardianAgreed = true` 를 남기자 `ConsentWithdrawSection` 이
     * 다른 모습으로 그려져 기준 그림과 달라졌다. `object` 하나를 공유하는 검사끼리는
     * **앞뒤로 다 치워야** 한다.
     */
    @After
    fun leaveNoTrace() {
        ConsentStore.withdraw()
    }

    /** 동의 전에는 동의 화면이 떠 있다 — 여기가 깨지면 아래 검사들이 무의미하다 */
    @Test
    fun consentScreenIsUpBeforeAgreeing() {
        awaitConsent()
        compose.onNodeWithText("시작하기 전에").assertIsDisplayed()
    }

    /**
     * ⚠️ **본 검사 — 동의 화면이 자기 창(`Dialog`)에 있는가.**
     *
     * 이것이 구멍을 막는 **바로 그 성질**이다. `MainActivity` 의 같은 `Box` 안에 그리면
     * 컴포즈 루트가 하나여서 카드 밖 여백의 터치가 **그 아래 형제(🎤 · 시연 서랍)로 내려간다.**
     * `Dialog` 는 안드로이드 창을 하나 더 만들고, 그 창이 화면을 덮으면 터치는 **OS 창 단계에서**
     * 막힌다 — 컴포즈가 끝까지 내려보내지 않는다.
     *
     * 그래서 루트 개수를 센다. **동의 중 2개**(앱 창 + 동의 창), **동의 뒤 1개**.
     * 누가 `Dialog` 를 걷어내면 이 수가 1로 떨어지고 검사가 깨진다 — 그것이 이 검사의 목적이다.
     *
     * ⚠️ **이 검사가 실기기 확인을 대신하지 못한다.** 창이 실제로 화면 전체를 덮는지는
     * `usePlatformDefaultWidth = false` 와 `fillMaxSize()` 에 달려 있고, 태블릿 세로처럼
     * 크기가 다른 화면에서는 눈으로 한 번 봐야 한다.
     */
    @Test
    fun consentLivesInItsOwnWindowSoTouchesCannotLeakBehind() {
        awaitConsent()

        val rootsWhileConsenting = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        assert(rootsWhileConsenting >= 2) {
            "동의 화면이 Dialog 가 아니다 (루트 $rootsWhileConsenting 개). " +
                "같은 Box 안에 그리면 카드 밖 여백의 터치가 🎤·시연 서랍으로 내려간다"
        }

        // 시연 서랍이 동의 화면 위로 열려 있지는 않다
        assert(countOf("🎮 조작") == 0) { "동의 화면 위에 시연 서랍이 떠 있다" }

        // 동의하면 창이 닫히고 앱 창만 남는다
        compose.onNodeWithText(
            "만 14세 미만 아동의 개인정보(음성 · 그림 · 대화 기록) 처리에 법정대리인으로서 동의합니다.",
        ).performClick()
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("시작하기 전에").fetchSemanticsNodes().isEmpty()
        }
        assert(ConsentStore.guardianAgreed) { "동의가 저장되지 않았다" }
    }

    /** 체크를 안 하면 「동의하고 시작」이 통하지 않는다 — 동의는 명시적이어야 한다 */
    @Test
    fun startButtonDoesNothingUntilTheBoxIsChecked() {
        awaitConsent()
        compose.onNodeWithText("동의하고 시작").performClick()
        // 체크 없이 눌렀으므로 아직 동의 화면이다
        compose.onNodeWithText("시작하기 전에").assertIsDisplayed()
        assert(!ConsentStore.guardianAgreed) { "체크 없이 동의가 저장됐다" }
    }

    /**
     * **거절할 길이 있다** (09-25). 전에는 「동의하고 시작」 하나뿐이고 뒤로가기도 막혀
     * 동의하지 않는 보호자가 나갈 방법이 없었다. 거절하면 앱이 닫힌다.
     */
    @Test
    fun declineClosesTheApp() {
        awaitConsent()
        compose.onNodeWithText("동의하지 않음").performClick()
        compose.waitForIdle()
        assert(compose.activity.isFinishing) { "「동의하지 않음」을 눌렀는데 앱이 안 닫혔다" }
        assert(!ConsentStore.guardianAgreed) { "거절했는데 동의가 저장됐다" }
    }

    /**
     * **동의가 기기에 남는다** (09-25). 전에는 메모리뿐이라 켤 때마다 동의 화면이 다시 떴다.
     *
     * 프로세스를 죽였다 살리는 대신 두 방향을 따로 본다 —
     * ① `agree()` 가 기기 저장소에 **쓰는가** ② `attach()` 가 기기 저장소에서 **읽는가**.
     * ⚠️ 저장소 이름·키(`consent` · `guardian_agreed`)를 여기서도 쓴다. 바꾸면 같이 바꾼다 —
     *    바꾸는 순간 **이미 설치한 사람의 동의가 사라진다**는 뜻이기도 하다.
     */
    @Test
    fun consentSurvivesARestart() {
        val ctx = compose.activity.applicationContext
        val prefs = ctx.getSharedPreferences("consent", android.content.Context.MODE_PRIVATE)

        // ① 쓰기
        ConsentStore.attach(ctx)
        ConsentStore.agree()
        assert(prefs.getBoolean("guardian_agreed", false)) { "동의가 기기에 안 써졌다" }

        // ② 읽기 — 저장소에만 참을 두고 다시 붙이면 참으로 읽어 와야 한다
        ConsentStore.withdraw()
        prefs.edit().putBoolean("guardian_agreed", true).commit()
        ConsentStore.attach(ctx)
        assert(ConsentStore.guardianAgreed) { "다시 켰을 때 저장된 동의를 못 읽었다" }

        // 철회도 기기에 남는다
        ConsentStore.withdraw()
        assert(!prefs.getBoolean("guardian_agreed", true)) { "철회가 기기에 안 써졌다" }
    }
}
