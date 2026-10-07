package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.demo.Stage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 누를 때 네모난 물결(리플)이 생기지 않는 클릭 — 그림을 누를 때 쓴다 (v0.8) */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
}

/** 화면 맨 위 가운데 — 지금 무엇을 하는 화면인가 */
@Composable
fun TitleChip(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    // 09-29 디자인 시스템 — 흰 펠트 판 (장면 위에서도 읽히게)
    Box(
        modifier
            .felt(if (dark) InkBrown.copy(alpha = 0.92f) else FeltWhite.copy(alpha = 0.94f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false, texture = !dark)
            .padding(horizontal = 18.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 16.sp, color = if (dark) FeltWhite else InkBrown)
    }
}

/** 별 모양 경로 (다섯 꼭지) */
internal fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.46f
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (rr * cos(a)).toFloat()
        val y = cy + (rr * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * 진행 막대 — 칸이 찰 때마다 로딩처럼 차오르고, 끝에 별이 있다.
 * 6칸이 다 차면 막대 전체와 별이 색으로 가득 차고 반짝인다 → 곧 동화책이 시작된다.
 * 점수가 아니라 "이야기가 얼마나 모였나"만 보여 준다.
 *
 * 09-29 디자인 시스템 「별 모으기 진행」 — 장면 위에서도 읽히게 **흰 펠트 판** 위에 털실 길,
 * 겨자 펠트가 차오르고 윗면에 윤기 한 줄 · 1.6초마다 빛이 지나간다. **쪽마다 별 구슬**(지난 쪽 흰 별 · 남은 쪽 흐린 별)로
 * 글자 없이 몇 쪽째인지 센다. 끝의 **리본 달린 메달**은 안쪽부터 차오르고, 다 차면 금빛 · 빛 원과 함께 숨 쉰다.
 */
@Composable
fun ProgressTrack(filled: Int, total: Int, modifier: Modifier = Modifier, beads: Int = total) {
    val frac by animateFloatAsState((filled.toFloat() / total).coerceIn(0f, 1f), tween(700), label = "prog")
    val done = filled >= total
    // the star that flew from the wallet lands here — the medal bumps once (ui/StarFlight.kt)
    val landed = remember { Animatable(1f) }
    LaunchedEffect(StarFlight.landings) {
        if (StarFlight.landings == 0) return@LaunchedEffect
        landed.snapTo(1.35f)
        landed.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    DisposableEffect(Unit) { onDispose { StarFlight.medal = null } }
    val inf = rememberInfiniteTransition(label = "prog")
    val glow by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val shimmer by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1600)), label = "shimmer")
    Box(modifier.width(320.dp).height(52.dp), contentAlignment = Alignment.CenterStart) {
        // 흰 펠트 판
        Box(
            Modifier
                .padding(end = 22.dp)
                .fillMaxWidth()
                .height(36.dp)
                .felt(FeltWhite.copy(alpha = 0.94f), RoundedCornerShape(Radius.Round), lift = 3.dp)
        ) {
            // 길은 끝의 메달 **밑까지** 이어진다 — 메달이 마지막 별 자리다 (10-01 사용자 요청: 마지막 별과 선이 끊겨 있었다).
            // 판 끝(오른쪽 22dp 안) 에서 13dp 더 들어간 곳 = 메달 안쪽
            Canvas(Modifier.fillMaxSize().padding(start = 14.dp, end = 13.dp)) {
                val h = 12.dp.toPx()
                val y = size.height / 2
                val r = CornerRadius(h / 2)
                // 털실 길 — 크림 바탕에 나무색 털실 점선
                drawRoundRect(WoolCream, Offset(0f, y - h / 2), Size(size.width, h), r)
                drawLine(StageWoodDeep.copy(alpha = 0.45f), Offset(h / 2, y), Offset(size.width - h / 2, y), 2.dp.toPx(),
                    cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                if (frac > 0f) {
                    val w = (size.width * frac).coerceAtLeast(h)
                    drawRoundRect(FeltMustard, Offset(0f, y - h / 2), Size(w, h), r)
                    // 윗면 윤기 한 줄
                    drawLine(FeltWhite.copy(alpha = 0.55f), Offset(h / 2, y - h * 0.22f), Offset(w - h / 2, y - h * 0.22f), 2.dp.toPx(), cap = StrokeCap.Round)
                    val sx = size.width * shimmer
                    if (sx >= 0f && sx + h <= w) drawRect(FeltWhite.copy(alpha = 0.35f), Offset(sx, y - h / 2), Size(h, h))
                }
                // 쪽마다 별 구슬 — 차오르는 선이 별에 닿는 순간 별도 코랄 펠트로 채워진다(흰 바느질 테두리).
                // 아직 닿지 않은 별은 흐린 별 (10-01 사용자 요청: 선만 차고 별은 비어 보였다)
                val reached = if (frac > 0f) (size.width * frac).coerceAtLeast(h) else 0f
                for (i in 1 until beads) {
                    val cx = size.width * i / beads
                    val star = starPath(cx, y, 7.5f.dp.toPx())
                    if (cx <= reached) {
                        drawPath(star, FeltCoral)
                        drawPath(star, FeltWhite, style = Stroke(1.6f.dp.toPx()))
                    } else {
                        drawPath(star, InkSoft.copy(alpha = 0.28f))
                    }
                }
            }
        }
        // 끝의 메달 — 리본 두 가닥 + 둥근 메달 + 별. 안쪽부터 차오르고 다 차면 숨 쉰다
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .size(50.dp)
                .onGloballyPositioned { StarFlight.medal = it.boundsInRoot() }
                .scale((if (done) glow else 1f) * landed.value)
        ) {
            val c = center
            val rr = size.minDimension * 0.36f
            // 리본
            for (sgn in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(c.x + sgn * rr * 0.25f, c.y + rr * 0.4f)
                    lineTo(c.x + sgn * rr * 0.85f, c.y + rr * 1.35f)
                    lineTo(c.x + sgn * rr * 0.45f, c.y + rr * 1.2f)
                    lineTo(c.x + sgn * rr * 0.2f, c.y + rr * 1.45f)
                    close()
                }
                drawPath(p, if (done) FeltCoral else FeltCoral.copy(alpha = 0.55f))
            }
            if (done) drawCircle(FeltMustard.copy(alpha = 0.35f), rr * 1.45f, c)
            drawCircle(if (done) FeltMustard else WoolCream, rr, c)
            if (!done && frac > 0f) drawCircle(FeltMustard.copy(alpha = 0.85f), rr * frac, c)
            drawCircle(if (done) Color(0xFFC98A12) else StageWood, rr, c, style = Stroke(2.2f.dp.toPx()))
            val st = starPath(c.x, c.y, rr * 0.62f)
            drawPath(st, if (done) FeltWhite else FeltWhite.copy(alpha = 0.9f))
        }
    }
}

/**
 * 하루 재화 — **오늘 만들 수 있는 책 N권** (09-29 디자인 시스템: 코랄 책 동전 + 흰 펠트 판).
 * 한도가 꺼져 있으면 ∞.
 */
@Composable
fun StarWallet(count: Int, unlimited: Boolean, modifier: Modifier = Modifier, flightSource: Boolean = false) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
        // the coin glows while a star leaves it for the mode (ui/StarFlight.kt)
        if (flightSource && StarFlight.leaving > 0f) Box(
            Modifier.size(48.dp).scale(1f + 0.55f * StarFlight.leaving)
                .background(FeltMustard.copy(alpha = 0.45f * StarFlight.leaving), CircleShape)
        )
        Box(
            Modifier
                .padding(start = 24.dp)
                .height(36.dp)
                .widthIn(min = 68.dp)
                .felt(FeltWhite.copy(alpha = 0.94f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
        ) {
            Text(
                if (unlimited) "∞" else "$count",
                fontSize = 20.sp, color = InkBrown,
                modifier = Modifier.align(Alignment.CenterEnd).padding(start = 32.dp, end = 16.dp),
            )
        }
        // 코랄 책 동전
        Box(
            Modifier.size(48.dp)
                .then(if (flightSource) Modifier.onGloballyPositioned { StarFlight.wallet = it.boundsInRoot() } else Modifier)
                .felt(FeltCoral, CircleShape, lift = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                // 펼친 책 — 두 쪽
                for (sgn in listOf(-1f, 1f)) {
                    val p = Path().apply {
                        moveTo(w / 2, h * 0.28f)
                        lineTo(w / 2 + sgn * w * 0.46f, h * 0.18f)
                        lineTo(w / 2 + sgn * w * 0.46f, h * 0.82f)
                        lineTo(w / 2, h * 0.92f)
                        close()
                    }
                    drawPath(p, FeltWhite)
                }
                drawLine(FeltCoral, Offset(w / 2, h * 0.3f), Offset(w / 2, h * 0.9f), 1.5f.dp.toPx())
            }
        }
    }
}


/**
 * 누르면 흔들리고 바로 위에 글자가 떠오르는 그림 (HTML 데모와 같은 반응 · v0.8).
 * text가 null이면 누를 수 없는 그림.
 */
@Composable
fun Tappable(
    text: (() -> String?)?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var popKey by remember { mutableIntStateOf(0) }
    var popText by remember { mutableStateOf("") }
    val wiggle = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey == 0) return@LaunchedEffect
        rise.snapTo(0f)
        wiggle.snapTo(0f)
        wiggle.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 600f), initialVelocity = 260f)
    }
    LaunchedEffect(popKey) {
        if (popKey == 0) return@LaunchedEffect
        rise.animateTo(1f, tween(1100))
    }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(wiggle.value)
                // 누를 수 있는 그림이면 두꺼운 테두리 (09-27 · Interactive.kt)
                .touchOutline(text != null)
                .then(
                    if (text != null) Modifier.noRippleClickable {
                        val t = text()
                        if (t != null) {
                            popText = t
                            popKey++
                        }
                        onTap()
                    } else Modifier
                )
        ) { content() }
        if (popKey > 0 && rise.value < 1f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .wrapContentSize(unbounded = true)
                    .offset { IntOffset(0, (-28 - 46 * rise.value).dp.roundToPx()) }
                    .alpha((1f - rise.value * rise.value).coerceIn(0f, 1f))
                    .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(popText, fontSize = 18.sp, color = FeltCoral, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/**
 * 아래 여백 — 오또 나레이션 칸에 가리지 않게 무대 안쪽에 두는 높이.
 * 09-29 나레이션 칸이 화면 아래 전체 폭(위 여백 30 + 칸 84 + 아래 8 = 122dp)이 되면서 76dp 로는
 * 주인공 고르기 다섯째 줄 · 카드가 칸 밑에 깔렸다 → 칸 높이에 맞춘다 (칸 전체 112dp → 116dp)
 */
val BottomChrome = 116.dp
/** 위 여백 — 🏠 · 🔒(누르는 자리 10 + 56dp)과 진행 막대 아래 */
val TopChrome = 72.dp

