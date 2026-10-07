package com.example.finalproject_demo.ui.missions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.ui.ArtView
import com.example.finalproject_demo.ui.Coral
import com.example.finalproject_demo.ui.Ink
import com.example.finalproject_demo.ui.Radius
import com.example.finalproject_demo.ui.Sfx
import com.example.finalproject_demo.ui.Sound
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * 미션 공통 틀 — 새 미션 화면이 같이 쓰는 조각 (`docs/맞춤미션_설계.md` §5-1 · #101 「첫 새 미션(C1)을 만들 때 모양을 정한다」).
 *
 * - [rememberIdleHint] — 8초 동안 진전이 없으면 손 힌트를 켠다(미션 구상 §5 · 원칙 6 탭 길이 보이는 때)
 * - [rememberMissionHint] — 15초 동안 그대로면 오또가 「이렇게 해 보자!」 하고 **흐릿한 예시**(반투명 손 · 제자리로 미끄러지는
 *   소품)를 한 번 보인다. **오또가 대신 해 주지 않는다** — 미션은 아이 손으로 끝까지 (#293 리뷰 · 조장 결정)
 * - [MissionDoneSignal] — 다 하면 한 번만 반짝 소리와 함께 **바로** 감독에게 `Reply.Tapped("mission", …)`.
 *   낭독이 결과 장면을 덮지 않게 늦추는 것은 감독 쪽(쪽 문장만 [DONE_SCENE_MS] 뒤에) — 여기서 신호를 늦추면
 *   그 사이 ▶ 로 넘긴 아이의 완료가 사라졌다(#293 리뷰)
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

/** 완료 장면을 보여 주는 시간 — 감독이 그 뒤에 쪽 문장을 다시 읽는다 (#260 §6-2 「끝 연출은 1.2~1.8초」) */
internal const val DONE_SCENE_MS = 1_500L

/** 진전 없이 이만큼 지나면 흐릿한 예시를 한 번 — 8초 손 힌트 다음 단계 */
internal const val HINT_AFTER_MS = 15_000L

/** 흐릿한 예시 한 번의 길이 */
internal const val HINT_SHOW_MS = 2_600L

/**
 * 예시를 보일 때 오또가 하는 말. **물음표를 넣지 않는다** — 물음표가 든 말은 `pendingQuestion` 이 돼
 * 리포트 대화록에 아이에게 한 질문으로 남는다(#293 리뷰)
 */
internal const val HINT_LINE = "이렇게 해 보자! 오또가 보여 줄게."

/** 미션을 다 했을 때 한 번 — 이미 끝난 쪽(다시 펼친 책)이면 보내지 않는다. 기다리지 않고 바로 보낸다(위 머리말) */
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

/**
 * [HINT_AFTER_MS] 동안 [progress] 가 그대로면 **한 번** — 오또가 [HINT_LINE] 을 말하고, 미션이 흐릿한 예시를 그린다.
 * 돌려주는 값은 예시의 진행(0 → 1) · 보이지 않을 때 null. 예시를 봤다는 것은 `mission_hint` 로 남는다(규칙 5) —
 * 끝낸 것은 아이라 결과(`solo`)는 그대로다. 미션 값을 바꾸는 길은 없다
 */
@Composable
internal fun rememberMissionHint(d: Director, progress: Float, done: Boolean, mission: String): Float? {
    var shown by remember { mutableStateOf(false) }
    var t by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(progress, done) {
        t = null                                          // 아이가 움직이면 예시는 거기서 사라진다
        if (done || shown || motionFrozen) return@LaunchedEffect
        delay(HINT_AFTER_MS)
        shown = true
        d.say(HINT_LINE)
        d.event("mission_hint", "mission" to mission)
        val start = withFrameMillis { it }
        while (true) {
            val p = (withFrameMillis { it } - start) / HINT_SHOW_MS.toFloat()
            if (p >= 1f) break
            t = p
        }
        t = null
    }
    return t
}

/** 흐릿한 예시의 투명도 — 스며 나와 머물다 사라진다. 진짜 손 · 소품과 헷갈리지 않게 반보다 흐리게 */
internal fun ghostAlpha(t: Float): Float = 0.45f * when {
    t < 0.15f -> t / 0.15f
    t > 0.8f -> (1f - t) / 0.2f
    else -> 1f
}.coerceIn(0f, 1f)

/** 예시가 [t] 일 때 [path] 위의 자리 — 앞 15% 는 첫 자리에 머물고, 0.15~0.8 사이에 끝까지 가서 끝자리에 머문다 */
internal fun ghostAlong(path: List<Offset>, t: Float): Offset {
    if (path.size < 2) return path.firstOrNull() ?: Offset.Zero
    val u = ((t - 0.15f) / 0.65f).coerceIn(0f, 1f)
    val e = u * u * (3f - 2f * u)                      // 천천히 떠나 천천히 닿는다
    val x = e * (path.size - 1)
    val i = x.toInt().coerceAtMost(path.size - 2)
    return path[i] + (path[i + 1] - path[i]) * (x - i)
}

/** 흐릿한 예시 하나 — [at] 이 가운데, 한 변 [sizePx] */
@Composable
internal fun Ghost(at: Offset, sizePx: Float, t: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset { IntOffset((at.x - sizePx / 2).roundToInt(), (at.y - sizePx / 2).roundToInt()) }
            .size((sizePx / density).dp)
            .alpha(ghostAlpha(t)),
    ) { content() }
}

/** 흐릿한 손 — 손가락 끝이 [at] 에 오게(그림의 손끝은 위 가운데) */
@Composable
internal fun GhostHand(at: Offset, sizePx: Float, t: Float) =
    Ghost(at + Offset(0f, sizePx * 0.4f), sizePx, t) { ArtView(Art.Img("ic_hand", Art.Emoji("👆")), Modifier.fillMaxSize()) }

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

