package com.example.finalproject_demo.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A star leaves the room's wallet and lands on the mode's star track (10-06 종훈).
 *
 * Wallet coin glows → a big star flies to the middle of the screen while the mode screen comes in →
 * it shrinks and waits small at the top centre → once the track's end medal is on screen it flies in and
 * the medal bumps. About 1.5 s of motion; the wait for the track (story PARTNER, co-op hero pick come
 * first) is not counted and gives up after [PARK_MS].
 *
 * Purely decorative: it never takes touches and the Director never waits for it. Still under tests
 * ([motionFrozen]) and when the phone has animations turned off.
 */
object StarFlight {
    /** Room wallet coin, in root pixels — reported by [StarWallet] with `flightSource` */
    var wallet by mutableStateOf<Rect?>(null)
    /** End medal of the visible star track, null while no track is shown — reported by [ProgressTrack] */
    var medal by mutableStateOf<Rect?>(null)
    /** 0..1 glow on the wallet coin while the star leaves it */
    var leaving by mutableFloatStateOf(0f)
    /** Bumped when a star lands — the medal pops once */
    var landings by mutableIntStateOf(0)

    internal const val PARK_MS = 8_000L
}

private enum class Leg { NONE, OUT, PARK, IN }

@Composable
fun StarFlightOverlay(flights: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Offset.Zero) }
    var leg by remember { mutableStateOf(Leg.NONE) }
    val t = remember { Animatable(0f) }
    // where the current leg starts — the parked spot is fixed, so the landing leg starts there
    var from by remember { mutableStateOf(Offset.Zero) }
    var to by remember { mutableStateOf(Offset.Zero) }
    var scaleFrom by remember { mutableFloatStateOf(1f) }
    var scaleTo by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(flights) {
        if (flights == 0) return@LaunchedEffect
        val off = motionFrozen || runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (off) return@LaunchedEffect
        try {
            val px = density.density
            val start = StarFlight.wallet?.center?.minus(origin)
                ?: Offset(size.x - 88f * px, 32f * px)
            val middle = Offset(size.x / 2, size.y * 0.45f)
            val park = Offset(size.x / 2, 30f * px)

            // 1 · the wallet coin glows (0.25 s) — the room is still on screen
            t.snapTo(0f)
            StarFlight.leaving = 0f
            val glow = Animatable(0f)
            glow.animateTo(1f, tween(250)) { StarFlight.leaving = value }

            // 2 · out to the middle, growing big (0.5 s) — the mode screen appears under it
            from = start; to = middle; scaleFrom = 0.8f; scaleTo = 3.2f; leg = Leg.OUT
            t.snapTo(0f)
            StarFlight.leaving = 0f
            t.animateTo(1f, tween(500, easing = FastOutSlowInEasing))

            // 3 · shrink up to the top centre and wait there for the track (0.4 s)
            from = middle; to = park; scaleFrom = 3.2f; scaleTo = 0.9f; leg = Leg.PARK
            t.snapTo(0f)
            t.animateTo(1f, tween(400, easing = FastOutSlowInEasing))

            // 4 · into the end medal once it is on screen (0.35 s); otherwise fade where it waits
            val medal = withTimeoutOrNull(StarFlight.PARK_MS) {
                snapshotFlow { StarFlight.medal }.filterNotNull().first()
            }
            from = park
            to = medal?.center?.minus(origin) ?: park
            scaleFrom = 0.9f; scaleTo = if (medal != null) 0.6f else 0f
            leg = Leg.IN
            t.snapTo(0f)
            t.animateTo(1f, tween(350, easing = LinearEasing))
            if (medal != null) StarFlight.landings++
        } finally {
            StarFlight.leaving = 0f
            leg = Leg.NONE
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned {
        val b = it.boundsInRoot()
        origin = b.topLeft
        size = Offset(b.width, b.height)
    }) {
        if (leg == Leg.NONE) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val k = t.value
            // a little arc on the way out — straight lines look mechanical
            val lift = if (leg == Leg.OUT) -this.size.height * 0.12f * (4 * k * (1 - k)) else 0f
            val p = Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k + lift)
            val sc = scaleFrom + (scaleTo - scaleFrom) * k
            if (sc <= 0.01f) return@Canvas
            val r = 18f * density.density * sc
            // soft halo, then the felt star with a stitched white edge (same star as the track)
            drawCircle(FeltMustard.copy(alpha = 0.28f), r * 1.5f, p)
            val star = starPath(p.x, p.y, r)
            drawPath(star, FeltMustard)
            drawPath(star, Color.White, style = Stroke(2.2f * density.density * sc.coerceAtMost(1.5f)))
        }
    }
}
