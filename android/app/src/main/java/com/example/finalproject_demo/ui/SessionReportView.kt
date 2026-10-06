package com.example.finalproject_demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.HOME_TIP
import com.example.finalproject_demo.demo.MOMENT_ADD
import com.example.finalproject_demo.demo.MOMENT_FEEL
import com.example.finalproject_demo.demo.MOMENT_MEANING
import com.example.finalproject_demo.demo.MOMENT_REASON
import com.example.finalproject_demo.demo.SessionReport
import com.example.finalproject_demo.demo.TalkLine
import com.example.finalproject_demo.demo.ga
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * 부모 리포트 「오늘의 기록」 — 10-06 종훈과 고친 시안(report-canvas Main · Transcript · QuietDay)대로.
 * 출처 색: 아이가 한 말 = 코랄 바탕 · 카드로 고름 = 회갈색 · 오또가 채움 = 흰 바탕 점선(규칙 5).
 * 점수 · 등급 · 비교 없음, 0 인 숫자 칸은 보이지 않는다(말이 적은 날을 모자란 날로 보이게 하지 않는다 · 규칙 9).
 */

private val RInk = Color(0xFF4A3428)
private val RSub = Color(0xFF7A6455)
private val RLine = Color(0xFFF1E8DA)
private val RAccent = Color(0xFFE8836B)
private val RTeal = Color(0xFF2F7F79)
private val ChildBg = Color(0xFFFCEDE8)
private val ChildFg = Color(0xFFA4533F)
private val CardBg = Color(0xFFEFEBE6)
private val CardFg = Color(0xFF7A6455)
private val OttoFg = Color(0xFF9A8B80)
private val OttoLine = Color(0xFFCDBFAE)

private val MOMENT_COLORS = mapOf(
    MOMENT_REASON to (Color(0xFFEAF4F2) to RTeal),
    MOMENT_ADD to (Color(0xFFF3EDF9) to Color(0xFF6B4FA5)),
    MOMENT_FEEL to (ChildBg to ChildFg),
)

private fun dayLine(r: SessionReport): String {
    val d = runCatching { LocalDate.parse(r.day) }.getOrNull()
    val date = d?.let { "${it.monthValue}월 ${it.dayOfMonth}일 ${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}" } ?: "오늘"
    return listOfNotNull(date, r.modeName, r.minutes?.let { "${it}분" }).joinToString(" · ")
}

@Composable
private fun RCard(modifier: Modifier = Modifier, bg: Color = Color.White, content: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, dashed: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (dashed) Modifier.dashed(OttoLine, 999f) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, fontSize = 12.sp, color = fg, maxLines = 1) }
}

private fun Modifier.dashed(c: Color, radius: Float) = drawBehind {
    val r = minOf(radius, size.minDimension / 2)
    drawRoundRect(c, cornerRadius = CornerRadius(r, r), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
}

@Composable
private fun SourcePill(by: String, child: String) = when (by) {
    "child" -> Pill("${child}${ga(child)} 말함", ChildBg, ChildFg)
    "card" -> Pill("카드로 고름", CardBg, CardFg)
    else -> Pill("오또가 채움", Color.White, OttoFg, dashed = true)
}

/** 오늘의 기록 — 한 권의 리포트. [onTalk] 은 「대화 전체 보기」 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionReportView(r: SessionReport, child: String, onTalk: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 머리 — 표지 · 날짜 · 오늘 만든 책
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp, 104.dp).clip(RoundedCornerShape(12.dp)).background(RAccent)) {
                AssetImage(r.bgName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { Box(Modifier.fillMaxSize().background(RAccent)) }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dayLine(r), fontSize = 13.sp, color = RSub)
                Text(if (r.mode == "diary") "오늘 ${child}${ga(child)} 쓴 일기" else "오늘 ${child}${ga(child)} 만든 이야기", fontSize = 15.sp, color = RInk)
                Text(r.title, fontFamily = KidFont, fontSize = 26.sp, color = RInk, lineHeight = 34.sp)
            }
        }

        // 숫자 칸 — 0 인 칸은 보이지 않는다. 말로 한 것이 없는 날은 칸 대신 아래 한 줄 (시안 QuietDay)
        val tiles = listOfNotNull(
            (r.exchanges to "번").takeIf { it.first > 0 }?.let { Triple(it.first.toString(), it.second, "주고받은 대화") },
            r.spoken.takeIf { it > 0 }?.let { Triple("$it", "마디", "${child}${ga(child)} 직접 한 말") },
            r.longestWords.takeIf { it > 1 }?.let { Triple("$it", "낱말", "가장 길게 한 말") },
            r.childBones.takeIf { it > 0 }?.let { Triple("$it", " / ${r.bones.size}", "이야기 뼈대를 말로 지음") },
        )
        if (tiles.isNotEmpty() && r.spoken > 0) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.forEach { (n, unit, label) ->
                RCard(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(n, fontFamily = KidFont, fontSize = 30.sp, color = RAccent)
                        Text(" $unit".replace("  ", " "), fontFamily = KidFont, fontSize = 16.sp, color = RInk, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(label, fontSize = 13.sp, color = RInk)
                }
            }
        }

        // 말이 적었던 날 — 숫자 대신 무엇으로 만들었는지 (시안 QuietDay)
        if (r.spoken == 0) RCard(Modifier.fillMaxWidth()) {
            Text(if (r.exchanges > 0) "오늘은 카드와 그림으로 ${if (r.mode == "diary") "일기를" else "이야기를"} 만들었어요" else "오늘은 오또가 이야기를 이끌었어요",
                fontFamily = KidFont, fontSize = 20.sp, color = RInk)
            Text("말로 하지 않아도 고르고 그린 것이 다 ${child}${ga(child)} 만든 거예요.", fontSize = 13.sp, color = RSub, modifier = Modifier.padding(top = 4.dp))
        }

        // 이야기의 뼈대 — 세로 줄 (긴 말도 깨지지 않게 · 시안 수정)
        if (r.bones.isNotEmpty()) RCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${child}${ga(child)} 지은 이야기의 뼈대", fontFamily = KidFont, fontSize = 19.sp, color = RInk, modifier = Modifier.weight(1f))
                Text("이야기를 이루는 ${r.bones.size}가지", fontSize = 12.sp, color = RSub)
            }
            Spacer(Modifier.height(8.dp))
            r.bones.forEach { b ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(RLine))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text(b.label, fontSize = 13.sp, color = if (b.by == "child") ChildFg else CardFg, modifier = Modifier.width(84.dp).padding(top = 2.dp))
                    Text(
                        if (b.quoted) "“${b.text}”" else b.text,
                        fontSize = 15.sp, lineHeight = 22.sp, color = if (b.by == "mascot") OttoFg else RInk,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    SourcePill(b.by, child)
                }
            }
        }

        // 오늘 보인 순간 — 아이 말 그대로 + 그 말이 보여 주는 것
        if (r.moments.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("오늘 보인 순간", fontFamily = KidFont, fontSize = 19.sp, color = RInk)
            // 한 마디가 여러 순간을 보였으면 한 장에 — 같은 말을 되풀이하지 않는다
            r.moments.groupBy { it.quote }.forEach { (quote, ms) ->
                RCard(Modifier.fillMaxWidth()) {
                    Text("“$quote”", fontSize = 16.sp, color = RInk)
                    ms.forEach { m ->
                        val (name, meaning) = MOMENT_MEANING[m.kind] ?: return@forEach
                        val (bg, fg) = MOMENT_COLORS[m.kind] ?: (ChildBg to ChildFg)
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
                            Box(Modifier.width(110.dp)) { Pill(name, bg, fg) }
                            Text(meaning, fontSize = 13.sp, lineHeight = 19.sp, color = RSub, modifier = Modifier.weight(1f).padding(top = 2.dp))
                        }
                    }
                }
            }
        }

        // 처음 해낸 것 · 집에서 이어 가기
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r.firsts.isNotEmpty()) RCard(Modifier.weight(1f).fillMaxHeight(), bg = Color(0xFFFFF6DC)) {
                Text("처음 해낸 것", fontFamily = KidFont, fontSize = 17.sp, color = RInk)
                r.firsts.forEach { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = RInk, modifier = Modifier.padding(top = 6.dp)) }
            }
            RCard(Modifier.weight(1f).fillMaxHeight(), bg = Color(0xFFEAF4F2)) {
                Text("집에서 이어 가기", fontFamily = KidFont, fontSize = 17.sp, color = RTeal)
                Text(r.homeQuestion, fontSize = 14.sp, lineHeight = 21.sp, color = RInk, modifier = Modifier.padding(top = 6.dp))
                Text(HOME_TIP, fontSize = 12.sp, color = RTeal, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (r.talk.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5D9C6)))
            Text("대화 전체 보기 ›", fontSize = 14.sp, color = RTeal, modifier = Modifier.align(Alignment.End).clickable(onClick = onTalk).padding(4.dp))
        }
    }
}

/** 대화 전체 — 오또는 왼쪽, 아이는 오른쪽. 카드 · 오또가 채운 답은 그렇다고 보인다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionTalkView(r: SessionReport, child: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 오늘의 기록", fontSize = 14.sp, color = RTeal, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Spacer(Modifier.weight(1f))
            Text("${dayLine(r)} · ${r.title}", fontSize = 13.sp, color = RSub, maxLines = 1)
        }
        Text("대화 전체", fontFamily = KidFont, fontSize = 24.sp, color = RInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${child}${ga(child)} 말함", ChildBg, ChildFg)
            Pill("카드 · 그림으로 고름", CardBg, CardFg)
            Pill("오또가 채움", Color.White, OttoFg, dashed = true)
        }
        Spacer(Modifier.height(4.dp))
        r.talk.forEach { l -> Bubble(l, child) }
    }
}

@Composable
private fun Bubble(l: TalkLine, child: String) {
    val mine = l.who != "otto"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        val shape = if (mine) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        val (bg, fg, text) = when (l.who) {
            "otto" -> Triple(Color.White, RInk, "오또 · ${l.text}")
            "child" -> Triple(ChildBg, RInk, "“${l.text}”")
            "card" -> Triple(CardBg, Color(0xFF5E4D42), "카드로 고름 · ${l.text}")
            "draw" -> Triple(CardBg, Color(0xFF5E4D42), "그림으로 답함 · ${l.text}")
            "adult" -> Triple(Color(0xFFEAF4F2), RInk, "함께한 어른 · ${l.text}")
            else -> Triple(Color.White, OttoFg, "대답 없음 · 오또가 채움: ${l.text}")
        }
        Box(
            Modifier.widthIn(max = 520.dp).clip(shape).background(bg)
                .then(if (l.who == "mascot") Modifier.dashed(OttoLine, 16f) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = fg) }
        if (l.tags.isNotEmpty()) {
            val first = l.tags.first()
            Text(
                "↳ " + l.tags.mapNotNull { MOMENT_MEANING[it]?.first }.joinToString(" · "),
                fontSize = 12.sp, color = (MOMENT_COLORS[first] ?: (ChildBg to ChildFg)).second,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 지난 기록 — 꽂은 책마다 한 장. 고르면 그 책의 리포트를 연다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastReports(list: List<SessionReport>, current: SessionReport?, onPick: (SessionReport) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("지난 기록", fontFamily = KidFont, fontSize = 17.sp, color = RInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { r ->
                val on = current != null && r.bookId == current.bookId && r.day == current.day
                val d = runCatching { LocalDate.parse(r.day) }.getOrNull()
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (on) RAccent else Color.White)
                        .border(1.dp, if (on) RAccent else RLine, RoundedCornerShape(999.dp))
                        .clickable { onPick(r) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("${d?.let { "${it.monthValue}/${it.dayOfMonth} " } ?: ""}${r.title}", fontSize = 13.sp, color = if (on) Color.White else RInk, maxLines = 1)
                }
            }
        }
    }
}
