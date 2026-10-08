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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import com.example.finalproject_demo.net.AiPicture
import com.example.finalproject_demo.net.NOTE_MAX
import com.example.finalproject_demo.net.ReportAttachment
import com.example.finalproject_demo.net.ReportCategory
import com.example.finalproject_demo.net.ReportPayload
import com.example.finalproject_demo.net.ReportResult
import com.example.finalproject_demo.net.ReportUpload
import kotlinx.coroutines.launch

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
 * ## 동의는 기기에 남는다 (09-25)
 *
 * 처음에는 메모리뿐이라 **껐다 켤 때마다 동의 화면이 다시 떴다.** 지금은 [ConsentStore.attach] 가
 * 기기(SharedPreferences)에서 읽고 쓴다. 신고는 서버로 보내고, 닿지 못하면 보호자가 메일을 연다(`ReportSheet` · #283).
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

    /**
     * **선택 동의 — 마스코트가 아이 이름을 소리로 불러도 되는가** (09-29 조장 · 규칙 6 개정).
     * 켜면 이름이 든 대사가 목소리 업체(TypeCast)로 간다 — 제3자 제공이라 **따로, 기본 꺼짐**으로 받는다.
     * 끄면 목소리에서 아이 이름은 「너」, 친구 이름은 「그 친구」로 바뀐다(10-01 #50)(`NameMask.speakable`). 화면 · 책에는 어느 쪽이든 실명이다.
     * ⚠️ 동의 화면에 체크 칸은 아직 없다 — UI(치영)에 붙인다
     */
    var nameVoiceAgreed by mutableStateOf(false)
        private set

    /**
     * **선택 동의 — 오또 목소리를 타입캐스트로** (10-06 조장). 타입캐스트는 받은 글자를 자기 서비스 개선 · 새 서비스에 쓸 수 있어
     * 위탁이 아니라 **제3자 제공**이다 → 따로, 기본 꺼짐. 끄면 이 집의 대사는 타입캐스트로 가지 않고 기본 목소리(OpenAI)로 읽는다.
     * 서버로는 `Server.typecastVoiceAgreed` 가 같이 움직인다.
     */
    var typecastVoiceAgreed by mutableStateOf(false)
        private set

    // ⚠️ **기기에 저장한다 (09-25).** 전에는 메모리뿐이라 앱을 켤 때마다 동의 화면이 다시 떴다.
    //    `SharedPreferences` 를 쓴다 — 값 둘(참/거짓)이라 DataStore 의존성을 들일 까닭이 없다.
    //    `allowBackup="false"` 라 이 파일은 구글 드라이브로 나가지 않는다(AndroidManifest).
    //    `attach` 를 안 부르면 메모리로만 동작한다 — 화면 검사가 그 모드로 돈다.
    private var prefs: SharedPreferences? = null
    private const val PREFS = "consent"
    private const val KEY_AGREED = "guardian_agreed"
    private const val KEY_MIC = "mic_notice_shown"
    private const val KEY_NAME_VOICE = "name_voice_agreed"
    private const val KEY_TYPECAST_VOICE = "typecast_voice_agreed"

    /** `MainActivity.onCreate` 에서 한 번. 저장된 값을 읽어 온다 */
    fun attach(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        guardianAgreed = p.getBoolean(KEY_AGREED, false)
        micNoticeShown = p.getBoolean(KEY_MIC, false)
        nameVoiceAgreed = p.getBoolean(KEY_NAME_VOICE, false)
        typecastVoiceAgreed = p.getBoolean(KEY_TYPECAST_VOICE, false)
        com.example.finalproject_demo.net.Server.typecastVoiceAgreed = typecastVoiceAgreed
        SentReports.attach(context)
    }

    /** 타입캐스트 목소리 선택 동의를 켜고 끈다 — 부모 영역 → 계정에서 언제든 바꿀 수 있다 */
    fun setTypecastVoice(on: Boolean) {
        typecastVoiceAgreed = on
        com.example.finalproject_demo.net.Server.typecastVoiceAgreed = on
        prefs?.edit()?.putBoolean(KEY_TYPECAST_VOICE, on)?.apply()
    }

    /** 이름 읽기 선택 동의를 켜고 끈다 — 부모 설정에서 언제든 바꿀 수 있어야 한다 */
    fun setNameVoice(on: Boolean) {
        nameVoiceAgreed = on
        prefs?.edit()?.putBoolean(KEY_NAME_VOICE, on)?.apply()
    }

    fun agree() {
        guardianAgreed = true
        prefs?.edit()?.putBoolean(KEY_AGREED, true)?.apply()
    }

    /** 동의 철회 — 정책상 **언제든 물릴 수 있어야** 한다. 물리면 다음 화면부터 동의를 다시 받는다 */
    fun withdraw() {
        guardianAgreed = false
        nameVoiceAgreed = false      // 본 동의를 물리면 선택 동의도 같이 물린다
        typecastVoiceAgreed = false
        com.example.finalproject_demo.net.Server.typecastVoiceAgreed = false
        micNoticeShown = false       // 마이크도 다시 묻는다 — 탈퇴 · 다른 보호자 계정 (10-05 치영 · ui/shell/Shell.kt)
        prefs?.edit()?.putBoolean(KEY_AGREED, false)?.putBoolean(KEY_NAME_VOICE, false)?.putBoolean(KEY_TYPECAST_VOICE, false)
            ?.putBoolean(KEY_MIC, false)?.apply()
    }

    fun markMicNoticeShown() {
        micNoticeShown = true
        prefs?.edit()?.putBoolean(KEY_MIC, true)?.apply()
    }

    /** 마이크 동의만 되돌린다 — 로그아웃하면 다시 로그인할 때 마이크를 다시 묻는다 (10-05 치영 · ui/shell/AccountScreens.kt) */
    fun forgetMic() {
        micNoticeShown = false
        prefs?.edit()?.putBoolean(KEY_MIC, false)?.apply()
    }

}

/** 신고를 받는 메일 — 처리방침 · 스토어 등록정보의 연락처와 같다(서버에 닿지 못했을 때만 쓴다) */
const val REPORT_TO = com.example.finalproject_demo.net.ReportUpload.MAIL_TO

/**
 * 보낸 신고의 접수 번호 — 최근 10개 `{번호 · 날짜 · 분류}` 만 (#283 · 설계 §2-4).
 * 보호자가 삭제를 요청하려면 번호가 필요하다. 탈퇴 · 기기 비우기(`LocalWipe`)가 지운다
 */
object SentReports {
    data class Sent(val id: String, val date: String, val category: String)

    const val PREFS = "sent_reports"
    private const val KEY = "list"
    private const val KEEP = 10
    val list = mutableStateListOf<Sent>()
    private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        reload()
    }

    /** 저장된 것을 다시 읽는다 — 기기를 비운 뒤에도 부른다 */
    fun reload() {
        list.clear()
        val raw = prefs?.getString(KEY, null) ?: return
        runCatching {
            val a = org.json.JSONArray(raw)
            (0 until a.length()).forEach { i ->
                val o = a.getJSONObject(i)
                list += Sent(o.getString("id"), o.optString("date"), o.optString("category"))
            }
        }
    }

    fun add(s: Sent) {
        list.add(0, s)
        while (list.size > KEEP) list.removeAt(list.lastIndex)
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("id", it.id).put("date", it.date).put("category", it.category)) }
        prefs?.edit()?.putString(KEY, a.toString())?.apply()
    }
}

/** 앱 버전 — 신고에 싣는다 */
private fun appVersion(ctx: Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
}.getOrNull() ?: "?"

// ── 1. 앱 내 신고 ──────────────────────────────────────────────

/**
 * 부모 모드 설정의 **신고** 자리 — 책 맥락이 없어 분류 · 설명만 받고 첨부 칸은 없다 (#283 · 설계 §2-1).
 * 책 속 그림 · 문장이면 책장 정리에서 그 책의 [신고]로 — 그 길이 주 입구다.
 */
@Composable
fun ReportSection(onLog: (String) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    NoticeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("문제가 있었나요?")
                Body("이야기나 그림에 이상한 점이 있으면 알려 주세요.")
            }
            Pill(if (open) "닫기" else "신고하기", Coral) { open = !open }
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            ReportSheet(book = null, onLog = onLog, onClose = { open = false })
        }
        Spacer(Modifier.height(8.dp))
        Text("책 속 그림 · 문장이라면 「책장 정리」에서 그 책의 [신고]를 눌러 주세요 — 그 그림이나 문장을 같이 보낼 수 있어요.",
            fontSize = 12.sp, color = Ink.copy(alpha = 0.6f), lineHeight = 17.sp)
        if (SentReports.list.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("보낸 신고 ${SentReports.list.size}건 · " + SentReports.list.take(3).joinToString(" · ") { it.id } +
                (if (SentReports.list.size > 3) " …" else ""), fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
        }
    }
}

/**
 * 신고 한 장 — 쪽(책에서 왔을 때) → 분류 → 설명 → ☐ 그림 / 문장 같이 보내기 → **미리 보기 안에서만** 보내기 (설계 §2-2 · §2-3).
 *
 * 서버가 받으면 접수 번호, 닿지 못하면 「서버에 닿지 못했어요」 + [메일로 보내기](보호자가 누를 때만 · 첨부 없이).
 * 아이가 그린 그림 · 녹음 · 목소리는 이 화면 어디에서도 고를 수 없다 — 후보는 [ReportBook] 의 AI 그림 · 쪽 문장뿐이다
 *
 * @param book 책장 정리의 책에서 왔으면 그 책, 설정에서 왔으면 null(첨부 칸 없음)
 */
@Composable
fun ReportSheet(
    book: com.example.finalproject_demo.demo.ReportBook?,
    onLog: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var page by remember(book) { mutableStateOf(if (book != null && book.pages.isNotEmpty()) 1 else null) }
    var category by remember { mutableStateOf<ReportCategory?>(null) }
    var note by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<String?>(null) }           // "picture" · "preset" · "sentence"
    var picture by remember(book) { mutableStateOf(book?.pictures?.firstOrNull()) }
    var preview by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReportResult?>(null) }
    var sent by remember { mutableStateOf<ReportPayload?>(null) }
    var mailOpened by remember { mutableStateOf<Boolean?>(null) }

    val sentence = book?.let { b -> page?.let { b.pages.getOrNull(it - 1) } }?.takeIf { it.isNotBlank() }
        ?.let { ReportAttachment.Sentence.of(it, book.names) }
    val hasPicture = book?.pictures?.isNotEmpty() == true
    val hasPreset = book?.presetBackground != null

    fun payload(att: ReportAttachment?) = ReportPayload(
        category = category ?: ReportCategory.OTHER, mode = book?.mode, page = page, note = note.trim(),
        appVersion = appVersion(ctx), attachment = att,
    )

    // ── 결과 ──
    when (val r = result) {
        is ReportResult.Accepted -> {
            Body("접수됐어요 · 접수 번호 ${r.id}")
            Body("처리가 끝나면 30일 뒤 지워져요. 지워 달라고 하시려면 이 번호를 $REPORT_TO 으로 보내 주세요.")
            Spacer(Modifier.height(10.dp))
            Pill("닫기", Sun) { onClose() }
            return
        }
        ReportResult.Unreached -> {
            Body("서버에 닿지 못했어요.")
            Body("메일로 보내실 수 있어요. 메일로는 그림 · 문장이 가지 않아요.")
            mailOpened?.let { opened ->
                Body(if (opened) "메일 앱이 열렸어요. 거기서 보내기를 누르시면 저희에게 전달돼요."
                    else "메일 앱을 찾지 못했어요. $REPORT_TO 로 직접 보내 주시면 확인할게요.")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("메일로 보내기", Sun) {
                    val p = sent ?: return@Pill
                    val opened = try { ctx.startActivity(ReportUpload.mailIntent(p)); true } catch (_: ActivityNotFoundException) { false }
                    mailOpened = opened
                    onLog("신고 — 서버 실패 → 메일 ${if (opened) "앱으로 넘김" else "앱 없음"}")
                }
                Pill("닫기", Color(0x22000000)) { onClose() }
            }
            return
        }
        null -> {}
    }

    // ── 미리 보기 — 보내기는 여기에만 ──
    if (preview) {
        val att: ReportAttachment? = if (!attach) null else when (kind) {
            "sentence" -> sentence
            "preset" -> book?.presetBackground?.let { ReportAttachment.Preset(it) }
            else -> null
        }
        Label("보낼 내용")
        Spacer(Modifier.height(6.dp))
        listOfNotNull(
            "분류" to (category ?: ReportCategory.OTHER).label,
            book?.let { "어디서" to ReportUpload.modeLabel(it.mode) + (page?.let { p -> " · ${p}쪽" } ?: "") },
            "앱 버전" to appVersion(ctx),
            "설명" to note.trim().ifBlank { "(없음)" },
            "첨부" to when {
                !attach -> "없음"
                kind == "picture" -> picture?.source?.label ?: "없음"
                kind == "preset" -> "앱에 든 배경 그림 이름 「${book?.presetBackground}」"
                kind == "sentence" -> "오또가 쓴 문장(이름은 가려서)"
                else -> "없음"
            },
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, fontSize = 13.sp, color = Ink.copy(alpha = 0.6f), modifier = Modifier.width(64.dp))
                Text(v, fontSize = 13.sp, color = Ink, lineHeight = 18.sp)
            }
        }
        if (attach && kind == "picture") picture?.let { ReportThumb(it, selected = true) {} }
        if (attach && kind == "sentence" && sentence != null) {
            Spacer(Modifier.height(4.dp))
            Text("“${sentence.masked}”", fontSize = 13.sp, color = Ink, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("아이가 그린 그림 · 녹음 · 목소리는 보내지 않아요 · 처리가 끝나면 30일 뒤 지워요",
            fontSize = 12.sp, color = Coral, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("고치기", Color(0x22000000)) { if (!sending) preview = false }
            Pill(if (sending) "보내는 중…" else "보내기", if (sending) Color(0x22000000) else Sun) {
                if (sending) return@Pill
                sending = true
                scope.launch {
                    val finalAtt = if (attach && kind == "picture") {
                        picture?.let { p -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { p.toAttachment() } }
                    } else att
                    val p = payload(finalAtt)
                    val r = ReportUpload.send(p)
                    sent = p
                    if (r is ReportResult.Accepted) {
                        SentReports.add(SentReports.Sent(r.id, java.time.LocalDate.now().toString(), p.category.wire))
                    }
                    // 시연 서랍 「기록」에 한 줄 — 새 이벤트 종류는 만들지 않는다
                    onLog("신고 — 분류 \"${p.category.wire}\"" + (p.page?.let { " · ${it}쪽" } ?: "") +
                        " · 첨부 " + when (finalAtt) { is ReportAttachment.Picture -> "그림"; is ReportAttachment.Preset -> "프리셋 이름"
                            is ReportAttachment.Sentence -> "문장"; null -> "없음" } +
                        " · " + if (r is ReportResult.Accepted) "서버 접수 ${r.id}" else "서버 실패")
                    result = r
                    sending = false
                }
            }
        }
        return
    }

    // ── 고르기 ──
    if (book != null && book.pages.isNotEmpty()) {
        Label("몇 쪽인가요?")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            book.pages.forEachIndexed { i, text ->
                val n = i + 1
                Column(
                    Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (page == n) Sun.copy(alpha = 0.35f) else Color(0x11000000))
                        .clickable { page = n }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("${n}쪽", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
                    // 폰 안에서만 보이는 원문 앞 12자 — 보낼 때는 이름을 가린다
                    Text(text.take(12), fontSize = 11.sp, color = Ink.copy(alpha = 0.6f), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    ReportCategory.entries.forEach { c ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (category == c) Sun.copy(alpha = 0.35f) else Color(0x11000000))
                .clickable { category = c }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (category == c) "●" else "○", fontSize = 14.sp, color = Ink)
            Spacer(Modifier.width(9.dp))
            Text(c.label, fontSize = 14.sp, color = Ink)
        }
    }
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0D000000))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (note.isEmpty()) Text("더 적어 주실 내용 (선택)", fontSize = 14.sp, color = Ink.copy(alpha = 0.45f))
        BasicTextField(
            value = note,
            onValueChange = { note = it.take(NOTE_MAX) },
            textStyle = TextStyle(fontSize = 14.sp, color = Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (book != null) {
        Spacer(Modifier.height(8.dp))
        CheckLine(attach, "문제 된 그림 / 문장 같이 보내기") {
            attach = !attach
            if (attach && kind == null) kind = when { hasPicture -> "picture"; hasPreset -> "preset"; sentence != null -> "sentence"; else -> null }
        }
        if (attach) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPicture || hasPreset) Pill("그림", if (kind == "picture" || kind == "preset") Sun else Color(0x11000000)) {
                    kind = if (hasPicture) "picture" else "preset"
                    if (category == null) category = ReportCategory.IMAGE
                }
                if (sentence != null) Pill("문장", if (kind == "sentence") Sun else Color(0x11000000)) {
                    kind = "sentence"
                    if (category == null) category = ReportCategory.TEXT
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                kind == "picture" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.pictures.forEach { p -> ReportThumb(p, selected = picture == p) { picture = p } }
                }
                kind == "preset" -> Body("앱에 든 배경 그림이라 이름(「${book.presetBackground}」)만 보내요.")
                kind == "sentence" && sentence != null -> Body("“${sentence.masked}”")
                else -> Body("이 쪽에는 오또가 만든 그림이나 문장이 없어요.")
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("보낼 내용 미리 보기", if (category == null) Color(0x22000000) else Sun) {
            if (category == null) return@Pill
            if (attach && kind == "picture" && picture == null) attach = false
            preview = true
        }
        Pill("닫기", Color(0x11000000)) { onClose() }
    }
}

/** 첨부 후보 그림 한 장 — 저장된 AI 그림 파일에서 읽는다 */
@Composable
private fun ReportThumb(p: AiPicture, selected: Boolean, onPick: () -> Unit) {
    val bmp = remember(p) { runCatching { p.bitmap()?.asImageBitmap() }.getOrNull() }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Sun.copy(alpha = 0.35f) else Color(0x11000000))
            .clickable { onPick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bmp != null) androidx.compose.foundation.Image(bmp, contentDescription = p.source.label, modifier = Modifier.size(84.dp))
        else Box(Modifier.size(84.dp).background(Color(0x11000000)))
        Text(p.source.label, fontSize = 11.sp, color = Ink)
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
                .felt(Wool, RoundedCornerShape(Radius.L), lift = 10.dp, texture = false)
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

// ── 5. 소리와 진동 ────────────────────────────────────────────

/** 설정에 들어가는 **효과음 · 진동** 스위치 (09-25). 저장은 [FeelPrefs] 가 한다 */
@Composable
fun SoundSettingsSection() {
    NoticeCard {
        Label("소리와 진동")
        Spacer(Modifier.height(8.dp))
        CheckLine(FeelPrefs.soundOn, "효과음") { FeelPrefs.setSound(!FeelPrefs.soundOn) }
        Spacer(Modifier.height(6.dp))
        CheckLine(FeelPrefs.buzzOn, "진동 — 누르거나 해냈을 때 살짝 떨려요") { FeelPrefs.setBuzz(!FeelPrefs.buzzOn) }
    }
}

// ── 공통 조각 ──────────────────────────────────────────────────

@Composable
private fun NoticeCard(content: @Composable ColumnScopeLike.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .felt(CardWhite, RoundedCornerShape(16.dp), lift = 1.dp, texture = false, stitch = false)
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
