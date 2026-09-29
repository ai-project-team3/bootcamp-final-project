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
 * 핫스팟을 **어느 층에** 그리나 (09-27 · `docs/무대_배치_규칙.md` §2 「그리는 순서」).
 *
 * 장소 장면은 배경 → 인물 → 앞 레이어 → **핫스팟** 순서로 그린다. 그런데 핫스팟에는 성격이 다른 둘이 섞여 있다.
 *  - [Pieces] 배경에서 떼어 낸 조각이 통 튀는 것 — **배경의 일부**다. 인물 앞에 오면 인물을 덮는다
 *  - [Marks]  테두리 · 반짝임 · 누르면 뜨는 글자 · 누르는 자리 — **상호작용 층**이다. 인물 뒤에 오면 가려서 못 누른다
 * 그래서 둘로 나눠 [Pieces] 는 인물 뒤, [Marks] 는 맨 위에 그린다. 한 번에 다 그리려면 [All] (책 쪽).
 */
enum class HotspotPart { All, Pieces, Marks }

/** 한 자리의 움직임 — 두 층이 **같은 것**을 봐야 누르면 뒤의 조각이 튄다 */
class HotspotSpot {
    val bounce = Animatable(1f)
    val rise = Animatable(1f)
    var popKey by mutableIntStateOf(0)
    var popText by mutableStateOf("")
}

/** 배경 하나의 핫스팟 상태. 두 층으로 나눠 그릴 때 한 번 만들어 둘 다에 넘긴다 */
class HotspotState internal constructor(n: Int) {
    val spots: List<HotspotSpot> = List(n) { HotspotSpot() }
}

@Composable
fun rememberHotspotState(bgName: String): HotspotState {
    val n = HOTSPOTS[bgName].orEmpty().size
    return remember(bgName, n) { HotspotState(n) }
}

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
 * @param introducing **누를 수 있는 자리를 전부** 반짝여 소개하는 중인가 (책을 편 처음 몇 초)
 * @param glowMentioned 소개가 아닐 때 **아이가 말한 자리**를 계속 반짝일 것인가
 */
@Composable
fun HotspotLayer(
    bgName: String,
    glow: Set<String>,
    pulse: Int,
    quake: Boolean = false,
    /**
     * **책을 편 처음 몇 초** 동안만 켠다. "여기 눌러 볼 수 있어" 를 알려 주는 안내다.
     *
     * ⚠️ 기본값이 `false` 인 이유 — 9/22에 기본값을 `true` 로 두었다가 **질문 화면(S3)에서도
     * 전부 반짝였다.** 안내는 책에서만 한다. 부르는 쪽이 일부러 켜야 켜진다.
     */
    introducing: Boolean = false,
    /**
     * 소개가 아닐 때의 동작. 질문 화면은 아이가 말한 것을 되비쳐 주므로 `true`,
     * 책은 소개가 끝나면 조용해져야 하므로 `false` 다 — 계속 반짝이면 그림을 읽는 것을 방해한다.
     */
    glowMentioned: Boolean = true,
    /**
     * 소개를 **다 보여 줬을 때** 부른다 (9/22).
     *
     * ⚠️ 시간을 여기서 재는 이유 — 부르는 쪽에서 재면 **원이 화면에 뜨기도 전에 시간이 간다.**
     * 책 표지에는 배경 자리가 없어서, 아이가 표지를 몇 초 보는 사이 안내가 끝나 버렸다.
     * 여기서 재면 원이 실제로 그려지는 순간부터 센다.
     */
    onIntroShown: () -> Unit = {},
    text: ((Hotspot) -> String)? = null,
    onTap: (Hotspot) -> Unit = {},
    /** 두 층으로 나눠 그릴 때 둘이 같이 쓰는 상태 ([rememberHotspotState]). 한 층이면 넘기지 않아도 된다 */
    state: HotspotState = rememberHotspotState(bgName),
    /** 어느 층인가 — [HotspotPart] */
    part: HotspotPart = HotspotPart.All,
) {
    val spots = HOTSPOTS[bgName].orEmpty()
    if (spots.isEmpty()) return
    // 원이 그려지는 이 자리에서부터 소개 시간을 센다
    val marks = part != HotspotPart.Pieces
    val pieces = part != HotspotPart.Marks
    if (marks) LaunchedEffect(introducing) {
        if (introducing) {
            delay(HOTSPOT_INTRO_MS)
            onIntroShown()
        }
    }
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
                val st = state.spots[i]
                val bounce = st.bounce
                val rise = st.rise
                // 아이가 말한 자리인가 — 쪽이 바뀔 때 통 튀는 것은 이것이 정한다 (소개가 끝나도 그대로)
                val mentioned = sp.key in glow
                // 소개 중이면 **누를 수 있는 자리를 전부** 보여 주고, 아니면 아이가 말한 자리만 (9/22).
                //
                // 전에는 소개할 때도 `glow`(아이가 말한 것)에 든 자리만 켰다. 그런데 일기·협업의 `glow` 는
                // 아이 말과 겹치는 낱말만 담아서 **비어 있는 날이 많았고, 안내가 아예 안 떴다.**
                val lit = SHOW_TOUCH_MARKS && (if (introducing) true else (mentioned && glowMentioned))
                if (marks) LaunchedEffect(pulse, mentioned) {
                    if (mentioned && pulse > 0) {
                        delay(i * 90L)
                        bounce.snapTo(1f)
                        bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f), initialVelocity = 2.2f)
                    }
                }
                if (marks) LaunchedEffect(st.popKey) {
                    if (st.popKey == 0) return@LaunchedEffect
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
                        // 누르는 **그 순간** 납작해졌다 손을 떼면 통 튀어 오른다 (9/23).
                        // 전에는 누르고 난 뒤에야 튀어서, 만지는 동안은 아무 반응이 없었다
                        .then(
                            if (marks) Modifier.pressPop {
                                st.popText = text?.invoke(sp) ?: sp.tap
                                st.popKey++
                                onTap(sp)
                            } else Modifier,
                        )
                ) {
                    // 만질 수 있다는 표시 — 반짝이지 않을 때도 늘 보인다 (09-27 · 멘토 요청 · `Interactive.kt`)
                    if (marks) TouchRing(Modifier.matchParentSize())
                    // 반짝이는 둘레 (말한 것만 계속 · 누른 것은 잠깐)
                    val ring = if (lit) shine else if (st.popKey > 0 && rise.value < 1f) (1f - rise.value) * 0.8f else 0f
                    if (marks && ring > 0f) {
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
                    if (pieces && id != 0 && (quake || sc2 != 1f || bounce.isRunning)) {
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
                    if (marks && st.popKey > 0 && rise.value < 1f) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .wrapContentSize(unbounded = true)
                                .offset { IntOffset(0, (-20 - rise.value * 40).roundToInt()) }
                                .graphicsLayer { alpha = 1f - rise.value * 0.6f }
                                .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(st.popText, fontSize = 16.sp, color = FeltCoral) }
                    }
                }
            }
        }
    }
}
