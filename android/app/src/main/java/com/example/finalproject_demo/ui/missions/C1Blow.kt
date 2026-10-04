package com.example.finalproject_demo.ui.missions

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.missions.BlowProp
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.Puff
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.rememberBlowLevel
import com.example.finalproject_demo.ui.rememberParticleField
import com.example.finalproject_demo.ui.touchOutline
import kotlin.math.abs
import kotlin.math.roundToInt

/** 후~ 세기가 이만큼 넘으면 「분다」로 친다 (A6 의 덤 불기와 같은 문턱 · 실기기로 다시 정한다 — 설계 §10) */
internal const val BLOW_ON = 0.22f

/** 소품 하나를 다 날리는 데 드는 양 — A6 의 「문지르기 3」과 같은 눈금 */
internal const val BLOW_FULL = 3f

/**
 * C1 불어서 날리기 — 미션 자리 1 (`docs/맞춤미션_설계.md` §4 · #101 첫 순서).
 *
 * 전에는 문지르기(A6)의 **덤**이었다(먼지 · 모래일 때만). 이제 아이가 「촛불 · 생일 · 민들레 · 먼지 · 바람」을 말한
 * 이야기에서 **불기가 미션 자체**다. 발달 목표는 호흡 조절.
 * 소품 그림은 `prop_candle` · `prop_candle_out` · `prop_dandelion`(10-04 · `tools/gen_room.py` 파이프라인, 같은 펠트 화풍).
 *
 * - **탭 길(원칙 6)** — 손가락으로 쓸어도 똑같이 날아간다. 마이크가 없거나 권한이 없어도 이 길로 끝난다.
 *   8초 진전이 없으면 손이 소품 위를 쓸어 보인다
 * - 마이크는 **이미 받은 권한만** 쓴다(`Blow.kt` — 미션 한가운데서 권한 창을 띄우지 않는다). 듣고 있으면 화면 아래에 표시
 * - 소리는 크기 한 숫자로만 읽고 버린다(`rememberBlowLevel`)
 */
@Composable
internal fun BlowMission(d: Director, done: Boolean, heroArt: Art, prop: BlowProp) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val life = remember { mutableStateListOf(0f, 0f, 0f) }
    val allOut = done || life.all { it >= BLOW_FULL }
    val puffs = rememberParticleField()
    val blow = rememberBlowLevel(!allOut)
    val micOn = remember {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val idle = rememberIdleHint(life.sum(), allOut)
    MissionDoneSignal(d, allOut, done, "미션1")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        // 날릴 것 셋 — 바닥 가운데에 나란히. 주인공은 왼쪽에서 본다
        val spots = listOf(0.44f to 0.62f, 0.58f to 0.66f, 0.72f to 0.62f).map { (x, y) -> Offset(x * wpx, y * hpx) }
        val size = 0.09f * wpx
        Stand(0.18f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        fun push(i: Int, amount: Float) {
            if (life[i] >= BLOW_FULL) return
            val before = life[i]
            life[i] = minOf(BLOW_FULL, life[i] + amount)
            val b = spots[i]
            if (before < BLOW_FULL && life[i] >= BLOW_FULL) {
                if (prop.flame) puffs.smoke(b.x, b.y - size * 0.6f, wpx * 0.05f) else puffs.steam(b.x, b.y, wpx * 0.06f, 8)
                Sfx.play(Sound.SPARKLE, 0L, view = view)
            }
        }

        // 후~ — 세기만큼 셋이 함께 날아간다. 촛불은 불꽃이 흔들리다 꺼진다
        LaunchedEffect(done, wpx, hpx) {
            if (done || motionFrozen) return@LaunchedEffect
            while (true) {
                withFrameNanos { }
                val strong = blow > BLOW_ON
                spots.forEachIndexed { i, b ->
                    val left = (1f - life[i] / BLOW_FULL).coerceIn(0f, 1f)
                    if (left <= 0f) return@forEachIndexed
                    if (strong) {
                        push(i, blow * 0.09f)
                        // 바람을 타고 오른쪽 위로 흩어진다
                        if (!prop.flame) puffs.water(b.x, b.y, blow * wpx * 0.02f, -blow * wpx * 0.006f, wpx * 0.03f)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(done) {
                    if (done) return@pointerInput
                    // 탭 길 — 쓸어도 · 톡 쳐도 된다
                    detectTapGestures { p -> spots.forEachIndexed { i, b -> if (near(p, b, size)) push(i, 1f) } }
                }
                .pointerInput(done) {
                    if (done) return@pointerInput
                    detectDragGestures { change, drag ->
                        val p = change.position
                        val amount = (abs(drag.x) + abs(drag.y)) / 200f
                        spots.forEachIndexed { i, b -> if (near(p, b, size)) push(i, amount) }
                        change.consume()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { puffs.tick; puffs.draw(this, setOf(Puff.FIRE, Puff.SMOKE)) }
            spots.forEachIndexed { i, b ->
                val t = if (done) 1f else (life[i] / BLOW_FULL).coerceIn(0f, 1f)
                // 날아가는 것은 오른쪽 위로 밀리며 옅어진다. 촛불은 제자리 — 불만 꺼진다
                val drift = if (prop.flame) 0f else t * size * 1.4f
                val fade = if (prop.flame) 1f else 1f - t
                if (fade > 0.02f) Box(
                    Modifier
                        .offset { IntOffset((b.x - size / 2 + drift).roundToInt(), (b.y - size / 2 - drift * 0.6f).roundToInt()) }
                        .size((size / density).dp)
                        .alpha(fade),
                ) {
                    // 촛불은 그림에 불꽃이 있다 — 다 불면 불꽃을 지운 그림으로
                    val art = if (t >= 1f && prop.gone != null) Art.Img(prop.gone, Art.Emoji("💨")) else Art.Img(prop.art, Art.Emoji(prop.emoji))
                    Box(Modifier.fillMaxSize().touchOutline(t < 1f)) { ArtView(art, Modifier.fillMaxSize()) }
                }
            }
            Canvas(Modifier.fillMaxSize()) { puffs.tick; puffs.draw(this, setOf(Puff.WATER, Puff.STEAM, Puff.SPARK)) }

            // 8초 힌트 — 남은 첫 소품 위를 손이 쓸어 보인다(탭 길이 있다는 것을 말 없이)
            if (idle && !allOut) {
                val sweep by rememberInfiniteTransition(label = "c1hint").animateFloat(
                    -1f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep",
                )
                val b0 = spots.firstOrNull { life[spots.indexOf(it)] < BLOW_FULL } ?: spots[0]
                Box(
                    Modifier
                        .offset { IntOffset((b0.x + sweep * wpx * 0.045f - wpx * 0.022f).roundToInt(), (b0.y - hpx * 0.02f).roundToInt()) }
                        .size((wpx * 0.044f / density).dp)
                        .alpha(0.75f),
                ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
            }
            MicListeningTag(micOn && !allOut, blow > BLOW_ON, "🎤 후~ 불어 봐! (손으로 쓸어도 돼)", "후~~~ 잘한다!")
        }
    }
}

private fun near(p: Offset, b: Offset, size: Float) = abs(p.x - b.x) < size * 0.8f && abs(p.y - b.y) < size * 0.9f
