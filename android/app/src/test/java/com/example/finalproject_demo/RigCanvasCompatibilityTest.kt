package com.example.finalproject_demo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import com.example.finalproject_demo.ui.MeshRig
import com.example.finalproject_demo.ui.RigMesh
import com.example.finalproject_demo.ui.RigMeshRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RigCanvasCompatibilityTest {
    // Emulate the documented hardware limitation, while drawing the result into
    // a real bitmap. Robolectric screenshots otherwise use a software canvas.
    private class HardwareTarget(val target: Bitmap) : Canvas(target) {
        var vertexCalls = 0
        var bitmapCalls = 0
        var lastFrame: Bitmap? = null
        override fun isHardwareAccelerated() = true
        override fun drawVertices(mode: VertexMode, vertexCount: Int, verts: FloatArray, vertOffset: Int,
            texs: FloatArray?, texOffset: Int, colors: IntArray?, colorOffset: Int,
            indices: ShortArray?, indexOffset: Int, indexCount: Int, paint: Paint) {
            check(Build.VERSION.SDK_INT >= 29) { "drawVertices is unsupported on legacy hardware" }
            vertexCalls++
            super.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors,
                colorOffset, indices, indexOffset, indexCount, paint)
        }
        override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
            bitmapCalls++
            lastFrame = bitmap
            super.drawBitmap(bitmap, left, top, paint)
        }
    }

    private fun rig(): MeshRig {
        val texture = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val vertices = floatArrayOf(4f, 4f, 20f, 4f, 4f, 20f)
        val mesh = RigMesh("blob", 32f, 32f, emptyList(), vertices, vertices.copyOf(),
            Array(3) { intArrayOf() }, Array(3) { floatArrayOf() }, shortArrayOf(0, 1, 2))
        vertices.copyInto(mesh.out)
        return MeshRig(mesh, texture)
    }

    private fun paint(rig: MeshRig) = Paint().apply {
        shader = BitmapShader(rig.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    @Test @Config(sdk = [26])
    fun androidEightHardwareShowsTheTexturedPuppet() {
        val rig = rig()
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        RigMeshRenderer(rig).draw(target, paint(rig))
        assertEquals(0, target.vertexCalls)
        assertEquals(1, target.bitmapCalls)
        assertEquals(Color.RED, target.target.getPixel(8, 8))
    }

    @Test @Config(sdk = [29])
    fun supportedHardwareKeepsItsDirectMeshPath() {
        val rig = rig()
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        RigMeshRenderer(rig).draw(target, paint(rig))
        assertEquals(1, target.vertexCalls)
        assertEquals(0, target.bitmapCalls)
        assertEquals(Color.RED, target.target.getPixel(8, 8))
    }

    @Test @Config(sdk = [26])
    fun legacyFramesReuseTheirBufferWithoutLeavingOldPuppetPixels() {
        val rig = rig()
        val renderer = RigMeshRenderer(rig)
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        renderer.draw(target, paint(rig))
        val frame = target.lastFrame
        target.target.eraseColor(Color.TRANSPARENT)
        for (i in rig.mesh.out.indices step 2) rig.mesh.out[i] += 10f
        renderer.draw(target, paint(rig))
        assertSame(frame, target.lastFrame)
        assertEquals(Color.TRANSPARENT, target.target.getPixel(8, 8))
        assertEquals(Color.RED, target.target.getPixel(18, 8))
    }

    @Test @Config(sdk = [26])
    fun transformedPuppetOutsideItsOriginalCanvasIsNotClipped() {
        val rig = rig()
        for (i in rig.mesh.out.indices step 2) rig.mesh.out[i] -= 12f
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        target.translate(20f, 20f)
        RigMeshRenderer(rig).draw(target, paint(rig))
        assertEquals(Color.RED, target.target.getPixel(16, 28))
        assertTrue(target.bitmapCalls > 0)
    }

    @Test @Config(sdk = [26])
    fun textureTransparencyIsPreserved() {
        val rig = rig()
        rig.bitmap.eraseColor(Color.argb(128, 255, 0, 0))
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        RigMeshRenderer(rig).draw(target, paint(rig))
        assertEquals(128, Color.alpha(target.target.getPixel(8, 8)))
    }

    @Test @Config(sdk = [26])
    fun softwareCanvasStillUsesTheDirectMeshPath() {
        val rig = rig()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        RigMeshRenderer(rig).draw(Canvas(bitmap), paint(rig))
        assertEquals(Color.RED, bitmap.getPixel(8, 8))
    }

    @Test @Config(sdk = [26])
    fun legacyFrameMatchesTheExistingSoftwarePaintSemantics() {
        val rig = rig()
        val expected = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val target = HardwareTarget(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        val renderer = RigMeshRenderer(rig)
        val paint = paint(rig).apply { alpha = 128 }
        renderer.draw(Canvas(expected), paint)
        renderer.draw(target, paint)
        assertEquals(expected.getPixel(8, 8), target.target.getPixel(8, 8))
    }
}
