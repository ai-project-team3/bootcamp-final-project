package com.example.finalproject_demo.ui.missions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.rotate
import com.example.finalproject_demo.ui.motionFrozen
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.Coral
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 펠트 원판 세 조각의 색 — 앱 공통 펠트 색(머스터드 · 청록 · 코랄) */
private val PIECE_COLORS = listOf(Color(0xFFF2B33D), Color(0xFF5FB8A8), Coral)

/**
 * E2 고쳐 주기 — 미션 자리 2 (`docs/맞춤미션_설계.md` §4 E2 · #101 다섯째 순서). 발달 목표는 공간 지각.
 *
 * 아이가 「부서졌어 · 망가졌어 · 고쳤어」라고 말한 이야기에서 나온다. 펠트 원판 하나가 세 조각인데 두 조각이 떨어져 나가 있다 —
 * 끌어다 빈자리 가까이 놓으면 딱 붙는다. **무엇이** 부서졌는지는 그리지 않는다: 아이가 말한 물건을 앱이 알 수 없고,
 * 실제 일에 말하지 않은 물건을 지어내면 안 된다(§3-8). 그래서 「조각」으로만 말한다.
 * - 3~4세 — 조각을 **톡 누르면** 제자리로 날아간다(끌기가 서툴러도 끝난다)
 * - 8초 진전이 없으면 손이 첫 조각을 빈자리로 옮겨 보인다
 */
@Composable
internal fun FixMission(d: Director, done: Boolean, heroArt: Art) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    // 떨어진 조각 둘(1 · 2번 부채꼴)의 지금 자리 — null 이면 처음 자리, 제자리에 붙으면 snapped
    val at = remember { mutableStateListOf<Offset?>(null, null) }
    val snapped = remember { mutableStateListOf(done, done) }
    val fixed = done || snapped.all { it }
    val idle = rememberIdleHint(snapped.count { it }.toFloat(), fixed)
    val hint = rememberMissionHint(d, snapped.count { it }.toFloat(), fixed, "E2")
    MissionDoneSignal(d, fixed, done, "미션2")
    // 다 고치면 고쳐진 물건이 한 번 흔들린다 — 「다시 붙었다」가 장면에 보이게 (#260 §6-3)
    val wobble = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(fixed) {
        if (!fixed || done || motionFrozen) return@LaunchedEffect
        for (v in listOf(9f, -7f, 4f, -2f, 0f)) wobble.animateTo(v, androidx.compose.animation.core.tween(120))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        // 책 화면의 위 1/4(도구 줄 · 안내)과 아래 문장 띠 사이에 다 들어가게 — 크면 위아래가 가렸다(10-05 실기기)
        val center = Offset(0.62f * wpx, 0.52f * hpx)
        val radius = 0.085f * wpx
        // 부채꼴 셋(각 120°) — 0번은 원판에 붙어 있고, 1 · 2번이 떨어져 있다
        val sweep = 120f
        val homes = listOf(center, center)                         // 붙으면 원판 가운데 기준으로 그린다
        val starts = listOf(Offset(0.36f * wpx, 0.58f * hpx), Offset(0.84f * wpx, 0.56f * hpx))
        Stand(0.14f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        fun pos(i: Int) = if (snapped[i]) homes[i] else at[i] ?: starts[i]
        fun snap(i: Int) {
            if (snapped[i]) return
            snapped[i] = true
            // 조각마다 톡 — 완료 반짝은 MissionDoneSignal 한 번만 (#260 효과음 규칙)
            Sfx.play(Sound.POP, minGapMs = 0L, view = view)
        }

        // 원판 — 붙어 있는 조각과 빈자리(점선)
        Canvas(Modifier.fillMaxSize()) {
          rotate(wobble.value, center) {
            val tl = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawArc(PIECE_COLORS[0], -90f, sweep, true, tl, box)
            listOf(1, 2).forEachIndexed { i, k ->
                if (!snapped[i]) drawArc(FeltWhite.copy(alpha = 0.8f), -90f + k * sweep, sweep, true, tl, box,
                    style = Stroke(radius * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.12f, radius * 0.09f))))
            }
          }
        }
        // 조각 둘 — 끌거나 톡 누른다
        listOf(1, 2).forEachIndexed { i, k ->
            val p = pos(i)
            Box(
                Modifier
                    .offset { IntOffset((p.x - radius).roundToInt(), (p.y - radius).roundToInt()) }
                    .size((radius * 2 / density).dp)
                    // 붙은 조각은 원판과 같이 흔들린다(상자 가운데 = 원판 가운데)
                    .rotate(if (snapped[i]) wobble.value else 0f)
                    .pointerInput(snapped[i]) {
                        if (snapped[i]) return@pointerInput
                        detectTapGestures(onTap = { snap(i) })          // 끌지 않고 톡 — 제자리로
                    }
                    .pointerInput(snapped[i]) {
                        if (snapped[i]) return@pointerInput
                        detectDragGestures(onDragEnd = {
                            val q = at[i] ?: starts[i]
                            if (hypot(q.x - homes[i].x, q.y - homes[i].y) < radius * 0.9f) snap(i)
                        }) { change, drag ->
                            at[i] = (at[i] ?: starts[i]) + drag
                            change.consume()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(PIECE_COLORS[k], -90f + k * sweep, sweep, true, Offset.Zero, this.size)
                    drawArc(FeltWhite.copy(alpha = 0.6f), -90f + k * sweep, sweep, true, Offset(size.width * 0.04f, size.height * 0.04f),
                        Size(size.width * 0.92f, size.height * 0.92f), style = Stroke(size.width * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))))
                }
            }
        }

        // 15초 흐릿한 예시 — 떨어진 조각 하나가 반투명으로 빈자리까지 미끄러졌다 사라진다(진짜 조각은 그대로)
        if (hint != null && !fixed) {
            val i = snapped.indexOfFirst { !it }.coerceAtLeast(0)
            val k = i + 1
            val g = ghostAlong(listOf(pos(i), homes[i]), hint)
            Ghost(g, radius * 2, hint) { Canvas(Modifier.fillMaxSize()) { drawArc(PIECE_COLORS[k], -90f + k * sweep, sweep, true, Offset.Zero, this.size) } }
            GhostHand(g, wpx * 0.06f, hint)
        } else if (idle && !fixed) {
            val slide by rememberInfiniteTransition(label = "e2hint").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "slide",
            )
            val i = snapped.indexOfFirst { !it }.coerceAtLeast(0)
            val from = pos(i)
            val h = from + (homes[i] - from) * slide
            Box(Modifier.offset { IntOffset((h.x - wpx * 0.01f).roundToInt(), h.y.roundToInt()) }.size((wpx * 0.044f / density).dp).alpha(0.8f)) {
                ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize())
            }
        }
        if (fixed) Box(Modifier.offset { IntOffset((center.x - wpx * 0.03f).roundToInt(), (center.y - radius * 1.7f).roundToInt()) }.size((wpx * 0.06f / density).dp)) {
            ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize())
        }
    }
}
