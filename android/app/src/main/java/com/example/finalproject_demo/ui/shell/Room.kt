package com.example.finalproject_demo.ui.shell

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.imageResource
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.coopReady
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.OttoMove
import com.example.finalproject_demo.ui.OttoPuppet
import com.example.finalproject_demo.ui.Cheek
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.rememberQuietRest
import com.example.finalproject_demo.ui.wakeOnTouch
import com.example.finalproject_demo.ui.Curtain
import com.example.finalproject_demo.ui.CurtainDeep
import com.example.finalproject_demo.ui.FeltButton
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltMustard
import com.example.finalproject_demo.ui.FeltSky
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.Kitten
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.StageWood
import com.example.finalproject_demo.ui.StageWoodDeep
import com.example.finalproject_demo.ui.StarWallet
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.WoolCream
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.motionFrozen
import com.example.finalproject_demo.ui.noRippleClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * ⑨ 오또의 방 — **버튼 화면이 없다. 방 안의 물건이 곧 메뉴다** (Toca Boca · Pok Pok · Khan Academy Kids · 09-29)
 *
 *   🎭 인형극 무대 = 이야기 만들기   🪟 창문 = 오늘 이야기   🛋 소파 = 같이 만들기   📚 책장 = 내 책
 *
 * 물건을 누르면 오또가 발자국을 남기며 그 물건으로 걸어가서 묻는다 — "책장으로 갈까?" (그림 · 목소리 · ✓/✕ 큰 버튼).
 * 「응!」이면 흐름에 **지금 첫 화면 버튼과 같은 신호**를 보낸다 → 흐름(`Scenes.sceneAdult`)은 그대로다.
 * 아무것도 안 누르고 6초가 지나면 오또가 무대를 권한다 — 반짝임은 **추천 하나에만**.
 * 왼쪽 위 🔒 는 **2초 길게** 눌러야 열린다.
 *
 * 자리는 `.pen` 의 800×360 좌표를 화면 비율로 옮긴다.
 */

/** 방 물건 → 이야기 모드 (이어 갈 이야기가 이 물건 것인가) */
private fun modeOf(value: String) = when (value) {
    "start" -> com.example.finalproject_demo.demo.StoryMode.STORY
    "diary" -> com.example.finalproject_demo.demo.StoryMode.DIARY
    "coop" -> com.example.finalproject_demo.demo.StoryMode.COOP
    else -> null
}

/**
 * 방 물건 하나 = 모드 하나. 물건 아래 **이름표**(아이콘 + 모드 이름)로 무엇을 하는 곳인지 보인다 (09-29 사용자 요청).
 * 누르면 모드마다 **다른 말**로 묻는다 — 「뭐 하러 갈까?」 하나로 다 묻지 않는다.
 *
 * @param title    이름표 · 확인 창 제목 (모드 이름)
 * @param question 확인 창에서 오또가 묻는 말
 * @param detail   확인 창 아래 한 줄 — 이 모드에서 무엇을 하는지
 * @param art      ComfyUI 로 만든 물건 그림 (`res/drawable` · tools/gen_room.py). 없으면 펠트 도형으로 그린다
 */
private enum class Thing(
    val value: String, val label: String, val title: String, val question: String, val detail: String, val icon: String, val art: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    /** 모드 색 — 이름표 아이콘 원 · 확인 창 테두리 · 제목 띠 */
    val color: Color,
    /** 모드 아이콘 그림 (ComfyUI · tools/gen_room.py) — 없으면 [icon] 이모지 */
    val badge: String,
    /** 이름표 아래 한 줄 — 무엇을 하는 곳인지 */
    val sub: String,
) {
    WINDOW("diary", "오늘 있었던 일로", "그림일기", "오늘 있었던 일로 그림일기 만들래?", "오늘 한 일을 말하면 그림일기가 돼요", "☀️", "room_window", 84f, 18f, 150f, 150f, FeltSky, "icon_diary", "오늘 있었던 일"),
    THEATER("start", "이야기 만들기", "동화 만들기", "오또랑 새 동화 만들래?", "주인공을 고르고 상상한 이야기를 말해요", "🎭", "room_theater", 414f, 70f, 190f, 222f, FeltCoral, "icon_story", "상상 이야기"),
    SOFA("coop", "같이 만들기", "같이 만들기", "부모님이 준비한 이야기 들어 볼래?", "부모님이 고른 이야기로 오또가 물어봐요", "🛋", "room_sofa", 10f, 196f, 230f, 134f, FeltTeal, "icon_coop", "어른이랑 함께"),
    SHELF("shelf", "책장", "내 책장", "내가 만든 책 보러 갈래?", "지금까지 만든 책을 다시 볼 수 있어요", "📚", "room_shelf", 624f, 58f, 164f, 252f, FeltMustard, "feat_shelf", "만든 책 보기"),
}

/**
 * 오또를 누르면 하는 리액션 (09-29 사용자 — 「마스코트를 터치하면 여러 리액션」). 직전과 같은 것은 고르지 않는다.
 * @param hearts 하트가 떠오르나
 */
private enum class Poke(val move: OttoMove, val line: String, val millis: Long, val hearts: Boolean = false) {
    JUMP(OttoMove.JUMP, "야호! 폴짝!", 1300),
    SPIN(OttoMove.SPIN, "빙글빙글~", 1100),
    DANCE(OttoMove.DANCE, "랄라~ 같이 춤출래?", 1800),
    GIGGLE(OttoMove.GIGGLE, "헤헤, 간지러워!", 1300),
    HELLO(OttoMove.WAVE, "안녕! 나 오또야", 1600),
    LOVE(OttoMove.HOORAY, "너 좋아!", 1600, hearts = true),
}

/**
 * ⑧ 방 둘러보기 (10-05) — 처음 설정 끝에 아이가 물건 넷을 **차례로 직접 눌러 보며** 무엇을 하는 곳인지 듣는다.
 * 전에는 무대 하나만 가리켜서 그림일기 · 같이 만들기 · 책장을 아이가 몰랐다. 동화(무대)를 맨 끝에 둔다 — 눌러서 바로 말해 보기 연습으로 간다.
 */
private val TOUR = listOf(
    Thing.WINDOW to "여기는 창문이야! 오늘 있었던 일을 말하면 그림일기가 돼",
    Thing.SOFA to "소파에선 엄마 아빠가 준비한 이야기를 같이 만들어",
    Thing.SHELF to "책장엔 우리가 만든 책이 모여. 언제든 다시 볼 수 있어!",
    Thing.THEATER to "마지막! 무대에선 상상한 동화를 만들어. 같이 해 보자!",
)

/** 오또 크기 · 쉬는 자리 (디자인 좌표) — 소파와 무대 사이 바닥. 물건과 겹치지 않게 (09-29) */
private const val OTTO = 160f
private const val HOME_X = 244f

/*
 * 디자인 좌표(800×360) → 이 화면의 dp.
 *
 * 09-30 기기 맞춤: 좌표를 **배경 그림(room_bg · 1344×768)에 붙인다.** 전에는 가로 · 세로를 따로 늘려서
 * 태블릿(세로가 긴 화면)에서 배경과 가구가 서로 다르게 늘어나 이름표 · 오또가 벽 · 바닥에서 떨어졌다.
 *  - 기준 폰(갤럭시 S10 · 868×411dp)에서 배경을 가로에 맞춰 그렸을 때의 자리를 그림 픽셀로 바꿔 둔다 → 기준 폰은 전과 똑같다
 *  - 배경은 **가로에 맞춘다** — 물건이 양끝까지 있어 좌우를 자를 수 없다
 *  - 세로가 남으면(태블릿) 그림을 바닥에 붙이고, 위는 줄무늬 벽을 늘려 채운다([RoomBackground])
 *  - 세로가 모자라면(긴 폰) 가운데를 보이되 물건이 있는 줄(창문 위 ~ 소파 아래)은 꼭 들어오게
 */
private const val IMG_W = 1344f
private const val IMG_H = 768f
private const val REF_W = 868f
private const val REF_H = 411f
private const val REF_S = REF_W / IMG_W
private val REF_OY = (REF_H - IMG_H * REF_S) / 2
private fun imgX(v: Float) = v / 800f * REF_W / REF_S
private fun imgY(v: Float) = (v / 360f * REF_H - REF_OY) / REF_S
/** 꼭 보여야 하는 줄 — 창문 위 · 소파 아래(이름표 · 발자국 포함) */
private val NEED_TOP = imgY(10f)
private val NEED_BOTTOM = imgY(345f)
/** 세로가 남을 때 늘리는 줄무늬 벽 — 위 가랜드 아래부터 걸레받이 위까지 (그림 픽셀) */
private const val WALL_TOP = 90f
private const val WALL_BOTTOM = 545f

private class Grid(w: Dp, h: Dp) {
    /** 그림 1px 이 몇 dp 인가 · 그림 왼쪽 위가 화면 어디인가 */
    val s: Float = minOf(w.value / IMG_W, h.value / (NEED_BOTTOM - NEED_TOP))
    val ox: Float = (w.value - IMG_W * s) / 2
    val oy: Float = (IMG_H * s).let { ih ->
        if (ih <= h.value) h.value - ih
        else ((h.value - ih) / 2).coerceIn(-NEED_TOP * s, h.value - NEED_BOTTOM * s)
    }
    /** 자리 */
    fun x(v: Float) = (ox + imgX(v) * s).dp
    fun y(v: Float) = (oy + imgY(v) * s).dp
    /** 길이 */
    fun dx(v: Float) = (imgX(v) * s).dp
    fun dy(v: Float) = (v / 360f * REF_H / REF_S * s).dp
}

/** 방 배경 — [Grid] 와 같은 배율 · 같은 자리. 위에 남는 곳은 가랜드는 맨 위에 두고 줄무늬 벽만 늘린다 */
@Composable
private fun RoomBackground(g: Grid, fallback: @Composable () -> Unit) {
    val id = com.example.finalproject_demo.ui.assetId("room_bg")
    if (id == 0) { fallback(); return }
    val img = androidx.compose.ui.graphics.ImageBitmap.imageResource(id)
    Canvas(Modifier.fillMaxSize()) {
        val k = density
        val s = g.s * k; val ox = g.ox * k; val oy = g.oy * k
        val w = (IMG_W * s).roundToInt()
        fun slice(sy0: Float, sy1: Float, dy0: Float, dy1: Float) = drawImage(
            img,
            srcOffset = IntOffset(0, (sy0 * img.height / IMG_H).roundToInt()),
            srcSize = IntSize(img.width, ((sy1 - sy0) * img.height / IMG_H).roundToInt()),
            dstOffset = IntOffset(ox.roundToInt(), dy0.roundToInt()),
            dstSize = IntSize(w, (dy1 - dy0).roundToInt().coerceAtLeast(1)),
        )
        if (oy <= 0f) slice(0f, IMG_H, oy, oy + IMG_H * s)
        else {
            slice(0f, WALL_TOP, 0f, WALL_TOP * s)
            slice(WALL_TOP, WALL_BOTTOM, WALL_TOP * s, oy + WALL_BOTTOM * s)
            slice(WALL_BOTTOM, IMG_H, oy + WALL_BOTTOM * s, oy + IMG_H * s)
        }
    }
}

@Composable
fun OttoRoom(d: Director, tutorial: Boolean = false, sample: Boolean = false, onTutorialTap: () -> Unit = {}) {
    val s = d.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Thing?>(null) }
    var asking by remember { mutableStateOf(false) }
    var idleHint by remember { mutableStateOf(false) }
    /** 오또가 걷는 중 — 걸음 사이 잠깐 멈출 때도 걷기 동작을 유지한다 */
    var moving by remember { mutableStateOf(false) }
    /** 걷는 방향 — 왼쪽으로 가면 왼쪽을 본다 */
    var goingLeft by remember { mutableStateOf(false) }
    /** 지금 하는 리액션 · 몇 번째 누름인가(같은 리액션이라도 다시 시작) */
    var poke by remember { mutableStateOf<Poke?>(null) }
    var pokeId by remember { mutableStateOf(0) }
    val view = androidx.compose.ui.platform.LocalView.current
    fun pokeOtto() {
        // 걷는 중 · 묻는 중 · 튜토리얼에서는 리액션하지 않는다(해야 할 것을 가리지 않게)
        if (moving || target != null || tutorial) return
        idleHint = false
        val next = Poke.entries.filter { it != poke }.random()
        poke = next; pokeId++
        com.example.finalproject_demo.ui.Sfx.play(com.example.finalproject_demo.ui.Sound.POP, 0L, view = view)
        val my = pokeId
        scope.launch { delay(next.millis); if (pokeId == my) poke = null }
    }
    val walkX = remember { Animatable(HOME_X) }
    val paws = remember { mutableStateListOf<Float>() }
    val offline = Server.on && !online(ctx)
    /** 방 둘러보기 — 지금 가리키는 물건 (튜토리얼일 때만) */
    var tour by remember { mutableStateOf(0) }
    val focus: Thing? = if (tutorial) TOUR[tour].first else null
    if (tutorial) {
        OttoSays(TOUR[tour].second)
        // 오또가 가리키는 물건 옆으로 걸어간다
        LaunchedEffect(tour) {
            val t = TOUR[tour].first
            // 물건 **옆**에 선다 — 앞에 서면 아이가 누를 물건 · 이름표를 오또가 가린다(10-05 책장). 왼쪽에 자리가 없으면 오른쪽
            val left = t.x - OTTO * 0.8f
            val to = (if (left >= 10f) left else t.x + t.w - OTTO * 0.2f).coerceIn(10f, 800f - OTTO)
            goingLeft = to < walkX.value
            if (motionFrozen) walkX.snapTo(to) else { moving = true; walkX.animateTo(to, tween(700, easing = LinearEasing)); moving = false }
        }
    }

    LaunchedEffect(target) {
        if (target == null && !tutorial && !motionFrozen) { delay(6000); idleHint = true }
    }

    fun pick(t: Thing) {
        // 둘러보기 — 오또가 가리키는 물건만 받는다. 다 보면 마지막(무대)에서 말해 보기로
        if (tutorial) {
            if (t != focus) return
            com.example.finalproject_demo.ui.Sfx.play(com.example.finalproject_demo.ui.Sound.POP, 0L, view = view)
            if (tour == TOUR.lastIndex) onTutorialTap() else tour++
            return
        }
        // 이미 한 물건으로 가는 중이거나 묻는 중이면 다른 물건은 받지 않는다 — 알림만 누를 수 있다
        if (target != null) return
        // 샘플 책 보기(로그인 · 동의 전) — 책장만. 이야기를 만들려면 로그인부터
        if (sample && t != Thing.SHELF) { Shell.sampleOnly = false; Shell.step = Step.LOGIN; return }
        idleHint = false
        target = t
        scope.launch {
            // 오또가 물건 쪽으로 걸어간다 — 지나간 자리에 발자국
            paws.clear()
            // 소파는 오른쪽 끝 옆에 선다 — 가운데에 서면 「같이 만들기 · 어른이랑 함께」 이름표를 가렸다 (#98 · 10-03 실기기)
            val to = (if (t == Thing.SOFA) t.x + t.w - OTTO * 0.2f else t.x + t.w / 2 - OTTO / 2).coerceIn(10f, 800f - OTTO)
            val from = walkX.value
            goingLeft = to < from
            moving = true
            if (!motionFrozen) {
                val steps = 4
                for (k in 1..steps) {
                    walkX.animateTo(from + (to - from) * k / steps, tween(220, easing = LinearEasing))
                    paws += walkX.value + OTTO / 2 - 10f
                }
            } else walkX.snapTo(to)
            moving = false
            asking = true
        }
    }

    // 한참 아무도 안 만지면 반복 움직임(인형 숨쉬기 · 반짝이)을 쉬게 한다 — 만지는 순간 바로 다시 (#40).
    // 걷는 중 · 묻는 중 · 누름 반응 중 · 튜토리얼에서는 쉬지 않는다
    val quiet = rememberQuietRest()
    val still = quiet.resting && !tutorial && target == null && poke == null && !moving && !walkX.isRunning

    BoxWithConstraints(Modifier.fillMaxSize().background(Wool).wakeOnTouch(quiet)) {
        val g = Grid(maxWidth, maxHeight)
        // 벽지 · 바닥 · 러그 — ComfyUI 그림(room_bg). 없으면 펠트 도형으로 그린다
        RoomBackground(g) { Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 800f; val sy = size.height / 360f
            for (i in 0 until 13) drawRect(WoolCream.copy(alpha = 0.5f), Offset((20 + i * 64) * sx, 0f), Size(22 * sx, 262 * sy))
            drawRect(StageWood, Offset(0f, 262 * sy), Size(size.width, size.height - 262 * sy))
            drawRect(StageWoodDeep.copy(alpha = 0.5f), Offset(0f, 262 * sy), Size(size.width, 4 * sy))
            drawOval(Cheek.copy(alpha = 0.4f), Offset(196 * sx, 296 * sy), Size(250 * sx, 50 * sy))
        } }
        // 물건
        Thing.entries.forEach { t ->
            val hinted = if (tutorial) t == focus else idleHint && t == Thing.THEATER
            // 만들다 멈춘 이야기가 있는 물건 — 털실 뭉치 표시 (여기서 이어 갈 수 있다)
            val resumable = !tutorial && s.paused != null && modeOf(t.value) == s.mode
            Box(
                Modifier.offset(g.x(t.x), g.y(t.y)).width(g.dx(t.w)).height(g.dy(t.h))
                    .alpha(if (tutorial && t != focus) 0.35f else 1f)
                    .noRippleClickable { pick(t) }
            ) {
                AssetImage(t.art, Modifier.fillMaxSize().padding(bottom = 22.dp), contentScale = ContentScale.Fit) {
                    Box(Modifier.fillMaxSize().padding(bottom = 22.dp)) {
                        when (t) {
                            Thing.WINDOW -> WindowThing()
                            Thing.THEATER -> TheaterThing()
                            Thing.SOFA -> SofaThing()
                            Thing.SHELF -> ShelfThing()
                        }
                    }
                }
                // 이름표 — 이 물건이 어떤 모드인지 (아이콘 + 이름)
                NameTag(t, Modifier.align(Alignment.BottomCenter))
                if (hinted) Sparkle(Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp), still)
                // 어른이 부모 모드에서 이야기를 준비해 뒀으면 소파에 선물 표시 (09-29) — 털실(🧶)이 있으면 그쪽이 먼저다
                if (t == Thing.SOFA && !tutorial && !resumable && s.coopReady) Box(
                    // 소파가 화면 왼쪽 끝에 있어 왼쪽 위에 두면 잘린다 — 오른쪽 위에
                    Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(44.dp).felt(FeltMustard, CircleShape, lift = 4.dp, stitch = false),
                    contentAlignment = Alignment.Center,
                ) { AssetImage("ic_gift", Modifier.size(34.dp)) { Text("🎁", fontSize = 22.sp) } }
                if (resumable) Box(
                    Modifier.align(Alignment.TopStart).offset((-10).dp, (-12).dp).size(44.dp).felt(FeltMustard, CircleShape, lift = 4.dp, stitch = false),
                    contentAlignment = Alignment.Center,
                ) { Text("🧶", fontSize = 22.sp) }
            }
        }
        // 발자국
        paws.forEach { px -> Text("🐾", fontSize = 18.sp, modifier = Modifier.offset(g.x(px), g.y(318f)).alpha(0.55f)) }
        // 오또
        Box(Modifier.offset(g.x(walkX.value), g.y(if (target != null) 330f - OTTO - 6f else 330f - OTTO)).size(g.dy(OTTO)).semantics { contentDescription = "오또" }.noRippleClickable { pokeOtto() }) {
            // 09-29 뼈대로 움직이는 오또 인형 — 걸을 땐 다리 · 팔을 번갈아, 물을 땐 그 물건을 가리키고,
            // 권할 땐 손을 흔들고, 평소엔 숨 쉬며 꼬리를 흔든다. 보는 방향은 가는 쪽 · 가리키는 쪽
            val walking = moving || walkX.isRunning
            val cx = walkX.value + OTTO / 2
            val face = when {
                walking -> goingLeft
                target != null -> target!!.let { it.x + it.w / 2 } < cx
                focus != null -> focus.x + focus.w / 2 < cx
                else -> false
            }
            OttoPuppet(
                move = when {
                    walking -> OttoMove.WALK
                    target != null || (tutorial && !walking) -> OttoMove.POINT
                    poke != null -> poke!!.move
                    idleHint || tutorial -> OttoMove.WAVE
                    else -> OttoMove.IDLE
                },
                modifier = Modifier.fillMaxSize(),
                flip = face,
                paused = still,
            )
            // 리액션 말풍선 · 하트 — 오또 머리 위
            poke?.let { p ->
                key(pokeId) {
                    PokeBubble(p.line, Modifier.align(Alignment.TopCenter).offset(y = (-34).dp))
                    if (p.hearts) Hearts(Modifier.align(Alignment.TopCenter).offset(y = 10.dp))
                }
            }
        }
        // 오또가 권하는 말 · 튜토리얼 안내
        if (focus != null) {
            // 말풍선은 가리키는 물건 쪽 위에 — 화면 밖으로 나가지 않게. 몇 번째인지 점으로
            // 창문은 맨 위에 있어 위에 띄우면 가린다 — 오른쪽 옆에
            val bx = if (focus == Thing.WINDOW) focus.x + focus.w + 12f else (focus.x + focus.w / 2 - 190f).coerceIn(8f, 800f - 390f)
            Column(Modifier.offset(g.x(bx), g.y(6f)).width(g.dx(380f))) {
                SpeakBubble(TOUR[tour].second + "\n👆 눌러 봐!")
                Row(Modifier.padding(top = 6.dp, start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TOUR.indices.forEach { i ->
                        Box(Modifier.size(if (i == tour) 12.dp else 9.dp).clip(CircleShape).background(if (i <= tour) focus.color else FeltWhite.copy(alpha = 0.8f)))
                    }
                }
            }
            Pointer(Modifier.offset(g.x(focus.x + focus.w / 2 - 30f), g.y(focus.y + focus.h * 0.45f)))
        } else if (idleHint) SpeakBubble("무대를 눌러 봐!", Modifier.offset(g.x(240f), g.y(14f)))

        if (!tutorial) {
            // 왼쪽 위 부모 문 — 누르면 부모 비밀번호
            LockDoor(Modifier.padding(12.dp)) { d.send(Reply.Tapped("parent", "부모 모드")) }
            // 오른쪽 위 오늘 만들 수 있는 책
            StarWallet(s.dayStars, unlimited = !s.limitOn, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 64.dp))
        }

        if (asking) target?.let { t ->
            // 알림이 떠 있는 동안 **뒤의 방은 눌리지 않는다** — 누르면 이 막이 받고 아무 일도 하지 않는다.
            // 닫는 것은 알림의 ✕ 버튼뿐이다 (09-29 사용자 요청 — 알림만 터치 가능)
            Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.45f)).noRippleClickable { })
            fun done() { asking = false; target = null; paws.clear(); scope.launch { walkX.snapTo(HOME_X) } }
            if (s.paused != null && modeOf(t.value) == s.mode) {
                // 만들다 멈춘 이야기 — 이어서 할까, 새로 만들까 (09-29). 이어 가면 별을 다시 쓰지 않는다
                ConfirmDialog(
                    "🧶", "만들던 이야기 이어서 할까?",
                    onNo = { done(); d.send(Reply.Tapped(t.value, t.label)) },
                    onYes = { done(); d.send(Reply.Tapped("resume", "이어서")) },
                    modifier = Modifier.align(Alignment.Center),
                    no = "✨" to "새로", yes = "▶" to "이어서",
                    title = t.title, detail = "새로 만들면 만들던 이야기는 사라져요",
                    art = t.art, accent = t.color,
                )
            } else if (t == Thing.SOFA && !s.coopReady) {
                // 준비된 이야기가 없다 — 옛 흐름(어른이 띠를 읽고 묻기)으로 들어가지 않고 부모님께 부탁하라고 한다 (09-29 사용자 요청).
                // 버튼은 하나 — 아이가 막히지 않고 방으로 돌아간다
                ConfirmDialog(
                    "🎁", "아직 준비된 이야기가 없어!", title = t.title, art = t.art, accent = t.color,
                    detail = "부모님한테 이야기를 골라 달라고 부탁해 볼까?",
                    note = "부모님은 🔒 → 같이 만들기에서 골라요",
                    no = null, yes = "✓" to "알겠어!",
                    onNo = {}, onYes = { asking = false; target = null; scope.launch { walkX.animateTo(HOME_X, tween(500)); paws.clear() } },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else ConfirmDialog(
                t.icon, t.question, title = t.title, art = t.art, accent = t.color,
                detail = if (t == Thing.SOFA) s.coopPick?.let { "‘${it.name}’ 이야기 · 부모님이 골라 뒀어요" } ?: t.detail else t.detail,
                onNo = { asking = false; target = null; scope.launch { goingLeft = HOME_X < walkX.value; moving = true; walkX.animateTo(HOME_X, tween(700)); moving = false; paws.clear() } },
                onYes = { done(); d.send(Reply.Tapped(t.value, t.label)) },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 고른 모드의 책장이 꽉 찼다 — 흐름이 `shelfFull` 을 켠다. 들어가지 않고 알린다 (#80 · guidelines/3 §3-5).
        // 아이 화면에는 결제 · 늘리기 안내가 없다. 빼기는 부모 모드에서만
        if (!tutorial) s.shelfFull?.let { mode ->
            val t = Thing.entries.firstOrNull { modeOf(it.value) == mode } ?: Thing.THEATER
            Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.45f)).noRippleClickable { })
            ConfirmDialog(
                "📚", "책장이 꽉 찼어!", title = t.title, art = Thing.SHELF.art, accent = t.color,
                detail = "엄마·아빠랑 책장을 정리해 볼까?",
                note = "부모님이 책장 정리에서 한 권을 빼면 다시 만들 수 있어요",
                no = "✕" to "닫기", yes = "🔒" to "부모 모드로",
                onNo = { d.send(Reply.Tapped("shelf:close", "닫기")) },
                onYes = { d.send(Reply.Tapped("shelf:tidy", "부모 모드로")) },
                modifier = Modifier.align(Alignment.Center).testTag("shelf-full"),
            )
        }

        // 하루 한도에 닿으면 — 흐름이 `notice` 를 켠다
        if (s.notice != null && !tutorial) DailyLimit(d)
        // 서버를 켰는데 인터넷이 없으면 — 막다른 화면 없이 책장 + 어른용 다시 시도
        if (offline && !tutorial && s.notice == null) OfflineScreen(onShelf = { d.send(Reply.Tapped("shelf", "책장")) })
    }
}

// ── 방 물건 (펠트 도형) ─────────────────────────────────────

@Composable
private fun WindowThing() = Box(Modifier.fillMaxSize().felt(StageWood, RoundedCornerShape(20.dp))) {
    Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFCFE6EE))) {
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(38.dp).felt(FeltMustard, CircleShape, lift = 0.dp, stitch = false))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 14.dp).width(56.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(FeltWhite))
        Box(Modifier.align(Alignment.Center).width(4.dp).fillMaxHeight().background(StageWood))
    }
}

@Composable
private fun TheaterThing() = Box(Modifier.fillMaxSize().felt(StageWoodDeep, RoundedCornerShape(24.dp))) {
    Box(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 32.dp, bottom = 12.dp).felt(CurtainDeep, RoundedCornerShape(14.dp), lift = 0.dp, stitch = false)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.3f).felt(Curtain, RoundedCornerShape(12.dp), lift = 0.dp, stitch = false))
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.3f).felt(Curtain, RoundedCornerShape(12.dp), lift = 0.dp, stitch = false))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp).felt(StageWood, RoundedCornerShape(6.dp), lift = 0.dp, stitch = false))
    }
    Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).width(100.dp).height(24.dp).felt(FeltMustard, RoundedCornerShape(12.dp), lift = 2.dp, stitch = false), contentAlignment = Alignment.Center) {
        Text("★", fontSize = 14.sp, color = FeltWhite)
    }
}

@Composable
private fun SofaThing() = Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().padding(top = 16.dp).felt(FeltSky, RoundedCornerShape(30.dp)))
    Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(60.dp).height(46.dp).felt(Cheek, RoundedCornerShape(20.dp)))
        Box(Modifier.width(60.dp).height(46.dp).felt(FeltMustard, RoundedCornerShape(20.dp)))
    }
}

@Composable
private fun ShelfThing() = Box(Modifier.fillMaxSize().felt(StageWood, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 14.dp)) {
    val colors = listOf(FeltCoral, FeltTeal, FeltMustard, FeltSky, Cheek, Kitten)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        repeat(3) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                repeat(4) { k -> Box(Modifier.width(18.dp).height((44 - (k % 2) * 8).dp).clip(RoundedCornerShape(4.dp)).background(colors[(r * 4 + k) % 6])) }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(StageWoodDeep))
        }
    }
}

@Composable
private fun Sparkle(modifier: Modifier, still: Boolean = false) {
    // 쉬는 동안에는 반짝이지 않고 그 자리에 있기만 한다 (#40)
    if (still) { Text("✨", fontSize = 30.sp, modifier = modifier); return }
    val t = rememberInfiniteTransition(label = "sp")
    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Text("✨", fontSize = 30.sp, modifier = modifier.alpha(a).scale(0.8f + 0.3f * a))
}

@Composable
private fun Pointer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "pt")
    val s by t.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "s")
    Box(modifier.size(64.dp).scale(s).felt(FeltWhite, CircleShape, lift = 4.dp, stitch = false), contentAlignment = Alignment.Center) { Text("👆", fontSize = 32.sp) }
}

/**
 * 🔒 부모 문 — **한 번 누르면** 부모 비밀번호 화면이 뜬다.
 *
 * 전에는 2초 길게 눌러야 열렸는데, 누르는 동안 아무 표시가 없어 「자물쇠가 안 열린다」는 지적을 받았다(09-29).
 * 아이가 들어가는 것은 다음 화면의 부모 비밀번호가 막는다.
 */
@Composable
fun LockDoor(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    TopSlot(onOpen, modifier.size(TopSlot)) {
        FeltButton(WoolCream, onClick = onOpen, modifier = Modifier.size(TopFace), shape = CircleShape) { Text("🔒", fontSize = 19.sp) }
    }
}

/**
 * 왼쪽 위 시스템 버튼 자리 (10-01) — 보이는 펠트는 [TopFace](48dp)로 줄여 덜 답답하게, 누르는 자리는 [TopSlot](56dp) 그대로.
 * 보이는 원 바깥 4dp 테두리도 눌린다 — 크기를 줄여도 누르기는 어려워지지 않는다
 */
@Composable
private fun TopSlot(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val TopSlot = 56.dp
private val TopFace = 48.dp

/** 오또를 눌렀을 때 머리 위에 톡 튀어나오는 말 */
@Composable
private fun PokeBubble(text: String, modifier: Modifier) {
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { if (!motionFrozen) pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f)) else pop.snapTo(1f) }
    Box(
        modifier.wrapContentSize(unbounded = true).scale(pop.value)
            .felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 4.dp, stitch = true)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) { Text(text, fontSize = 16.sp, color = InkBrown, maxLines = 1) }
}

/** 하트가 여러 개 떠오르며 옅어진다 */
@Composable
private fun Hearts(modifier: Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!motionFrozen) rise.animateTo(1f, tween(1500)) else rise.snapTo(0.4f) }
    Box(modifier.size(120.dp, 90.dp)) {
        listOf(-40 to 0f, 0 to 0.15f, 36 to 0.3f, -18 to 0.45f).forEach { (dx, delay) ->
            val k = ((rise.value - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (k > 0f) Text(
                "💕", fontSize = (18 + 8 * k).sp,
                modifier = Modifier.align(Alignment.BottomCenter).offset(x = dx.dp, y = (-70 * k).dp).alpha(1f - k * 0.8f),
            )
        }
    }
}

/** 방 물건 아래 이름표 — 아이콘 + 모드 이름 (글을 못 읽는 아이도 아이콘으로 안다) */
@Composable
private fun NameTag(t: Thing, modifier: Modifier) {
    // 09-29 사용자 — 「모드 설명 텍스트 · 이모티콘 UI 퀄리티를 올려 줘」. 이모지 대신 ComfyUI 펠트 아이콘을 모드 색 원에 넣고,
    // 이름 아래에 무엇을 하는 곳인지 한 줄. 글을 못 읽는 아이는 아이콘 · 색으로, 어른은 글로 안다
    Row(
        modifier.offset(y = 6.dp).felt(FeltWhite, RoundedCornerShape(Radius.Round), lift = 4.dp, stitch = true)
            .border(2.dp, t.color.copy(alpha = 0.55f), RoundedCornerShape(Radius.Round))
            .padding(start = 4.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).felt(t.color, CircleShape, lift = 1.dp, stitch = false), contentAlignment = Alignment.Center) {
            AssetImage(t.badge, Modifier.size(30.dp)) { Text(t.icon, fontSize = 16.sp) }
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(t.title, fontSize = 15.sp, color = InkBrown, maxLines = 1, lineHeight = 17.sp)
            ParentText { Text(t.sub, fontSize = 10.sp, color = InkSoft, maxLines = 1, lineHeight = 12.sp) }
        }
    }
}

/** 확인 창 — 무엇으로 가는지 그림 · 오또 목소리 · ✕ 아니 / ✓ 응! 큰 버튼 (`.pen` confirm_dialog) */
@Composable
fun ConfirmDialog(
    icon: String, question: String, onNo: () -> Unit, onYes: () -> Unit, modifier: Modifier = Modifier,
    no: Pair<String, String>? = "✕" to "아니", yes: Pair<String, String> = "✓" to "응!",
    title: String? = null, detail: String? = null,
    /** detail 아래 더 작은 한 줄 — 어른에게 하는 말 (09-29) */
    note: String? = null,
    /** 창 위에 띄울 그림(방 물건 · ComfyUI). 없으면 [icon] 이모지 원 */
    art: String? = null,
    /** 모드 색 — 테두리 · 제목 띠 */
    accent: Color = FeltMustard,
) {
    val top = if (art != null) 70.dp else 34.dp
    // 창 안을 눌러도 뒤로 새지 않게 — 버튼 말고는 아무 일도 없다
    Box(modifier.width(440.dp).height((if (detail != null) 262.dp else 216.dp) + (if (note != null) 16.dp else 0.dp) + top).noRippleClickable { }) {
        Box(Modifier.fillMaxSize().padding(top = top).felt(Wool, RoundedCornerShape(32.dp), lift = 10.dp).border(5.dp, accent, RoundedCornerShape(32.dp))) {
            Column(Modifier.fillMaxSize().padding(top = if (art != null) 58.dp else 44.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (title != null) Box(Modifier.felt(accent, RoundedCornerShape(Radius.Round), lift = 2.dp, stitch = false).padding(horizontal = 14.dp, vertical = 3.dp)) {
                    Text(title, fontSize = 14.sp, color = FeltWhite)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊", fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Text(question, fontSize = 23.sp, color = InkBrown, maxLines = 1)
                }
                if (detail != null) ParentText { Text(detail, fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp), maxLines = 1) }
                if (note != null) ParentText { Text(note, fontSize = 11.sp, color = InkSoft.copy(alpha = 0.8f), maxLines = 1) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (no != null) FeltButton(WoolCream, onClick = onNo, modifier = Modifier.width(120.dp).height(76.dp), shape = RoundedCornerShape(26.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(no.first, fontSize = 26.sp, color = InkBrown); Text(no.second, fontSize = 16.sp, color = InkBrown) }
                    }
                    FeltButton(FeltTeal, onClick = onYes, modifier = Modifier.width(120.dp).height(76.dp), shape = RoundedCornerShape(26.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(yes.first, fontSize = 26.sp, color = FeltWhite); Text(yes.second, fontSize = 16.sp, color = FeltWhite) }
                    }
                }
            }
        }
        if (art != null) AssetImage(art, Modifier.align(Alignment.TopCenter).size(130.dp)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).size(76.dp).felt(accent, CircleShape, lift = 5.dp), contentAlignment = Alignment.Center) { Text(icon, fontSize = 36.sp) }
        }
        else Box(Modifier.align(Alignment.TopCenter).size(76.dp).felt(FeltCoral, CircleShape, lift = 5.dp).border(4.dp, FeltWhite.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 36.sp)
        }
    }
}

// ── ⑮ 하루 한도에 닿으면 — 밤 장면 · 하품하는 오또 · 구석 작은 어른 버튼 ──────────────

@Composable
private fun DailyLimit(d: Director) {
    var adult by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF3B4A6B)).noRippleClickable { }) {
        AssetImage("night_bg", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { Canvas(Modifier.fillMaxSize()) {
            val rnd = java.util.Random(3)
            repeat(12) { drawCircle(FeltMustard.copy(alpha = 0.8f), 2.5f + rnd.nextFloat() * 3f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.45f)) }
            drawCircle(Color(0xFFF7E08A), size.height * 0.12f, Offset(size.width * 0.83f, size.height * 0.2f))
            drawRect(StageWoodDeep, Offset(0f, size.height * 0.8f), Size(size.width, size.height * 0.2f))
        } }
        Otto(Pose.YAWN, Modifier.align(Alignment.Center).padding(top = 30.dp).size(240.dp))
        SpeakBubble("오늘은 여기까지! 내일 또 만나", Modifier.align(Alignment.CenterStart).padding(start = 36.dp, bottom = 60.dp))
        // 아이가 할 일 하나 — 지난 책 보기 (막다른 화면이 없게)
        FeltButton(FeltCoral, onClick = { d.send(Reply.Tapped("notice:shelf", "책장")) }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp).size(120.dp), shape = RoundedCornerShape(30.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📚", fontSize = 40.sp); Text("책장", fontSize = 18.sp, color = FeltWhite) }
        }
        // 어른 경로 하나 — 구석 · 작게 → 부모 비밀번호 → 한 권 더 (Lingokids · YouTube Kids)
        Row(
            Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(RoundedCornerShape(20.dp)).background(FeltWhite.copy(alpha = 0.15f)).clickable { adult = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("🔒", fontSize = 13.sp); Spacer(Modifier.width(6.dp)); ParentText { Text("어른", fontSize = 13.sp, color = FeltWhite) } }
        if (adult) Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.5f)).noRippleClickable { }, contentAlignment = Alignment.Center) {
            ParentText {
                Column(Modifier.width(560.dp).felt(Wool, RoundedCornerShape(28.dp), lift = 10.dp, texture = false).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘 한 권 더 만들기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkBrown, modifier = Modifier.weight(1f))
                        Text("닫기", fontSize = 14.sp, color = InkSoft, modifier = Modifier.clickable { adult = false }.padding(8.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    if (Shell.hasPin) PinPad("부모 비밀번호", "한 권 더 만들려면 비밀번호를 넣어 주세요", onDone = { ok -> Shell.checkPin(ok).also { if (it) {
                        adult = false
                        d.s.usedToday = (d.s.usedToday - 1).coerceAtLeast(0)
                        d.send(Reply.Tapped("notice:ok", "한 권 더"))
                    } } })
                    else YearPad(onPass = {
                        adult = false
                        // 한 권 더 — 오늘 쓴 수를 하나 되돌린다(하루 한도 자체는 부모 설정에서 바꾼다)
                        d.s.usedToday = (d.s.usedToday - 1).coerceAtLeast(0)
                        d.send(Reply.Tapped("notice:ok", "한 권 더"))
                    })
                }
            }
        }
    }
}

// ── 예외 · 오프라인 — 아이용 하나(책장) + 어른용 작게 ─────────────────────────

@Composable
private fun OfflineScreen(onShelf: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Wool)) {
        Otto(Pose.THINK, Modifier.align(Alignment.CenterStart).padding(start = 70.dp, top = 40.dp).size(220.dp))
        SpeakBubble("새 책은 조금 뒤에 만들자!", Modifier.padding(start = 40.dp, top = 20.dp), color = WoolCream)
        FeltButton(FeltCoral, onClick = onShelf, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 120.dp).width(220.dp).height(200.dp), shape = RoundedCornerShape(36.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📚", fontSize = 64.sp); Spacer(Modifier.height(8.dp)); Text("책장 가기", fontSize = 22.sp, color = FeltWhite) }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(RoundedCornerShape(20.dp)).background(InkBrown.copy(alpha = 0.1f)).clickable { tick++ }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("📶", fontSize = 13.sp); Spacer(Modifier.width(6.dp)); ParentText { Text("다시 시도", fontSize = 13.sp, color = InkSoft) } }
        // 다시 시도 — 다시 그리면 online() 을 다시 본다
        LaunchedEffect(tick) { online(ctx) }
    }
}

/** 인터넷에 붙어 있나 — 서버를 켰을 때만 본다 */
fun online(ctx: Context): Boolean = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(true)

/**
 * 이야기 도중 **왼쪽 위 = 시스템** (디자인 시스템 「공통 자리」 · Toca · Sago) — 🏠 방으로 · 🔒 부모 문(2초).
 * 🏠 는 바로 나가지 않고 「방으로 갈까?」를 묻는다 — 아이가 잘못 눌러 만들던 책을 잃지 않게
 */
@Composable
fun KidTopBar(d: Director, modifier: Modifier = Modifier, lock: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // 「방으로 갈까?」 확인은 화면 전체를 덮어야 해서 앱 틀(OttoShell)이 그린다
        // 보이는 크기 48dp · 누르는 자리 56dp — 자리 사이 4dp 라 보이는 버튼 사이는 12dp (10-01 — 56dp 원이 바짝 붙어 답답했다)
        TopSlot({ Shell.askHome = true }, Modifier.size(TopSlot)) {
            FeltButton(WoolCream, onClick = { Shell.askHome = true }, modifier = Modifier.size(TopFace), shape = CircleShape) { Text("🏠", fontSize = 21.sp) }
        }
        // 🔒 부모 문 — 그림일기 화면에는 없다(판을 넓게 · 10-05 진웅). 방 · 다른 모드는 그대로
        if (lock) {
            Spacer(Modifier.width(4.dp))
            LockDoor { d.openParent() }
        }
        // 같이 만들기 중에만 — 부모 「그만하기」 (#36). 누르면 확인 창을 한 번 더 거친다(아이가 잘못 누르지 않게).
        // 10-01 — 옆 🏠 · 🔒 와 같은 펠트 버튼(크림 펠트 · 바느질 · 같은 높이 · 누르면 꾹)에 ComfyUI 펠트 손바닥 그림.
        // 글씨는 어른용 고딕 그대로 — 어른이 누르는 버튼이다
        val s = d.s
        // 그리기 단계에도 둔다 — 이야기 칸이 다 차 끝난 이유가 정해진 뒤라 전에는 사라졌다 (#98)
        if (s.isCoop && s.scene == com.example.finalproject_demo.demo.Scene.DIARY &&
            (s.endReason == null || s.stage is com.example.finalproject_demo.demo.Stage.DrawPad)) {
            Spacer(Modifier.width(4.dp))
            TopSlot({ Shell.askStop = true }, Modifier.height(TopSlot).padding(horizontal = 4.dp)) {
                FeltButton(WoolCream, onClick = { Shell.askStop = true }, modifier = Modifier.height(TopFace), shape = RoundedCornerShape(Radius.Round)) {
                    Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssetImage("ic_parent_stop", Modifier.size(32.dp)) { Text("✋", fontSize = 20.sp) }
                        Spacer(Modifier.width(5.dp))
                        ParentText { Text("그만하기", fontSize = 14.sp, color = InkBrown, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
