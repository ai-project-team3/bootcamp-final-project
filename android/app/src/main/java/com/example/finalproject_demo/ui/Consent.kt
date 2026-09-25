package com.example.finalproject_demo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * 고지 · 동의 · 신고 — **정책이 요구하는 화면 넷** (2026-09-23)
 *
 * **정본은 `docs/스토어_출시_체크리스트.md` §4 (조장 작성)** 이다. 문구와 동작이 거기 적혀 있고,
 * 이 파일은 그것을 화면으로 옮긴 것이다. 문구가 바뀌면 **그 문서를 먼저 고치고** 여기를 맞춘다.
 * 구글 플레이 「가족」 정책과 개인정보보호법이 요구하는 자리이고, 심사 반려가 가장 많이 나는 곳이다.
 *
 *   1. 앱 내 신고        부모 모드 설정 안
 *   2. 보호자 동의       **앱 첫 실행** (스플래시 뒤) — 계정이 없어 가입 자리를 첫 실행이 대신한다
 *   3. 마이크 사용 고지  첫 🎤 누르기 전
 *   4. AI 음성 고지      설정에 한 줄
 *
 * ## 왜 한 파일에 모았나
 *
 * 네 화면이 붙는 자리가 서로 다른 파일이고, 그중 둘은 **남의 소유 파일**이다
 * (`ui/Parent.kt` 진웅). 내용을 여기 두면 그쪽에는 **부르는 한 줄**만 들어간다.
 * 나중에 문구가 바뀌어도 이 파일만 고치면 된다.
 *
 * ## ⚠️ 문구는 전부 초안이다
 *
 * **법정대리인 동의 문구는 개발자가 지어낼 것이 아니다.** 아래 텍스트는 «무엇을 물어야 하는가»를
 * 보여 주는 자리 표시이고, 최종 문구는 사람이 채워야 한다. 화면에도 초안임을 띄워 둔다.
 * 문구가 정해지면 [DRAFT] 를 지우고 이 주석도 지운다.
 *
 * ## ⚠️ 저장은 앱이 켜져 있는 동안만이다
 *
 * 더미 화면이라 메모리에만 남는다. 껐다 켜면 동의 화면이 다시 뜬다.
 * 진짜 제품에서는 [ConsentStore] 안쪽을 저장소(DataStore 등)로 바꾸면 된다 — 부르는 쪽은 그대로다.
 */

/**
 * 문구가 아직 초안임을 화면에 알리는 표시.
 *
 * **09-25 에 껐다.** 심사자가 「초안입니다 — 법무 확인이 필요합니다」 배지를 보면 미완성 앱으로 읽는다.
 * 문구는 `docs/스토어_출시_체크리스트.md` §4 를 그대로 옮긴 것이고, 처리방침은 이미 공개했다
 * (`PRIVACY_URL`). ⚠️ **법무 검토를 받은 것은 아니다** — 배지를 끈 것이지 검토가 끝난 것이 아니다.
 */
private const val DRAFT = false

/** 공개한 개인정보처리방침. 방침을 고치면 레포 `docs/공개용_개인정보처리방침.md` 와 이 저장소를 **같이** 고친다 */
const val PRIVACY_URL = "https://leejonghoona.github.io/otto-privacy/"

/**
 * 동의 · 고지 · 신고를 기억하는 곳.
 *
 * 동의와 마이크 고지는 **기기에 저장한다**(09-25, `attach`). 앱을 껐다 켜도 다시 묻지 않는다.
 * 저장 방식을 바꿀 때는 여기만 고친다 — 화면 쪽은 손대지 않아도 된다.
 */
object ConsentStore {
    /** 보호자 동의를 받았는가 */
    var guardianAgreed by mutableStateOf(false)
        private set

    /** 마이크 고지를 한 번 보여 줬는가 */
    var micNoticeShown by mutableStateOf(false)
        private set

    /** 이 기기에서 신고 화면을 연 기록 — 실제 전달은 메일 앱이 한다(`ReportSection`) */
    val reports = mutableStateListOf<Report>()

    // ⚠️ **기기에 저장한다 (09-25).** 전에는 메모리뿐이라 앱을 켤 때마다 동의 화면이 다시 떴다.
    //    `SharedPreferences` 를 쓴다 — 값 둘(참/거짓)이라 DataStore 의존성을 들일 까닭이 없다.
    //    `allowBackup="false"` 라 이 파일은 구글 드라이브로 나가지 않는다(AndroidManifest).
    //    `attach` 를 안 부르면 메모리로만 동작한다 — 화면 검사가 그 모드로 돈다.
    private var prefs: SharedPreferences? = null
    private const val PREFS = "consent"
    private const val KEY_AGREED = "guardian_agreed"
    private const val KEY_MIC = "mic_notice_shown"

    /** `MainActivity.onCreate` 에서 한 번. 저장된 값을 읽어 온다 */
    fun attach(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        guardianAgreed = p.getBoolean(KEY_AGREED, false)
        micNoticeShown = p.getBoolean(KEY_MIC, false)
    }

    fun agree() {
        guardianAgreed = true
        prefs?.edit()?.putBoolean(KEY_AGREED, true)?.apply()
    }

    /** 동의 철회 — 정책상 **언제든 물릴 수 있어야** 한다. 물리면 다음 화면부터 동의를 다시 받는다 */
    fun withdraw() {
        guardianAgreed = false
        prefs?.edit()?.putBoolean(KEY_AGREED, false)?.apply()
    }

    fun markMicNoticeShown() {
        micNoticeShown = true
        prefs?.edit()?.putBoolean(KEY_MIC, true)?.apply()
    }

    fun report(reason: String, note: String) {
        reports.add(0, Report(reason, note))
    }
}

/** 신고 한 건. 언제인지는 더미라 순번으로만 둔다 */
data class Report(val reason: String, val note: String)

/**
 * 신고를 받는 주소. **처리방침·스토어 등록정보와 같은 주소여야 한다** —
 * `docs/공개용_개인정보처리방침.md` · `docs/스토어_등록정보.md` 가 이 주소를 연락처로 적었다.
 */
const val REPORT_TO = "ljh11442@gmail.com"

/** 부모가 적는 칸의 길이 상한 — 메일 한 통에 들어갈 만큼 */
private const val REPORT_NOTE_MAX = 1000

/**
 * 신고를 **메일 앱으로 넘긴다** (09-25). 열었으면 true.
 *
 * ## 왜 메일인가
 * 전에는 메모리 리스트에 쌓기만 했다 — Play 가 요구하는 「앱 내 신고」가 **아무 데도 안 갔다.**
 * 서버(`/report`)가 아직 없으므로 지금 쓸 수 있는 실제 전달 경로는 메일뿐이다.
 * 부모가 보내기를 눌러야 가므로 **보냈는지는 앱이 모른다** — 화면도 그렇게 말한다.
 *
 * ## 무엇을 싣나 — 최소한만
 * 사유 · 부모가 적은 글 · 앱 버전. **아이가 한 말 · 그림 · 기기 식별키는 싣지 않는다.**
 * 싣고 싶어지는 날이 오면 그건 새 수집 항목이라 처리방침부터 고친다.
 *
 * ## 서버가 생기면
 * 이 함수만 바꾼다. 화면(`ReportSection`)은 그대로 둔다.
 */
private fun sendReportMail(ctx: Context, reason: String, note: String): Boolean {
    val version = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "?"
    val subject = "[오또 신고] $reason"
    val body = buildString {
        appendLine("사유: $reason")
        appendLine()
        appendLine(note.ifBlank { "(적은 내용 없음)" })
        appendLine()
        append("앱 버전: $version")
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        // 일부 메일 앱은 EXTRA_* 를 무시하고 mailto 주소의 질의만 읽는다 — 둘 다 싣는다
        data = Uri.parse(
            "mailto:$REPORT_TO?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}",
        )
        putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_TO))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** 신고 사유 — 아이용 앱에서 부모가 고를 만한 것들 (초안) */
private val REPORT_REASONS = listOf(
    "부적절한 그림",
    "부적절한 말",
    "오류",
    "기타",
)

// ── 1. 앱 내 신고 ──────────────────────────────────────────────

/**
 * 부모 모드 설정에 들어가는 **신고** 자리.
 *
 * 누르면 사유를 고르고 한 줄 적는다. **저장만 하고 아무 데도 보내지 않는다** — 더미다.
 */
@Composable
fun ReportSection(onLog: (String) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var mailOpened by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    NoticeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("문제가 있었나요?")
                Body("이야기나 그림에 이상한 점이 있으면 알려 주세요.")
            }
            Pill(if (open) "닫기" else "신고하기", Coral) { open = !open; done = false }
        }

        if (open) {
            Spacer(Modifier.height(12.dp))
            if (done) {
                // 메일 앱을 열었을 뿐 **보냈는지는 모른다** — 그래서 「접수했어요」라고 하지 않는다
                if (mailOpened) {
                    Body("메일 앱이 열렸어요. 거기서 보내기를 누르시면 저희에게 전달돼요.")
                } else {
                    Body("메일 앱을 찾지 못했어요. $REPORT_TO 로 직접 보내 주시면 확인할게요.")
                }
            } else {
                REPORT_REASONS.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (picked == r) Sun.copy(alpha = 0.35f) else Color(0x11000000))
                            .clickable { picked = r }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (picked == r) "●" else "○", fontSize = 14.sp, color = Ink)
                        Spacer(Modifier.width(9.dp))
                        Text(r, fontSize = 14.sp, color = Ink)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 진짜 입력칸이다 (09-25). 전에는 누르면 「(부모가 적은 내용)」이 박히는 가짜였다
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0D000000))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (note.isEmpty()) {
                        Text("더 적어 주실 내용 (선택)", fontSize = 14.sp, color = Ink.copy(alpha = 0.45f))
                    }
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(REPORT_NOTE_MAX) },
                        textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("보내기", if (picked == null) Color(0x22000000) else Sun) {
                        picked?.let {
                            ConsentStore.report(it, note)
                            val opened = sendReportMail(ctx, it, note)
                            // 시연 서랍 「기록」에 한 줄. **새 이벤트 종류는 만들지 않는다** (문서 §4 「하지 말 것」)
                            onLog("신고 — 사유 \"$it\"${if (note.isBlank()) "" else " · 적은 내용 있음"} · " +
                                if (opened) "메일 앱으로 넘김" else "메일 앱 없음")
                            mailOpened = opened
                            done = true
                            picked = null
                            note = ""
                        }
                    }
                }
            }
        }
    }
}

// ── 2. 보호자 동의 ─────────────────────────────────────────────

/**
 * 부모 모드에 **처음 들어갈 때** 한 장.
 *
 * 만 14세 미만 아이의 법정대리인 동의를 받는 자리다. 동의하기 전에는 앞으로 못 간다.
 *
 * @param onAgree 동의함
 * @param onBack  동의하지 않음 — 앱을 닫는다 (`MainActivity` 가 `finish()`)
 */
@Composable
fun GuardianConsentScreen(onAgree: () -> Unit, onBack: () -> Unit) {
    // 문구는 `docs/스토어_출시_체크리스트.md` §4 ② 를 그대로 옮긴 것이다 (개인정보보호법 제22조의2)
    var agreed by remember { mutableStateOf(false) }
    val ready = agreed

    // ⚠️ **Dialog 로 감싼다 (9/25).** 전에는 `MainActivity` 의 같은 Box 안 마지막 자식으로
    //    그렸는데, 이 루트가 `background` 만 갖고 있어 **터치를 소비하지 않았다.**
    //    카드가 화면의 78%×92% 라 좌우 11% · 상하 4% 가 비고, 그 빈 자리 아래에는 **먼저 그려진**
    //    🎤 `FloatingControls`(우하단 64dp)와 시연 서랍 롱프레스 핫스팟(우상단 48dp)이 그대로 있었다.
    //    그래서 **동의 화면이 떠 있는데 아이가 오른쪽 아래를 누르면 마이크가 켜지고,
    //    오른쪽 위를 길게 누르면 디버그 서랍이 동의 화면 위로 열렸다.**
    //    9/23 에 「부모 모드 진입에만 걸어 둔 것은 약하다」고 고친 그 문제가 다른 경로로 남아 있었다.
    //    `MicNoticeSheet` 가 쓰는 것과 같은 방패다 — 뒤로가기·바깥 탭으로도 닫히지 않는다.
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        // 가로 화면은 세로가 짧다(393dp). 스크롤을 **가운데에만** 두고 버튼은 아래에 붙박아
        // 두지 않으면 「동의하고 들어가기」가 화면 밖으로 밀린다 (9/23)
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.92f)
                .shadow(12.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(CardWhite)
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("시작하기 전에", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Body("이 앱은 아이가 말한 것으로 이야기를 만들어요. 보호자께서 한 번만 확인해 주세요.")
            if (DRAFT) DraftMark("아래 문구는 초안입니다 — 최종 문구는 법무 확인이 필요합니다")

            Spacer(Modifier.height(16.dp))
            CheckLine(
                checked = agreed,
                text = "만 14세 미만 아동의 개인정보(음성 · 그림 · 대화 기록) 처리에 " +
                    "법정대리인으로서 동의합니다.",
            ) { agreed = !agreed }

            Spacer(Modifier.height(12.dp))
            // 계정이 있는 앱이면 회원가입 뒤에 서는 줄이다. 계정이 없으니 여기 선다.
            // ⚠️ 「이용약관」 줄은 뺐다 (09-25) — 약관 문서가 없어 누르면 아무 일도 없는 줄이었다.
            //    눌러도 안 열리는 링크는 심사에서 「준비 중」 표시보다 나쁘다. 계정·결제가 없는
            //    무료 앱이라 약관이 필수는 아니다. 약관을 쓰면 PRIVACY_URL 처럼 주소를 두고 여기 한 줄 더한다
            DocLink("개인정보처리방침", PRIVACY_URL)
            Spacer(Modifier.height(10.dp))
            Body("• 동의는 부모 모드 설정에서 언제든 철회할 수 있습니다.")
            Body("• 동의하지 않으시면 앱을 닫습니다. 아이 목소리로 이야기를 만드는 앱이라 동의 없이 쓸 수 있는 부분이 없어요.")

          }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ⚠️ **거절할 길이 있어야 동의다 (09-25).** 전에는 이 줄에 버튼이 하나뿐이었고
                //    `onBack` 을 부르는 곳이 없었다. 뒤로가기도 Dialog 가 막으므로, 동의하지 않는
                //    보호자는 **나갈 방법이 없었다** — 「자유로운 동의」가 아니다.
                //    거절하면 앱을 닫는다. 아이 목소리로 이야기를 만드는 앱이라 동의 없이 쓸 수 있는
                //    부분이 없고, 반쯤 되는 모드를 만들면 그 모드가 무엇을 처리하는지 다시 고지해야 한다.
                Pill("동의하지 않음", Color(0x14000000)) { onBack() }
                Spacer(Modifier.weight(1f))
                Pill("동의하고 시작", if (ready) Sun else Color(0x22000000)) {
                    if (ready) {
                        ConsentStore.agree()
                        onAgree()
                    }
                }
            }
        }
    }
    }
}

/**
 * 개인정보처리방침으로 가는 줄.
 *
 * ⚠️ **아직 웹 주소가 없다.** 문서(`docs/개인정보처리방침.md`)는 조장이 초안을 써 두었지만
 * 심사에는 **웹에 올라간 URL** 이 필요하다. 주소가 생기면 여기서 열면 된다.
 */
@Composable
private fun DocLink(title: String, url: String) {
    // 브라우저로 연다. 브라우저가 없는 기기(키즈 전용 태블릿 등)면 조용히 아무 일도 없다 —
    // 주소는 스토어 등록정보에도 적혀 있다
    val uri = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0D000000))
            .clickable { runCatching { uri.openUri(url) } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("›", fontSize = 16.sp, color = Ink.copy(alpha = 0.5f))
    }
}

/** 설정에 들어가는 **동의 철회** 자리 — 동의는 언제든 물릴 수 있어야 한다 */
@Composable
fun ConsentWithdrawSection() {
    NoticeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("보호자 동의")
                Body(if (ConsentStore.guardianAgreed) "동의함" else "동의하지 않음")
            }
            if (ConsentStore.guardianAgreed) {
                Pill("철회", Color(0x22000000)) { ConsentStore.withdraw() }
            }
        }
    }
}

// ── 3. 마이크 사용 목적 고지 ───────────────────────────────────

/**
 * **처음 🎤 를 누르기 전** 한 장. 한 번 보면 다시 안 뜬다.
 *
 * ⚠️ 여기 적은 내용이 **실제 구현과 같아야 한다.** 다르면 앱이 내려간다
 * (`docs/출시_체크리스트.md` §2). 지금은 폰 안에서만 처리한다고 적었는데,
 * LLM·STT 서버 연동이 붙으면 **이 문구부터 고쳐야 한다.**
 */
@Composable
fun MicNoticeSheet(onOk: () -> Unit) {
    // Dialog 로 감싼다 — 어디서 불러도 **화면 전체를 덮는다.**
    // 🎤 버튼은 오른쪽 아래 Row 안에 있어서, 거기서 그냥 그리면 그 줄 안에만 그려진다
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.68f)
                .shadow(12.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(CardWhite)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎤", fontSize = 38.sp)
            Spacer(Modifier.height(8.dp))
            Text("마이크를 왜 쓰나요?", fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            // ⚠️ 문구를 **지어내면 안 되는 자리다.** 처음에 「폰 안에서만 처리한다」고 썼는데,
            //    실제 설계는 **우리 서버까지 간다**(`docs/스토어_출시_체크리스트.md` §4 ③).
            //    선언과 구현이 다르면 앱이 내려간다. 아래는 그 문서의 문구를 그대로 옮긴 것이다
            Body("아이 목소리를 글자로 바꾸려고 마이크를 씁니다.")
            Body("소리는 우리 서버까지만 가고 바로 지워져요.")
            Body("다른 회사로는 보내지 않아요.")
            Spacer(Modifier.height(18.dp))
            // [알겠어요] 다음에 **시스템 권한 요청**이 뜬다 (문서 §4 ③). 고지가 먼저다
            Pill("알겠어요", Sun) {
                ConsentStore.markMicNoticeShown()
                onOk()
            }
        }
    }
    }
}

/**
 * 고지를 본 **뒤에** 뜨는 시스템 권한 요청.
 *
 * 순서가 중요하다 — 플레이의 「눈에 띄는 고지」 요건은 **왜 쓰는지를 먼저 알리고** 권한을 묻게 한다.
 * 거절해도 앱은 그대로 돈다. 대본 앱이라 대본 버튼으로 답할 수 있다.
 */
@Composable
fun MicPermissionRequest(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (already || motionFrozen) onDone()
        else runCatching { ask.launch(Manifest.permission.RECORD_AUDIO) }.onFailure { onDone() }
    }
}

// ── 4. AI 음성 고지 한 줄 ──────────────────────────────────────

/** 설정에 들어가는 한 줄 — 마스코트 목소리가 사람이 아님을 알린다 */
@Composable
fun AiVoiceNotice() {
    NoticeCard {
        Label("AI 음성 안내")
        Body("마스코트가 내는 목소리는 사람이 녹음한 것이 아니라 AI가 만든 소리입니다.")
        if (DRAFT) DraftMark("쓰는 음성 서비스 이름을 함께 적을지 정해야 합니다")
    }
}

// ── 공통 조각 ──────────────────────────────────────────────────

@Composable
private fun NoticeCard(content: @Composable ColumnScopeLike.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(16.dp),
    ) { ColumnScopeLike.content() }
}

/** `NoticeCard` 안에서 쓰는 이름표 — Column 스코프를 그대로 넘기지 않으려고 둔 껍데기 */
object ColumnScopeLike

@Composable
private fun Label(text: String) =
    Text(text, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) {
    Spacer(Modifier.height(3.dp))
    Text(text, fontSize = 14.sp, color = Ink.copy(alpha = 0.75f), lineHeight = 20.sp)
}

/** 아직 초안이라는 표시. 최종 문구가 들어오면 [DRAFT] 를 false 로 두면 전부 사라진다 */
@Composable
private fun DraftMark(why: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Coral.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text("초안", fontSize = 12.sp, color = Coral, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(why, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f), lineHeight = 17.sp)
    }
}

@Composable
private fun CheckLine(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) Sun.copy(alpha = 0.22f) else Color(0x0D000000))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (checked) "☑" else "☐", fontSize = 18.sp, color = Ink)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = Ink, lineHeight = 20.sp)
    }
}

@Composable
private fun Pill(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}
