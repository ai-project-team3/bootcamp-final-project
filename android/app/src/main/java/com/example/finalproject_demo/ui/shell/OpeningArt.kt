package com.example.finalproject_demo.ui.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.finalproject_demo.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** Coordinates are dp in the real room, never a second copy of the HTML's pretend menu. */
internal data class OpeningPlacement(val stage: RectF, val homeX: Float, val homeY: Float, val catSize: Float)

internal data class OpeningCamera(val scale: Float, val x: Float, val y: Float)

internal fun openingCamera(t: Float, w: Float, h: Float, placement: OpeningPlacement): OpeningCamera {
    val stage = placement.stage
    val p = OpeningMotion.smooth(OpeningMotion.progress(t, 4050f, 6400f))
    // Fit the complete stage: targetSdk 36 tablets can rotate to portrait.
    val zoom = minOf(w / stage.width(), h / stage.height())
    var scale = exp(ln(zoom) * (1f - p))
    var camera = OpeningCamera(scale,
        w / 2 - OpeningMotion.mix(stage.centerX(), w / 2, p) * scale,
        h / 2 - OpeningMotion.mix(stage.centerY(), h / 2, p) * scale)
    if (t >= 4480f) {
        // The real theater is higher than the PoC's: frame the whole jumping sprite.
        val cat = openingCat(t, placement)
        val bounds = RectF(stage)
        bounds.union(cat.x - cat.scale / 2, cat.y - cat.scale, cat.x + cat.scale / 2, cat.y + cat.scale * .025f)
        val margin = minOf(w, h) * .025f
        scale = minOf(scale, (w - margin * 2) / bounds.width(), (h - margin * 2) / bounds.height())
        val minX = margin - bounds.left * scale
        val minY = margin - bounds.top * scale
        camera = OpeningCamera(scale,
            camera.x.coerceIn(minX, maxOf(minX, w - margin - bounds.right * scale)),
            camera.y.coerceIn(minY, maxOf(minY, h - margin - bounds.bottom * scale)))
    }
    return camera
}

internal fun openingCat(t: Float, placement: OpeningPlacement): OpeningMotion.Point {
    val r = placement.stage
    val k = r.width() / 1200f
    return OpeningMotion.cat(t,
        OpeningMotion.Point(r.left + 886.5f * k, r.top + 552f * k, 285f * k),
        OpeningMotion.Point(placement.homeX, placement.homeY, placement.catSize), placement.catSize / 234f)
}

/** Bitmaps are decoded once off the UI thread, then retained only for this opening. */
internal class OpeningArt(private val context: Context) {
    private fun asset(name: String) = context.assets.open("opening/$name").use { BitmapFactory.decodeStream(it)!! }
    private val stage = asset("stage-premium.png")
    private val curtain = asset("curtain-premium.png")
    private val rope = asset("rope-premium.png")
    private val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_otto_v2, BitmapFactory.Options().apply { inScaled = false })
    private val cat = BitmapFactory.decodeResource(context.resources, R.drawable.otto_pose_wave, BitmapFactory.Options().apply { inScaled = false })
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val font: Typeface = ResourcesCompat.getFont(context, R.font.jua) ?: Typeface.DEFAULT

    private fun Canvas.image(bitmap: Bitmap, rect: RectF) { drawBitmap(bitmap, null, rect, paint) }
    private inline fun Canvas.saved(block: Canvas.() -> Unit) { val save = save(); try { block() } finally { restoreToCount(save) } }

    fun draw(canvas: Canvas, time: Float, pulled: Float, releasedPull: Float, placement: OpeningPlacement) = with(canvas) {
        val r = placement.stage
        val k = r.width() / 1200f
        val fade = 1f - OpeningMotion.progress(time, 4050f, 4850f)
        if (fade > 0f) saved {
            translate(r.left, r.top); scale(k, k)
            clipRect(0f, 0f, 1200f, 675f)
            val faded = saveLayerAlpha(0f, 0f, 1200f, 675f, (255 * fade).toInt())
            image(stage, RectF(0f, 0f, 1200f, 675f))
            sign(time, false); sign(time, true)
            if (time < 4480f) sprite(886.5f, 552f, 285f)
            curtains(time)
            // Keep the scalloped valance in front of the two moving curtain leaves.
            val valance = Path().apply {
                moveTo(0f, 0f); lineTo(1200f, 0f); lineTo(1200f, 120f)
                cubicTo(1130f, 122f, 1040f, 85f, 1012f, 50f)
                cubicTo(968f, 85f, 910f, 84f, 866f, 40f)
                cubicTo(823f, 82f, 750f, 86f, 706f, 40f)
                cubicTo(650f, 105f, 548f, 108f, 492f, 40f)
                cubicTo(450f, 82f, 375f, 90f, 334f, 40f)
                cubicTo(287f, 83f, 235f, 91f, 187f, 47f)
                cubicTo(145f, 102f, 63f, 123f, 0f, 120f); close()
            }
            saved { clipPath(valance); image(stage, RectF(0f, 0f, 1200f, 675f)) }
            if (time < 2300f) {
                val stretch = if (time == 0f) pulled else {
                    val p = OpeningMotion.progress(time, 0f, 1080f)
                    releasedPull * exp(-6f * p) * kotlin.math.cos(PI.toFloat() * 4f * p)
                }
                saved {
                    translate(1091f, -40f)
                    scale(1f - stretch / 2700f, 1f + stretch / 453f)
                    image(rope, RectF(-151f, 0f, 151f, 453f))
                }
            }
            restoreToCount(faded)
        }
        if (time >= 4480f) {
            val p = openingCat(time, placement)
            sprite(p.x, p.y, p.scale)
        }
    }

    private fun Canvas.sprite(x: Float, foot: Float, width: Float) {
        // The complete approved sprite uses only uniform scaling and translation.
        paint.color = Color.argb(48, 78, 48, 23)
        drawOval(RectF(x - width * .27f, foot - width * .035f, x + width * .27f, foot + width * .025f), paint)
        paint.alpha = 255
        image(cat, RectF(x - width / 2, foot - width, x + width / 2, foot))
    }

    private fun Canvas.curtains(t: Float) {
        for (right in listOf(false, true)) saved {
            val p = OpeningMotion.smooth(OpeningMotion.progress(t, if (right) 845f else 780f, if (right) 2475f else 2410f))
            val direction = if (right) 1 else -1
            translate(if (right) 1212f else -12f, -35f)
            translate(direction * 623f * .88f * p, 0f)
            scale(OpeningMotion.mix(1f, .24f, p), 1f)
            if (right) scale(-1f, 1f)
            paint.color = Color.rgb(137, 24, 20)
            drawRect(15f, 18f, 616f, 622f, paint)
            image(curtain, RectF(-15f, -20f, 638f, 653f))
        }
    }

    private fun Canvas.sign(t: Float, subtitle: Boolean) = saved {
        val y = if (subtitle) OpeningMotion.signY(t, 1400f, 2920f, 3660f) else OpeningMotion.signY(t, 1180f, 2650f, 3520f)
        translate(0f, y)
        val left = if (subtitle) 241f else 269f
        val top = if (subtitle) 414f else 184f
        val width = if (subtitle) 470f else 394f
        val inset = if (subtitle) 33f else 54f
        paint.color = Color.rgb(183, 142, 72); paint.strokeWidth = if (subtitle) 4f else 6f
        for (x in listOf(left + inset, left + width - inset)) drawLine(x, -650f, x, top + 25f, paint)
        // The original is an atlas: title upper-left, mascot right, old subtitle below.
        if (!subtitle) drawBitmap(logo, Rect(0, 0, 394, 222), RectF(left, top, left + width, top + 222f), paint)
        else {
            paint.color = Color.rgb(71, 159, 139)
            drawRoundRect(RectF(left, top, left + width, top + 110f), 55f, 55f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            paint.pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f)
            paint.color = Color.rgb(211, 239, 223)
            drawRoundRect(RectF(left + 9f, top + 9f, left + width - 9f, top + 101f), 48f, 48f, paint)
            paint.style = Paint.Style.FILL; paint.pathEffect = null
            paint.color = Color.rgb(255, 251, 237); paint.typeface = font; paint.textSize = 52f; paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            drawText("말로 만드는 그림책", left + width / 2, top + 55f - (fm.ascent + fm.descent) / 2, paint)
        }
    }
}
