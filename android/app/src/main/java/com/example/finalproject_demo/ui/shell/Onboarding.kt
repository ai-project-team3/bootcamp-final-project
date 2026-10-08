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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
import com.example.finalproject_demo.net.EmailRules
import com.example.finalproject_demo.net.SocialLogin
import com.example.finalproject_demo.net.SocialResult
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

// ── ② 로그인 (1/5) — 카카오 · 네이버 · Google SDK · 이메일 로그인 · 이메일 회원가입 (10-05) ─────────────

@Composable
fun LoginScreen(onDone: () -> Unit, onEmail: (EmailMode) -> Unit, onSample: () -> Unit = {}, expired: Boolean = false, allowSample: Boolean = false) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    var msg by remember { mutableStateOf<String?>(null) }
    /** 지금 연결 중인 방법 — 그동안 다른 버튼은 눌리지 않는다(두 번 눌러 창이 두 개 뜨지 않게) */
    var busy by remember { mutableStateOf<AuthProvider?>(null) }
    fun go(p: AuthProvider) {
        if (busy != null) return
        busy = p; msg = null
        scope.launch {
            when (val r = SocialLogin.signIn(activity, p)) {
                is SocialResult.Ok -> when (val a = Accounts.api.loginSocial(r.who)) {
                    is AuthResult.Ok -> { Accounts.guardian = a.guardian; onDone() }
                    is AuthResult.Fail -> msg = a.why
                }
                is SocialResult.Cancelled -> {}
                is SocialResult.Fail -> msg = r.why
            }
            busy = null
        }
    }
    ObFrame(
        step = if (expired) null else 0,
        title = if (expired) "다시 로그인해 주세요" else "만든 책을 안전하게 보관해요",
        sub = if (expired) "보안을 위해 로그인이 풀렸어요. 폰 안의 책은 그대로 있어요." else "아이 이름 · 사진은 받지 않아요. 보호자 계정 하나면 돼요.",
        onBack = null,
        art = { Otto(if (expired) Pose.CALL else Pose.WAVE, Modifier.size(190.dp)) },
    ) {
        Text(if (expired) "쓰던 방법으로 다시 로그인해요" else "보호자 계정으로 시작해요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown)
        Spacer(Modifier.height(10.dp))
        // 세 버튼 같은 크기 — 「다른 버튼보다 덜 눈에 띄게 만들면 안 됨」(각 사 규격)
        listOf(AuthProvider.KAKAO, AuthProvider.NAVER, AuthProvider.GOOGLE).forEach { p ->
            SocialButton(p, { go(p) }, busy = busy == p, enabled = busy == null)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(2.dp))
        // 이메일 — 로그인 · 회원가입을 나란히 (소셜 계정이 없는 보호자)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("✉  이메일로 로그인", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkBrown,
                modifier = Modifier.clickable(enabled = busy == null) { onEmail(EmailMode.LOGIN) }.padding(vertical = 10.dp, horizontal = 4.dp))
            Text("|", fontSize = 12.sp, color = InkSoft.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))
            Text("이메일로 회원가입", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FeltTeal,
                modifier = Modifier.clickable(enabled = busy == null) { onEmail(EmailMode.SIGNUP) }.padding(vertical = 10.dp, horizontal = 4.dp))
            Spacer(Modifier.weight(1f))
            // 09-29 사용자 요청 — 처음 설정을 끝내야 방에 들어간다. 로그인 없이 둘러보는 길은 기본으로 닫아 둔다
            if (!expired && allowSample) Text("로그인 없이 샘플 책 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FeltTeal, modifier = Modifier.clickable { onSample() })
        }
        msg?.let { Text(it, fontSize = 12.sp, color = FeltCoral, lineHeight = 17.sp) }
        Spacer(Modifier.weight(1f))
        Text("계속하면 다음 단계에서 이용약관 · 개인정보 처리에 동의를 받아요", fontSize = 11.sp, color = InkSoft)
    }
}

// ── ② 이메일 — 로그인 · 회원가입 · 비밀번호 다시 정하기 (10-05) ────────────────────────

/** 이메일 화면이 하는 일 */
enum class EmailMode { LOGIN, SIGNUP, RESET }

@Composable
fun EmailScreen(start: EmailMode = EmailMode.LOGIN, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(start) }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun switch(m: EmailMode) { mode = m; pw = ""; pw2 = ""; msg = null }
    val emailOk = EmailRules.emailOk(email)
    val ready = when (mode) {
        EmailMode.LOGIN -> emailOk && pw.isNotEmpty()
        EmailMode.SIGNUP, EmailMode.RESET -> emailOk && EmailRules.passwordOk(pw) && pw == pw2
    }
    ObFrame(
        step = 0,
        title = when (mode) { EmailMode.LOGIN -> "이메일로 로그인"; EmailMode.SIGNUP -> "이메일로 회원가입"; EmailMode.RESET -> "비밀번호 다시 정하기" },
        sub = when (mode) {
            EmailMode.LOGIN -> "이 폰에서 가입한 이메일과 비밀번호를 넣어 주세요."
            EmailMode.SIGNUP -> "보호자 이메일 하나면 돼요. 비밀번호는 이 폰에 암호화해서만 저장해요."
            EmailMode.RESET -> "가입한 이메일을 넣고 새 비밀번호를 정해요."
        },
        onBack = { if (mode == EmailMode.RESET) switch(EmailMode.LOGIN) else onBack() },
        art = { Otto(if (mode == EmailMode.SIGNUP) Pose.WAVE else Pose.THINK, Modifier.size(170.dp)) },
        scroll = true,
        cta = when { busy -> "확인 중…"; mode == EmailMode.LOGIN -> "로그인"; mode == EmailMode.SIGNUP -> "가입하기"; else -> "새 비밀번호로 로그인" },
        ctaEnabled = ready && !busy,
        onCta = {
            busy = true; msg = null
            scope.launch {
                val r = when (mode) {
                    EmailMode.LOGIN -> Accounts.api.login(email.trim(), pw)
                    EmailMode.SIGNUP -> Accounts.api.signUp(email.trim(), pw)
                    EmailMode.RESET -> Accounts.api.resetPassword(email.trim(), pw)
                }
                busy = false
                when (r) {
                    is AuthResult.Ok -> { Accounts.guardian = r.guardian; onDone() }
                    is AuthResult.Fail -> msg = r.why
                }
            }
        },
    ) {
        // 로그인 ↔ 회원가입 탭 (다시 정하기는 탭 없이)
        if (mode != EmailMode.RESET) Row(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp)).background(InkBrown.copy(alpha = 0.06f)).padding(3.dp),
        ) {
            listOf(EmailMode.LOGIN to "로그인", EmailMode.SIGNUP to "회원가입").forEach { (m, t) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(if (mode == m) FeltWhite else Color.Transparent)
                        .clickable { if (mode != m) switch(m) },
                    contentAlignment = Alignment.Center,
                ) { Text(t, fontSize = 14.sp, fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal, color = if (mode == m) InkBrown else InkSoft) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Field("이메일", email, "parent@example.com", KeyboardType.Email, false) { email = it.trim(); msg = null }
        if (email.isNotEmpty() && !emailOk) Text("이메일 형식이 아니에요", fontSize = 11.sp, color = FeltCoral, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(4.dp))
        Field(if (mode == EmailMode.RESET) "새 비밀번호" else "비밀번호", pw, if (mode == EmailMode.LOGIN) "비밀번호" else "영문 + 숫자 8자 이상",
            KeyboardType.Password, !show, trailing = if (show) "숨기기" else "보기", onTrailing = { show = !show }) { pw = it; msg = null }
        if (mode != EmailMode.LOGIN) {
            // 규칙을 입력하는 동안 바로 보여 준다 — 다 맞으면 청록 ✓
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Rule("8자 이상", EmailRules.longEnough(pw))
                Rule("영문 + 숫자", EmailRules.hasLetterAndDigit(pw))
                Rule("빈칸 없음", pw.isNotEmpty() && pw.none { it.isWhitespace() })
            }
            Spacer(Modifier.height(2.dp))
            Field("비밀번호 확인", pw2, "한 번 더", KeyboardType.Password, !show) { pw2 = it; msg = null }
            if (pw2.isNotEmpty() && pw != pw2) Text("비밀번호가 서로 달라요", fontSize = 11.sp, color = FeltCoral, modifier = Modifier.padding(top = 3.dp))
        } else Text(
            "비밀번호를 잊었어요", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkSoft,
            modifier = Modifier.align(Alignment.End).clickable { switch(EmailMode.RESET) }.padding(vertical = 8.dp),
        )
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = FeltCoral, lineHeight = 17.sp) }
    }
}

@Composable
private fun Rule(t: String, ok: Boolean) {
    Text((if (ok) "✓ " else "· ") + t, fontSize = 11.sp, fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal, color = if (ok) FeltTeal else InkSoft)
}

@Composable
internal fun Field(
    label: String, value: String, hint: String, type: KeyboardType, secret: Boolean,
    trailing: String? = null, onTrailing: () -> Unit = {}, onChange: (String) -> Unit,
) {
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
    Spacer(Modifier.height(2.dp))
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(FeltWhite)
            .border(1.5.dp, InkBrown.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = InkSoft)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = InkBrown, fontFamily = ParentFont),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = InkSoft, modifier = Modifier.clickable { onTrailing() }.padding(8.dp))
    }
}

// ── ③ 동의 (2/5) — 전체 동의 · [필수]/[선택] · 항목마다 앱 안에서 전문 보기 (10-05 개편 · `Terms.kt`) ─────────────

@Composable
fun ConsentStep(onBack: () -> Unit, onDone: () -> Unit, onDecline: () -> Unit = {}, reconsent: Boolean = false) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // 선택 항목도 **미리 체크하지 않는다** — 모두 꺼진 채로 시작
    val agreed = remember { androidx.compose.runtime.mutableStateMapOf<TermsDoc, Boolean>() }
    var reading by remember { mutableStateOf<TermsDoc?>(null) }
    var asking by remember { mutableStateOf(false) }
    fun on(d: TermsDoc) = agreed[d] == true
    val all = TermsDoc.entries.all { on(it) }
    val left = TermsDoc.entries.count { it.required && !on(it) }
    Box(Modifier.fillMaxSize()) {
        // 이미 동의한 보호자에게 판이 올라 다시 묻는 화면 (#256) — 처음 가입과 같은 「1/5 · 이렇게만 써요」라 왜 다시 묻는지 몰랐다
        val changes = if (reconsent) termsChangesSince(Shell.consentVersion) else emptyList()
        val sub = if (!reconsent) "목소리는 글자로 바꾼 뒤 바로 지우고, 책은 이 폰에 저장해요."
            else "다시 확인하고 동의해 주세요. 바뀐 것:\n" + changes.take(3).joinToString("\n") { "· $it" } +
                (if (changes.size > 3) "\n외 ${changes.size - 3}개" else "")
        ObFrame(
            step = if (reconsent) null else 1, title = if (reconsent) "약관이 바뀌었어요" else "이렇게만 써요", sub = sub, onBack = onBack,
            art = { DataPath() },
            scroll = true,
            cta = "동의하고 계속", ctaEnabled = left == 0,
            onCta = {
                val now = System.currentTimeMillis()
                // 원래 동의 저장소를 그대로 쓴다 — 거기 동의가 있어야 아이가 말할 수 있다 (개인정보보호법 제22조의2)
                ConsentStore.agree()
                ConsentStore.setTypecastVoice(on(TermsDoc.TYPECAST_VOICE))      // 선택 — 기본 꺼짐 (10-06)
                Shell.saveConsent(TERMS_VERSION, news = on(TermsDoc.NEWS), at = now)
                scope.launch {
                    Accounts.api.recordConsent(ConsentRecord(
                        terms = on(TermsDoc.TERMS), privacy = on(TermsDoc.GUARDIAN_INFO), guardian = on(TermsDoc.CHILD_INFO),
                        marketing = on(TermsDoc.NEWS), at = now, overseas = on(TermsDoc.OVERSEAS),
                        typecastVoice = on(TermsDoc.TYPECAST_VOICE),
                        version = TERMS_VERSION,
                    ))
                }
                // 광고성 정보 수신에 동의했으면 **동의한 날을 알린다** (정보통신망법 제50조)
                if (on(TermsDoc.NEWS)) android.widget.Toast.makeText(ctx, "${dateText(now)} 소식 알림 받기에 동의했어요. 부모 영역 → 계정에서 끌 수 있어요", android.widget.Toast.LENGTH_LONG).show()
                onDone()
            },
            // 거절할 길 — 동의 버튼과 같은 크기 · 같은 줄 (눈속임 설계 금지). 누르면 무엇이 되는지 먼저 알려 준다
            cta2 = "동의하지 않음", onCta2 = { asking = true },
        ) {
            // 전체 동의 카드
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (all) FeltTeal.copy(alpha = 0.12f) else FeltWhite)
                    .border(1.5.dp, if (all) FeltTeal else InkBrown.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .clickable { val v = !all; TermsDoc.entries.forEach { agreed[it] = v } }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(all, big = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("약관에 모두 동의해요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBrown)
                    Text("선택 항목도 포함해요. 선택은 동의하지 않아도 모든 기능을 쓸 수 있어요.", fontSize = 11.sp, color = InkSoft, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            TermsDoc.entries.forEach { d ->
                TermsLine(d, on(d), onToggle = { agreed[d] = !on(d) }, onOpen = { reading = d })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (left > 0) "필수 항목 ${left}개에 더 동의하면 계속할 수 있어요" else "필수 항목에 모두 동의했어요",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (left > 0) FeltCoral else FeltTeal,
            )
            Text("동의는 부모 영역에서 언제든 철회할 수 있어요 · 전체 처리방침은 부모 영역 → 계정", fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 2.dp))
        }
        // 전문 보기 — 동의 화면 위에 펼친다. 「동의하고 닫기」를 누르면 그 항목이 체크된다(카카오 · 토스 방식)
        reading?.let { d -> TermsSheet(d, agreed = on(d), onAgree = { agreed[d] = true; reading = null }, onClose = { reading = null }) }
        if (asking) DeclineDialog(onStay = { asking = false }, onLeave = onDecline)
    }
}

/** 동의 한 줄 — [체크] [필수/선택] 이름 · 한 줄 풀이 · 전문 보기 › */
@Composable
private fun TermsLine(d: TermsDoc, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable { onToggle() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(2.dp))
            CheckBox(checked)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (d.required) FeltCoral else FeltTeal,
                        modifier = Modifier.border(1.dp, if (d.required) FeltCoral else FeltTeal, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(d.short, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkBrown, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text(d.plain, fontSize = 11.sp, color = InkSoft, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { onOpen() }.semantics { contentDescription = "${d.short} 전문 보기" },
            contentAlignment = Alignment.Center,
        ) { Text("›", fontSize = 22.sp, color = InkSoft) }
    }
}

@Composable
private fun CheckBox(checked: Boolean, big: Boolean = false) {
    Box(
        Modifier.size(if (big) 26.dp else 22.dp).clip(CircleShape)
            .background(if (checked) FeltTeal else FeltWhite)
            .border(1.5.dp, if (checked) FeltTeal else InkBrown.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("✓", fontSize = if (big) 15.sp else 13.sp, color = if (checked) FeltWhite else InkBrown.copy(alpha = 0.2f), fontWeight = FontWeight.Bold) }
}

/** 약관 전문 — 화면 전체를 덮고 위아래로 읽는다. 표는 머리 · 내용 두 칸 */
@Composable
fun TermsSheet(d: TermsDoc, agreed: Boolean, onAgree: (() -> Unit)?, onClose: () -> Unit) = ParentText {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.35f)).noRippleClickable { }) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.82f).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(22.dp)).background(Wool),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("[${d.tag}] ${d.short}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }.semantics { contentDescription = "닫기" }, contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 18.sp, color = InkSoft)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(InkBrown.copy(alpha = 0.1f)))
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text(d.plain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FeltTeal)
                Spacer(Modifier.height(8.dp))
                d.parts.forEach { p ->
                    Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkBrown, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    if (p.body.isNotEmpty()) Text(p.body, fontSize = 13.sp, color = InkBrown, lineHeight = 20.sp)
                    if (p.rows.isNotEmpty()) Column(
                        Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, InkBrown.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    ) {
                        p.rows.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(InkBrown.copy(alpha = 0.08f)))
                            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                                Text(r.head, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkBrown,
                                    modifier = Modifier.width(150.dp).fillMaxHeight().background(WoolCream).padding(horizontal = 10.dp, vertical = 8.dp))
                                Text(r.body, fontSize = 12.sp, color = InkBrown, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("약관 판 $TERMS_VERSION", fontSize = 11.sp, color = InkSoft)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                if (onAgree != null && !agreed) {
                    PBtn("닫기", onClose, Modifier.width(120.dp), primary = false, height = 46.dp)
                    PBtn("동의하고 닫기", onAgree, Modifier.width(180.dp), height = 46.dp)
                } else PBtn("닫기", onClose, Modifier.width(140.dp), height = 46.dp)
            }
        }
    }
}

/** 「동의하지 않음」 — 앱을 닫기 전에 무엇이 되는지 알려 준다. 돌아가기가 먼저(실수로 누른 보호자) */
@Composable
private fun DeclineDialog(onStay: () -> Unit, onLeave: () -> Unit) = ParentText {
    androidx.activity.compose.BackHandler(onBack = onStay)
    Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.35f)).noRippleClickable { }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(420.dp).clip(RoundedCornerShape(22.dp)).background(Wool).padding(24.dp)) {
            Text("동의하지 않고 나갈까요?", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(8.dp))
            Text("필수 항목에 동의하지 않으면 오또를 쓸 수 없어 앱을 닫아요. 아무것도 저장하지 않고, 다음에 켜면 다시 물어요.", fontSize = 13.sp, color = InkSoft, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                PBtn("앱 닫기", onLeave, Modifier.width(130.dp), primary = false, height = 46.dp)
                PBtn("돌아가서 보기", onStay, Modifier.width(160.dp), height = 46.dp)
            }
        }
    }
}

/** 「2026년 10월 5일」 */
fun dateText(at: Long): String = java.text.SimpleDateFormat("yyyy년 M월 d일", java.util.Locale.KOREA).format(java.util.Date(at))

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
        // 휴대폰 권한이 이미 켜져 있어도(같은 폰의 다른 계정 · 다시 가입) 이 보호자에게 **직접 동의를 받는다** — 「다음」으로 흘려보내지 않는다 (10-05)
        title = when (state) { "on" -> "마이크 사용에 동의해 주세요"; "denied" -> "마이크가 꺼져 있어요"; else -> "마이크를 켜 주세요" },
        sub = when (state) {
            "on" -> "휴대폰 권한은 이미 켜져 있어요. 이 보호자 계정으로 오또가 아이 목소리를 듣는 데 동의해 주세요."
            "denied" -> "설정에서 언제든 켤 수 있어요. 켜지 않아도 그림을 골라서 만들 수 있어요."
            else -> "다음에 뜨는 휴대폰 창에서 '허용'을 눌러 주세요."
        },
        onBack = onBack,
        art = { Otto(Pose.LISTEN, Modifier.size(190.dp)) },
        cta = when (state) { "on" -> "동의하고 계속"; "denied" -> "설정에서 켜기"; else -> "마이크 켜기" },
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
        // 🛡 줄 — 10-06: 「녹음은 폰 밖으로 나가지 않아요」는 사실과 달랐다(음성은 글자로 바꾸려고 우리 서버까지 간다 · net/Server.kt /stt).
        //    이 화면이 markMicNoticeShown() 을 불러 정확한 고지(ui/Consent.kt 「우리 서버까지만」)가 다시 안 뜨므로 여기서 바로 말한다 (guidelines/1 §1-5)
        listOf("👂" to "말할 차례에만 들어요 — 오또가 귀를 쫑긋할 때", "🛡" to "소리는 글자로 바꾸려고 우리 서버까지만 가고 바로 지워요", "✋" to "안 켜도 그림을 골라서 만들 수 있어요").forEach { (ic, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(32.dp).felt(FeltTeal, CircleShape, lift = 0.dp, stitch = false), contentAlignment = Alignment.Center) { Text(ic, fontSize = 15.sp) }
                Spacer(Modifier.width(12.dp))
                Text(t, fontSize = 13.sp, color = InkBrown)
            }
        }
    }
}

/** 이 앱의 설정 화면(권한 켜기) */
internal fun openAppSettings(ctx: android.content.Context) = runCatching {
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
        Text("이제 아이에게 건네주세요. 오또가 방을 한 바퀴 돌며 물건 넷을 소개하고, 말하기 연습을 한 번 해요.", fontSize = 15.sp, color = InkSoft, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Text("부모 설정은 언제든 왼쪽 위 자물쇠를 누르고 방금 정한 비밀번호를 넣으면 열려요.", fontSize = 12.sp, color = InkSoft, lineHeight = 18.sp)
    }
}

// ── ⑥ 튜토리얼 ② 말해 보기 — 틀려도 괜찮은 연습 ──────────────────────────

@Composable
fun TutorialTalk(onDone: () -> Unit) {
    var said by remember { mutableStateOf(false) }
    val line = if (said) "잘했어! 이렇게 말하면 오또가 이야기를 이어 가" else "좋아하는 동물을 말해 줄래?"
    OttoSays(line)
    Box(Modifier.fillMaxSize()) {
        AssetImage("bg_park", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Wool)) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, InkBrown.copy(alpha = 0.25f)))))
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp).felt(FeltMustard, RoundedCornerShape(16.dp), lift = 3.dp, stitch = false).padding(horizontal = 16.dp, vertical = 5.dp),
        ) { Text("✨ 연습", fontSize = 15.sp, color = FeltWhite) }
        // 연습 버튼도 실제처럼 나레이션 칸 **안** 오른쪽에 (09-29)
        Narration(
            mode = NarrationMode.STORY, state = if (said) com.example.finalproject_demo.ui.OttoState.TALK else com.example.finalproject_demo.ui.OttoState.LISTEN,
            line = line,
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
