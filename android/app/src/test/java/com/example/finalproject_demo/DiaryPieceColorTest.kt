package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.addStroke
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * #263 — a big stroke in another colour drawn beside an unnamed piece is a new thing (the crab beside the waves,
 * the person beside the tree), unless it is pressed against it or drawn inside it. Small strokes in another colour
 * (a window, a door, a button) still belong to the piece they are drawn on.
 */
class DiaryPieceColorTest {

    /** a drawn line, sampled every 0.01 like a finger's points — distance is measured between points */
    private fun line(c: Color, x0: Float, y0: Float, x1: Float, y1: Float): Stroke {
        val n = maxOf(1, (kotlin.math.hypot(x1 - x0, y1 - y0) / 0.01f).toInt())
        return Stroke(c, (0..n).map { i -> Offset(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n) })
    }
    private val tree = Color(0xFF6B4226)
    private val person = Color(0xFF3B6FD9)

    /** a square board, so x and y distances read the same */
    private fun day() = DemoState().apply { drawingAspect = 1f }.newDiaryDay()

    @Test
    fun aBigStrokeInAnotherColourBesideAPieceIsANewThing() {
        val d = day()
        val trunk = d.addStroke(line(tree, 0.30f, 0.40f, 0.30f, 0.80f))
        val body = d.addStroke(line(person, 0.36f, 0.45f, 0.36f, 0.75f))     // 0.06 away, 0.30 tall
        assertNotEquals("a person drawn beside the tree became the tree", trunk, body)
    }

    @Test
    fun aBigStrokeInTheSameColourAtTheSameGapIsStillTheSamePiece() {
        val d = day()
        val left = d.addStroke(line(tree, 0.30f, 0.40f, 0.30f, 0.80f))
        val right = d.addStroke(line(tree, 0.36f, 0.45f, 0.36f, 0.75f))      // the second line of the trunk
        assertEquals(left, right)
    }

    @Test
    fun aBigStrokeInAnotherColourPressedAgainstAPieceJoinsIt() {
        val d = day()
        val trunk = d.addStroke(line(tree, 0.30f, 0.40f, 0.30f, 0.80f))
        val leaves = d.addStroke(line(Color(0xFF2E9E44), 0.32f, 0.20f, 0.32f, 0.42f))   // 0.02 away — the crown on the trunk
        assertEquals(trunk, leaves)
    }

    @Test
    fun aSmallStrokeInAnotherColourBesideAPieceIsADetailOfIt() {
        val d = day()
        val wall = d.addStroke(line(tree, 0.30f, 0.40f, 0.30f, 0.80f))
        val button = d.addStroke(line(person, 0.35f, 0.55f, 0.37f, 0.58f))  // 0.05 away, small
        assertEquals("a small stroke in another colour by the piece is part of it", wall, button)
    }

    @Test
    fun aBigStrokeInAnotherColourInsideAPieceIsPartOfIt() {
        val d = day()
        val house = d.addStroke(line(tree, 0.10f, 0.30f, 0.60f, 0.90f))
        val door = d.addStroke(line(person, 0.30f, 0.55f, 0.30f, 0.75f))     // a tall door inside the house's box
        assertEquals(house, door)
    }
}
