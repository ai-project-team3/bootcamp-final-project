package com.example.finalproject_demo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import kotlin.math.ceil
import kotlin.math.floor

/** Draw a posed mesh in its original coordinates; the caller owns placement and scaling. */
internal class RigMeshRenderer(private val rig: MeshRig) {
    private var frame: Bitmap? = null
    private var frameCanvas: Canvas? = null
    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, paint: Paint) {
        // Hardware Canvas supports drawVertices only from API 29. A bitmap Canvas
        // supports it on older Android too, preserving the same pose and texture.
        if (Build.VERSION.SDK_INT >= 29 || !canvas.isHardwareAccelerated) {
            drawMesh(canvas, paint)
            return
        }

        val mesh = rig.mesh
        var left = 0f; var top = 0f
        var right = mesh.canvasW; var bottom = mesh.canvasH
        for (i in mesh.out.indices step 2) {
            left = minOf(left, mesh.out[i]); right = maxOf(right, mesh.out[i])
            top = minOf(top, mesh.out[i + 1]); bottom = maxOf(bottom, mesh.out[i + 1])
        }
        left = floor(left); top = floor(top)
        val width = (ceil(right) - left).toInt().coerceAtLeast(1)
        val height = (ceil(bottom) - top).toInt().coerceAtLeast(1)
        val old = frame
        if (old == null || width > old.width || height > old.height) {
            // Grow only when needed; avoid allocating a bitmap for every animation frame.
            val w = if (old != null && width > old.width) maxOf(width, old.width * 2) else maxOf(width, old?.width ?: 0)
            val h = if (old != null && height > old.height) maxOf(height, old.height * 2) else maxOf(height, old?.height ?: 0)
            frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            frameCanvas = Canvas(frame!!)
        }
        val bitmap = frame!!
        val software = frameCanvas!!
        bitmap.eraseColor(Color.TRANSPARENT)
        val save = software.save()
        try {
            software.translate(-left, -top)
            drawMesh(software, paint)
        } finally {
            software.restoreToCount(save)
        }
        // The caller's scale, position and bobbing still apply; include vertices that
        // moved beyond the original canvas so raised arms and tails are not clipped.
        canvas.drawBitmap(bitmap, left, top, framePaint)
    }

    private fun drawMesh(canvas: Canvas, paint: Paint) {
        val mesh = rig.mesh
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.out.size, mesh.out, 0, rig.drawTex, 0,
            null, 0, mesh.indices, 0, mesh.indices.size, paint)
    }
}
