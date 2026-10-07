package com.example.finalproject_demo.demo.scene

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Where kit pieces go — the Kotlin port of `eval/layout_proto.py` (10-03).
 *
 *  1. tags per piece  — [KitPiece] (role · real size with hero = 1)
 *  2. one perspective — depth d (0 horizon … 1 front) gives both the feet line and the height.
 *     **It is the actors' perspective** ([SceneFrame] is built from `WorldItemView`'s FEET_* / TALL_* in
 *     `ui/Screen.kt`), so a piece at depth d stands on the same line and at the same scale as an actor at d.
 *  3. a place frame   — sky anchor · sky fill · float · far band · landmarks · flat · cover clumps ·
 *     one foreground piece, with keep-out boxes (actors, ↩ ↪ edge tabs)
 *  4. N candidates    — seeded, scored, best wins. The seed base is stored per place (`DemoState.sceneSeed`),
 *     so undo/redo and recomposition redraw the same scene.
 *
 * All values are pixels of the stage box. No Android types — `SceneLayoutTest` runs it on the JVM.
 */

/** An axis-aligned box in px */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val area: Float get() = max(0f, r - l) * max(0f, b - t)
    fun inter(o: Box): Float = Box(max(l, o.l), max(t, o.t), min(r, o.r), min(b, o.b)).area
    val cx: Float get() = (l + r) / 2
}

/**
 * The stage geometry in px.
 *
 * @param feetFar feet line at depth 0 — **this is the kit's horizon** (top edge of the ground felt)
 * @param feetNear feet line at depth 1 (front row)
 * @param tallFar hero height at depth 0 · [tallNear] at depth 1
 * @param top px under the title chrome — sky pieces stay below it
 * @param bottom px where the visible stage ends (above the narration panel); the foreground piece is cut here
 * @param tabW / [tabH] the ↩ ↪ buttons at the left/right edge, vertically centred (`MainActivity` CenterStart/End)
 */
data class SceneFrame(
    val w: Float, val h: Float,
    val feetFar: Float, val feetNear: Float,
    val tallFar: Float, val tallNear: Float,
    val top: Float = 0f, val bottom: Float = h,
    val tabW: Float = 0f, val tabH: Float = 0f,
) {
    val horizon: Float get() = feetFar
    fun feetY(d: Float): Float = feetFar + (feetNear - feetFar) * d
    fun heroH(d: Float): Float = tallFar + (tallNear - tallFar) * d
}

/** Where an actor stands (WorldItem xf · depth) — the kit keeps its box clear */
data class StageActor(val x: Float, val depth: Float)

/** The live story's two actors (`StoryLiveFlow.conversationWorld`) — one place for both */
val HERO_SPOT = StageActor(0.25f, 1f)
val FRIEND_SPOT = StageActor(0.72f, 0.9f)
fun stageActors(n: Int): List<StageActor> = listOf(HERO_SPOT, FRIEND_SPOT).take(n.coerceIn(0, 2))

/**
 * One piece on the stage.
 * @param y feet y ([PieceBase.FEET]) or centre y ([PieceBase.CENTER]), px
 * @param fade far-band haze 0 … 1
 */
data class PlacedPiece(
    val piece: KitPiece,
    val x: Float, val y: Float, val h: Float,
    val flip: Boolean = false, val tilt: Float = 0f, val fade: Float = 0f,
) {
    val w: Float get() = h * piece.aspect
    val box: Box get() {
        val top = if (piece.base == PieceBase.FEET) y - h else y - h / 2
        return Box(x - w / 2, top, x + w / 2, top + h)
    }
    /** Drawn in front of the actors ([SceneKitView] front layer) */
    val front: Boolean get() = piece.role == PieceRole.FOREGROUND
}

data class KitScene(val seed: Long, val pieces: List<PlacedPiece>, val score: Float)

/** The actor's box — same feet / height / square art as `WorldItemView` (FEET_IN_ART 0.93), body ±0.33 tall */
fun actorBox(f: SceneFrame, a: StageActor): Box {
    val tall = f.heroH(a.depth)
    val feet = min(f.feetY(a.depth), f.bottom)
    val hw = tall * 0.33f
    return Box(f.w * a.x - hw, feet - tall * 0.93f, f.w * a.x + hw, feet)
}

fun keepOut(f: SceneFrame, actors: Int): List<Box> {
    val boxes = stageActors(actors).map { actorBox(f, it) }.toMutableList()
    if (f.tabW > 0f) {
        val t = f.h / 2 - f.tabH / 2
        boxes += Box(0f, t, f.tabW, t + f.tabH)
        boxes += Box(f.w - f.tabW, t, f.w, t + f.tabH)
    }
    return boxes
}

/** Depth whose scale the sky pieces use */
private const val SKY_DEPTH = 0.2f

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

/**
 * One candidate scene for [seed]. Small pieces (sky fill, float, flat, cover) never touch a keep-out box —
 * a placement that would is dropped. Landmarks are big and stand behind, so they try a few spots and take
 * the least overlap; [score] judges the rest.
 */
fun layoutScene(kit: SceneKitDef, seed: Long, actors: Int, f: SceneFrame): List<PlacedPiece> {
    val rng = Random(seed)
    val out = mutableListOf<PlacedPiece>()
    val heroes = stageActors(actors).map { actorBox(f, it) }
    val keep = keepOut(f, actors)
    // the ↶ ↪ buttons — keep-out ends with them (see [keepOut]). Big pieces may stand behind an actor, never under a button:
    // on the phone the lamp, the bed, the lighthouse and the moon sat behind ↶ (10-06 · #222)
    val tabs = keep.drop(heroes.size)
    val W = f.w
    val H = f.h
    fun clear(b: Box, others: List<Box> = keep) = others.none { it.inter(b) > 0f }

    // sky anchor — the big one in an upper corner
    val taken = mutableListOf<Box>()
    // Sky pieces are sized at a far depth (0.2), not the front row: the app's hero is 0.58 H tall (the prototype's
    // 0.42 H), so a front-row sun filled the thin sky band and sank behind the hills (10-05 screenshot)
    kit.byRole(PieceRole.SKY_ANCHOR).forEachIndexed { i, p ->
        val h = p.size * f.heroH(SKY_DEPTH)
        // not above the gap: the landmarks stand there and hid the sun (10-05 screenshot) — keep the corners,
        // pulled out past the actors' heads
        val spots = listOf(0.09f * W, 0.91f * W)
        var cx = if (i == 0) spots[rng.nextInt(spots.size)] else W - (out.firstOrNull()?.x ?: 0f)
        val cy = f.top + h * 0.6f + rng.range(0f, 0.04f) * H
        // a tall anchor reaching down to a button moves in from the edge until it clears it (at most a fifth of the width)
        val tilt = rng.range(-p.tilt, p.tilt)
        val inward = if (cx < W / 2) 1f else -1f
        var steps = 0
        while (steps < 10 && tabs.any { it.inter(PlacedPiece(p, cx, cy, h, tilt = tilt).box) > 0f }) { cx += inward * 0.02f * W; steps++ }
        val placed = PlacedPiece(p, cx, cy, h, tilt = tilt)
        out += placed
        val b = placed.box
        taken += Box(b.l - h * 0.2f, b.t - h * 0.2f, b.r + h * 0.2f, b.b + h * 0.2f)
    }

    // sky fill — spread (dart throwing, at least 0.11 W apart), never on an anchor, a head or a tab
    val fill = kit.byRole(PieceRole.SKY_FILL).flatMap { p -> List(max(1, p.copies)) { p } }.shuffled(rng)
    run {
        val pts = mutableListOf<PlacedPiece>()
        var tries = 0
        while (pts.size < fill.size && tries++ < fill.size * 40) {
            val p = fill[pts.size]
            val x = rng.range(0.06f, 0.94f) * W
            val y = rng.range(f.top + 0.03f * H, f.horizon - 0.12f * H)
            val c = PlacedPiece(p, x, y, p.size * f.heroH(SKY_DEPTH) * rng.range(0.75f, 1.15f), rng.nextBoolean() && p.flip,
                rng.range(-p.tilt, p.tilt))
            val b = c.box
            if (b.t < f.top || b.b > f.horizon) continue
            if (!clear(b, keep + taken)) continue
            if (pts.any { hypot(it.x - x, it.y - y) < 0.11f * W || it.box.inter(b) > 0f }) continue
            pts += c
        }
        out += pts
    }

    // float — butterflies at middle height, off the actors
    kit.byRole(PieceRole.FLOAT).forEach { p ->
        repeat(max(1, p.copies)) {
            for (t in 0 until 20) {
                val c = PlacedPiece(p, rng.range(0.08f, 0.92f) * W, f.horizon - rng.range(0.04f, 0.2f) * H,
                    p.size * f.heroH(0.6f), rng.nextBoolean(), rng.range(-p.tilt, p.tilt))
                if (clear(c.box) && out.none { it.piece.role == PieceRole.FLOAT && hypot(it.x - c.x, it.y - c.y) < 0.15f * W }) {
                    out += c; break
                }
            }
        }
    }

    // far band — small hazy copies along the horizon; this is what stops the middle looking empty
    val far = kit.byRole(PieceRole.FAR)
    if (far.isNotEmpty()) {
        var x = rng.range(0f, 0.08f) * W
        while (x < W) {
            val p = far[rng.nextInt(far.size)]
            val d = rng.range(0f, 0.08f)
            out += PlacedPiece(p, x, f.feetY(d) - 0.004f * H, p.size * f.heroH(d) * rng.range(0.45f, 0.65f),
                p.flip && rng.nextBoolean(), fade = 0.45f)
            x += W * rng.range(0.08f, 0.16f)
        }
    }

    // landmarks — pick a few, a little behind; each tries 16 spots and keeps the one that covers least
    val marks = kit.byRole(PieceRole.LANDMARK).shuffled(rng).take(kit.landmarks)
    val markBoxes = mutableListOf<Box>()
    for (p in marks) {
        val d = rng.range(0.12f, 0.3f)
        val feet = f.feetY(d)
        val h = min(p.size * f.heroH(d), feet - f.top * 0.5f)
        val w = h * p.aspect
        var best: PlacedPiece? = null
        var bestCost = Float.MAX_VALUE
        repeat(16) {
            val x = rng.range(w / 2, max(w / 2, W - w / 2))
            val c = PlacedPiece(p, x, feet, h, p.flip && rng.nextBoolean())
            val b = c.box
            val cost = (heroes.sumOf { it.inter(b).toDouble() } + 3 * markBoxes.sumOf { it.inter(b).toDouble() } +
                10 * tabs.sumOf { it.inter(b).toDouble() }).toFloat() / b.area
            if (cost < bestCost) { bestCost = cost; best = c }
        }
        best?.let { out += it; markBoxes += it.box }
    }

    // flat — lies on the ground in front of the landmarks
    kit.byRole(PieceRole.FLAT).forEach { p ->
        for (t in 0 until 16) {
            val d = rng.range(0.3f, 0.8f)
            val c = PlacedPiece(p, rng.range(0.08f, 0.92f) * W, f.feetY(d), p.size * f.heroH(d), p.flip && rng.nextBoolean())
            if (clear(c.box)) { out += c; break }
        }
    }

    // cover — clumps of 2–3, not an even carpet; pieces tagged copies = 1 (bench, ball) stand alone
    val pool = kit.byRole(PieceRole.COVER).filter { it.copies == 0 }
    if (pool.isNotEmpty()) repeat(3 + rng.nextInt(2)) {
        val p0 = pool[rng.nextInt(pool.size)]
        val cx = rng.range(0.06f, 0.94f) * W
        val d0 = rng.range(0.3f, 0.85f)
        repeat(2 + rng.nextInt(2)) { k ->
            val p = if (rng.nextFloat() < 0.7f) p0 else pool[rng.nextInt(pool.size)]
            val d = (d0 + rng.range(-0.12f, 0.12f)).coerceIn(0.25f, 1f)
            val c = PlacedPiece(p, (cx + rng.range(-1f, 1f) * W * 0.035f * (k + 1)).coerceIn(0.03f * W, 0.97f * W), f.feetY(d),
                p.size * f.heroH(d) * rng.range(0.85f, 1.15f), p.flip && rng.nextBoolean())
            if (clear(c.box)) out += c
        }
    }
    kit.byRole(PieceRole.COVER).filter { it.copies == 1 }.forEach { p ->
        for (t in 0 until 16) {
            val d = rng.range(0.3f, 0.9f)
            val c = PlacedPiece(p, rng.range(0.08f, 0.92f) * W, f.feetY(d), p.size * f.heroH(d), p.flip && rng.nextBoolean())
            if (clear(c.box)) { out += c; break }
        }
    }

    // foreground — one big piece cut by a bottom corner (the cheapest depth there is), drawn over the actors
    val fronts = kit.byRole(PieceRole.FOREGROUND)
    if (fronts.isNotEmpty()) {
        val p = fronts[rng.nextInt(fronts.size)]
        val left = rng.nextBoolean()
        out += PlacedPiece(p, W * (if (left) 0.03f else 0.97f), f.bottom + 0.04f * H, p.size * f.heroH(1.15f), !left && p.flip)
    }
    return out
}

/**
 * Lower is better. Weights are the prototype's, picked by eye on 10-03 — not a measurement.
 * overlap of different ground kinds · keep-out (actors, tabs) · left/right balance · empty ground columns.
 */
fun score(items: List<PlacedPiece>, actors: Int, f: SceneFrame): Float {
    val boxes = items.filter { it.fade == 0f }
    val ground = boxes.filter { it.piece.base == PieceBase.FEET }
    var s = 0f
    for (i in ground.indices) for (j in i + 1 until ground.size) {
        val a = ground[i]; val b = ground[j]
        if (a.piece.res != b.piece.res) s += 3 * a.box.inter(b.box) / max(1f, min(a.box.area, b.box.area))
    }
    val keep = keepOut(f, actors)
    for (it in boxes) {
        if (it.front) continue
        val b = it.box
        for (k in keep) s += 4 * k.inter(b) / max(1f, b.area)
    }
    val tot = ground.sumOf { it.box.area.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    s += 4 * abs(ground.sumOf { (it.box.area * (it.box.cx / f.w - 0.5f)).toDouble() }.toFloat() / tot)
    val cols = BooleanArray(6)
    for (g in ground) {
        val b = g.box
        for (c in 0 until 6) if (b.l < (c + 1) * f.w / 6 && b.r > c * f.w / 6) cols[c] = true
    }
    for (k in keep.take(stageActors(actors).size)) cols[min(5, max(0, (k.cx / f.w * 6).toInt()))] = true
    s += 0.6f * cols.count { !it }
    return s
}

/** [candidates] seeded layouts from [seedBase]; the lowest score wins (ties → the first seed) */
fun bestScene(kit: SceneKitDef, actors: Int, f: SceneFrame, candidates: Int = 20, seedBase: Long = 0L): KitScene =
    (0 until candidates).map { i ->
        val seed = seedBase + i
        val items = layoutScene(kit, seed, actors, f)
        KitScene(seed, items, score(items, actors, f))
    }.minBy { it.score }
