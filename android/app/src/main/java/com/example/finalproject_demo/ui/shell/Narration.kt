package com.example.finalproject_demo.ui.shell

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.ui.Cheek
import com.example.finalproject_demo.ui.Curtain
import com.example.finalproject_demo.ui.Expr
import com.example.finalproject_demo.ui.FeltSky
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.OttoFace
import com.example.finalproject_demo.ui.OttoState
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.felt

/**
 * 모드 — 나레이션 칸 테두리 색 · 모드 표시 (디자인 시스템 09-28 저녁판 「세 모드 모두 같은 틀」)
 */
enum class NarrationMode(val color: Color, val label: String, val icon: String) {
    STORY(Curtain, "이야기 만들기", "🎭"),
    DIARY(FeltSky, "오늘 이야기", "☀️"),
    COOP(FeltTeal, "같이 만들기", "👪"),
}

/**
 * **나레이션 칸** — 화면 아래 전체 폭 한 줄: **[오또 얼굴] [오또가 하는 말] [녹음 버튼]** 이 모두 칸 **안**에 있다 (09-29 사용자 요청).
 *
 * 전에는 얼굴이 칸 왼쪽 위로 걸쳐 나오고 🎤 · ➡️ 는 칸 밖 오른쪽 아래에 떠 있어서, 무대 위 버튼과 겹치기 쉬웠다.
 *
 *   얼굴 테두리 = 상태 — 분홍 말함 · 청록 들음 · 겨자 생각. **말할 때도 들을 때처럼** 테두리 색 파동이 얼굴 둘레로 퍼진다
 *   얼굴 그림 = 표정 — [Expr] (기쁨 · 깜짝 · 속상 · 궁금 · 뿌듯). 표정마다 움직임도 다르다
 *   칸 테두리 · 모드 표시 = 모드 — 빨강 이야기 만들기 · 파랑 오늘 이야기 · 청록 같이 만들기
 *
 * 09-30 — 얼굴은 칸 왼쪽 아래에 밑선을 맞춰 앉고 위로 솟는다. 칸은 얼굴보다 낮고(최소 62dp) 글씨는 칸 아래쪽에 붙는다.
 * 높이는 위 여백 6 + 얼굴 84 + 아래 8 = 98dp — 무대 안쪽 아래 여백(`BottomChrome` 102dp)이 이것에 맞춘다.
 *
 * @param trailing 칸 오른쪽 끝에 넣을 버튼들(녹음 · 그리기). 없으면 말하는 중 🔊 · 듣는 중 목소리 막대를 보인다
 */
@Composable
fun Narration(
    mode: NarrationMode,
    state: OttoState,
    line: String,
    modifier: Modifier = Modifier,
    speaker: String? = null,
    burst: Mood = Mood.NONE,
    burstId: Int = 0,
    expr: Expr = Expr.NONE,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 6.dp)) {
        // 칸 — 얼굴보다 낮다. 얼굴 자리(왼쪽 84dp)는 비워 두고 글씨는 칸 **아래쪽**에 붙인다 (09-30 사용자 그림)
        Box(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .felt(Wool, RoundedCornerShape(26.dp), lift = 5.dp)
                    .border(5.dp, mode.color, RoundedCornerShape(26.dp))
                    .padding(start = FaceSize + 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    if (speaker != null) ParentText { Text(speaker, fontSize = 12.sp, color = mode.color) }
                    Text(line, fontSize = 22.sp, color = InkBrown, lineHeight = 29.sp, maxLines = 2)
                }
                if (trailing != null) {
                    Spacer(Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = trailing)
                } else {
                    if (state == OttoState.TALK) {
                        Box(Modifier.size(46.dp).felt(Cheek, CircleShape, lift = 3.dp, stitch = false), contentAlignment = Alignment.Center) { Text("🔊", fontSize = 20.sp) }
                    }
                    if (state == OttoState.LISTEN) VoiceBars()
                }
            }
            // 모드 표시 — 칸 위에 걸친 작은 펠트 (오른쪽 녹음 버튼 위를 피해 조금 안쪽)
            Row(
                Modifier.align(Alignment.TopEnd).offset(x = (-110).dp, y = (-12).dp).felt(mode.color, RoundedCornerShape(15.dp), lift = 3.dp, stitch = false)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mode.icon, fontSize = 13.sp); Spacer(Modifier.width(6.dp))
                ParentText { Text(mode.label, fontSize = 13.sp, color = FeltWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        }
        // 오또 얼굴 — 칸 왼쪽 끝에 **밑선을 맞춰** 올라앉고 위로는 칸 밖으로 솟는다. 파동은 얼굴과 같은 중심에서 퍼진다
        OttoFace(state, Modifier.align(Alignment.BottomStart).size(FaceSize), burst = burst, burstId = burstId, expr = expr, pulse = true)
    }
}

private val FaceSize = 84.dp

/** 아이 목소리 막대 — 들리는 동안 출렁 */
@Composable
private fun VoiceBars() {
    val t = rememberInfiniteTransition(label = "bars")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "k")
    Row(
        Modifier.height(46.dp).felt(FeltTeal, RoundedCornerShape(23.dp), lift = 3.dp, stitch = false).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(10, 22, 32, 16, 26, 12, 20).forEachIndexed { i, h ->
            val f = if (i % 2 == 0) 0.6f + 0.4f * k else 1f - 0.4f * k
            Box(Modifier.width(6.dp).height((h * f).dp).clip(RoundedCornerShape(3.dp)).background(FeltWhite))
        }
    }
}
