package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.addStroke
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Real drawings, replayed stroke by stroke (#263) — `src/test/resources/diary_strokes`.
 *
 * A developer's own test drawings from the phone (10-05 ~ 10-07), saved books turned into geometry only:
 * no names, no sayings. Each stroke carries the piece it belongs to. Two saved books grouped wrong and
 * are split in the truth: the person drawn beside the sun · cloud (10-05_5) and the crab beside the
 * sand castle (10-05_4). Replayed with no talk in between — the board's own grouping only.
 */
class DiaryDrawnPiecesTest {

    private class Drawing(val name: String, val aspect: Float, val strokes: List<Stroke>, val truth: List<Int>)

    /** One stroke a line: `piece color(argb) width x,y x,y …` · the header ends with the board's width/height */
    private fun load(name: String): Drawing {
        val lines = File("src/test/resources/diary_strokes/$name.txt").readLines()
        val aspect = lines.first().substringAfterLast(" ").toFloat()
        val rows = lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(" ") }
        val strokes = rows.map { r ->
            Stroke(Color(r[1].toLong().toInt()), r.drop(3).map { p ->
                p.split(",").let { Offset(it[0].toFloat(), it[1].toFloat()) }
            }, r[2].toFloat())
        }
        return Drawing(name, aspect, strokes, rows.map { it[0].toInt() })
    }

    private fun replay(d: Drawing): List<Int> {
        val day = DemoState().apply { drawingAspect = d.aspect }.newDiaryDay()
        d.strokes.forEach { day.addStroke(it) }
        // a stroke's piece is where it ended up — later strokes can move it (a background drawn in two strokes)
        return d.strokes.map { s -> day.pieces.first { s in it.strokes }.id }
    }

    /** Pieces that hold strokes of more than one truth piece — things drawn apart that became one piece */
    private fun wrongMerges(d: Drawing, got: List<Int>): List<String> =
        got.indices.groupBy { got[it] }.values
            .map { idx -> idx.map { d.truth[it] }.toSortedSet() }
            .filter { it.size > 1 }
            .map { "${d.name}: truth pieces $it became one" }

    /** Truth pieces whose strokes ended in more than one piece — one thing drawn apart that became several */
    private fun wrongSplits(d: Drawing, got: List<Int>): List<String> =
        got.indices.groupBy { d.truth[it] }.entries
            .map { (t, idx) -> t to idx.map { got[it] }.toSortedSet() }
            .filter { it.second.size > 1 }
            .map { "${d.name}: truth piece ${it.first} split into ${it.second.size}" }

    private val all = listOf("2026-10-05_4", "2026-10-05_5", "2026-10-06_1", "2026-10-06_2", "2026-10-06_3", "2026-10-07_0")

    @Test
    fun aPersonDrawnBesideTheSunIsNotTheSun() {
        val d = load("2026-10-05_5")
        val got = replay(d)
        val sun = got[d.truth.indexOf(1)]
        val person = d.truth.indices.filter { d.truth[it] == 3 }.map { got[it] }
        assertTrue("the person beside the sun · cloud became the sun: $got", person.none { it == sun })
    }

    @Test
    fun aCrabDrawnBesideTheCastleIsNotTheCastle() {
        val d = load("2026-10-05_4")
        val got = replay(d)
        val castle = got[d.truth.indexOf(1)]
        val crab = d.truth.indices.filter { d.truth[it] == 2 }.map { got[it] }
        assertTrue("the crab beside the sand castle became the castle: $got", crab.none { it == castle })
    }

    /**
     * Without talk, things drawn over or pressed against each other still become one piece (two touching blue things,
     * a tree's crown and trunk, a person over a yellow shape) — on the phone Otto asks and the child's answer splits them.
     * Six merged before #263, four after the line distance, three after the colour rule; this keeps it from growing back.
     */
    @Test
    fun theDrawingsDoNotMergeMoreThanBefore() {
        val report = all.flatMap { n -> load(n).let { wrongMerges(it, replay(it)) } }
        println(report.joinToString("\n").ifEmpty { "no wrong merges" })
        assertTrue(report.joinToString("\n"), report.size <= 3)
    }

    /** …and the rule does not break one thing into several: only the tree drawn with two trunks far apart (10-06_2) */
    @Test
    fun oneThingStaysOnePiece() {
        val report = all.flatMap { n -> load(n).let { wrongSplits(it, replay(it)) } }
        println(report.joinToString("\n").ifEmpty { "no wrong splits" })
        assertTrue(report.joinToString("\n"), report.size <= 1)
    }

    @Test
    fun aPersonDrawnBesideTheTreeIsNotTheTree() {
        val d = load("2026-10-06_3")
        val got = replay(d)
        val tree = got[d.truth.indexOf(1)]
        val person = d.truth.indices.filter { d.truth[it] == 5 }.map { got[it] }
        assertTrue("the person beside the tree became the tree: $got", person.none { it == tree })
    }

    @Test
    fun aCrabDrawnBesideTheWavesIsNotTheWaves() {
        val d = load("2026-10-05_4")
        val got = replay(d)
        val waves = got[d.truth.indexOf(0)]
        val crab = d.truth.indices.filter { d.truth[it] == 2 }.map { got[it] }
        assertTrue("the crab beside the waves became the waves: $got", crab.none { it == waves })
    }
}
