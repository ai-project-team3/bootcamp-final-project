package com.example.finalproject_demo.ui.shell

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The approved PoC's clock; it deliberately stops while the bundled introduction speaks. */
internal object OpeningMotion {
    const val duration = 9800f
    const val voiceAt = 2920f
    data class Point(val x: Float, val y: Float, val scale: Float)
    data class Hop(val start: Float, val end: Float, val destination: Float, val height: Float)
    val hops = listOf(
        Hop(4480f, 5700f, .44f, 108f), Hop(5920f, 6520f, .64f, 38f),
        Hop(6670f, 7230f, .83f, 29f), Hop(7380f, 7920f, 1f, 21f),
    )
    fun pull(distance: Float) = 220f * (1f - exp(-distance.coerceAtLeast(0f) / 220f))
    fun canRelease(distance: Float) = pull(distance) >= 28f
    fun progress(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)
    fun smooth(p: Float) = p * p * (3f - 2f * p)
    fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
    fun advance(time: Float, delta: Float, voiceDone: Boolean): Float =
        (time + delta.coerceAtLeast(0f)).coerceAtMost(if (voiceDone) duration else voiceAt)

    /** A single, intact sprite moves about its feet; no face/limb mesh or nonuniform scale. */
    fun cat(time: Float, start: Point, end: Point, heightScale: Float = 1f): Point {
        var from = 0f
        for (hop in hops) {
            if (time < hop.start) break
            if (time <= hop.end) {
                val p = progress(time, hop.start, hop.end)
                val distance = mix(from, hop.destination, p)
                return Point(mix(start.x, end.x, distance),
                    mix(start.y, end.y, distance) - sin(PI.toFloat() * p) * hop.height * heightScale,
                    mix(start.scale, end.scale, mix(from, hop.destination, smooth(p))))
            }
            from = hop.destination
        }
        return Point(mix(start.x, end.x, from), mix(start.y, end.y, from), mix(start.scale, end.scale, from))
    }

    fun signY(t: Float, start: Float, down: Float, up: Float): Float {
        if (t >= up) return -700f * smooth(progress(t, up, up + 850f))
        val p = progress(t, start, down)
        return when {
            p < .63f -> mix(-610f, 15f, smooth(p / .63f))
            p < .8f -> mix(15f, -7f, (p - .63f) / .17f)
            else -> mix(-7f, 0f, (p - .8f) / .2f)
        }
    }
}
