package com.example.finalproject_demo.ui.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.ui.Cheek
import com.example.finalproject_demo.ui.Curtain
import com.example.finalproject_demo.ui.Expr
import com.example.finalproject_demo.ui.FeltSky
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.OttoFace
import com.example.finalproject_demo.ui.OttoState
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.felt

/**
 * 모드 — 나레이션 칸 테두리 색 (디자인 시스템 09-28 저녁판 「세 모드 모두 같은 틀」)
 */
enum class NarrationMode(val color: Color, val label: String, val icon: String) {
    STORY(Curtain, "이야기 만들기", "🎭"),
    DIARY(FeltSky, "오늘 이야기", "☀️"),
    COOP(FeltTeal, "같이 만들기", "👪"),
}

/**
 * **나레이션 칸** — 화면 아래 전체 폭 한 줄: **[오또 얼굴] [오또가 하는 말] [녹음 버튼]** 이 모두 칸 **안**에 있다 (09-29 사용자 요청).
 *
 * 전에는 얼굴이 칸 왼쪽 위로 걸쳐 나오고 🎤 · ➡️ 는 칸 밖 오른쪽 아래에 떠 있어서, 무대 위 버튼과 겹치기 쉬웠다.
 *
 *   얼굴 테두리 = 상태 — 분홍 말함 · 청록 들음 · 겨자 생각. **말할 때도 들을 때처럼** 테두리 색 파동이 얼굴 둘레로 퍼진다
 *   얼굴 그림 = 표정 — [Expr] (기쁨 · 깜짝 · 속상 · 궁금 · 뿌듯). 표정마다 움직임도 다르다
 *   칸 테두리 = 모드 — 빨강 이야기 만들기 · 파랑 오늘 이야기 · 청록 같이 만들기
 *
 * 09-30 — 칸이 얼굴 · 버튼을 동그랗게 감싸고, 가운데 글씨 띠는 글씨에 딱 맞게 얇다([NarrationShape]). 모드 딱지는 없앴다 (테두리 색만 모드).
 * 높이는 위 여백 4 + 둥근 자리 92 + 아래 8 = 104dp — 무대 안쪽 아래 여백(`BottomChrome` 108dp)이 이것에 맞춘다.
 *
 * @param trailing 칸 오른쪽 끝에 넣을 버튼들(녹음 · 그리기). 없으면 말하는 중 🔊 · 듣는 중 목소리 막대를 보인다
 */
@Composable
fun Narration(
    mode: NarrationMode,
    state: OttoState,
    line: String,
    modifier: Modifier = Modifier,
    speaker: String? = null,
    burst: Mood = Mood.NONE,
    burstId: Int = 0,
    expr: Expr = Expr.NONE,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    var trailingW by remember { mutableIntStateOf(0) }
    var barH by remember { mutableIntStateOf(0) }
    val rightW = if (trailing != null && trailingW > 0) maxOf(Bulge, with(density) { trailingW.toDp() } + 16.dp) else 0.dp
    val barHdp = with(density) { barH.toDp() }
    val shape = remember(rightW, barHdp) { NarrationShape(Bulge, barHdp, rightW) }
    Box(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp)) {
        // 칸 — 왼쪽 얼굴 · 오른쪽 버튼을 동그랗게 감싸고, 가운데 글씨 띠는 글씨에 딱 맞게 얇다 (09-30 사용자 그림)
        // 바느질 점선은 모양을 줄여 그리는데 이 모양에선 어긋나서 끈다 — 테두리 한 줄만 깔끔하게
        Box(Modifier.matchParentSize().felt(Wool, shape, lift = 5.dp, stitch = false).border(4.dp, mode.color, shape))
        // 글씨 띠 — 칸 밑에 붙는다. 이 높이가 띠 높이다
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .onSizeChanged { barH = it.height }
                .padding(start = Bulge + 8.dp, end = if (rightW > 0.dp) rightW + 8.dp else 16.dp, top = 10.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                if (speaker != null) ParentText { Text(speaker, fontSize = 12.sp, color = mode.color) }
                Text(line, fontSize = 22.sp, color = InkBrown, lineHeight = 29.sp, maxLines = 2)
            }
            if (trailing == null) {
                if (state == OttoState.TALK) {
                    Box(Modifier.size(46.dp).felt(Cheek, CircleShape, lift = 3.dp, stitch = false), contentAlignment = Alignment.Center) { Text("🔊", fontSize = 20.sp) }
                }
                if (state == OttoState.LISTEN) VoiceBars()
            }
        }
        // 녹음 · 그리기 버튼 — 오른쪽 둥근 자리 가운데
        if (trailing != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).height(Bulge).padding(end = 8.dp).onSizeChanged { trailingW = it.width },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = trailing,
            )
        }
        // 오또 얼굴 — 왼쪽 둥근 자리 가운데. 파동은 얼굴과 같은 중심에서 퍼진다
        Box(Modifier.align(Alignment.BottomStart).size(Bulge), contentAlignment = Alignment.Center) {
            OttoFace(state, Modifier.size(FaceSize), burst = burst, burstId = burstId, expr = expr, pulse = true)
        }
    }
}

/**
 * 칸 모양 — 왼쪽 동그라미(얼굴) + 가운데 얇은 띠(글씨) + 오른쪽 둥근 자리(버튼)를 한 줄로 이은 윤곽.
 * 셋 다 밑선이 같고, 동그라미들만 띠 위로 솟는다. 동그라미와 띠가 만나는 곳은 꺾이지 않게 부드럽게 휜다.
 * [right] 가 0 이면 띠가 오른쪽 끝까지 가서 둥글게 끝난다
 */
private class NarrationShape(val bulge: Dp, val bar: Dp, val right: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val w = size.width; val h = size.height
        val d = bulge.toPx().coerceAtMost(h); val rad = d / 2; val cy = h - rad
        val b = bar.toPx().coerceIn(rad * 0.4f, d * 0.85f)
        val r = right.toPx()
        val top = h - b
        val fillet = 12.dp.toPx()
        val dx = kotlin.math.sqrt(rad * rad - (top - cy) * (top - cy))   // 띠 윗선이 동그라미와 만나는 곳까지의 가로 거리
        val meet = Math.toDegrees(kotlin.math.atan2((top - cy).toDouble(), dx.toDouble())).toFloat() // 만나는 곳의 각도 (위쪽이라 음수)
        val ease = 22f  // 부드럽게 휘기 시작하는 각도 폭
        val lcx = rad
        val path = Path().apply {
            moveTo(lcx, h)
            // 왼쪽 동그라미 — 아래 → 왼쪽 → 위 → 띠와 만나기 조금 전까지 (시계 방향)
            arcTo(Rect(lcx - rad, cy - rad, lcx + rad, cy + rad), 90f, (360f + meet - ease) - 90f, false)
            quadraticTo(lcx + dx, top, lcx + dx + fillet, top)
            if (r > 0f) {
                val rcx = w - r + rad
                lineTo(rcx - dx - fillet, top)
                val a0 = 180f - meet + ease   // 오른쪽 둥근 자리 — 띠와 만난 곳 조금 뒤부터
                val ax = rcx + rad * kotlin.math.cos(Math.toRadians(a0.toDouble())).toFloat()
                val ay = cy + rad * kotlin.math.sin(Math.toRadians(a0.toDouble())).toFloat()
                quadraticTo(rcx - dx, top, ax, ay)
                arcTo(Rect(rcx - rad, cy - rad, rcx + rad, cy + rad), a0, 270f - a0, false)
                val ecx = w - rad
                lineTo(ecx, h - d)
                arcTo(Rect(ecx - rad, cy - rad, ecx + rad, cy + rad), 270f, 180f, false)
            } else {
                lineTo(w - b / 2, top)
                arcTo(Rect(w - b, top, w, h), 270f, 180f, false)
            }
            close()
        }
        Outline.Generic(path)
    }
}

private val Bulge = 92.dp
private val FaceSize = 84.dp

/** 아이 목소리 막대 — 들리는 동안 출렁 */
@Composable
private fun VoiceBars() {
    val t = rememberInfiniteTransition(label = "bars")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "k")
    Row(
        Modifier.height(46.dp).felt(FeltTeal, RoundedCornerShape(23.dp), lift = 3.dp, stitch = false).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(10, 22, 32, 16, 26, 12, 20).forEachIndexed { i, h ->
            val f = if (i % 2 == 0) 0.6f + 0.4f * k else 1f - 0.4f * k
            Box(Modifier.width(6.dp).height((h * f).dp).clip(RoundedCornerShape(3.dp)).background(FeltWhite))
        }
    }
}
