package com.example.finalproject_demo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.scene.KitScene
import com.example.finalproject_demo.demo.scene.PieceBase
import com.example.finalproject_demo.demo.scene.PieceRole
import com.example.finalproject_demo.demo.scene.PlacedPiece
import com.example.finalproject_demo.demo.scene.SceneFrame
import com.example.finalproject_demo.demo.scene.SceneKitDef
import com.example.finalproject_demo.demo.scene.bestScene
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Stage floor + felt pieces (10-05 · `docs/배경_조각_목록.md` · layout in `demo/scene/SceneLayout.kt`).
 *
 * Two layers so the actors (still drawn by `WorldItemView` in Screen.kt) sit between them:
 *   [SceneBack]  sky · sky pieces · two felt hills · hazy far band · ground · ground pieces (sorted by feet)
 *   [SceneFront] the one foreground piece cut by a bottom corner — over the actors' feet
 * Both compute the same scene from the same inputs (`bestScene` is deterministic for a seed base).
 */

/** ↩ ↪ edge tabs (`TurnNavButton`) — the layout keeps pieces out from behind them */
private val TAB_W = 56.dp
private val TAB_H = 92.dp

/**
 * The stage geometry in px, from **the actors' own numbers** (`FEET_*` · `TALL_*` · bottom inset in Screen.kt),
 * so a kit piece at depth d stands on the actors' feet line at the actors' scale. The kit's horizon is the
 * actors' depth-0 feet line (FEET_FAR) — the kit is fitted to the actors, not the other way round.
 */
/** How far into the actors' depth range the kit's horizon sits — 0.35 puts it near 0.70 H on a phone (10-05) */
private const val KIT_NEAREST_FAR = 0.35f

@Composable
private fun rememberKitScene(kit: SceneKitDef, seedBase: Long, actors: Int, wPx: Float, hPx: Float, bottomInset: Dp, topInset: Dp): Pair<KitScene, SceneFrame> {
    val density = LocalDensity.current
    return remember(kit, seedBase, actors, wPx, hPx, bottomInset, topInset) {
        with(density) {
            // The kit starts at the actors' depth KIT_NEAREST_FAR, not 0: with the horizon at the depth-0 feet line
            // (0.62 H) the ground took ~38 % of the screen — 「바닥 너무 비율이 커」 (10-05 device). Same perspective
            // line, its far end cut, so pieces and actors still match in size where they stand.
            val near = minOf(hPx * FEET_NEAR, hPx - bottomInset.toPx())
            val far = hPx * FEET_FAR + (near - hPx * FEET_FAR) * KIT_NEAREST_FAR
            val f = SceneFrame(
                w = wPx, h = hPx,
                feetFar = far,
                feetNear = near,
                tallFar = hPx * (TALL_FAR + (TALL_NEAR - TALL_FAR) * KIT_NEAREST_FAR), tallNear = hPx * TALL_NEAR,
                top = topInset.toPx(), bottom = hPx - bottomInset.toPx(),
                tabW = TAB_W.toPx(), tabH = TAB_H.toPx(),
            )
            bestScene(kit, actors, f, seedBase = seedBase) to f
        }
    }
}

@Composable
private fun kitBitmaps(kit: SceneKitDef): Map<String, ImageBitmap> {
    val out = HashMap<String, ImageBitmap>()
    // a fixed list per kit, so the composable calls below keep their order
    for (res in kit.pieces.map { it.res }.distinct()) assetBitmap(res)?.let { out[res] = it }
    return out
}

/** Felt grain — a small noise tile, repeated; plus a coarse blotch map stretched over the stage. Made once. */
private object FeltNoise {
    val grain: ImageBitmap by lazy { noise(96, 96, 20, seed = 11) }
    val blotch: ImageBitmap by lazy { noise(40, 24, 36, seed = 5) }

    private fun noise(w: Int, h: Int, spread: Int, seed: Int): ImageBitmap {
        val rnd = java.util.Random(seed.toLong())
        val px = IntArray(w * h) {
            val v = 128 + (rnd.nextGaussian() * spread).toInt().coerceIn(-127, 127)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

@Composable
fun SceneBack(kit: SceneKitDef, seedBase: Long, actors: Int, bottomInset: Dp, topInset: Dp, modifier: Modifier = Modifier) {
    val imgs = kitBitmaps(kit)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val (scene, f) = rememberKitScene(kit, seedBase, actors,
            with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, bottomInset, topInset)
        val skyTop = Color(kit.skyTop)
        val skyBottom = Color(kit.skyBottom)
        val grain = remember { ShaderBrush(ImageShader(FeltNoise.grain, TileMode.Repeated, TileMode.Repeated)) }
        Canvas(Modifier.fillMaxSize()) {
            val seed = scene.seed.toFloat()
            // sky — two-colour felt gradient with soft blotches
            drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom), endY = f.horizon))
            drawImage(FeltNoise.blotch, srcSize = IntSize(FeltNoise.blotch.width, FeltNoise.blotch.height),
                dstSize = IntSize(size.width.roundToInt(), f.horizon.roundToInt()), alpha = 0.10f,
                blendMode = BlendMode.Overlay, filterQuality = FilterQuality.Low)
            scene.pieces.filter { it.piece.base == PieceBase.CENTER && it.piece.role != PieceRole.FLOAT }
                .sortedBy { it.h }.forEach { drawPiece(imgs, it, skyBottom) }
            // hills — two felt layers above the horizon, then the hazy far band
            hill(f, seed, f.horizon - 0.13f * f.h, 16f, 120f, Color(kit.hillFar))
            hill(f, seed + 2, f.horizon - 0.07f * f.h, 12f, 90f, Color(kit.hillNear))
            scene.pieces.filter { it.fade > 0f }.forEach { drawPiece(imgs, it, skyBottom) }
            // ground — from the actors' depth-0 feet line down, soft wavy stitched edge
            hill(f, seed + 4, f.horizon, 5f, 140f, Color(kit.ground))
            drawRect(grain, alpha = 0.07f, blendMode = BlendMode.Overlay)
            // ground pieces: flat first, then by feet (far → near), then what floats
            val ground = scene.pieces.filter { it.piece.base == PieceBase.FEET && it.fade == 0f && !it.front }
            ground.filter { it.piece.role == PieceRole.FLAT }.forEach { drawPiece(imgs, it, skyBottom) }
            ground.filter { it.piece.role != PieceRole.FLAT }.sortedBy { it.y }.forEach {
                shadow(it)
                drawPiece(imgs, it, skyBottom)
            }
            scene.pieces.filter { it.piece.role == PieceRole.FLOAT }.forEach { drawPiece(imgs, it, skyBottom) }
        }
    }
}

/** The pieces in front of the actors — draw after `WorldItemView`s */
@Composable
fun SceneFront(kit: SceneKitDef, seedBase: Long, actors: Int, bottomInset: Dp, topInset: Dp, modifier: Modifier = Modifier) {
    val imgs = kitBitmaps(kit)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val (scene, _) = rememberKitScene(kit, seedBase, actors,
            with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, bottomInset, topInset)
        val skyBottom = Color(kit.skyBottom)
        Canvas(Modifier.fillMaxSize()) {
            scene.pieces.filter { it.front }.forEach { drawPiece(imgs, it, skyBottom) }
        }
    }
}

/** A felt layer whose top edge is a soft wave, filled down to the bottom, with a shade line and running stitches */
private fun DrawScope.hill(f: SceneFrame, seed: Float, y0: Float, amp: Float, freq: Float, color: Color) {
    // the prototype's numbers are for a 1344 × 768 picture — scale them to this stage
    val sx = f.w / 1344f
    val sy = f.h / 768f
    val a = amp * sy
    val fr = freq * sx
    val edge = Path()
    val fill = Path()
    val stitch = Path()
    var x = 0f
    val step = 6f * sx
    while (x <= f.w + step) {
        val y = y0 + a * sin(x / fr + seed) + a * 0.4f * sin(x / (fr * 0.37f) + seed * 2)
        if (x == 0f) { edge.moveTo(x, y); fill.moveTo(x, y); stitch.moveTo(x, y + 9f * sy) }
        else { edge.lineTo(x, y); fill.lineTo(x, y); stitch.lineTo(x, y + 9f * sy) }
        x += step
    }
    fill.lineTo(f.w + step, f.h); fill.lineTo(0f, f.h); fill.close()
    drawPath(edge, Color.Black.copy(alpha = 0.10f), style = Stroke(width = 8f * sy))
    val dark = Color(
        max(0f, color.red - 18f / 255f), max(0f, color.green - 18f / 255f), max(0f, color.blue - 18f / 255f),
    )
    drawPath(fill, Brush.verticalGradient(listOf(color, dark), startY = y0, endY = f.h))
    drawPath(stitch, Color(0xFFFFFAEE).copy(alpha = 0.6f), style = Stroke(width = 2f * sy,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * sx, 8f * sx))))
}

/** Soft ground shadow at the feet — the only proof a piece touches the floor */
private fun DrawScope.shadow(p: PlacedPiece) {
    val w = p.w * 0.7f
    val h = w * 0.14f
    drawOval(
        Brush.radialGradient(
            0f to Color(0x66141820), 1f to Color(0x00141820),
            center = Offset(p.x, p.y), radius = w / 2,
        ),
        topLeft = Offset(p.x - w / 2, p.y - h / 2), size = Size(w, h),
    )
}

private fun DrawScope.drawPiece(imgs: Map<String, ImageBitmap>, p: PlacedPiece, haze: Color) {
    val img = imgs[p.piece.res] ?: return
    val b = p.box
    val pivot = Offset(p.x, (b.t + b.b) / 2)
    val dst = IntSize(max(1, (b.r - b.l).roundToInt()), max(1, (b.b - b.t).roundToInt()))
    val at = IntOffset(b.l.roundToInt(), b.t.roundToInt())
    val src = IntSize(img.width, img.height)
    rotate(p.tilt, pivot) {
        scale(if (p.flip) -1f else 1f, 1f, pivot) {
            if (p.fade > 0f) {
                // far band: less colour, then a veil of the sky's lower colour — air between us and the horizon
                val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - p.fade * 0.6f) })
                drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, colorFilter = grey, filterQuality = FilterQuality.Medium)
                drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, alpha = p.fade * 0.55f,
                    colorFilter = ColorFilter.tint(haze, BlendMode.SrcIn), filterQuality = FilterQuality.Medium)
            } else {
                drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, filterQuality = FilterQuality.Medium)
            }
        }
    }
}
