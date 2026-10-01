package com.example.finalproject_demo.ui.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import com.example.finalproject_demo.R
import com.example.finalproject_demo.ui.Felt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Paint
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
 * **나레이션 칸** — 화면 아래 전체 폭 한 줄: **[오또 얼굴 + 오또가 하는 말]** 칸과, 그 오른쪽 칸 밖의 **[녹음 버튼]**.
 *
 * 09-29 에는 버튼까지 칸 안에 넣었다가, 09-30 칸이 얼굴을 감싸는 펠트 모양이 되면서 버튼은 칸 밖 오른쪽으로 뺐다 (사용자 요청).
 * 버튼은 칸과 같은 줄 · 얼굴과 같은 높이 가운데라 무대 위 버튼과 겹치지 않는다.
 *
 *   얼굴 테두리 = 상태 — 분홍 말함 · 청록 들음 · 겨자 생각. **말할 때도 들을 때처럼** 테두리 색 파동이 얼굴 둘레로 퍼진다
 *   얼굴 그림 = 표정 — [Expr] (기쁨 · 깜짝 · 속상 · 궁금 · 뿌듯). 표정마다 움직임도 다르다
 *   칸 테두리 = 모드 — 빨강 이야기 만들기 · 파랑 오늘 이야기 · 청록 같이 만들기
 *
 * 09-30 — 칸이 얼굴을 동그랗게 감싸고 (녹음 · 그리기 버튼은 칸 밖 오른쪽), 가운데 글씨 띠는 글씨에 딱 맞게 얇다([NarrationShape]). 모드 딱지는 없앴다 (테두리 색만 모드).
 * 칸은 펠트로 오려 붙인 모양 — 모드 색 펠트 테두리 + 크림 펠트 천 + 바느질 점선 ([drawFeltCloth] · [drawFeltEdge]). 테두리 띠는 얼굴 위에 그려 얼굴 가장자리를 덮는다.
 * 높이는 위 여백 4 + 둥근 자리 100 + 아래 8 = 112dp — 무대 안쪽 아래 여백(`BottomChrome` 116dp)이 이것에 맞춘다.
 *
 * @param trailing 칸 밖 오른쪽에 세울 버튼들(녹음 · 그리기). 없으면 말하는 중 🔊 · 듣는 중 목소리 막대를 보인다
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
    // 글씨 띠 높이 — **그리기 단계에서만** 읽는다. 조합 단계에서 읽으면 한 프레임 늦게 모양이 바뀌어,
    // 나레이션이 뜰 때마다 첫 프레임에 띠 높이 0 인 옛 모양이 번쩍 보였다 (09-30 사용자 제보)
    val barH = remember { mutableIntStateOf(0) }
    val creamImg = ImageBitmap.imageResource(R.drawable.narration_felt)
    val fiberImg = ImageBitmap.imageResource(R.drawable.felt_texture)
    val cream = remember(creamImg) { ShaderBrush(ImageShader(creamImg, TileMode.Repeated, TileMode.Repeated)) }
    val fibers = remember(fiberImg) { ShaderBrush(ImageShader(fiberImg, TileMode.Repeated, TileMode.Repeated)) }
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    // 칸 — 왼쪽 얼굴을 동그랗게 감싸고, 글씨 띠는 글씨에 딱 맞게 얇다 (09-30 사용자 그림)
    Box(Modifier.weight(1f)) {
        // ① 그림자 · 크림 펠트 천
        Box(Modifier.matchParentSize().drawBehind { if (barH.intValue > 0) drawFeltCloth(barH.intValue.toFloat(), cream) })
        // 글씨 띠 — 칸 밑에 붙는다. 이 높이가 띠 높이다
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .onSizeChanged { barH.intValue = it.height }
                .padding(start = Bulge + 8.dp, end = 20.dp, top = 10.dp, bottom = 7.dp),
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
        // 오또 얼굴 — 테두리 안쪽보다 조금 크게 두고 틀 안에서만 움직인다. 가장자리는 ② 테두리 띠가 덮는다
        Box(Modifier.align(Alignment.BottomStart).size(Bulge), contentAlignment = Alignment.Center) {
            OttoFace(state, Modifier.size(FaceSize), burst = burst, burstId = burstId, expr = expr, pulse = true, frame = CircleShape)
        }
        // ② 모드 색 펠트 테두리 띠 · 바느질선 — 얼굴 **위에** 그려 얼굴 가장자리를 덮는다
        Box(Modifier.matchParentSize().drawBehind { if (barH.intValue > 0) drawFeltEdge(barH.intValue.toFloat(), mode.color, fibers) })
    }
    // 녹음 · 그리기 버튼 — 칸 **밖** 오른쪽에 따로 선다 (09-30 사용자 요청).
    // 화면 아래 여백 = 화면 오른쪽 여백이 되게 밑에 붙인다. 이 줄 밑에는 부르는 쪽 여백(약 6dp)이 더 있어서
    // 그만큼 내린다 — 실물폰(S10)에서 오른쪽 · 아래 틈을 재어 맞췄다 (09-30)
    if (trailing != null) {
        Spacer(Modifier.width(12.dp))
        Row(Modifier.align(Alignment.Bottom).offset(y = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), content = trailing)
    }
    }
}

/**
 * 칸 윤곽 — 왼쪽 동그라미(얼굴) + 글씨 띠를 한 줄로 이은 모양. 밑선이 같고 동그라미만 띠 위로 솟는다.
 * 동그라미와 띠가 만나는 곳은 꺾이지 않게 부드럽게 휘고, 띠 오른쪽 끝은 둥글다.
 * [inset] px 만큼 안으로 줄인 같은 모양도 만든다 — 테두리 띠 · 바느질선이 윤곽을 그대로 따라가게
 */
private fun DrawScope.narrationPath(bar: Float, inset: Float): Path {
    val w = size.width; val h = size.height
    val d = Bulge.toPx().coerceAtMost(h); val cy = h - d / 2
    val b = bar.coerceIn(d * 0.2f, d * 0.85f)
    val rad = d / 2 - inset
    val top = h - b + inset
    val bottom = h - inset
    val fillet = 12.dp.toPx()
    val dx = kotlin.math.sqrt((rad * rad - (top - cy) * (top - cy)).coerceAtLeast(0f))   // 띠 윗선이 동그라미와 만나는 곳까지의 가로 거리
    val meet = Math.toDegrees(kotlin.math.atan2((top - cy).toDouble(), dx.toDouble())).toFloat() // 만나는 곳의 각도 (위쪽이라 음수)
    val ease = 22f  // 부드럽게 휘기 시작하는 각도 폭
    val lcx = d / 2
    val er = (bottom - top) / 2
    return Path().apply {
        moveTo(lcx, bottom)
        // 왼쪽 동그라미 — 아래 → 왼쪽 → 위 → 띠와 만나기 조금 전까지 (시계 방향)
        arcTo(Rect(lcx - rad, cy - rad, lcx + rad, cy + rad), 90f, (360f + meet - ease) - 90f, false)
        quadraticTo(lcx + dx, top, lcx + dx + fillet, top)
        lineTo(w - inset - er, top)
        arcTo(Rect(w - inset - er * 2, top, w - inset, bottom), 270f, 180f, false)
        close()
    }
}

/** ① 부드러운 그림자 + 크림 펠트 천(ComfyUI 그림 `narration_felt`) */
private fun DrawScope.drawFeltCloth(bar: Float, cream: ShaderBrush) {
    val outer = narrationPath(bar, 0f)
    drawIntoCanvas { c ->
        val p = Paint().apply { color = Felt.ShadowColor }
        p.asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(8.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(0f, 4.dp.toPx()); c.drawPath(outer, p); c.restore()
    }
    drawPath(outer, cream)
}

/** ② 모드 색 펠트 테두리 띠(양모 결) + 천 가장자리 그늘 + 띠 가운데를 도는 흰 바느질 점선 */
private fun DrawScope.drawFeltEdge(bar: Float, edge: Color, fibers: ShaderBrush) {
    val band = EdgeBand.toPx()
    val ring = Path.combine(PathOperation.Difference, narrationPath(bar, 0f), narrationPath(bar, band))
    drawPath(ring, edge)
    drawPath(ring, fibers, alpha = 0.55f)
    drawPath(narrationPath(bar, band), Color.Black.copy(alpha = 0.10f), style = Stroke(1.5.dp.toPx()))
    val dash = Felt.StitchDash.toPx()
    drawPath(
        narrationPath(bar, band / 2), Color.White.copy(alpha = 0.8f),
        style = Stroke(Felt.StitchWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))),
    )
}

/** 얼굴이 앉는 둥근 자리 */
private val Bulge = 100.dp

/** 모드 색 펠트 테두리 띠 두께 (09-30 8 → 7dp, 살짝 얇게) */
private val EdgeBand = 7.dp

/** 오또 얼굴 — 테두리 안쪽(100 - 7×2 = 86)보다 양쪽 2dp 크게. 가장자리는 테두리 띠가 덮어 틈이 안 보인다 */
private val FaceSize = Bulge - EdgeBand * 2 + 4.dp

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
