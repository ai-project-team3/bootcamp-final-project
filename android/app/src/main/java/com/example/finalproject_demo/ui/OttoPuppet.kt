package com.example.finalproject_demo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import kotlin.math.abs
import kotlin.math.sin

/*
 * 오또 인형 (09-29 사용자 — 「마스코트한테도 뼈대를 붙여서 실제로 움직이게」 · 2안 「부위를 나눠 제대로」).
 *
 * 전신 A-포즈 한 장(ComfyUI · FLUX Kontext)을 몸 · 팔 둘 · 다리 둘 · 꼬리로 잘라(`tools/otto_puppet.py`)
 * 관절 축으로 돌려 붙인다. 부위는 모두 512×512 같은 틀이라 겹치기만 하면 제자리다.
 *
 * 자동 뼈대(`RigCore`)로 먼저 시험했는데, 오또는 팔이 후드 소매째 몸에 붙어 있어 팔을 못 잡거나(덩어리형)
 * 만세에서 후드 깃이 찢어졌다(`RigBuilderTest.마스코트_오또_시험`). 부위를 따로 두면 찢어질 것이 없다.
 *
 * 그리는 순서(뒤 → 앞): 꼬리 · 왼다리 · 오른다리 · 몸 · 왼팔 · 오른팔. 팔 뿌리는 어깨 둘레 둥근 조각까지 포함해
 * 돌려도 몸 쪽 자른 자리를 덮고, 다리 · 꼬리 뿌리는 몸 뒤에 숨는다.
 * 각도 식은 `tools/otto_puppet_preview.py` 와 같다 — 한쪽을 바꾸면 다른 쪽도.
 */

/**
 * 오또 인형 동작. 뒤의 넷은 **터치 리액션**(09-29 · 오또의 방에서 오또를 누르면 하나를 골라 한 번 한다)
 */
enum class OttoMove { IDLE, WAVE, WALK, HOORAY, POINT, JUMP, SPIN, DANCE, GIGGLE }

/** 한 프레임의 자세 — 부위 각도(PARTS 순서) · 몸 위아래(512 기준 px) · 몸 기울기(도, 발밑 축) · 좌우 폭(빙글 돌 때 1 → -1 → 1) */
private class PoseOut(val ang: FloatArray, val bob: Float, val tilt: Float = 0f, val sx: Float = 1f)

/** 부위 그림 · 관절 축(틀 안 비율) — `tools/otto_puppet.py` 가 출력한 값 */
private class Part(val file: String, val px: Float, val py: Float)

/**
 * 정면 — **기존 마스코트 그림**(`mascot.png`)을 자른 조각 (09-29 사용자 — 「마스코트 기존 이미지에 뼈대를 붙여서」).
 * 앱 곳곳의 오또와 같은 모습이다. `tools/otto_puppet_mascot.py` 가 잘랐다. 순서(뒤 → 앞): 꼬리 · 몸 · 머리 · 왼팔 · 오른팔
 */
private val PARTS = listOf(
    Part("otto_m_tail", 0.3359f, 0.7773f),
    Part("otto_m_body", 0.5f, 0.5f),
    Part("otto_m_head", 0.5f, 0.5859f),
    Part("otto_m_arm_l", 0.3359f, 0.6211f),
    Part("otto_m_arm_r", 0.6875f, 0.6211f),
)

/**
 * **옆모습** 부위 — 걸을 때만 쓴다 (09-29 사용자 — 「정면을 보고 팔다리만 흔드는 게 아니라 옆을 보고 실제로 걷는 것처럼」).
 * **기존 마스코트를 옆으로 돌린 그림**(ComfyUI Kontext · `tools/gen_faces.py side_m`)을 `tools/otto_puppet_side.py` 로 잘랐다.
 * 순서(뒤 → 앞): 꼬리 · 뒷다리 · 앞다리 · 뒷팔 · 몸 · 앞팔(소매째)
 */
private val SIDE = listOf(
    Part("otto_side_tail", 0.3828f, 0.7422f),
    Part("otto_side_leg_far", 0.5957f, 0.7959f),
    Part("otto_side_leg_near", 0.4668f, 0.7812f),
    Part("otto_side_arm_far", 0.6836f, 0.6250f),
    Part("otto_side_body", 0.5f, 0.5f),
    Part("otto_side_arm_near", 0.5586f, 0.5713f),
)

/** 옆모습 걷기 — 다리를 앞뒤로 번갈아, 팔은 **소매째 어깨에서** 다리와 반대로 크게 흔들고, 몸은 걸음마다 통통 · 살짝 앞으로 기운다 */
private fun sideWalk(t: Float): PoseOut {
    val k = sin(t * 8f)
    // tail, leg_far, leg_near, arm_far, body, arm_near — 팔은 다리와 반대로
    val a = floatArrayOf(10 * sin(t * 4f), -18 * k, 18 * k, 18 * k, 0f, -20 * k)
    return PoseOut(a, -5 * abs(sin(t * 8f)), tilt = 3f)
}

/**
 * 정면 자세 — 시간 t(초) → (꼬리 · 몸 · 머리 · 왼팔 · 오른팔) 각도 · 몸 위아래 · 기울기 · 좌우 폭.
 * 양수 = 시계 방향. 왼팔은 시계 방향이면 앞발이 올라가고, 오른팔은 반시계(음수)면 올라간다.
 * 원래 팔을 든 자세라 팔은 ±30° 안에서만 움직인다(더 내리면 소매 속 빈자리가 보인다).
 * 식은 `tools/otto_mascot_preview.py` 와 같다 — 한쪽을 바꾸면 다른 쪽도
 */
private fun ottoPose(move: OttoMove, t: Float): PoseOut {
    fun s(x: Float) = sin(x)
    // tail, body, head, arm_l, arm_r
    val a = floatArrayOf(10 * s(t * 3f), 0f, 2.5f * s(t * 1.6f), 3 * s(t * 2f), -3 * s(t * 2f))
    var bob = 2 * s(t * 2f)
    var tilt = 0f
    var sx = 1f
    when (move) {
        OttoMove.IDLE -> {}
        // 손 흔들기 — 오른팔을 흔들며 고개를 살짝 갸웃
        OttoMove.WAVE -> { a[4] = -18 + 22 * s(t * 9f); a[2] = -5f; a[0] = 14 * s(t * 4f) }
        // 가리키기 — 오른팔을 들고 그쪽으로 고개
        OttoMove.POINT -> { a[4] = -28 + 3 * s(t * 3f); a[2] = -6f }
        OttoMove.HOORAY -> { a[3] = 28 + 5 * s(t * 8f); a[4] = -28 - 5 * s(t * 8f); bob = -6 * abs(s(t * 4f)); a[2] = 3 * s(t * 8f) }
        // 정면 걷기(옆모습 그림이 없을 때만) — 통통 튀며 팔을 번갈아
        OttoMove.WALK -> { val k = s(t * 8f); a[3] = 12 * k; a[4] = 12 * k; bob = -4 * abs(k); a[0] = 10 * s(t * 4f) }
        // 폴짝 — 높이 뛰며 두 팔을 번쩍
        OttoMove.JUMP -> { val h = abs(s(t * 5f)); bob = -44 * h; a[3] = 30 * h; a[4] = -30 * h; a[0] = 20 * s(t * 10f); a[2] = -4 * h }
        // 빙글 — 제자리에서 한 바퀴(좌우 폭이 1 → -1 → 1)
        OttoMove.SPIN -> { sx = kotlin.math.cos(t * 2f * Math.PI.toFloat() / 1.1f); bob = -8 * abs(s(t * 3f)) }
        // 춤 — 팔을 번갈아 올리고 몸 · 고개를 좌우로
        OttoMove.DANCE -> {
            val k = s(t * 7f)
            a[3] = 25 * maxOf(k, 0f) - 10 * maxOf(-k, 0f); a[4] = -25 * maxOf(-k, 0f) + 10 * maxOf(k, 0f)
            tilt = 8 * k; bob = -5 * abs(k); a[2] = -6 * k; a[0] = 18 * k
        }
        // 간지러워 — 팔을 오므리고 고개를 기울인 채 부르르
        OttoMove.GIGGLE -> { a[3] = -14f; a[4] = 14f; tilt = 4 * s(t * 40f); a[2] = 8 + 3 * s(t * 30f); bob = -2 * abs(s(t * 20f)); a[0] = 25 * s(t * 12f) }
    }
    return PoseOut(a, bob, tilt, sx)
}

/**
 * 뼈대로 움직이는 오또 — 정사각 틀.
 * @param flip 왼쪽을 볼 때(왼쪽으로 걸을 때 · 왼쪽 물건을 가리킬 때) 좌우를 뒤집는다. 옆모습 그림은 오른쪽을 본다
 */
@Composable
fun OttoPuppet(move: OttoMove, modifier: Modifier = Modifier, flip: Boolean = false, paused: Boolean = false) {
    if (assetId("otto_m_body") == 0) { AssetImage("mascot", modifier); return }
    // 흐르는 시간(초). 화면 검사에서는 한 순간에 멈춘다 · [paused] 면 쉰다(방에 한참 아무도 안 만질 때 · #40)
    val t by produceState(0.35f, move, paused) {
        if (motionFrozen || paused) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    // 걸을 때는 옆모습 조각으로 (그림이 없으면 정면 걷기)
    val side = move == OttoMove.WALK && assetId("otto_side_body") != 0
    val parts = if (side) SIDE else PARTS
    val pose = if (side) sideWalk(t) else ottoPose(move, t)
    val ang = pose.ang
    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = (if (flip) -1f else 1f) * pose.sx
                translationY = pose.bob / 512f * size.height
                rotationZ = if (flip) -pose.tilt else pose.tilt   // 왼쪽을 보면 기우는 방향도 반대로
                transformOrigin = TransformOrigin(0.5f, 0.95f)   // 발밑을 축으로 기운다
            },
    ) {
        parts.forEachIndexed { i, p ->
            Image(
                painterResource(assetId(p.file)), null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = ang[i]
                    transformOrigin = TransformOrigin(p.px, p.py)
                },
            )
        }
    }
}
