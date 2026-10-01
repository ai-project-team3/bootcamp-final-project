package com.example.finalproject_demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/*
 * ── 가만히 두면 쉬기 (#40 · 10-01) ─────────────────────────────────────
 *
 * 방 · 시작 화면은 반복 움직임이 하나라도 돌면 매 프레임 다시 그린다 — 갤럭시 S10 5G 에서 버튼 두근거림 하나로
 * CPU 87%, 방(인형 + 반짝이) 106%. 움직임을 다 멈추면 0 프레임 · 0% (eval/results.md 09-30).
 * 그래서 한참 아무도 안 만지면 반복 움직임을 **쉬게** 하고, 만지는 순간 **바로** 다시 움직인다 —
 * 아이가 「멈췄다(고장)」로 느끼지 않게 (조장 조건 · #40).
 *
 * **방 · 시작 화면에만** 쓴다. 이야기 중(마스코트가 말하거나 듣는 화면)에는 쉬지 않는다.
 */

/** 마지막 터치 뒤 이만큼 조용하면 쉰다 — 조장 안 「20~30초」의 가운데 */
const val QUIET_REST_MS = 25_000L

class QuietRest {
    /** 누른 횟수 — 바뀔 때마다 시계를 처음부터 다시 잰다 */
    var touches by mutableIntStateOf(0)
        internal set

    /** 지금 쉬는 중인가 */
    var resting by mutableStateOf(false)
        internal set
}

/**
 * 조용한 시간을 재는 시계. [enabled] 가 거짓이면 쉬지 않는다 — 화면 검사(`motionFrozen`)는 이미 멈춰 있고,
 * 시간에 따라 그림이 바뀌면 기준 그림과 어긋난다.
 */
@Composable
fun rememberQuietRest(afterMs: Long = QUIET_REST_MS, enabled: Boolean = !motionFrozen): QuietRest {
    val rest = remember { QuietRest() }
    LaunchedEffect(rest.touches, enabled, afterMs) {
        rest.resting = false
        if (!enabled) return@LaunchedEffect
        delay(afterMs)
        rest.resting = true
    }
    return rest
}

/**
 * 화면을 누르면 깨운다. 터치를 **보기만** 하고 가로채거나 처리됨으로 표시하지 않는다 — 아래 버튼은 그대로 눌린다.
 * 가장 먼저 보는 단계(`Initial`)에서 보므로 어느 물건을 눌러도, 빈 바닥을 눌러도 깬다.
 */
fun Modifier.wakeOnTouch(rest: QuietRest): Modifier = pointerInput(rest) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press) rest.touches++
        }
    }
}
