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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.finalproject_demo.ui.Puff
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.rememberParticleField
import com.example.finalproject_demo.ui.touchOutline
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 잠그려면 손잡이를 이만큼 돌린다 — 두 바퀴 */
internal const val TURN_FULL = (4 * PI).toFloat()

/** 한 번 톡 누를 때 도는 양 — 다섯 번이면 잠긴다(원 그리기가 어려운 3~4세 · 설계 §10) */
internal const val TURN_TAP = TURN_FULL / 5f

/**
 * A4 돌려 잠그기 — 미션 자리 2 (`docs/맞춤미션_설계.md` §4 · #101 셋째 순서). 발달 목표는 소근육(원 그리기).
 *
 * 아이가 「물이 샜어 · 꽉 잠갔어」라고 말한 이야기에서 나온다. 수도꼭지 위 빨간 손잡이 둘레를 손가락으로 **빙글빙글** 돌리면
 * 손잡이가 따라 돌고, 두 바퀴면 물이 멈춘다. 어느 쪽으로 돌려도 된다(방향을 가르치는 미션이 아니다).
 * 원을 못 그리는 아이도 끝나게 손잡이를 **톡톡 눌러도** 조금씩 돈다. 8초 진전이 없으면 손이 원을 그려 보인다
 */
@Composable
internal fun TurnMission(d: Director, done: Boolean, heroArt: Art) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    var turned by remember { mutableFloatStateOf(if (done) TURN_FULL else 0f) }
    // 다섯 번 더한 값이 소수점 오차로 TURN_FULL 에 살짝 못 미친다 — 조금 여유를 둔다
    val closed = done || turned >= TURN_FULL - 0.01f
    val puffs = rememberParticleField()
    val idle = rememberIdleHint((turned / 0.5f).roundToInt().toFloat(), closed)
    MissionDoneSignal(d, closed, done, "미션2")
    LaunchedEffect(closed) { if (closed && !done) Sfx.play(Sound.SPARKLE, 0L, view = view) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val faucet = 0.17f * wpx
        val tap = Offset(0.58f * wpx, 0.44f * hpx)                 // 수도꼭지 그림 가운데
        // 그림(prop_faucet — 레버를 잘라 낸 수도꼭지)의 노란 받침 위에 손잡이, 오른쪽 아래 끝에서 물이 나온다
        val knob = Offset(tap.x - faucet * 0.26f, tap.y - faucet * 0.62f)
        val knobR = faucet * 0.30f
        val spout = Offset(tap.x + faucet * 0.38f, tap.y + faucet * 0.17f)
        Stand(0.18f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        // 열려 있는 동안 물이 똑똑 — 많이 돌릴수록 줄어든다
        LaunchedEffect(done, wpx) {
            if (done || motionFrozen) return@LaunchedEffect
            while (turned < TURN_FULL) {
                withFrameNanos { }
                val open = 1f - turned / TURN_FULL
                if (Math.random() < 0.35 * open) puffs.water(spout.x, spout.y, 0f, wpx * 0.006f, wpx * 0.02f)
            }
        }

        fun add(a: Float) { if (!closed) turned = minOf(TURN_FULL, turned + a) }

        Box(Modifier.offset { IntOffset((tap.x - faucet / 2).roundToInt(), (tap.y - faucet / 2).roundToInt()) }.size((faucet / density).dp)) {
            ArtView(Art.Img("prop_faucet", Art.Emoji("🚰")), Modifier.fillMaxSize())
        }
        // 물줄기 — 잠글수록 가늘어지다 멈춘다
        Canvas(Modifier.fillMaxSize()) {
            puffs.tick
            val open = if (closed) 0f else 1f - turned / TURN_FULL
            if (open > 0f) drawLine(Color(0xFF8CC8F0).copy(alpha = 0.85f), spout, spout.copy(y = spout.y + hpx * 0.18f), strokeWidth = 18f * open + 4f, cap = StrokeCap.Round)
            puffs.draw(this, setOf(Puff.WATER, Puff.STEAM, Puff.SPARK))
        }
        // 손잡이 — 빨간 펠트 바퀴(살 넷). 돌린 만큼 돈다
        Box(
            Modifier
                .offset { IntOffset((knob.x - knobR * 1.6f).roundToInt(), (knob.y - knobR * 1.6f).roundToInt()) }
                .size((knobR * 3.2f / density).dp)
                .pointerInput(closed) {
                    if (closed) return@pointerInput
                    detectTapGestures(onPress = { add(TURN_TAP) })        // 누르는 순간 한 칸 — 뗄 때까지 기다리지 않는다
                }
                .pointerInput(closed) {
                    if (closed) return@pointerInput
                    val c = Offset(size.width / 2f, size.height / 2f)
                    var last: Float? = null
                    detectDragGestures(onDragEnd = { last = null }, onDragCancel = { last = null }) { change, _ ->
                        val p = change.position - c
                        val a = atan2(p.y, p.x)
                        last?.let { l ->
                            var da = a - l
                            if (da > PI) da -= (2 * PI).toFloat()
                            if (da < -PI) da += (2 * PI).toFloat()
                            add(abs(da))
                        }
                        last = a
                        change.consume()
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().touchOutline(!closed)) {
                Canvas(Modifier.fillMaxSize().rotate((turned * 180f / PI).toFloat())) {
                    // 두툼한 펠트 바퀴 — 테 · 살 셋 · 가운데 단추(과녁처럼 보이지 않게 살을 셋으로)
                    val r = this.size.minDimension / 2f * 0.62f
                    val o = center
                    drawCircle(Coral, r, o, style = Stroke(r * 0.42f))
                    drawCircle(FeltWhite.copy(alpha = 0.55f), r, o, style = Stroke(r * 0.05f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.10f))))
                    repeat(3) { k ->
                        val t = k * 2 * PI / 3
                        drawLine(Coral, o, Offset(o.x + (r * cos(t)).toFloat(), o.y + (r * sin(t)).toFloat()), strokeWidth = r * 0.34f, cap = StrokeCap.Round)
                    }
                    drawCircle(Coral, r * 0.34f, o)
                    drawCircle(FeltWhite, r * 0.14f, o)
                }
            }
        }

        // 8초 힌트 — 손이 손잡이 둘레를 한 바퀴 돈다
        if (idle && !closed) {
            val spin by rememberInfiniteTransition(label = "a4hint").animateFloat(
                0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "spin",
            )
            val hx = knob.x + knobR * 1.1f * cos(spin); val hy = knob.y + knobR * 1.1f * sin(spin)
            Box(
                Modifier.offset { IntOffset((hx - wpx * 0.02f).roundToInt(), (hy - wpx * 0.02f).roundToInt()) }.size((wpx * 0.044f / density).dp).alpha(0.8f),
            ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
        }
        if (closed) Box(Modifier.offset { IntOffset((knob.x - wpx * 0.03f).roundToInt(), (knob.y - knobR * 2.6f).roundToInt()) }.size((wpx * 0.06f / density).dp)) {
            ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize())
        }
    }
}

