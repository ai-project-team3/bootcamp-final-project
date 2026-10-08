package com.example.finalproject_demo.ui.missions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.AssetImage
import androidx.compose.ui.graphics.graphicsLayer
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.Ink
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.rememberTilt
import com.example.finalproject_demo.ui.touchOutline
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 기울기가 이만큼 넘어야 공이 움직인다 — 손에 든 폰의 떨림은 무시(실기기로 다시 정한다 · 설계 §10) */
internal const val TILT_DEAD = 0.06f

/**
 * D4 기울여 굴리기 — 미션 자리 2 (`docs/맞춤미션_설계.md` §4 ★D4 · #101 넷째 순서). 발달 목표는 대근육 · 조절.
 *
 * 아이가 「공을 굴렸어 · 골인 · 공이 굴러갔어」라고 말한 이야기에서 나온다. 폰을 살살 기울이면 공이 그쪽으로 데굴데굴 굴러가고,
 * 오른쪽 골대 안에 들어가면 끝난다. 화면 가장자리에 닿으면 튕긴다.
 * - **탭 길(원칙 6)** — 공을 손가락으로 끌어다 골대에 넣어도 된다. 기울기 센서가 없는 기기에서는 이 길뿐이다
 * - 8초 진전이 없으면 손이 공을 골대 쪽으로 끌어 보인다. 기울기는 그 자리에서 쓰고 버린다(`Tilt.kt`)
 */
@Composable
internal fun RollMission(d: Director, done: Boolean, heroArt: Art) {
    val density = LocalDensity.current.density
    var ball by remember { mutableStateOf<Offset?>(null) }
    var vel by remember { mutableStateOf(Offset.Zero) }
    var spin by remember { mutableStateOf(0f) }
    var inGoal by remember { mutableStateOf(done) }
    val tilt = rememberTilt(!inGoal)
    // 반복문 안에서는 늘 지금 기울기를 읽는다 — 그냥 tilt 를 쓰면 처음 값에 묶인다
    val tiltNow by androidx.compose.runtime.rememberUpdatedState(tilt)
    // 진전 — 공이 골대에 얼마나 가까워졌나(8초 힌트용 · 10칸으로 끊어 조금 움직인 것은 진전으로 치지 않는다)
    var near by remember { mutableStateOf(0f) }
    val idle = rememberIdleHint(near, inGoal)
    val hint = rememberMissionHint(d, near, inGoal, "D4")
    MissionDoneSignal(d, inGoal, done, "미션2")
    // 공이 지나간 자리 — 점선 궤적이 곧 진행이다(막대 대신 · #260 §6-3)
    val trail = remember { androidx.compose.runtime.mutableStateListOf<Offset>() }
    // 골이 들어가면 그물이 한 번 출렁
    val net = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(inGoal) {
        if (!inGoal || done || motionFrozen) return@LaunchedEffect
        net.animateTo(1.14f, tween(140))
        net.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 300f))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val r = 0.045f * wpx
        val start = Offset(0.36f * wpx, 0.56f * hpx)
        val goal = Offset(0.82f * wpx, 0.50f * hpx)
        val goalW = 0.10f * wpx; val goalH = 0.30f * hpx
        val field = Size(wpx, hpx)
        val p = ball ?: start
        Stand(0.14f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        fun settle(at: Offset) {
            ball = at
            if (trail.isEmpty() || hypot(trail.last().x - at.x, trail.last().y - at.y) > r * 0.9f) {
                trail += at
                if (trail.size > 40) trail.removeAt(0)
            }
            val dist = hypot(goal.x - at.x, goal.y - at.y)
            near = ((1f - dist / (goal.x - start.x)).coerceIn(0f, 1f) * 10).roundToInt().toFloat()
            if (!inGoal && kotlin.math.abs(at.x - goal.x) < goalW * 0.5f && kotlin.math.abs(at.y - goal.y) < goalH * 0.45f) inGoal = true
        }

        // 기울인 쪽으로 구른다 — 속도에 마찰을 두고, 가장자리에서 튕긴다
        LaunchedEffect(done, tilt == null, wpx) {
            if (done || tilt == null || motionFrozen) return@LaunchedEffect
            var last = 0L
            while (!inGoal) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val t = tiltNow ?: continue
                val ax = if (kotlin.math.abs(t.x) > TILT_DEAD) t.x else 0f
                val ay = if (kotlin.math.abs(t.y) > TILT_DEAD) t.y else 0f
                var v = Offset(vel.x * 0.985f + ax * wpx * 1.6f * dt, vel.y * 0.985f + ay * wpx * 1.6f * dt)
                var q = (ball ?: start) + v * dt
                if (q.x < r || q.x > field.width - r) { v = Offset(-v.x * 0.5f, v.y); q = Offset(q.x.coerceIn(r, field.width - r), q.y) }
                if (q.y < r || q.y > field.height - r) { v = Offset(v.x, -v.y * 0.5f); q = Offset(q.x, q.y.coerceIn(r, field.height - r)) }
                vel = v
                spin += v.x * dt / r * 57.3f
                settle(q)
            }
        }

        // 지나간 자리 — 옅은 점
        Canvas(Modifier.fillMaxSize()) {
            trail.forEachIndexed { k, q -> drawCircle(Ink.copy(alpha = 0.10f + 0.18f * k / trail.size.coerceAtLeast(1)), r * 0.13f, q) }
        }

        // 골대 — 펠트 그물 그림(prop_goal_net). 없으면 펠트 테두리를 그린다. 공이 들어가면 출렁
        Box(
            Modifier
                .offset { IntOffset((goal.x - goalW * 0.75f).roundToInt(), (goal.y - goalH * 0.6f).roundToInt()) }
                .size((goalW * 1.5f / density).dp, (goalH * 1.2f / density).dp)
                .graphicsLayer { scaleX = net.value; scaleY = 2f - net.value },
        ) {
            AssetImage("prop_goal_net", Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width / 1.5f; val h = size.height / 1.2f
                    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                    drawRoundRect(FeltWhite.copy(alpha = 0.55f), tl, Size(w, h), androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
                    drawRoundRect(Ink.copy(alpha = 0.55f), tl, Size(w, h), androidx.compose.ui.geometry.CornerRadius(w * 0.15f), style = Stroke(w * 0.08f))
                    val step = w / 4
                    for (k in 1..3) drawLine(Ink.copy(alpha = 0.25f), Offset(tl.x + k * step, tl.y), Offset(tl.x + k * step, tl.y + h), strokeWidth = 3f)
                    for (k in 1..5) drawLine(Ink.copy(alpha = 0.25f), Offset(tl.x, tl.y + k * h / 6), Offset(tl.x + w, tl.y + k * h / 6), strokeWidth = 3f)
                }
            }
        }

        // 공 — 끌어서 옮겨도 된다(탭 길)
        val shown = if (inGoal) goal else p
        Box(
            Modifier
                .offset { IntOffset((shown.x - r).roundToInt(), (shown.y - r).roundToInt()) }
                .size((2 * r / density).dp)
                .rotate(spin)
                .pointerInput(inGoal) {
                    if (inGoal) return@pointerInput
                    detectDragGestures { change, drag ->
                        vel = Offset.Zero
                        settle((ball ?: start) + drag)
                        change.consume()
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().touchOutline(!inGoal)) { ArtView(Art.Img("coop_el_soccer", Art.Emoji("⚽")), Modifier.fillMaxSize()) }
        }

        // 15초 흐릿한 예시 — 공이 반투명으로 골대까지 굴러갔다 사라진다(진짜 공은 그대로 · 넣는 것은 아이)
        if (hint != null && !inGoal) {
            val g = ghostAlong(listOf(p, goal), hint)
            Ghost(g, 2 * r, hint) { ArtView(Art.Img("coop_el_soccer", Art.Emoji("⚽")), Modifier.fillMaxSize()) }
            GhostHand(g, wpx * 0.06f, hint)
        } else if (idle && !inGoal) {
            val slide by rememberInfiniteTransition(label = "d4hint").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "slide",
            )
            val hx = p.x + (goal.x - p.x) * slide * 0.6f
            Box(
                Modifier.offset { IntOffset((hx - wpx * 0.01f).roundToInt(), (p.y).roundToInt()) }.size((wpx * 0.044f / density).dp).alpha(0.8f),
            ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
        }
        if (inGoal) Box(Modifier.offset { IntOffset((goal.x - wpx * 0.03f).roundToInt(), (goal.y - goalH * 0.75f).roundToInt()) }.size((wpx * 0.06f / density).dp)) {
            ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize())
        }
    }
}
