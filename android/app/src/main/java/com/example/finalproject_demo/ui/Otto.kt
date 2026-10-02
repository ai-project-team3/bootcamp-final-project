package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.ui.shell.Narration
import com.example.finalproject_demo.ui.shell.NarrationMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * 오또(마스코트) — 얼굴 · 표정 · 나레이션 칸 · 녹음 버튼 (09-29 · Chrome.kt 에서 옮김)
 *
 * 얼굴 테두리 색으로 **차례**를 알린다 — 말하는 중 분홍 [Cheek] · 듣는 중 청록 [FeltTeal] · 생각하는 중 겨자 [FeltMustard].
 * 말할 때도 들을 때처럼 테두리 색 파동이 퍼진다(09-29 사용자 요청 — 전에는 들을 때만 파동이 있었다).
 * 파동은 얼굴과 **같은 상자**에서 같이 움직여, 얼굴이 기울거나 뛰어도 중심이 어긋나지 않는다.
 * (10-01 에 잠깐 껐다가 사용자 요청으로 그대로 되살렸다. 옛 모양이 번쩍인 원인은 띠 안 🔊 가 붙었다 떨어지며 띠 길이가 바뀐 것이었다)
 */

enum class OttoState(val ring: Color, val face: String) {
    TALK(Cheek, "otto_face_talk"),
    LISTEN(FeltTeal, "otto_face_listen"),
    THINK(FeltMustard, "otto_face_think"),
    WAIT(FeltTeal, "otto_face_talk"),
    IDLE(WoolCream, "otto_face_talk"),
}

/**
 * 오또 표정 — ComfyUI(FLUX Kontext)로 지금 얼굴의 **표정만** 바꾼 그림 (tools/gen_faces.py).
 * 그림이 없으면 상태 얼굴을 그대로 쓴다. 표정마다 움직임도 다르다([OttoFace]).
 */
enum class Expr(val face: String?) {
    NONE(null),
    /** 기쁨 — 잘했어 · 좋아 · 해냈다. 통통 뛴다 */
    HAPPY("otto_face_happy"),
    /** 깜짝 — 앗 · 어? · 우와. 움찔 커졌다 돌아온다 */
    SURPRISED("otto_face_surprised"),
    /** 속상 — 아팠겠다 · 슬펐구나 · 무서웠지. 천천히 고개를 떨군다 */
    SAD("otto_face_sad"),
    /** 궁금 — 물어볼 때. 고개를 이쪽저쪽 갸웃 */
    CURIOUS("otto_face_curious"),
    /** 뿌듯 — 완성 · 책장에 꽂혔어. 가슴을 펴듯 부풀었다 줄었다 */
    PROUD("otto_face_proud"),
}

private val SAD_WORDS = listOf("속상", "슬퍼", "슬펐", "아팠", "아프", "무서", "걱정", "미안", "울었", "울고", "힘들", "외로", "심심했")
private val HAPPY_WORDS = listOf("잘했", "좋아", "멋지", "멋진", "최고", "해냈", "신나", "재밌", "재미있", "고마워", "대단", "훌륭", "좋은 생각")
private val PROUD_WORDS = listOf("책장에 꽂", "완성", "다 만들었", "짜잔", "책이 됐", "책이 되었")
private val SURPRISE_START = Regex("^(앗|어\\?|어!|우와|와!|쉿|헉|깜짝)")
private val ASK_WORDS = listOf("뭐", "어디", "왜", "누구", "어떻게", "어떤", "무슨", "말해 줄래", "알려 줄래")

/** 오또가 하는 말 · 감독이 알린 기분으로 표정을 고른다 */
fun exprFor(text: String, mood: Mood): Expr {
    val t = text.trim()
    return when {
        mood == Mood.SURPRISED || SURPRISE_START.containsMatchIn(t) -> Expr.SURPRISED
        mood == Mood.CHEER -> Expr.HAPPY
        PROUD_WORDS.any { it in t } -> Expr.PROUD
        SAD_WORDS.any { it in t } -> Expr.SAD
        HAPPY_WORDS.any { it in t } -> Expr.HAPPY
        t.endsWith("?") || ASK_WORDS.any { it in t } -> Expr.CURIOUS
        else -> Expr.NONE
    }
}

/**
 * 오또 얼굴 — 상태 색 틀 안에서 상태 · 표정마다 다르게 움직인다.
 *
 * @param burst   한 번 터지는 반응. [Mood.CHEER] 면 폴짝 뛰며 반짝임, [Mood.SURPRISED] 면 움찔 커졌다 부르르
 * @param burstId 이 번호가 바뀔 때마다 [burst] 가 다시 터진다 (같은 반응이 연달아 와도)
 * @param expr    표정 — 말하는 중 · 가만있을 때만 얼굴 그림을 바꾼다(듣는 중 · 생각하는 중은 그 얼굴 그대로)
 * @param pulse   말하는 중 · 듣는 중 · 기다리는 중이면 테두리 색 파동을 얼굴 둘레에 퍼뜨린다
 */
@Composable
fun OttoFace(
    state: OttoState,
    modifier: Modifier = Modifier,
    burst: Mood = Mood.NONE,
    burstId: Int = 0,
    expr: Expr = Expr.NONE,
    pulse: Boolean = false,
) {
    val inf = rememberInfiniteTransition(label = "otto")
    val hop by inf.animateFloat(0f, -5f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "hop")
    val bounce by inf.animateFloat(0f, -9f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    val lean by inf.animateFloat(-3f, 3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "lean")
    val nod by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "nod")
    val sway by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val breathe by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val jolt by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(160), RepeatMode.Reverse), label = "jolt")
    val dots by inf.animateFloat(0f, 3f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "dots")
    val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")

    // 한 번 터지는 반응 — 뛰어오름(jump) · 눌림과 늘어남(sx · sy) · 떨림(shake) · 머리 위 「!」(mark)
    val jump = remember { Animatable(0f) }
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    var bursting by remember { mutableStateOf(Mood.NONE) }
    val field = rememberParticleField()
    val density = LocalDensity.current
    var faceSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(burstId) {
        if (motionFrozen || (burst != Mood.CHEER && burst != Mood.SURPRISED)) return@LaunchedEffect
        bursting = burst
        val px = with(density) { 1.dp.toPx() }
        val bouncy = spring<Float>(Spring.DampingRatioMediumBouncy)
        try {
            coroutineScope {
                if (burst == Mood.CHEER) {
                    launch { sy.animateTo(0.82f, tween(90)); sy.animateTo(1.12f, tween(160)); sy.animateTo(0.9f, tween(140)); sy.animateTo(1f, bouncy) }
                    launch { sx.animateTo(1.12f, tween(90)); sx.animateTo(0.92f, tween(160)); sx.animateTo(1.08f, tween(140)); sx.animateTo(1f, bouncy) }
                    launch {
                        delay(90)
                        field.burst(faceSize.width / 2, faceSize.height * 0.35f, faceSize.width * 0.55f, n = 16)
                        jump.animateTo(-18f * px, tween(200, easing = FastOutSlowInEasing))
                        jump.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                } else {
                    launch { sx.animateTo(1.18f, tween(80)); sx.animateTo(1f, bouncy) }
                    launch { sy.animateTo(1.18f, tween(80)); sy.animateTo(1f, bouncy) }
                    launch { jump.animateTo(-8f * px, tween(80)); jump.animateTo(0f, bouncy) }
                    launch { repeat(3) { shake.animateTo(7f, tween(45)); shake.animateTo(-7f, tween(45)) }; shake.animateTo(0f, tween(45)) }
                    launch { mark.animateTo(1f, bouncy); delay(500); mark.animateTo(0f, tween(180)) }
                }
            }
        } finally {
            bursting = Mood.NONE
            jump.snapTo(0f); sx.snapTo(1f); sy.snapTo(1f); shake.snapTo(0f); mark.snapTo(0f)
        }
    }

    // 얼굴 그림 — 한 번 터지는 반응 > 듣는 중 · 생각 중 얼굴 > 표정 > 상태 얼굴
    val talkOrIdle = state == OttoState.TALK || state == OttoState.IDLE || state == OttoState.WAIT
    val shownExpr = when {
        bursting == Mood.CHEER -> Expr.HAPPY
        bursting == Mood.SURPRISED -> Expr.SURPRISED
        talkOrIdle -> expr
        else -> Expr.NONE
    }
    val face = shownExpr.face?.takeIf { assetId(it) != 0 } ?: if (bursting != Mood.NONE) OttoState.LISTEN.face else state.face

    // 계속 움직임 — 표정이 있으면 표정의 움직임, 없으면 상태의 움직임
    var baseY = 0f
    var baseTilt = 0f
    var baseScale = 1f
    when (shownExpr) {
        Expr.HAPPY -> { baseY = bounce; baseTilt = lean * 1.3f }
        Expr.SURPRISED -> baseScale = jolt.coerceAtMost(1.06f)
        Expr.SAD -> { baseY = 2f + nod * 2f; baseTilt = -4f - nod * 2f }
        Expr.CURIOUS -> baseTilt = sway
        Expr.PROUD -> baseScale = breathe
        Expr.NONE -> {
            baseY = when (state) { OttoState.TALK -> hop; OttoState.WAIT -> nod * 3f; else -> 0f }
            baseTilt = when (state) {
                OttoState.LISTEN -> 0f        // 듣는 동안 기울이지 않는다 — 청록 파동이 얼굴 정중앙에서 퍼지게
                OttoState.TALK -> 0f
                OttoState.WAIT -> nod * 5f
                OttoState.THINK -> sway * 0.7f
                OttoState.IDLE -> lean
            }
        }
    }
    val pulsing = pulse && (state == OttoState.TALK || state == OttoState.LISTEN || state == OttoState.WAIT)

    Box(modifier) {
        // 얼굴과 파동을 **한 상자**에 — 뛰고 기울어도 파동이 얼굴 정중앙에 붙어 다닌다
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (baseY * density.density + jump.value).roundToInt()) }
                .graphicsLayer {
                    scaleX = sx.value * baseScale; scaleY = sy.value * baseScale
                    rotationZ = baseTilt + shake.value
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (pulsing) {
                // 두 겹 파동 — 번갈아 커지며 옅어진다. 색은 지금 상태(말함 분홍 · 들음 청록)
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    for (k in 0..1) {
                        val f = (wave + k * 0.5f) % 1f
                        drawCircle(state.ring.copy(alpha = 0.5f * (1f - f)), radius = r * (1f + 0.17f * f))
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(Felt.ShadowY, CircleShape, ambientColor = Felt.ShadowColor, spotColor = Felt.ShadowColor)
                    .clip(CircleShape)
                    .background(state.ring)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(WoolCream),
            ) { AssetImage(face, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { ArtView(Art.Mascot, Modifier.fillMaxSize()) } }
        }

        // 머리 위 「…」 — 기다릴 때와 생각할 때. 점이 하나씩 차오른다
        if (state == OttoState.WAIT || state == OttoState.THINK) {
            Text(
                ".".repeat(dots.toInt().coerceIn(0, 2) + 1).padEnd(3, ' '),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkBrown,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-8).dp)
                    .background(WoolCream, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp),
            )
        }
        // 놀랐을 때 「!」
        if (mark.value > 0.01f) {
            Text(
                "!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = FeltCoral,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-12).dp)
                    .graphicsLayer { scaleX = mark.value; scaleY = mark.value; alpha = mark.value.coerceIn(0f, 1f) },
            )
        }
        // 반짝임은 얼굴 틀 밖까지 퍼지게 — 자르지 않는 겉 상자에 그린다
        Canvas(Modifier.fillMaxSize()) {
            faceSize = size
            field.tick
            field.draw(this)
        }
    }
}

/**
 * 오또 말풍선 → **나레이션 칸** (화면 아래 전체 폭). 말할 때마다 톡 튀어나오고 글자가 한 자씩 써진다.
 * 녹음 버튼 · 그리기 버튼은 칸 오른쪽 끝에 따로 선다 (09-30 사용자 요청).
 */
@Composable
fun MascotBubble(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    val id = s.lineId
    val text = s.line
    // 처음부터 「튀어나오기 전」 상태로 — 전에는 다 보인 상태(투명도 1 · 글자 전부)로 한 프레임 그렸다가
    // 아래 LaunchedEffect 가 되돌려서, 나레이션이 뜰 때마다 문장 전체가 번쩍 보였다 사라졌다 (09-30 사용자 제보)
    val pop = remember { Animatable(0.6f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(id) {
        shown = 0
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(id, text) {
        while (shown < text.length) {
            delay(28)
            shown++
        }
    }
    val controls = s.micEnabled || s.drawEnabled
    if (text.isBlank() && !controls) return
    val talking = shown < text.length
    val state = when {
        s.micOn -> OttoState.LISTEN
        talking -> OttoState.TALK
        s.stage is Stage.Making -> OttoState.THINK
        // 말을 다 했고 마이크를 쓸 수 있으면 아이 답을 기다리는 중이다 — 불빛을 켠다 (10-01 사용자 요청)
        s.mood == Mood.WAITING || s.micEnabled -> OttoState.WAIT
        else -> OttoState.IDLE
    }
    val mode = when {
        s.isCoop -> NarrationMode.COOP
        s.isDiary -> NarrationMode.DIARY
        else -> NarrationMode.STORY
    }
    // 오또가 할 말이 없는 차례(같이 만들기에서 어른이 묻는 차례 등)에도 칸이 비지 않게 — 아이에게 차례를 알린다
    val full = if (text.isBlank()) "네 차례야! 마이크를 누르고 말해 봐" else text
    val line = if (text.isBlank()) full else text.take(shown)
    Narration(
        mode = mode, state = state, line = line, fullLine = full,
        // 투명도는 영역 크기만 한 버퍼에 그려 합친다 — 그래서 버퍼 **위쪽에 여유(Headroom)** 를 넣는다. 전에는 새 말과 함께
        // 터지는 점프 · 파동 · 「!」가 칸 위로 나가면서 잘렸다 (10-01 사용자 제보). 여유 칸은 투명하고 누름을 받지 않는다.
        // (버퍼 없이 그리는 ModulateAlpha 는 겹친 층이 비쳐 띠가 분홍빛으로 물들어서 쓰지 않는다)
        modifier = modifier.graphicsLayer { alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }.padding(top = BubbleHeadroom),
        speaker = if (s.speaker != "마스코트") s.speaker else null,
        burst = s.mood, burstId = s.moodId,
        expr = exprFor(text, s.mood),
        trailing = if (controls) { {
            if (s.micEnabled) TurnNavButtons(d)
            if (s.drawEnabled) DrawButton(d)
            if (s.micEnabled) MicButton(d, size = 96.dp)  // 나레이션 줄 오른쪽 — 얼굴 자리(100dp)와 무게를 맞춘다
        } } else null,
    )
}

/**
 * 되돌리기 · 앞으로 가기 (10-02 조장 · #87) — 잘못 알아들은 답을 무른다. 아이 차례(마이크가 켜질 수 있을 때)에만,
 * 무를 차례가 있을 때만 보인다. 누르면 말하던 마스코트도 끊긴다(`Director.send`). 흐름은 `demo/TurnHistory`.
 */
@Composable
fun TurnNavButtons(d: Director) {
    val s = d.s
    if (!s.canUndo && !s.canRedo) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (s.canUndo) FeltButton(
            WoolCream,
            onClick = { d.send(Reply.Tapped(com.example.finalproject_demo.demo.TurnHistory.UNDO, "되돌리기")) },
            modifier = Modifier.padding(end = 8.dp).size(56.dp), shape = CircleShape,
        ) { Text("↩", fontSize = 26.sp, color = InkBrown) }
        if (s.canRedo) FeltButton(
            WoolCream,
            onClick = { d.send(Reply.Tapped(com.example.finalproject_demo.demo.TurnHistory.REDO, "앞으로")) },
            modifier = Modifier.padding(end = 8.dp).size(56.dp), shape = CircleShape,
        ) { Text("↪", fontSize = 26.sp, color = InkBrown) }
    }
}

/** 나레이션 위 여유 — 오또 점프(18dp) · 파동 · 「!」가 투명도 버퍼 안에 들어오게 */
private val BubbleHeadroom = 40.dp

/**
 * 나레이션 칸이 없는 화면(첫 화면 · 책 · 부모)에서만 쓰는 오른쪽 아래 버튼.
 * ➡️ 넘기기는 없앴다 (09-29 사용자 요청 — 이야기 짓기에서 화살표 · 넘기는 기능 없애기). 시연 서랍의 「넘기기」는 그대로다
 */
@Composable
fun FloatingControls(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (s.drawEnabled) DrawButton(d, Modifier.padding(end = 10.dp))
        if (s.micEnabled) MicButton(d)
    }
}

@Composable
private fun DrawButton(d: Director, modifier: Modifier = Modifier) {
    FeltButton(
        WoolCream,
        onClick = { d.send(Reply.Tapped("draw", "직접 그리기")) },
        modifier = modifier.size(64.dp),
        shape = CircleShape,
    ) { AssetImage("gift_crayon", Modifier.size(44.dp)) { Text("🖍️", fontSize = 28.sp) } }
}

/**
 * **녹음 버튼** (09-29 다시 그림) — 청록 펠트 원 + 바느질 고리 + ComfyUI 펠트 마이크(feat_talk).
 *   가만있을 때 — 숨 쉬듯 살짝 커졌다 작아진다(누를 차례라는 신호)
 *   녹음 중    — 코랄 원 + 흰 멈춤 네모, 둘레로 코랄 물결 두 겹이 퍼진다
 * 처음 누를 때는 왜 마이크를 쓰는지 먼저 알린다(출시 체크리스트 §2) — 한 번 보면 다시 안 뜬다
 */
@Composable
fun MicButton(d: Director, size: Dp = 76.dp) {
    val s = d.s
    var askMic by remember { mutableStateOf(false) }
    var askPermission by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "mic")
    val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "wave")
    val breathe by inf.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val on = s.micOn
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (on) Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            for (k in 0..1) {
                val f = (wave + k * 0.5f) % 1f
                drawCircle(FeltCoral.copy(alpha = 0.55f * (1f - f)), radius = r * (0.82f + 0.4f * f), style = Stroke(4.dp.toPx()))
            }
        }
        FeltButton(
            if (on) FeltCoral else FeltTeal,
            onClick = { if (ConsentStore.micNoticeShown) d.toggleMic() else askMic = true },
            modifier = Modifier.size(size * 0.86f).graphicsLayer { val k = if (on) 1f else breathe; scaleX = k; scaleY = k },
            shape = CircleShape,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 바느질 고리 — 펠트 가장자리를 꿰맨 흰 점선
                Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                    drawCircle(
                        FeltWhite.copy(alpha = 0.75f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
                    )
                }
                if (on) Box(Modifier.size(size * 0.28f).clip(RoundedCornerShape(7.dp)).background(FeltWhite))
                else AssetImage("feat_talk", Modifier.size(size * 0.56f)) { ArtView(Art.Img("ic_mic", Art.Emoji("🎤")), Modifier.size(size * 0.5f)) }
            }
        }
    }
    if (askMic) {
        MicNoticeSheet {
            askMic = false
            askPermission = true
            d.toggleMic()
        }
    }
    if (askPermission) MicPermissionRequest { askPermission = false }
}
