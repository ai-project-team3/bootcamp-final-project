package com.example.finalproject_demo.ui.shell

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.net.Accounts
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.ConsentStore
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.PRIVACY_URL
import com.example.finalproject_demo.ui.felt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 부모 영역 · 계정 · 탈퇴 ① 안내 · ② 최종 확인 (09-29 · `.pen` 「부모 영역 · 계정」 · 「탈퇴 ①②」)
 *
 * Google Play 요건: 앱 안에서 계정 삭제를 시작할 수 있어야 한다. 사유는 묻지 않는다.
 * 서버 계정 삭제는 `Accounts.api.deleteAccount()` — 지금은 폰 안의 가짜(net/Account.kt).
 */

/** 부모 영역 「계정」 탭 — 로그인 정보 · 로그아웃(데이터 유지) · 맨 아래 작은 회원 탈퇴 */
@Composable
fun AccountTab(d: Director) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Accounts.guardian
    fun open() = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Line("로그인", if (g == null) "로그인 안 함 · 샘플 책 보기" else "${g.provider.label} · ${g.email}" + (if (g.dev) " (개발용)" else ""))
        Line("가입한 날", g?.let { SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(it.since)) } ?: "—")
        Line("이용약관", "보기") { Shell.doc = TermsDoc.TERMS }
        Line("개인정보처리방침 (전체)", "웹에서 보기") { open() }
        // 선택 동의 — 언제든 바꿀 수 있어야 한다. 소식 알림은 동의한 날을 보여 준다 (정보통신망법 제50조)
        Line("소식 알림 받기", Shell.newsSince?.let { "${dateText(it)} 동의 · 끄기" } ?: "받지 않음 · 켜기") {
            Shell.setNews(Shell.newsSince == null)
        }
        // 오또 목소리(타입캐스트) — 제3자 제공 선택 동의. 켤 때는 내용을 읽고 「동의하고 닫기」로만 켠다 (10-06)
        Line("오또 목소리 · 타입캐스트", if (ConsentStore.typecastVoiceAgreed) "동의함 · 끄기" else "기본 목소리 · 켜기") {
            if (ConsentStore.typecastVoiceAgreed) ConsentStore.setTypecastVoice(false) else Shell.doc = TermsDoc.TYPECAST_VOICE
        }
        Line("부모 비밀번호", if (Shell.hasPin) "바꾸기" else "정하기") { Shell.sheet = Sheet.PIN_CHANGE }
        Line("기능 안내 다시 보기", "동화 · 그림일기 · 같이 만들기 · 책장 · 부모 영역") { Shell.guide = true }
        // 테스트 빌드만 (10-08 #319) — 처음 설정의 진행 기록만 지우고 처음부터 다시 본다. 책 · 그림 · 녹음은 그대로
        if (Shell.redoAvailable) Line("처음 설정 다시 하기 (테스트용)", "로그인부터 튜토리얼까지") {
            d.send(Reply.Tapped("home", "처음으로")); Shell.redoOnboarding()
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (g != null) PBtn("로그아웃", {
                scope.launch { Accounts.logout(ctx) }      // 카카오 · 네이버 · Google 세션도 끊는다
                // 다시 로그인하면 마이크를 다시 묻는다 — 앱 안 동의는 지우고, 안드로이드 13+ 는 휴대폰 권한도 돌려준다 (10-05)
                ConsentStore.forgetMic(); Shell.giveBackMic(ctx)
                d.send(Reply.Tapped("home", "처음으로"))
                Shell.step = Step.LOGIN          // 로그아웃해도 폰 안의 책은 남는다 → 로그인 화면으로
            }, Modifier.width(160.dp), primary = false, height = 44.dp)
            else PBtn("로그인하기", { d.send(Reply.Tapped("home", "처음으로")); Shell.step = Step.LOGIN }, Modifier.width(160.dp), height = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text("로그아웃해도 폰 안의 책은 남아요", fontSize = 12.sp, color = InkSoft)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "회원 탈퇴 · 데이터 삭제", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSoft,
            modifier = Modifier.clickable { Shell.wipeLocal = false; Shell.sheet = Sheet.WITHDRAW_INFO }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Line(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(FeltWhite)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkBrown, modifier = Modifier.weight(1f))
        Text(if (onClick != null) "$value  ›" else value, fontSize = 13.sp, color = InkSoft)
    }
}

/** 부모 비밀번호 바꾸기 — 지금 번호 확인 → 새 번호 두 번. 정한 적이 없으면 바로 새 번호 */
@Composable
fun PinChangeSheet() {
    var verified by remember { mutableStateOf(!Shell.hasPin) }
    var done by remember { mutableStateOf(false) }
    ObFrame(
        step = null, title = if (done) "비밀번호를 바꿨어요" else "부모 비밀번호 바꾸기",
        sub = "비밀번호는 이 폰 안에만 저장돼요. 잊으면 보호자 태어난 해로 다시 정할 수 있어요.",
        onBack = { Shell.sheet = Sheet.NONE },
        art = { AssetImage("pi_lock", Modifier.size(160.dp)) { Text("🔒", fontSize = 60.sp) } },
        cta = if (done) "닫기" else null, onCta = { Shell.sheet = Sheet.NONE },
    ) {
        Spacer(Modifier.height(8.dp))
        when {
            done -> Text("다음부터 새 번호로 부모 영역이 열려요.", fontSize = 15.sp, color = InkBrown)
            !verified -> PinPad("지금 비밀번호", "바꾸기 전에 지금 번호를 확인해요", onDone = { ok -> Shell.checkPin(ok).also { if (it) verified = true } })
            else -> PinCreate(onSet = { Shell.setPin(it); done = true })
        }
    }
}

/** 탈퇴 ① · ② — 부모 영역 위에 뜨는 큰 창 */
@Composable
fun WithdrawSheet(d: Director) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val books = d.s.shelf.size
    when (Shell.sheet) {
        Sheet.WITHDRAW_INFO -> ObFrame(
            step = null, title = "회원 탈퇴 · 데이터 삭제",
            sub = "사유는 묻지 않아요. 앱을 지운 뒤에는 웹 페이지에서도 삭제를 요청할 수 있어요.",
            onBack = { Shell.sheet = Sheet.NONE },
            art = { Box(Modifier.size(110.dp).felt(FeltCoral, CircleShape), contentAlignment = Alignment.Center) { Text("👤", fontSize = 48.sp) } },
            cta = "다음 — 본인 확인", ctaEnabled = checked, onCta = { Shell.sheet = Sheet.WITHDRAW_VERIFY },
            cta2 = "취소", onCta2 = { Shell.sheet = Sheet.NONE },
        ) {
            Text("탈퇴하면 이렇게 돼요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(12.dp))
            Item("👤", "보호자 계정과 기록이 바로 삭제돼요")
            Item("📚", if (Shell.wipeLocal) "이 폰의 책 ${books}권 · 그림 · 녹음 · 아이 이름도 지워요" else "이 폰의 책 ${books}권 · 그림 · 녹음은 남아요")
            CheckRow(Shell.wipeLocal, "폰 안의 책 · 그림 · 녹음도 함께 지우기", { Shell.wipeLocal = !Shell.wipeLocal })
            CheckRow(checked, "안내를 모두 확인했어요", { checked = !checked })
        }
        Sheet.WITHDRAW_VERIFY -> WithdrawVerify(onPass = { Shell.sheet = Sheet.WITHDRAW_CONFIRM })
        Sheet.WITHDRAW_CONFIRM -> ObFrame(
            step = null, title = "마지막 확인", sub = "끝나면 처음 설치한 상태(⓪ CLAP)로 돌아가요.",
            onBack = { Shell.sheet = Sheet.WITHDRAW_VERIFY },
            art = { Box(Modifier.size(110.dp).felt(FeltCoral, CircleShape), contentAlignment = Alignment.Center) { Text("⚠️", fontSize = 48.sp) } },
            cta = if (busy) "지우는 중…" else "삭제하기", ctaEnabled = !busy,
            onCta = {
                busy = true
                scope.launch {
                    // 서버 계정 즉시 삭제 → 동의 철회 → (고르면) 폰 데이터 삭제 → 처음 상태로
                    Accounts.withdraw(ctx)          // 소셜이면 그 회사와 앱의 연결도 끊는다(unlink)
                    // 휴대폰의 마이크 권한도 돌려준다 — 다시 가입하면 권한 창부터 다시 뜬다 (10-05).
                    // 안드로이드 13+ 만 앱이 스스로 권한을 물릴 수 있다(앱이 다음에 꺼질 때 적용). 12 이하는 앱 안의 마이크 동의만 다시 받는다
                    Shell.giveBackMic(ctx)
                    ConsentStore.withdraw()
                    // 고르면 폰에 저장된 책 · 그림 · 녹음 · 아이 이름까지 파일째 지운다 (10-05 — 전엔 화면 목록만 비워 다시 켜면 돌아왔다)
                    val wiped = Shell.wipeLocal
                    if (wiped) { LocalWipe.wipe(ctx); d.s.shelf.clear() }
                    d.send(Reply.Tapped("home", "처음으로"))
                    Shell.resetToFirstRun()
                    // 흐름 · 책장이 메모리에 쥐고 있던 책까지 놓도록 화면을 새로 띄운다(지운 저장소에서 다시 읽는다)
                    if (wiped && !com.example.finalproject_demo.ui.motionFrozen) (ctx as? android.app.Activity)?.recreate()
                }
            },
            cta2 = "취소", onCta2 = { Shell.sheet = Sheet.NONE },
        ) {
            Text("정말 삭제할까요?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(8.dp))
            Text("${Accounts.guardian?.provider?.label ?: "보호자"} 계정을 삭제해요.", fontSize = 14.sp, color = InkSoft)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFBE3DF))
                    .border(1.5.dp, FeltCoral, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠️", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                Text(
                    "삭제하면 되돌릴 수 없어요. 계정 · 기록${if (Shell.wipeLocal) " · 이 폰의 책 ${books}권" else ""}이 지워져요.",
                    fontSize = 13.sp, color = InkBrown, lineHeight = 19.sp,
                )
            }
            // 안드로이드 12 이하는 앱이 마이크 권한을 스스로 끌 수 없다 — 다시 가입할 때 권한 창이 뜨게 하려면 보호자가 직접 (10-05)
            if (Shell.micStuckOn(ctx)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("휴대폰 마이크 권한은 이 폰에서 직접 꺼야 해요", fontSize = 12.sp, color = InkSoft, modifier = Modifier.weight(1f))
                    PBtn("설정에서 끄기", { openAppSettings(ctx) }, Modifier.width(150.dp), primary = false, height = 40.dp)
                }
            }
        }
        Sheet.NONE, Sheet.PIN_CHANGE -> {}
    }
}

/**
 * 탈퇴 ② 본인 확인 (10-05) — 이메일 계정은 **그 계정 비밀번호**, 소셜 계정은 **부모 비밀번호(PIN)**.
 *
 * 다른 앱들도 소셜 재로그인까지는 잘 요구하지 않고(비밀번호 재입력 · 앱 PIN 이 흔하다), 소셜 재로그인은 스토어 패키지 키가
 * 등록되기 전에는 실패해 **탈퇴 자체를 막는다** — Play 는 앱 안 탈퇴를 막으면 안 된다. 그래서 어떤 경우에도 막히지 않게:
 *  - 이메일 비밀번호를 잊었으면 부모 비밀번호로 대신 확인할 수 있다
 *  - 부모 비밀번호를 정한 적이 없으면(옛 설치) 확인 없이 다음으로 — 부모 영역은 이미 어른 확인을 거쳤다
 */
@Composable
private fun WithdrawVerify(onPass: () -> Unit) {
    val scope = rememberCoroutineScope()
    val g = Accounts.guardian
    var usePin by remember { mutableStateOf(g?.provider != com.example.finalproject_demo.net.AuthProvider.EMAIL) }
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(usePin) { if (usePin && !Shell.hasPin) onPass() }
    fun check() {
        if (busy || g == null) return
        busy = true
        scope.launch {
            if (Accounts.api.verifyPassword(g.email, pw)) onPass()
            else {
                wrong++; pw = ""
                msg = if (wrong >= 3) "비밀번호가 ${wrong}번 맞지 않았어요. 잊었으면 아래 「부모 비밀번호로 확인」을 눌러 주세요" else "비밀번호가 맞지 않아요"
            }
            busy = false
        }
    }
    ObFrame(
        step = null, title = "본인 확인",
        sub = if (usePin) "탈퇴는 되돌릴 수 없어서, 보호자인지 한 번 더 확인해요." else "탈퇴는 되돌릴 수 없어서, 가입한 이메일 계정의 비밀번호를 한 번 더 확인해요.",
        onBack = { Shell.sheet = Sheet.WITHDRAW_INFO },
        art = { AssetImage("pi_lock", Modifier.size(150.dp)) { Text("🔒", fontSize = 56.sp) } },
        cta = if (usePin) null else if (busy) "확인 중…" else "확인", ctaEnabled = pw.isNotEmpty() && !busy, onCta = { check() },
        cta2 = "취소", onCta2 = { Shell.sheet = Sheet.NONE },
    ) {
        if (usePin) {
            PinPad("부모 비밀번호", "처음 설정에서 정한 네 자리를 넣어 주세요", onDone = { Shell.checkPin(it).also { ok -> if (ok) onPass() } })
        } else {
            Text("${g?.email ?: ""} 계정", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(10.dp))
            Field("비밀번호", pw, "이메일 계정 비밀번호", androidx.compose.ui.text.input.KeyboardType.Password, !show,
                trailing = if (show) "숨기기" else "보기", onTrailing = { show = !show }) { pw = it; msg = null }
            msg?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = FeltCoral, lineHeight = 17.sp) }
            Spacer(Modifier.height(10.dp))
            if (Shell.hasPin) Text(
                "비밀번호가 기억나지 않아요 — 부모 비밀번호로 확인", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkSoft,
                modifier = Modifier.clickable { usePin = true; msg = null }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Item(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(34.dp).felt(FeltCoral, CircleShape, lift = 0.dp, stitch = false), contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = InkBrown)
    }
}
