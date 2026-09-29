package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.example.finalproject_demo.demo.coopAsked
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
    PTab("coop", "pi_coop", "🤝", "협업 질문", "「같이 만들기」에서 어른이 아이에게 물을 질문을 미리 넣어 둬요"),
    PTab("ach", "pi_achieve", "🏅", "업적", "아이가 한 일로만 받는 선물과 해결 방법 도감"),
    PTab("set", "pi_settings", "⚙️", "설정", "하루 한도 · 시작할 때 확인 · 그림체 · 소리 · 동의"),
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
            Spacer(Modifier.weight(1f))
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
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 22.dp)
            ) {
                when (cur.key) {
                    "coop" -> CoopQuestionsTab(d)
                    "ach" -> AchievementsTab(d)
                    "set" -> SettingsTab(d)
                    "acct" -> com.example.finalproject_demo.ui.shell.AccountTab(d)
                    else -> RecordTab(d)
                }
            }
        }
    }
}

/**
 * 오늘의 기록 — 6축 · 말한 방식 · 같은 질문에 한 답 · 누리과정 · 질문 카드 · 고지. 등급 · 비교 · 수준 이름은 없다 (16).
 * 세 모드가 다 여기로 온다 — 문구는 그 모드가 실제로 물은 것만 쓴다 (일기 · 협업은 함께할 사람을 안 묻는다 · 9/22).
 */
@Composable
private fun RecordTab(d: Director) {
    val s = d.s
    if (s.title == null && s.quotes.isEmpty()) {
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
    PCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp, 54.dp).clip(RoundedCornerShape(12.dp))) {
                AssetImage(s.bgName, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Sun2)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("『${s.title ?: "만드는 중"}』", fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold)
                // 일기 · 협업 모드는 "누구랑 같이 만들래?"를 묻지 않는다 →
                // 물어보지 않은 사람 이름을 지어내지 않는다. 협업은 어른이 질문을 넣어 둔 것이 확실하므로 그렇게만 적는다
                val who = when {
                    s.isCoop -> "어른 질문으로 · "
                    s.isDiary -> ""
                    else -> "${s.pn}${wa(s.pn)} 함께 · "
                }
                // 걸린 시간은 잰 것만 적는다. 동화 모드는 시작 시각을 남기지 않아 쓸 수 없다 — 없는 숫자를 만들지 않는다 (9/22)
                val minutes = if (s.isDiary && s.diaryStart > 0L) ((System.currentTimeMillis() - s.diaryStart) / 60_000L).coerceAtLeast(1L) else null
                val took = minutes?.let { "약 ${it}분 · " } ?: ""
                Text("오늘 · $took$who${s.placeName} · ${s.pageCount}쪽", fontSize = 12.sp, color = PSub)
                // 일기 · 협업으로 만든 책은 부모 화면에서만 그렇게 보인다 (아이 화면에는 이 말이 없다 · 일기 §0)
                if (s.isCoop) Text("오늘 있었던 일로 · 어른이 넣어 둔 질문으로 지은 책이에요", fontSize = 12.sp, color = PAccent)
                else if (s.isDiary) Text("오늘 있었던 일로 만든 책이에요", fontSize = 12.sp, color = PAccent)
            }
            Chip("${s.modeVoice}번 말했어요", PMint)
        }
    }

    val s1 = s.signals.filter { it.startsWith("S1") }.map { it.substringAfter("\"").substringBefore("\"") }
    val s2 = s.signals.filter { it.startsWith("S2") }.map { it.substringAfter("\"").substringBefore("\"") }
    val made = buildList {
        // 일기 모드의 그리기 걸음(오늘 만난 사람 그리기)을 남겨 둔 이유 — 없으면 이 축이 빈칸으로 나온다 (일기 설계 §8 · 9/21)
        if (s.drawing.isNotEmpty()) add("${s.friendCallName}${eul(s.friendCallName)} 직접 그렸어요")
        if (s.sound?.contains("원본") == true) add("${s.dino.name} 소리를 냈어요")
    }
    val reasonQuote = s1.firstOrNull()
    val fillQuote = s2.firstOrNull { it != reasonQuote } ?: s2.firstOrNull()
    val talkQuote = s.quotes.firstOrNull { it != reasonQuote && it != fillQuote } ?: s.quotes.firstOrNull()
    data class Axis(val emoji: String, val name: String, val n: Int, val what: String, val quote: String?)
    val axes = listOf(
        // 카드마다 다른 말을 보여 준다 (같은 문장이 되풀이되지 않게)
        Axis("🗣", "말하기", s.modeVoice, "마이크로 ${s.modeVoice}번 말했어요", talkQuote),
        Axis("💡", "이유 말하기", s.s1count, if (s.s1count == 0) "오늘은 까닭을 말하지 않았어요" else "까닭을 ${s.s1count}번 말했어요", reasonQuote),
        Axis("💗", "마음 말하기", s.feelings.size, if (s.feelings.isEmpty()) "오늘은 마음을 말하지 않았어요" else "${s.feelings.distinct().joinToString(", ") { "${it}던" }} 마음을 말했어요", null),
        Axis("🧩", "이야기 채우기", s2.size, if (s2.isEmpty()) "물어본 것에 답했어요" else "묻지 않은 것을 ${s2.size}번 덧붙였어요", fillQuote),
        Axis("🖍", "만들기", made.size, made.joinToString(" · ").ifEmpty { "오늘은 프리셋을 골랐어요" }, null),
        Axis(
            "🤝", "함께하기", s.partnerTurns,
            when {
                // 협업 모드는 어른이 넣어 둔 질문으로 아이에게 묻는다 — 이 축이 처음으로 제대로 찬다 (협업 §4-2 · guidelines/9 §9-5)
                s.isCoop -> "어른이 넣어 둔 질문 ${s.partnerTurns}개로 이야기했어요"
                // 일기 모드는 함께할 사람을 묻지 않았다. 없는 사람 이름을 지어내지 않는다
                s.isDiary -> if (s.companionKind.isBlank()) "오늘은 마스코트와 주고받았어요" else "오늘 ${s.companionKind}${wa(s.companionKind)} 있었던 이야기예요"
                else -> "${s.pn}${wa(s.pn)} ${s.partnerTurns}번 주고받았어요"
            },
            if (s.isCoop) s.adultLine else s.partnerHelp,
        ),
    )
    Section("오늘 ${s.childName}${ga(s.childName)} 한 것", "●●● · ●●○ · ●○○ 는 오늘 어디까지 해 봤는지일 뿐, 점수가 아니에요")
    axes.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                PCard(Modifier.weight(1f).height(96.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(a.name, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Dots(if (a.n >= 3) 3 else if (a.n >= 1) 2 else 1)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(a.what, fontSize = 13.sp, color = Ink, maxLines = 2)
                    a.quote?.let { Text("\"$it\"", fontSize = 12.sp, color = PSub, maxLines = 1) }
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PCard(Modifier.weight(1f)) {
            Text("말한 방식", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val parts = listOf(
                Triple("말로", s.modeVoice, PAccent),
                Triple("카드로", s.modeCard, Sun),
                Triple("그림으로", s.modeDraw, PMint),
                Triple("말 없이", s.modeSilent, Color(0xFFBDB1A2)),
            )
            val total = parts.sumOf { it.second }.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(84.dp)) {
                    var start = -90f
                    val sw = size.minDimension * 0.22f
                    val inset = sw / 2
                    parts.forEach { (_, n, c) ->
                        val sweep = 360f * n / total
                        if (sweep > 0) drawArc(c, start, sweep, useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        start += sweep
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    parts.forEach { (t, n, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(6.dp))
                            Text("$t ${n}번", fontSize = 13.sp, color = Ink)
                        }
                    }
                }
            }
        }
        PCard(Modifier.weight(1f)) {
            Text("같은 질문에 한 답", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            // 일기 · 협업 모드의 기준 질문 ①은 "오늘 어디 갔었어?" 다 — 같은 자리, 다른 재료 (일기 설계 §0 · §4-1)
            Text(if (s.isDiary) "\"오늘 어디 갔었어?\"" else "\"어디로 가 볼까?\"", fontSize = 13.sp, color = PSub)
            Spacer(Modifier.height(8.dp))
            // ⚠️ "지난번" 줄은 뺐다 (9/22). 전에는 "\"바다\" 한 낱말" 같은 **글자 상수**를 지난번 답인 것처럼 보여 줬는데,
            //    앱은 아직 아무것도 저장하지 않는다 — 지난번 기록이 없다. 없는 숫자를 진짜인 척하지 않는다 (guidelines/9 §9-5).
            //    저장이 붙으면 이 자리에 지난 답들이 날짜와 함께 나란히 선다 (README 「부모 리포트」 예).
            Row {
                Text("오늘", fontSize = 12.sp, color = PSub, modifier = Modifier.width(44.dp))
                Text(s.quotes.firstOrNull()?.let { "\"$it\"" } ?: "\"${s.place ?: "-"}\" (카드로 고름)", fontSize = 12.sp, color = Ink, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Text("지난 기록은 아직 없어요. 책이 쌓이면 같은 질문에 한 답이 날짜별로 나란히 보여요.", fontSize = 11.sp, color = PSub)
            Text("다른 아이가 아니라 ${s.childName}${ga(s.childName)} 지난번의 ${s.childName}${wa3(s.childName)}만 견줘요", fontSize = 11.sp, color = PSub)
        }
    }

    // 협업 모드의 결과물 — **부모가 궁금해한 것에 아이가 뭐라고 했나** (부모협업모드_설계 §0 · 구현설계 §2-3).
    // 인용은 아이가 말한 것(`by: child`)만 따옴표로. 카드 · 마스코트가 채운 것은 그렇다고 적는다 (guidelines/2 §1-4)
    if (s.isCoop && s.coopAsked.isNotEmpty()) {
        Section("어른이 넣어 둔 질문에 한 답", "넣은 순서대로 · 아이가 말한 것만 따옴표")
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

    // 부모 협업 모드가 파는 것 — 동화책이 아니라 **질문하는 법**이다 (협업 §7).
    // ⚠️ 점수를 보여 주지 않는다. "당신의 질문은 60점"은 앱을 지우게 만든다. 남기는 형태는 다음에 넣어 볼 질문 한 개다.
    // 9/22 — 협업은 **부모가 질문을 미리 넣어 두는 모드**가 됐다 (guidelines/9 §9-5). 부모가 옆에서 기다린다는 전제의
    //    문구("아이가 막히면 재촉하지 말고 기다려 주세요")는 뺐다. 넣어 둔 질문을 규칙으로 살펴 주는 일은 아직 없다 —
    //    그래서 지금은 예시 한 개만 보여 준다.
    if (s.isCoop) {
        Section("다음에 넣어 볼 질문", "부모 협업 모드에서만 · 점수가 아니라 질문 한 개예요")
        PCard(Modifier.fillMaxWidth()) {
            Text("“오늘 제일 재밌었던 거 하나만 말해 줄래?”", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "“재밌었어?” 처럼 예/아니오로 끝나는 질문 대신 이렇게 넣어 보세요. " +
                    "의문사는 하나만, 선택지는 질문보다 먼저.",
                fontSize = 13.sp, color = PSub,
            )
            Spacer(Modifier.height(4.dp))
            Text("ⓘ 부모 질문에 점수를 매기지 않습니다. 이 칸은 다음에 넣어 볼 질문 한 개만 알려 드려요.", fontSize = 11.sp, color = PSub)
        }
    }

    Section("누리과정으로 보면")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("의사소통 · 말하기", PAccent)
        Chip("듣기와 말하기", Sun)
        Chip("예술경험 · 창의적으로 표현하기", PMint)
    }

    Section("오늘 이야기로 해 볼 놀이", "질문 카드 3장")
    val f = s.friendName.takeUnless { it.startsWith("{") } ?: "새 친구"
    // 일기 모드의 질문 카드는 오늘 있었던 일에서 나온다 — 공룡 · 소리 칸은 묻지 않았다 (일기 설계 §2-2).
    // ⚠️ 아이가 아무도 말하지 않은 날에는 "새 친구" 질문을 넣지 않는다 — 없는 친구를 앱이 만들어 내면 안 된다 (§3-2)
    // ⚠️ 일기 · 협업은 "누구랑 같이 만들래?"를 묻지 않았다 — `s.pn` 은 기본값 "엄마"라 질문 카드에 쓰면 없는 사람이 생긴다 (9/22)
    val playCards = if (s.isDiary) listOfNotNull(
        s.friendName.takeUnless { it.startsWith("{") }?.let { n -> "\"${n}${eun(n)} 내일은 뭐 하고 놀까?\"" },
        "\"오늘 ${s.placeName}에서 제일 재밌었던 게 뭐였어?\"",
        "\"내일 ${s.placeName}에 가면 뭐 하고 싶어?\"",
        "\"오늘 있었던 일을 하나만 더 이야기해 줄래?\"",
    ).take(3) else listOf(
        "\"${f}${eun(f)} 오늘 뭐 하고 놀까?\"",
        "\"${s.dino.name}${ga(s.dino.name)} 또 울면 어떻게 할까?\"",
        "\"${s.placeName}에 또 가면 누구를 만날까?\"",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        playCards.forEachIndexed { i, q ->
            Column(
                Modifier
                    .weight(1f)
                    .height(86.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(listOf(Color(0xFFFFE7DD), Color(0xFFFFF1CC), Color(0xFFDDF2EA))[i])
                    .padding(12.dp)
            ) {
                Text("질문 ${i + 1}", fontSize = 11.sp, color = PSub)
                Text(q, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3EDE3)).padding(12.dp)) {
        Column {
            listOf(
                "ⓘ 놀이 대화 요약이며 언어발달 평가 · 진단이 아닙니다.",
                "ⓘ 아이 목소리와 그림은 폰 안에만 있고, 이름은 가린 뒤에야 밖으로 나갑니다.",
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

/** 질문 자리 — 앞 네 줄은 기승전결(일기 §2-1), 그 뒤는 자유. 부모에게는 「기승전결」 대신 묻는 말로 보여 준다 */
private val COOP_PARTS = listOf("어디", "무슨 일", "왜", "어떻게 됐나")
private const val COOP_MAX = 6          // 6쪽 책에 장면이 6개다 (구현설계 §1-③)

/** 추천 — 지금은 대본이다. 나중에 LLM이 "찬 칸을 보고" 추천한다 (guidelines/9 §9-5 "추천하는 정도로만") */
private val COOP_SUGGESTIONS = listOf(
    "오늘 어디 갔었어?",
    "거기서 무슨 일이 있었어?",
    "왜 그랬을까?",
    "그래서 어떻게 됐어?",
    "오늘 제일 재밌었던 게 뭐였어?",
    "누구랑 같이 있었어?",
)

/**
 * 협업 질문 — **부모가 이야기 전에 질문을 적어 두는 곳** (구현설계 §2-1 · 부모협업모드_설계 §0 9/22 개정).
 * 이 화면이 협업 모드의 절반이다 — 나머지 절반은 마스코트가 이 질문을 아이에게 읽어 주는 것(CoopScenes).
 *
 * - 자리는 지금 **위치**로 정한다 (0~3 = 어디·무슨 일·왜·어떻게, 4~ = 자유). 홀더가 `List<String>` 이라서다.
 *   `ParentQuestion(part, text)` 로 바뀌면(조장 요청) 이 위치 규칙은 필드로 옮긴다
 * - 글자만 받는다. 음성 입력은 안 한다 (구현설계 §1-⑤)
 * - 귀띔은 [questionHint] — 막지도 점수를 매기지도 않는다 (§1-⑥ · 설계 §7)
 * - 빈 줄은 그대로 둔다. 읽는 쪽(CoopScenes)이 빈 줄을 건너뛴다
 */
@Composable
private fun CoopQuestionsTab(d: Director) {
    val s = d.s
    val qs = s.parentQuestions

    fun set(i: Int, text: String) {
        while (qs.size <= i) qs.add("")
        qs[i] = text
    }

    PCard(Modifier.fillMaxWidth()) {
        Text("오늘 아이에게 물어볼 것을 적어 두세요", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text("[같이 만들기]를 시작하면 마스코트가 이 순서대로 대신 물어봐요. 오늘 이야기에만 쓰여요.", fontSize = 13.sp, color = PSub)
    }

    CoopTemplateCards(qs)

    Section("질문", "네 자리를 먼저 채우고, 더 있으면 [＋]")
    val rows = maxOf(COOP_PARTS.size, qs.size)
    for (i in 0 until rows) {
        val text = qs.getOrNull(i) ?: ""
        val label = COOP_PARTS.getOrNull(i) ?: "자유"
        PCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = PSub, modifier = Modifier.width(72.dp))
                TextField(
                    value = text,
                    onValueChange = { set(i, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("예: ${COOP_SUGGESTIONS.getOrElse(i) { COOP_SUGGESTIONS.last() }}", fontSize = 14.sp, color = PSub.copy(alpha = 0.6f)) },
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

    Section("이런 질문은 어때요", "탭하면 빈 자리에 들어가요")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            COOP_SUGGESTIONS.forEach { q ->
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
                "ⓘ 질문이 모자라면 마스코트가 이야기에 맞춰 이어서 물어봐요.",
                "ⓘ 아이 말은 마이크로 받고, 이름은 가린 뒤에야 밖으로 나가요.",
            ).forEach { Text(it, fontSize = 11.sp, color = PSub) }
        }
    }
}

/**
 * 템플릿 카드 넷 — 탭하면 네 자리가 한 번에 채워지고, 빈칸(장소·호칭) 하나만 고친다 ([COOP_TEMPLATES]).
 * 채우기만 한다 — 홀더(`parentQuestions`)와 마스코트가 읽는 흐름(CoopScenes)은 그대로다.
 * 채운 뒤에도 아래 입력 줄은 그대로 열려 있어 줄 단위로 고칠 수 있다.
 */
@Composable
private fun CoopTemplateCards(qs: MutableList<String>) {
    var picked by remember { mutableStateOf<CoopTemplate?>(null) }
    var value by remember { mutableStateOf("") }

    fun replace(lines: List<String>) {
        qs.clear(); qs.addAll(lines)
    }

    fun change(t: CoopTemplate, v: String) {
        replace(refillBlank(qs.toList(), t.questions(value), t.questions(v)))
        value = v
    }

    Section("템플릿으로 시작하기", "탭하면 네 자리가 한 번에 채워져요")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COOP_TEMPLATES.forEach { t ->
            val on = picked?.key == t.key
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Color(0xFFFFF1CC) else Color.White)
                    .border(1.dp, if (on) PAccent else PLine, RoundedCornerShape(14.dp))
                    .clickable {
                        picked = t
                        value = t.blank?.default ?: ""
                        replace(fillFromTemplate(qs.toList(), t.questions()))
                    }
                    .padding(10.dp),
            ) {
                Text(t.emoji, fontSize = 20.sp)
                Text(t.label(if (on) value else null), fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(t.questions(if (on) value else null).first(), fontSize = 11.sp, color = PSub, maxLines = 2)
            }
        }
    }

    val t = picked
    val b = t?.blank
    if (t != null && b != null) {
        Spacer(Modifier.height(8.dp))
        PCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.label} 바꾸기", fontSize = 13.sp, color = PSub, modifier = Modifier.width(72.dp))
                b.choices.forEach { c ->
                    val on = value.trim() == c
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) PAccent else Color(0xFFF3EDE3))
                            .clickable { change(t, c) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(c, fontSize = 13.sp, color = if (on) Color.White else Ink) }
                }
                if (b.free) {
                    TextField(
                        value = value,
                        onValueChange = { change(t, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("직접 쓰기", fontSize = 13.sp, color = PSub.copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = PBg, unfocusedContainerColor = PBg,
                            focusedIndicatorColor = PAccent, unfocusedIndicatorColor = PLine,
                        ),
                    )
                }
            }
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
    Section("받은 선물", "아이가 한 일로만 받아요 · 많이 말한 것 · 빨리 한 것에는 주지 않아요")
    val items = s.achievements.filterNot { it.startsWith("해결 방법 도감") }
    if (items.isEmpty()) {
        PCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetImage("pi_achieve", Modifier.size(56.dp).alpha(0.45f)) { Text("🏅", fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("아직 받은 선물이 없어요", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("미션을 스스로 해내거나 직접 그림을 그리면 받아요", fontSize = 12.sp, color = PSub)
                }
            }
        }
    }
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                PCard(Modifier.weight(1f)) {
                    AssetImage(if (a.contains("크레용")) "gift_crayon" else "pi_achieve", Modifier.size(44.dp)) { Text(if (a.contains("크레용")) "🌈" else "🏅", fontSize = 22.sp) }
                    Text(a, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
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
@Composable
private fun SettingsTab(d: Director) {
    val s = d.s
    // 정책이 요구하는 세 자리 — 내용은 `ui/Consent.kt` 에 있다 (치영 · 9/23).
    // 여기는 부르는 줄만 둔다: 소리와 진동(09-25) · 신고 · AI 음성 고지 · 보호자 동의 철회
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

    Section("그림체", "다음 책부터 세계 그림에 적용 · 아이 그림 · 도감 주인공은 그대로")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ART_STYLES.forEach { st ->
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
                if (!st.ready) Text("데모 그림 준비 중", fontSize = 10.sp, color = PSub)
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
            "글자로 바뀐 말" to "이름은 폰에서 가린 뒤에야 서버로 가요 · 이 폰에 기록으로 남아요",
            "아이 그림" to "이 폰에만 · AI가 다시 그리지 않아요",
            "어른이 넣어 둔 질문" to "그 이야기 한 번에만 쓰고 지워요",
        ).forEach { (what, how) ->
            Text(what, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(how, fontSize = 12.sp, color = PSub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("ⓘ 지금 데모는 아무것도 저장하지 않아요. 앱을 끄면 다 사라져요.", fontSize = 11.sp, color = PSub)
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
