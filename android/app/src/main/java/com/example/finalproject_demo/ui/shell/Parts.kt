package com.example.finalproject_demo.ui.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.finalproject_demo.net.AuthProvider
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.FeltButton
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.WoolCream
import com.example.finalproject_demo.ui.felt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/*
 * 틀에서 같이 쓰는 부품 — 디자인 시스템 `.pen` 의 ob_frame · pbtn · checkbox · 소셜 버튼 · 태어난 해 입력 (09-29)
 */

/** 오또 자세 그림 — `res/drawable/otto_pose_*` (ComfyUI 로 만든 9가지 · design/assets/poses) */
enum class Pose(val file: String) {
    WAVE("otto_pose_wave"), PHONE("otto_pose_phone"), POINT("otto_pose_point"), WALK("otto_pose_walk"),
    TALK("otto_pose_talk"), LISTEN("otto_pose_listen"), THINK("otto_pose_think"), YAWN("otto_pose_yawn"), CALL("otto_pose_call"),
    /** 09-30 #38 그림일기 D3 — 아이 옆에 엎드려 같이 쓰는 오또: 크레용으로 그리기 · 공책에 쓰기 · 고개 들고 보기 */
    LIE_DRAW("otto_pose_lie_draw"), LIE_WRITE("otto_pose_lie_write"), LIE_LOOK("otto_pose_lie_look"),
}

@Composable
fun Otto(pose: Pose, modifier: Modifier = Modifier, bob: Boolean = true) {
    val t = rememberInfiniteTransition(label = "otto")
    val y by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "bob")
    AssetImage(pose.file, modifier.then(if (bob) Modifier.padding(top = (4 + y).dp.coerceAtLeast(0.dp)) else Modifier))
}

/** 부모용 버튼 — 펠트의 ① 단색 · ③ 바느질만 (결 · 큰 그림자 없음). 주 버튼 코랄 · 보조 크림 */
@Composable
fun PBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, height: Dp = 52.dp) {
    val bg = when {
        !enabled -> InkSoft.copy(alpha = 0.25f)
        primary -> FeltCoral
        else -> WoolCream
    }
    Box(
        modifier
            .height(height)
            .felt(bg, RoundedCornerShape(Radius.Round), lift = if (primary && enabled) 3.dp else 0.dp, texture = false)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (primary && enabled) FeltWhite else InkBrown) }
}

/** 체크 상자 한 줄 — 누르는 곳은 줄 전체 (부모 최소 48dp) */
@Composable
fun CheckRow(checked: Boolean, text: String, onToggle: () -> Unit, big: Boolean = false, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(if (big) 48.dp else 38.dp).clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (big) 26.dp else 22.dp).clip(RoundedCornerShape(7.dp))
                .background(if (checked) FeltTeal else FeltWhite)
                .border(1.5.dp, if (checked) FeltTeal else InkBrown.copy(alpha = 0.25f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Text("✓", fontSize = if (big) 17.sp else 14.sp, color = FeltWhite, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = if (big) 17.sp else 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, color = InkBrown, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = InkSoft, modifier = Modifier.clickable { onTrailing?.invoke() }.padding(8.dp))
    }
}

/**
 * 소셜 로그인 버튼 — 각 사 규격(디자인 시스템 「로그인 버튼 규격」 · 10-05 실제 SDK 연결).
 *  - 카카오: 노랑 #FEE500 · 검은 말풍선 심볼 · 글자 85% 검정
 *  - 네이버: 초록 #03C75A · 흰 N
 *  - Google: 흰 바탕 · 회색 테두리 #747775 · 네 색 G
 * 심볼은 각 사 배포 이미지의 모양을 코드로 그린 것이다(이미지 파일 없이 어느 화면 밀도에서도 또렷하게).
 * [busy] 면 「연결 중…」, [enabled] 가 아니면 흐리게 — 하나를 누른 동안 다른 창이 또 뜨지 않게.
 */
@Composable
fun SocialButton(provider: AuthProvider, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, enabled: Boolean = true) {
    val (bg, fg, label) = when (provider) {
        AuthProvider.KAKAO -> Triple(Color(0xFFFEE500), Color(0xD9000000), "카카오로 시작하기")
        AuthProvider.NAVER -> Triple(Color(0xFF03C75A), Color.White, "네이버로 시작하기")
        AuthProvider.GOOGLE -> Triple(Color.White, Color(0xFF1F1F1F), "Google로 시작하기")
        AuthProvider.EMAIL -> Triple(WoolCream, InkBrown, "이메일로 계속하기")
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .alpha(if (enabled || busy) 1f else 0.55f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (provider == AuthProvider.GOOGLE) Modifier.border(1.dp, Color(0xFF747775), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { BrandMark(provider) }
        Text(if (busy) "${provider.label} 연결 중…" else label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(22.dp))
    }
}

/** 각 사 심볼 — 카카오 말풍선 · 네이버 N · Google G */
@Composable
private fun BrandMark(p: AuthProvider) = Canvas(Modifier.size(20.dp)) {
    val w = size.width; val h = size.height
    when (p) {
        AuthProvider.KAKAO -> {
            val ink = Color.Black
            drawOval(ink, Offset(0f, h * 0.06f), Size(w, h * 0.72f))
            val tail = Path().apply { moveTo(w * 0.24f, h * 0.62f); lineTo(w * 0.18f, h * 0.95f); lineTo(w * 0.46f, h * 0.72f); close() }
            drawPath(tail, ink)
        }
        AuthProvider.NAVER -> {
            val n = Path().apply {
                moveTo(w * 0.12f, h * 0.12f); lineTo(w * 0.38f, h * 0.12f); lineTo(w * 0.62f, h * 0.52f); lineTo(w * 0.62f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.12f); lineTo(w * 0.88f, h * 0.88f); lineTo(w * 0.62f, h * 0.88f); lineTo(w * 0.38f, h * 0.48f)
                lineTo(w * 0.38f, h * 0.88f); lineTo(w * 0.12f, h * 0.88f); close()
            }
            drawPath(n, Color.White)
        }
        AuthProvider.GOOGLE -> {
            val sw = w * 0.2f
            val inset = sw / 2
            val box = Size(w - sw, h - sw)
            val tl = Offset(inset, inset)
            drawArc(Color(0xFFEA4335), 200f, 110f, false, tl, box, style = Stroke(sw))    // 빨강 (위)
            drawArc(Color(0xFFFBBC05), 140f, 60f, false, tl, box, style = Stroke(sw))     // 노랑 (왼쪽 아래)
            drawArc(Color(0xFF34A853), 45f, 95f, false, tl, box, style = Stroke(sw))      // 초록 (아래)
            drawArc(Color(0xFF4285F4), -10f, 55f, false, tl, box, style = Stroke(sw))     // 파랑 (오른쪽)
            drawRect(Color(0xFF4285F4), Offset(w * 0.5f, h * 0.5f - sw / 2), Size(w * 0.5f - inset / 2, sw))
        }
        AuthProvider.EMAIL -> drawRect(InkBrown, Offset(0f, h * 0.2f), Size(w, h * 0.6f), style = Stroke(w * 0.1f))
    }
}

/** 오또 목소리 말풍선 — 🔊 + 한 줄 (아이 화면의 글자는 보조, 소리가 먼저) */
@Composable
fun SpeakBubble(text: String, modifier: Modifier = Modifier, color: Color = Wool) {
    Row(
        modifier.felt(color, RoundedCornerShape(24.dp), lift = 4.dp, stitch = false).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔊", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, color = InkBrown)
    }
}

/**
 * 설정 화면 공통 틀 (`.pen` ob_frame) — 위: ← · 진행 점 3 / 왼쪽 340: 그림 + 한 줄 제목 / 오른쪽 460: 조작부 /
 * 오른쪽 아래: 큰 버튼 204×56 (토스 「한 화면 한 가지」). 보호자가 보는 화면이라 글꼴은 본문용 고딕.
 */
@Composable
fun ObFrame(
    step: Int?,
    title: String,
    sub: String,
    onBack: (() -> Unit)?,
    art: @Composable BoxScope.() -> Unit,
    cta: String? = null,
    ctaEnabled: Boolean = true,
    onCta: () -> Unit = {},
    cta2: String? = null,
    onCta2: () -> Unit = {},
    /** 오른쪽 조작부가 길면(약관 · 회원가입) 위아래로 밀어 본다 — 이때 안에서 `weight` 는 쓰지 않는다 */
    scroll: Boolean = false,
    right: @Composable ColumnScope.() -> Unit,
) = ParentText { Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(Wool)) {
        Column(Modifier.weight(340f).fillMaxHeight().felt(WoolCream, RoundedCornerShape(0.dp), lift = 0.dp, stitch = false).padding(start = 30.dp, end = 20.dp, bottom = 22.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center, content = art)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkBrown, lineHeight = 27.sp)
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 13.sp, color = InkSoft, lineHeight = 19.sp)
        }
        Box(Modifier.weight(460f).fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize().padding(top = 58.dp, bottom = 86.dp)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 32.dp),
                content = right,
            )
            if (step != null) StepDots(step, Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 28.dp))
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cta2 != null) PBtn(cta2, onCta2, Modifier.width(150.dp), primary = false, height = 56.dp)
                if (cta != null) PBtn(cta, onCta, Modifier.width(204.dp), enabled = ctaEnabled, height = 56.dp)
            }
        }
    }
    if (onBack != null) Box(Modifier.padding(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(FeltWhite.copy(alpha = 0.7f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Text("←", fontSize = 22.sp, color = InkBrown)
        }
    }
} }

private val STEPS = listOf("로그인", "동의", "마이크", "비밀번호", "맞춤 설정")

@Composable
private fun StepDots(step: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        STEPS.forEachIndexed { i, _ ->
            Box(Modifier.height(12.dp).width(if (i == step) 34.dp else 12.dp).clip(CircleShape)
                .background(if (i == step) FeltCoral else if (i < step) FeltTeal else InkBrown.copy(alpha = 0.15f)))
        }
        Spacer(Modifier.width(4.dp))
        Text("${step + 1} / ${STEPS.size}  ${STEPS[step]}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
    }
}

/**
 * 태어난 해 확인 (`.pen` year_gate) — 부모 영역 · 하루 한도 연장. **PIN 대신 태어난 해**(Lingokids · ABCmouse).
 * 네 자리를 다 넣으면 바로 넘어간다. 만 19세 이상인 해만 통과. 틀려도 잠그지 않고, 3번 틀리면 30초 기다린다.
 *
 * @param onDigit 흐름(`Director.pinGate`)에 숫자 하나씩 알려야 할 때 (부모 문). 다른 곳에서는 null
 */
@Composable
fun YearPad(onPass: () -> Unit, modifier: Modifier = Modifier, note: String = "네 자리를 다 넣으면 바로 넘어가요") {
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var waitLeft by remember { mutableIntStateOf(0) }
    var shake by remember { mutableStateOf(false) }
    LaunchedEffect(waitLeft) { if (waitLeft > 0) { delay(1000); waitLeft-- } }
    LaunchedEffect(digits) {
        if (digits.length == 4) {
            val y = digits.toInt()
            val now = Calendar.getInstance().get(Calendar.YEAR)
            if (y in 1920..(now - 19)) onPass()
            else {
                delay(300); shake = true; digits = ""; wrong++
                if (wrong >= 3) { waitLeft = 30; wrong = 0 }
                delay(500); shake = false
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("보호자님이 태어난 해 네 자리", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.scale(if (shake) 0.97f else 1f)) {
                repeat(4) { i ->
                    Box(
                        Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(14.dp)).background(FeltWhite)
                            .border(if (i == digits.length) 2.5.dp else 1.5.dp, if (i == digits.length) FeltCoral else InkBrown.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (i < digits.length) Text(digits[i].toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = InkBrown) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    waitLeft > 0 -> "잠시 뒤에 다시 해 주세요 · ${waitLeft}초"
                    shake -> "다시 확인해 주세요"
                    else -> note
                },
                fontSize = 12.sp, color = if (shake) FeltCoral else InkSoft, lineHeight = 17.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.width(54.dp)) else Box(
                            Modifier.width(54.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(WoolCream)
                                .clickable(enabled = waitLeft == 0) {
                                    digits = if (k == "⌫") digits.dropLast(1) else if (digits.length < 4) digits + k else digits
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkBrown) }
                    }
                }
            }
        }
    }
}

/**
 * **2초 길게 눌러야** 열리는 부모 문 (Pok Pok · Apple Kids · Sago). 아이가 우연히 들어가지 않게.
 * 누르는 동안 테두리가 차오른다.
 */
fun Modifier.holdToOpen(millis: Long = 2000, onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown()
        val up = withTimeoutOrNull(millis) { waitForUpOrCancellation() }
        if (up == null) onOpen()
    }
}

/**
 * **부모 비밀번호 네 자리** 입력 (09-29 사용자 — 「비밀번호를 태어난 해로 하지 말고 직접 설정하게」).
 *
 * 네 자리를 다 넣으면 [onDone] 에 넘긴다. [onDone] 이 false 를 돌려주면(틀렸음) 흔들고 비운다.
 * 3번 틀리면 30초 기다린다 — 잠그지는 않는다. 눌린 숫자는 점으로만 보인다(옆에서 아이가 보고 따라 누르지 않게).
 *
 * @param reset 이 값이 바뀌면 입력을 비운다(만들기 → 확인 단계로 넘어갈 때)
 */
@Composable
fun PinPad(title: String, note: String, onDone: (String) -> Boolean, modifier: Modifier = Modifier, reset: Any? = null) {
    var digits by remember(reset) { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var waitLeft by remember { mutableIntStateOf(0) }
    var shake by remember { mutableStateOf(false) }
    LaunchedEffect(waitLeft) { if (waitLeft > 0) { delay(1000); waitLeft-- } }
    LaunchedEffect(digits) {
        if (digits.length == 4) {
            delay(150)
            if (!onDone(digits)) {
                shake = true; digits = ""; wrong++
                if (wrong >= 3) { waitLeft = 30; wrong = 0 }
                delay(600); shake = false
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.scale(if (shake) 0.97f else 1f)) {
                repeat(4) { i ->
                    val filled = i < digits.length
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (filled) FeltCoral else FeltWhite)
                            .border(2.dp, if (filled) FeltCoral else if (i == digits.length) FeltCoral.copy(alpha = 0.6f) else InkBrown.copy(alpha = 0.18f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    waitLeft > 0 -> "잠시 뒤에 다시 해 주세요 · ${waitLeft}초"
                    shake -> "비밀번호가 맞지 않아요"
                    else -> note
                },
                fontSize = 12.sp, color = if (shake) FeltCoral else InkSoft, lineHeight = 17.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.width(58.dp)) else Box(
                            Modifier.width(58.dp).height(46.dp).clip(RoundedCornerShape(14.dp)).background(WoolCream)
                                .clickable(enabled = waitLeft == 0) {
                                    digits = if (k == "⌫") digits.dropLast(1) else if (digits.length < 4) digits + k else digits
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = InkBrown) }
                    }
                }
            }
        }
    }
}

/**
 * 비밀번호 **새로 정하기** — 네 자리 → 한 번 더 → 같으면 [onSet]. 다르면 처음부터.
 */
@Composable
fun PinCreate(onSet: (String) -> Unit, modifier: Modifier = Modifier) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    PinPad(
        title = if (first == null) "새 비밀번호 네 자리" else "한 번 더 눌러 주세요",
        note = when {
            mismatch -> "두 번 누른 번호가 달라요. 처음부터 다시 정해 주세요"
            first == null -> "부모 영역을 열 때 써요. 아이가 모르는 번호로 정해 주세요"
            else -> "방금 누른 네 자리를 한 번 더"
        },
        onDone = { pin ->
            val f = first
            when {
                f == null -> { first = pin; mismatch = false; true }
                f == pin -> { onSet(pin); true }
                else -> { first = null; mismatch = true; true }
            }
        },
        modifier = modifier,
        reset = first to mismatch,
    )
}
