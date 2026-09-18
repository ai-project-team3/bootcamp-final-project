package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
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
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.bat
import com.example.finalproject_demo.demo.eul
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.wa
import com.example.finalproject_demo.demo.eun
import com.example.finalproject_demo.demo.ga

private val PBg = Color(0xFFFBF4E8)
private val PCard = Color.White
private val PLine = Color(0xFFEDE2CF)
private val PSub = Color(0xFF8A7B6A)
private val PAccent = Color(0xFFE8704F)
private val PMint = Color(0xFF7CC4A8)

@Composable
private fun PCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .shadow(3.dp, RoundedCornerShape(18.dp), ambientColor = Ink.copy(alpha = 0.12f), spotColor = Ink.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(18.dp))
            .background(PCard)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun Section(text: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
        Text(text, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
        if (sub != null) {
            Spacer(Modifier.width(8.dp))
            Text(sub, fontSize = 12.sp, color = PSub)
        }
    }
}

/** 부모 모드 — 왼쪽 메뉴 · 오른쪽 카드. 아래 마스코트 · 버튼은 없다 (v0.8) */
@Composable
fun ParentView(d: Director, tab: String) {
    val s = d.s
    Row(Modifier.fillMaxSize().background(PBg)) {
        // ── 왼쪽 메뉴
        Column(
            Modifier
                .width(178.dp)
                .fillMaxHeight()
                .background(Color(0xFFF3E7D2))
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White)) { HeroImage(s.heroAttr ?: s.heroes.first().attr, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("${s.childName}네 기록", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("부모만 보는 화면", fontSize = 11.sp, color = PSub)
                }
            }
            Spacer(Modifier.height(16.dp))
            listOf(Triple("rec", "📋", "오늘의 기록"), Triple("ach", "🏅", "업적"), Triple("set", "⚙️", "설정")).forEach { (k, e, t) ->
                val on = tab == k
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { d.send(Reply.Tapped("tab:$k", t)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e, fontSize = 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(t, fontSize = 15.sp, color = if (on) Ink else PSub, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    if (on) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(PAccent))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sun)
                    .clickable { d.send(Reply.Tapped("home", "처음으로")) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("🏠 아이 모드로", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold) }
        }
        // ── 오른쪽 내용
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // 고정 머리 — 화면 이름은 여기 (내용이 스크롤돼도 겹치지 않게)
            Row(
                Modifier.fillMaxWidth().background(PBg).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("부모 모드", fontSize = 13.sp, color = PSub)
                Text("  ›  ", fontSize = 13.sp, color = PSub)
                Text(when (tab) { "ach" -> "업적"; "set" -> "설정"; else -> "오늘의 기록" }, fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(PLine))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp)
            ) {
                when (tab) {
                    "ach" -> AchievementsTab(d)
                    "set" -> SettingsTab(d)
                    else -> RecordTab(d)
                }
            }
        }
    }
}

/** 오늘의 기록 — 6축 · 말한 방식 · 본인 대비 · 누리과정 · 질문 카드 · 고지. 등급 · 비교 · 수준 이름은 없다 (16) */
@Composable
private fun RecordTab(d: Director) {
    val s = d.s
    if (s.title == null && s.quotes.isEmpty()) {
        PCard(Modifier.fillMaxWidth()) {
            Text("오늘은 아직 만든 책이 없어요", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("아이와 이야기를 하나 만들면 여기에 기록이 생겨요.", fontSize = 13.sp, color = PSub)
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
                Text("오늘 · 약 15분 · ${s.pn}${wa(s.pn)} 함께 · ${s.placeName} · ${s.pageCount}쪽", fontSize = 12.sp, color = PSub)
            }
            Chip("${s.modeVoice}번 말했어요", PMint)
        }
    }

    val s1 = s.signals.filter { it.startsWith("S1") }.map { it.substringAfter("\"").substringBefore("\"") }
    val s2 = s.signals.filter { it.startsWith("S2") }.map { it.substringAfter("\"").substringBefore("\"") }
    val made = buildList {
        if (s.drawing.isNotEmpty()) add("${s.friendName}${eul(s.friendName)} 직접 그렸어요")
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
        Axis("🤝", "함께하기", s.partnerTurns, "${s.pn}${wa(s.pn)} ${s.partnerTurns}번 주고받았어요", s.partnerHelp),
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
            Text("지난번과 같은 질문", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("\"어디로 가 볼까?\"", fontSize = 13.sp, color = PSub)
            Spacer(Modifier.height(8.dp))
            Compare("지난번", "\"바다\" 한 낱말", 0.35f)
            Spacer(Modifier.height(6.dp))
            Compare("오늘", s.quotes.firstOrNull()?.let { "\"$it\"" } ?: "\"${s.place ?: "-"}\" (카드로 고름)", if (s.quotes.isNotEmpty()) 0.8f else 0.4f)
            Spacer(Modifier.height(6.dp))
            Text("다른 아이가 아니라 ${s.childName}${ga(s.childName)} 지난번의 ${s.childName}${wa3(s.childName)}만 견줘요", fontSize = 11.sp, color = PSub)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "\"${f}${eun(f)} 오늘 뭐 하고 놀까?\"",
            "\"${s.dino.name}${ga(s.dino.name)} 또 울면 어떻게 할까?\"",
            "\"${s.placeName}에 또 가면 누구를 만날까?\"",
        ).forEachIndexed { i, q ->
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

@Composable
private fun Compare(label: String, text: String, frac: Float) {
    Column {
        Row {
            Text(label, fontSize = 12.sp, color = PSub, modifier = Modifier.width(44.dp))
            Text(text, fontSize = 12.sp, color = Ink, maxLines = 1)
        }
        Box(Modifier.padding(start = 44.dp, top = 3.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PLine)) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(PMint))
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
                    Text(if (got) "🧩" else "❔", fontSize = 22.sp)
                    Text(t, fontSize = 12.sp, color = if (got) Ink else PSub)
                }
            }
        }
    }
    Section("받은 선물", "아이가 한 일로만 받아요 · 많이 말한 것 · 빨리 한 것에는 주지 않아요")
    val items = s.achievements.filterNot { it.startsWith("해결 방법 도감") }
    if (items.isEmpty()) {
        Text("아직 없어요", fontSize = 13.sp, color = PSub)
    }
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                PCard(Modifier.weight(1f)) {
                    Text(if (a.contains("크레용")) "🌈" else "🏅", fontSize = 22.sp)
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
                    .shadow(if (on) 6.dp else 2.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
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
fun PinView(d: Director, stage: Stage.Pin) {
    Row(
        Modifier.fillMaxSize().background(PBg).padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 40.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (stage.purpose == "start") "어른 확인" else "부모 확인", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(
                if (stage.purpose == "start") "이야기를 시작하려면 어른이 비밀번호를 넣어 주세요" else "비밀번호 4자리를 넣어 주세요",
                fontSize = 13.sp, color = PSub, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < stage.typed) PAccent else PLine))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("데모: 아무 숫자 4개", fontSize = 11.sp, color = PSub)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("취소", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        val v = when (k) { "취소" -> "pin:cancel"; "⌫" -> "pin:back"; else -> "pin:$k" }
                        Box(
                            Modifier
                                .size(width = 72.dp, height = 50.dp)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (k.length == 1) Color.White else Color(0xFFF3EDE3))
                                .clickable { d.send(Reply.Tapped(v, k)) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, fontSize = if (k.length == 1) 22.sp else 15.sp, color = Ink, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
