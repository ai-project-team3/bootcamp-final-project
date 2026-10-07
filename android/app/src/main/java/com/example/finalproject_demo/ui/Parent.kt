package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.ART_STYLES
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reward
import com.example.finalproject_demo.demo.Rewards
import com.example.finalproject_demo.demo.SessionReport
import com.example.finalproject_demo.demo.SessionReports
import com.example.finalproject_demo.demo.buildSessionReport
import com.example.finalproject_demo.demo.SHELF_CAPACITY
import com.example.finalproject_demo.demo.SHELF_MODES
import com.example.finalproject_demo.demo.ShelfEntry
import com.example.finalproject_demo.demo.shelfCount
import com.example.finalproject_demo.demo.shelfDeleteSignal
import com.example.finalproject_demo.demo.shelfEntries
import com.example.finalproject_demo.demo.CoopPlan
import com.example.finalproject_demo.demo.CoopAfter
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.COOP_AFTER_SUGGESTION
import com.example.finalproject_demo.demo.coopAfterAsk
import com.example.finalproject_demo.demo.coopBeforeAfter
import com.example.finalproject_demo.demo.COOP_PAIR_PLAY_CARD
import com.example.finalproject_demo.demo.coopAsked
import com.example.finalproject_demo.demo.coopReportCopy
import com.example.finalproject_demo.demo.coopParentUsed
import com.example.finalproject_demo.demo.feelingsSaid
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.coopReady
import com.example.finalproject_demo.demo.coopParentAdvice
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.bat
import com.example.finalproject_demo.demo.eul
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.wa
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.ga

// 09-29 디자인 시스템 토큰 (`Theme.kt`) — 부모 영역은 차분한 성인 UI: 크림 메뉴 · 흰 카드 · 코랄 강조
private val PBg = Wool
private val PCard = FeltWhite
private val PLine = Color(0xFFEDE2CF)
private val PSub = InkSoft
private val PAccent = FeltCoral
private val PMint = FeltTeal

@Composable
private fun PCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            // 부모 카드 — 펠트의 ① 단색만 (결 · 큰 그림자 없음 · 디자인 시스템 §4) + 옅은 테두리로 카드끼리 구분 (09-29)
            .felt(PCard, RoundedCornerShape(20.dp), lift = 2.dp, texture = false, stitch = false)
            .border(1.dp, PLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) { content() }
}

@Composable
private fun Section(text: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(PAccent))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
        if (sub != null) {
            Spacer(Modifier.width(8.dp))
            Text(sub, fontSize = 12.sp, color = PSub, maxLines = 1)
        }
    }
}

/**
 * 부모 영역 탭 — 키 · 펠트 아이콘(ComfyUI `pi_*`) · 이모지(그림이 없을 때) · 이름 · 이 탭에서 보는 것 한 줄 (09-29)
 */
private data class PTab(val key: String, val art: String, val emoji: String, val title: String, val desc: String)

private val PTABS = listOf(
    PTab("rec", "pi_record", "📋", "오늘의 기록", "오늘 아이가 한 말 그대로 · 만든 책 · 이야기한 방식"),
    PTab("coop", "pi_coop", "🤝", "같이 만들기", "소파에서 같이 만들 이야기를 고르고, 더 물어볼 질문을 적어 둬요"),
    PTab("ach", "pi_achieve", "🏅", "업적", "아이가 한 일로만 받는 선물과 해결 방법 도감"),
    PTab("set", "pi_settings", "⚙️", "설정", "하루 한도 · 시작할 때 확인 · 그림체 · 소리 · 동의"),
    PTab("shelf", "pi_shelf", "📚", "책장 정리", "모드마다 ${SHELF_CAPACITY}권까지 · 뺄 책을 골라요 · 아이 화면에서는 지우지 않아요"),
    PTab("acct", "pi_account", "👤", "계정", "로그인 · 부모 비밀번호 · 처음 설정 다시 보기 · 탈퇴"),
)

/** 탭 아이콘 — 펠트 그림을 흰 둥근 네모에 */
@Composable
private fun TabIcon(t: PTab, size: androidx.compose.ui.unit.Dp, on: Boolean) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(if (on) Color(0xFFFFF4E6) else Color.White.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) { AssetImage(t.art, Modifier.size(size * 0.82f)) { Text(t.emoji, fontSize = (size.value * 0.45f).sp) } }
}

/** 부모 모드 — 왼쪽 메뉴 · 오른쪽 카드. 아래 마스코트 · 버튼은 없다 (v0.8) */
@Composable
fun ParentView(d: Director, tab: String) = ParentText { ParentViewBody(d, tab) }

@Composable
private fun ParentViewBody(d: Director, tab: String) {
    val s = d.s
    val cur = PTABS.firstOrNull { it.key == tab } ?: PTABS.first()
    // 같이 만들기 초안 — 탭을 오가도 남고, 부모 모드를 나가면 버린다 (저장 버튼 아래 고정 줄이 같이 쓴다)
    val coop = remember(s) { CoopDraft(s) }
    // 부모 설정에서 바꾼 값은 폰에 남긴다 — 전에는 앱을 다시 켜면 처음 설정 값으로 돌아갔다 (09-29)
    LaunchedEffect(s.limitOn, s.dailyLimit, s.pinToStart, s.artStyle) {
        if (com.example.finalproject_demo.ui.shell.Shell.onboarded) {
            com.example.finalproject_demo.ui.shell.Shell.saveSetup(if (s.limitOn) s.dailyLimit else null, s.artStyle, s.pinToStart)
        }
    }
    Row(Modifier.fillMaxSize().background(PBg)) {
        // ── 왼쪽 메뉴 (09-29 다시 그림) — 오또 머리 · 펠트 아이콘 탭 · 아이 화면으로
        Column(
            Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(WoolCream)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(FeltTeal).padding(3.dp).clip(CircleShape).background(Color.White)) {
                    AssetImage("otto_face_talk", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) { HeroImage(s.heroAttr ?: s.heroes.first().attr, Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("부모 모드", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("${s.childName}네 기록 · 어른만", fontSize = 11.sp, color = PSub)
                }
            }
            // 탭이 여섯이라 낮은 가로 폰(411dp)에서는 아래 「아이 화면으로」가 잘렸다 — 탭만 스크롤하고 그 버튼은 늘 보이게 (#80)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PTABS.forEach { t ->
                val on = t.key == cur.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { d.send(Reply.Tapped("tab:${t.key}", t.title)) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(if (on) PAccent else Color.Transparent))
                    Spacer(Modifier.width(6.dp))
                    TabIcon(t, 36.dp, on)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, fontSize = 15.sp, color = if (on) Ink else PSub, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .felt(PAccent, RoundedCornerShape(Radius.Round), lift = 3.dp, texture = false)
                    .clickable { d.send(Reply.Tapped("home", "처음으로")) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AssetImage("pi_home", Modifier.size(30.dp)) { Text("🏠", fontSize = 18.sp) }
                Spacer(Modifier.width(8.dp))
                Text("아이 화면으로", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // ── 오른쪽 내용
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // 고정 머리 — 탭 아이콘 · 이름 · 이 탭에서 보는 것 (내용이 스크롤돼도 겹치지 않게)
            Row(
                Modifier.fillMaxWidth().background(PBg).padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabIcon(cur, 46.dp, true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(cur.title, fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text(cur.desc, fontSize = 12.sp, color = PSub, maxLines = 1)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(PLine))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()       // 스크롤된 칩이 고정 머리 밑으로 그려져 머리가 눌리지 않게 (#98)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 22.dp)
            ) {
                when (cur.key) {
                    "coop" -> CoopQuestionsTab(coop)
                    "ach" -> AchievementsTab(d)
                    "set" -> SettingsTab(d)
                    "shelf" -> ShelfTidyTab(d)
                    "acct" -> com.example.finalproject_demo.ui.shell.AccountTab(d)
                    else -> RecordTab(d)
                }
            }
            // 저장 줄은 스크롤 밖에 고정 — 템플릿을 고른 자리에서도, 질문 줄을 고친 자리에서도 바로 누른다
            if (cur.key == "coop" && coop.editing) CoopSaveBar(coop)
        }
    }
}

/**
 * 오늘의 기록 — 숫자 칸 · 이야기의 뼈대 · 오늘 보인 순간 · 처음 해낸 것 · 집에서 이어 가기 · 대화 전체 · 지난 기록 (10-06 종훈 시안).
 * 등급 · 비교 · 수준 이름은 없다 (16 · 규칙 9). 화면은 `SessionReportView.kt`, 재료는 `demo/SessionReport.kt`.
 * 세 모드가 다 여기로 온다 — 문구는 그 모드가 실제로 물은 것만 쓴다 (일기 · 협업은 함께할 사람을 안 묻는다 · 9/22).
 */
@Composable
private fun RecordTab(d: Director) {
    val s = d.s
    // 협업 리포트의 말 — 한 번만 만들어 아래 여러 칸이 같이 쓴다 (#99 리뷰 4)
    val coopCopy = if (s.isCoop) s.coopReportCopy() else null
    // 지금 세션 — 꽂았으면 그 책의 리포트, 아직이면 지금까지 주고받은 것으로 (demo/SessionReport.kt · 10-06 종훈 시안)
    val live = remember(s.talk.size, s.lastReport, s.title) {
        if (s.talk.isEmpty()) null else s.lastReport?.takeIf { it.talk.size == s.talk.size } ?: s.buildSessionReport()
    }
    val past = remember(s.lastReport, s.shelf.size) { SessionReports.all() }
    var picked by remember { mutableStateOf<SessionReport?>(null) }
    var talkOpen by remember { mutableStateOf(false) }
    val r = picked ?: live ?: s.lastReport ?: past.firstOrNull()
    if (r == null) {
        // 빈 화면도 막다른 곳이 아니게 — 무엇이 여기에 생기는지 보여 주고, 아이 화면으로 돌아갈 길 (09-29)
        PCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetImage("otto_pose_think", Modifier.size(120.dp)) { Text("📖", fontSize = 48.sp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("오늘은 아직 만든 책이 없어요", fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("아이와 이야기를 하나 만들면 여기에 생겨요.", fontSize = 13.sp, color = PSub)
                    Spacer(Modifier.height(10.dp))
                    listOf("💬" to "아이가 한 말 그대로", "📖" to "만든 책과 쪽수", "🎙" to "말로 · 그림으로 답한 횟수").forEach { (e, t) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(e, fontSize = 13.sp); Spacer(Modifier.width(6.dp)); Text(t, fontSize = 13.sp, color = Ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(46.dp).felt(PMint, RoundedCornerShape(Radius.Round), lift = 2.dp, texture = false)
                    .clickable { d.send(Reply.Tapped("home", "처음으로")) },
                contentAlignment = Alignment.Center,
            ) { Text("아이 화면에서 이야기 만들기", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        return
    }
    if (talkOpen) {
        SessionTalkView(r, s.childName, onBack = { talkOpen = false })
        return
    }
    SessionReportView(r, s.childName, onTalk = { talkOpen = true })
    // 협업 칸은 지금 세션의 것 — 지난 책을 고른 동안은 보이지 않는다
    val coopNow = s.isCoop && picked == null && live != null

    // 협업 모드의 결과물 — **부모가 궁금해한 것에 아이가 뭐라고 했나** (부모협업모드_설계 §0 · 구현설계 §2-3).
    // 인용은 아이가 말한 것(`by: child`)만 따옴표로. 카드 · 마스코트가 채운 것은 그렇다고 적는다 (guidelines/2 §1-4)
    if (coopNow && s.coopAsked.isNotEmpty()) {
        val copy = coopCopy!!
        Section(copy.askedTitle, copy.askedSub)
        PCard(Modifier.fillMaxWidth()) {
            s.coopAsked.forEachIndexed { i, qa ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Text("“${qa.question}”", fontSize = 12.sp, color = PSub)
                Text(
                    when (qa.by) {
                        "child" -> "\"${qa.answer}\""
                        "card" -> "${qa.answer} (카드로 골랐어요)"
                        "mascot" -> "${qa.answer} (마스코트가 대신 정했어요)"
                        else -> "답하지 않았어요"
                    },
                    fontSize = 14.sp, color = Ink, fontWeight = if (qa.by == "child") FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }

    // 「가기 전 · 다녀온 뒤」 — 오늘 책이 다녀온 책이고 짝이 책장에 있을 때만 (협업모드_확장_설계 §2-5)
    val pair = remember(coopNow, s.shelf.size) { if (coopNow) s.coopBeforeAfter() else null }
    if (pair != null) {
        Section("가기 전 · 다녀온 뒤", "‘${pair.name}’ · 맞고 틀린 게 아니라 상상한 말과 겪고 나서 한 말이에요")
        PCard(Modifier.fillMaxWidth()) {
            Row {
                Spacer(Modifier.width(64.dp))
                Text("가기 전${pair.beforeDate.takeIf(String::isNotBlank)?.let { " ($it)" } ?: ""}", fontSize = 12.sp, color = PSub, modifier = Modifier.weight(1f))
                Text("다녀온 뒤${pair.afterDate.takeIf(String::isNotBlank)?.let { " ($it)" } ?: ""}", fontSize = 12.sp, color = PSub, modifier = Modifier.weight(1f))
            }
            pair.rows.forEach { r ->
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(r.label, fontSize = 12.sp, color = PSub, modifier = Modifier.width(64.dp))
                    Text(r.before, fontSize = 13.sp, color = Ink, fontWeight = if (r.beforeByChild) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text(r.after, fontSize = 13.sp, color = Ink, fontWeight = if (r.afterByChild) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                }
            }
            pair.summary?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 14.sp, color = Ink)
            }
            Spacer(Modifier.height(4.dp))
            Text("ⓘ 두 책은 질문이 달라서 길이나 횟수를 견주지 않아요. 아이가 한 말 그대로예요.", fontSize = 11.sp, color = PSub)
        }
    }

    // 부모 협업 모드가 파는 것 — 동화책이 아니라 **질문하는 법**이다 (협업 §7).
    // ⚠️ 점수를 보여 주지 않는다. "당신의 질문은 60점"은 앱을 지우게 만든다. 남기는 형태는 다음에 넣어 볼 질문 한 개다.
    // 9/22 — 협업은 **부모가 질문을 미리 넣어 두는 모드**가 됐다 (guidelines/9 §9-5). 부모가 옆에서 기다린다는 전제의
    //    문구("아이가 막히면 재촉하지 말고 기다려 주세요")는 뺐다. 넣어 둔 질문을 규칙으로 살펴 주는 일은 아직 없다 —
    //    10-06 — 넣어 둔 질문에 아이가 한 답이 있으면 거기서 하나를 고른다(`CoopReport.kt` nextQuestionFromAnswers ·
    //    아이가 제일 많이 말한 질문, 없으면 답이 안 나온 질문 하나를 바꿔 쓴 예와 함께). 넣은 질문이 없으면 이유별 예시 한 개.
    if (coopNow) {
        Section("다음에 넣어 볼 질문", if (s.coopParentUsed > 0) "넣어 둔 질문에 아이가 한 답에서 골랐어요 · 점수가 아니라 질문 한 개예요"
            else "부모 협업 모드에서만 · 점수가 아니라 질문 한 개예요")
        PCard(Modifier.fillMaxWidth()) {
            val copy = coopCopy!!
            Text("“${copy.nextQuestion}”", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(copy.nextWhy, fontSize = 13.sp, color = PSub)
            Spacer(Modifier.height(4.dp))
            Text("ⓘ 부모 질문에 점수를 매기지 않습니다. 이 칸은 다음에 넣어 볼 질문 한 개만 알려 드려요.", fontSize = 11.sp, color = PSub)
        }
    }

    Spacer(Modifier.height(18.dp))
    PastReports(past, r) { picked = it; talkOpen = false }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3EDE3)).padding(12.dp)) {
        Column {
            listOf(
                "ⓘ 놀이 대화 요약이며 언어발달 평가 · 진단이 아닙니다.",
                // 10-06: 「이름은 가린 뒤에야」는 10-02 전 설계다 — 지금은 가리지 않는다(guidelines/1 규칙 6 · Terms.kt 10-05 판과 같은 말로)
                "ⓘ 아이 목소리는 글자로 바꾼 뒤 바로 지워지고, 글자는 처리 위탁사(OpenAI · TypeSafe)로 갑니다 — TypeSafe 에는 이름을 가려서. 그림과 녹음한 소리는 폰 안에만 있어요.",
                "ⓘ 궁금한 점은 영유아건강검진(K-DST)이나 언어재활사와 상담하세요.",
            ).forEach { Text(it, fontSize = 11.sp, color = PSub) }
        }
    }
}

private fun wa3(w: String) = if (bat(w)) "이와" else "와"

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 12.sp, color = Ink) }
}

@Composable
private fun Dots(n: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < n) PAccent else PLine)) }
    }
}

/** 기승전결 네 자리 — 부모에게는 「기승전결」 대신 묻는 말로 보여 준다 (템플릿 질문 0~3과 같은 순서) */
private val COOP_PARTS = listOf("어디", "무슨 일", "왜", "어떻게 됐나")

/**
 * 부모가 더 적을 수 있는 질문 수 — 꼬리질문 자리에 끼워 묻는데, 조건 없이 늘 묻는 꼬리질문 자리가 다섯이다
 * (`CoopSteps.kt` · companion · detail · try · after · keep). 더 받으면 못 묻고 끝나는 질문이 생긴다
 */
const val COOP_MAX = 5

/** 추천 — 지금은 대본이다. 나중에 LLM이 고른 이야기를 보고 추천한다 (guidelines/9 §9-5 "추천하는 정도로만") */
val COOP_SUGGESTIONS = listOf(
    "제일 재밌었던 게 뭐였어?",
    "거기서 누구를 만났어?",
    "그때 어떤 기분이 들었어?",
    "다음엔 뭐 해 보고 싶어?",
)

/**
 * 고른 이유에 맞춘 추천 — 곧 해요에 「뭐였어? · 만났어?」를 권하면 오또의 갈무리(시제)와 어긋난다.
 * 다녀왔어요 · 이유 없이 질문만이면 [COOP_SUGGESTIONS] 그대로
 */
fun coopSuggestions(reason: CoopReason?): List<String> = when (reason) {
    CoopReason.SOON -> listOf("거기서 제일 해 보고 싶은 게 뭐야?", "거기서 누구를 만날 것 같아?", "가기 전에 어떤 기분이 들어?", "다녀오면 뭐 해 보고 싶어?")
    CoopReason.DREAM -> listOf("그 이야기에서 뭐가 제일 좋아?", "누가 같이 나오면 좋겠어?", "그러면 어떤 기분일까?", "그다음엔 무슨 일이 생길까?")
    else -> COOP_SUGGESTIONS
}

/**
 * 같이 만들기(옛 이름 협업 질문) — **부모가 이야기를 고르고, 더 물어볼 질문을 적어 두는 곳** (09-30 개정).
 * 이 화면이 협업 모드의 절반이다 — 나머지 절반은 오또가 일반 모드처럼 묻되 고른 이야기에 맞추는 것(CoopScenes).
 *
 * - 템플릿은 **맥락**이다 — 질문 줄을 채우지 않는다. 오또가 기승전결 네 자리에서 그 맥락으로 묻는다
 * - 적은 질문은 **끼워 넣는 것**이다 — 네 자리가 아니라 꼬리질문 자리에 적은 순서대로 들어간다
 * - 글자만 받는다. 음성 입력은 안 한다 (구현설계 §1-⑤)
 * - 귀띔은 [questionHint] — 막지도 점수를 매기지도 않는다 (§1-⑥ · 설계 §7)
 * - 빈 줄은 그대로 둔다. 읽는 쪽(CoopScenes)이 빈 줄을 건너뛴다
 */
@Composable
private fun CoopQuestionsTab(c: CoopDraft) {
    if (!c.editing) { CoopSavedCard(c); return }
    val qs = c.qs
    // 「다녀온 뒤」 상자 — 준비된 이야기가 없고, 지금 상자에서 꺼내 고치는 중이 아닐 때만 (협업모드_확장_설계 §2-2)
    if (!c.hasSaved && c.fromAfter == null) CoopAfterCards(c)

    fun set(i: Int, text: String) {
        while (qs.size <= i) qs.add("")
        qs[i] = text
    }

    PCard(Modifier.fillMaxWidth()) {
        val after = c.fromAfter
        if (after != null) {
            // a draft opened from the 「다녀온 뒤」 box says so (#255) — the parent saw the old heading and thought nothing happened
            Text("‘${after.name}’ 다녀온 이야기를 준비하고 있어요", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("질문은 비워 두었어요. 더 물어볼 게 있으면 적고, [저장하기]를 누르면 소파에 🎁가 붙어요.", fontSize = 13.sp, color = PSub)
        } else {
            Text(if (c.hasSaved) "저장된 이야기를 고치는 중이에요" else "오늘 아이와 만들 이야기를 골라 두세요", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("이야기를 고르거나 질문을 적은 뒤 아래 [저장하기]를 눌러야 확정돼요. 아이가 오또의 방에서 소파(같이 만들기)를 누르면 오또가 평소처럼 물어보되, 고른 이야기에 맞춰 묻고 적어 둔 질문도 중간에 끼워 물어봐요. 오늘 이야기에만 쓰여요.", fontSize = 13.sp, color = PSub)
        }
    }

    CoopTemplateCards(c)
    val suggestions = (if (c.fromAfter != null) listOf(COOP_AFTER_SUGGESTION) else emptyList()) + coopSuggestions(c.pick?.reasonOrNull())

    Section("더 물어볼 질문 (선택)", "오또가 이야기 중간에 적은 순서대로 끼워서 물어봐요 · ${COOP_MAX}개까지")
    val rows = maxOf(1, qs.size)
    for (i in 0 until rows) {
        val text = qs.getOrNull(i) ?: ""
        val label = "질문 ${i + 1}"
        PCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = PSub, modifier = Modifier.width(72.dp))
                TextField(
                    value = text,
                    onValueChange = { set(i, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("예: ${suggestions[i % suggestions.size]}", fontSize = 14.sp, color = PSub.copy(alpha = 0.6f), fontFamily = ParentFont) },   // 글꼴을 맞춘다 (#98)
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PBg, unfocusedContainerColor = PBg,
                        focusedIndicatorColor = PAccent, unfocusedIndicatorColor = PLine,
                    ),
                )
                if (text.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (i < qs.size) qs.removeAt(i) }.padding(8.dp),
                    ) { Text("🗑", fontSize = 16.sp) }
                }
            }
            questionHint(text)?.let { h ->
                Spacer(Modifier.height(4.dp))
                Text("💡 ${h.why}", fontSize = 12.sp, color = PAccent)
                Text(h.example, fontSize = 12.sp, color = PSub)
            }
            // 오또가 묻기 전 갈무리와 같은 기준의 귀띔 — 막지 않고, 바꿀지는 부모가 고른다 (10-02 · CoopGuard.kt)
            var kept by remember(text) { mutableStateOf(false) }
            if (!kept) coopParentAdvice(text)?.let { a ->
                Spacer(Modifier.height(4.dp))
                a.notes.forEach { Text("💡 $it", fontSize = 12.sp, color = PAccent) }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.suggestion?.let { sug ->
                        CoopChip("“$sug” 로 바꿀게요", on = true) { set(i, sug) }
                    }
                    CoopChip("그대로 둘게요", on = false) { kept = true }
                }
            }
        }
    }
    if (rows < COOP_MAX) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF3EDE3))
                .clickable { set(rows, "") }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("＋ 하나 더", fontSize = 13.sp, color = Ink) }
    }

    // 이미 넣은 질문은 목록에서 뺀다 — 다 넣었으면 칸째로 숨긴다 (#98)
    val left = suggestions.filter { q -> qs.none { it.trim() == q } }
    if (left.isNotEmpty()) Section("이런 질문은 어때요", "탭하면 빈 자리에 들어가요")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            left.forEach { q ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            val empty = (0 until maxOf(rows, qs.size)).firstOrNull { (qs.getOrNull(it) ?: "").isBlank() }
                            when {
                                empty != null -> set(empty, q)
                                qs.size < COOP_MAX -> qs.add(q)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("“$q”", fontSize = 13.sp, color = Ink) }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3EDE3)).padding(12.dp)) {
        Column {
            listOf(
                "ⓘ 넣은 질문에 점수를 매기지 않아요. 아이가 더 길게 답할 만한 방법만 귀띔해요.",
                "ⓘ 질문을 안 적어도 괜찮아요. 오또가 고른 이야기에 맞춰 처음부터 끝까지 물어봐요.",
                "ⓘ 아이 말은 마이크로 받아 글자로 바꾼 뒤 바로 지워요. 글자는 이름 그대로 서버와 처리 위탁사로 가요.",
            ).forEach { Text(it, fontSize = 11.sp, color = PSub) }
        }
    }
}

/**
 * 템플릿으로 준비하기 — **장소 · 직업 · 스포츠 → 요소 하나(직접 쓰기 포함) → 고른 이유** (09-29 · [COOP_KINDS]).
 * 고른 것은 **이야기의 맥락**이다 (09-30) — 질문 줄(`parentQuestions`)은 건드리지 않는다. 오또가 기승전결 네 자리에서
 * 이 맥락에 맞춰 묻고([templateQuestions]), 부모에게는 그 질문을 미리 보여 준다([CoopTemplatePreview]).
 * 고른 것은 초안([CoopDraft])에만 담기고, [저장하기]를 눌러야 `s.coopPick` 에 남는다.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CoopTemplateCards(c: CoopDraft) {
    val pick = c.pick
    // keyed on fromAfter, not on pick (#255): the cards are first drawn with no pick, so a plain remember kept kindKey = null
    // and the draft filled by [있었던 일로 준비하기] never showed. pick?.kind as the key would undo a kind the parent changed
    // before choosing an item; fromAfter only changes when a box card is opened or cancelled
    var kindKey by remember(c.fromAfter) { mutableStateOf(c.pick?.kind) }
    var typing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val kind = kindKey?.let { coopKind(it) }
    // 종류를 누르면 다음 단계(하나 고르기)가 화면 아래에 생겨 변화가 안 보였다 — 그 자리로 끌어온다 (#98)
    val nextStep = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var tappedKind by remember { mutableStateOf(0) }
    LaunchedEffect(tappedKind) { if (tappedKind > 0) { kotlinx.coroutines.delay(60); nextStep.bringIntoView() } }

    fun reasonOf(p: CoopPick?) = p?.reasonOrNull()

    /** 고른 것을 초안에 — 맥락만 담는다. 적어 둔 질문은 그대로 */
    fun apply(k: CoopKind, name: String, reason: CoopReason?) {
        c.pick = CoopPick(k.key, name, reason?.key)
    }

    Section("이야기 고르기", "고르면 오또가 이 이야기에 맞춰 물어봐요 · [저장하기]로 확정")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COOP_KINDS.forEach { k ->
            val on = kind?.key == k.key
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Color(0xFFFFF1CC) else Color.White)
                    .border(1.dp, if (on) PAccent else PLine, RoundedCornerShape(14.dp))
                    .clickable { kindKey = k.key; typing = false; err = null; tappedKind++ }
                    .padding(10.dp),
            ) {
                AssetImage(coopKindArt(k), Modifier.size(48.dp)) { Text(k.emoji, fontSize = 20.sp) }
                Text("${k.title} · ${k.arc}", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(k.items.joinToString(" · "), fontSize = 11.sp, color = PSub, maxLines = 2)
            }
        }
    }

    if (kind == null) return
    val picked = pick?.takeIf { it.kind == kind.key }

    Section("${kind.title} 하나 고르기", "목록에 없으면 직접 써도 돼요")
    FlowRow(Modifier.bringIntoViewRequester(nextStep), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kind.items.forEach { name ->
            CoopChip(name, on = picked?.name == name, art = COOP_ITEM_ART[name]) { typing = false; err = null; apply(kind, name, reasonOf(picked)) }
        }
        val customOn = picked != null && picked.name !in kind.items
        if (!typing) CoopChip(if (customOn) "✏️ ${picked!!.name}" else "✏️ 직접 쓰기", on = customOn, dashed = !customOn, art = COOP_CUSTOM_ART) {
            typing = true; err = null; draft = if (customOn) picked!!.name else ""
        }
    }
    if (typing) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it.take(COOP_NAME_MAX + 4); err = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("${kind.title} 이름 (예: ${kind.customExample})", fontSize = 13.sp, color = PSub.copy(alpha = 0.6f), fontFamily = ParentFont) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PBg, unfocusedContainerColor = PBg,
                    focusedIndicatorColor = PAccent, unfocusedIndicatorColor = PLine,
                ),
            )
            Spacer(Modifier.width(8.dp))
            CoopChip("넣기", on = true) {
                val v = cleanCoopName(draft)
                if (v == null) err = if (draft.isBlank()) "이름을 한 글자 이상 써 주세요" else "한글 · 영문 · 숫자로 ${COOP_NAME_MAX}자까지 써 주세요"
                else { typing = false; apply(kind, v, reasonOf(picked)) }
            }
        }
        err?.let { Text(it, fontSize = 12.sp, color = Curtain, modifier = Modifier.padding(top = 4.dp)) }
    }

    if (picked == null) return
    Section("고른 이유가 있나요?", "하나만 · 이유에 따라 첫 질문이 달라져요")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoopReason.entries.forEach { r ->
            CoopChip("${kind.reasonLabels[r]}", sub = kind.reasonExamples[r], on = picked.reason == r.key, art = coopReasonArt(r)) {
                apply(kind, picked.name, if (picked.reason == r.key) null else r)
            }
        }
    }
    Text("안 고르면 상상 이야기로 물어봐요.", fontSize = 11.sp, color = PSub, modifier = Modifier.padding(top = 4.dp))

    CoopTemplatePreview(picked)
}

/** 고른 이야기로 오또가 네 자리에서 물을 질문 — 미리 보기. 지금은 틀에 이름을 끼운 대본, LLM이 붙으면 아이 답에 맞춰 달라진다 */
@Composable
private fun CoopTemplatePreview(pick: CoopPick) {
    val lines = pick.templateQuestions()
    if (lines.isEmpty()) return
    Section("오또가 이렇게 물어봐요", "이야기 뼈대 네 자리 · 아이가 어려워하면 쉬운 말로 바꿔 물어요")
    PCard(Modifier.fillMaxWidth()) { CoopTemplateLines(lines) }
}

@Composable
private fun CoopTemplateLines(lines: List<String>) {
    lines.forEachIndexed { i, q ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(COOP_PARTS.getOrNull(i) ?: "", fontSize = 12.sp, color = PSub, modifier = Modifier.width(72.dp))
            Text("“$q”", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 같이 만들기 **초안** (09-30 사용자 요청 — 고르자마자 확정되던 것을 [저장하기]로 확정하게).
 * 고른 이야기 · 질문 줄은 여기에만 쓰고, [save] 할 때 `s.coopPick` · `s.parentQuestions` 로 옮긴다.
 * 그래서 저장하기 전에는 소파에 🎁 가 붙지 않고, 부모 모드를 나가면 저장 안 한 초안은 버려진다.
 * 저장한 뒤에는 [CoopSavedCard] 가 보이고, 거기서 [edit](수정) · [delete](삭제)를 한다.
 */
private class CoopDraft(private val s: DemoState) {
    val qs = mutableStateListOf<String>()
    var pick by mutableStateOf<CoopPick?>(null)
    /** 저장된 것이 없으면 처음부터 고치는 화면, 있으면 저장된 카드부터 */
    var editing by mutableStateOf(!s.coopReady)
    var askDelete by mutableStateOf(false)
    /** 「다녀온 뒤」 상자에서 꺼내 채운 초안이면 그 칸 — [save] 할 때 같은 이야기 · 다녀왔어요 그대로면 짝을 지을 책을 기억한다 */
    var fromAfter by mutableStateOf<CoopAfter?>(null)
    /** [아직이에요] 로 닫은 칸 — 부모 모드를 나갔다 오면 다시 보인다 */
    val hidden = mutableStateListOf<CoopAfter>()

    init { load() }

    /** 상자 — 가기 전 책이 아직 책장에 있는 것만 */
    val afters: List<CoopAfter>
        get() = CoopPlan.after(s).filter { a -> a !in hidden && CoopShelf.books(s).any { it.id == a.beforeBookId } }

    /** [있었던 일로 준비하기] — 같은 종류 · 이름 · 다녀왔어요로 채운 편집 화면. 질문 칸은 비운다. 확정은 [저장하기] */
    fun prepareAfter(a: CoopAfter) {
        qs.clear(); pick = CoopPick(a.kind, a.name, CoopReason.DONE.key); fromAfter = a; askDelete = false; editing = true
        com.example.finalproject_demo.net.Trace.line("parent", "prepare after-story ‘${a.name}’ (before book ${a.beforeBookId})")
    }

    fun dismissAfter(a: CoopAfter) {
        com.example.finalproject_demo.net.Trace.line("parent", "after-story ‘${a.name}’ not yet (before book ${a.beforeBookId})")
        CoopPlan.dismissAfter(s, a)
    }

    val hasSaved: Boolean get() = s.coopReady
    /** 이야기를 골랐거나 질문을 하나라도 적었나 */
    val filled: Boolean get() = pick != null || qs.any { it.isNotBlank() }
    val dirty: Boolean get() = qs.toList() != s.parentQuestions.toList() || pick != s.coopPick
    /** 저장할 수 있나 — 바뀐 게 있고, 비어 있지 않을 때 (다 지우고 싶으면 [삭제하기]) */
    val canSave: Boolean get() = dirty && filled

    private fun load() { qs.clear(); qs.addAll(s.parentQuestions); pick = s.coopPick }

    fun edit() { load(); askDelete = false; editing = true }

    fun cancel() { load(); fromAfter = null; editing = !s.coopReady }

    fun save() {
        if (!canSave) return
        s.parentQuestions.clear(); s.parentQuestions.addAll(qs.dropLastWhile { it.isBlank() })
        s.parentQIndex = 0
        s.coopPick = pick
        // 상자에서 꺼낸 그대로(같은 이야기 · 다녀왔어요)일 때만 짝 — 부모가 이름이나 이유를 바꿨으면 다른 이야기다
        val paired = fromAfter?.takeIf { a -> pick?.kind == a.kind && pick?.name?.trim() == a.name && pick?.reasonOrNull() == CoopReason.DONE }
        paired?.let { CoopPlan.startAfter(s, it) }
        com.example.finalproject_demo.net.Trace.line("parent", "co-op plan saved · ${pick?.kind}/${pick?.name}/${pick?.reason} · ${qs.count { it.isNotBlank() }} questions" +
            (paired?.let { " · paired with before book ${it.beforeBookId}" } ?: ""))
        fromAfter = null
        CoopPlan.saved(s)         // 앱을 껐다 켜도 남는다 (#98 · CoopPlanStore.kt)
        load(); editing = false
    }

    fun delete() { s.clearParentQuestions(); load(); askDelete = false; editing = true }
}

/**
 * 「다녀온 뒤」 상자 카드 (협업모드_확장_설계 §2-2) — 곧 해요로 지은 책마다 하나.
 * 아이 화면에는 아무 표시가 없다(다음 책을 권하지 않는다). 다녀온 이야기를 열지는 부모가 여기서 정한다
 */
@Composable
private fun CoopAfterCards(c: CoopDraft) {
    c.afters.forEach { a ->
        PCard(Modifier.fillMaxWidth()) {
            Text("🧳 ${coopAfterAsk(a)}", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(a.madeAt.takeIf(String::isNotBlank)?.let { "${it}에" }, "「${a.beforeTitle}」${eul(a.beforeTitle)} 지었어요.").joinToString(" ") +
                    " 다녀왔다면 이번엔 진짜 있었던 일로 지어 봐요. 두 책을 부모 리포트에서 나란히 볼 수 있어요.",
                fontSize = 13.sp, color = PSub,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("있었던 일로 준비하기", PMint, Modifier.weight(1.4f)) { c.prepareAfter(a) }
                PButton("아직이에요", PSub, Modifier.weight(1f), outline = true) { c.hidden += a }
                PButton("안 하게 됐어요", PSub, Modifier.weight(1f), outline = true) { c.dismissAfter(a) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/** 저장된 이야기 — 무엇을 골랐나 · 오또가 물을 순서 · [수정하기] [삭제하기] */
@Composable
private fun CoopSavedCard(c: CoopDraft) {
    val pick = c.pick
    val kind = pick?.let { coopKind(it.kind) }
    PCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (kind != null) AssetImage(coopKindArt(kind), Modifier.size(56.dp)) { Text(kind.emoji, fontSize = 26.sp) }
            else AssetImage("pi_coop", Modifier.size(56.dp)) { Text("🤝", fontSize = 26.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("✓ 저장됐어요 · 소파에 🎁 표시가 붙어요", fontSize = 12.sp, color = PMint, fontWeight = FontWeight.Bold)
                Text(
                    if (pick != null && kind != null) "${kind.title} · ‘${pick.name}’ ${kind.arc}" else "직접 적은 질문",
                    fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold,
                )
                val reason = pick?.reason?.let { r -> CoopReason.entries.firstOrNull { it.key == r } }
                if (kind != null) Text(reason?.let { "고른 이유 · ${kind.reasonLabels[it]}" } ?: "고른 이유 없음 · 상상 이야기로 물어봐요", fontSize = 12.sp, color = PSub)
            }
        }
        Spacer(Modifier.height(12.dp))
        val lines = pick?.templateQuestions().orEmpty()
        if (lines.isNotEmpty()) {
            Text("오또가 이야기 뼈대 네 자리에서 이렇게 물어봐요", fontSize = 13.sp, color = PSub)
            Spacer(Modifier.height(6.dp))
            CoopTemplateLines(lines)
        }
        val mine = c.qs.filter { it.isNotBlank() }
        if (mine.isNotEmpty()) {
            if (lines.isNotEmpty()) Spacer(Modifier.height(10.dp))
            Text("적어 둔 질문 · 이야기 중간에 이 순서로 끼워 물어봐요", fontSize = 13.sp, color = PSub)
            Spacer(Modifier.height(6.dp))
            mine.forEachIndexed { i, q ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("질문 ${i + 1}", fontSize = 12.sp, color = PSub, modifier = Modifier.width(72.dp))
                    Text("“$q”", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!c.askDelete) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PButton("✏️ 수정하기", PAccent, Modifier.weight(1f)) { c.edit() }
                PButton("🗑 삭제하기", Curtain, Modifier.weight(1f), outline = true) { c.askDelete = true }
            }
        } else {
            // 되돌릴 수 없으니 한 번 더 묻는다 — 시스템 창 대신 카드 안에서. 화면 아래로 잘리지 않게 끌어올린다
            val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            LaunchedEffect(Unit) { bring.bringIntoView() }
            Box(Modifier.bringIntoViewRequester(bring).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDECE8)).padding(12.dp)) {
                Column {
                    Text("저장된 이야기를 지울까요?", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("지우면 소파의 🎁 표시가 없어지고, 이야기를 처음부터 다시 골라야 해요.", fontSize = 12.sp, color = PSub)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PButton("아니요", PSub, Modifier.weight(1f), outline = true) { c.askDelete = false }
                        PButton("지우기", Curtain, Modifier.weight(1f)) { c.delete() }
                    }
                }
            }
        }
    }
}

/** 고치는 동안 아래에 고정되는 줄 — 상태 한 줄 · [취소] · [저장하기] */
@Composable
private fun CoopSaveBar(c: CoopDraft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PLine))
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                c.canSave -> "저장하지 않은 변경이 있어요 · 저장해야 소파에 🎁가 붙어요"
                !c.filled -> "이야기를 고르거나 질문을 하나 이상 적어 주세요"   // 「템플릿」은 개발 용어 (#98)
                else -> "바뀐 것이 없어요"
            },
            fontSize = 13.sp, color = if (c.canSave) PAccent else PSub, modifier = Modifier.weight(1f),
        )
        if (c.hasSaved) {
            PButton("취소", PSub, Modifier.width(96.dp), outline = true) { c.cancel() }
            Spacer(Modifier.width(10.dp))
        }
        PButton("저장하기", PMint, Modifier.width(140.dp), enabled = c.canSave) { c.save() }
    }
}

@Composable
private fun PButton(text: String, color: Color, modifier: Modifier = Modifier, outline: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.Round)
    Box(
        modifier
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (outline) Modifier.clip(shape).background(Color.White).border(1.5.dp, color, shape) else Modifier.felt(color, shape, lift = 2.dp, texture = false))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, color = if (outline) color else Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CoopChip(text: String, on: Boolean, sub: String? = null, dashed: Boolean = false, art: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) PAccent else if (dashed) Color.White else Color(0xFFF3EDE3))
            .then(if (dashed) Modifier.border(1.dp, PAccent, RoundedCornerShape(999.dp)) else Modifier)
            .clickable { onClick() }
            .padding(start = if (art != null) 8.dp else 14.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 펠트 그림이 있으면 앞에 작게 — 없으면 글자만 (이모지를 따로 두지 않는다)
        if (art != null && assetId(art) != 0) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = if (on) 0.9f else 0.7f)), contentAlignment = Alignment.Center) {
                AssetImage(art, Modifier.size(26.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(if (art != null && assetId(art) != 0) text.removePrefix("✏️ ") else text, fontSize = 13.sp, color = if (on) Color.White else if (dashed) PAccent else Ink)
        if (sub != null) {
            Spacer(Modifier.width(6.dp))
            Text(sub, fontSize = 11.sp, color = if (on) Color.White.copy(alpha = 0.85f) else PSub)
        }
    }
}

@Composable
private fun AchievementsTab(d: Director) {
    val s = d.s
    PCard(Modifier.fillMaxWidth()) {
        Text("해결 방법 도감", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("친구와 함께" to true, "혼자 해 보기" to false, "도움 청하기" to false, "기다리기" to false).forEach { (t, on) ->
                val got = on && "해결 방법 도감 · 친구와 함께" in s.achievements
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (got) Color(0xFFFFF1CC) else Color(0xFFF3EDE3))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (got) AssetImage("gift_book", Modifier.size(40.dp)) { Text("🧩", fontSize = 22.sp) }
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("?", fontSize = 18.sp, color = PSub, fontWeight = FontWeight.Bold) }
                    Text(t, fontSize = 12.sp, color = if (got) Ink else PSub)
                }
            }
        }
    }
    // 받은 보상은 폰에 남고 그림판 도구가 된다 (demo/Rewards.kt · 10-06 종훈 · #223)
    Section("받은 선물", "아이가 한 일로만 받아요 · 받으면 다음 그림판에서 바로 써요")
    Reward.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { r ->
                val got = r in Rewards.owned
                PCard(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (got) Color(0xFFFFF1CC) else Color(0xFFF3EDE3)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (got) r.emoji else "?", fontSize = 20.sp, color = PSub) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 14.sp, color = if (got) Ink else PSub, fontWeight = FontWeight.Bold)
                            Text(r.how, fontSize = 12.sp, color = PSub)
                            Rewards.dayOf(r)?.let { d -> Text("${d.substring(5).replace('-', '/')} 받음 · 그림판에서 써요", fontSize = 11.sp, color = PAccent) }
                        }
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    // 미션에서 오늘 받은 손 배지 — 그 이야기 안의 것이라 오늘만 보인다
    val badges = s.achievements.filterNot { it.startsWith("해결 방법 도감") || "크레용" in it }
    if (badges.isNotEmpty()) {
        Section("오늘 미션에서 받은 것")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { badges.forEach { Chip("🏅 $it", Sun) } }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 12.sp, color = PSub)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = PAccent, checkedThumbColor = Color.White, uncheckedTrackColor = PLine, uncheckedThumbColor = Color.White, uncheckedBorderColor = PLine),
        )
    }
}

/** 설정 — 하루 한도(토글 · 권수) · 시작 비밀번호(토글) · 그림체 4종 · 데이터. 다시 그리기 상한은 뺐다 (v0.8) */
/**
 * 오또가 아이를 부르는 말 (10-02 조장) — 실명 칸이 아니라 **호칭**이다. 10-06 조장: 별명을 권한다(예시도 별명만) —
 * 이 말은 대사 · 목소리와 함께 위탁사로 가므로, 실명이 아니면 알아볼 수 있는 정보가 하나 준다.
 * 그대로 서버(대사 · 책)와 목소리로 간다(`net/ChildCall`). 비우면 「친구」.
 */
@Composable
private fun ChildCallSection() {
    var text by remember { mutableStateOf(com.example.finalproject_demo.net.ChildCall.name ?: "") }
    PCard(Modifier.fillMaxWidth()) {
        Text("오또가 아이를 뭐라고 부를까요?", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text("실명 대신 별명을 권해요 · 오또가 이야기와 목소리에서 이렇게 불러요 · 비우면 「친구」", fontSize = 12.sp, color = PSub)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it.take(10) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("예: 콩이 · 별님 · 꼬마공룡", fontSize = 14.sp, color = PSub.copy(alpha = 0.6f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PBg, unfocusedContainerColor = PBg,
                    focusedIndicatorColor = PAccent, unfocusedIndicatorColor = PLine,
                ),
            )
            Spacer(Modifier.width(8.dp))
            PButton("저장", PAccent, Modifier.widthIn(min = 72.dp)) { com.example.finalproject_demo.net.ChildCall.set(text) }
        }
    }
}

@Composable
private fun SettingsTab(d: Director) {
    val s = d.s
    // 정책이 요구하는 세 자리 — 내용은 `ui/Consent.kt` 에 있다 (치영 · 9/23).
    // 여기는 부르는 줄만 둔다: 소리와 진동(09-25) · 신고 · AI 음성 고지 · 보호자 동의 철회
    ChildCallSection()
    Spacer(Modifier.height(10.dp))
    SoundSettingsSection()
    Spacer(Modifier.height(10.dp))
    ReportSection { d.log(it) }
    Spacer(Modifier.height(10.dp))
    AiVoiceNotice()
    Spacer(Modifier.height(10.dp))
    ConsentWithdrawSection()
    Spacer(Modifier.height(10.dp))
    PCard(Modifier.fillMaxWidth()) {
        SettingRow("하루 한도", "하루에 만들 수 있는 이야기 수를 정해요 · 끄면 별을 쓰지 않아요", s.limitOn) { d.send(Reply.Tapped("set:limit", "한도")) }
        if (s.limitOn) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하루에", fontSize = 14.sp, color = Ink)
                Spacer(Modifier.width(10.dp))
                Stepper("−") { d.send(Reply.Tapped("set:limit-", "−")) }
                Text("${s.dailyLimit}권", fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                Stepper("＋") { d.send(Reply.Tapped("set:limit+", "＋")) }
                Spacer(Modifier.weight(1f))
                Chip("오늘 남은 이야기 ${s.dayStars}권", Sun)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PCard(Modifier.fillMaxWidth()) {
        SettingRow(
            "이야기를 시작할 때 비밀번호",
            if (s.pinToStart) "켜짐 — 어른이 비밀번호를 넣어야 새 이야기를 시작해요" else "꺼짐 — 아이가 [이야기 만들기]로 바로 시작해요",
            s.pinToStart,
        ) { d.send(Reply.Tapped("set:pin", "비밀번호")) }
        Spacer(Modifier.height(4.dp))
        Text("아이 혼자 별을 다 써 버리거나 이야기를 계속 이어 만드는 것을 막을 때 켜 두세요.", fontSize = 11.sp, color = PSub)
    }

    // 준비된 그림체가 하나뿐이면 고를 것이 없다 — 칸째 숨긴다(「데모 그림 준비 중」 견본만 보이던 것 · #154)
    if (ART_STYLES.count { it.ready } > 1) {
    Section("그림체", "다음 책부터 세계 그림에 적용 · 아이 그림 · 도감 주인공은 그대로")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ART_STYLES.filter { it.ready }.forEach { st ->
            val on = s.artStyle == st.key
            Column(
                Modifier
                    .weight(1f)
                    .felt(FeltWhite, RoundedCornerShape(16.dp), lift = if (on) 4.dp else 1.dp, texture = false, stitch = false)
                    .border(if (on) 3.dp else 0.dp, if (on) PAccent else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { d.send(Reply.Tapped("set:style:${st.key}", st.name)) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                    AssetImage(st.img, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF3EDE3)), contentAlignment = Alignment.Center) { ArtView(Art.DinoArt(Color(0xFF6FC276), "horn"), Modifier.size(60.dp)) }
                    }
                    if (on) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(PAccent), contentAlignment = Alignment.Center) {
                        Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(st.name, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
    }

    // ── 아래 넷은 9/22 멘토 검토가 찾은 공백이다 (guidelines/9 §9-5 박진웅). **스케치다** — 자리와 문구만 있고
    //    눌러도 동작하지 않는다. 저장이 없으므로(§9-8) 열람·정정·삭제도 실제로 할 것이 없다. 붙을 때 이 카드들이 진짜가 된다.
    Section("내 아이의 기록", "보호자가 보고 · 고치고 · 지울 수 있어야 해요")
    PCard(Modifier.fillMaxWidth()) {
        listOf(
            Triple("보기", "오늘 아이가 한 말과 만든 책을 그대로 봐요", "「오늘의 기록」 · 「책장」"),
            Triple("고치기", "이름을 잘못 가렸거나 잘못 알아들은 말이 있으면 고쳐요", "준비 중"),
            Triple("지우기", "책 한 권 · 오늘 기록 · 전부, 골라서 지워요", "준비 중"),
        ).forEach { (t, desc, state) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(t, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text(desc, fontSize = 12.sp, color = PSub)
                }
                Chip(state, if (state == "준비 중") PLine else PMint)
            }
        }
    }

    Section("무엇이 언제 생기고 언제 지워지나")
    PCard(Modifier.fillMaxWidth()) {
        listOf(
            "아이가 녹음한 소리(울음소리 등)" to "이 폰에만 · 폰 밖으로 안 나가요 · 책을 지우면 같이 지워져요",
            "아이가 말한 음성" to "글자로 바꾸려고 우리 서버까지만 가요 · 글자가 되면 바로 지워요 · 다른 회사에는 안 보내요",
            // 10-06: 세 줄을 Terms.kt 10-05 판과 맞췄다 — 이름은 가리지 않고(규칙 6), 그림은 「오또가 그려 줘」를 고를 때만 다시 그리며
            //        (demo/DiaryRedraw.kt), 책은 이 폰의 책장에 저장된다(StoryBookStore · DiaryBookStore · CoopBookStore)
            "글자로 바뀐 말" to "서버와 처리 위탁사(OpenAI · TypeSafe)로 가요 — OpenAI 에는 이름 그대로, TypeSafe 에는 이름을 가려서 · 이 폰에 기록으로 남아요",
            "아이 그림" to "이 폰에만 · 「오또가 그려 줘」를 고른 조각만 우리 서버에서 다시 그리고 바로 지워요",
            "어른이 넣어 둔 질문" to "그 이야기 한 번에만 쓰고 지워요",
        ).forEach { (what, how) ->
            Text(what, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(how, fontSize = 12.sp, color = PSub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("ⓘ 만든 책 · 아이 그림 · 녹음은 이 폰의 책장에만 저장돼요. 책을 지우거나 탈퇴하면 같이 지워져요.", fontSize = 11.sp, color = PSub)
    }

    Section("AI 목소리 알림")
    PCard(Modifier.fillMaxWidth()) {
        Text("마스코트 목소리는 사람이 아니라 AI가 만든 소리예요", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("보호자에게: 처음 켤 때 한 번, 그리고 여기서 언제든 다시 볼 수 있어요.", fontSize = 12.sp, color = PSub)
        Text("아이에게: 첫 이야기에서 마스코트가 스스로 말해요 — \"나는 진짜 병아리가 아니라 이야기 친구야.\"", fontSize = 12.sp, color = PSub)
        Text("ⓘ 문구와 시점은 아직 정하는 중이에요.", fontSize = 11.sp, color = PSub)
    }

    Section("누가 한 말인지 구분해서 보여 줘요")
    PCard(Modifier.fillMaxWidth()) {
        listOf(
            Triple("🧒", "아이가 한 말", "따옴표 · 굵게 — 기록과 책에 인용되는 건 이것뿐"),
            Triple("🧑", "어른이 넣은 질문", "「어른이 넣어 둔 질문에 한 답」에 작은 글씨로"),
            Triple("🐥", "마스코트가 대신 정한 것", "\"(마스코트가 대신 정했어요)\" 라고 적고, 횟수·인용에서는 빼요"),
        ).forEach { (e, who, how) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(e, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(who, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text(how, fontSize = 12.sp, color = PSub)
                }
            }
        }
    }

    Section("데이터")
    PCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("기록 · 그림은 이 폰에만 있어요", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
            Box(Modifier.alpha(0.5f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3EDE3)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text("내보내기", fontSize = 13.sp, color = Ink) }
            Box(Modifier.alpha(0.5f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFE7DD)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text("모두 지우기", fontSize = 13.sp, color = PAccent) }
        }
        Text("데모에서는 누를 수 없어요", fontSize = 11.sp, color = PSub)
    }
}

@Composable
private fun Stepper(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFF3EDE3)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold) }
}

/** 비밀번호 4자리 — 부모 모드에 들어갈 때 · (설정하면) 이야기를 시작할 때 */
@Composable
fun PinView(d: Director, stage: Stage.Pin) = ParentText { PinViewBody(d, stage) }

@Composable
private fun PinViewBody(d: Director, stage: Stage.Pin) {
    // 09-29 사용자 — 「비밀번호를 태어난 해로 하지 말고 직접 설정하게」. 처음 설정에서 정한 **부모 비밀번호 네 자리**로 연다.
    //   잊었으면 → 보호자 태어난 해 확인 → 새 비밀번호. 정한 적이 없으면(처음 설정 전에 설치한 폰) 여기서 먼저 정한다.
    // 흐름(`Director.pinGate`)은 그대로 둔다: 통과하면 「통과」, 닫기는 「취소」 신호를 보낸다
    val start = stage.purpose == "start"
    var mode by remember { mutableStateOf(if (com.example.finalproject_demo.ui.shell.Shell.hasPin) "check" else "create") }
    fun pass() = d.send(Reply.Tapped("pin:ok", "통과"))
    com.example.finalproject_demo.ui.shell.ObFrame(
        step = null,
        title = when (mode) { "forgot" -> "비밀번호를 잊었어요"; "create" -> "부모 비밀번호 정하기"; else -> if (start) "어른 확인" else "부모 영역" },
        sub = when (mode) {
            "forgot" -> "보호자님이 태어난 해를 확인한 뒤 새 비밀번호를 정해요."
            "create" -> "아직 정한 비밀번호가 없어요. 부모 영역을 열 네 자리를 정해 주세요."
            else -> if (start) "이야기를 시작하려면 부모 비밀번호를 넣어 주세요." else "처음 설정에서 정한 부모 비밀번호를 넣어 주세요."
        },
        onBack = { if (mode == "forgot") mode = "check" else d.send(Reply.Tapped("pin:cancel", "취소")) },
        art = {
            AssetImage("pi_lock", Modifier.size(170.dp)) { Box(Modifier.size(110.dp).felt(FeltTeal, CircleShape), contentAlignment = Alignment.Center) { Text("🔒", fontSize = 48.sp) } }
        },
    ) {
        when (mode) {
            "check" -> {
                com.example.finalproject_demo.ui.shell.PinPad(
                    "부모 비밀번호", "3번 틀리면 30초 기다려요",
                    onDone = { com.example.finalproject_demo.ui.shell.Shell.checkPin(it).also { ok -> if (ok) pass() } },
                )
                Spacer(Modifier.height(4.dp))
                Text("비밀번호를 잊었어요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PAccent,
                    modifier = Modifier.clickable { mode = "forgot" }.padding(vertical = 8.dp))
            }
            "forgot" -> com.example.finalproject_demo.ui.shell.YearPad(onPass = { mode = "create" }, note = "3번 틀리면 30초 기다려요")
            else -> com.example.finalproject_demo.ui.shell.PinCreate(onSet = { com.example.finalproject_demo.ui.shell.Shell.setPin(it); pass() })
        }
    }
}

/**
 * 책장 정리 — 모드마다 12권. 꽉 차면 아이가 방에서 그 모드를 고를 때 알림이 뜨고, 어른이 여기서 한 권을 뺀다 (10-02 조장 #80).
 * 빼기는 이 화면에서만 — 아이 말 · 그림 · 녹음이 같이 사라지는 일이라 「정말 뺄까요?」를 한 번 더 묻는다
 */
@Composable
private fun ShelfTidyTab(d: Director) {
    val s = d.s
    val mode = s.shelfTidyMode ?: StoryMode.STORY
    // 책을 빼면 목록을 다시 읽는다 — 저장소 목록은 Compose 상태가 아니라서 책장(`s.shelf`) 수로 다시 그린다
    val shelfSize = s.shelf.size
    val books = remember(mode, shelfSize) { d.shelfEntries(mode) }
    var asking by remember(mode) { mutableStateOf<ShelfEntry?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SHELF_MODES.forEach { (m, name) ->
            val n = remember(m, shelfSize) { d.shelfCount(m) }?.toString() ?: "?"
            CoopChip("$name $n/$SHELF_CAPACITY", on = m == mode) {
                d.send(Reply.Tapped("shelf:mode:${m.name}", name))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (books.size >= SHELF_CAPACITY) PCard(Modifier.fillMaxWidth()) {
        Text("책장이 꽉 찼어요", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("한 권을 빼면 아이가 이 모드로 새 책을 만들 수 있어요. 빼지 않으면 지금 책은 모두 그대로 남아요.", fontSize = 13.sp, color = PSub)
    }
    if (books.isEmpty()) PCard(Modifier.fillMaxWidth()) {
        Text("아직 꽂힌 책이 없어요", fontSize = 15.sp, color = PSub)
    }
    books.forEach { b ->
        Spacer(Modifier.height(8.dp))
        PCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(listOfNotNull(b.madeAt.takeIf(String::isNotBlank), "${b.pages}쪽").joinToString(" · "), fontSize = 12.sp, color = PSub)
                }
                if (asking != b) PButton("빼기", PAccent, Modifier.width(96.dp), outline = true) { asking = b }
            }
            // 「정말 뺄까요?」는 누른 책 카드 안에서 — 목록 맨 아래에 두면 12권 아래로 밀려 보이지 않는다
            if (asking == b) {
                Spacer(Modifier.height(10.dp))
                Text("이 책을 뺄까요? 아이 말 · 그림 · 녹음이 폰에서 지워지고 되돌릴 수 없어요.", fontSize = 13.sp, color = Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("그대로 두기", PSub, Modifier.width(140.dp), outline = true) { asking = null }
                    PButton("네, 뺄게요", PAccent, Modifier.width(140.dp)) {
                        asking = null
                        d.send(Reply.Tapped(shelfDeleteSignal(b.mode, b.id), "빼기"))
                    }
                }
            }
        }
    }
}

/**
 * 부모 리포트 「만들기」 축 — 아이가 오늘 직접 만든 것.
 *
 * 그림일기는 화이트보드 그림을 `sceneDrawing` · 오늘 판(`diaryDay.pieces`)으로 옮기고 `drawing` 을 비운다(`keepSceneDrawing`).
 * 전에는 `drawing` 만 봐서 그림을 그린 그림일기 아이에게도 「오늘은 프리셋을 골랐어요」가 나왔다 (#65 2번).
 */
internal fun reportMade(s: DemoState): List<String> = buildList {
    // isDiary 는 협업에서도 참이다(일기 질문을 같이 쓴다) — 그림일기 뒤 협업 리포트에 남은 diaryDay 로 「직접 그렸어요」가 거짓으로 떴다(#129 리뷰)
    if (s.mode == StoryMode.DIARY && (s.sceneDrawing.isNotEmpty() || s.diaryDay.pieces.isNotEmpty())) add("오늘 있었던 일을 직접 그렸어요")
    // 일기 모드의 그리기 걸음(오늘 만난 사람 그리기)을 남겨 둔 이유 — 없으면 이 축이 빈칸으로 나온다 (일기 설계 §8 · 9/21)
    if (s.drawing.isNotEmpty()) add("${s.friendCallName}${eul(s.friendCallName)} 직접 그렸어요")
    if (s.sound?.contains("원본") == true) add("${s.dino.name} 소리를 냈어요")
}
