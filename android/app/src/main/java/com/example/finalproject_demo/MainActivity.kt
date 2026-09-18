package com.example.finalproject_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.finalproject_demo.ui.Muted
import com.example.finalproject_demo.ui.ProgressTrack
import com.example.finalproject_demo.ui.PuppetTypography
import com.example.finalproject_demo.ui.StageView
import com.example.finalproject_demo.ui.StarWallet
import com.example.finalproject_demo.ui.TitleChip

/**
 * 말로 짓는 인형극 — 데모 (가로 전용).
 * 백엔드 · 네트워크 · 권한 없음. 흐름과 화면만 보여준다.
 *
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
    val s = d.s

    LaunchedEffect(Unit) { d.go(Scene.ADULT) }

    val pinStage = s.stage as? Stage.Pin
    val bubbleHidden = s.scene in setOf(Scene.ADULT, Scene.BOOK, Scene.PARENT) || pinStage != null

    Box(Modifier.fillMaxSize().background(Bg)) {
        StageView(d, Modifier.fillMaxSize())

        // 맨 위 가운데 — 진행 막대(가장 위) + 그 아래 화면 이름
        Column(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.progressVisible && pinStage == null) ProgressTrack(s.filled, 6, Modifier.padding(bottom = 2.dp))
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

        if (!bubbleHidden) MascotBubble(d, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp))
        if (pinStage == null) FloatingControls(d, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp))

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
    }
}
