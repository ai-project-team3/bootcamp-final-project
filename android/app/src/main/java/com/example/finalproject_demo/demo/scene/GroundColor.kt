package com.example.finalproject_demo.demo.scene

import kotlin.math.sqrt

/**
 * The felt floor's colour for a picture the server drew (#257 · 10-07 device). A generated background is used only when no
 * kit matches the place, so the floor used to fall back to the park's grass every time — a snowfield or a wooden room stood
 * on green. Read from the picture instead: the median colour of the band where the actors stand and the floor begins
 * (70–88 % of the height), a little darker because the felt grain goes on top. The very bottom was tried first and gave
 * muddy colours on real server pictures — flowers, cars and leaves crowd the foreground (7 pictures from the phone, 10-07).
 * Pure — the caller decodes the picture (shrunk) and caches the answer per picture.
 *
 * @param color ARGB of the floor
 * @param hasGround the picture already paints a ground there (one even colour across the bottom band, different from the
 *   band above it) — the floor is then laid thin and see-through, just enough under the actors' feet
 */
data class Ground(val color: Int, val hasGround: Boolean)

/** Bottom band read to tell a painted ground — 12 % of the height (design §5-2) */
private const val BAND = 0.12f
/** The band read for the colour — where the actors' feet and the floor's horizon are */
private const val COLOUR_FROM = 0.70f
private const val COLOUR_TO = 0.88f
/** How much darker than the picture the felt is drawn */
private const val DARKEN = 0.08f
/** Below this spread (0 … 255) the bottom band is one colour */
private const val EVEN_SPREAD = 22.0
/** Steps of 6 % used to see how far up the ground reaches */
private const val STEP = 0.06f
/** A band this close (RGB distance) to the bottom colour is still the ground */
private const val SAME = 15.0
/** What is above the ground must be at least this far from it — a smooth sky gradient never makes this step */
private const val GROUND_STEP = 38.0

fun groundOf(px: IntArray, w: Int, h: Int): Ground {
    require(w > 0 && h > 0 && px.size >= w * h)
    val bandH = maxOf(1, (h * BAND).toInt())
    val bottom = rows(px, w, h - bandH, h)
    val med = median(bottom)
    val feet = median(rows(px, w, (h * COLOUR_FROM).toInt(), maxOf((h * COLOUR_FROM).toInt() + 1, (h * COLOUR_TO).toInt())))
    val dark = rgb(
        (r(feet) * (1 - DARKEN)).toInt(), (g(feet) * (1 - DARKEN)).toInt(), (b(feet) * (1 - DARKEN)).toInt(),
    )
    return Ground(dark, spread(bottom, med) < EVEN_SPREAD && paintedGround(px, w, h, bandH, med))
}

/** The bottom colour carries on upward for a while, then something clearly different sits on it */
private fun paintedGround(px: IntArray, w: Int, h: Int, bandH: Int, med: Int): Boolean {
    val stepH = maxOf(1, (h * STEP).toInt())
    var top = h - bandH
    while (top - stepH >= 0) {
        val band = rows(px, w, top - stepH, top)
        if (band.isEmpty() || dist(median(band), med) > SAME) break
        top -= stepH
    }
    if (top - stepH < 0) return false                       // the same colour all the way up — no ground line
    val above = rows(px, w, maxOf(0, top - stepH), top)
    return above.isNotEmpty() && dist(median(above), med) > GROUND_STEP
}

private fun rows(px: IntArray, w: Int, from: Int, to: Int): IntArray {
    if (to <= from) return IntArray(0)
    return IntArray((to - from) * w) { i -> px[(from + i / w) * w + i % w] }.filter { (it ushr 24) > 128 }.toIntArray()
}

private fun median(c: IntArray): Int {
    if (c.isEmpty()) return rgb(120, 178, 84)
    fun mid(ch: (Int) -> Int) = c.map(ch).sorted()[c.size / 2]
    return rgb(mid(::r), mid(::g), mid(::b))
}

/** Mean distance from the median — how far the band is from one even colour */
private fun spread(c: IntArray, m: Int): Double = if (c.isEmpty()) 0.0 else c.sumOf { dist(it, m) } / c.size

private fun dist(a: Int, b: Int): Double {
    val dr = (r(a) - r(b)).toDouble(); val dg = (g(a) - g(b)).toDouble(); val db = (b(a) - b(b)).toDouble()
    return sqrt(dr * dr + dg * dg + db * db)
}

private fun r(c: Int) = (c shr 16) and 0xFF
private fun g(c: Int) = (c shr 8) and 0xFF
private fun b(c: Int) = c and 0xFF
private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

