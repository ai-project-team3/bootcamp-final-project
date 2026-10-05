package com.example.finalproject_demo.ui.missions

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.ui.Coral
import com.example.finalproject_demo.ui.Ink
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.delay

/*
 * 미션 공통 틀 — 새 미션 화면이 같이 쓰는 조각 (`docs/맞춤미션_설계.md` §5-1 · #101 「첫 새 미션(C1)을 만들 때 모양을 정한다」).
 *
 * - [rememberIdleHint] — 8초 동안 진전이 없으면 손 힌트를 켠다(미션 구상 §5 · 원칙 6 탭 길이 보이는 때)
 * - [MissionDoneSignal] — 다 하면 한 번만 반짝 소리 + 감독에게 `Reply.Tapped("mission", …)` (지금 미션들과 같은 신호)
 * - [MicListeningTag] — 마이크 미션이 듣고 있다는 표시. 부모가 마이크가 켜진 것을 알 수 있어야 한다(설계 §10 마이크 고지).
 *   **화면 아래**에 둔다 — 위쪽은 책 안내 말풍선 자리라 두 개가 겹쳤다(#98 「후~ 불어 봐」 잘림)
 */

/** 8초 동안 [progress] 가 그대로면 true. [done] 이면 늘 false */
@Composable
internal fun rememberIdleHint(progress: Float, done: Boolean): Boolean {
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(progress, done) {
        idle = false
        if (done || motionFrozen) return@LaunchedEffect
        delay(8_000)
        idle = true
    }
    return idle
}

/** 미션을 다 했을 때 한 번 — 이미 끝난 쪽(다시 펼친 책)이면 보내지 않는다 */
@Composable
internal fun MissionDoneSignal(d: Director, finished: Boolean, alreadyDone: Boolean, label: String) {
    val view = LocalView.current
    var sent by remember { mutableStateOf(alreadyDone) }
    LaunchedEffect(finished) {
        if (finished && !sent) {
            sent = true
            Sfx.play(Sound.SPARKLE, 0L, view = view)
            d.send(Reply.Tapped("mission", label))
        }
    }
}

/** 마이크가 듣는 중 — 「🎤 듣고 있어요 · 손으로 해도 돼」. 잘하면 칭찬으로 바뀐다 */
@Composable
internal fun BoxScope.MicListeningTag(listening: Boolean, strong: Boolean, idle: String, cheer: String) {
    if (!listening) return
    Text(
        if (strong) cheer else idle,
        fontSize = 15.sp,
        color = if (strong) Coral else Ink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 92.dp)
            .felt(Wool.copy(alpha = 0.95f), RoundedCornerShape(Radius.Round), lift = 3.dp, stitch = false)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

