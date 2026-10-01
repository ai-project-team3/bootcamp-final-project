package com.example.finalproject_demo.ui.shell

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.net.Accounts
import com.example.finalproject_demo.net.AuthProvider
import com.example.finalproject_demo.net.AuthResult
import com.example.finalproject_demo.net.ConsentRecord
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.rememberQuietRest
import com.example.finalproject_demo.ui.wakeOnTouch
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.Curtain
import com.example.finalproject_demo.ui.CurtainDeep
import com.example.finalproject_demo.ui.FeltButton
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltMustard
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.PRIVACY_URL
import com.example.finalproject_demo.ui.ParentFont
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.StageWood
import com.example.finalproject_demo.ui.StageWoodDeep
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.WoolCream
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.noRippleClickable
import kotlinx.coroutines.launch

/*
 * 처음 한 번 — ① 타이틀 · ② 로그인 · 이메일 · ③ 동의 · ④ 마이크 · ⑤ 아이에게 건네기 · ⑦ 기능 소개 ·
 * 예외 · 로그인이 풀렸을 때 (09-29 · `.pen` screens_v2 · v3)
 */

// ── ① 타이틀 — 인형극 무대 · 커튼 · 로고 · 「눌러서 시작」 ─────────────────────

@Composable
fun TitleScreen(onStart: () -> Unit) {
    // 한참 아무도 안 만지면 「눌러서 시작」 두근거림을 쉬게 한다 — 이 하나로 CPU 87% 였다 (#40)
    val quiet = rememberQuietRest()
    val pulse = if (quiet.resting) 1f else {
        val t = rememberInfiniteTransition(label = "title")
        t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    }
    Box(Modifier.fillMaxSize().background(CurtainDeep).wakeOnTouch(quiet).noRippleClickable { onStart() }) {
        // 무대 그림 — ComfyUI(title_bg · tools/gen_room.py). 없으면 아래 펠트 도형으로 그린다
        AssetImage("title_bg", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) { TitleFallback() }
        Column(Modifier.align(Alignment.Center).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AssetImage("logo_otto_v2", Modifier.width(420.dp))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.scale(pulse).felt(Wool, RoundedCornerShape(20.dp), lift = 4.dp, stitch = false).padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👆", fontSize = 16.sp); Spacer(Modifier.width(6.dp)); Text("눌러서 시작", fontSize = 16.sp, color = InkBrown)
            }
        }
    }
}

@Composable
private fun TitleFallback() {
    Box(Modifier.fillMaxSize()) {
        // 조명 · 무대 바닥
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFE9B8), Color.Transparent), center = Offset(size.width / 2, size.height * 0.35f), radius = size.width * 0.38f),
                size.width * 0.38f, Offset(size.width / 2, size.height * 0.35f))
            drawRect(StageWood, Offset(0f, size.height * 0.82f))
            drawRect(StageWoodDeep, Offset(0f, size.height * 0.82f), androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()))
        }
        // 커튼 양쪽 — 주름 네 줄 · 묶는 끈
        Row(Modifier.fillMaxSize()) {
            CurtainSide()
            Spacer(Modifier.weight(1f))
            CurtainSide()
        }
        Box(Modifier.fillMaxWidth().height(34.dp).felt(Curtain, RoundedCornerShape(0.dp), lift = 4.dp, stitch = false))
    }
}

@Composable
private fun CurtainSide() {
    Box(Modifier.width(150.dp).fillMaxHeight(0.92f).felt(Curtain, RoundedCornerShape(bottomEnd = 30.dp, bottomStart = 30.dp), lift = 6.dp, stitch = false)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(4) { Box(Modifier.width(12.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(CurtainDeep.copy(alpha = 0.35f))) }
        }
        Box(Modifier.align(Alignment.Center).padding(top = 40.dp).fillMaxWidth().height(18.dp).felt(FeltMustard, RoundedCornerShape(9.dp), lift = 2.dp, stitch = false))
    }
}

// ── ② 로그인 (1/3) ───────────────────────────────────────────

@Composable
fun LoginScreen(onDone: () -> Unit, onEmail: () -> Unit, onSample: () -> Unit = {}, expired: Boolean = false, allowSample: Boolean = false) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    fun go(p: AuthProvider) = scope.launch {
        when (val r = Accounts.api.login(p)) {
            is AuthResult.Ok -> { Accounts.guardian = r.guardian; onDone() }
            is AuthResult.Fail -> msg = r.why
        }
    }
    ObFrame(
        step = if (expired) null else 0,
        title = if (expired) "다시 로그인해 주세요" else "만든 책을 안전하게 보관해요",
        sub = if (expired) "보안을 위해 로그인이 풀렸어요. 폰 안의 책은 그대로 있어요." else "아이 이름 · 사진은 받지 않아요. 보호자 계정 하나면 돼요.",
        onBack = null,
        art = { Otto(if (expired) Pose.CALL else Pose.WAVE, Modifier.size(190.dp)) },
    ) {
        Text("보호자 계정으로 시작해요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown)
        Spacer(Modifier.height(12.dp))
        // 세 버튼 같은 크기 — 「다른 버튼보다 덜 눈에 띄게 만들면 안 됨」(각 사 규격)
        SocialButton(AuthProvider.KAKAO, { go(AuthProvider.KAKAO) })
        Spacer(Modifier.height(8.dp))
        SocialButton(AuthProvider.NAVER, { go(AuthProvider.NAVER) })
        Spacer(Modifier.height(8.dp))
        SocialButton(AuthProvider.GOOGLE, { go(AuthProvider.GOOGLE) })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이메일로 계속하기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSoft, modifier = Modifier.clickable { onEmail() }.padding(vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            // 09-29 사용자 요청 — 처음 설정을 끝내야 방에 들어간다. 로그인 없이 둘러보는 길은 기본으로 닫아 둔다
            if (!expired && allowSample) Column(horizontalAlignment = Alignment.End) {
                Text("로그인 없이 샘플 책 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FeltTeal, modifier = Modifier.clickable { onSample() })
                Text("샘플 책은 녹음 없이 읽기만 해요", fontSize = 11.sp, color = InkSoft)
            }
        }
        msg?.let { Text(it, fontSize = 12.sp, color = FeltCoral) }
    }
}

// ── ② 이메일로 계속 (1/3) ────────────────────────────────────

@Composable
fun EmailScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    ObFrame(
        step = 0, title = "이메일로 계속하기", sub = "인증 메일을 보내 드려요. 비밀번호를 잊으면 메일로 다시 만들어요.",
        onBack = onBack, art = { Otto(Pose.WAVE, Modifier.size(190.dp)) },
        cta = "다음", ctaEnabled = email.contains('@') && pw.length >= 8,
        onCta = {
            scope.launch {
                when (val r = Accounts.api.login(AuthProvider.EMAIL, email.trim(), pw)) {
                    is AuthResult.Ok -> { Accounts.guardian = r.guardian; onDone() }
                    is AuthResult.Fail -> msg = r.why
                }
            }
        },
    ) {
        Field("이메일", email, "parent@example.com", KeyboardType.Email, false) { email = it }
        Spacer(Modifier.height(14.dp))
        Field("비밀번호", pw, "8자 이상 · 영문 + 숫자", KeyboardType.Password, true) { pw = it }
        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 12.sp, color = FeltCoral) }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, type: KeyboardType, secret: Boolean, onChange: (String) -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(FeltWhite)
            .border(1.5.dp, InkBrown.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = InkSoft)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = InkBrown, fontFamily = ParentFont),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── ③ 동의 (2/3) — 왼쪽은 데이터가 쓰이는 길 그림 세 칸 ─────────────────────

@Composable
fun ConsentStep(onBack: () -> Unit, onDone: () -> Unit, onDecline: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var terms by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var guardian by remember { mutableStateOf(false) }
    var marketing by remember { mutableStateOf(false) }   // 선택은 미리 체크하지 않는다
    val all = terms && privacy && guardian && marketing
    fun open() = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
    ObFrame(
        step = 1, title = "이렇게만 써요", sub = "녹음은 글로 바꾼 뒤 바로 지우고, 책은 이 폰에 저장해요.", onBack = onBack,
        art = { DataPath() },
        cta = "동의하고 계속", ctaEnabled = terms && privacy && guardian,
        onCta = {
            // 원래 동의 저장소를 그대로 쓴다 — 거기 동의가 있어야 아이가 말할 수 있다 (개인정보보호법 제22조의2)
            ConsentStore.agree()
            scope.launch { Accounts.api.recordConsent(ConsentRecord(terms, privacy, guardian, marketing, System.currentTimeMillis())) }
            onDone()
        },
        // 거절할 길 — 동의하지 않으면 앱을 닫는다 (09-25 · 동의하지 않는 보호자가 나갈 방법이 있어야 한다)
        cta2 = "동의하지 않음", onCta2 = onDecline,
    ) {
        CheckRow(all, "모두 동의해요", { val v = !all; terms = v; privacy = v; guardian = v; marketing = v }, big = true)
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(InkBrown.copy(alpha = 0.1f)))
        Spacer(Modifier.height(6.dp))
        CheckRow(terms, "[필수] 이용약관", { terms = !terms }, trailing = "보기", onTrailing = { open() })
        CheckRow(privacy, "[필수] 보호자 개인정보 수집 · 이용", { privacy = !privacy }, trailing = "보기", onTrailing = { open() })
        CheckRow(guardian, "[필수] 만 14세 미만 아동의 법정대리인 동의", { guardian = !guardian }, trailing = "보기", onTrailing = { open() })
        CheckRow(marketing, "[선택] 새 기능 알림", { marketing = !marketing })
    }
}

@Composable
private fun DataPath() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("🎙", "아이가 말해요", FeltTeal), Triple("✏️", "오또가 글로\n바꿔요", FeltMustard), Triple("📖", "책으로\n저장돼요", FeltCoral))
            .forEachIndexed { i, (ic, t, c) ->
                val art = listOf("feat_talk", "otto_face_think", "feat_book")[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                    Box(Modifier.size(64.dp).felt(c.copy(alpha = 0.35f), CircleShape, lift = 2.dp), contentAlignment = Alignment.Center) {
                        AssetImage(art, Modifier.size(54.dp)) { Text(ic, fontSize = 28.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkBrown, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }
                if (i < 2) Text("›", fontSize = 22.sp, color = InkSoft)
            }
    }
}

// ── ④ 마이크 (3/3) — 「나중에」 필수 · 거부해도 앱은 그대로 ────────────────────

@Composable
fun MicStep(onBack: () -> Unit, onDone: () -> Unit) {
    // 09-29 사용자 — 「마이크 켜기에서 권한 창이 안 뜬다」. 실기기에서 보니 권한이 **이미 허용**이면 창 없이 바로
    // 넘어가 창이 안 뜨는 것처럼 보였고, 두 번 거절한 폰은 안드로이드가 창을 다시 띄워 주지 않는다.
    // 그래서 지금 상태를 보여 준다: 허용됨 → ✓ + 다음 / 아직 → 마이크 켜기(권한 창) / 거절 → 설정에서 켜기 · 마이크 없이 계속
    val ctx = LocalContext.current
    val activity = ctx as? android.app.Activity
    fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    var state by remember { mutableStateOf(if (granted()) "on" else "ask") }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        state = if (ok) "on" else "denied"
        if (ok) onDone()
    }
    // 설정 앱에서 켜고 돌아오면 다시 확인한다
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && granted()) state = "on" }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun ask() {
        ConsentStore.markMicNoticeShown()
        if (motionFrozenForTests()) { onDone(); return }
        // 거절한 뒤 다시 물어도 안드로이드가 창을 안 띄우는 경우(두 번 거절) — 설정으로 안내
        val canAsk = activity == null || state != "denied" ||
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)
        if (canAsk) runCatching { launcher.launch(android.Manifest.permission.RECORD_AUDIO) }.onFailure { state = "denied" }
        else openAppSettings(ctx)
    }
    ObFrame(
        step = 2,
        title = when (state) { "on" -> "마이크가 켜져 있어요"; "denied" -> "마이크가 꺼져 있어요"; else -> "마이크를 켜 주세요" },
        sub = when (state) {
            "on" -> "이미 허용되어 있어요. 오또가 말할 차례에만 들어요."
            "denied" -> "설정에서 언제든 켤 수 있어요. 켜지 않아도 그림을 골라서 만들 수 있어요."
            else -> "다음에 뜨는 휴대폰 창에서 '허용'을 눌러 주세요."
        },
        onBack = onBack,
        art = { Otto(Pose.LISTEN, Modifier.size(190.dp)) },
        cta = when (state) { "on" -> "다음"; "denied" -> "설정에서 켜기"; else -> "마이크 켜기" },
        onCta = {
            when (state) {
                "on" -> { ConsentStore.markMicNoticeShown(); onDone() }
                "denied" -> openAppSettings(ctx)
                else -> ask()
            }
        },
        cta2 = if (state == "denied") "마이크 없이 계속" else null,
        onCta2 = { ConsentStore.markMicNoticeShown(); onDone() },
    ) {
        Text("오또가 아이 목소리를 들을 수 있게", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown)
        Spacer(Modifier.height(10.dp))
        // 지금 상태 한 줄 — 켜짐(청록) · 꺼짐(코랄)
        if (state != "ask") Row(
            Modifier.felt(if (state == "on") FeltTeal else FeltCoral, RoundedCornerShape(Radius.Round), lift = 2.dp, stitch = false, texture = false)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(if (state == "on") "✓  마이크 허용됨" else "✕  마이크 허용 안 됨", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FeltWhite) }
        Spacer(Modifier.height(8.dp))
        listOf("👂" to "말할 차례에만 들어요 — 오또가 귀를 쫑긋할 때", "🛡" to "녹음은 폰 밖으로 나가지 않아요", "✋" to "안 켜도 그림을 골라서 만들 수 있어요").forEach { (ic, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(32.dp).felt(FeltTeal, CircleShape, lift = 0.dp, stitch = false), contentAlignment = Alignment.Center) { Text(ic, fontSize = 15.sp) }
                Spacer(Modifier.width(12.dp))
                Text(t, fontSize = 13.sp, color = InkBrown)
            }
        }
    }
}

/** 이 앱의 설정 화면(권한 켜기) */
private fun openAppSettings(ctx: android.content.Context) = runCatching {
    ctx.startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** JVM 검사(Robolectric)에서는 권한 창을 띄우지 않는다 */
private fun motionFrozenForTests() = com.example.finalproject_demo.ui.motionFrozen

// ── ④ 부모 비밀번호 (4/5) — 직접 네 자리를 정한다 (09-29 사용자 요청) ─────────────────

@Composable
fun PinStep(onBack: () -> Unit, onDone: () -> Unit) {
    ObFrame(
        step = 3, title = "부모 비밀번호를 정해요", sub = "부모 영역을 열 때 · 하루 한도를 늘릴 때 이 번호를 물어요.", onBack = onBack,
        art = { AssetImage("pi_lock", Modifier.size(170.dp)) { Box(Modifier.size(110.dp).felt(FeltTeal, CircleShape), contentAlignment = Alignment.Center) { Text("🔒", fontSize = 48.sp) } } },
    ) {
        Spacer(Modifier.height(8.dp))
        PinCreate(onSet = { Shell.setPin(it); onDone() })
    }
}

// ── ⑤ 맞춤 설정 (5/5) — 하루 책 수 · 그림체 · 시작할 때 어른 확인. **셋 다 골라야** 넘어간다 (09-29) ──────

@Composable
fun SetupStep(onBack: () -> Unit, onDone: () -> Unit) {
    var limit by remember { mutableStateOf<Int?>(null) }        // 0 = 제한 없음
    var style by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf<Boolean?>(null) }
    ObFrame(
        step = 4, title = "우리 아이에게 맞춰요", sub = "나중에 부모 설정에서 언제든 바꿀 수 있어요.", onBack = onBack,
        art = { Otto(Pose.THINK, Modifier.size(190.dp)) },
        cta = "설정 끝", ctaEnabled = limit != null && style != null && pin != null,
        onCta = { Shell.saveSetup(limit!!.takeIf { it > 0 }, style!!, pin!!); onDone() },
    ) {
        SetupLabel("하루에 만들 책")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1권", 2 to "2권", 3 to "3권", 0 to "제한 없음").forEach { (v, t) -> Choice(t, limit == v) { limit = v } }
        }
        Spacer(Modifier.height(14.dp))
        SetupLabel("그림체")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.example.finalproject_demo.demo.ART_STYLES.forEach { a ->
                Choice(a.name.substringBefore(' '), style == a.key, enabled = a.ready) { style = a.key }
            }
        }
        Text("지금은 양모 펠트만 쓸 수 있어요", fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        SetupLabel("이야기를 시작할 때 어른 확인")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("받지 않아요", pin == false) { pin = false }
            Choice("비밀번호로 확인", pin == true) { pin = true }
        }
    }
}

@Composable
private fun SetupLabel(t: String) {
    Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkBrown, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Choice(t: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) FeltTeal else if (enabled) FeltWhite else InkBrown.copy(alpha = 0.06f))
            .border(1.5.dp, if (on) FeltTeal else InkBrown.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) FeltWhite else if (enabled) InkBrown else InkSoft) }
}

// ── ⑤ 아이에게 건네기 — 진행 점 없음 ─────────────────────────────

@Composable
fun HandoffStep(onDone: () -> Unit) {
    ObFrame(
        step = null, title = "설정이 끝났어요", sub = "처음 설정은 이번 한 번만 해요.", onBack = null,
        art = { Otto(Pose.PHONE, Modifier.size(200.dp)) },
        cta = "아이 차례 시작", onCta = onDone,
    ) {
        Spacer(Modifier.height(20.dp))
        Text("준비 끝!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = InkBrown)
        Spacer(Modifier.height(12.dp))
        Text("이제 아이에게 건네주세요. 오또가 목소리로 이어서 안내해요.", fontSize = 15.sp, color = InkSoft, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Text("부모 설정은 언제든 왼쪽 위 자물쇠를 누르고 방금 정한 비밀번호를 넣으면 열려요.", fontSize = 12.sp, color = InkSoft, lineHeight = 18.sp)
    }
}

// ── ⑥ 튜토리얼 ② 말해 보기 — 틀려도 괜찮은 연습 ──────────────────────────

@Composable
fun TutorialTalk(onDone: () -> Unit) {
    var said by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AssetImage("bg_park", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Wool)) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, InkBrown.copy(alpha = 0.25f)))))
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp).felt(FeltMustard, RoundedCornerShape(16.dp), lift = 3.dp, stitch = false).padding(horizontal = 16.dp, vertical = 5.dp),
        ) { Text("✨ 연습", fontSize = 15.sp, color = FeltWhite) }
        // 연습 버튼도 실제처럼 나레이션 칸 **안** 오른쪽에 (09-29)
        Narration(
            mode = NarrationMode.STORY, state = if (said) com.example.finalproject_demo.ui.OttoState.TALK else com.example.finalproject_demo.ui.OttoState.LISTEN,
            line = if (said) "잘했어! 이렇게 말하면 돼" else "좋아하는 동물을 말해 줄래?",
            modifier = Modifier.align(Alignment.BottomCenter),
            expr = if (said) com.example.finalproject_demo.ui.Expr.HAPPY else com.example.finalproject_demo.ui.Expr.CURIOUS,
            trailing = {
                // 마이크 없이도 넘어갈 수 있게 — 탭하면 「말한 것」으로 친다(연습이라 녹음하지 않는다)
                FeltButton(if (said) FeltCoral else FeltTeal, onClick = { if (said) onDone() else said = true },
                    modifier = Modifier.size(66.dp).semantics { contentDescription = if (said) "다음" else "연습 말하기" }, shape = CircleShape) {
                    if (said) Text("➜", fontSize = 30.sp, color = FeltWhite)
                    else AssetImage("feat_talk", Modifier.size(42.dp)) { Text("🎙", fontSize = 30.sp) }
                }
            },
        )
    }
}

// ── ⑦ 기능 소개 — 해 본 것을 세 가지로 ────────────────────────────

@Composable
fun FeaturesStep(onDone: () -> Unit) = Box(Modifier.fillMaxSize().felt(Wool, RoundedCornerShape(0.dp), lift = 0.dp, stitch = false)) {
    AssetImage("logo_otto_v2", Modifier.padding(start = 18.dp, top = 10.dp).width(120.dp))
    Text("오또랑 이렇게 놀아요", fontSize = 30.sp, color = InkBrown, modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp))
    Row(Modifier.align(Alignment.Center).padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("🎙", "말하면" to "오또에게 말로 이야기해요", FeltTeal), Triple("📖", "그림책이 돼요" to "말한 대로 그림이 그려져요", FeltCoral),
            Triple("📚", "책장에 모여요" to "만든 책은 언제든 다시 봐요", FeltMustard)).forEachIndexed { i, (ic, tt, c) ->
            val art = listOf("feat_talk", "feat_book", "feat_shelf")[i]
            // 번호 원은 카드 **밖**에 — 카드(felt)는 자기 안을 잘라 모서리에 걸친 원이 잘렸다
            Box(Modifier.width(200.dp).height(196.dp)) {
                Column(Modifier.fillMaxSize().felt(FeltWhite, RoundedCornerShape(28.dp)).padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AssetImage(art, Modifier.size(96.dp)) {
                        Box(Modifier.size(92.dp).felt(c, CircleShape), contentAlignment = Alignment.Center) { Text(ic, fontSize = 42.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tt.first, fontSize = 22.sp, color = InkBrown)
                    ParentText { Text(tt.second, fontSize = 12.sp, color = InkSoft) }
                }
                Box(Modifier.offset((-8).dp, (-10).dp).size(36.dp).felt(c, CircleShape, lift = 2.dp, stitch = false), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 18.sp, color = FeltWhite)
                }
            }
            if (i < 2) Text("›", fontSize = 30.sp, color = InkSoft, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
    ParentText { Text("방금 해 본 것 — 오또가 목소리로 한 번 더 정리해 줘요", fontSize = 13.sp, color = InkSoft, modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 30.dp)) }
    ParentText { PBtn("오또의 방으로", onDone, Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 18.dp).width(180.dp), height = 52.dp) }
}
