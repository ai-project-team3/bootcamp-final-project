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
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.roundToInt
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.*

/** A6 문지르기 — mission slot 1 (moved from ui/Book.kt as is, 10-03 · 맞춤미션 설계 §7-1 1-a) */
/**
 * 4쪽 미션 1 — 문지르기 (쉬움). 장면 4에서 "누가 흔들었나"에 따라 흔적 · 도구가 바뀐다.
 * 탈것 위에 붙은 것 3개를 손가락으로 문지르면 줄어들다 없어진다. 도구가 손가락을 따라온다. 공룡을 문지르면 장난 반응.
 */
@Composable
internal fun RubMission(d: Director, done: Boolean, heroArt: Art, dinoArt: Art, tool: String) {
    val view = LocalView.current   // 효과음과 같이 진동 (Sfx · 09-25)
    val s = d.s
    val m = s.mission1()
    val density = LocalDensity.current.density
    val rub = remember { mutableStateListOf(0f, 0f, 0f) }
    // 불·물·김은 **그림이 아니라 계산**이다 (9/23). 흔적 그림은 그대로 두고 그 위에 입자를 얹는다 —
    // 미션 성공 판정은 전과 똑같이 `rub` 이 정하므로 화면 없이 도는 검사가 그대로 돈다
    val puffs = rememberParticleField()
    var finger by remember { mutableStateOf<Offset?>(null) }
    // 손가락을 대고 있는 동안은 **가만히 있어도** 물이 나온다 — 소방 호스는 그래야 한다 (9/23)
    var spraying by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(done) }
    var gag by remember { mutableStateOf<String?>(null) }
    val allOut = done || rub.all { it >= 3f }
    // 8초 동안 아무 진전이 없으면 **마스코트가 첫 걸음을 보여 준다** (미션 구상 §5).
    // 말로 설명하지 않는다 — 손이 한 번 지나가는 것을 보여 줄 뿐이다
    val progress = rub.sum()

    // 후~ 불어서 날리기 (미션 구상 C1 · 9/23).
    //
    // **날아갈 수 있는 것에만** 붙인다 — 먼지 · 모래는 불면 날아가지만 먹물 · 진흙은 아니다.
    // 손으로 문지르는 길은 **그대로 남는다.** 불기는 덤이지 대신이 아니다 (실패 없는 설계).
    val blowable = m.blobName.contains("먼지") || m.blobName.contains("모래") || m.blobName.contains("가루")
    val blow = rememberBlowLevel(blowable && !allOut)
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(progress, allOut) {
        showHint = false
        if (allOut || motionFrozen) return@LaunchedEffect
        delay(8_000)
        showHint = true
    }
    val lift by animateFloatAsState(if (allOut) -30f else 0f, tween(900), label = "lift")
    LaunchedEffect(allOut) { if (allOut && !fired) { fired = true; Sfx.play(Sound.SPARKLE, 0L, view = view); d.send(Reply.Tapped("mission", "미션1")) } }
    LaunchedEffect(gag) { if (gag != null) { delay(1400); gag = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        val vx = 0.33f; val vy = 0.22f; val vw = 0.20f; val va = 0.7f
        val vwPx = vw * wpx; val vhPx = vwPx / va
        // 흔적이 붙는 자리 (탈것 그림 안의 비율): 로켓은 아래 엔진 · 거북이 등 · 기차 지붕
        // 일기 모드는 탈것 대신 하루를 메고 다닌 가방에 흙이 묻는다 — 뼈대는 그대로, 소품만 바꾼다 (§7-1 ②)
        val fy = when (s.themeKey) { "space" -> 0.72f; "sea" -> 0.36f; else -> 0.30f }
        // 일기·협업에는 탈것이 없다 (§2-2). 전에는 **가방**을 띄워 놓고 거기에 흙을 묻혔는데,
        // 아이가 가방 이야기를 한 적이 없어서 "놀이터에 남은 모래를 치웠어요" 자막 옆에
        // 뜬금없는 가방이 떠 있었다 (9/22). 이제 흔적은 **놀던 자리(바닥)** 에 흩어진다.
        val scripted = !com.example.finalproject_demo.net.Server.liveFor(s.mode)
        val liveStory = s.mode == StoryMode.STORY && !scripted
        // A live story has no scripted ride. Put its marks on a visible patch, not the hidden ride's roof.
        val blobs = when {
            liveStory -> listOf(0.36f to 0.52f, 0.445f to 0.52f, 0.4025f to 0.70f)
                .map { (bx, by) -> Offset(bx * wpx, by * hpx) }
            s.isDiary -> listOf(0.30f to 0.66f, 0.46f to 0.73f, 0.63f to 0.65f)
                .map { (bx, by) -> Offset(bx * wpx, by * hpx) }
            else -> listOf(0.22f to fy, 0.50f to fy + 0.08f, 0.78f to fy)
                .map { (bx, by) -> Offset(vx * wpx + bx * vwPx, vy * hpx + by * vhPx) }
        }
        if (liveStory) Box(
            Modifier.offset { IntOffset((wpx * 0.30f).roundToInt(), (hpx * 0.42f).roundToInt()) }
                .size((wpx * 0.21f / density).dp, (hpx * 0.38f / density).dp)
                .felt(WoolCream.copy(alpha = 0.88f), RoundedCornerShape(18.dp), lift = 2.dp)
                .semantics { contentDescription = "${m.blobName} 지우는 자리" },
        )
        if (!s.isDiary && scripted) Layer(vx, vy, vw, va, Modifier.offset { IntOffset(0, lift.roundToInt()) }) { ArtView(s.rideArt, Modifier.fillMaxSize()) }
        Stand(0.14f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize().then(
            if (liveStory) Modifier.semantics { contentDescription = "미션 주인공 인형" } else Modifier,
        )) }
        val rubFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        if (rubFriend != null) Stand(0.66f, 0.11f, 0.85f) { ArtView(rubFriend, Modifier.fillMaxSize().then(
            if (liveStory) Modifier.semantics { contentDescription = "미션 친구 인형" } else Modifier,
        )) }
        if (!s.isDiary && scripted) Stand(0.86f, 0.19f, 0.92f, endInset = 88.dp) {
            ArtView(dinoArt, Modifier.fillMaxSize().semantics { contentDescription = "동행 인형 ${s.dino.name}" })
        }

        // 불을 계속 피운다. 문지를수록 기운이 줄어 **입자 수가** 사그라든다.
        //
        // ⚠️ `LaunchedEffect(puffs.tick)` 으로 쓰면 안 된다 — 프레임마다 코루틴을 접었다 다시 띄운다.
        //    한 번만 띄우고 그 안에서 프레임을 기다린다 (9/23)
        val burns = m.blobName.contains("불") || m.blobName.contains("용암")
        LaunchedEffect(done, wpx, hpx, s.isDiary) {
            if (done || motionFrozen) return@LaunchedEffect
            while (true) {
                withFrameNanos { }
                // 후~ 부는 세기만큼 흔적이 날아간다. 세게 불수록 빨리 사라진다
                if (blowable && blow > 0.22f) {
                    blobs.forEachIndexed { i, b ->
                        if (rub[i] < 3f) {
                            val before = rub[i]
                            rub[i] = minOf(3f, rub[i] + blow * 0.09f)
                            // 날아가는 것이 보이게 — 바람을 타고 옆으로 흩어진다
                            puffs.water(b.x, b.y, blow * wpx * 0.02f, -blow * wpx * 0.004f, wpx * 0.03f)
                            if (before < 3f && rub[i] >= 3f) Sfx.play(Sound.SPARKLE, 0L, view = view)
                        }
                    }
                }

                // 호스에서 한 줄기로 뿜는다.
                //
                // ⚠️ **노즐은 한자리에 고정한다** (9/23). 처음에는 호스 그림이 손가락을 따라다니게 두었는데,
                //    그러면 노즐과 겨눈 곳 사이에 거리가 없어 물이 찌끔하고 말았다.
                //    소방차는 노즐이 제자리에 있고 **겨누는 곳이 움직인다** — 그래야 줄기가 길게 끈는다
                val fp = finger
                val nozzleX = 108f * density
                val nozzleY = hpx - 108f * density
                if (spraying && fp != null) {
                    puffs.jet(nozzleX, nozzleY, fp.x, fp.y, wpx * 0.055f)
                }
                blobs.forEachIndexed { i, b ->
                    val alive = (1f - rub[i] / 3f).coerceIn(0f, 1f)
                    if (alive > 0f) {
                        // 「불」일 때만 타오른다 — 먹물 · 모래 · 진흙은 타는 것이 아니다
                        // ⚠️ 반지름을 쪽 전체 폭(0.075)으로 잡았더니 불꿃 수십 개가 겹쳐
                        // **녹색 구름 한 덩어리**가 되어 탈것을 덮었다 (9/23 실기기 확인).
                        // 불은 흔적 하나 크기면 된다
                        if (burns) puffs.flame(b.x, b.y, wpx * 0.032f, alive)
                        puffs.quench(b.x, b.y, wpx * 0.085f, wpx * 0.06f)
                        // 물이 겨눠져 있는 동안 치익 — 간격을 두어 뭉개지지 않게
                        if (spraying) Sfx.play(Sound.HISS, minGapMs = 260L)
                        // 겨누고 **가만히 있어도** 꺼진다 — 호스를 들고 버티는 것도 끄는 것이다
                        if (spraying && fp != null &&
                            abs(fp.x - b.x) < wpx * 0.07f && abs(fp.y - b.y) < hpx * 0.14f
                        ) {
                            rub[i] = minOf(3f, rub[i] + 0.05f)
                            if (rub[i] >= 3f) puffs.steam(b.x, b.y, wpx * 0.075f, 8)
                        }
                    } else if (Math.random() < 0.04) {
                        puffs.smoke(b.x, b.y - wpx * 0.01f, wpx * 0.05f)   // 다 끈 자리에서 잔연기
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(done) {
                    if (done) return@pointerInput
                    detectDragGestures(
                        onDragStart = { finger = it; spraying = true },
                        onDragEnd = { finger = null; spraying = false },
                        onDragCancel = { finger = null; spraying = false },
                    ) { change, drag ->
                        val p = change.position
                        finger = p
                        val amount = abs(drag.x) + abs(drag.y)
                        var hit = false
                        blobs.forEachIndexed { i, b ->
                            if (abs(p.x - b.x) < wpx * 0.07f && abs(p.y - b.y) < hpx * 0.14f && rub[i] < 3f) {
                                val before = rub[i]
                                rub[i] = minOf(3f, rub[i] + amount / 200f); hit = true
                                // 마지막 한 방울이 꺼진 순간 — 김이 확 피어오른다
                                if (before < 3f && rub[i] >= 3f) {
                                    puffs.steam(b.x, b.y, wpx * 0.075f, 10)
                                    Sfx.play(Sound.SPARKLE, minGapMs = 0L, view = view)
                                }
                            }
                        }
                        // 목표 밖 장난 반응 — 일기 모드에는 공룡이 없으니 그 자리도 없다 (§2-2)
                        if (!hit && !s.isDiary && p.x > wpx * 0.70f && p.y > hpx * 0.36f && gag == null) { gag = "부르르! ${s.soundLine}"; d.send(Reply.Tapped("gag", "장난")) }
                        change.consume()
                    }
                }
        ) {
            // 불과 연기는 흔적 그림 **뒤**에 — 그림이 또렷하게 남는다
            Canvas(Modifier.fillMaxSize()) {
                puffs.tick          // 프레임마다 다시 그리게 하는 한 줄
                puffs.draw(this, setOf(Puff.FIRE, Puff.SMOKE))
            }
            blobs.forEachIndexed { i, b ->
                val st = if (done) 3f else rub[i]
                val sz = (0.085f - 0.025f * minOf(st, 2f)) * wpx
                Box(
                    Modifier
                        .offset { IntOffset((b.x - sz / 2).roundToInt(), (b.y - sz / 2).roundToInt()) }
                        .size((sz / density).dp)
                        .semantics { contentDescription = "${m.blobName} 흔적 ${i + 1}" }
                ) {
                    // 불은 **그림을 쓰지 않는다** (9/23 요청). 정지한 🔥 한 장이 타오르는 파티클 위에
                    // 겹쳐 있으면 그 장만 멈춰 보여 오히려 어색했다. 먹물 · 모래 · 진흙은 타는 것이
                    // 아니라 파티클로 대신할 수 없으므로 그림을 그대로 둔다
                    if (burns) Unit
                    else if (st >= 3f) ArtView(Art.Img(m.gone, Art.Emoji(m.goneEmoji)), Modifier.fillMaxSize().alpha(0.8f))
                    // 아직 지울 흔적은 두꺼운 테두리 (09-27) — 불은 그림이 아니라 파티클이라 두를 모양이 없다
                    // ⚠️ 테두리는 그림 **바깥 상자**에 단다 — `Image` 는 제 크기로 잘라서(clipToBounds) 바깥으로 번진 테두리가 사라진다
                    else Box(Modifier.fillMaxSize().touchOutline()) { ArtView(Art.Img(m.blob, Art.Emoji(m.blobEmoji)), Modifier.fillMaxSize()) }
                }
            }
            // 불기를 받는 쪽이면 마이크가 듣고 있다는 것을 **보이게** 둔다 —
            // 부모가 "마이크가 켜져 있다"를 알 수 있어야 한다 (문서 §같이 생각해 볼 질문)
            if (blowable && !allOut) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                        .felt(Wool.copy(alpha = 0.95f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (blow > 0.22f) "후~~~ 잘한다!" else "🎤 후~ 불어 봐! (손으로 쓸어도 돼)",
                        fontSize = 15.sp,
                        color = if (blow > 0.22f) Coral else Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 8초 힌트 — 첫 흔적 위를 손이 슥 지나간다
            if (showHint && !allOut) {
                val hintT = rememberInfiniteTransition(label = "hint1")
                val sweep by hintT.animateFloat(
                    -1f, 1f,
                    infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "sweep",
                )
                val b0 = blobs.firstOrNull { rub[blobs.indexOf(it)] < 3f } ?: blobs[0]
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (b0.x + sweep * wpx * 0.045f - wpx * 0.022f).roundToInt(),
                                (b0.y - hpx * 0.02f).roundToInt(),
                            )
                        }
                        .size((wpx * 0.044f / density).dp)
                        .alpha(0.75f)
                ) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }
            }

            // 물 · 김 · 반짝임은 **앞**에 — 뒤에 그리면 물이 불 뒤로 숨어 뿌리는 느낌이 안 난다
            Canvas(Modifier.fillMaxSize()) {
                puffs.tick
                puffs.draw(this, setOf(Puff.WATER, Puff.STEAM, Puff.SPARK))
            }
            // 호스는 **제자리에 서 있다.** 손가락을 따라다니면 물줄기가 나올 거리가 없다 (9/23)
            if (!allOut) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 70.dp, bottom = 70.dp).size(76.dp)
                ) { ArtView(Art.Img(m.tool, Art.Emoji(m.toolEmoji)), Modifier.fillMaxSize()) }
            }
            gag?.let { g ->
                Box(
                    Modifier.offset { IntOffset((wpx * 0.68f).roundToInt(), (hpx * 0.30f).roundToInt()) }
                        .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
            }
        }
        if (allOut) {
            Box(Modifier.offset {
                if (liveStory) IntOffset((wpx * 0.4025f - 60.dp.toPx()).roundToInt(), (hpx * 0.61f).roundToInt())
                else IntOffset((vx * wpx).roundToInt(), (vy * hpx - 20).roundToInt())
            }.size(120.dp, 54.dp)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize()) }
        }
    }
}
