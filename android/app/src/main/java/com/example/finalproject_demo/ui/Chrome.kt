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
import androidx.compose.runtime.LaunchedEffect
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

/** 누를 때 네모난 물결(리플)이 생기지 않는 클릭 — 그림을 누를 때 쓴다 (v0.8) */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
}

/** 화면 맨 위 가운데 — 지금 무엇을 하는 화면인가 */
@Composable
fun TitleChip(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Box(
        modifier
            .shadow(6.dp, RoundedCornerShape(999.dp), ambientColor = Ink.copy(alpha = 0.2f), spotColor = Ink.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(999.dp))
            .background(if (dark) Color(0xFF3A332C).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 18.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 16.sp, color = if (dark) Color.White else Ink, fontWeight = FontWeight.Bold)
    }
}

/** 별 모양 경로 (다섯 꼭지) */
private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
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
 */
@Composable
fun ProgressTrack(filled: Int, total: Int, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((filled.toFloat() / total).coerceIn(0f, 1f), tween(700), label = "prog")
    val done = filled >= total
    val inf = rememberInfiniteTransition(label = "prog")
    val glow by inf.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "glow")
    val shimmer by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1600)), label = "shimmer")
    Box(modifier.width(280.dp).height(34.dp), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.padding(end = 20.dp).fillMaxWidth().height(16.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.85f), cornerRadius = r)
            drawRoundRect(Color(0xFFE3D6BF), cornerRadius = r, style = Stroke(2.dp.toPx()))
            if (frac > 0f) {
                val w = size.width * frac
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Sun2, Sun, Coral), endX = size.width),
                    size = Size(w, size.height), cornerRadius = r,
                )
                // 차오르는 동안 빛이 지나간다
                val sx = size.width * shimmer
                if (sx >= 0f && sx + size.height <= w) drawRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(sx, 0f), size = Size(size.height, size.height))
            }
        }
        // 끝의 별 — 다 차면 금빛으로 채워지고 커졌다 작아졌다
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .scale(if (done) glow else 1f)
        ) {
            val c = center
            val p = starPath(c.x, c.y, size.minDimension / 2)
            if (done) {
                drawCircle(Sun.copy(alpha = 0.35f), size.minDimension / 2 * 1.1f)
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Sun)))
                drawPath(p, Color(0xFFD98A00), style = Stroke(2.dp.toPx()))
            } else {
                drawPath(p, Color.White)
                drawPath(p, Color(0xFFD9C8A8), style = Stroke(2.5f.dp.toPx()))
                // 조금씩 차오르는 별 안쪽
                if (frac > 0f) drawPath(starPath(c.x, c.y, size.minDimension / 2 * 0.55f * frac), Sun2)
            }
        }
    }
}

/**
 * 하루 별(재화) — 왼쪽 별 아이콘 + 오른쪽 둥근 모서리 틀(왼쪽 변 없음) 안에 숫자.
 * 한도가 꺼져 있으면 ∞.
 */
@Composable
fun StarWallet(count: Int, unlimited: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
        // 틀: 위 · 오른쪽 · 아래만 그린다 (왼쪽 변이 열려 있고, 그 자리에 별이 걸친다)
        Box(
            Modifier
                .padding(start = 22.dp)
                .height(34.dp)
                .widthIn(min = 64.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val sw = 2.5f.dp.toPx()
                val r = 10.dp.toPx()                 // 둥근 모서리
                val top = sw / 2
                val bottom = size.height - sw / 2
                val right = size.width - sw / 2
                fun outline(closeLeft: Boolean) = Path().apply {
                    moveTo(0f, top)
                    lineTo(right - r, top)
                    arcTo(androidx.compose.ui.geometry.Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
                    lineTo(right, bottom - r)
                    arcTo(androidx.compose.ui.geometry.Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
                    lineTo(0f, bottom)
                    if (closeLeft) close()        // 칠하기만 닫고, 테두리는 왼쪽 변을 그리지 않는다
                }
                drawPath(outline(true), Color.White.copy(alpha = 0.92f))
                drawPath(outline(false), Color(0xFFD9A441), style = Stroke(sw))
            }
            Text(
                if (unlimited) "∞" else "$count",
                fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).padding(start = 30.dp, end = 16.dp),
            )
        }
        // 왼쪽 별
        Canvas(Modifier.size(44.dp)) {
            val p = starPath(center.x, center.y, size.minDimension / 2)
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Sun)))
            drawPath(p, Color(0xFFD98A00), style = Stroke(2.dp.toPx()))
        }
    }
}


/**
 * 마스코트 말풍선 — 화면 아래 왼쪽에 떠 있다 (아래 띠를 없애 무대를 넓게 쓴다 · v0.8).
 * 말할 때마다 풍선이 톡 튀어나오고 글자가 한 자씩 써진다. 마스코트는 말하는 동안 콩콩 뛴다.
 */
@Composable
fun MascotBubble(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    val id = s.lineId
    val text = s.line
    val pop = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(text.length) }
    LaunchedEffect(id) {
        shown = 0
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(id, text) {
        while (shown < text.length) {
            delay(28)
            shown++
        }
    }
    val talking = shown < text.length
    val inf = rememberInfiniteTransition(label = "mascot")
    val hop by inf.animateFloat(0f, -7f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "hop")
    val tilt by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tilt")
    if (text.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier
                .size(68.dp)
                .offset { IntOffset(0, if (talking) hop.roundToInt() else 0) }
                .rotate(if (talking) 0f else tilt)
        ) { ArtView(Art.Mascot, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(4.dp))
        Column(
            Modifier
                .padding(bottom = 18.dp)
                .widthIn(max = 470.dp)
                .scale(pop.value)
                .alpha(((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f))
                .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            if (s.speaker != "마스코트") Text(s.speaker, fontSize = 11.sp, color = Coral, fontWeight = FontWeight.Bold)
            Text(text.take(shown), fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 22.sp)
        }
    }
}

/** 오른쪽 아래에 떠 있는 🎤 · ➡️ (쓸 수 있을 때만 보인다) */
@Composable
fun FloatingControls(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (s.drawEnabled) {
            Box(
                Modifier
                    .padding(end = 10.dp, bottom = 8.dp)
                    .shadow(6.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .clickable { d.send(com.example.finalproject_demo.demo.Reply.Tapped("draw", "직접 그리기")) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("🖍️ 직접 그리기", fontSize = 15.sp, color = Ink) }
        }
        if (s.nextEnabled) {
            Box(
                Modifier
                    .padding(end = 10.dp, bottom = 4.dp)
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Sun)
                    .clickable { d.skip() },
                contentAlignment = Alignment.Center,
            ) { ArtView(Art.Img("ic_next", Art.Emoji("➡️")), Modifier.size(30.dp)) }
        }
        if (s.micEnabled) {
            val inf = rememberInfiniteTransition(label = "mic")
            val pulse by inf.animateFloat(1f, 1.14f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "pulse")
            Box(contentAlignment = Alignment.Center) {
                if (s.micOn) Box(Modifier.size(78.dp).scale(pulse).clip(CircleShape).background(Coral.copy(alpha = 0.25f)))
                Box(
                    Modifier
                        .size(64.dp)
                        .shadow(10.dp, CircleShape, ambientColor = Coral.copy(alpha = 0.6f), spotColor = Coral.copy(alpha = 0.6f))
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Coral2, Coral)))
                        .clickable { d.toggleMic() },
                    contentAlignment = Alignment.Center,
                ) { if (s.micOn) Text("⏹", fontSize = 26.sp, color = Color.White) else ArtView(Art.Img("ic_mic", Art.Emoji("🎤")), Modifier.size(38.dp)) }
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
                    .shadow(4.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(popText, fontSize = 17.sp, color = Coral, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** 아래 여백 — 떠 있는 말풍선 · 버튼에 가리지 않게 무대 안쪽에 두는 높이 */
val BottomChrome = 76.dp
val TopChrome = 80.dp

@Composable
fun FullHeightSpacer() = Spacer(Modifier.fillMaxHeight())
