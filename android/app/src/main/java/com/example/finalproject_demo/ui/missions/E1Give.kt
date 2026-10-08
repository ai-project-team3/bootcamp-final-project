package com.example.finalproject_demo.ui.missions

import androidx.compose.animation.core.Animatable
import com.example.finalproject_demo.demo.heroImageName
import com.example.finalproject_demo.demo.storyHeroArt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.pageKind
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.SavedStoryBook
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.missions.MissionId
import com.example.finalproject_demo.demo.missions.missions
import com.example.finalproject_demo.demo.mission2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.*

/** E1 건네주기 — mission slot 2 (moved from ui/Book.kt as is, 10-03 · 맞춤미션 설계 §7-1 1-a) */
/**
 * 5쪽 미션 2 — 끌어다 놓기 (보통 · 목표 영역 넓게). 장면 10에서 아이가 말한 것을 친구에게 건넨다.
 * 친구에게 놓으면 하트가 퐁퐁, 주인공에게 놓으면 장난 반응. 미션 1에서 도와줬으면 톡 누르면 날아간다.
 */
@Composable
internal fun GiveMission(d: Director, done: Boolean, heroArt: Art, tool: String) {
    val view = LocalView.current   // 효과음과 같이 진동 (Sfx · 09-25)
    val s = d.s
    val m = s.mission2()
    val easy = s.m1Result == "helped"
    val scope = rememberCoroutineScope()
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var given by remember { mutableStateOf(done) }
    var gag by remember { mutableStateOf<String?>(null) }
    // 손가락이 마지막으로 움직인 속도 — 손을 뗄 때 물건이 그 기세로 계속 간다 (9/23)
    var fling by remember { mutableStateOf(Offset.Zero) }
    // 받는 순간 친구가 폴짝 뛰고 반짝임이 터진다 (미션 구상 E1 반응 보강 · 9/23)
    val puffs = rememberParticleField()
    val hop = remember { Animatable(0f) }
    // 8초 동안 안 건네면 마스코트가 첫 걸음을 보여 준다
    var showHint by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "give")
    val beat by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "beat")
    val rise by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "rise")
    // 다른 미션과 같은 신호 — 반짝 한 번 · 받는 장면(폴짝 · 하트)을 보인 뒤 보낸다 (#260 · 전에는 여기서 따로 1.2초)
    MissionDoneSignal(d, given, done, "미션2")
    LaunchedEffect(given) {
        showHint = false
        if (given || motionFrozen) return@LaunchedEffect
        delay(8_000)
        showHint = true
    }
    LaunchedEffect(gag) { if (gag != null) { delay(1500); gag = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val dens = LocalDensity.current
        val itemSize: Dp = 88.dp
        val itemPx = with(dens) { itemSize.toPx() }
        // 건넬 물건(별 · 음표 …)은 책 안내 말풍선 아래 띠에서 시작한다 — 0.22 는 말풍선 밑에 깔렸다
        // (#154 · A4 · C3 · E2 · A5 와 같은 띠 `93bd340`). 위 1/4 아래 · 아래 문장 띠 위
        val startX = wpx * 0.30f; val startY = hpx * 0.42f
        // 친구 = 목표 (넓게 판정)
        val fx = 0.60f; val fy = 0.26f; val fw = 0.19f
        val fL = fx * wpx; val fT = fy * hpx; val fS = fw * wpx
        val fCx = fL + fS / 2; val fCy = fT + fS / 2
        val hL = 0.10f * wpx; val hT = 0.36f * hpx; val hW = 0.11f * wpx

        // ⚠️ 받는 쪽(친구)는 **그대로 둔다** — 그 자리·크기가 물건을 놓았는지 판정과 묶여 있어
        //    옮기면 미션이 안 끝난다 (fL · fT · fS)
        Stand(0.16f, 0.11f) { Tappable({ reactionFor(s, tool, "hero") }, Modifier.fillMaxSize()) { ArtView(heroArt, Modifier.fillMaxSize()) } }
        // 일기·협업에서 받는 쪽은 **또래 아이**다. 동화 모드의 공룡·외계인과 같은 크기로 그리면
        // 주인공보다 두 배 커서 어른처럼 보였다 (9/22). 가운데를 축으로 줄이므로 놓는 자리는 그대로다
        val targetScale = if (s.isDiary) 0.62f else 1f
        // 물건이 가까워질수록 받는 쪽이 그쪽으로 몸을 기울인다 — 「받고 싶어」가 보이게 (#260 §6-3 · 그림 없이 기울기로)
        val itemCx = startX + ox.value + itemPx / 2; val itemCy = startY + oy.value + itemPx / 2
        val reach = if (given) 0f else (1f - hypot(fCx - itemCx, fCy - itemCy) / hypot(fCx - startX - itemPx / 2, fCy - startY - itemPx / 2)).coerceIn(0f, 1f)
        val hint = rememberMissionHint(d, (reach * 10).roundToInt().toFloat(), given, "E1")
        Layer(
            fx, fy, fw, 1f,
            Modifier
                .offset { IntOffset((-reach * fS * 0.08f).roundToInt(), (-hop.value * hpx * 0.045f).roundToInt()) }
                .rotate(-8f * reach)
                .scale((if (given) beat else 1f) * targetScale),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 미션 2는 건넬 상대가 있어야 한다. 아이가 그린 것 → 아이가 말한 사람 →
                // 둘 다 없으면 마스코트가 받는다. **없는 친구를 앱이 만들어 내지 않는다** (일기 §3-2)
                val target = s.friendOrPartnerArt ?: Art.Mascot
                // no white frame around who receives it (#259) — it read as a cut-out card over the stage; the drag target is still the whole layer
                Tappable({ reactionFor(s, tool, "friend") }, Modifier.fillMaxSize()) { ArtView(target, Modifier.fillMaxSize()) }
            }
        }
        if (given) {
            // 하트가 퐁퐁 올라간다
            repeat(3) { i ->
                val t = (rise + i / 3f) % 1f
                Box(
                    Modifier
                        .offset { IntOffset((fCx - 60 + (i - 1) * 60).roundToInt(), (fT - 20 - t * 90).roundToInt()) }
                        .size(44.dp).alpha(1f - t)
                ) { ArtView(Art.Img("prop_heart", Art.Emoji("💗")), Modifier.fillMaxSize()) }
            }
        }
        // 받는 순간 — 친구가 폴짝 뛰고 반짝임이 터진다.
        // 전에는 하트만 올라가서 "받았다"는 느낌이 약했다 (미션 구상 E1)
        LaunchedEffect(given) {
            // 검사에서는 멈춘다 — 안 그러면 찍는 순간 친구가 뛰어오른 중이라 기준 그림이 매번 달라진다
            if (!given || motionFrozen) return@LaunchedEffect
            // 받을 때 툭 — 완료 반짝은 MissionDoneSignal 한 번만 (#260 효과음 규칙)
            Sfx.play(Sound.THUD, 0L, view = view)
            puffs.burst(fCx, fCy, wpx * 0.03f, 22)
            hop.snapTo(0f)
            hop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f), initialVelocity = 7f)
        }
        Canvas(Modifier.fillMaxSize()) {
            puffs.tick
            puffs.draw(this)
        }

        // 건넬 물건
        val atX = if (given) fCx - itemPx * 0.35f else startX + ox.value
        val atY = if (given) fCy + fS * 0.2f - itemPx * 0.35f else startY + oy.value
        Box(
            Modifier
                .offset { IntOffset(atX.roundToInt(), atY.roundToInt()) }
                .size(if (given) itemSize * 0.7f else itemSize)
                .pointerInput(easy, given) {
                    if (given) return@pointerInput
                    if (easy) {
                        detectTapGestures {
                            scope.launch { ox.animateTo(fCx - itemPx / 2 - startX, tween(700)) }
                            scope.launch { oy.animateTo(fCy - itemPx / 2 - startY, tween(700)); given = true }
                        }
                    } else {
                        detectDragGestures(onDragEnd = {
                            val cx = startX + ox.value + itemPx / 2
                            val cy = startY + oy.value + itemPx / 2
                            val pad = 60f
                            if (cx > fL - pad && cx < fL + fS + pad && cy > fT - pad && cy < fT + fS + pad) {
                                given = true
                            } else {
                                if (cx > hL - pad && cx < hL + hW + pad && cy > hT - pad) {
                                    gag = "헤헤, ${s.friendName}한테 줘야지!"
                                    d.send(Reply.Tapped("gag", "장난"))
                                }
                                // 손을 뗀 기세로 잠깐 더 날아갔다 스프링으로 제자리에 안착한다.
                                // 딱 멈춰 되돌아가면 죽은 물건 같고, 기세가 이어지면 손에 잡혔던 느낌이 남는다
                                val back = spring<Float>(dampingRatio = 0.62f, stiffness = 210f)
                                scope.launch { ox.animateTo(0f, back, initialVelocity = fling.x) }
                                scope.launch { oy.animateTo(0f, back, initialVelocity = fling.y) }
                                fling = Offset.Zero
                            }
                        }) { change, drag ->
                            scope.launch { ox.snapTo(ox.value + drag.x) }
                            scope.launch { oy.snapTo(oy.value + drag.y) }
                            // 최근 움직임을 섞어 둔다 — 한 프레임만 보면 튀는 값이 들어온다
                            fling = Offset(fling.x * 0.6f + drag.x * 24f, fling.y * 0.6f + drag.y * 24f)
                            change.consume()
                        }
                    }
                }
        ) {
            // 테두리는 그림 바깥 상자에 (Image 는 제 크기로 잘라 버린다)
            Box(Modifier.fillMaxSize().touchOutline(!given)) { ArtView(Art.Img(m.item, Art.Emoji(m.itemEmoji)), Modifier.fillMaxSize()) }
            if (easy && !given) Text("톡!", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
        }
        // 15초 흐릿한 예시 — 물건이 반투명으로 친구에게 미끄러졌다 사라진다(진짜 물건은 그대로 · 건네는 것은 아이)
        if (hint != null && !given) {
            val g = ghostAlong(listOf(Offset(startX + itemPx / 2, startY + itemPx / 2), Offset(fCx, fCy)), hint)
            Ghost(g, itemPx, hint) { ArtView(Art.Img(m.item, Art.Emoji(m.itemEmoji)), Modifier.fillMaxSize()) }
            GhostHand(g, wpx * 0.06f, hint)
        } else if (showHint && !given) {
            val hintT = rememberInfiniteTransition(label = "hint2")
            val go by hintT.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "go",
            )
            // 시작 자리 → 친구 자리. 말로 설명하지 않고 **길을 보여 준다**
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (startX + (fCx - itemPx / 2 - startX) * go).roundToInt(),
                            (startY + (fCy - itemPx / 2 - startY) * go).roundToInt(),
                        )
                    }
                    .size(itemSize)
                    .alpha((1f - go) * 0.5f)
            ) { ArtView(Art.Img(m.item, Art.Emoji(m.itemEmoji)), Modifier.fillMaxSize()) }
        }
        gag?.let { g ->
            Box(
                Modifier.offset { IntOffset(hL.roundToInt(), (hT - 40).roundToInt()) }
                    .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
        }
    }
}
