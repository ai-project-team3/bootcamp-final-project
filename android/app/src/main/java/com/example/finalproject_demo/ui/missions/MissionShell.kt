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
 * - [MissionHelp] — 15초 동안 그대로면 오또가 「같이 해 볼까?」 하며 **반쯤** 해 준다. 끝내 주지는 않는다 —
 *   마지막은 아이 손으로 끝나야 「주인공 따라 하기」다 (#260 · `docs/실기기수정_설계_1007.md` §6-2)
 * - [MissionDoneSignal] — 다 하면 한 번만 반짝 소리, 완료 장면을 [DONE_SCENE_MS] 보여 준 뒤 감독에게
 *   `Reply.Tapped("mission", …)` — 그 신호에 감독이 쪽 문장을 다시 읽는다. 바로 보내면 낭독이 결과 장면을 덮었다
 *
 * **효과음 규칙 (#260 §6-2)** — 한 미션에 진행 중 한 종류(톡 · 치익 …) + 완료 반짝 한 번. 완료 반짝은 여기서만 낸다.
 * 하나씩 끝날 때(불 하나 · 블록 하나)는 진행 소리로 — 마지막 하나와 완료가 겹쳐 반짝이 두 번 났다
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

/** 완료 장면을 보여 주는 시간 — 그 뒤 감독이 쪽 문장을 다시 읽는다 (#260 §6-2 「끝 연출은 1.2~1.8초」) */
internal const val DONE_SCENE_MS = 1_500L

/** 진전 없이 이만큼 지나면 오또가 반쯤 도와준다 — 8초 손 힌트 다음 단계 */
internal const val HELP_AFTER_MS = 15_000L

/** 도와줄 때 오또가 하는 말 — 아이에게 「같이」 하자고 한다(대신 해 주지 않는다) */
internal const val HELP_LINE = "같이 해 볼까? 오또가 반만 해 줄게. 나머지는 네가 해 봐!"

/** 미션을 다 했을 때 한 번 — 이미 끝난 쪽(다시 펼친 책)이면 보내지 않는다 */
@Composable
internal fun MissionDoneSignal(d: Director, finished: Boolean, alreadyDone: Boolean, label: String) {
    val view = LocalView.current
    var sent by remember { mutableStateOf(alreadyDone) }
    LaunchedEffect(finished) {
        if (finished && !sent) {
            sent = true
            Sfx.play(Sound.SPARKLE, 0L, view = view)
            // 결과 장면(불이 꺼지고 김이 오른다 …)을 먼저 보여 준다. 검사는 움직임을 멈춰 두므로 기다리지 않는다
            if (!motionFrozen) delay(DONE_SCENE_MS)
            d.send(Reply.Tapped("mission", label))
        }
    }
}

/**
 * [HELP_AFTER_MS] 동안 [progress] 가 그대로면 **한 번** — 오또가 [HELP_LINE] 을 말하고 [halfway] 로 반쯤 해 준다.
 * [halfway] 는 미션이 정한다(불 셋이면 하나 반 · 블록 셋이면 하나). **끝내는 데까지 가면 안 된다** — 마지막은 아이 몫
 */
@Composable
internal fun MissionHelp(d: Director, progress: Float, done: Boolean, halfway: () -> Unit) {
    var helped by remember { mutableStateOf(false) }
    val half by androidx.compose.runtime.rememberUpdatedState(halfway)
    LaunchedEffect(progress, done) {
        if (done || helped || motionFrozen) return@LaunchedEffect
        delay(HELP_AFTER_MS)
        helped = true
        d.say(HELP_LINE)
        half()
    }
}

/**
 * 같은 양이 여럿인 미션(불 셋 · 흔적 셋)을 전체의 반까지 채운다 — 앞에서부터, 이미 채운 만큼은 그대로.
 * 전체 [full] × 개수의 반을 넘지 않는다. 이미 반을 넘었으면 아무것도 안 한다
 */
internal fun fillToHalf(amounts: MutableList<Float>, full: Float) {
    var need = full * amounts.size / 2f - amounts.sum()
    for (i in amounts.indices) {
        if (need <= 0f) return
        val add = minOf(full - amounts[i], need)
        if (add > 0f) { amounts[i] += add; need -= add }
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

