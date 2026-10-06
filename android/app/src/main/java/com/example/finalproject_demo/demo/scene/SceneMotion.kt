package com.example.finalproject_demo.demo.scene

import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
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
 *  - [MotionKind.GLOW]    the sun breathes · stars twinkle
 *  - [MotionKind.SWIM]    fish: a butterfly's round (coral to coral) without the wing beat, turned to where they swim
 *  - [MotionKind.RISE]    bubbles rise from the sea floor and wobble on the way up
 *  - visitors             a bird that is **not in the layout** flies in, sits on a tree or a bench for a while
 *                         and flies on ([SceneMotions.visitors]) — the still scene and its screenshots have no bird
 *
 * The wind is **one field** over the stage ([wind]): a breeze plus a gust that travels left to right, so the far
 * trees lean a moment before the near grass does — pieces answer the same air instead of each wobbling alone.
 *
 * Which piece moves how is a table by picture name ([MOTION_BY_RES]), next to the piece tags in `SceneKit.kt`
 * rather than inside them for now (the prototype — once settled the column moves into [KitPiece]).
 */
enum class MotionKind { SWAY, DRIFT, FLUTTER, BOB, GLOW, SWIM, RISE }

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
    // 공룡 나라 (10-06)
    "kit_dino_palm_tree" to MotionSpec(MotionKind.SWAY, 0.050f, 0.6f, 2.2f),      // fronds swing, the trunk hardly bends
    "kit_dino_jungle" to MotionSpec(MotionKind.SWAY, 0.014f, 0.6f, 1.8f),
    "kit_dino_fern" to MotionSpec(MotionKind.SWAY, 0.110f, 1.4f, 1.3f),
    "kit_dino_hibiscus" to MotionSpec(MotionKind.SWAY, 0.090f, 1.3f, 1.5f),
    "kit_dino_big_leaf" to MotionSpec(MotionKind.SWAY, 0.070f, 0.9f, 1.4f),
    // 우주 (10-06): the stars twinkle (doc §6-3 「별 — 반짝임」), the flag ripples on its pole
    "kit_space_star" to MotionSpec(MotionKind.GLOW, 0.12f, 2.6f),
    "kit_space_crystal" to MotionSpec(MotionKind.GLOW, 0.04f, 1.1f),
    "kit_space_flag" to MotionSpec(MotionKind.SWAY, 0.030f, 1.6f, 1.2f),
    // 바닷속 (10-06): seaweed waves in the current, fish swim between the corals, bubbles rise, the jellyfish pulses
    "kit_sea_seaweed" to MotionSpec(MotionKind.SWAY, 0.110f, 0.8f, 1.2f),
    "kit_sea_fan_coral" to MotionSpec(MotionKind.SWAY, 0.025f, 0.7f, 1.6f),
    "kit_sea_yellow_fish" to MotionSpec(MotionKind.SWIM),
    "kit_sea_clownfish" to MotionSpec(MotionKind.SWIM),
    "kit_sea_bubble" to MotionSpec(MotionKind.RISE),
    "kit_sea_jellyfish" to MotionSpec(MotionKind.BOB, 3f, 0.6f),
)

/**
 * 앉을 자리 (doc §6-3) — where a bird may land on a piece, as shares of its box: u from the left, v from the top
 * (read off the pictures in `art/bg_kit`). Mirrored with the piece when the layout flips it.
 */
val PERCHES_BY_RES: Map<String, List<Pair<Float, Float>>> = mapOf(
    "kit_common_round_tree" to listOf(0.50f to 0.03f, 0.20f to 0.11f, 0.80f to 0.11f),   // crown top · both shoulders
    "kit_common_bush" to listOf(0.50f to 0.03f),
    "kit_park_bench" to listOf(0.30f to 0.07f, 0.70f to 0.07f),      // on the top rail, between its two knobs
    "kit_park_slide" to listOf(0.36f to 0.02f, 0.85f to 0.02f),      // the two side panels
    "kit_park_swing" to listOf(0.50f to 0.07f),                       // the top bar
    "kit_park_street_lamp" to listOf(0.50f to 0.01f),
    "kit_dino_palm_tree" to listOf(0.50f to 0.06f),                       // the crown, where the fronds meet
    "kit_dino_log" to listOf(0.35f to 0.12f, 0.70f to 0.12f),             // on top of the log
    "kit_dino_nest" to listOf(0.50f to 0.30f),                            // in the nest
)

/**
 * Someone who comes by: two pictures (sitting · flying, both **facing right**) and a size with the hero's height = 1
 * at the depth of the piece it lands on.
 */
data class VisitorSpec(val sit: String, val fly: String, val size: Float, val sitAspect: Float, val flyAspect: Float)

/** The doc's 「새」 (§3-5 `little blue bird`, 0.15) — a little bigger so it reads on a phone */
val BIRD = VisitorSpec("kit_forest_bird", "kit_forest_bird_fly", size = 0.19f, sitAspect = 0.972f, flyAspect = 1.112f)

/** Who visits which kit (doc §6-3 table: the bird comes to the forest and the park) */
val VISITORS_BY_KIT: Map<String, List<VisitorSpec>> = mapOf("park" to listOf(BIRD), "dino" to listOf(BIRD))

/**
 * A visitor as drawn this instant: [res] with its bottom centre at ([x], [y]), [h] px tall.
 * [flip] = faces left · [tilt] degrees · [scaleY] the wing beat squashing the flying picture.
 */
data class Visitor(
    val res: String, val x: Float, val y: Float, val h: Float, val aspect: Float,
    val flip: Boolean = false, val tilt: Float = 0f, val scaleY: Float = 1f, val sitting: Boolean = false,
)

/** Where a butterfly rests — flowers, and for want of a flower the top of a bush or a tuft of grass */
private val FLOWERS = setOf("kit_common_tulip", "kit_common_daisy", "kit_dino_hibiscus")
private val GREENS = setOf("kit_common_bush", "kit_common_grass", "kit_dino_fern", "kit_sea_pink_coral", "kit_sea_fan_coral")

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

/** A bird's visit: wait off stage, fly in, sit, fly out — then the next visit, to another perch */
const val VISIT_FIRST_S = 1.5
const val VISIT_FLY_S = 2.6
const val VISIT_SIT_S = 8.0
const val VISIT_AWAY_S = 6.8
const val VISIT_PERIOD_S = VISIT_FLY_S + VISIT_SIT_S + VISIT_FLY_S + VISIT_AWAY_S

/**
 * The motions of one placed scene. Build once per scene (it sorts out which flowers each butterfly visits),
 * then ask [of] every frame.
 */
class SceneMotions(
    private val scene: KitScene, private val f: SceneFrame,
    /** the kit's visitors ([VISITORS_BY_KIT]) — none by default */
    private val guests: List<VisitorSpec> = emptyList(),
    /** how many actors stand on the stage — a bird does not land on a perch they cover */
    actors: Int = 0,
) {
    private class Stop(val x: Float, val y: Float)

    /** A landing spot: the piece, the spot in its box (already mirrored), and the hero's height at that depth */
    private class Perch(val host: PlacedPiece, val u: Float, val v: Float, val heroH: Float) {
        val x: Float get() = host.box.l + host.w * u
        val y: Float get() = host.box.t + host.h * v
    }

    private val perches: List<Perch>

    private val index = IdentityHashMap<PlacedPiece, Int>().apply { scene.pieces.forEachIndexed { i, p -> put(p, i) } }

    /** butterfly → the points it visits, the first being where the layout put it */
    private val routes = IdentityHashMap<PlacedPiece, List<Stop>>()

    init {
        val keep = stageActors(actors).map { actorBox(f, it) }
        perches = scene.pieces.filter { it.fade == 0f && !it.front }.flatMap { p ->
            PERCHES_BY_RES[p.piece.res].orEmpty().map { (u, v) -> Perch(p, if (p.flip) 1f - u else u, v, p.h / p.piece.size) }
        }.filter { at ->
            // the tallest guest must fit: on the stage, under the title chrome, clear of the actors and the edge tabs
            val h = (guests.maxOfOrNull { it.size } ?: 0f) * at.heroH
            val box = Box(at.x - h * 0.6f, at.y - h, at.x + h * 0.6f, at.y)
            val tabs = abs(at.y - h / 2 - f.h / 2) < f.tabH / 2 + h / 2 && (box.l < f.tabW || box.r > f.w - f.tabW)
            box.l > 0f && box.r < f.w && box.t > f.top && keep.none { it.inter(box) > 0f } && !tabs
        }.sortedBy { it.x }

        // where a butterfly rests: flowers first (the big foreground one too — it hovers over the bloom, behind it),
        // then the tops of bushes and grass. On a phone the ground strip is narrow and often holds no flower at all
        val ground = scene.pieces.filter { it.fade == 0f }
        val perches = (ground.filter { it.piece.res in FLOWERS } + ground.filter { it.piece.res in GREENS }).sortedBy { it.x }
        val butterflies = scene.pieces.filter { MOTION_BY_RES[it.piece.res]?.kind.let { k -> k == MotionKind.FLUTTER || k == MotionKind.SWIM } }
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
            MotionKind.SWIM -> {
                // the same round, slower and flatter; no wing beat — the fish turns to face where it is going
                // (the pictures face left; the layout may have flipped one already)
                val now = flutter(p, t * 0.7, ph)
                val next = flutter(p, t * 0.7 + 0.05, ph)
                val right = next.dx > now.dx
                PieceMotion(dx = now.dx, dy = now.dy, tilt = now.tilt * 0.4f,
                    scaleX = (if (right) -1f else 1f) * (if (p.flip) -1f else 1f))
            }
            MotionKind.RISE -> {
                // floor (the horizon) → the top of the water, then again from the floor; a slow side-to-side wobble
                val span = (f.horizon - f.top - p.h).coerceAtLeast(1f).toDouble()
                val v = f.h * 0.035 * (0.7 + 0.6 * phase(p))
                val up = ((f.horizon - p.h / 2 - p.y) + v * t + phase(p) * span) % span
                val y = f.horizon - p.h / 2 - up
                PieceMotion(dx = (p.h * 0.5 * sin(1.4 * t + ph)).toFloat(), dy = (y - p.y).toFloat())
            }
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
                // the sun breathes slowly; a star twinkles quicker and bigger (amount · speed), each on its own phase
                val amount = if (spec.amount == 1f) 0.025 else spec.amount.toDouble()
                val s = (1.0 + amount * sin(0.8 * spec.speed * t + ph)).toFloat()
                PieceMotion(tilt = (3.0 * sin(0.25 * t + ph)).toFloat(), scaleX = s, scaleY = s)
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

    /** Where a perch is right now — it rides its host when the host leans in the wind */
    private fun perchAt(at: Perch, t: Double): Pair<Float, Float> {
        val m = of(at.host, t)
        return (at.x + m.dx + m.bend * (1f - at.v).pow(m.stiff)) to (at.y + m.dy)
    }

    /**
     * The guests on the stage at [t] — empty most of the time between visits.
     *
     * One visit ([VISIT_PERIOD_S]): in from one side on a swoop, [VISIT_SIT_S] on a perch (a hop now and then, a look
     * back over the shoulder), out by the other side. Each visit takes the next perch; with no perch free the bird
     * just crosses the sky. Drawn behind the actors, so it never passes over them.
     */
    fun visitors(t: Double): List<Visitor> = guests.mapIndexedNotNull { g, spec ->
        val tt = t - VISIT_FIRST_S - g * 7.3
        if (tt < 0) return@mapIndexedNotNull null
        val n = floor(tt / VISIT_PERIOD_S).toLong()
        val into = tt - n * VISIT_PERIOD_S
        if (into >= VISIT_FLY_S + VISIT_SIT_S + VISIT_FLY_S) return@mapIndexedNotNull null       // away
        val fromLeft = (n + g) % 2 == 0L
        val at = if (perches.isEmpty()) null else perches[(((n * 5 + g * 3 + scene.seed) % perches.size + perches.size) % perches.size).toInt()]
        val h = spec.size * (at?.heroH ?: f.heroH(0.5f))
        val wide = h * spec.flyAspect
        // in and out high in the sky, a little different every visit
        val skyY = f.top + h + (f.horizon - f.top - h) * (0.15f + 0.25f * (((n * 37) % 10 + 10) % 10) / 10f)
        val inX = if (fromLeft) -wide else f.w + wide
        val outX = if (fromLeft) f.w + wide else -wide
        val (px, py) = if (at != null) perchAt(at, t) else (f.w / 2) to skyY
        val beat = abs(cos(2 * PI * 3.2 * t)).toFloat()
        fun flying(e: Float, ax: Float, ay: Float, bx: Float, by: Float): Visitor {
            // a swoop: the control point sits above the higher end, so the bird comes down onto the perch
            val cx = (ax + bx) / 2
            val cy = min(ay, by) - (f.horizon - f.top) * 0.12f
            val x = (1 - e) * (1 - e) * ax + 2 * (1 - e) * e * cx + e * e * bx
            val y = (1 - e) * (1 - e) * ay + 2 * (1 - e) * e * cy + e * e * by
            val vx = 2 * (1 - e) * (cx - ax) + 2 * e * (bx - cx)
            val vy = 2 * (1 - e) * (cy - ay) + 2 * e * (by - cy)
            val left = bx < ax
            // nose along the path, gently; the picture faces right, so a bird flying left is mirrored and so is its tilt
            val slope = Math.toDegrees(atan2(vy.toDouble(), abs(vx).toDouble())).toFloat().coerceIn(-18f, 18f) * 0.6f
            return Visitor(
                spec.fly, x, (y - h * 0.10f * (1f - beat)).coerceAtLeast(f.top + h), h, spec.flyAspect,
                flip = left, tilt = if (left) -slope else slope, scaleY = 0.80f + 0.20f * beat,
            )
        }
        fun ease(u: Double) = (u * u * (3 - 2 * u)).toFloat()
        when {
            // no perch free: one crossing of the sky, then gone until the next visit
            at == null -> if (into < 2 * VISIT_FLY_S) flying(ease(into / (2 * VISIT_FLY_S)), inX, skyY, outX, skyY) else null
            into < VISIT_FLY_S -> flying(ease(into / VISIT_FLY_S), inX, skyY, px, py)
            into < VISIT_FLY_S + VISIT_SIT_S -> {
                val sat = into - VISIT_FLY_S
                val hop = (sat % 2.4) / 0.35                     // a quick hop every 2.4 s
                val up = if (sat > 0.6 && hop < 1.0) h * 0.14f * sin(PI * hop).toFloat() else 0f
                val lookBack = sat / VISIT_SIT_S in 0.38..0.62    // over the shoulder, then forward again
                Visitor(
                    spec.sit, px, py - up, h, spec.sitAspect,
                    flip = fromLeft == lookBack, tilt = (3.0 * sin(1.3 * t)).toFloat(), sitting = true,
                )
            }
            else -> flying(ease((into - VISIT_FLY_S - VISIT_SIT_S) / VISIT_FLY_S), px, py, outX, skyY)
        }
    }
}
