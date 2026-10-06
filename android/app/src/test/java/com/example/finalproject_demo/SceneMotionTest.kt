package com.example.finalproject_demo

import com.example.finalproject_demo.demo.scene.BIRD
import com.example.finalproject_demo.demo.scene.Box
import com.example.finalproject_demo.demo.scene.FRIEND_SPOT
import com.example.finalproject_demo.demo.scene.HERO_SPOT
import com.example.finalproject_demo.demo.scene.MOTION_BY_RES
import com.example.finalproject_demo.demo.scene.MotionKind
import com.example.finalproject_demo.demo.scene.PARK_KIT
import com.example.finalproject_demo.demo.scene.PERCHES_BY_RES
import com.example.finalproject_demo.demo.scene.SEA_KIT
import com.example.finalproject_demo.demo.scene.PieceMotion
import com.example.finalproject_demo.demo.scene.SceneFrame
import com.example.finalproject_demo.demo.scene.SceneMotions
import com.example.finalproject_demo.demo.scene.VISIT_FIRST_S
import com.example.finalproject_demo.demo.scene.VISIT_FLY_S
import com.example.finalproject_demo.demo.scene.actorBox
import com.example.finalproject_demo.demo.scene.bestScene
import com.example.finalproject_demo.demo.scene.wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** The living background (`demo/scene/SceneMotion.kt` · doc §6-3) — pure, so plain JVM tests */
class SceneMotionTest {
    /** Landscape phone 807 × 393 dp, the same frame as `SceneLayoutTest` */
    private val f = SceneFrame(
        w = 807f, h = 393f,
        feetFar = 393f * 0.62f, feetNear = minOf(393f * 0.84f, 393f - 116f),
        tallFar = 393f * 0.30f, tallNear = 393f * 0.58f,
        top = 72f, bottom = 393f - 116f, tabW = 56f, tabH = 92f,
    )
    private val scenes = (0L until 12L).map { bestScene(PARK_KIT, 2, f, seedBase = it * 100) }

    /** two minutes of frames at 30 a second */
    private val times = (0 until 3600).map { it / 30.0 }

    private fun kindOf(res: String) = MOTION_BY_RES[res]?.kind

    @Test fun theWindStaysInItsRange() {
        for (t in times) for (x in listOf(0f, 0.3f, 0.7f, 1f)) {
            val w = wind(t, x)
            assertTrue("wind $w at t=$t x=$x", w > -0.5f && w < 1.9f)
        }
    }

    @Test fun whatHasNoRowStandsStill() {
        for (scene in scenes) {
            val m = SceneMotions(scene, f)
            for (p in scene.pieces.filter { it.piece.res !in MOTION_BY_RES }) {
                for (t in listOf(0.0, 3.7, 61.2)) assertEquals("${p.piece.name} moved", PieceMotion.NONE, m.of(p, t))
            }
        }
    }

    /** A swaying piece only bends: no slide, no turn — its feet stay on their shadow — and never further than its tag says */
    @Test fun treesAndFlowersBendButStayPlanted() {
        var bent = 0
        for (scene in scenes) {
            val m = SceneMotions(scene, f)
            for (p in scene.pieces.filter { kindOf(it.piece.res) == MotionKind.SWAY }) {
                val spec = MOTION_BY_RES.getValue(p.piece.res)
                for (t in times) {
                    val at = m.of(p, t)
                    assertEquals(0f, at.dx, 0f); assertEquals(0f, at.dy, 0f); assertEquals(0f, at.tilt, 0f)
                    // wind tops out near 1.75 and the piece's own shiver adds 0.15
                    assertTrue("${p.piece.name} leans ${at.bend} of ${p.h}", abs(at.bend) <= p.h * spec.amount * 2.0f)
                    if (abs(at.bend) > 0.5f) bent++
                }
            }
        }
        assertTrue("nothing ever bent", bent > 1000)
    }

    /** One wind: two pieces of the same kind standing close lean the same way most of the time */
    @Test fun neighboursLeanTogether() {
        var same = 0
        var all = 0
        for (scene in scenes) {
            val m = SceneMotions(scene, f)
            val sway = scene.pieces.filter { kindOf(it.piece.res) == MotionKind.SWAY }
            for (a in sway) for (b in sway) {
                if (a === b || a.piece.res != b.piece.res || abs(a.x - b.x) > f.w * 0.15f) continue
                for (t in times.filterIndexed { i, _ -> i % 10 == 0 }) {
                    all++
                    if (m.of(a, t).bend * m.of(b, t).bend > 0f) same++
                }
            }
        }
        assertTrue("no neighbours to compare", all > 0)
        assertTrue("neighbours agree only $same of $all", same > all * 0.8)
    }

    /** A cloud drifts smoothly; the only jump is the wrap, and that happens with the whole cloud off the stage */
    @Test fun cloudsDriftAndWrapOffStage() {
        var wraps = 0
        for (scene in scenes) {
            val m = SceneMotions(scene, f)
            for (p in scene.pieces.filter { kindOf(it.piece.res) == MotionKind.DRIFT }) {
                // long enough for every cloud to cross at least once
                val long = (0 until 30 * 400).map { it / 30.0 }
                var last = p.x + m.of(p, 0.0).dx
                for (t in long.drop(1)) {
                    val x = p.x + m.of(p, t).dx
                    if (abs(x - last) > 2f) {
                        wraps++
                        assertTrue("wrapped in view: $last → $x", last - p.w / 2 >= f.w - 1f && x + p.w / 2 <= 1f)
                    }
                    last = x
                }
            }
        }
        assertTrue("no cloud ever wrapped", wraps > 0)
    }

    /** A butterfly never jumps, stays on the stage, and travels — to the flowers and greens when the scene has any */
    @Test fun butterfliesGoFromFlowerToFlower() {
        var butterflies = 0
        var perchesSeen = 0
        var perchesThere = 0
        val rests = setOf("kit_common_tulip", "kit_common_daisy", "kit_common_bush", "kit_common_grass")
        for (scene in scenes) {
            val m = SceneMotions(scene, f)
            val perches = scene.pieces.filter { it.piece.res in rests && it.fade == 0f }
            for (p in scene.pieces.filter { kindOf(it.piece.res) == MotionKind.FLUTTER }) {
                butterflies++
                val seen = HashSet<Int>()
                var lx = p.x + m.of(p, 0.0).dx
                var ly = p.y + m.of(p, 0.0).dy
                var minX = lx
                var maxX = lx
                for (t in times.drop(1)) {
                    val at = m.of(p, t)
                    val x = p.x + at.dx
                    val y = p.y + at.dy
                    assertTrue("jumped ${hypot(x - lx, y - ly)} px in a frame", hypot(x - lx, y - ly) < p.h * 0.6f)
                    assertTrue("left the stage: $x, $y", x > -p.w && x < f.w + p.w && y > f.top - 1f && y < f.bottom)
                    assertTrue("wings ${at.scaleX}", at.scaleX in 0.3f..1.01f)
                    perches.forEachIndexed { i, fl ->
                        val px = fl.x.coerceIn(p.w, f.w - p.w)
                        if (hypot(x - px, y - maxOf(f.top + p.h, fl.box.t - p.h * 0.35f)) < p.h * 1.2f) seen += i
                    }
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    lx = x; ly = y
                }
                assertTrue("a butterfly stayed within ${maxX - minX} px", maxX - minX > f.w * 0.1f)
                perchesSeen += seen.size
                perchesThere += perches.size
            }
        }
        assertTrue("no butterflies in twelve scenes", butterflies > 0)
        assertTrue("no perch in twelve scenes", perchesThere > 0)
        assertEquals("every butterfly visits every perch in two minutes", perchesThere, perchesSeen)
    }

    // ── the bird (doc §6-3: flies in → sits on a perch → flies on) ──────────────────────────────

    @Test fun withoutGuestsNobodyComes() {
        val m = SceneMotions(scenes[0], f)
        for (t in times) assertTrue(m.visitors(t).isEmpty())
    }

    /** It shows up soon, is away between visits, and never jumps while it is on the stage */
    @Test fun theBirdComesAndGoesWithoutAJump() {
        for (scene in scenes) {
            val m = SceneMotions(scene, f, listOf(BIRD), actors = 2)
            assertTrue("no bird in the first seconds", m.visitors(VISIT_FIRST_S + VISIT_FLY_S / 2).size == 1)
            var away = 0
            var last: Pair<Float, Float>? = null
            for (t in times) {
                val v = m.visitors(t).firstOrNull()
                if (v == null) { away++; last = null; continue }
                assertTrue("off the top: ${v.y - v.h}", v.y - v.h >= f.top - 1f)
                last?.let { (lx, ly) -> assertTrue("jumped ${hypot(v.x - lx, v.y - ly)} px at t=$t", hypot(v.x - lx, v.y - ly) < f.w * 0.04f) }
                last = v.x to v.y
            }
            assertTrue("the bird never left", away > 0)
        }
    }

    /** Sitting means on a perch of a piece that has one, clear of the two actors; flying means nose first */
    @Test fun theBirdSitsOnAPerchAndFliesNoseFirst() {
        val actors = listOf(HERO_SPOT, FRIEND_SPOT).map { actorBox(f, it) }
        var sat = 0
        val spots = HashSet<Int>()
        var withTwoPerches = 0
        for (scene in scenes) {
            val m = SceneMotions(scene, f, listOf(BIRD), actors = 2)
            val hosts = scene.pieces.filter { it.piece.res in PERCHES_BY_RES && it.fade == 0f && !it.front }
            val here = HashSet<Int>()
            var lastX: Float? = null
            for (t in (0 until 30 * 300).map { it / 30.0 }) {
                val v = m.visitors(t).firstOrNull()
                if (v == null) { lastX = null; continue }
                if (v.sitting) {
                    sat++
                    assertEquals(BIRD.sit, v.res)
                    // on (or a hop above) one of the hosts: within its width — a swaying tree carries it a little — near a perch height
                    val host = hosts.firstOrNull { h ->
                        PERCHES_BY_RES.getValue(h.piece.res).any { (u, pv) ->
                            val px = h.box.l + h.w * (if (h.flip) 1f - u else u)
                            val py = h.box.t + h.h * pv
                            abs(v.x - px) < h.h * 0.12f && v.y <= py + 1f && v.y >= py - v.h * 0.2f
                        }
                    }
                    assertTrue("sitting in the air at ${v.x}, ${v.y}", host != null)
                    val box = Box(v.x - v.h * v.aspect / 2, v.y - v.h, v.x + v.h * v.aspect / 2, v.y)
                    assertTrue("sat on an actor", actors.none { it.inter(box) > 0f })
                    here += (v.x / 4).toInt()
                } else {
                    assertEquals(BIRD.fly, v.res)
                    lastX?.let { lx -> if (abs(v.x - lx) > 0.5f) assertEquals("flies tail first at t=$t", v.x < lx, v.flip) }
                }
                lastX = if (v.sitting) null else v.x
            }
            if (here.size >= 2) withTwoPerches++
            spots += here
        }
        assertTrue("the bird never sat down in twelve scenes", sat > 0)
        assertTrue("the bird always takes the same perch", withTwoPerches > 0)
    }

    // ── 바닷속 (10-06): fish swim between the corals, bubbles rise ──────────────────────────────

    /** A fish never jumps, stays in the water, and faces where it swims (the pictures face left) */
    @Test fun fishSwimFacingTheWayTheyGo() {
        var fish = 0
        for (seed in 0L until 8L) {
            val scene = bestScene(SEA_KIT, 2, f, seedBase = seed * 100)
            val m = SceneMotions(scene, f)
            for (p in scene.pieces.filter { kindOf(it.piece.res) == MotionKind.SWIM }) {
                fish++
                var lx = p.x + m.of(p, 0.0).dx
                var ly = p.y + m.of(p, 0.0).dy
                for (t in times.drop(1)) {
                    val at = m.of(p, t)
                    val x = p.x + at.dx
                    val y = p.y + at.dy
                    assertTrue("jumped at t=$t", hypot(x - lx, y - ly) < p.h * 0.6f)
                    assertTrue("left the water: $y", y > f.top - 1f && y < f.bottom)
                    if (abs(x - lx) > 1f) {
                        val screenFacesRight = (if (p.flip) -1f else 1f) * at.scaleX < 0f
                        assertEquals("swims tail first at t=$t", x > lx, screenFacesRight)
                    }
                    lx = x; ly = y
                }
            }
        }
        assertTrue("no fish in eight sea scenes", fish > 0)
    }

    /** A bubble only goes up between the floor and the top of the water, except the one drop back to the floor */
    @Test fun bubblesRiseFromTheFloor() {
        var bubbles = 0
        var wraps = 0
        for (seed in 0L until 8L) {
            val scene = bestScene(SEA_KIT, 2, f, seedBase = seed * 100)
            val m = SceneMotions(scene, f)
            for (p in scene.pieces.filter { kindOf(it.piece.res) == MotionKind.RISE }) {
                bubbles++
                var ly = p.y + m.of(p, 0.0).dy
                for (t in times.drop(1)) {
                    val y = p.y + m.of(p, t).dy
                    assertTrue("bubble at $y outside the water", y >= f.top - 1f && y <= f.horizon)
                    if (y > ly + 1f) wraps++ else assertTrue("bubble sank at t=$t", y <= ly + 0.01f)
                    ly = y
                }
            }
        }
        assertTrue("no bubbles in eight sea scenes", bubbles > 0)
        assertTrue("no bubble ever reached the top", wraps > 0)
    }
}
