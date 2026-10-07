package com.example.finalproject_demo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.scene.KitScene
import com.example.finalproject_demo.demo.scene.PieceBase
import com.example.finalproject_demo.demo.scene.PieceMotion
import com.example.finalproject_demo.demo.scene.PieceRole
import com.example.finalproject_demo.demo.scene.PlacedPiece
import com.example.finalproject_demo.demo.scene.SceneFrame
import com.example.finalproject_demo.demo.scene.SceneKitDef
import com.example.finalproject_demo.demo.scene.SceneMotions
import com.example.finalproject_demo.demo.scene.VISITORS_BY_KIT
import com.example.finalproject_demo.demo.scene.Visitor
import com.example.finalproject_demo.demo.scene.bestScene
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Stage floor + felt pieces (10-05 · `docs/배경_조각_목록.md` · layout in `demo/scene/SceneLayout.kt`).
 *
 * Two layers so the actors (still drawn by `WorldItemView` in Screen.kt) sit between them:
 *   [SceneBack]  sky · sky pieces · two felt hills · hazy far band · ground · ground pieces (sorted by feet)
 *   [SceneFront] the one foreground piece cut by a bottom corner — over the actors' feet
 * Both compute the same scene from the same inputs (`bestScene` is deterministic for a seed base).
 *
 * **The living background** (10-05 · doc §6-3): the pieces keep moving by rule — trees and flowers lean with one
 * wind, clouds drift, butterflies go from flower to flower (`demo/scene/SceneMotion.kt`). Both layers read the
 * same clock ([sceneClock]), only inside the draw pass, so a frame redraws the canvas and recomposes nothing.
 */

/**
 * The one switch for the living background. Off: every piece stands where the layout put it.
 * It is also still when the phone has animations removed (animator scale 0) and under Robolectric — the
 * reference screenshots are of the layout, not of a moment in the wind.
 */
object SceneLife {
    @Volatile var on: Boolean = true
    /** frame time of the first living frame — one epoch for both layers, so front and back share the wind */
    internal var epoch = 0L
}

/** Pins the scene time in seconds (the reel test · previews). null = the frame clock, or still (see [SceneLife]) */
val LocalSceneTime = compositionLocalOf<Double?> { null }

/** Seconds of scene time as a state to read **in the draw pass**, or null when the scene stands still */
@Composable
private fun sceneClock(): State<Double>? {
    val pinned = LocalSceneTime.current
    if (pinned != null) return rememberUpdatedState(pinned)
    val ctx = LocalContext.current
    val still = remember {
        !SceneLife.on || android.os.Build.FINGERPRINT == "robolectric" ||
            android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (still) return null
    return produceState(0.0) {
        while (true) withFrameNanos { n ->
            if (SceneLife.epoch == 0L) SceneLife.epoch = n
            value = (n - SceneLife.epoch) / 1e9
        }
    }
}

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

/**
 * The stage frame for a kit. The kit starts at the actors' depth KIT_NEAREST_FAR, not 0: with the horizon at the depth-0
 * feet line (0.62 H) the ground took ~38 % of the screen — 「바닥 너무 비율이 커」 (10-05 device). Same perspective line,
 * its far end cut, so pieces and actors still match in size where they stand. In px, so the book's kit picture can use it.
 */
internal fun kitFrame(wPx: Float, hPx: Float, bottomInsetPx: Float, topInsetPx: Float, tabWPx: Float, tabHPx: Float): SceneFrame {
    val near = minOf(hPx * FEET_NEAR, hPx - bottomInsetPx)
    val far = hPx * FEET_FAR + (near - hPx * FEET_FAR) * KIT_NEAREST_FAR
    return SceneFrame(
        w = wPx, h = hPx,
        feetFar = far,
        feetNear = near,
        tallFar = hPx * (TALL_FAR + (TALL_NEAR - TALL_FAR) * KIT_NEAREST_FAR), tallNear = hPx * TALL_NEAR,
        top = topInsetPx, bottom = hPx - bottomInsetPx,
        tabW = tabWPx, tabH = tabHPx,
    )
}

/** The stage geometry the kit and the plain felt floor share (#242) — the same frame as [kitFrame] */
private fun androidx.compose.ui.unit.Density.stageFrame(wPx: Float, hPx: Float, bottomInset: Dp, topInset: Dp): SceneFrame =
    kitFrame(wPx, hPx, bottomInset.toPx(), topInset.toPx(), TAB_W.toPx(), TAB_H.toPx())

/**
 * The frame the stage last drew a kit in (px) — the book's kit picture uses it so the same seed lays the pieces out the
 * same. The screen size the system reports leaves out the navigation bar (2050 vs 2280 on the S10), which moved pieces.
 */
internal object KitStageFrame {
    @Volatile var last: SceneFrame? = null

    /** The landscape stage's size, noted by every world stage — so even the first kit has the stage's frame */
    fun note(wPx: Float, hPx: Float, density: Float) {
        last = kitFrame(wPx, hPx, BottomChrome.value * density, TopChrome.value * density, TAB_W.value * density, TAB_H.value * density)
    }
}

@Composable
private fun rememberKitScene(kit: SceneKitDef, seedBase: Long, actors: Int, wPx: Float, hPx: Float, bottomInset: Dp, topInset: Dp): Pair<KitScene, SceneFrame> {
    val density = LocalDensity.current
    return remember(kit, seedBase, actors, wPx, hPx, bottomInset, topInset) {
        val f = with(density) { stageFrame(wPx, hPx, bottomInset, topInset) }
        if (bottomInset > 0.dp) KitStageFrame.last = f      // the landscape stage, not the portrait tablet's inner frame
        bestScene(kit, actors, f, seedBase = seedBase) to f
    }
}

/**
 * The kit's felt ground alone, over a background picture that has none (10-06 lead · 「바닥이 됐다 안 됐다」).
 * Only a kit stage had a floor: a generated background, the space and sea pictures and co-op once its picture
 * arrived left the actors standing on 84 % of the screen in mid-air. Same horizon, wave and stitches as the
 * kit's ground, in the colour of the kit the place belongs to — draw it behind the hotspots and the actors.
 */
@Composable
fun FeltFloor(ground: Color, bottomInset: Dp, modifier: Modifier = Modifier, thin: Boolean = false) {
    val grain = remember { ShaderBrush(ImageShader(FeltNoise.grain, TileMode.Repeated, TileMode.Repeated)) }
    BoxWithConstraints(modifier.fillMaxSize().then(if (thin) Modifier.alpha(0.85f) else Modifier)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val f = remember(wPx, hPx, bottomInset) { with(density) { stageFrame(wPx, hPx, bottomInset, 0.dp) } }
        // over a picture that paints its own ground (#257): 60 % as tall, see-through — just under the actors' feet
        val top = if (thin) f.h - (f.h - f.horizon) * 0.6f else f.horizon
        val shape = remember(f, thin) { hillShape(f, 4f, top, 5f, 140f) }
        Canvas(Modifier.fillMaxSize()) {
            hill(f, shape, ground)
            clipPath(shape.fill) { drawRect(grain, alpha = 0.07f, blendMode = BlendMode.Overlay) }
        }
    }
}

@Composable
private fun kitBitmaps(kit: SceneKitDef): Map<String, ImageBitmap> {
    val out = HashMap<String, ImageBitmap>()
    // a fixed list per kit, so the composable calls below keep their order
    for (res in kit.pieces.map { it.res }.distinct()) assetBitmap(res)?.let { out[res] = it }
    // the pictures of who comes by (the bird) — not in the layout, so not among the pieces
    for (res in VISITORS_BY_KIT[kit.key].orEmpty().flatMap { listOf(it.sit, it.fly) }) assetBitmap(res)?.let { out[res] = it }
    return out
}

/** The back layer — sky · sky pieces · hills · far band · ground · ground pieces · visitors. [t] null = still */
private fun DrawScope.drawKitBack(
    kit: SceneKitDef, scene: KitScene, f: SceneFrame, imgs: Map<String, ImageBitmap>, hills: List<HillShape>,
    grain: ShaderBrush, motions: SceneMotions?, t: Double?,
) {
    val skyTop = Color(kit.skyTop)
    val skyBottom = Color(kit.skyBottom)
    fun drawPiece(p: PlacedPiece) = drawPiece(imgs, p, skyBottom, if (t == null || motions == null) PieceMotion.NONE else motions.of(p, t))
    // sky — two-colour felt gradient with soft blotches
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom), endY = f.horizon))
    drawImage(FeltNoise.blotch, srcSize = IntSize(FeltNoise.blotch.width, FeltNoise.blotch.height),
        dstSize = IntSize(size.width.roundToInt(), f.horizon.roundToInt()), alpha = 0.10f,
        blendMode = BlendMode.Overlay, filterQuality = FilterQuality.Low)
    scene.pieces.filter { it.piece.base == PieceBase.CENTER && it.piece.role != PieceRole.FLOAT }
        .sortedBy { it.h }.forEach { drawPiece(it) }
    // hills — two felt layers above the horizon, then the hazy far band
    if (kit.hills) {
        hill(f, hills[0], Color(kit.hillFar))
        hill(f, hills[1], Color(kit.hillNear))
    }
    scene.pieces.filter { it.fade > 0f }.forEach { drawPiece(it) }
    // ground — from the actors' depth-0 feet line down, soft wavy stitched edge
    hill(f, hills[2], Color(kit.ground))
    drawRect(grain, alpha = 0.07f, blendMode = BlendMode.Overlay)
    // ground pieces: flat first, then by feet (far → near), then what floats
    val ground = scene.pieces.filter { it.piece.base == PieceBase.FEET && it.fade == 0f && !it.front }
    ground.filter { it.piece.role == PieceRole.FLAT }.forEach { drawPiece(it) }
    ground.filter { it.piece.role != PieceRole.FLAT }.sortedBy { it.y }.forEach {
        shadow(it)          // the feet never move (a tree bends, it does not slide), so the shadow stays
        drawPiece(it)
    }
    scene.pieces.filter { it.piece.role == PieceRole.FLOAT }.forEach { drawPiece(it) }
    // who comes by — only while the scene is alive; behind the actors, like everything in this layer
    if (t != null && motions != null) motions.visitors(t).forEach { drawVisitor(imgs, it) }
}

/**
 * The kit as **one still picture** — no actors, nothing moving (#222 · 10-06). The stage draws a kit from pieces, but the
 * book, the shelf thumbnail and the puzzle draw one picture (`bgName`): without it a 바닷가 · 실내 · 숲 story's book fell back
 * to the snow picture. Saved like a generated background, so every later screen just reads a file.
 * Laid out **in the stage's own frame** — the landscape screen, the same top · bottom chrome and ↶ ↪ buttons — so the same
 * seed puts every piece where the stage had it: the book page shows the place the child just saw (10-06 · #222).
 */
fun renderKitPicture(context: android.content.Context, kit: SceneKitDef, seedBase: Long, wPx: Int = 0, hPx: Int = 0): Bitmap {
    val res = context.resources
    val opt = android.graphics.BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    @Suppress("DiscouragedApi")
    fun load(name: String) = res.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
        ?.let { android.graphics.BitmapFactory.decodeResource(res, it, opt)?.asImageBitmap() }
    val imgs = kit.pieces.map { it.res }.distinct().mapNotNull { r -> load(r)?.let { r to it } }.toMap()
    val dm = res.displayMetrics
    val density = dm.density
    // size 0 = the frame the stage itself last drew in; before any stage, this screen with the stage's chrome
    val stage = KitStageFrame.last.takeIf { wPx <= 0 }
    val f = stage ?: run {
        val w0 = if (wPx > 0) wPx else max(dm.widthPixels, dm.heightPixels)
        val h0 = if (hPx > 0) hPx else minOf(dm.widthPixels, dm.heightPixels)
        kitFrame(w0.toFloat(), h0.toFloat(), BottomChrome.value * density, TopChrome.value * density, TAB_W.value * density, TAB_H.value * density)
    }
    val w = f.w.roundToInt()
    val h = f.h.roundToInt()
    val scene = bestScene(kit, 2, f, seedBase = seedBase)
    val hills = kitHills(f, scene)
    val grain = ShaderBrush(ImageShader(FeltNoise.grain, TileMode.Repeated, TileMode.Repeated))
    val out = androidx.compose.ui.graphics.ImageBitmap(w, h)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(density), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(out), Size(w.toFloat(), h.toFloat()),
    ) {
        drawKitBack(kit, scene, f, imgs, hills, grain, null, null)
        scene.pieces.filter { it.front }.forEach { drawPiece(imgs, it, Color(kit.skyBottom)) }
    }
    return out.asAndroidBitmap()
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
        val grain = remember { ShaderBrush(ImageShader(FeltNoise.grain, TileMode.Repeated, TileMode.Repeated)) }
        val motions = remember(scene, f, actors) { SceneMotions(scene, f, VISITORS_BY_KIT[kit.key].orEmpty(), actors) }
        // the hills never move — their paths are made once, not every living frame
        val hills = remember(scene, f) { kitHills(f, scene) }
        val clock = sceneClock()
        Canvas(Modifier.fillMaxSize()) { drawKitBack(kit, scene, f, imgs, hills, grain, motions, clock?.value) }
    }
}

/** The pieces in front of the actors — draw after `WorldItemView`s */
@Composable
fun SceneFront(kit: SceneKitDef, seedBase: Long, actors: Int, bottomInset: Dp, topInset: Dp, modifier: Modifier = Modifier) {
    val imgs = kitBitmaps(kit)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val (scene, f) = rememberKitScene(kit, seedBase, actors,
            with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, bottomInset, topInset)
        val skyBottom = Color(kit.skyBottom)
        val motions = remember(scene, f) { SceneMotions(scene, f) }
        val clock = sceneClock()
        Canvas(Modifier.fillMaxSize()) {
            val t = clock?.value
            scene.pieces.filter { it.front }.forEach {
                drawPiece(imgs, it, skyBottom, if (t == null) PieceMotion.NONE else motions.of(it, t))
            }
        }
    }
}

/** The two hills and the ground edge of a scene — one recipe for the stage and the book picture, so they stay alike */
private fun kitHills(f: SceneFrame, scene: KitScene): List<HillShape> {
    val seed = scene.seed.toFloat()
    return listOf(
        hillShape(f, seed, f.horizon - 0.13f * f.h, 16f, 120f),
        hillShape(f, seed + 2, f.horizon - 0.07f * f.h, 12f, 90f),
        hillShape(f, seed + 4, f.horizon, 5f, 140f),
    )
}

/** The three paths of one felt layer: the wavy top edge, the fill down to the bottom, the running stitches under the edge */
private class HillShape(val edge: Path, val fill: Path, val stitch: Path, val y0: Float)

private fun hillShape(f: SceneFrame, seed: Float, y0: Float, amp: Float, freq: Float): HillShape {
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
    return HillShape(edge, fill, stitch, y0)
}

/** A felt layer whose top edge is a soft wave, filled down to the bottom, with a shade line and running stitches */
private fun DrawScope.hill(f: SceneFrame, shape: HillShape, color: Color) {
    val sx = f.w / 1344f
    val sy = f.h / 768f
    drawPath(shape.edge, Color.Black.copy(alpha = 0.10f), style = Stroke(width = 8f * sy))
    val dark = Color(
        max(0f, color.red - 18f / 255f), max(0f, color.green - 18f / 255f), max(0f, color.blue - 18f / 255f),
    )
    drawPath(shape.fill, Brush.verticalGradient(listOf(color, dark), startY = shape.y0, endY = f.h))
    drawPath(shape.stitch, Color(0xFFFFFAEE).copy(alpha = 0.6f), style = Stroke(width = 2f * sy,
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

/** A visitor ([Visitor]): its picture with the bottom centre on (x, y), mirrored when it faces left */
private fun DrawScope.drawVisitor(imgs: Map<String, ImageBitmap>, v: Visitor) {
    val img = imgs[v.res] ?: return
    val w = v.h * v.aspect
    val feet = Offset(v.x, v.y)
    rotate(v.tilt, Offset(v.x, v.y - v.h / 2)) {
        scale(if (v.flip) -1f else 1f, v.scaleY, feet) {
            drawImage(img, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((v.x - w / 2).roundToInt(), (v.y - v.h).roundToInt()),
                dstSize = IntSize(max(1, w.roundToInt()), max(1, v.h.roundToInt())), filterQuality = FilterQuality.Medium)
        }
    }
}

/** Rows of the bend mesh — enough that a leaning trunk reads as a curve, not as a fold */
private const val BEND_ROWS = 8

private fun DrawScope.drawPiece(imgs: Map<String, ImageBitmap>, p: PlacedPiece, haze: Color, m: PieceMotion = PieceMotion.NONE) {
    val img = imgs[p.piece.res] ?: return
    val b = p.box
    val pivot = Offset(p.x, (b.t + b.b) / 2)
    val dst = IntSize(max(1, (b.r - b.l).roundToInt()), max(1, (b.b - b.t).roundToInt()))
    val at = IntOffset(b.l.roundToInt(), b.t.roundToInt())
    val src = IntSize(img.width, img.height)
    translate(m.dx, m.dy) {
        rotate(p.tilt + m.tilt, pivot) {
            scale((if (p.flip) -1f else 1f) * m.scaleX, m.scaleY, pivot) {
                if (m.bend != 0f) {
                    // inside the flip the x axis runs backwards — turn the lean so the wind still blows one way
                    bent(p.piece.res, img, b, if (p.flip) -m.bend else m.bend, m.stiff, p.fade, haze)
                } else if (p.fade > 0f) {
                    // far band: less colour, then a veil of the sky's lower colour — air between us and the horizon
                    val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - p.fade * 0.6f) })
                    drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, colorFilter = grey, filterQuality = FilterQuality.Medium)
                    drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, alpha = p.fade * 0.55f,
                        colorFilter = ColorFilter.tint(haze, BlendMode.SrcIn), filterQuality = FilterQuality.Medium)
                } else {
                    drawImage(img, srcSize = src, dstOffset = at, dstSize = dst, alpha = m.alpha, filterQuality = FilterQuality.Medium)
                }
            }
        }
    }
}

/**
 * Far-band pictures with their haze already in them — the still path lays the haze on in two passes every time,
 * but a mesh takes neither the saturation filter nor the veil's alpha, so the bent path draws one ready picture.
 * A handful of entries: far pieces of the kit on screen × its sky colour.
 */
private object HazedPieces {
    private val made = HashMap<Triple<String, Int, Int>, Bitmap>()

    fun of(res: String, img: ImageBitmap, fade: Float, haze: Color): Bitmap =
        made.getOrPut(Triple(res, (fade * 100).roundToInt(), haze.toArgb())) {
            val src = img.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1f - fade * 0.6f) })
            c.drawBitmap(src, 0f, 0f, paint)
            paint.colorFilter = android.graphics.PorterDuffColorFilter(haze.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
            paint.alpha = (fade * 0.55f * 255).roundToInt()
            c.drawBitmap(src, 0f, 0f, paint)
            out
        }
}

/**
 * The picture bent by the wind: the bottom row stays on the bottom edge of [box] and each row above leans further,
 * the top by [bend] px (`Canvas.drawBitmapMesh`, the way the character rig draws its skin). A rotation would swing
 * the feet off their shadow; a bend keeps the tree planted. A far-band piece is drawn from its hazed copy ([HazedPieces]).
 */
private fun DrawScope.bent(res: String, img: ImageBitmap, box: com.example.finalproject_demo.demo.scene.Box, bend: Float, stiff: Float, fade: Float, haze: Color) {
    val verts = FloatArray((BEND_ROWS + 1) * 4)
    val h = box.b - box.t
    for (j in 0..BEND_ROWS) {
        val up = 1f - j / BEND_ROWS.toFloat()          // 1 at the top row, 0 at the feet
        val lean = bend * up.pow(stiff)
        val y = box.t + h * j / BEND_ROWS
        verts[j * 4] = box.l + lean; verts[j * 4 + 1] = y
        verts[j * 4 + 2] = box.r + lean; verts[j * 4 + 3] = y
    }
    val bmp = if (fade > 0f) HazedPieces.of(res, img, fade, haze) else img.asAndroidBitmap()
    drawIntoCanvas { c ->
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        c.nativeCanvas.drawBitmapMesh(bmp, 1, BEND_ROWS, verts, 0, null, 0, paint)
    }
}
