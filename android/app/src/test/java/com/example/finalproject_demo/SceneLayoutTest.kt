package com.example.finalproject_demo

import com.example.finalproject_demo.demo.scene.PARK_KIT
import com.example.finalproject_demo.demo.scene.PieceRole
import com.example.finalproject_demo.demo.scene.SceneFrame
import com.example.finalproject_demo.demo.scene.bestScene
import com.example.finalproject_demo.demo.scene.keepOut
import com.example.finalproject_demo.demo.scene.layoutScene
import com.example.finalproject_demo.demo.scene.score
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kit layout (`demo/scene/SceneLayout.kt`) — pure, so plain JVM tests */
class SceneLayoutTest {
    /** Landscape phone 807 × 393 dp, with Screen.kt's FEET_* / TALL_* and the chrome (top 72 · bottom 116) */
    private val phone = frame(807f, 393f, bottomInset = 116f, top = 72f)
    /** Portrait tablet: the stage is a 1344:768 frame above the chrome — no insets inside it */
    private val tabletFrame = frame(800f, 800f * 768f / 1344f, bottomInset = 0f, top = 0f)

    private fun frame(w: Float, h: Float, bottomInset: Float, top: Float) = SceneFrame(
        w = w, h = h,
        feetFar = h * 0.62f, feetNear = minOf(h * 0.84f, h - bottomInset),
        tallFar = h * 0.30f, tallNear = h * 0.58f,
        top = top, bottom = h - bottomInset, tabW = 56f, tabH = 92f,
    )

    /** Roles that are small enough that a placement touching an actor or a tab is dropped */
    private val hard = setOf(PieceRole.SKY_FILL, PieceRole.FLOAT, PieceRole.FLAT, PieceRole.COVER)

    @Test fun sameSeedSameScene() {
        for (f in listOf(phone, tabletFrame)) {
            assertEquals(layoutScene(PARK_KIT, 42L, 2, f), layoutScene(PARK_KIT, 42L, 2, f))
            assertEquals(bestScene(PARK_KIT, 2, f, seedBase = 1234L), bestScene(PARK_KIT, 2, f, seedBase = 1234L))
        }
    }

    @Test fun differentSeedsGiveDifferentScenes() {
        val scenes = (0L until 10L).map { layoutScene(PARK_KIT, it, 2, phone) }.toSet()
        assertTrue("seeds should vary the scene", scenes.size > 5)
    }

    @Test fun piecesStayInsideTheStage() {
        for (f in listOf(phone, tabletFrame)) for (seed in 0L until 50L) {
            for (p in layoutScene(PARK_KIT, seed, 2, f)) {
                val b = p.box
                if (p.front) {
                    // cut by a bottom corner on purpose — but its centre stays on the stage edge
                    assertTrue(p.x in 0f..f.w)
                    assertTrue(b.t < f.bottom)
                    continue
                }
                assertTrue("${p.piece.name} x ${p.x}", p.x in 0f..f.w)
                assertTrue("${p.piece.name} y ${p.y}", p.y in 0f..f.h)
                assertTrue("${p.piece.name} top ${b.t} above the stage", b.t >= 0f)
                if (p.piece.role in setOf(PieceRole.SKY_ANCHOR, PieceRole.SKY_FILL)) {
                    assertTrue("${p.piece.name} under the title", b.t >= f.top - 1f)
                    assertTrue("${p.piece.name} below the horizon", b.b <= f.horizon + 1f)
                } else if (p.piece.role == PieceRole.FLOAT) {
                    assertTrue("${p.piece.name} floats in the air", p.y < f.horizon)
                } else {
                    // ground pieces stand between the horizon and the actors' front feet line
                    assertTrue("${p.piece.name} feet ${p.y}", p.y >= f.feetY(0f) - 0.01f * f.h && p.y <= f.feetNear + 0.5f)
                }
            }
        }
    }

    @Test fun smallPiecesNeverTouchActorsOrTabs() {
        for (f in listOf(phone, tabletFrame)) for (base in listOf(0L, 100L, 7777L)) {
            val best = bestScene(PARK_KIT, 2, f, seedBase = base)
            val keep = keepOut(f, 2)
            for (p in best.pieces.filter { it.piece.role in hard }) {
                assertTrue("${p.piece.name} overlaps a keep-out box", keep.none { it.inter(p.box) > 0f })
            }
        }
    }

    @Test fun bestScoreIsNoWorseThanAnyCandidate() {
        for (f in listOf(phone, tabletFrame)) {
            val best = bestScene(PARK_KIT, 2, f, candidates = 20, seedBase = 500L)
            val all = (500L until 520L).map { score(layoutScene(PARK_KIT, it, 2, f), 2, f) }
            assertEquals(all.min(), best.score, 1e-4f)
            assertTrue(best.score <= all.max())
            assertTrue("the winner comes from the candidates", best.seed in 500L until 520L)
        }
    }

    @Test fun everySceneHasTheFramePieces() {
        for (seed in 0L until 20L) {
            val roles = bestScene(PARK_KIT, 2, phone, seedBase = seed * 20).pieces.map { it.piece.role }.toSet()
            for (r in listOf(PieceRole.SKY_ANCHOR, PieceRole.FAR, PieceRole.LANDMARK, PieceRole.FOREGROUND)) {
                assertTrue("seed $seed lacks $r", r in roles)
            }
        }
    }
}
