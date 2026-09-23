package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
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
import com.example.finalproject_demo.demo.bookCaption
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.mission1
import com.example.finalproject_demo.demo.mission2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면 비율로 자리 잡기 (책 쪽 안에서) */
@Composable
private fun Layer(xf: Float, yf: Float, wf: Float, aspect: Float = 0.75f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth * wf
        Box(
            modifier
                .padding(start = maxWidth * xf, top = maxHeight * yf)
                .width(w)
                .height(w / aspect)
        ) { content() }
    }
}

/*
 * ── 책 쪽에도 무대 배치 규칙을 적용한다 (9/23) ──────────────────────────
 *
 * `docs/무대_배치_규칙.md` 는 무대 화면(`Screen.kt` `WorldItemView`)까지만 다뤘고,
 * 책 쪽은 *"이 문서는 안 봤다"* 로 남겨 두었다. 그런데 **시연에서 가장 오래 보이는 화면이 책**이다.
 * 실기기로 확인해 보니 무대 화면만 고쳐 놓고 책은 그대로 인물이 하늘에 떠 있었다.
 *
 * 규칙은 같다 — 발을 바닥에 내리고, 키를 키우고, 발밑에 그림자를 깐다.
 * 다만 책 쪽은 **일부러 띄워 놓은 구성**이 섞여 있어(로켓 위에 올라탄 주인공 · 날아가는 탈것)
 * 서 있어야 할 것만 골라 [Stand] 로 바꾼다. 나머지는 [Layer] 그대로 둔다.
 */

/** 책 쪽에서 인물이 서는 바닥 — 아래 자막 띠 위다 */
private const val BOOK_FEET_NEAR = 0.80f
private const val BOOK_FEET_FAR = 0.60f

/** 책 쪽 인물의 키 (쪽 높이 기준). 무대 화면보다 조금 작다 — 한 쪽에 서넛이 함께 선다 */
private const val BOOK_TALL_NEAR = 0.46f
private const val BOOK_TALL_FAR = 0.26f

private const val BOOK_WF_HERO = 0.11f

/** 바닥이 없는 배경 — 우주와 바닷속에서는 그림자를 깔지 않는다 */
private fun hasFloor(bgName: String) = bgName != "bg_space" && bgName != "bg_sea"

/**
 * 인물을 **바닥에 세운다.** [Layer] 와 달리 [xf] 는 가운데이고, 높이·크기는 [depth] 가 정한다.
 *
 * @param xf 가로 **가운데** (0~1)
 * @param wf 크기 견줌 — 기존 값을 그대로 넘기면 「누가 더 큰가」가 유지된다 (주인공 0.11 · 공룡 0.28)
 * @param depth 1 = 앞줄 · 0 = 지평선
 * @param floor 발밑 그림자를 깔 것인가 (우주 · 바닷속은 false)
 */
@Composable
private fun Stand(
    xf: Float,
    wf: Float,
    depth: Float = 1f,
    floor: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = depth.coerceIn(0f, 1f)
        val bulk = kotlin.math.sqrt((wf / BOOK_WF_HERO).coerceIn(0.5f, 4f))
        val tall = (maxHeight * (BOOK_TALL_FAR + (BOOK_TALL_NEAR - BOOK_TALL_FAR) * d) * bulk)
            .coerceAtMost(maxHeight * 0.72f)
        val wide = tall
        val feet = maxHeight * (BOOK_FEET_FAR + (BOOK_FEET_NEAR - BOOK_FEET_FAR) * d)
        val left = maxWidth * xf - wide / 2
        val top = feet - tall * 0.93f

        if (floor) {
            val shW = wide * 0.56f
            val shH = wide * 0.13f
            Box(
                Modifier
                    .padding(start = left + (wide - shW) / 2, top = feet - shH * 0.35f)
                    .width(shW)
                    .height(shH)
                    .then(modifier)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(
                        Brush.radialGradient(
                            0f to Color(0x8C191005),
                            0.5f to Color(0x59191005),
                            1f to Color(0x00191005),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width / 2,
                        ),
                        alpha = 0.40f + 0.45f * d,
                    )
                }
            }
        }
        Box(modifier.padding(start = left, top = top).width(wide).height(tall)) { content() }
    }
}

/**
 * 미션 2 — **4등분 퍼즐** (미션 구상 A3).
 *
 * 아이가 맞추는 그림은 **아이 이야기로 만든 그 배경**이다. 새 그림을 가져오지 않는다 —
 * *"자기 이야기를 손으로 완성한다"* 가 이 미션의 뜻이다. 다 맞추면 그림이 살아 움직인다.
 *
 * 실패가 없다 — 틀린 자리에 놓으면 **말없이 제자리로 돌아간다.** 틀렸다는 표시도 소리도 없다.
 * 미션 1을 도움받아 끝낸 아이(`m1Result == "helped"`)에게는 **두 조각**만 준다 (§2 · 3~4세 완화).
 */
@Composable
private fun PuzzlePage(d: Director, done: Boolean) {
    val s = d.s
    val density = LocalDensity.current.density
    val picId = assetId(s.bgName)
    // 그림이 없으면 퍼즐을 낼 수 없다 — 건네주기로 물러난다 (없는 것을 만들어 내지 않는다)
    if (picId == 0) {
        DragPage(d, done, Art.HeroArt(s.heroAttr ?: HeroAttr()), "hand")
        return
    }
    val pic = ImageBitmap.imageResource(picId)
    val cols = if (s.m1Result == "helped") 2 else 2
    val rows = if (s.m1Result == "helped") 1 else 2
    val count = cols * rows

    val placed = remember { mutableStateListOf(*Array(count) { done }) }
    val drag = remember { List(count) { Animatable(Offset.Zero, Offset.VectorConverter) } }
    var sent by remember { mutableStateOf(done) }
    val allIn = done || placed.all { it }
    val puffs = rememberParticleField()
    val scope = rememberCoroutineScope()

    LaunchedEffect(allIn) {
        if (!allIn || sent) return@LaunchedEffect
        sent = true
        Sfx.play(Sound.SPARKLE, 0L)
        d.send(Reply.Tapped("mission", "미션2"))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wpx = constraints.maxWidth.toFloat()
        val hpx = constraints.maxHeight.toFloat()
        // 판 — 화면 오른쪽에 놓는다. 왼쪽 아래는 조각을 늘어놓는 자리다
        val boardH = hpx * 0.44f
        val boardW = boardH * (cols.toFloat() / rows)
        val boardX = wpx * 0.60f
        val boardY = hpx * 0.20f
        val pw = boardW / cols
        val ph = boardH / rows

        fun slot(i: Int) = Offset(boardX + (i % cols) * pw, boardY + (i / cols) * ph)
        // 흩어 놓는 자리 — 왼쪽 아래에 **겹치지 않게** 나란히. 겹쳐 두면 아이가 집을 수가 없다
        fun home(i: Int) = Offset(wpx * 0.05f + i * (pw * 1.06f), hpx * 0.56f)

        // 맞출 자리 — **완성된 그림을 흐리게** 깔아 어디에 뭘 놓는지 보여 준다.
        // 실패 없는 설계의 절반은 안내다 — 어디에 놓을지 모르면 그건 어려운 게 아니라 막막한 것이다
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                pic,
                dstOffset = IntOffset(boardX.roundToInt(), boardY.roundToInt()),
                dstSize = IntSize(boardW.roundToInt(), boardH.roundToInt()),
                alpha = 0.28f,
            )
            for (i in 0 until count) {
                val p = slot(i)
                drawRect(Color.White.copy(alpha = 0.75f), topLeft = p, size = Size(pw, ph), style = Stroke(4f))
            }
        }

        for (i in 0 until count) {
            val target = slot(i)
            val start = home(i)
            val at = if (placed[i]) target else start + drag[i].value
            Box(
                Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size((pw / density).dp, (ph / density).dp)
                    .pointerInput(placed[i], done) {
                        if (placed[i] || done) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                val now = start + drag[i].value
                                val near = kotlin.math.hypot(now.x - target.x, now.y - target.y) < pw * 0.45f
                                if (near) {
                                    placed[i] = true
                                    Sfx.play(Sound.THUD, 0L)
                                    puffs.burst(target.x + pw / 2, target.y + ph / 2, wpx * 0.018f, 10)
                                } else {
                                    // 틀렸다고 말하지 않는다 — 조용히 제자리로
                                    scope.launch {
                                        drag[i].animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = 260f))
                                    }
                                }
                            },
                        ) { change, d2 ->
                            scope.launch { drag[i].snapTo(drag[i].value + d2) }
                            change.consume()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // 배경 그림에서 이 조각에 해당하는 네모만 오려 그린다
                    val sx = (pic.width.toFloat() / cols * (i % cols)).roundToInt()
                    val sy = (pic.height.toFloat() / rows * (i / cols)).roundToInt()
                    drawImage(
                        pic,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(pic.width / cols, pic.height / rows),
                        dstOffset = IntOffset(0, 0),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                    // 오려낸 흰 테두리 — 아이 그림과 같은 결 (⭐27)
                    drawRect(Color.White.copy(alpha = 0.9f), style = Stroke(6f))
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            puffs.tick
            puffs.draw(this)
        }
    }
}

@Composable
private fun RoundBtn(text: String, bg: Color, size: Int = 46, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(6.dp, CircleShape, ambientColor = Ink.copy(alpha = 0.3f), spotColor = Ink.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(bg)
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = (size * 0.45).sp, color = Ink, fontWeight = FontWeight.Bold) }
}

/** 도구별 반응 글자 — 누른 것 바로 위에 뜬다 (HTML 데모와 같은 방식) */
private fun reactionFor(s: DemoState, tool: String, target: String, objName: String? = null, objTap: String? = null): String {
    val f = s.friendName
    return when (tool) {
        "hammer" -> "간지러워!"
        "feather" -> "깔깔깔!"
        "glass" -> when (target) {
            "hero" -> if (s.isDiary) "${s.childName}의 오늘 이야기!" else "${s.childName}${eun(s.childName)} ${s.th.vehicle} 선장!"
            "dino" -> "${s.dino.label} · ${s.dino.look}"
            "friend" -> if (s.isDiary) "${s.friendCallName} · 오늘 만난 사람" else "$f · ${s.newcomerKind} 친구"
            "vehicle" -> "${s.rideName}예요"
            "obj" -> objName ?: "?"
            else -> "?"
        }
        else -> when (target) {   // 손
            "hero" -> "히히!"
            "friend" -> "헤헤, 반가워!"
            "dino" -> s.soundLine
            "vehicle" -> "부릉부릉!"
            "obj" -> objTap ?: "톡!"
            else -> "톡!"
        }
    }
}

/**
 * 책 한 쪽 — 전체 화면 그림책 (v0.8: 마스코트 · 아래 버튼 없이 책만).
 * 쪽 수(6~8)와 쪽마다의 그림 구성은 템플릿이 정한다 (v0.9) — 출발 · 흔들림 · 대화 · 여정 · 실패 · 미션 2개 · 함께.
 * 세계 배경 위에 확정 그림(주인공 · 아이 그림 · 공룡), 배경 속 것은 그 자리가 반응한다. 누르면 그 자리 위에 반응 글자.
 */
@Composable
fun BookPageView(d: Director, stage: Stage.BookPage) {
    val s = d.s
    val page = stage.index
    var tool by remember { mutableStateOf("hand") }
    val heroArt = Art.HeroArt(s.heroAttr ?: HeroAttr())
    val dinoArt = Art.DinoArt(s.dinoColor, s.dinoKey)

    fun react(target: String) = d.send(Reply.Tapped("tool:$tool:$target", tool))

    val inf = rememberInfiniteTransition(label = "book")
    val wobble by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "wobble")
    val shake by inf.animateFloat(-9f, 9f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "shake")
    val bob by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val twinkle by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "tw")

    /**
     * 책 쪽의 인물 한 장.
     *
     * @param stand 바닥에 **서는** 인물이면 깊이(1 = 앞줄). null 이면 예전처럼 좌표 그대로 띄운다 —
     *   로켓에 올라탄 주인공 · 날아가는 탈것처럼 **일부러 띄운 구성**이 있다 (9/23)
     */
    @Composable
    fun Char(
        target: String,
        art: Art,
        xf: Float,
        yf: Float,
        wf: Float,
        aspect: Float = 0.75f,
        mod: Modifier = Modifier,
        onHand: (() -> Unit)? = null,
        stand: Float? = null,
    ) {
        val body: @Composable () -> Unit = {
            Tappable(
                text = { reactionFor(s, tool, target) },
                modifier = Modifier.fillMaxSize(),
                onTap = { if (tool == "hand" && onHand != null) onHand() else react(target) },
            ) { ArtView(art, Modifier.fillMaxSize()) }
        }
        if (stand != null) Stand(xf, wf, stand, floor = hasFloor(s.bgName), modifier = mod, content = body)
        else Layer(xf, yf, wf, aspect, mod, content = body)
    }

    val kind = s.pageKind(page)
    val last = s.pageCount

    /**
     * **쪽에 적힌 대로 움직인다** (9/21).
     *
     * "로켓에 올라탔어요" 라고 적혀 있는데 주인공이 탈것 **옆에** 서 있거나,
     * "손을 흔들었어요" 라고 적혀 있는데 가만히 서 있으면 아이가 글과 그림을 잇지 못한다.
     * 자막에 나온 낱말을 보고 자리와 몸짓을 정한다.
     *
     * "기차가 흔들렸어요"처럼 사람이 흔든 것이 아닌 문장은 인사로 읽지 않는다 — `손`·`인사`·`안녕`이 같이 있어야 한다.
     */
    val caption = if (page > 0) s.bookCaption(page) else ""
    val riding = ridingFrom(caption)
    val waving = wavingFrom(caption)
    // 소리말과 흔들림도 자막에서 읽는다 (9/22) — 쪽 종류가 아니라 **적힌 내용**이 정한다
    val effect = if (kind == PageKind.SHAKE) effectFrom(caption) else null
    val quaking = kind == PageKind.SHAKE && quakeFrom(caption)
    // 인사 — 몸을 좌우로 기울인다. 손 그림이 따로 없으니 몸짓으로 보여 준다
    val waveMod = if (waving) Modifier.graphicsLayer {
        rotationZ = wobble * 1.6f
        transformOrigin = TransformOrigin(0.5f, 1f)
    } else Modifier

    /**
     * **움직임도 자막에서 읽는다** (9/22).
     *
     * "그네를 탔어요" 라고 적혀 있는데 주인공이 가만히 서 있으면 아이가 글과 그림을 잇지 못한다.
     * 그림을 새로 만들지 않고 **몸짓으로** 보여 준다 — 그네는 좌우로 크게 흔들리고,
     * 미끄럼틀은 비스듬히 기울어 미끄러지고, 뛰는 장면은 위아래로 통통 튄다.
     */
    val motion = motionFrom(caption)
    val motionMod = when (motion) {
        Motion.SWING -> Modifier.graphicsLayer {
            // 그네 — 위쪽 줄에 매달린 것처럼 **머리 위를 축으로** 크게 흔들린다
            rotationZ = wobble * 2.6f
            transformOrigin = TransformOrigin(0.5f, -0.8f)
        }
        Motion.SLIDE -> Modifier.graphicsLayer {
            // 미끄럼틀 — 비스듬히 기울고 살짝 내려앉는다
            rotationZ = 16f
            translationY = bob * 1.4f
        }
        Motion.RUN -> Modifier.graphicsLayer {
            // 뛰기 — 통통 튄다
            translationY = -abs(wobble) * 1.3f
            rotationZ = wobble * 0.5f
        }
        Motion.NONE -> Modifier
    }
    // 인사와 움직임이 같이 있으면 움직임이 이긴다 — 그네를 타면서 손을 흔드는 그림은 읽기 어렵다
    val poseMod = if (motion != Motion.NONE) motionMod else waveMod

    // ⚠️ 소개 시간은 여기서 재지 않는다 (9/22). 여기서 재면 **표지를 보는 동안 시간이 가 버린다** —
    //    표지(0쪽)에는 배경 자리가 없어서 원이 뜨기도 전에 안내가 끝났다.
    //    이제 `HotspotLayer` 가 원을 실제로 그리는 순간부터 세고, 다 보여 주면 알려 준다.

    @Composable
    fun Scenery(quake: Boolean = false, glow: Set<String> = if (s.isDiary) s.diaryGlow else s.mentioned.toSet()) {
        // 배경 그림 속 것들 — 새로 얹지 않고 그 자리가 반응한다 (9/17)
        HotspotLayer(
            s.bgName, glow, pulse = page, quake = quake,
            // 안내는 **책에서만** 한다. 소개가 끝나면 조용해진다 — 계속 반짝이면 그림 읽기를 방해한다
            introducing = !s.hotspotIntroShown,
            glowMentioned = false,
            onIntroShown = { s.hotspotIntroShown = true },
            text = { h -> if (tool == "glass") h.name else if (tool == "feather") "간질간질~" else h.tap },
            onTap = { react("bg") },
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2E2A26))) {
        AssetImage(s.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(s.worldBg)))
        }
        // 소리말은 **자막에서 읽어 온다** (9/22). 맞는 것이 없으면 아무것도 띄우지 않는다.
        // 전에는 이 자리에서 늘 "쿵!" 이 떴다 — 일기 모드의 같은 쪽은 "오늘 있었던 일" 쪽이라
        // 그림 그린 날에도 "쿵!" 이 올라갔다. `effectFrom` 의 주석에 자세히 적었다
        effect?.let { word ->
            Text(word, fontSize = 44.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    .offset { IntOffset(if (quaking) shake.roundToInt() else 0, 0) })
        }

        // 일기 모드에는 탈것(로켓 · 거북이 · 기차)도 동행 공룡도 없다 — 묻지 않는 칸이다 (일기 설계 §2-2).
        // 아이가 아무도 그리지 않은 날에는 친구 자리도 비워 둔다 — 앱이 없는 친구를 만들어 내지 않는다 (§3-2).
        val showRide = !s.isDiary
        val showDino = !s.isDiary
        // 일기 · 협업은 아이가 그린 것 → 아이가 말한 사람 순으로 세우고, 둘 다 없으면 아무도 안 세운다 (일기 §3-2)
        val friendShown: Art? = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        val showFriend = friendShown != null

        when (kind) {
            PageKind.COVER -> Cover(d, heroArt)
            PageKind.DEPART -> {
                Scenery()
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(0, wobble.roundToInt()) })
                if (showRide && riding) {
                    // 탈것 위 — 탈것과 같은 흔들림으로 함께 움직여야 "타고 있다"로 보인다.
                    // 발이 탈것 몸통에 **겹쳐야** 올라탄 것으로 보인다. 띄우면 위에 떠 있는 것처럼 보였다 (9/21)
                    Char("hero", heroArt, 0.445f, 0.120f, 0.075f, mod = Modifier.offset { IntOffset(0, wobble.roundToInt()) }.then(poseMod))
                } else {
                    Char("hero", heroArt, 0.20f, 0.30f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(poseMod), stand = 1f)
                }
            }
            PageKind.SHAKE -> {
                // 무너지거나 부딪힌 쪽에서만 흔든다. "까르르 웃었어요" 에 화면이 흔들리면 아이가 무서워한다
                Scenery(quake = quaking)
                // 흔들리는 쪽이 아니면 주인공도 탈것도 가만히 있는다 — 숨쉬듯 까딱이기만 한다
                val jolt = if (quaking) shake else bob
                if (showRide) Char("vehicle", s.rideArt, 0.34f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(if (quaking) jolt.roundToInt() else 0, if (quaking) 0 else jolt.roundToInt()) })
                Char("hero", heroArt, 0.18f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(if (quaking) (jolt / 2).roundToInt() else 0, if (quaking) 0 else jolt.roundToInt()) }.then(poseMod), stand = 1f)
                // "창밖에서 손을 흔들고 있었어요" — 적혀 있으면 정말 흔든다
                if (friendShown != null) FadeIn { Char("friend", friendShown, 0.76f, 0.20f, 0.18f, 1f, waveMod, stand = 0.85f) }
            }
            PageKind.MEET, PageKind.TALK -> {
                Scenery()
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.14f, 0.15f, 0.62f)
                Char("hero", heroArt, 0.20f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(poseMod), stand = 1f)
                // 인사하는 쪽에서는 친구도 같이 손을 흔든다 — 한쪽만 흔들면 어색하다
                if (friendShown != null) Char("friend", friendShown, 0.74f, 0.24f, 0.18f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }.then(waveMod), stand = 0.85f)
                if (s.partnerHelpLine != null && page == last - 1) {
                    Layer(0.04f, 0.40f, 0.09f) { ArtView(Art.Img(s.partner.img, Art.Emoji(s.partner.emoji)), Modifier.fillMaxSize()) }
                }
            }
            PageKind.JOURNEY -> {
                Scenery(glow = if (s.isDiary) s.diaryGlow else s.hotspots.map { it.key }.toSet())
                if (showRide) Char("vehicle", s.rideArt, 0.36f, 0.14f, 0.17f, 0.62f, Modifier.offset { IntOffset((wobble * 3).roundToInt(), wobble.roundToInt()) })
                // 여정 쪽은 늘 "타고 가는 중" 이다 — 옆에 세워 두면 걸어가는 것처럼 보인다
                if (showRide) Char("hero", heroArt, 0.405f, 0.100f, 0.075f, mod = Modifier.offset { IntOffset((wobble * 3).roundToInt(), wobble.roundToInt()) }.then(poseMod))
                else Char("hero", heroArt, 0.20f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(poseMod), stand = 1f)
                if (friendShown != null) Char("friend", friendShown, 0.76f, 0.26f, 0.16f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }, stand = 0.85f)
            }
            PageKind.FAIL -> {
                Scenery()
                Char("hero", heroArt, 0.20f, 0.32f, 0.11f, stand = 1f)
                // 풀이 죽은 친구 — 살짝 기울고 아래로
                if (friendShown != null) Char("friend", friendShown, 0.72f, 0.34f, 0.15f, 1f, Modifier.offset { IntOffset(0, 10) }.alpha(0.85f), stand = 0.85f)
                Text("…", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 170.dp).alpha(twinkle))
            }
            PageKind.RUB -> RubPage(d, stage.m1Done, heroArt, dinoArt, tool)
            // 미션 2가 **틀에 따라 갈라진다** (9/23 · 미션 구상 §4).
            // 「다시 쌓다 · 맞추다 · 되돌리다」로 푸는 틀(A 도전-성취 · G 우화-교훈)은 퍼즐이,
            // 「건네다 · 나누다」로 푸는 나머지 틀은 지금까지의 건네주기가 맞다.
            // 쪽 종류(DRAG)와 감독에게 보내는 신호는 그대로라 책 흐름은 안 바뀐다
            PageKind.DRAG ->
                if (s.templateKey == "A" || s.templateKey == "G") PuzzlePage(d, stage.m2Done)
                else DragPage(d, stage.m2Done, heroArt, tool)
            PageKind.TOGETHER -> {
                Scenery(glow = if (s.isDiary) s.diaryGlow else s.hotspots.map { it.key }.toSet())
                Box(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).size(110.dp, 50.dp).alpha(twinkle)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("⭐✨⭐")), Modifier.fillMaxSize()) }
                Char("hero", heroArt, 0.17f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(poseMod), stand = 1f)
                if (friendShown != null) Char("friend", friendShown, 0.42f, 0.26f, 0.17f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }, stand = 0.85f)
                if (showDino) Char("dino", dinoArt, 0.72f, 0.20f, 0.28f, 1.35f, Modifier.offset { IntOffset(0, bob.roundToInt()) }, onHand = { d.send(Reply.Tapped("dino", s.dino.label)) }, stand = 0.92f)
                if (s.partnerHelpLine != null) {
                    Layer(0.80f, 0.34f, 0.10f) { ArtView(Art.Img(s.partner.img, Art.Emoji(s.partner.emoji)), Modifier.fillMaxSize()) }
                }
            }
        }

        // 위쪽 안내 한 줄 (마스코트 말풍선 대신)
        if (s.bookNote.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 46.dp)
                    .widthIn(max = 520.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("👆 ${s.bookNote}", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }

        if (page > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 64.dp, end = 64.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFFBF2).copy(alpha = 0.96f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.bookCaption(page), fontSize = 17.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).noRippleClickable { d.send(Reply.Tapped("speak", "낭독")) }) { ArtView(Art.Img("ic_speaker", Art.Emoji("🔊")), Modifier.fillMaxSize()) }
            }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("ic_hand", "✋", "hand"), Triple("ic_hammer", "🔨", "hammer"), Triple("ic_feather", "🪶", "feather"), Triple("ic_magnifier", "🔍", "glass")).forEach { (img, e, k) ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .shadow(if (tool == k) 6.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (tool == k) Sun else Color.White.copy(alpha = 0.9f))
                            .border(if (tool == k) 3.dp else 0.dp, Color.White, CircleShape)
                            .noRippleClickable { tool = k }
                            .padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) { ArtView(Art.Img(img, Art.Emoji(e)), Modifier.fillMaxSize()) }
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 76.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(last) { i -> Box(Modifier.size(if (i + 1 == page) 10.dp else 7.dp).clip(CircleShape).background(if (i + 1 == page) Sun else Color.White.copy(alpha = 0.7f))) }
                Spacer(Modifier.width(6.dp))
                Text("$page/$last", fontSize = 12.sp, color = Color.White)
            }
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            if (page > 0) RoundBtn("◀", Color.White.copy(alpha = 0.9f)) { d.send(Reply.Tapped("prev", "앞")) }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            val canNext = when (kind) { PageKind.RUB -> stage.m1Done; PageKind.DRAG -> stage.m2Done; else -> true }
            if (page == last) {
                Box(
                    Modifier.size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Sun).noRippleClickable { d.send(Reply.Tapped("next", "다음")) }.padding(7.dp)
                ) { ArtView(Art.Img("ic_books", Art.Emoji("📚")), Modifier.fillMaxSize()) }
            } else {
                RoundBtn("▶", if (canNext) Sun else Color.White.copy(alpha = 0.5f)) { if (canNext) d.send(Reply.Tapped("next", "다음")) }
            }
        }
    }
}

@Composable
private fun FadeIn(content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(900), label = "fade")
    Box(Modifier.fillMaxSize().alpha(a)) { content() }
}

@Composable
private fun Cover(d: Director, heroArt: Art) {
    val s = d.s
    Box(Modifier.fillMaxSize().background(Color(0x55000000)))
    Column(
        Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 96.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .shadow(10.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFFFBF2))
                .border(4.dp, Sun2, RoundedCornerShape(22.dp))
                .padding(horizontal = 28.dp, vertical = 10.dp)
        ) { Text("『${s.title}』", fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ArtView(heroArt, Modifier.size(96.dp, 134.dp))
            // 일기 모드 표지에는 아이가 그린 것만 선다 — 안 그렸으면 주인공만 (§2-2 · §3-2)
            val coverFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
            if (coverFriend != null) ArtView(coverFriend, Modifier.size(120.dp))
            if (!s.isDiary) ArtView(Art.DinoArt(s.dinoColor, s.dinoKey), Modifier.size(130.dp, 110.dp))
        }
        Spacer(Modifier.height(6.dp))
        // 지은이는 **아이 이름만** 쓴다 (9/22).
        // 전에는 "· 함께 {어른}" 이 늘 붙었다. 어른이 지은 자리가 하나도 없는 날에도 붙어서
        // 아이가 혼자 지은 책에 어른 이름이 올라갔다. 이 책의 지은이는 아이다.
        Text("글 · 그림 ${s.childName}", fontSize = 13.sp, color = Color.White)
        s.template?.let { t -> Text("${t.name} · ${t.pages.size}쪽", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f)) }
    }
}

/**
 * 4쪽 미션 1 — 문지르기 (쉬움). 장면 4에서 "누가 흔들었나"에 따라 흔적 · 도구가 바뀐다.
 * 탈것 위에 붙은 것 3개를 손가락으로 문지르면 줄어들다 없어진다. 도구가 손가락을 따라온다. 공룡을 문지르면 장난 반응.
 */
@Composable
private fun RubPage(d: Director, done: Boolean, heroArt: Art, dinoArt: Art, tool: String) {
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
    LaunchedEffect(allOut) { if (allOut && !fired) { fired = true; Sfx.play(Sound.SPARKLE, 0L); d.send(Reply.Tapped("mission", "미션1")) } }
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
        val blobs = if (s.isDiary)
            listOf(0.30f to 0.66f, 0.46f to 0.73f, 0.63f to 0.65f).map { (bx, by) -> Offset(bx * wpx, by * hpx) }
        else
            listOf(0.22f to fy, 0.50f to fy + 0.08f, 0.78f to fy).map { (bx, by) -> Offset(vx * wpx + bx * vwPx, vy * hpx + by * vhPx) }
        if (!s.isDiary) Layer(vx, vy, vw, va, Modifier.offset { IntOffset(0, lift.roundToInt()) }) { ArtView(s.rideArt, Modifier.fillMaxSize()) }
        Stand(0.14f, 0.11f) { ArtView(heroArt, Modifier.fillMaxSize()) }
        val rubFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        if (rubFriend != null) Stand(0.66f, 0.11f, 0.85f) { ArtView(rubFriend, Modifier.fillMaxSize()) }
        if (!s.isDiary) Stand(0.86f, 0.19f, 0.92f) { ArtView(dinoArt, Modifier.fillMaxSize()) }

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
                            if (before < 3f && rub[i] >= 3f) Sfx.play(Sound.SPARKLE, 0L)
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
                                    Sfx.play(Sound.SPARKLE, minGapMs = 0L)
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
                ) {
                    // 불은 **그림을 쓰지 않는다** (9/23 요청). 정지한 🔥 한 장이 타오르는 파티클 위에
                    // 겹쳐 있으면 그 장만 멈춰 보여 오히려 어색했다. 먹물 · 모래 · 진흙은 타는 것이
                    // 아니라 파티클로 대신할 수 없으므로 그림을 그대로 둔다
                    if (burns) Unit
                    else if (st >= 3f) ArtView(Art.Img(m.gone, Art.Emoji(m.goneEmoji)), Modifier.fillMaxSize().alpha(0.8f))
                    else ArtView(Art.Img(m.blob, Art.Emoji(m.blobEmoji)), Modifier.fillMaxSize())
                }
            }
            // 불기를 받는 쪽이면 마이크가 듣고 있다는 것을 **보이게** 둔다 —
            // 부모가 "마이크가 켜져 있다"를 알 수 있어야 한다 (문서 §같이 생각해 볼 질문)
            if (blowable && !allOut) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                        .shadow(4.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
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
                        .shadow(4.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
            }
        }
        if (allOut) {
            Box(Modifier.offset { IntOffset((vx * wpx).roundToInt(), (vy * hpx - 20).roundToInt()) }.size(120.dp, 54.dp)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("✨")), Modifier.fillMaxSize()) }
        }
    }
}

/**
 * 5쪽 미션 2 — 끌어다 놓기 (보통 · 목표 영역 넓게). 장면 10에서 아이가 말한 것을 친구에게 건넨다.
 * 친구에게 놓으면 하트가 퐁퐁, 주인공에게 놓으면 장난 반응. 미션 1에서 도와줬으면 톡 누르면 날아간다.
 */
@Composable
private fun DragPage(d: Director, done: Boolean, heroArt: Art, tool: String) {
    val s = d.s
    val m = s.mission2()
    val easy = s.m1Result == "helped"
    val scope = rememberCoroutineScope()
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var given by remember { mutableStateOf(done) }
    var sent by remember { mutableStateOf(done) }
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
    LaunchedEffect(given) { if (given && !sent) { sent = true; delay(1200); d.send(Reply.Tapped("mission", "미션2")) } }
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
        val startX = wpx * 0.30f; val startY = hpx * 0.22f
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
        Layer(
            fx, fy, fw, 1f,
            Modifier
                .offset { IntOffset(0, (-hop.value * hpx * 0.045f).roundToInt()) }
                .scale((if (given) beat else 1f) * targetScale),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 미션 2는 건넬 상대가 있어야 한다. 아이가 그린 것 → 아이가 말한 사람 →
                // 둘 다 없으면 마스코트가 받는다. **없는 친구를 앱이 만들어 내지 않는다** (일기 §3-2)
                val target = s.friendOrPartnerArt ?: Art.Mascot
                Tappable({ reactionFor(s, tool, "friend") }, Modifier.fillMaxSize()) { ArtView(target, Modifier.fillMaxSize()) }
                if (!given) Box(Modifier.fillMaxSize().border(3.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)))
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
            Sfx.play(Sound.THUD, 0L)
            puffs.burst(fCx, fCy, wpx * 0.03f, 22)
            Sfx.play(Sound.SPARKLE, 0L)
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
            ArtView(Art.Img(m.item, Art.Emoji(m.itemEmoji)), Modifier.fillMaxSize())
            if (easy && !given) Text("톡!", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (showHint && !given) {
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
                    .shadow(4.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(g, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * 쪽 자막에서 **탈것에 탔는지** 읽는다 (9/21).
 * `BookPageView` 안에 두면 화면 없이 검사할 수 없어서 밖으로 꺼냈다.
 */
/**
 * 상호작용 원을 **소개하는 시간** (9/22). 이 뒤로는 둘레가 조용해진다.
 * 짧으면 못 보고, 길면 그림을 읽는 것을 방해한다.
 */
const val HOTSPOT_INTRO_MS = 4500L

/** 쪽 자막이 말하는 움직임 — 그림을 새로 만들지 않고 몸짓으로 보여 준다 (9/22) */
enum class Motion { NONE, SWING, SLIDE, RUN }

/**
 * 쪽 자막에서 **움직임**을 읽는다 (9/22).
 *
 * *"그네를 타고 있다" · "미끄럼틀을 타고 있다"* 처럼 역동적인 내용이 적혀 있는데 주인공이
 * 가만히 서 있으면 글과 그림이 따로 논다. 자막에 나온 낱말로 자세를 정한다.
 *
 * ⚠️ `ridingFrom`(탈것에 올라탐)과는 다른 축이다. 그쪽은 **자리**를 정하고 이쪽은 **몸짓**을 정한다.
 * 그래서 "로켓을 타고" 는 여기서 걸리지 않아야 한다 — 탈것은 이미 그림에 있다.
 */
fun motionFrom(caption: String): Motion = when {
    // ⚠️ 일이 어긋난 쪽에서는 움직이지 않는다 (9/22). "달리다가 넘어졌어요" 는 **넘어진 것**이
    //    이야기인데, '달리'만 보고 통통 뛰게 하면 글과 그림이 반대로 간다
    listOf("넘어", "무너", "떨어", "부딪", "울었", "다쳤", "아팠").any { it in caption } -> Motion.NONE
    "그네" in caption -> Motion.SWING
    "미끄럼" in caption || "미끄러" in caption -> Motion.SLIDE
    listOf("뛰어", "달렸", "달려", "뛰었", "쫓아").any { it in caption } -> Motion.RUN
    else -> Motion.NONE
}

fun ridingFrom(caption: String): Boolean =
    listOf("올라탔", "타고", "탔어", "탔습", "태우").any { it in caption }

/**
 * 쪽 자막에서 **손을 흔드는지** 읽는다.
 *
 * ⚠️ "기차가 흔들렸어요" · "거북이를 붙잡고 마구 흔들고 있었어요" 는 인사가 아니다 —
 * `손` · `인사` 가 같이 있어야 한다. 이 오탐이 없는지는 에뮬레이터에서도 확인했다 (9/21).
 */
fun wavingFrom(caption: String): Boolean =
    ("흔들" in caption && ("손" in caption || "인사" in caption)) ||
        "안녕" in caption || "인사했" in caption

/**
 * 쪽 자막에서 **소리말**을 읽는다 (9/22).
 *
 * 전에는 `PageKind.SHAKE` 쪽이면 내용과 상관없이 **늘 "쿵!"** 이 떴다.
 * 동화 모드에서는 누가 탈것을 흔드는 쪽이라 맞았지만, 일기·협업 모드에서는 같은 자리가
 * *"오늘 있었던 일"* 쪽이다. **"친구랑 그림을 그렸어요" 위에 "쿵!" 이 떠 있었다.**
 *
 * 이제 자막에 적힌 것에서 고른다. 맞는 것이 없으면 **아무것도 띄우지 않는다** —
 * 없는 소리를 지어내지 않는 쪽이 낫다 (일기 설계 §3-2와 같은 판단).
 *
 * 차례가 중요하다. "넘어져서 울었어요" 는 넘어진 쪽이 먼저다.
 */
fun effectFrom(caption: String): String? = when {
    listOf("무너", "와르르", "쏟아져", "쓰러").any { it in caption } -> "와르르!"
    // "쿵" 은 자막이 직접 그렇게 적은 경우다 ("땅이 쿵쿵 울리고…") — 동화 모드가 쓰던 말을 지킨다
    listOf("넘어", "떨어", "부딪", "박았", "구르", "쿵").any { it in caption } -> "쿵!"
    listOf("빙글", "소용돌이", "휘몰").any { it in caption } -> "빙글빙글!"
    listOf("쏟", "엎질", "튀었", "splash", "물장구").any { it in caption } -> "촤악!"
    listOf("울었", "눈물", "훌쩍", "속상").any { it in caption } -> "훌쩍…"
    listOf("달렸", "뛰어", "달려", "쫓").any { it in caption } -> "다다닥!"
    listOf("싸웠", "다퉜", "뺏", "티격").any { it in caption } -> "티격태격!"
    listOf("웃었", "까르르", "신났", "재밌", "깔깔").any { it in caption } -> "까르르!"
    listOf("흔들", "덜컹", "기우뚱").any { it in caption } -> "덜컹!"
    listOf("놀랐", "깜짝", "헉").any { it in caption } -> "깜짝!"
    else -> null
}

/**
 * 이 쪽이 **정말 흔들리는** 쪽인가 — 배경을 지진처럼 떨지 결정한다 (9/22).
 *
 * 소리말이 있다고 다 흔드는 것이 아니다. *"까르르!"* 나 *"훌쩍…"* 에 화면이 흔들리면
 * 아이가 무서워한다. 무너지거나 부딪히거나 덜컹거린 쪽에서만 흔든다.
 */
fun quakeFrom(caption: String): Boolean =
    effectFrom(caption) in setOf("와르르!", "쿵!", "덜컹!", "빙글빙글!")
