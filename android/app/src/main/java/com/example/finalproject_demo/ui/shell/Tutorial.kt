package com.example.finalproject_demo.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltMustard
import com.example.finalproject_demo.ui.FeltSky
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.WoolCream
import com.example.finalproject_demo.ui.felt

/*
 * ── 기능 안내 (10-05 · 치영) — 보호자가 오또로 무엇을 할 수 있는지 **확실히** 알게 ─────────────────────
 *
 * 전에는 처음 설정 끝에 카드 세 장(말하면 · 그림책이 돼요 · 책장에 모여요)만 있어서, 그림일기 · 같이 만들기 ·
 * 부모 영역이 있다는 것을 보호자가 몰랐다. 이제 처음 설정은 이렇게 간다:
 *
 *   ⑤ 맞춤 설정 → **⑥ 기능 안내 (보호자 · 여섯 장)** → ⑦ 아이에게 건네기 → **⑧ 방 둘러보기 (아이 · 물건 넷)** → ⑨ 말해 보기 → 방
 *
 * 한 장마다 「어디에 있나 · 무엇이 되나 · 이렇게 해요 1 2 3 · 부모님 팁」. 방의 물건 그림 · 모드 아이콘을 그대로 써서
 * 방에 들어갔을 때 같은 그림을 알아보게 한다. 부모 영역 → 계정 → 「기능 안내 다시 보기」로 언제든 다시 연다.
 */

/** 안내 한 장 */
data class GuidePage(
    val art: String, val fallback: String, val accent: Color,
    val name: String, val where: String, val title: String, val steps: List<String>, val tip: String,
)

val GUIDE = listOf(
    GuidePage(
        "room_theater", "🎭", FeltCoral, "동화 만들기", "방의 인형극 무대",
        "상상한 이야기가 그림책이 돼요",
        listOf(
            "무대를 누르고 주인공의 모습(머리 · 옷 · 안경)을 골라요",
            "오또가 묻는 말에 아이가 말로 답해요 — 오또가 귀를 쫑긋하면 말할 차례예요",
            "답할 때마다 장면이 그려지고, 마지막엔 표지까지 있는 그림책이 완성돼요",
        ),
        "말이 막히면 화면에 뜨는 그림 버튼을 눌러 골라도 이야기가 이어져요",
    ),
    GuidePage(
        "room_window", "☀️", FeltSky, "그림일기", "방의 창문",
        "오늘 있었던 일을 그림일기로 남겨요",
        listOf(
            "창문을 누르면 오또가 「오늘 무슨 일 있었어?」 하고 물어요",
            "누구랑 · 어디서 · 무엇을 · 기분이 어땠는지 말로 답해요",
            "아이가 직접 그리거나 「오또가 대신 그려 주기」로 그림을 채워 일기를 완성해요",
        ),
        "잠들기 전 하루를 돌아보며 이야기 나누는 시간으로 좋아요",
    ),
    GuidePage(
        "room_sofa", "🛋", FeltTeal, "같이 만들기", "방의 소파 · 부모 영역",
        "부모님이 고른 이야기로 함께 만들어요",
        listOf(
            "🔒 부모 영역 → 같이 만들기에서 이야기 갈래(장소 · 직업 · 스포츠)와 그 안의 하나를 고르고, 묻고 싶은 질문을 적어요",
            "방의 소파에 🎁 표시가 붙으면 아이가 소파를 눌러 시작해요",
            "오또가 부모님 질문을 대신 묻고, 아이의 답으로 둘만의 책이 만들어져요",
        ),
        "아이 옆에서 「그만하기」를 누르면 지금까지 한 이야기로 책을 마무리해요",
    ),
    GuidePage(
        "room_shelf", "📚", FeltMustard, "내 책장", "방의 책장",
        "만든 책은 모두 책장에 모여요",
        listOf(
            "책장을 누르면 지금까지 만든 동화 · 그림일기 · 같이 만든 책이 보여요",
            "책을 펼쳐 다시 읽고, 아이가 녹음한 소리도 다시 들어요",
            "만들다 멈춘 이야기는 물건에 🧶 표시 — 누르면 이어서 해요",
        ),
        "책 · 그림 · 녹음은 이 폰에만 저장돼요",
    ),
    GuidePage(
        "pi_lock", "🔒", FeltCoral, "부모 영역", "방 왼쪽 위 🔒",
        "부모님만 여는 기록 · 설정",
        listOf(
            "🔒 을 누르고 처음에 정한 부모 비밀번호 네 자리를 넣어요",
            "오늘의 기록에서 아이가 한 말을 원문 그대로 볼 수 있어요",
            "하루 책 수 · 소리 · 업적 · 계정 · 문제 신고를 여기서 해요",
        ),
        "비밀번호를 잊으면 보호자 태어난 해로 다시 정할 수 있어요",
    ),
    GuidePage(
        "feat_talk", "🎙", FeltTeal, "안심하고 쓰세요", "개인정보",
        "아이 목소리는 남기지 않아요",
        listOf(
            "마이크는 오또가 귀를 쫑긋한 차례에만 들어요",
            "목소리는 글자로 바꾼 뒤 바로 지우고, 서버에 저장하지 않아요",
            "아이가 그린 그림 · 녹음한 소리 · 책은 폰 밖으로 나가지 않아요",
        ),
        "동의 · 소식 알림 · 이름 부르기는 부모 영역 → 계정에서 언제든 바꿀 수 있어요",
    ),
)

/**
 * ⑥ 기능 안내 — 여섯 장을 넘기며 본다. 옆으로 밀어도 넘어간다. 아래 점을 누르면 그 장으로.
 * @param finish 마지막 장의 버튼 글자 — 처음 설정이면 「아이에게 건네기」, 다시 보기면 「닫기」
 */
@Composable
fun FeatureGuide(finish: String = "아이에게 건네기", onClose: (() -> Unit)? = null, onDone: () -> Unit) = ParentText {
    var page by remember { mutableIntStateOf(0) }
    var forward by remember { mutableIntStateOf(1) }
    fun go(to: Int) { if (to in GUIDE.indices && to != page) { forward = if (to > page) 1 else -1; page = to } }
    val last = page == GUIDE.lastIndex
    Box(
        Modifier.fillMaxSize().background(Wool).pointerInput(Unit) {
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                if (dx < -80f) go(page + 1) else if (dx > 80f) go(page - 1)
            }) { _, d -> dx += d }
        },
    ) {
        // 위 — 로고 · 무엇을 보는 화면인지 · 몇 장째
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AssetImage("logo_otto_v2", Modifier.width(76.dp))
            Spacer(Modifier.width(12.dp))
            Text("오또로 이렇게 놀아요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Spacer(Modifier.weight(1f))
            Text("${page + 1} / ${GUIDE.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
            if (onClose != null) Box(Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).clickable { onClose() }.semantics { contentDescription = "닫기" }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 18.sp, color = InkSoft)
            }
        }
        AnimatedContent(
            page, label = "guide",
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 6 * forward } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 * forward } + fadeOut(tween(200)))
            },
            modifier = Modifier.fillMaxSize().padding(top = 66.dp, bottom = 76.dp),
        ) { i -> GuideCard(GUIDE[i], i) }
        // 아래 — 점(누르면 그 장) · 이전 · 다음
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GUIDE.forEachIndexed { i, g ->
                    Box(
                        Modifier.height(12.dp).width(if (i == page) 30.dp else 12.dp).clip(CircleShape)
                            .background(if (i == page) g.accent else InkBrown.copy(alpha = 0.15f))
                            .clickable { go(i) }.semantics { contentDescription = "${g.name} 안내" },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (page > 0) PBtn("이전", { go(page - 1) }, Modifier.width(110.dp), primary = false, height = 50.dp)
            Spacer(Modifier.width(10.dp))
            PBtn(if (last) finish else "다음", { if (last) onDone() else go(page + 1) }, Modifier.width(190.dp), height = 50.dp)
        }
    }
}

@Composable
private fun GuideCard(g: GuidePage, index: Int) {
    Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        // 왼쪽 — 방에서 보게 될 바로 그 그림 + 어디에 있나
        Column(
            Modifier.width(250.dp).fillMaxHeight().felt(FeltWhite, RoundedCornerShape(26.dp)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AssetImage(g.art, Modifier.size(170.dp)) {
                    Box(Modifier.size(120.dp).felt(g.accent, CircleShape), contentAlignment = Alignment.Center) { Text(g.fallback, fontSize = 54.sp) }
                }
            }
            Row(
                Modifier.felt(g.accent, RoundedCornerShape(16.dp), lift = 2.dp, stitch = false, texture = false).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("📍 ${g.where}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FeltWhite) }
        }
        Spacer(Modifier.width(26.dp))
        // 오른쪽 — 이름 · 무엇이 되나 · 이렇게 해요 · 팁
        Column(Modifier.weight(1f)) {
            Text("${index + 1}. ${g.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = g.accent)
            Spacer(Modifier.height(2.dp))
            Text(g.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkBrown, lineHeight = 28.sp)
            Spacer(Modifier.height(10.dp))
            g.steps.forEachIndexed { k, s ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(g.accent), contentAlignment = Alignment.Center) {
                        Text("${k + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FeltWhite)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(s, fontSize = 13.sp, color = InkBrown, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WoolCream).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💡", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("부모님 팁 · ${g.tip}", fontSize = 12.sp, color = InkSoft, lineHeight = 17.sp)
            }
        }
    }
}

/**
 * 튜토리얼에서 오또가 하는 말을 **목소리로도** 낸다 — 아이는 글을 못 읽는다.
 * 앱에 구워 둔 소리가 있으면 그것을, 없으면 서버 목소리(서버를 켰을 때만). 실패하면 말풍선만 남는다.
 * 튜토리얼 대사에는 아이 이름 · 개인정보가 없다.
 */
@Composable
fun OttoSays(line: String) {
    LaunchedEffect(line) {
        if (!Voice.canSpeak) return@LaunchedEffect
        val audio = Voice.baked(line) ?: if (Server.on) Server.tts(line) else null
        audio?.let { runCatching { Voice.playAndWait(it) } }
    }
}
