package com.example.finalproject_demo

import com.example.finalproject_demo.demo.scene.groundOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * #257 (10-07 device) — the felt floor under a generated background was always the park's grass. Read from the picture:
 * the median of its bottom band, a little darker. Four small pictures, drawn here: snow, a wooden floor, sand, sky only.
 */
class GroundColorTest {
    private val w = 48
    private val h = 27

    /** sky to [split] of the height, then the ground colour — each pixel jittered like felt */
    private fun picture(sky: Int, ground: Int?, split: Float = 0.7f, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        fun jitter(c: Int): Int {
            fun ch(v: Int) = (v + rnd.nextInt(-8, 9)).coerceIn(0, 255)
            return (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
        return IntArray(w * h) { i ->
            val y = i / w
            // sky alone: a gradient to the bottom, no flat ground
            if (ground == null) jitter(blend(sky, 0xFFB0D8F0.toInt(), y / h.toFloat())) else jitter(if (y < h * split) sky else ground)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun dist(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    /** the ground colour, 8 % darker — what the floor should be */
    private fun expected(c: Int): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) * 0.92f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test
    fun theFloorTakesThePicturesGround() {
        val sky = 0xFF9CC8EC.toInt()
        for ((name, ground) in listOf("snow" to 0xFFF2F4F8.toInt(), "wooden floor" to 0xFFB07A48.toInt(), "sand" to 0xFFE8D2A0.toInt())) {
            val g = groundOf(picture(sky, ground), w, h)
            assertTrue("$name: ${Integer.toHexString(g.color)}", dist(g.color, expected(ground)) < 10.0)
            assertTrue("$name has its own ground", g.hasGround)
        }
    }

    @Test
    fun aSkyOnlyPictureHasNoGround() {
        val g = groundOf(picture(0xFF5A8FD0.toInt(), null), w, h)
        assertFalse("a gradient to the bottom is not a ground", g.hasGround)
    }

    @Test
    fun notTheParkGrassForASnowfield() {
        val park = 0xFF78B254.toInt()
        val g = groundOf(picture(0xFF9CC8EC.toInt(), 0xFFF2F4F8.toInt()), w, h)
        assertTrue(dist(g.color, park) > 100.0)
        assertEquals(0xFF, (g.color ushr 24))
    }
}
