package com.example.finalproject_demo

import com.example.finalproject_demo.ui.ParticleField
import com.example.finalproject_demo.ui.motionFrozen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 불 · 물 · 김을 **화면 없이** 돌려 본다 (9/23).
 *
 * ## 왜 생겼나
 *
 * 파티클을 넣은 첫날 **물을 쏘는 순간 앱이 죽었다.** `quench()` 가 물방울을 훑는 도중에
 * 같은 통에 김을 넣어 순회가 깨졌다(`ConcurrentModificationException`).
 * 검사 92개가 전부 초록이었는데도 그랬다.
 *
 * 못 잡은 까닭이 뼈아프다 — 화면 검사(Roborazzi)에서는 그림이 매번 달라지지 않게
 * **파티클을 아예 멈춰 두었다.** 멈춰 둔 것은 터질 수가 없다.
 *
 * 그래서 여기서는 [motionFrozen] 을 **일부러 꺼서** 진짜로 입자를 만들고 부딪히게 한다.
 * `ParticleField` 는 Compose 화면이 필요 없는 보통 클래스라 이렇게 돌릴 수 있다.
 */
class MotionTest {

    private var original = false

    @Before
    fun thaw() {
        // 검사 환경에서는 기본으로 멈춰 있다. 여기서는 진짜로 움직여야 한다
        original = motionFrozen
        motionFrozen = false
    }

    /**
     * 되돌려 놓는다 — 이것을 빼면 뒤에 도는 **화면 검사가 들쏓날쏓하게** 된다.
     * 전역 값이라 한 번 뒤집어 놓으면 그다음 검사의 그림에 무작위 입자가 끼어든다.
     */
    @After
    fun refreeze() {
        motionFrozen = original
    }

    /**
     * **물이 불에 닿는 순간 터지지 않는가** — 앱을 죽였던 바로 그 자리다.
     *
     * 불을 피우고 그 위에 물을 잔뜩 뿌린 뒤 [ParticleField.quench] 를 부른다.
     * 고치기 전에는 여기서 `ConcurrentModificationException` 이 났다.
     */
    @Test
    fun sprayingWaterOnFireDoesNotBlowUp() {
        val f = ParticleField()
        repeat(12) { f.flame(100f, 100f, 30f, 1f) }
        // 불 한가운데에 물을 퍼붓는다 — 여러 방울이 한꺼번에 닿아야 그때 그 상황이 된다
        repeat(12) { f.water(100f, 100f, 3f, -2f, 40f) }
        val before = f.count
        assertTrue("물이 하나도 안 만들어졌다", before > 0)

        // 여기서 터졌다
        f.quench(100f, 100f, 60f, 40f)
        f.advance(1f / 60f)

        // 닿은 물방울은 사라지고 김으로 바뀌므로 개수가 확 줄지는 않는다.
        // 중요한 것은 **터지지 않는 것**이고, 그다음이 물이 실제로 사라졌는가다
        assertTrue("물이 불에 닿았는데 아무 일도 없었다", f.count < before)
    }

    /** 한 바퀴를 길게 돌려도 터지지 않고, 입자가 무한히 쌓이지도 않는가 */
    @Test
    fun aLongBurnStaysBoundedAndNeverThrows() {
        val f = ParticleField()
        repeat(600) {
            f.flame(100f, 100f, 30f, 1f)
            f.water(100f, 100f, 4f, -3f, 40f)
            f.quench(100f, 100f, 60f, 40f)
            f.advance(1f / 60f)
        }
        // 소프트웨어 렌더링(에뮬레이터)에서도 버티도록 상한을 둔다
        assertTrue("입자가 끝없이 쌓인다 (${f.count}개)", f.count <= 260)
    }

    /** 시간이 지나면 다 사라지는가 — 안 사라지면 화면에 찌꺼기가 남는다 */
    @Test
    fun everythingFadesAwayWhenNothingFeedsIt() {
        val f = ParticleField()
        repeat(20) { f.flame(100f, 100f, 30f, 1f) }
        repeat(20) { f.smoke(100f, 100f, 30f) }
        repeat(200) { f.advance(1f / 60f) }   // 3초 남짓
        assertEquals("다 꺼졌는데 입자가 남아 있다", 0, f.count)
    }

    /** 불이 꺼져 갈수록 **입자 수가 줄어든다** — 그림이 작아지는 것과 다른 연출의 핵심이다 */
    @Test
    fun aDyingFireMakesFewerSparksNotSmallerOnes() {
        fun sparksAt(strength: Float): Int {
            val f = ParticleField()
            repeat(40) { f.flame(100f, 100f, 30f, strength) }
            return f.count
        }
        val full = sparksAt(1f)
        val dying = sparksAt(0.2f)
        assertTrue("꺼져 가는 불이 활활 타는 불만큼 입자를 낸다 ($dying vs $full)", dying < full)
        assertEquals("다 꺼진 불에서 입자가 나온다", 0, sparksAt(0f))
    }

    /** 멈춤 스위치가 켜지면 아무것도 안 만든다 — 화면 검사가 이것에 기대고 있다 */
    @Test
    fun frozenMeansNothingIsEverCreated() {
        motionFrozen = true
        val f = ParticleField()
        f.flame(100f, 100f, 30f, 1f)
        f.water(100f, 100f, 3f, -2f, 40f)
        f.steam(100f, 100f, 30f)
        f.smoke(100f, 100f, 30f)
        f.burst(100f, 100f, 30f)
        assertEquals("멈춰 있는데 입자가 생겼다 — 화면 검사가 매번 달라진다", 0, f.count)
    }
}
