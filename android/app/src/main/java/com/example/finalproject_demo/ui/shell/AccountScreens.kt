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
        Line("로그인", if (g == null) "로그인 안 함 · 샘플 책 보기" else "${g.provider.label} · ${g.email}")
        Line("가입한 날", g?.let { SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(it.since)) } ?: "—")
        Line("개인정보처리방침", "보기") { open() }
        Line("이용약관", "보기") { open() }
        Line("부모 비밀번호", if (Shell.hasPin) "바꾸기" else "정하기") { Shell.sheet = Sheet.PIN_CHANGE }
        Line("처음 설정 다시 보기", "로그인 · 동의 · 맞춤 설정") { d.send(Reply.Tapped("home", "처음으로")); Shell.redoOnboarding() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (g != null) PBtn("로그아웃", {
                scope.launch { Accounts.api.logout(); Accounts.guardian = null }
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
    var checked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val books = d.s.shelf.size
    when (Shell.sheet) {
        Sheet.WITHDRAW_INFO -> ObFrame(
            step = null, title = "회원 탈퇴 · 데이터 삭제",
            sub = "사유는 묻지 않아요. 앱을 지운 뒤에는 웹 페이지에서도 삭제를 요청할 수 있어요.",
            onBack = { Shell.sheet = Sheet.NONE },
            art = { Box(Modifier.size(110.dp).felt(FeltCoral, CircleShape), contentAlignment = Alignment.Center) { Text("👤", fontSize = 48.sp) } },
            cta = "다음 — 본인 확인", ctaEnabled = checked, onCta = { Shell.sheet = Sheet.WITHDRAW_CONFIRM },
            cta2 = "취소", onCta2 = { Shell.sheet = Sheet.NONE },
        ) {
            Text("탈퇴하면 이렇게 돼요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(12.dp))
            Item("👤", "보호자 계정과 기록이 바로 삭제돼요")
            Item("📚", "이 폰에 있는 책 ${books}권 · 그림 · 녹음")
            CheckRow(Shell.wipeLocal, "폰 안의 책 · 그림 · 녹음도 함께 지우기", { Shell.wipeLocal = !Shell.wipeLocal })
            CheckRow(checked, "안내를 모두 확인했어요", { checked = !checked })
        }
        Sheet.WITHDRAW_CONFIRM -> ObFrame(
            step = null, title = "마지막 확인", sub = "끝나면 처음 설치한 상태(⓪ CLAP)로 돌아가요.",
            onBack = { Shell.sheet = Sheet.WITHDRAW_INFO },
            art = { Box(Modifier.size(110.dp).felt(FeltCoral, CircleShape), contentAlignment = Alignment.Center) { Text("⚠️", fontSize = 48.sp) } },
            cta = if (busy) "지우는 중…" else "삭제하기", ctaEnabled = !busy,
            onCta = {
                busy = true
                scope.launch {
                    // 서버 계정 즉시 삭제 → 동의 철회 → (고르면) 폰 데이터 삭제 → 처음 상태로
                    Accounts.api.deleteAccount()
                    Accounts.guardian = null
                    ConsentStore.withdraw()
                    if (Shell.wipeLocal) d.s.shelf.clear()   // TODO: 책 · 그림 · 녹음을 파일로 저장하게 되면 그 파일도 지운다
                    d.send(Reply.Tapped("home", "처음으로"))
                    Shell.resetToFirstRun()
                }
            },
            cta2 = "취소", onCta2 = { Shell.sheet = Sheet.NONE },
        ) {
            Text("정말 삭제할까요?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.height(8.dp))
            // TODO(서버): 소셜 계정이면 여기서 한 번 더 로그인해 본인인지 확인한다 (지금은 SDK 가 없어 건너뜀)
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
        }
        Sheet.NONE, Sheet.PIN_CHANGE -> {}
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
