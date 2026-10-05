package com.example.finalproject_demo.ui.shell

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.finalproject_demo.ui.InkBrown
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.stopCoopByParent
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.net.Accounts
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.SplashScreen
import com.example.finalproject_demo.ui.Wool

/**
 * 앱 틀 — `MainActivity` 가 흐름 화면(StageView) 위에 **한 번** 부른다 (09-29).
 * 순서는 `Shell.kt` 머리 주석. 흐름(`Director`)에는 기존 신호만 보낸다.
 *
 * ⚠️ 설정 화면(로그인 · 동의 · 마이크 …)은 [Shield] 층에 그린다 — 그 위 화면이 받지 않은 터치를 전부 삼켜
 *    뒤의 🎤 · 시연 서랍으로 새지 않는다(09-25 · ConsentGateTest). 처음 설정 전체가 **층 하나**라 단계 사이에 뒤가 비치지 않는다.
 */
@Composable
fun OttoShell(d: Director) {
    val ctx = LocalContext.current
    remember { Shell.attach(ctx); Accounts.attach(ctx); com.example.finalproject_demo.net.SocialLogin.init(ctx); Shell.applySetup(d.s); true }
    val activity = ctx as? android.app.Activity

    fun afterLogin() { Shell.step = if (Shell.onboarded) Step.APP else Step.CONSENT }

    if (Shell.step == Step.APP) {
        // 동의가 없으면(탈퇴 · 철회 뒤) 말하기 전에 다시 묻는다. 샘플 책 보기는 책장만이라 예외
        if (!ConsentStore.guardianAgreed && !Shell.sampleOnly) Shell.step = Step.CONSENT
        else if (d.s.stage == Stage.Adult) OttoRoom(d, sample = Shell.sampleOnly)
        if (Shell.sheet == Sheet.PIN_CHANGE) Shield(onBack = { Shell.sheet = Sheet.NONE }) { PinChangeSheet() }
        else if (Shell.sheet != Sheet.NONE) Shield(onBack = { Shell.sheet = Sheet.NONE }) { WithdrawSheet(d) }
        // 부모 영역 → 계정 → 기능 안내 다시 보기
        if (Shell.guide) Shield(onBack = { Shell.guide = false }) {
            FeatureGuide(finish = "닫기", onClose = { Shell.guide = false }) { Shell.guide = false }
        }
        // 부모 영역 → 계정에서 연 약관 전문. 이름 부르기는 여기서 「동의하고 닫기」를 눌러야 켜진다(제3자 제공)
        Shell.doc?.let { doc ->
            Shield(onBack = { Shell.doc = null }) {
                TermsSheet(
                    doc, agreed = doc != TermsDoc.NAME_VOICE,
                    onAgree = if (doc == TermsDoc.NAME_VOICE) ({ ConsentStore.setNameVoice(true); Shell.doc = null }) else null,
                    onClose = { Shell.doc = null },
                )
            }
        }
        // 이야기 도중 🏠 — 「방으로 갈까?」 (KidTopBar 가 켠다. 화면 전체를 덮어야 해서 여기서 그린다)
        if (Shell.askHome) Shield(onBack = { Shell.askHome = false }) {
            Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                // 나가도 만들던 이야기는 기억해 둔다 — 방에서 같은 물건을 누르면 「이어서 할까?」
                ConfirmDialog("🏠", "방으로 갈까?", onNo = { Shell.askHome = false }, onYes = { Shell.askHome = false; d.leaveToRoom() })
            }
        }
        // 같이 만들기 도중 부모 「그만하기」 (#36) — 멈추면 지금까지 모인 답으로 책을 만든다
        if (Shell.askStop) Shield(onBack = { Shell.askStop = false }) {
            Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                ConfirmDialog(
                    "✋", "이야기를 여기서 마칠까?",
                    detail = "지금까지 한 이야기로 책을 만들어요",
                    note = "부모님이 누르는 버튼이에요",
                    no = "▶" to "계속하기", yes = "📖" to "마치기",
                    onNo = { Shell.askStop = false },
                    onYes = { Shell.askStop = false; d.stopCoopByParent() },
                )
            }
        }
        return
    }

    Shield {
        // 단계 사이에 뒤가 비치지 않게 창 바닥을 먼저 칠하고, 단계가 바뀔 때는 살짝 겹쳐 바뀐다(뚝 끊기지 않게)
        Box(Modifier.fillMaxSize().background(Wool)) {
            androidx.compose.animation.Crossfade(Shell.step, animationSpec = androidx.compose.animation.core.tween(220), label = "step") { step ->
            when (step) {
                Step.CLAP -> SplashScreen { Shell.step = Step.TITLE }
                Step.TITLE -> TitleScreen {
                    Shell.step = when {
                        !Shell.onboarded -> Step.LOGIN
                        Accounts.guardian == null -> Step.EXPIRED        // 예외 · 로그인이 풀렸을 때
                        // 동의가 없거나, 약관이 바뀌어 새 판에 아직 동의하지 않았으면 다시 묻는다 (처리방침 제11조)
                        !ConsentStore.guardianAgreed || Shell.consentVersion != TERMS_VERSION -> Step.CONSENT
                        else -> Step.APP
                    }
                }
                Step.LOGIN -> LoginScreen(onDone = { afterLogin() }, onEmail = { Shell.emailMode = it; Shell.step = Step.EMAIL })
                Step.EXPIRED -> LoginScreen(onDone = { afterLogin() }, onEmail = { Shell.emailMode = it; Shell.step = Step.EMAIL }, expired = true)
                Step.EMAIL -> EmailScreen(Shell.emailMode, onBack = { Shell.step = if (Shell.onboarded) Step.EXPIRED else Step.LOGIN }, onDone = { afterLogin() })
                Step.CONSENT -> ConsentStep(
                    onBack = { Shell.step = Step.LOGIN },
                    onDone = { Shell.step = if (Shell.onboarded) Step.APP else Step.MIC },
                    onDecline = { activity?.finish() },      // 동의하지 않으면 앱을 닫는다 — 다음에 켜면 다시 묻는다
                )
                Step.MIC -> MicStep(onBack = { Shell.step = Step.CONSENT }, onDone = { Shell.step = Step.PIN })
                Step.PIN -> PinStep(onBack = { Shell.step = Step.MIC }, onDone = { Shell.step = Step.SETUP })
                Step.SETUP -> SetupStep(onBack = { Shell.step = Step.PIN }, onDone = { Shell.applySetup(d.s); Shell.step = Step.FEATURES })
                // 10-05 — 보호자가 기능을 먼저 다 보고(⑥), 아이에게 건넨 뒤(⑦) 아이가 방을 둘러보고(⑧) 말해 본다(⑨)
                Step.FEATURES -> FeatureGuide { Shell.step = Step.HANDOFF }
                Step.HANDOFF -> HandoffStep { Shell.step = Step.TUTORIAL_TAP }
                Step.TUTORIAL_TAP -> OttoRoom(d, tutorial = true) { Shell.step = Step.TUTORIAL_TALK }
                Step.TUTORIAL_TALK -> TutorialTalk { Shell.finishOnboarding() }
                Step.APP -> {}
            }
            }
        }
    }
}

/**
 * **방패 층** — 화면 전체를 덮고, 그 위 화면이 받지 않은 터치를 **전부 삼킨다** (뒤의 🎤 · 시연 서랍으로 새지 않게).
 * 뒤로가기도 여기서 받는다(기본은 아무것도 안 함 — 설정 도중 앱이 닫히지 않게).
 *
 * 09-29 전에는 자기 창(`Dialog`)에 띄웠다. 그런데 실기기(갤럭시 S10 5G · 안드로이드 12)에서 Dialog 창은
 * 시스템 바를 숨겨도 **바가 있던 자리만큼 안쪽으로 줄어**, 스플래시 · 로그인 · 설정 화면 가장자리에 여백이 남았다
 * (「스플래시를 풀스크린으로 만들어 줘」). 본 화면은 끝까지 꽉 차므로, 창을 따로 만들지 않고 본 화면 맨 위 층에 그린다.
 * 터치가 뒤로 새지 않는 것은 `ConsentGateTest` 가 본다.
 */
@Composable
fun Shield(onBack: () -> Unit = {}, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxSize()
            // 터치를 **받기만 하고 처리됨(consume)으로 표시하지 않는다.** 맨 위 층이 터치를 받는 것만으로
            // 그 아래 형제(흐름 화면 · 🎤 · 시연 서랍)에는 전달되지 않는다.
            // ⚠️ 09-29 처음에는 Final 단계에서 전부 consume 했는데, 탭 감지는 손을 뗄 때 Final 단계에서
            //    「누가 이미 처리했나」를 보고 누름을 취소한다 → 실기기에서 층 안의 버튼이 **전부 안 눌렸다**
            //    (「눌러서 시작」이 안 넘어감). JVM 검사는 누름을 한 번에 넣어서 이것을 못 잡았다
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final)
                }
            },
    ) { content() }
}

/** 상태 표시줄 · 내비게이션 바를 숨기고 화면 끝(카메라 구멍 쪽 포함)까지 그린다 */
fun hideBars(window: android.view.Window) {
    @Suppress("DEPRECATION")
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    // 안드로이드 11+ 에서 창(특히 Dialog)은 기본으로 **시스템 바 자리를 피해** 놓인다 — 바를 숨겨도 그 자리만큼
    // 창이 줄어 스플래시 · 설정 화면 가장자리에 여백이 남았다(09-29 실기기 캡처). 피하지 않게 한다
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.attributes = window.attributes.apply { fitInsetsTypes = 0; fitInsetsSides = 0 }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
