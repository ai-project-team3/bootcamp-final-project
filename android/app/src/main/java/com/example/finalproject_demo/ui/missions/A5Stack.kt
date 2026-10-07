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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.touchOutline
import kotlin.math.abs
import kotlin.math.roundToInt

/** 블록 셋의 펠트 색 — 머스터드 · 청록 · 코랄 */
private val BLOCK_COLORS = listOf(androidx.compose.ui.graphics.Color(0xFFF2B33D), androidx.compose.ui.graphics.Color(0xFF5FB8A8), com.example.finalproject_demo.ui.Coral)

/**
 * A5 쌓기 — 미션 자리 2 (`docs/맞춤미션_설계.md` §4 A5 · #101 다섯째 순서). 발달 목표는 균형 · 끈기.
 *
 * 아이가 「무너졌어 · 다시 쌓았어 · 탑」이라고 말한 이야기에서 나온다. 바닥에 흩어진 블록 셋을 하나씩 위로 끌어 올려
 * 탑 자리(점선) 위에 놓으면 차곡차곡 쌓인다 — **아래부터 한 칸씩**, 놓은 블록이 다음 블록의 받침이 된다.
 * 무너지지 않는다(실패 없음 · 원칙 3) — 탑 자리 근처에 놓기만 하면 맞는 칸에 앉는다.
 * - 3~4세 — 블록을 **톡 누르면** 탑 맨 위로 뛰어오른다
 * - 8초 진전이 없으면 손이 다음 블록을 탑 위로 옮겨 보인다
 */
@Composable
internal fun StackMission(d: Director, done: Boolean, heroArt: Art) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val at = remember { mutableStateListOf<Offset?>(null, null, null) }
    // 블록 i 가 탑의 몇 번째 칸에 앉았나(-1 = 아직 바닥)
    val level = remember { mutableStateListOf(if (done) 0 else -1, if (done) 1 else -1, if (done) 2 else -1) }
    val stacked = done || level.all { it >= 0 }
    val idle = rememberIdleHint(level.count { it >= 0 }.toFloat(), stacked)
    MissionDoneSignal(d, stacked, done, "미션2")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val b = 0.075f * wpx                                       // 블록 한 변
        // 아래 문장 띠에 가리지 않게 바닥을 조금 위로(10-05 실기기)
        val base = Offset(0.64f * wpx, 0.64f * hpx)                 // 탑 맨 아래 칸의 가운데
        val starts = listOf(Offset(0.34f * wpx, 0.64f * hpx), Offset(0.44f * wpx, 0.68f * hpx), Offset(0.84f * wpx, 0.66f * hpx))
        Stand(0.14f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        fun slot(n: Int) = Offset(base.x, base.y - n * b * 0.88f)      // 위 블록 돌기가 아래 블록에 살짝 묻힌다
        fun pos(i: Int) = if (level[i] >= 0) slot(level[i]) else at[i] ?: starts[i]
        fun stack(i: Int) {
            if (level[i] >= 0) return
            level[i] = level.count { it >= 0 }
            // 블록마다 톡 — 완료 반짝은 MissionDoneSignal 한 번만 (#260 효과음 규칙 · 마지막 블록에 두 번 울렸다)
            Sfx.play(Sound.POP, minGapMs = 0L, view = view)
        }

        // 15초 그대로면 오또가 첫 블록을 올려 준다 — 나머지 둘은 아이가 (#260)
        MissionHelp(d, level.count { it >= 0 }.toFloat(), stacked) {
            if (level.count { it >= 0 } == 0) level.indexOfFirst { it < 0 }.takeIf { it >= 0 }?.let { stack(it) }
        }

        // 탑 자리 — 아직 빈 칸을 점선으로(다음 칸이 어디인지 보이게)
        Canvas(Modifier.fillMaxSize()) {
            val next = level.count { it >= 0 }
            if (next < 3) {
                val s = slot(next)
                drawRoundRect(FeltWhite.copy(alpha = 0.85f), Offset(s.x - b / 2, s.y - b / 2), Size(b, b),
                    androidx.compose.ui.geometry.CornerRadius(b * 0.15f),
                    style = Stroke(b * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(b * 0.14f, b * 0.10f))))
            }
        }
        // 블록 셋 — 아래 칸부터 그리면 위 칸이 앞에 온다
        (0 until 3).sortedBy { if (level[it] >= 0) level[it] else 9 }.forEach { i ->
            val p = pos(i)
            Box(
                Modifier
                    .offset { IntOffset((p.x - b / 2).roundToInt(), (p.y - b / 2).roundToInt()) }
                    .size((b / density).dp)
                    .pointerInput(level[i] >= 0) {
                        if (level[i] >= 0) return@pointerInput
                        detectTapGestures(onTap = { stack(i) })          // 끌지 않고 톡 — 탑 맨 위로
                    }
                    .pointerInput(level[i] >= 0) {
                        if (level[i] >= 0) return@pointerInput
                        detectDragGestures(onDragEnd = {
                            val q = at[i] ?: starts[i]
                            val top = slot(level.count { it >= 0 })
                            if (abs(q.x - top.x) < b * 1.2f && abs(q.y - top.y) < b * 1.6f) stack(i)
                        }) { change, drag ->
                            at[i] = (at[i] ?: starts[i]) + drag
                            change.consume()
                        }
                    },
            ) {
                // 펠트 블록 — 그림(prop_block)은 선물 상자처럼 보이고 여백이 커서 탑이 떠 보였다. 빈틈없이 쌓이게 직접 그린다
                Box(Modifier.fillMaxSize().touchOutline(level[i] < 0)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = BLOCK_COLORS[i]
                        val stud = size.width * 0.13f
                        drawRoundRect(c, Offset(0f, stud * 0.7f), Size(size.width, size.height - stud * 0.7f), androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f))
                        listOf(0.3f, 0.7f).forEach { x -> drawCircle(c, stud, Offset(size.width * x, stud * 0.9f)) }
                        drawRoundRect(FeltWhite.copy(alpha = 0.6f), Offset(size.width * 0.07f, stud * 0.7f + size.width * 0.07f),
                            Size(size.width * 0.86f, size.height - stud * 0.7f - size.width * 0.14f), androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
                            style = Stroke(size.width * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    }
                }
            }
        }

        if (idle && !stacked) {
            val slide by rememberInfiniteTransition(label = "a5hint").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "slide",
            )
            val i = (0 until 3).first { level[it] < 0 }
            val from = pos(i); val to = slot(level.count { it >= 0 })
            val h = from + (to - from) * slide
            Box(Modifier.offset { IntOffset((h.x - wpx * 0.01f).roundToInt(), h.y.roundToInt()) }.size((wpx * 0.044f / density).dp).alpha(0.8f)) {
                ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize())
            }
        }
        // 반짝이는 탑 꼭대기 오른쪽 옆 — 위에 두면 책 도구 줄에 걸쳤다(10-05 실기기)
        if (stacked) Box(Modifier.offset { IntOffset((base.x + b * 0.7f).roundToInt(), (slot(2).y - b).roundToInt()) }.size((wpx * 0.06f / density).dp)) {
            ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize())
        }
    }
}
