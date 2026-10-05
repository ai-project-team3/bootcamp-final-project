package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import com.example.finalproject_demo.demo.coopMetLabel
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
import com.example.finalproject_demo.ui.missions.GiveMission
import com.example.finalproject_demo.ui.missions.PuzzleMission
import com.example.finalproject_demo.ui.missions.RubMission
import com.example.finalproject_demo.ui.missions.BlowMission
import com.example.finalproject_demo.ui.missions.SoundMission
import com.example.finalproject_demo.ui.missions.HoseMission
import com.example.finalproject_demo.ui.missions.TurnMission
import com.example.finalproject_demo.ui.missions.RollMission
import com.example.finalproject_demo.demo.missions.soundProp
import com.example.finalproject_demo.demo.missions.blowProp
import com.example.finalproject_demo.demo.mission2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면 비율로 자리 잡기 (책 쪽 안에서) */
@Composable
internal fun Layer(xf: Float, yf: Float, wf: Float, aspect: Float = 0.75f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth * wf
        Box(
            // place first, then the caller's modifier: a scale in it (DRAG friend 0.62) then shrinks
            // about the figure's own centre, not about the corner of the page (#41)
            Modifier
                .offset(x = maxWidth * xf, y = maxHeight * yf)   // offset, not padding: padding < 0 crashes
                .then(modifier)
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
internal fun Stand(
    xf: Float,
    wf: Float,
    depth: Float = 1f,
    floor: Boolean = true,
    modifier: Modifier = Modifier,
    endInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = depth.coerceIn(0f, 1f)
        val bulk = kotlin.math.sqrt((wf / BOOK_WF_HERO).coerceIn(0.5f, 4f))
        val tall = (maxHeight * (BOOK_TALL_FAR + (BOOK_TALL_NEAR - BOOK_TALL_FAR) * d) * bulk)
            .coerceAtMost(maxHeight * 0.72f)
            .let { if (endInset > 0.dp) it.coerceAtMost((maxWidth - endInset).coerceAtLeast(0.dp)) else it }
        val wide = tall
        val feet = maxHeight * (BOOK_FEET_FAR + (BOOK_FEET_NEAR - BOOK_FEET_FAR) * d)
        val left = (maxWidth * xf - wide / 2).let {
            if (endInset > 0.dp) it.coerceAtMost(maxWidth - endInset - wide) else it
        }
        val top = feet - tall * 0.93f

        if (floor) {
            val shW = wide * 0.56f
            val shH = wide * 0.13f
            Box(
                Modifier
                    .offset(x = left + (wide - shW) / 2, y = feet - shH * 0.35f)
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
        // offset, not padding: at the page's edge (RUB xf 0.14 on a 16:10 tablet) left < 0, and a
        // negative padding throws — the app closed on the first mission (#41)
        Box(Modifier.offset(x = left, y = top).then(modifier).width(wide).height(tall)) { content() }
    }
}

@Composable
private fun RoundBtn(text: String, bg: Color, size: Int = 64, onClick: () -> Unit) {
    // 09-29 디자인 시스템 ⑪ — ◀ ▶ 64dp 펠트 버튼(꾹 눌림)
    FeltButton(bg, onClick = onClick, modifier = Modifier.size(size.dp), shape = CircleShape) {
        Text(text, fontSize = (size * 0.42).sp, color = if (bg == FeltCoral) FeltWhite else InkBrown)
    }
}

/** 도구별 반응 글자 — 누른 것 바로 위에 뜬다 (HTML 데모와 같은 방식) */
internal fun reactionFor(s: DemoState, tool: String, target: String, objName: String? = null, objTap: String? = null): String {
    val f = s.friendName
    return when (tool) {
        "hammer" -> "간지러워!"
        "feather" -> "깔깔깔!"
        "glass" -> when (target) {
            "hero" -> when {
                s.isDiary -> "${s.childName}의 오늘 이야기!"
                com.example.finalproject_demo.net.Server.liveFor(s.mode) -> "${s.childName}의 이야기!"   // 서버 모드엔 기본 탈것이 없다(10-02)
                else -> "${s.childName}${eun(s.childName)} ${s.th.vehicle} 선장!"
            }
            "dino" -> "${s.dino.label} · ${s.dino.look}"
            "friend" -> if (s.isDiary) "${s.friendCallName} · ${s.coopMetLabel() ?: "오늘 만난 사람"}" else "$f · ${s.newcomerKind} 친구"
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
fun BookPageView(d: Director, stage: Stage.BookPage, savedBook: SavedStoryBook? = null, onReply: (Reply) -> Unit = d::send) {
    val s = d.s
    val page = stage.index
    var tool by remember { mutableStateOf("hand") }
    val heroArt = s.storyHeroArt
    val dinoArt = Art.DinoArt(s.dinoColor, s.dinoKey)

    fun react(target: String) = onReply(Reply.Tapped("tool:$tool:$target", tool))

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
        act: RigMotion? = null,
        walkIn: Boolean = false,
    ) {
        // 걸어 들어오기 (09-28) — 쪽이 열리면 화면 왼쪽 밖에서 제자리까지 1.4초. 걷는 동안은 걷기 동작
        val walk = remember(page) { Animatable(if (walkIn && !motionFrozen) 1f else 0f) }
        LaunchedEffect(page) { if (walk.value > 0f) walk.animateTo(0f, tween(1400, easing = LinearEasing)) }
        val screenW = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val walking = walk.value > 0f
        val walkMod = if (walkIn) Modifier.graphicsLayer { translationX = -walk.value * screenW * (xf + 0.12f) } else Modifier
        val body: @Composable () -> Unit = {
            Tappable(
                text = { reactionFor(s, tool, target) },
                modifier = Modifier.fillMaxSize(),
                onTap = { if (tool == "hand" && onHand != null) onHand() else react(target) },
            ) { ArtView(art, Modifier.fillMaxSize(), if (walking) RigMotion.WALK else act) }
        }
        if (stand != null) Stand(xf, wf, stand, floor = hasFloor(s.bgName), modifier = walkMod.then(mod), content = body)
        else Layer(xf, yf, wf, aspect, walkMod.then(mod), content = body)
    }

    val kind = if (page == 0) PageKind.COVER else savedBook?.pages?.getOrNull(page - 1)?.kind ?: s.pageKind(page)
    val last = savedBook?.pages?.size ?: s.pageCount

    /**
     * **쪽에 적힌 대로 움직인다** (9/21).
     *
     * "로켓에 올라탔어요" 라고 적혀 있는데 주인공이 탈것 **옆에** 서 있거나,
     * "손을 흔들었어요" 라고 적혀 있는데 가만히 서 있으면 아이가 글과 그림을 잇지 못한다.
     * 자막에 나온 낱말을 보고 자리와 몸짓을 정한다.
     *
     * "기차가 흔들렸어요"처럼 사람이 흔든 것이 아닌 문장은 인사로 읽지 않는다 — `손`·`인사`·`안녕`이 같이 있어야 한다.
     */
    val caption = if (page > 0) savedBook?.pages?.getOrNull(page - 1)?.caption ?: s.bookCaption(page) else ""
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

    // 뼈대로 움직이는 동작 (09-28) — 주인공 뼈대가 준비됐으면 몸을 기울여 인사하는 대신 **팔이** 흔든다.
    // 그네 · 미끄럼틀 · 뛰기처럼 몸 전체가 움직이는 것은 그대로 몸짓(motionMod)으로 둔다
    val heroAct = heroActFrom(kind, caption, riding)
    val dinoAct = buddyActFrom(kind, caption)
    val heroWalks = page > 0 && walksInFrom(kind, caption, riding)
    val heroRigged = if (s.storyHeroImage != null) RigCache.peek(s.storyHeroImage!!) != null
        else rememberRig(s.heroAttr?.let { heroImageName(it) }) != null
    val heroPose = if (heroRigged && motion == Motion.NONE) Modifier else poseMod

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
        // 서버 모드 동화에는 아이가 말하거나 그린 것만 선다 — 대본의 기본 탈것(기차 · 로켓)과 공룡은 빼다(10-02 조장 실기기)
        val scripted = !com.example.finalproject_demo.net.Server.liveFor(s.mode)
        val showRide = !s.isDiary && scripted
        val showDino = !s.isDiary && scripted
        // 일기 · 협업은 아이가 그린 것 → 아이가 말한 사람 순으로 세우고, 둘 다 없으면 아무도 안 세운다 (일기 §3-2)
        val friendShown: Art? = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
        val showFriend = friendShown != null

        when (kind) {
            PageKind.COVER -> Cover(d, heroArt)
            PageKind.DEPART -> {
                Scenery()
                // 일기 화이트보드는 배경이나 친구 그림으로 바꾸지 않고 첫 쪽에 원본 획을 붙인다.
                if (s.isDiary && s.sceneDrawing.isNotEmpty()) {
                    Layer(0.52f, 0.14f, 0.36f, aspect = 1.5f) {
                        Box(
                            Modifier.fillMaxSize()
                                .felt(FeltWhite, RoundedCornerShape(14.dp), lift = 5.dp, texture = false)
                                .padding(8.dp)
                        ) { ArtView(s.sceneArt, Modifier.fillMaxSize()) }
                    }
                }
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(0, wobble.roundToInt()) })
                if (showRide && riding) {
                    // 탈것 위 — 탈것과 같은 흔들림으로 함께 움직여야 "타고 있다"로 보인다.
                    // 발이 탈것 몸통에 **겹쳐야** 올라탄 것으로 보인다. 띄우면 위에 떠 있는 것처럼 보였다 (9/21)
                    Char("hero", heroArt, 0.445f, 0.120f, 0.075f, mod = Modifier.offset { IntOffset(0, wobble.roundToInt()) }.then(heroPose), act = heroAct)
                } else {
                    Char("hero", heroArt, 0.20f, 0.30f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(heroPose), stand = 1f, act = heroAct, walkIn = heroWalks)
                }
            }
            PageKind.SHAKE -> {
                // 무너지거나 부딪힌 쪽에서만 흔든다. "까르르 웃었어요" 에 화면이 흔들리면 아이가 무서워한다
                Scenery(quake = quaking)
                // 흔들리는 쪽이 아니면 주인공도 탈것도 가만히 있는다 — 숨쉬듯 까딱이기만 한다
                val jolt = if (quaking) shake else bob
                if (showRide) Char("vehicle", s.rideArt, 0.34f, 0.16f, 0.17f, 0.62f, Modifier.offset { IntOffset(if (quaking) jolt.roundToInt() else 0, if (quaking) 0 else jolt.roundToInt()) })
                Char("hero", heroArt, 0.18f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(if (quaking) (jolt / 2).roundToInt() else 0, if (quaking) 0 else jolt.roundToInt()) }.then(heroPose), stand = 1f, act = heroAct)
                // "창밖에서 손을 흔들고 있었어요" — 적혀 있으면 정말 흔든다
                if (friendShown != null) FadeIn { Char("friend", friendShown, 0.76f, 0.20f, 0.18f, 1f, waveMod, stand = 0.85f) }
            }
            PageKind.MEET, PageKind.TALK -> {
                Scenery()
                if (showRide) Char("vehicle", s.rideArt, 0.40f, 0.14f, 0.15f, 0.62f)
                Char("hero", heroArt, 0.20f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(heroPose), stand = 1f, act = heroAct, walkIn = heroWalks)
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
                if (showRide) Char("hero", heroArt, 0.405f, 0.100f, 0.075f, mod = Modifier.offset { IntOffset((wobble * 3).roundToInt(), wobble.roundToInt()) }.then(heroPose), act = heroAct)
                else Char("hero", heroArt, 0.20f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(heroPose), stand = 1f, act = heroAct, walkIn = heroWalks)
                if (friendShown != null) Char("friend", friendShown, 0.76f, 0.26f, 0.16f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }, stand = 0.85f)
            }
            PageKind.FAIL -> {
                Scenery()
                Char("hero", heroArt, 0.20f, 0.32f, 0.11f, stand = 1f, act = heroAct)
                // 풀이 죽은 친구 — 살짝 기울고 아래로
                if (friendShown != null) Char("friend", friendShown, 0.72f, 0.34f, 0.15f, 1f, Modifier.offset { IntOffset(0, 10) }.alpha(0.85f), stand = 0.85f)
                Text("…", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 170.dp).alpha(twinkle))
            }
            // 미션 자리 1 — 아이 말에 불 것(촛불 · 민들레 · 먼지 · 바람)이 있으면 C1 불기, 아니면 A6 문지르기 (#101)
            PageKind.RUB -> s.blowProp()?.let { BlowMission(d, stage.m1Done, heroArt, it) }
                ?: s.soundProp()?.let { SoundMission(d, stage.m1Done, heroArt, it) }
                ?: RubMission(d, stage.m1Done, heroArt, dinoArt, tool)
            // 미션 2가 **틀에 따라 갈라진다** (9/23 · 미션 구상 §4).
            // 「다시 쌓다 · 맞추다 · 되돌리다」로 푸는 틀(A 도전-성취 · G 우화-교훈)은 퍼즐이,
            // 「건네다 · 나누다」로 푸는 나머지 틀은 지금까지의 건네주기가 맞다.
            // 쪽 종류(DRAG)와 감독에게 보내는 신호는 그대로라 책 흐름은 안 바뀐다
            PageKind.DRAG ->
                when (s.missions().slot2) {
                    MissionId.A3 -> PuzzleMission(d, stage.m2Done)
                    MissionId.A1 -> HoseMission(d, stage.m2Done, heroArt)       // 「불을 껐어」 (#101)
                    MissionId.A4 -> TurnMission(d, stage.m2Done, heroArt)       // 「꽉 잠갔어」 (#101)
                    MissionId.D4 -> RollMission(d, stage.m2Done, heroArt)       // 「공을 굴렸어 · 골인」 (#101)
                    else -> GiveMission(d, stage.m2Done, heroArt, tool)
                }
            PageKind.TOGETHER -> {
                Scenery(glow = if (s.isDiary) s.diaryGlow else s.hotspots.map { it.key }.toSet())
                Box(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).size(110.dp, 50.dp).alpha(twinkle)) { ArtView(Art.Img("prop_sparkle", Art.Emoji("⭐✨⭐")), Modifier.fillMaxSize()) }
                Char("hero", heroArt, 0.17f, 0.32f, 0.11f, mod = Modifier.offset { IntOffset(0, bob.roundToInt()) }.then(heroPose), stand = 1f, act = heroAct)
                if (friendShown != null) Char("friend", friendShown, 0.42f, 0.26f, 0.17f, 1f, Modifier.offset { IntOffset(0, (-bob).roundToInt()) }, stand = 0.85f,
                    // no dino in a live book — the recorded sound plays from the newcomer instead (10-05)
                    onHand = if (!showDino && s.mode == com.example.finalproject_demo.demo.StoryMode.STORY) ({ d.send(Reply.Tapped("dino", s.friendCallName)) }) else null)
                if (showDino) Char("dino", dinoArt, 0.72f, 0.20f, 0.28f, 1.35f, Modifier.offset { IntOffset(0, bob.roundToInt()) }, onHand = { d.send(Reply.Tapped("dino", s.dino.label)) }, stand = 0.92f, act = dinoAct)
                if (s.partnerHelpLine != null) {
                    Layer(0.80f, 0.34f, 0.10f) { ArtView(Art.Img(s.partner.img, Art.Emoji(s.partner.emoji)), Modifier.fillMaxSize()) }
                }
            }
        }

        // 위쪽 안내 한 줄 (마스코트 말풍선 대신). 책장을 넘기는 중(page > 0)에는 맨 위 가운데에 도구가 있어 그 아래로 내린다
        if (s.bookNote.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (page > 0) 76.dp else 46.dp)
                    .widthIn(max = 520.dp)
                    .felt(Wool, RoundedCornerShape(20.dp), lift = 4.dp, stitch = false)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("👆 ${s.bookNote}", fontSize = 16.sp, color = InkBrown, textAlign = TextAlign.Center) }
        }

        if (page > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 84.dp, end = 84.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    // 09-29 디자인 시스템 ⑪ — 아래 자막 띠: 크림 펠트 + 바느질 · 🔊 다시 듣기
                    .felt(Wool.copy(alpha = 0.97f), RoundedCornerShape(22.dp), lift = 5.dp)
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(caption, fontSize = 19.sp, lineHeight = 25.sp, color = InkBrown, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                FeltButton(Cheek, onClick = { onReply(Reply.Tapped("speak", "낭독")) }, modifier = Modifier.size(48.dp), shape = CircleShape) {
                    ArtView(Art.Img("ic_speaker", Art.Emoji("🔊")), Modifier.size(28.dp))
                }
            }
            // 미션 도구 — 펠트 원(고른 것은 겨자). **맨 위 가운데** (10-01 사용자 요청 — 전에는 왼쪽 위 🏠 · 🔒 뒤)
            Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("ic_hand", "✋", "hand"), Triple("ic_hammer", "🔨", "hammer"), Triple("ic_feather", "🪶", "feather"), Triple("ic_magnifier", "🔍", "glass")).forEach { (img, e, k) ->
                    FeltButton(if (tool == k) FeltMustard else FeltWhite, onClick = { tool = k }, modifier = Modifier.size(52.dp), shape = CircleShape) {
                        ArtView(Art.Img(img, Art.Emoji(e)), Modifier.size(34.dp))
                    }
                }
            }
            // 페이지 진행도 — 왼쪽 위 🏠 · 🔒(시스템) 바로 뒤에 붙인다 (10-01 사용자 요청 — 전에는 오른쪽 위).
            // 보이는 🏠 · 🔒 는 124dp 에서 끝난다(누르는 자리 12 + 56 + 4 + 56 에서 안쪽 4dp) — 버튼 사이와 같은 12dp 띄운다
            Row(
                Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 136.dp)
                    .felt(InkBrown.copy(alpha = 0.55f), RoundedCornerShape(Radius.Round), lift = 2.dp, stitch = false, texture = false).padding(horizontal = 10.dp, vertical = 6.dp),
                // 가운데 도구와 붙지 않게 조금 날씬하게 — 점 · 간격을 줄였다 (태블릿 · 4:3 에서 거의 맞닿았다)
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(last) { i -> Box(Modifier.size(if (i + 1 == page) 9.dp else 6.dp).clip(CircleShape).background(if (i + 1 == page) Sun else Color.White.copy(alpha = 0.7f))) }
                Spacer(Modifier.width(5.dp))
                Text("$page/$last", fontSize = 12.sp, color = Color.White)
            }
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            if (page > 0) RoundBtn("◀", Wool) { onReply(Reply.Tapped("prev", "앞")) }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            val canNext = when (kind) { PageKind.RUB -> stage.m1Done; PageKind.DRAG -> stage.m2Done; else -> true }
            if (page == last) {
                FeltButton(FeltMustard, onClick = { onReply(Reply.Tapped("next", "다음")) }, modifier = Modifier.size(Touch.KidMin), shape = CircleShape) {
                    ArtView(Art.Img("ic_books", Art.Emoji("📚")), Modifier.size(40.dp))
                }
            } else {
                RoundBtn("▶", if (canNext) FeltCoral else WoolCream.copy(alpha = 0.6f)) { if (canNext) onReply(Reply.Tapped("next", "다음")) }
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
                .felt(Wool, RoundedCornerShape(26.dp), lift = 8.dp)
                .border(4.dp, FeltMustard, RoundedCornerShape(26.dp))
                .padding(horizontal = 30.dp, vertical = 12.dp)
        ) { Text("『${s.title}』", fontSize = 30.sp, color = InkBrown, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ArtView(heroArt, Modifier.size(96.dp, 134.dp))
            // 일기 모드 표지에는 아이가 그린 것만 선다 — 안 그렸으면 주인공만 (§2-2 · §3-2)
            val coverFriend = if (s.isDiary) s.friendOrPartnerArt else s.friendArt
            if (coverFriend != null) ArtView(coverFriend, Modifier.size(120.dp))
            if (!s.isDiary && !com.example.finalproject_demo.net.Server.liveFor(s.mode)) ArtView(Art.DinoArt(s.dinoColor, s.dinoKey), Modifier.size(130.dp, 110.dp))
        }
        Spacer(Modifier.height(6.dp))
        // 지은이는 **아이 이름만** 쓴다 (9/22).
        // 전에는 "· 함께 {어른}" 이 늘 붙었다. 어른이 지은 자리가 하나도 없는 날에도 붙어서
        // 아이가 혼자 지은 책에 어른 이름이 올라갔다. 이 책의 지은이는 아이다.
        Text("글 · 그림 ${s.childName}", fontSize = 13.sp, color = Color.White)
        s.template?.let { t -> Text("${t.pages.size}쪽", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f)) }
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

/**
 * **쪽에 적힌 대로 뼈대가 움직인다** (09-28) — 주인공의 동작.
 *
 * 전에는 그림 한 장을 통째로 기울이고 흔들었다(`waveMod` · `motionMod`). 이제 팔이 따로 움직이니
 * "손을 흔들었어요" 에는 **정말 손을 흔들고**, 해낸 쪽에서는 두 팔을 번쩍 든다.
 *
 * 차례가 중요하다 — 속상한 일이 먼저다. "넘어져서 울었어요" 에 만세를 하면 글과 그림이 반대로 간다.
 */
fun heroActFrom(kind: PageKind, caption: String, riding: Boolean): RigMotion = when {
    kind == PageKind.FAIL || listOf("넘어", "무너", "떨어", "부딪", "울었", "다쳤", "아팠", "속상", "슬펐").any { it in caption } -> RigMotion.SAD
    wavingFrom(caption) -> RigMotion.WAVE
    kind == PageKind.TOGETHER || listOf("만세", "기뻐", "기뻤", "신났", "해냈", "웃었", "좋아했").any { it in caption } -> RigMotion.HOORAY
    !riding && motionFrom(caption) == Motion.RUN -> RigMotion.WALK
    else -> RigMotion.IDLE
}

/** 공룡(동행)의 동작 — 함께하는 마지막 쪽에서는 꼬리를 흔들며 반긴다 */
fun buddyActFrom(kind: PageKind, caption: String): RigMotion = when {
    kind == PageKind.FAIL || listOf("울었", "속상", "슬펐").any { it in caption } -> RigMotion.SAD
    kind == PageKind.TOGETHER -> RigMotion.HOORAY
    else -> RigMotion.IDLE
}

/**
 * 주인공이 **걸어 들어오는** 쪽인가 (09-28) — 떠나는 쪽 · 만나는 쪽에서 화면 왼쪽에서 제자리까지 걸어온다.
 * 탈것에 탄 쪽은 걷지 않는다.
 */
fun walksInFrom(kind: PageKind, caption: String, riding: Boolean): Boolean =
    !riding && (kind == PageKind.DEPART || kind == PageKind.MEET || listOf("걸어", "찾아갔", "다가갔", "다가왔").any { it in caption })

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
