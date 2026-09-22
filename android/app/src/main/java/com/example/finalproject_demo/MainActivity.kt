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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.Bg
import com.example.finalproject_demo.ui.DemoDrawer
import com.example.finalproject_demo.ui.FloatingControls
import com.example.finalproject_demo.ui.MascotBubble
import com.example.finalproject_demo.ui.ParentBand
import com.example.finalproject_demo.ui.Muted
import com.example.finalproject_demo.ui.ProgressTrack
import com.example.finalproject_demo.ui.PuppetTypography
import com.example.finalproject_demo.ui.SplashScreen
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.StarWallet
import com.example.finalproject_demo.ui.TitleChip

/**
 * 말로 짓는 인형극 — 데모 (가로 전용).
 * 백엔드 · 네트워크 · 권한 없음. 흐름과 화면만 보여준다.
 *
 * 켜면 팀 이름 스플래시(CLAP) → 첫 화면.
 * 화면 구성 (v0.8): 무대가 화면 전체를 쓴다.
 *  - 맨 위 가운데: 진행 막대(끝에 별 · 화면 가장 위) · 그 아래 지금 무엇을 하는 화면인지
 *  - 아래 왼쪽: 마스코트 말풍선 (떠 있음) · 아래 오른쪽: 🎤 ➡️ (쓸 수 있을 때만)
 *  - 시작 화면 · 책 · 부모 모드 · 비밀번호에서는 마스코트 말풍선을 숨긴다
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MaterialTheme(typography = PuppetTypography) { DemoApp() } }
    }
}

@Composable
fun DemoApp() {
    val scope = rememberCoroutineScope()
    val d = remember { Director(scope) }
    var drawerOpen by remember { mutableStateOf(false) }
    var splash by remember { mutableStateOf(true) }
    val s = d.s

    LaunchedEffect(Unit) { d.go(Scene.ADULT) }

    val pinStage = s.stage as? Stage.Pin
    val bubbleHidden = s.scene in setOf(Scene.ADULT, Scene.BOOK, Scene.PARENT) || pinStage != null

    Box(Modifier.fillMaxSize().background(Bg)) {
        StageView(d, Modifier.fillMaxSize())

        // 맨 위 가운데 — 진행 막대(가장 위) + 그 아래 화면 이름
        Column(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // 필수 칸은 동화 모드 6개 · 일기 모드 기승전결 네 자리 (일기 설계 §2-1)
            // 막대는 **물은 질문 수**로 찬다 (9/22). 필수 칸(4·6)으로 세면 질문을 여러 개 답해도
            // 안 움직이다가 한 번에 뛴다. 끝나는 조건은 여전히 filled/reqCount 가 정한다 (Model.askTotal)
            if (s.progressVisible && pinStage == null) ProgressTrack(s.askDone, s.askTotal, Modifier.padding(bottom = 2.dp))
            // 부모 모드는 화면 안의 고정 머리에 이름이 있다 (스크롤 내용과 겹치지 않게)
            if (s.scene != Scene.PARENT || pinStage != null) TitleChip(
                if (pinStage != null) (if (pinStage.purpose == "start") "어른 확인" else "부모 확인") else s.scene.label,
                dark = s.scene == Scene.BOOK,
            )
        }

        // 시작 화면 오른쪽 위 — 하루 별
        if (s.scene == Scene.ADULT && pinStage == null) {
            StarWallet(s.dayStars, unlimited = !s.limitOn, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp))
        }

        s.countdown?.let { left ->
            Box(
                Modifier
                    .align(Alignment.TopStart)
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
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (!bubbleHidden) MascotBubble(d, Modifier.padding(start = 8.dp, bottom = 6.dp))
            // 아이 화면 위에 덮지 않고 아래에 띠로 붙는다: 아이는 위 그림을, 부모는 아래 글자를 본다
            if (pinStage == null) ParentBand(d)
        }

        // 🎤 · ➡️ 는 **늘 오른쪽 아래**다 (9/22). 전에는 띠가 떠 있으면 172dp 위로 올려
        //  화면 중간에 붕 떠 있었다. 지금은 자리를 지키고, 대신 띠가 글자를 버튼 앞에서 끊는다
        //  (ParentBand.kt 의 END_RESERVE). 버튼 크기를 바꾸면 그 값도 같이 바꾼다.
        if (pinStage == null) FloatingControls(
            d,
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp),
        )

        // 오른쪽 위 구석 길게 누르기 → 시연 서랍
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { drawerOpen = true })
                }
        )

        if (drawerOpen) DemoDrawer(d) { drawerOpen = false }

        // 앱을 켜면 팀 이름(CLAP)이 먼저 — 첫 화면 위를 덮었다가 옅어진다
        if (splash) SplashScreen { splash = false }
    }
}
