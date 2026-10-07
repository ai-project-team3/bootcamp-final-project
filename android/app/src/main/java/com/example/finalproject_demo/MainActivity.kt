package com.example.finalproject_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.LocalStoryBookStore
import com.example.finalproject_demo.demo.StoryImageStore
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.liveStoryProgress
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.DemoDrawer
import com.example.finalproject_demo.ui.FloatingControls
import com.example.finalproject_demo.ui.MascotBubble
import com.example.finalproject_demo.ui.ParentBand
import com.example.finalproject_demo.ui.Muted
import com.example.finalproject_demo.ui.ProgressTrack
import com.example.finalproject_demo.ui.StoryPreparationTrack
import com.example.finalproject_demo.ui.PuppetTypography
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.FeelPrefs
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.ui.GuardianConsentScreen
import com.example.finalproject_demo.ui.SplashScreen
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.StarWallet
import com.example.finalproject_demo.ui.TitleChip

/**
 * 말로 짓는 인형극 — 데모 (가로 전용).
 * 서버 모드는 실행 인자(`-e server … -e live …` · 저장하지 않는다)로 켠다. 인자가 없으면 디버그 빌드는 [DEFAULT_SERVER] 로
 * 세 모드를 켜고(10-02 #47), 스토어 빌드는 대본으로 흐름과 화면을 보여 준다.
 *
 * 켜면 팀 이름 스플래시(CLAP) → 첫 화면.
 * 화면 구성 (v0.8): 무대가 화면 전체를 쓴다.
 *  - 맨 위 가운데: 진행 막대(끝에 별 · 화면 가장 위)
 *  - 아래 왼쪽: 마스코트 말풍선 (떠 있음) · 아래 오른쪽: 🎤 ➡️ (쓸 수 있을 때만)
 *  - 시작 화면 · 책 · 부모 모드 · 비밀번호에서는 마스코트 말풍선을 숨긴다
 */
/** 팀 서버의 공개 주소(https · Cloudflare 터널) — 인자 없이 켠 디버그 빌드가 붙는 곳 */
const val DEFAULT_SERVER = "https://otto-back.shelldocs.cloud"

class MainActivity : ComponentActivity() {
    /** 지금 흐름 — 검사(`ShellFlowTest`)가 상태를 들여다볼 때 쓴다 */
    var director: Director? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 동의를 기기에서 읽어 온다 — 없으면 켤 때마다 동의 화면이 다시 뜬다 (09-25)
        ConsentStore.attach(this)
        FeelPrefs.load(this)      // 효과음 · 진동 켬/끔 (부모 설정 · 09-25)
        // 서버 주소 — `-e server` 가 먼저(디버그만). 없으면 공개 주소로 세 모드를 켠다(10-02 #47).
        // 10-06 조장: 스토어(릴리스) 빌드도 서버 연결판이다 — 호출 한도(`net/CallLimits` · 기기 하루 두 권)와
        // 서버 하루 상한(`backend/app/limits.py`)이 같이 켜지고, 처리방침(약관 2026-10-06.3)을 같은 업로드에서 맞췄다.
        // 화면 검사(Robolectric)는 끈다 — 검사가 진짜 서버를 부르면 안 된다
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val byDefault = !android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
        // 인자는 **디버그 빌드만** 읽는다 (10-06) — 런처가 exported 라, 릴리스에서도 읽으면 다른 앱이 임의 https 주소를 넣어
        // 아이 음성(/stt)을 그쪽으로 보낼 수 있다. 스토어 빌드는 인자를 무시하고 공개 주소로만 간다
        val extra: (String) -> String? = { k -> if (debuggable) intent?.getStringExtra(k) else null }
        (extra("server") ?: DEFAULT_SERVER.takeIf { byDefault })?.let { Server.base = it.trimEnd('/') }
        // 어느 모드를 서버로 돌릴지 — `-e live story,diary,coop` 또는 `all`. 기본 주소로 켰으면 전부
        (extra("live") ?: "all".takeIf { byDefault && Server.base == DEFAULT_SERVER })
            ?.let { Server.liveModes = Server.parseLive(it) }
        com.example.finalproject_demo.demo.SessionReports.attach(this)   // 부모 리포트 — 책마다 폰 안에만 (10-06)
        com.example.finalproject_demo.demo.Rewards.attach(this)   // 업적 보상 — 폰 안에만 (10-06 · #223)
        com.example.finalproject_demo.net.CallLimits.attach(this)   // 서버 연결판 하루 한도 — 폰에만 (10-06)
        com.example.finalproject_demo.net.CallLimits.enabled = !debuggable   // 스토어 빌드만 — 팀 개발 앱 · 검사는 막지 않는다
        Voice.attach(this)        // 진짜 마이크 · 마스코트 목소리 — 서버 모드에서만 쓴다 (net/Voice.kt)
        com.example.finalproject_demo.sound.ChildSound.attach(this)   // 아이가 만든 소리 — 폰에만 (#42)
        com.example.finalproject_demo.net.ChildCall.attach(this)     // 마스코트가 아이를 부르는 말 — 부모가 정함 (10-02)
        com.example.finalproject_demo.sound.ChildSound.discardSession()   // 책에 안 넣은 채 앱이 꺼졌던 소리
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 풀스크린 — 카메라 구멍(노치) 쪽까지 그린다 (09-29). 가로 화면에서 한쪽에 검은 띠가 남지 않게
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MaterialTheme(typography = PuppetTypography) { com.example.finalproject_demo.ui.FitScreen { DemoApp() } } }
    }

    /** 앱으로 돌아올 때 · 창(설정 · 알림)이 닫힐 때마다 다시 전체 화면으로 (09-29) */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) com.example.finalproject_demo.ui.shell.hideBars(window)
    }
}

@Composable
fun DemoApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val d = remember { Director(scope, LocalStoryBookStore(context), StoryImageStore(context)).also {
        com.example.finalproject_demo.demo.DiaryShelf.attach(context, it.s)   // 그림일기 책장 저장(#37)
        com.example.finalproject_demo.demo.CoopShelf.attach(context, it.s)    // 같이 만들기 책장 저장(#83)
        it.recoverStoryImages()   // 세 책장을 다 붙인 뒤 — 아무 책도 안 쓰는 서버 그림을 지운다(#61 · #80)
        com.example.finalproject_demo.demo.CoopPlan.attach(context, it.s)     // 부모가 저장한 같이 만들기 이야기 · 질문(#98)
    } }
    (context as? MainActivity)?.director = d
    var drawerOpen by remember { mutableStateOf(false) }
    val s = d.s

    LaunchedEffect(Unit) { d.go(Scene.ADULT) }

    val pinStage = s.stage as? Stage.Pin
    // 책장 아래 선반 줄을 말풍선이 가렸다 — 책장에서는 오또 말은 목소리로만(#154)
    val bubbleHidden = s.scene in setOf(Scene.ADULT, Scene.BOOK, Scene.PARENT, Scene.SHELF) || pinStage != null
    // 그림일기 화면은 대사 칸 · 별 막대를 스스로 그린다 (D1 작은 말풍선 · D3 별 둘 · D5 없음 — ui/DiaryViews.kt)
    val diaryOwnsChrome = s.stage is com.example.finalproject_demo.demo.DiaryStage

    Box(Modifier.fillMaxSize().background(Bg)) {
      // 처음 설정(스플래시 · 로그인 · 동의 …)이 끝나기 전에는 흐름 화면을 그리지 않는다 (09-29).
      // 설정 창(Dialog)이 뜨기 한순간 전에 **뒤의 옛 첫 화면이 비쳐** 켤 때 화면이 이상해 보였다
      if (com.example.finalproject_demo.ui.shell.Shell.step == com.example.finalproject_demo.ui.shell.Step.APP) {
        // Stage.Adult is Otto's room, which OttoShell draws on top of everything. Drawing the old start
        // screen (AdultScreen) under it kept its logo bob running: the hidden screen made the whole window
        // redraw 60 times a second — ~140% CPU idle on a Galaxy S10 5G (eval/results.md 09-30)
        if (s.stage != Stage.Adult) StageView(d, Modifier.fillMaxSize())

        // Live story progress names preparation milestones, not an unknown number of future questions.
        // Other modes and scripted stories keep their existing track; diary stages own their chrome.
        if (s.progressVisible && pinStage == null && !diaryOwnsChrome) {
            val trackModifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
            val storyProgress = s.liveStoryProgress
            if (storyProgress != null) StoryPreparationTrack(storyProgress, trackModifier)
            else ProgressTrack(s.askDone, s.askTotal, trackModifier)
        }

        // 왼쪽 위 = 시스템 — 🏠 방으로 · 🔒 부모 문(2초). 방 · 부모 · 책장은 자기 버튼이 있어 뺀다
        val kidScreen = pinStage == null && s.scene !in setOf(Scene.ADULT, Scene.PARENT, Scene.SHELF)
        // 이야기 도중에는 🔒 대신 오른쪽 위 ⏸ (#125 · 10-05 조장 — 이야기 중에는 부모 모드가 필요 없다).
        // 그림일기도 같은 자리 — 일기 화면이 그 아래로 비킨다 (#163 · 10-06 진웅 · ui/DiaryViews.kt PauseBottom)
        val pausable = kidScreen && (s.mode == com.example.finalproject_demo.demo.StoryMode.STORY || s.mode == com.example.finalproject_demo.demo.StoryMode.DIARY || s.isCoop)
        if (kidScreen) com.example.finalproject_demo.ui.shell.KidTopBar(d, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp), lock = !diaryOwnsChrome && !pausable)
        // 시연 서랍 칸(오른쪽 위 48dp · 아래)보다 위에 — 아니면 ⏸ 가운데와 오른쪽 위 누름을 서랍 칸이 먹는다(10-06 화면 검사).
        // 서랍은 ⏸ 바깥 구석(오른쪽 끝 12dp · 위 10dp)을 길게 누르면 그대로 열린다
        if (pausable) com.example.finalproject_demo.ui.shell.PauseButton(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 10.dp).zIndex(12f).testTag("pause")) { d.holdSession() }

        // 화면이 꺼지거나 앱이 뒤로 가면 이야기 도중이면 ⏸ (#125) — 돌아오면 「잠깐 쉬는 중」이 떠 있다
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val inSession by rememberUpdatedState(kidScreen)
        DisposableEffect(owner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP && inSession) d.holdSession()
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        // 시작 화면 오른쪽 위 — 하루 별
        if (s.scene == Scene.ADULT && pinStage == null) {
            StarWallet(s.dayStars, unlimited = !s.limitOn, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp))
        }

        s.countdown?.let { left ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("기다리는 중 ${String.format("%.1f", left)}초", fontSize = 13.sp, color = Muted)
            }
        }

        // 화면 아래는 **겹치지 않게 쌓는다** (9/22).
        //
        // 전에는 말풍선과 부모 띠가 둘 다 화면 맨 아래에 놓여 서로 겹쳤다. 그걸 피하려고
        // 띠가 떠 있는 동안 말풍선을 통째로 숨겼는데(`parentCard == null`), 그 바람에
        // **아이가 답한 뒤 마스코트가 받아주는 말이 화면에서 사라졌다.**
        // 협업 모드에서 부모에게 넘긴 것은 ③ 다음 질문 하나뿐이고, ① 받아주기와 ② 되돌려주기는
        // 마스코트가 그대로 한다 (부모협업모드_설계.md §2-2). 그러니 반응은 보여야 한다.
        //
        // 이제 Column으로 쌓는다 — 위가 마스코트 말풍선, 아래가 부모 띠. 겹칠 수가 없다.
        // 나레이션은 화면 **맨 앞** — 오또가 뛰어도 무대 그림 · 다른 층에 가리지 않는다 (10-01)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(10f)) {
            if (!bubbleHidden && !diaryOwnsChrome) MascotBubble(d, Modifier.padding(start = 8.dp, bottom = 6.dp))
            // 아이 화면 위에 덮지 않고 아래에 띠로 붙는다: 아이는 위 그림을, 부모는 아래 글자를 본다
            if (pinStage == null) ParentBand(d)
        }

        // 🎤 · ➡️ 는 **늘 오른쪽 아래**다 (9/22). 전에는 띠가 떠 있으면 172dp 위로 올려
        //  화면 중간에 붕 떠 있었다. 지금은 자리를 지키고, 대신 띠가 글자를 버튼 앞에서 끊는다
        //  (ParentBand.kt 의 END_RESERVE). 버튼 크기를 바꾸면 그 값도 같이 바꾼다.
        // 🎤 · 🖍️ 는 나레이션 칸 **안**에 있다 (09-29). 칸이 없는 화면에서만 오른쪽 아래에 따로 띄운다
        if (pinStage == null && bubbleHidden) FloatingControls(
            d,
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp),
        )

        // 되돌리기 · 앞으로 가기 — 화면 양끝 가운데 (10-02 조장 · 책의 ◀ ▶ 자리). 아이 차례 · 무를 차례가 있을 때만
        if (pinStage == null && s.micEnabled) {
            if (s.canUndo) com.example.finalproject_demo.ui.TurnNavButton(d, undo = true, Modifier.align(Alignment.CenterStart).zIndex(11f))
            if (s.canRedo) com.example.finalproject_demo.ui.TurnNavButton(d, undo = false, Modifier.align(Alignment.CenterEnd).zIndex(11f))
        }

        // 오른쪽 위 구석 길게 누르기 → 시연 서랍 — **디버그 빌드만** (10-06). 서랍에는 「🔢 (시연) 비밀번호 4자리 입력」 ·
        // 하루 한도 되돌리기 · 서버 스위치가 있어, 릴리스에 두면 아이가 구석을 길게 눌러 부모 영역에 닿는다
        if (BuildConfig.DEBUG) Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { drawerOpen = true })
                }
        )

      }
        // 앱 틀 (09-29 · 치영) — ⓪ CLAP → ① 타이틀 → 처음이면 로그인 · 동의 · 마이크 · 튜토리얼 → ⑨ 오또의 방.
        // 예전 「스플래시 → 보호자 동의」 자리를 이 틀이 맡는다. 동의 화면은 전처럼 **자기 창**이라 뒤로 터치가 새지 않는다.
        // 흐름(Director)은 그대로이고, 틀은 기존 신호(start · diary · coop · shelf · parent)만 보낸다 — ui/shell/Shell.kt
        com.example.finalproject_demo.ui.shell.OttoShell(d)

        // 모드를 시작하면 방의 별 하나가 날아가 별 막대 끝에 앉는다 — 누름을 받지 않는 장식 층 (10-06 종훈)
        com.example.finalproject_demo.ui.StarFlightOverlay(s.starFlights, Modifier.zIndex(13f))

        // 시연 서랍은 틀보다 **위에** — 오또의 방 위에서도 열려야 한다
        if (drawerOpen) DemoDrawer(d) { drawerOpen = false }
    }
}
