package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.HOTSPOTS
import com.example.finalproject_demo.demo.Hotspot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val BG_W = 1344f
private const val BG_H = 768f

/**
 * 배경 그림 **안에 이미 그려진 것**을 살아 움직이게 하는 층 (9/17).
 * 새 그림을 얹지 않는다 — 그 자리의 배경을 동그랗게 떼어 낸 조각을 통 튀게 하고, 둘레를 반짝이게 한다.
 *
 * ContentScale.Crop 으로 깔린 배경(1344×768)과 같은 계산으로 자리를 맞춘다:
 *   scale = max(W/1344, H/768) · 잘린 만큼(off) 빼기.
 *
 * @param glow   반짝이게 할 key (아이가 말한 것)
 * @param pulse  바뀔 때마다 glow 것들이 한 번 통 튄다
 * @param quake  흔들리는 중 (사건 장면)
 * @param text   누르면 뜨는 글자 — null 이면 hotspot.tap
 */
@Composable
fun HotspotLayer(
    bgName: String,
    glow: Set<String>,
    pulse: Int,
    quake: Boolean = false,
    text: ((Hotspot) -> String)? = null,
    onTap: (Hotspot) -> Unit = {},
) {
    val spots = HOTSPOTS[bgName].orEmpty()
    if (spots.isEmpty()) return
    val id = assetId(bgName)
    val density = LocalDensity.current.density
    val inf = rememberInfiniteTransition(label = "hot")
    val shine by inf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shine")
    val jitter by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(110), RepeatMode.Reverse), label = "jit")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val sc = max(w / BG_W, h / BG_H)
        val offX = (BG_W * sc - w) / 2f
        val offY = (BG_H * sc - h) / 2f
        spots.forEachIndexed { i, sp ->
            val cx = sp.cx * BG_W * sc - offX
            val cy = sp.cy * BG_H * sc - offY
            val r = sp.r * BG_W * sc
            if (cy + r > 0 && cy - r < h) key(i, bgName) {
                val bounce = remember { Animatable(1f) }
                var popKey by remember { mutableIntStateOf(0) }
                var popText by remember { mutableStateOf("") }
                val rise = remember { Animatable(1f) }
                val lit = sp.key in glow
                LaunchedEffect(pulse, lit) {
                    if (lit && pulse > 0) {
                        delay(i * 90L)
                        bounce.snapTo(1f)
                        bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f), initialVelocity = 2.2f)
                    }
                }
                LaunchedEffect(popKey) {
                    if (popKey == 0) return@LaunchedEffect
                    launch { rise.snapTo(0f); rise.animateTo(1f, tween(1100)) }
                    bounce.snapTo(1f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.25f, stiffness = 520f), initialVelocity = 3f)
                }
                val sizeDp = (2 * r / density).dp
                val shakeX = if (quake) jitter * ((i % 3) - 1).toFloat() else 0f
                Box(
                    Modifier
                        .offset { IntOffset((cx - r + shakeX).roundToInt(), (cy - r).roundToInt()) }
                        .size(sizeDp)
                        .noRippleClickable {
                            popText = text?.invoke(sp) ?: sp.tap
                            popKey++
                            onTap(sp)
                        }
                ) {
                    // 반짝이는 둘레 (말한 것만 계속 · 누른 것은 잠깐)
                    val ring = if (lit) shine else if (popKey > 0 && rise.value < 1f) (1f - rise.value) * 0.8f else 0f
                    if (ring > 0f) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .requiredSize(sizeDp * 1.45f)
                                .graphicsLayer { alpha = ring }
                                .background(
                                    Brush.radialGradient(
                                        0f to Color(0x33FFF6C8), 0.52f to Color(0x22FFF6C8),
                                        0.66f to Color(0xFFFFE066), 0.78f to Color(0x99FFD23F), 1f to Color.Transparent,
                                    ),
                                    CircleShape,
                                )
                        )
                        if (lit) Text(
                            "✨", fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = ring; scaleX = 0.8f + ring * 0.4f; scaleY = 0.8f + ring * 0.4f },
                        )
                    }
                    // 배경의 그 자리를 떼어 낸 조각 — 통 튀거나 흔들린다 (그때만 보인다 · 가만히 있을 때는 배경 그대로)
                    val sc2 = bounce.value
                    if (id != 0 && (quake || sc2 != 1f || bounce.isRunning)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = sc2; scaleY = sc2; translationY = (1f - sc2) * r * 0.6f }
                                .clip(CircleShape)
                        ) {
                            Image(
                                painterResource(id), null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .offset { IntOffset((-offX - (cx - r)).roundToInt(), (-offY - (cy - r)).roundToInt()) }
                                    .requiredSize((BG_W * sc / density).dp, (BG_H * sc / density).dp),
                            )
                        }
                    }
                    if (popKey > 0 && rise.value < 1f) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .wrapContentSize(unbounded = true)
                                .offset { IntOffset(0, (-20 - rise.value * 40).roundToInt()) }
                                .graphicsLayer { alpha = 1f - rise.value * 0.6f }
                                .shadow(4.dp, RoundedCornerShape(999.dp))
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) { Text(popText, fontSize = 15.sp, color = Coral, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
