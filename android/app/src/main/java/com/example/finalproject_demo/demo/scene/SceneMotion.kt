package com.example.finalproject_demo.demo.scene

import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The living background (10-05 · `docs/배경_조각_목록.md` §6-3 — 치영): kit pieces that keep moving by rule.
 * No LLM, no server call — a pure function of the placed scene and the time, so `SceneMotionTest` runs it on the JVM.
 *
 *  - [MotionKind.SWAY]    trees · bushes · grass · flowers lean with one shared wind. **The feet never move** —
 *                         the drawing bends the picture (top leans most), it does not rotate it
 *  - [MotionKind.DRIFT]   clouds cross the sky slowly and come back from the other side
 *  - [MotionKind.FLUTTER] butterflies circle a flower in a figure of eight, then fly on to the next one
 *  - [MotionKind.BOB]     balloons · kite tug on their string
 *  - [MotionKind.GLOW]    the sun breathes
 *
 * The wind is **one field** over the stage ([wind]): a breeze plus a gust that travels left to right, so the far
 * trees lean a moment before the near grass does — pieces answer the same air instead of each wobbling alone.
 *
 * Which piece moves how is a table by picture name ([MOTION_BY_RES]), next to the piece tags in `SceneKit.kt`
 * rather than inside them for now (the prototype — once settled the column moves into [KitPiece]).
 */
enum class MotionKind { SWAY, DRIFT, FLUTTER, BOB, GLOW }

/**
 * @param amount SWAY: how far the top leans at wind 1, as a share of the piece's height ·
 *   BOB: degrees of swing · others unused
 * @param speed how quickly the piece answers (small light things are quicker)
 * @param stiff SWAY: the bend curve — 1 leans like a ruler, 2 keeps the lower half almost straight (a trunk)
 */
data class MotionSpec(val kind: MotionKind, val amount: Float = 1f, val speed: Float = 1f, val stiff: Float = 1.5f)

val MOTION_BY_RES: Map<String, MotionSpec> = mapOf(
    "kit_common_round_tree" to MotionSpec(MotionKind.SWAY, 0.035f, 0.7f, 2.0f),
    "kit_park_tree_row" to MotionSpec(MotionKind.SWAY, 0.014f, 0.6f, 1.8f),
    "kit_common_bush" to MotionSpec(MotionKind.SWAY, 0.040f, 1.0f, 1.4f),
    "kit_common_grass" to MotionSpec(MotionKind.SWAY, 0.130f, 1.5f, 1.3f),
    "kit_common_tulip" to MotionSpec(MotionKind.SWAY, 0.100f, 1.3f, 1.5f),
    "kit_common_daisy" to MotionSpec(MotionKind.SWAY, 0.100f, 1.4f, 1.5f),
    "kit_common_cloud" to MotionSpec(MotionKind.DRIFT),
    "kit_common_butterfly" to MotionSpec(MotionKind.FLUTTER),
    "kit_park_balloons" to MotionSpec(MotionKind.BOB, 4f, 0.8f),
    "kit_park_kite" to MotionSpec(MotionKind.BOB, 7f, 1.2f),
    "kit_common_sun" to MotionSpec(MotionKind.GLOW),
)

/** Where a butterfly rests — flowers, and for want of a flower the top of a bush or a tuft of grass */
private val FLOWERS = setOf("kit_common_tulip", "kit_common_daisy")
private val GREENS = setOf("kit_common_bush", "kit_common_grass")

/**
 * How one piece is drawn this instant, on top of where the layout put it.
 *
 * @param dx / [dy] px the whole piece moves
 * @param bend px the **top edge** leans sideways while the feet stay (SWAY) — right is positive
 * @param stiff the bend curve (see [MotionSpec.stiff])
 * @param tilt degrees added to the layout's tilt
 * @param scaleX wings folding (butterfly) · [scaleY] with it for the sun's breath
 */
data class PieceMotion(
    val dx: Float = 0f, val dy: Float = 0f,
    val bend: Float = 0f, val stiff: Float = 1.5f,
    val tilt: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
) {
    val still: Boolean get() = this == NONE

    companion object { val NONE = PieceMotion() }
}

/**
 * The wind at time [t] (seconds) over the stage, [xn] = x / stage width. About -0.3 … 1.7: a breeze that mostly
 * blows to the right (the mean lean), ripples on top, and a gust travelling left → right every nine seconds or so.
 */
fun wind(t: Double, xn: Float): Float {
    val x = xn.toDouble()
    val breeze = 0.30 * sin(0.9 * t - 2.4 * x) + 0.18 * sin(1.7 * t - 4.1 * x + 1.3) + 0.10 * sin(2.9 * t - 7.0 * x + 0.7)
    val gust = max(0.0, sin(0.7 * t - 1.6 * x + 0.4)).pow(4)
    return (0.25 + breeze + 0.9 * gust).toFloat()
}

/** Seconds a butterfly stays over one flower · seconds it takes to fly to the next */
private const val HOVER_S = 6.0
private const val GLIDE_S = 2.6

/**
 * The motions of one placed scene. Build once per scene (it sorts out which flowers each butterfly visits),
 * then ask [of] every frame.
 */
class SceneMotions(private val scene: KitScene, private val f: SceneFrame) {
    private class Stop(val x: Float, val y: Float)

    private val index = IdentityHashMap<PlacedPiece, Int>().apply { scene.pieces.forEachIndexed { i, p -> put(p, i) } }

    /** butterfly → the points it visits, the first being where the layout put it */
    private val routes = IdentityHashMap<PlacedPiece, List<Stop>>()

    init {
        // where a butterfly rests: flowers first (the big foreground one too — it hovers over the bloom, behind it),
        // then the tops of bushes and grass. On a phone the ground strip is narrow and often holds no flower at all
        val ground = scene.pieces.filter { it.fade == 0f }
        val perches = (ground.filter { it.piece.res in FLOWERS } + ground.filter { it.piece.res in GREENS }).sortedBy { it.x }
        val butterflies = scene.pieces.filter { MOTION_BY_RES[it.piece.res]?.kind == MotionKind.FLUTTER }
        butterflies.forEachIndexed { b, p ->
            val over = perches.map { fl -> Stop(fl.x.coerceIn(p.w, f.w - p.w), max(f.top + p.h, fl.box.t - p.h * 0.35f)) } +
                // and where the other butterflies started, so there is somewhere to go even on a bare lawn
                butterflies.filter { it !== p }.map { Stop(it.x, it.y) }
            // each butterfly starts its round at a different stop, and the second one goes the other way
            val shift = if (over.isEmpty()) 0 else (b * max(1, over.size / max(1, butterflies.size))) % over.size
            val round = (over.drop(shift) + over.take(shift)).let { if (b % 2 == 1) it.reversed() else it }
            routes[p] = listOf(Stop(p.x, p.y)) + round
        }
    }

    /** A steady number in 0 … 1 for this piece — its own phase, so two tulips never move as one */
    private fun phase(p: PlacedPiece): Double {
        val i = index[p] ?: 0
        return ((i * 0.6180339887 + scene.seed * 0.137) % 1.0 + 1.0) % 1.0
    }

    fun of(p: PlacedPiece, t: Double): PieceMotion {
        val spec = MOTION_BY_RES[p.piece.res] ?: return PieceMotion.NONE
        val ph = phase(p) * 2 * PI
        return when (spec.kind) {
            MotionKind.SWAY -> {
                // the shared wind where this piece stands, plus a small shiver of its own; far pieces answer less
                val own = 0.15 * sin(spec.speed * 3.1 * t + ph)
                val far = 1f - p.fade * 0.5f
                val lean = (wind(t * spec.speed.coerceAtMost(1f).toDouble() + ph * 0.05, p.x / f.w) + own).toFloat()
                PieceMotion(bend = p.h * spec.amount * lean * far, stiff = spec.stiff)
            }
            MotionKind.DRIFT -> {
                // leaves by the right edge and comes back from the left; a crossing takes about two minutes
                val v = f.w * 0.010 * (0.7 + 0.6 * phase(p))
                val span = (f.w + p.w).toDouble()
                val x = ((p.x + p.w / 2 + v * t) % span + span) % span - p.w / 2
                PieceMotion(dx = (x - p.x).toFloat(), dy = (p.h * 0.04 * sin(0.3 * t + ph)).toFloat())
            }
            MotionKind.FLUTTER -> flutter(p, t, ph)
            MotionKind.BOB -> {
                val w = wind(t, p.x / f.w)
                val swing = sin(spec.speed * t + ph) + 0.4 * sin(spec.speed * 2.3 * t + ph * 1.7)
                PieceMotion(
                    dx = (p.h * 0.03 * swing).toFloat() + p.h * 0.03f * w,
                    dy = (p.h * 0.03 * sin(spec.speed * 0.7 * t + ph)).toFloat(),
                    tilt = (spec.amount * 0.7 * swing).toFloat() + spec.amount * 0.4f * w,
                )
            }
            MotionKind.GLOW -> {
                val s = (1.0 + 0.025 * sin(0.8 * t)).toFloat()
                PieceMotion(tilt = (3.0 * sin(0.25 * t)).toFloat(), scaleX = s, scaleY = s)
            }
        }
    }

    private fun flutter(p: PlacedPiece, t: Double, ph: Double): PieceMotion {
        val stops = routes[p] ?: return PieceMotion.NONE
        // where the round has got to: hover over stop k, then glide to k + 1
        val leg = HOVER_S + GLIDE_S
        val tt = t + ph / (2 * PI) * 2.0                      // the two butterflies do not set off together
        val k = ((tt / leg).toLong() % stops.size).toInt()
        val into = tt % leg
        val a = stops[k]
        val b = stops[(k + 1) % stops.size]
        var cx = a.x
        var cy = a.y
        if (into > HOVER_S && stops.size > 1) {
            val e = ((into - HOVER_S) / GLIDE_S).let { it * it * (3 - 2 * it) }.toFloat()   // ease in and out
            val dist = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
            cx = a.x + (b.x - a.x) * e
            // rises on the way — a butterfly does not fly in a straight line
            cy = a.y + (b.y - a.y) * e - min(dist * 0.18f, p.h * 2.5f) * sin(PI * e).toFloat()
        }
        // the figure of eight never stops, so hover and glide join without a jump
        val th = 1.1 * t + ph
        val r = p.h * 0.55f
        val x = cx + r * sin(th).toFloat()
        val y = (cy + r * 0.5f * sin(2 * th).toFloat()).coerceAtLeast(f.top + p.h / 2)
        return PieceMotion(
            dx = x - p.x, dy = y - p.y,
            tilt = (10.0 * cos(th)).toFloat(),
            scaleX = (0.35 + 0.65 * abs(cos(2 * PI * 2.6 * t + ph))).toFloat(),     // wings fold about the body
        )
    }
}
