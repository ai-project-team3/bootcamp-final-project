package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/*
 * 움직임 — 불 · 물 · 연기 같은 **계산으로 만드는 것**과, 만졌을 때의 손맛을 한곳에 모은다 (9/23).
 *
 * 왜 이 파일이 생겼나
 *   책 4쪽의 불 끄기 미션이 **정지 그림 세 장이 작아지기만** 했다. 불은 타지 않고 물은 뿌려지지 않았다.
 *   그림을 더 그려서 될 일이 아니다 — 불꽃과 물줄기는 화가가 그리는 게 아니라 **매 프레임 계산해서**
 *   점을 찍는 것이다. 그래서 게임 엔진을 가져오지 않고도 되고, 팀에 애니메이터가 없어도 된다.
 *
 * 무엇이 들어 있나
 *   1. [ParticleField]  — 불 · 물 · 김 · 연기 · 반짝임을 한 통에 담아 한 번에 그린다
 *   2. [breathing]      — 가만히 있는 등장인물이 숨을 쉰다. "죽은 그림" 느낌을 없애는 가장 싼 방법
 *   3. [pressPop]       — 누르면 납작해졌다 통 튀어 오른다 (눌림과 늘어남)
 *
 * ⚠️ 아이용이라는 것
 *   불은 **둥글고 밝은 만화 불**이다. 화면을 어둡게 하거나 연기로 시야를 가리지 않는다.
 *   설계의 「미션 무실패」가 깨지면 안 된다 — 무서우면 아이가 손을 못 댄다.
 */

/**
 * 검사에서 움직임을 멈춘다.
 *
 * Roborazzi 는 화면을 그림으로 찍어 기준 그림과 픽셀을 대조하는데, 애니메이션이 돌면
 * **찍을 때마다 그림이 달라져** 매번 실패한다. 검사가 시작할 때 이것을 켜면
 * 숨쉬기 · 깜빡임 · 파티클이 모두 첫 프레임에서 멈춘다.
 */
// 검사 파일을 고치지 않고 **스스로 알아차린다** — Robolectric 은 `Build.FINGERPRINT` 가 "robolectric" 이다.
// 누군가 새 화면 검사를 만들 때 이것을 켜는 것을 잊어도 깨지지 않는다. 손으로 바꿀 수도 있게 `var` 로 둔다.
var motionFrozen: Boolean = runCatching {
    android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
}.getOrDefault(false)

// ── 1. 파티클 ──────────────────────────────────────────────────

/** 입자 종류 — 그리는 법과 물리가 조금씩 다르다 */
enum class Puff {
    /** 불꽃 — 위로 올라가며 노랑 → 주황 → 빨강으로 식는다 */
    FIRE,

    /** 물방울 — 뿜은 방향으로 날아가다 중력에 떨어진다 */
    WATER,

    /** 김 — 물이 불에 닿은 자리에서 하얗게 피어오른다 */
    STEAM,

    /** 연기 — 다 끈 뒤 천천히 흩어진다 */
    SMOKE,

    /** 반짝임 — 미션을 끝냈을 때 */
    SPARK,
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val kind: Puff,
    val seed: Float,
    /** 불꽃이 **돌아오려는 가운데 축** (9/23). 위로 갈수록 여기로 모여 끝이 뾰족해진다 */
    val home: Float = 0f,
)

/**
 * 입자를 담고 매 프레임 움직여 그린다.
 *
 * 그리는 쪽은 [tick] 을 읽기만 하면 된다 — 프레임마다 값이 바뀌므로 Compose 가 다시 그린다.
 */
class ParticleField {
    private val items = ArrayList<Particle>(256)

    /** 프레임 번호. Canvas 안에서 읽으면 매 프레임 다시 그려진다 */
    var tick by mutableIntStateOf(0)
        private set

    val count: Int get() = items.size

    /** 너무 많이 쌓이면 오래된 것부터 버린다 — 소프트웨어 렌더링(에뮬레이터)에서 느려지지 않게 */
    private fun trim() {
        while (items.size > MAX) items.removeAt(0)
    }

    fun clear() {
        items.clear()
    }

    /**
     * 불꽃을 피운다.
     *
     * @param strength 0~1. 불이 꺼져 갈수록 줄어들어 **입자 수 자체가 줄어든다** —
     *   그림이 작아지는 것과 꺼져 가는 것은 눈에 아주 다르게 보인다
     */
    fun flame(cx: Float, cy: Float, r: Float, strength: Float) {
        if (motionFrozen) return
        if (strength <= 0f) return
        val rate = 4.5f * strength
        val n = rate.toInt() + if (Random.nextFloat() < rate % 1f) 1 else 0
        repeat(n) {
            // 밑변을 **좁게**, 그리고 가운데로 몰리게 잡는다 (9/23).
            // 고르게 뿌리면 불이 옆으로 퍼진 얼룩처럼 보인다 — 불은 밑이 좁고 위로 갈수록 모인다.
            // 난수를 둘 더해 평균을 내면 가운데가 두꺼운 삼각 분포가 된다
            val bias = (Random.nextFloat() + Random.nextFloat()) / 2f - 0.5f
            items += Particle(
                x = cx + bias * r * 0.55f,
                y = cy + r * (0.20f + Random.nextFloat() * 0.18f),
                vx = (Random.nextFloat() - 0.5f) * r * 0.30f,
                vy = -r * (2.1f + Random.nextFloat() * 1.5f),
                life = 1f,
                maxLife = 0.42f + Random.nextFloat() * 0.30f,
                size = r * (0.17f + Random.nextFloat() * 0.17f) * (0.6f + 0.4f * strength),
                kind = Puff.FIRE,
                seed = Random.nextFloat() * 6.28f,
                home = cx,
            )
        }
        trim()
    }

    /**
     * 물을 뿜는다 — **손가락이 움직인 방향과 속도**를 그대로 받는다.
     * 살살 문지르면 졸졸 흐르고 빠르게 그으면 확 뿜어진다. 이 차이가 손맛을 만든다.
     */
    fun water(x: Float, y: Float, dx: Float, dy: Float, scale: Float) {
        if (motionFrozen) return
        val speed = kotlin.math.hypot(dx, dy).coerceIn(0f, scale * 2.5f)
        repeat(4) {
            items += Particle(
                x = x + (Random.nextFloat() - 0.5f) * scale * 0.25f,
                y = y + (Random.nextFloat() - 0.5f) * scale * 0.25f,
                vx = dx * 6f + (Random.nextFloat() - 0.5f) * (scale * 1.2f + speed),
                vy = dy * 6f + (Random.nextFloat() - 0.5f) * scale * 0.8f - scale * 0.4f,
                life = 1f,
                maxLife = 0.30f + Random.nextFloat() * 0.25f,
                size = scale * (0.07f + Random.nextFloat() * 0.06f),
                kind = Puff.WATER,
                seed = 0f,
            )
        }
        trim()
    }

    /**
     * **소방 호스 줄기** (9/23 요청).
     *
     * [water] 는 손가락이 움직인 만큼만 흩뿌려서, 살살 문지르면 물이 찔끔 나왔다.
     * 소방차 호스는 **노즐에서 한 줄기로 쭉** 뻗고, 겨눈 곳까지 날아가 부딪혀 흩어진다.
     * 그래서 이쪽은 손 움직임이 아니라 **노즐 → 겨눈 곳** 을 보고 뿜는다.
     *
     * @param nx 노즐 자리 (호스 그림 끝)
     * @param tx 겨눈 자리 (손가락)
     */
    fun jet(nx: Float, ny: Float, tx: Float, ty: Float, scale: Float) {
        if (motionFrozen) return
        val dx = tx - nx
        val dy = ty - ny
        val dist = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / dist
        val uy = dy / dist
        // 겨눈 곳을 지나 조금 더 날아가야 "뿜는" 것으로 보인다. 너무 느리면 물이 떨어지기만 한다
        val speed = (dist * 7.5f).coerceIn(scale * 22f, scale * 46f)
        repeat(7) {
            // 노즐에서 멀어질수록 퍼진다 — 한 점에서 나오면 실 같아 보인다
            val spread = (Random.nextFloat() - 0.5f) * 0.20f
            val cs = kotlin.math.cos(spread)
            val sn = kotlin.math.sin(spread)
            val vx = (ux * cs - uy * sn) * speed * (0.82f + Random.nextFloat() * 0.36f)
            val vy = (ux * sn + uy * cs) * speed * (0.82f + Random.nextFloat() * 0.36f)
            items += Particle(
                x = nx + (Random.nextFloat() - 0.5f) * scale * 0.18f,
                y = ny + (Random.nextFloat() - 0.5f) * scale * 0.18f,
                vx = vx,
                vy = vy,
                life = 1f,
                maxLife = 0.22f + Random.nextFloat() * 0.16f,
                size = scale * (0.13f + Random.nextFloat() * 0.10f),
                kind = Puff.WATER,
                seed = 0f,
            )
        }
        trim()
    }

    /** 물이 불에 닿은 자리 — 치익, 하얀 김 */
    fun steam(x: Float, y: Float, scale: Float, n: Int = 3) {
        if (motionFrozen) return
        repeat(n) {
            items += Particle(
                x = x + (Random.nextFloat() - 0.5f) * scale * 0.5f,
                y = y,
                vx = (Random.nextFloat() - 0.5f) * scale * 0.7f,
                vy = -scale * (0.7f + Random.nextFloat() * 0.6f),
                life = 1f,
                maxLife = 0.6f + Random.nextFloat() * 0.5f,
                size = scale * (0.18f + Random.nextFloat() * 0.16f),
                kind = Puff.STEAM,
                seed = 0f,
            )
        }
        trim()
    }

    /** 다 끈 뒤 남는 연기 한 줄기 */
    fun smoke(x: Float, y: Float, scale: Float) {
        if (motionFrozen) return
        items += Particle(
            x = x + (Random.nextFloat() - 0.5f) * scale * 0.4f,
            y = y,
            vx = (Random.nextFloat() - 0.5f) * scale * 0.35f,
            vy = -scale * (0.5f + Random.nextFloat() * 0.4f),
            life = 1f,
            maxLife = 1.1f + Random.nextFloat() * 0.7f,
            size = scale * (0.22f + Random.nextFloat() * 0.2f),
            kind = Puff.SMOKE,
            seed = Random.nextFloat() * 6.28f,
        )
        trim()
    }

    /** 해냈을 때 사방으로 터지는 반짝임 */
    fun burst(x: Float, y: Float, scale: Float, n: Int = 14) {
        if (motionFrozen) return
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val sp = scale * (1.2f + Random.nextFloat() * 1.4f)
            items += Particle(
                x = x, y = y,
                vx = kotlin.math.cos(a) * sp,
                vy = kotlin.math.sin(a) * sp - scale * 0.4f,
                life = 1f,
                maxLife = 0.5f + Random.nextFloat() * 0.4f,
                size = scale * (0.07f + Random.nextFloat() * 0.07f),
                kind = Puff.SPARK,
                seed = 0f,
            )
        }
        trim()
    }

    /**
     * 물방울이 불에 닿았는지 본다. 닿았으면 김으로 바꾼다.
     *
     * **눈에 보이는 것만 바꾼다** — 불이 얼마나 꺼졌는지(미션 성공 판정)는 원래대로
     * 문지른 양이 정한다. 그래야 화면 없이 도는 검사가 그대로 돈다.
     */
    fun quench(cx: Float, cy: Float, r: Float, scale: Float) {
        if (motionFrozen) return
        // ⚠️ **훑는 도중에 넣지 않는다** (9/23). 처음에 물방울을 찾으면서 바로 그 자리에 김을 넣었는데,
        //    김도 같은 통에 들어가므로 순회가 깨져 **물을 콰는 순간 앱이 죽었다**
        //    (ConcurrentModificationException). 지울 것을 먼저 모아 놓고, 훑기를 마친 뒤에 넣는다.
        var found = 0
        var w = 0
        val steamAt = ArrayList<Float>(6)
        for (i in items.indices) {
            val p = items[i]
            if (p.kind != Puff.WATER) {
                items[w++] = p
                continue
            }
            val dx = p.x - cx
            val dy = p.y - cy
            if (dx * dx + dy * dy < r * r) {
                if (found++ < 3) { steamAt += p.x; steamAt += p.y }
                // 이 물방울은 여기서 사라진다 — 옮기지 않으므로 다음 칸이 그 자리를 덮는다
            } else {
                items[w++] = p
            }
        }
        while (items.size > w) items.removeAt(items.size - 1)
        var k = 0
        while (k < steamAt.size) {
            steam(steamAt[k], steamAt[k + 1], scale, 1)
            k += 2
        }
    }

    /** 한 프레임만큼 움직인다 */
    fun advance(dt: Float) {
        var i = 0
        while (i < items.size) {
            val p = items[i]
            p.life -= dt / p.maxLife
            if (p.life <= 0f) {
                items.removeAt(i)
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            when (p.kind) {
                // 불은 올라가며 일렁인다 — 좌우 흔들림이 없으면 그냥 점이 떠오르는 것으로 보인다
                Puff.FIRE -> {
                    p.vy *= 0.94f
                    // 가운데 축으로 **당겨 온다** — 이 한 줄이 불끝을 뾰족하게 만든다.
                    // 없으면 입자가 처음 흩어진 자리 그대로 위로 올라가 폭이 안 줄어든다
                    p.vx += (p.home - p.x) * 7.5f * dt
                    p.vx *= 0.95f
                    // 일렁임은 살짝만. 세면 다시 옆으로 번진다
                    p.x += sin(p.seed + p.life * 11f) * p.size * 0.30f * dt * 60f
                }
                // 물은 중력에 떨어지고 공기 저항으로 느려진다
                Puff.WATER -> {
                    // 중력이 세면 줄기가 노즐 앞에서 뚝 떨어진다. 겨눈 곳까지는 곧게 가야 한다
                    p.vy += 1500f * dt
                    p.vx *= 0.985f
                }
                Puff.STEAM, Puff.SMOKE -> {
                    p.vy *= 0.97f
                    p.x += sin(p.seed + p.life * 4f) * p.size * 0.5f * dt * 60f
                }
                Puff.SPARK -> {
                    p.vy += 900f * dt
                    p.vx *= 0.97f
                }
            }
            i++
        }
        tick++
    }

    /**
     * 그린다. 부르는 쪽은 Canvas 안에서 `field.tick` 을 한 번 읽어 주면 된다.
     *
     * @param kinds 이 종류만 그린다. **불은 흔적 그림 뒤에, 물과 김은 앞에** 그려야 한다 —
     *   물이 불 뒤로 숨으면 뿌리는 느낌이 안 난다. null 이면 전부.
     */
    fun draw(scope: DrawScope, kinds: Set<Puff>? = null) {
        items.forEach { p ->
            if (kinds != null && p.kind !in kinds) return@forEach
            val t = p.life.coerceIn(0f, 1f)
            when (p.kind) {
                Puff.FIRE -> {
                    // 식어 가는 색 — 흰노랑 → 노랑 → 주황 → 빨강.
                    // 어두운 빨강까지 가지 않는다. 아이 화면이라 불이 밝고 동그래야 한다
                    val c = when {
                        t > 0.78f -> Color(0xFFFFF3B0)
                        t > 0.55f -> Color(0xFFFFD34E)
                        t > 0.30f -> Color(0xFFFF9A3C)
                        else -> Color(0xFFFF6B4A)
                    }
                    val r = p.size * (0.40f + t * 0.65f)
                    // 둘레를 부드럽게 — 딱 떨어지는 원이면 종이 조각처럼 보인다.
                    // 다만 번짐을 크게 주면 **그것만으로 불이 옆으로 퍼져 보인다** (9/23)
                    scope.drawCircle(c.copy(alpha = 0.16f * t), r * 1.25f, Offset(p.x, p.y))
                    scope.drawCircle(c.copy(alpha = 0.90f * t), r, Offset(p.x, p.y))
                }
                Puff.WATER -> {
                    // 세 겹으로 겹쳐 굵기를 낸다 — 옅은 물보라 · 물빛 · 흰 심지
                    scope.drawCircle(Color(0xFFBFEBFF).copy(alpha = 0.30f * t), p.size * 1.9f, Offset(p.x, p.y))
                    scope.drawCircle(Color(0xFF7FD4FF).copy(alpha = 0.92f * t), p.size, Offset(p.x, p.y))
                    scope.drawCircle(Color.White.copy(alpha = 0.75f * t), p.size * 0.52f, Offset(p.x - p.size * 0.2f, p.y - p.size * 0.2f))
                }
                Puff.STEAM -> {
                    val r = p.size * (0.7f + (1f - t) * 1.5f)
                    scope.drawCircle(Color.White.copy(alpha = 0.55f * t), r, Offset(p.x, p.y))
                }
                Puff.SMOKE -> {
                    val r = p.size * (0.7f + (1f - t) * 1.8f)
                    scope.drawCircle(Color(0xFFBFB6AE).copy(alpha = 0.40f * t), r, Offset(p.x, p.y))
                }
                Puff.SPARK -> {
                    scope.drawCircle(Color(0xFFFFE07A).copy(alpha = t), p.size, Offset(p.x, p.y))
                }
            }
        }
    }

    private companion object {
        /** 소프트웨어 렌더링(에뮬레이터)에서도 버티는 선 */
        const val MAX = 220
    }
}

/**
 * 매 프레임 스스로 움직이는 [ParticleField] 를 만든다.
 *
 * `withFrameNanos` 로 화면 주사에 맞춰 돈다 — 타이머로 돌리면 프레임과 어긋나 끊겨 보인다.
 */
@Composable
fun rememberParticleField(): ParticleField {
    val field = remember { ParticleField() }
    LaunchedEffect(Unit) {
        if (motionFrozen) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                field.advance(dt)
            }
        }
    }
    return field
}

// ── 2. 숨쉬기 ──────────────────────────────────────────────────

/**
 * 가만히 있는 등장인물이 **숨을 쉰다.**
 *
 * 정지 그림 한 장을 살아 보이게 하는 가장 싼 방법이다. 크기를 아주 조금(1% 남짓)만 바꾸는데,
 * 발이 뜨지 않게 **아래쪽을 축으로** 늘였다 줄인다. 가로는 반대로 줄여 부피가 유지되는 것처럼 보이게 한다.
 *
 * @param seed 인물마다 조금씩 어긋나게 — 여럿이 똑같이 숨 쉬면 기계처럼 보인다
 */
@Composable
fun Modifier.breathing(periodMs: Int = 2600, amount: Float = 0.014f, seed: Int = 0): Modifier {
    if (motionFrozen) return this
    val inf = rememberInfiniteTransition(label = "breath")
    val t by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(periodMs, delayMillis = (seed * 137) % 700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    return this.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleY = 1f + amount * t
        scaleX = 1f - amount * 0.45f * t
    }
}

// ── 3. 손맛 ────────────────────────────────────────────────────

/**
 * 누르면 **납작해졌다 통 튀어 오른다** — 애니메이션 12원칙의 「눌림과 늘어남」.
 *
 * 손을 떼는 순간 스프링이 1을 지나쳐 살짝 부풀었다 돌아온다. 그 지나침(overshoot)이 없으면
 * 눌린 것이 그냥 제자리로 돌아올 뿐이라 **만졌다는 느낌이 안 난다.**
 */
@Composable
fun Modifier.pressPop(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.85f)
            val p = press.value
            scaleX = 1f + 0.07f * p
            scaleY = 1f - 0.09f * p
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    scope.launch { press.animateTo(1f, tween(80)) }
                    Sfx.play(Sound.POP)
                    val released = tryAwaitRelease()
                    scope.launch {
                        press.animateTo(
                            0f,
                            spring(dampingRatio = 0.34f, stiffness = 760f),
                            initialVelocity = -3.4f,
                        )
                    }
                    if (released) onClick()
                },
            )
        }
}

/**
 * 눈을 **불규칙하게** 깜빡이게 하는 시계.
 *
 * 일정한 간격으로 깜빡이면 기계처럼 보인다. 사람은 2~6초 사이 아무 때나 깜빡이고,
 * 가끔 두 번 연달아 깜빡인다.
 *
 * @return 지금 눈을 감고 있는가
 */
@Composable
fun rememberBlink(key: Any?): Boolean {
    var closed by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        if (motionFrozen) return@LaunchedEffect
        while (true) {
            delay(2200L + Random.nextLong(3600))
            closed = 1; delay(110); closed = 0
            if (Random.nextFloat() < 0.22f) {   // 가끔 두 번 연달아
                delay(140); closed = 1; delay(100); closed = 0
            }
        }
    }
    return closed == 1
}
