package com.example.finalproject_demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director

/**
 * 띠 오른쪽에 비워 두는 폭 — 🎤(약 78dp) + ➡️(48+10dp) + 바깥 여백(14dp).
 * [com.example.finalproject_demo.ui.FloatingControls] 가 여기 앉는다. 버튼을 키우면 이 값도 같이 키운다.
 */
private val END_RESERVE = 168.dp

/**
 * 부모 띠 — 부모 협업 모드에서 **새로 만드는 유일한 화면**이다 (부모협업모드_설계.md §3).
 *
 * ```
 * ┌──────────────────────────────┐
 * │    마스코트 · 그림 · 진행 별      │  ← 아이가 보는 곳 (S3 그대로)
 * ├──────────────────────────────┤
 * │ 🐻  "거기서 무슨 일이 있었어?"     │  ← 부모 띠 (여기)
 * └──────────────────────────────┘
 * ```
 *
 * 9/21에 띠에서 버튼 두 개를 뺐다. [다르게 물어볼래] · [내가 답할래]를 고르는 것이 일이 되어
 * 아이와 이야기하는 흐름이 끊겼고, 그 버튼이 보내던 값(`coop:adult`)이 화면에 그대로 새어 나오기도 했다.
 * 지금 띠는 **읽을 것만 보여 준다.** 질문을 바꾸는 것도 마지막에 채우는 것도 동화 모드와 똑같이 마스코트가 한다.
 * 왼쪽의 마스코트는 아이 쪽 신호다 — 글을 못 읽는 아이에게는 "마스코트가 물어본다"로 보인다.
 *
 * 왜 아이 화면에 같이 띄워도 되나 — **4~5세는 글을 못 읽는다.**
 * 아이는 위쪽 그림을 보고 부모는 아래쪽 글자를 읽는다 (§3).
 *
 * ⚠️ 이 카드는 **소리가 없다.** 마스코트가 읽어 주면 부모가 물을 이유가 없어진다.
 * 부모가 읽고 **자기 말로** 묻는 것이 이 모드의 전부다 (§2-1 ASK′).
 */
@Composable
fun ParentBand(d: Director, modifier: Modifier = Modifier) {
    val s = d.s
    val card = s.parentCard
    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
        modifier = modifier,
    ) {
        val t = rememberInfiniteTransition(label = "band")
        val glow by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFF3C3340))
                // 오른쪽 아래 🎤 · ➡️ 자리를 비워 둔다 — 글자가 버튼 밑으로 들어가지 않게 (9/22).
                // 버튼을 위로 올리는 대신 띠가 양보한다. 버튼은 늘 오른쪽 아래에 있어야 손이 간다
                .padding(start = 18.dp, end = END_RESERVE, top = 10.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 마스코트가 띠 왼쪽에서 물어보는 것처럼 — 숨 쉬듯 살짝 커졌다 작아진다 (9/21)
                Box(Modifier.size(52.dp).scale(0.94f + glow * 0.08f)) {
                    ArtView(Art.Mascot, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 어른에게 하는 말이라 작게 — 아이는 이 줄을 읽지 않는다
                    Text(
                        "어른이 읽고 물어봐 주세요",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        "“${card.orEmpty()}”",
                        fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 27.sp,
                    )
                }
            }
            // ⚠️ 지금은 **뜨지 않는 줄**이다. [내가 답할래]를 빼면서 `author = "adult"` 인 자리가
            //    아예 생기지 않게 됐고, `parentTooMuch` 는 그 수를 센다 (9/21).
            //    어른이 칸을 직접 짓는 흐름이 다시 들어오면 그대로 살아난다 — 그래서 지우지 않고 둔다.
            if (s.parentTooMuch) {
                Spacer(Modifier.height(6.dp))
                // 부모 자리가 절반을 넘으면 알려 준다. 채점이 아니라 귀띔이다 (§6)
                Text(
                    "어른이 지은 자리가 많아요. 아이 차례를 한 번 더 기다려 볼까요?",
                    fontSize = 12.sp, color = Color(0xFFFFC9A8),
                )
            }
        }
    }
}
