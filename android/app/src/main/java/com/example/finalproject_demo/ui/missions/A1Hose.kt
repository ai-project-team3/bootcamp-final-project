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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.Puff
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Stand
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.rememberParticleField
import com.example.finalproject_demo.ui.touchOutline
import kotlin.math.abs
import kotlin.math.roundToInt

/** 불 하나를 다 끄는 데 드는 양 */
internal const val HOSE_FULL = 1f

/** 한 번 톡 누를 때 꺼지는 양 — 세 번이면 하나가 꺼진다(겨누고 있기 어려운 3~4세 · 설계 §10) */
internal const val HOSE_TAP = 0.34f

/**
 * A1 물대포로 끄기 — 미션 자리 2 (`docs/맞춤미션_설계.md` §4 · #101 셋째 순서). 발달 목표는 눈-손 협응(겨누고 버티기).
 *
 * 아이가 「불을 껐어 · 물을 뿌렸어」라고 말한 이야기에서 나온다. 불 셋을 손가락으로 **꾹 누르고 있으면** 제자리에 선
 * 호스에서 물줄기가 그 자리로 날아가 꺼진다(호스는 움직이지 않는다 — 노즐이 손가락을 따라오면 물줄기가 짧아졌다 · `A6Rub.kt` 9/23).
 * 터치 미션이라 따로 탭 길이 필요 없지만, 오래 누르기 힘든 아이를 위해 **톡톡 눌러도** 꺼진다. 8초 진전이 없으면 손이 불을 눌러 보인다
 */
@Composable
internal fun HoseMission(d: Director, done: Boolean, heroArt: Art) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val life = remember { mutableStateListOf(0f, 0f, 0f) }
    val allOut = done || life.all { it >= HOSE_FULL }
    val puffs = rememberParticleField()
    var finger by remember { mutableStateOf<Offset?>(null) }
    val idle = rememberIdleHint(life.sum(), allOut)
    MissionDoneSignal(d, allOut, done, "미션2")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val fires = listOf(0.46f to 0.56f, 0.61f to 0.62f, 0.76f to 0.55f).map { (x, y) -> Offset(x * wpx, y * hpx) }
        val size = 0.085f * wpx
        val nozzle = Offset(0.30f * wpx, hpx - 108f * density)       // 주인공 오른쪽 발치 — 주인공에 겹치지 않게
        Stand(0.16f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }

        fun hit(p: Offset) = fires.indexOfFirst { abs(p.x - it.x) < size * 0.8f && abs(p.y - it.y) < size }
        fun douse(i: Int, amount: Float) {
            if (i < 0 || life[i] >= HOSE_FULL) return
            val before = life[i]
            life[i] = minOf(HOSE_FULL, life[i] + amount)
            if (before < HOSE_FULL && life[i] >= HOSE_FULL) {
                puffs.steam(fires[i].x, fires[i].y, wpx * 0.075f, 10)
                // 불 하나가 꺼질 때는 치익 — 완료 반짝은 MissionDoneSignal 한 번만 (#260 효과음 규칙)
                Sfx.play(Sound.HISS, 0L, view = view)
            }
        }

        // 15초 그대로면 오또가 반쯤 꺼 준다 — 앞에서부터 불 하나 반 (#260)
        MissionHelp(d, life.sum(), allOut) {
            val want = HOSE_FULL * life.size / 2f
            for (i in life.indices) {
                val left = want - life.sum()
                if (left <= 0f) break
                douse(i, minOf(HOSE_FULL - life[i], left))
            }
        }

        // 누르고 있는 동안 물줄기 — 겨눈 불이 조금씩 꺼진다(1초 남짓)
        LaunchedEffect(done, wpx, hpx) {
            if (done || motionFrozen) return@LaunchedEffect
            while (true) {
                withFrameNanos { }
                val fp = finger
                if (fp != null) {
                    puffs.jet(nozzle.x, nozzle.y, fp.x, fp.y, wpx * 0.055f)
                    Sfx.play(Sound.HISS, minGapMs = 260L)
                    douse(hit(fp), 0.014f)
                }
                fires.forEachIndexed { i, f ->
                    val left = (1f - life[i] / HOSE_FULL).coerceIn(0f, 1f)
                    if (left > 0f) puffs.flame(f.x, f.y, wpx * 0.032f, left) else if (Math.random() < 0.04) puffs.smoke(f.x, f.y - wpx * 0.01f, wpx * 0.05f)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(done) {
                    if (done) return@pointerInput
                    // 누르는 동안 물줄기 — 누르자마자 한 번 꺼지고(톡), 떼지 않으면 계속 뿜는다
                    detectTapGestures(onPress = { p ->
                        finger = p
                        douse(hit(p), HOSE_TAP)
                        tryAwaitRelease()
                        finger = null
                    })
                }
                .pointerInput(done) {
                    if (done) return@pointerInput
                    // 누른 채로 끌면 겨누는 곳이 따라간다
                    detectDragGestures(onDragEnd = { finger = null }, onDragCancel = { finger = null }) { change, _ ->
                        finger = change.position
                        change.consume()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { puffs.tick; puffs.draw(this, setOf(Puff.FIRE, Puff.SMOKE)) }
            fires.forEachIndexed { i, f ->
                val left = if (done) 0f else (1f - life[i] / HOSE_FULL).coerceIn(0f, 1f)
                if (left > 0.02f) Box(
                    Modifier
                        .offset { IntOffset((f.x - size / 2).roundToInt(), (f.y - size / 2).roundToInt()) }
                        .size((size / density).dp)
                        .alpha(0.35f + 0.65f * left),
                ) {
                    Box(Modifier.fillMaxSize().touchOutline()) { ArtView(Art.Img("prop_fire", Art.Emoji("🔥")), Modifier.fillMaxSize()) }
                }
            }
            Canvas(Modifier.fillMaxSize()) { puffs.tick; puffs.draw(this, setOf(Puff.WATER, Puff.STEAM, Puff.SPARK)) }
            // 호스는 제자리에 — 물줄기는 여기서 손가락까지 날아간다
            if (!allOut) Box(
                Modifier.offset { IntOffset((nozzle.x - 38 * density).roundToInt(), (nozzle.y - 38 * density).roundToInt()) }.size(76.dp),
            ) { ArtView(Art.Img("prop_hose", Art.Emoji("🚿")), Modifier.fillMaxSize()) }

            if (idle && !allOut) {
                val press by rememberInfiniteTransition(label = "a1hint").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "press",
                )
                val f0 = fires.firstOrNull { life[fires.indexOf(it)] < HOSE_FULL } ?: fires[0]
                Box(
                    Modifier
                        .offset { IntOffset((f0.x - wpx * 0.01f).roundToInt(), (f0.y + press * hpx * 0.03f).roundToInt()) }
                        .size((wpx * 0.044f / density).dp)
                        .alpha(0.8f),
                ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
            }
        }
    }
}
