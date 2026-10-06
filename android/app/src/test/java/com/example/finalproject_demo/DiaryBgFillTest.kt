package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.PieceRole
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.addStroke
import com.example.finalproject_demo.demo.newDiaryDay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 판을 끝에서 끝까지 **칠해서** 그린 배경(모래 · 바다 · 하늘)도 배경이다.
 * 10-06 실기기(15:23 · 진웅): 노란 모래와 파란 바다를 지그재그로 칠했더니 둘 다 높이가 납작한 선 기준(0.25)을 넘어
 * 물체가 됐고, 닿아 있어 한 물체 「바닷가」로 묶여 물체로 다시 그려졌다. 아래 두 획은 그때 획 기록(diary_trace)을 세 점마다 하나씩 줄인 것
 */
class DiaryBgFillTest {
    private fun stroke(c: Color, xy: FloatArray) = Stroke(c, xy.toList().chunked(2).map { Offset(it[0], it[1]) })

    private val sand = floatArrayOf(0.035f, 0.651f, 0.050f, 0.653f, 0.085f, 0.657f, 0.134f, 0.662f, 0.196f, 0.668f, 0.259f, 0.672f, 0.320f, 0.672f, 0.379f, 0.672f, 0.438f, 0.672f, 0.496f, 0.677f, 0.555f, 0.677f, 0.613f, 0.667f, 0.667f, 0.657f, 0.716f, 0.650f, 0.762f, 0.646f, 0.804f, 0.641f, 0.847f, 0.634f, 0.897f, 0.619f, 0.919f, 0.619f, 0.936f, 0.626f, 0.953f, 0.636f, 0.965f, 0.650f, 0.972f, 0.665f, 0.974f, 0.679f, 0.963f, 0.688f, 0.944f, 0.690f, 0.925f, 0.689f, 0.910f, 0.691f, 0.894f, 0.700f, 0.875f, 0.710f, 0.847f, 0.731f, 0.763f, 0.824f, 0.717f, 0.877f, 0.763f, 0.830f, 0.881f, 0.741f, 0.947f, 0.717f, 0.913f, 0.768f, 0.841f, 0.840f, 0.847f, 0.829f, 0.920f, 0.776f, 0.917f, 0.830f, 0.865f, 0.915f, 0.886f, 0.895f, 0.966f, 0.841f, 0.936f, 0.903f, 0.879f, 0.979f, 0.960f, 0.879f, 1.020f, 0.830f, 0.914f, 0.923f, 0.841f, 0.947f, 0.892f, 0.813f, 0.879f, 0.761f, 0.707f, 0.889f, 0.680f, 0.887f, 0.822f, 0.731f, 0.845f, 0.719f, 0.684f, 0.909f, 0.662f, 0.917f, 0.802f, 0.743f, 0.784f, 0.743f, 0.601f, 0.897f, 0.577f, 0.892f, 0.716f, 0.734f, 0.710f, 0.753f, 0.534f, 0.957f, 0.537f, 0.943f, 0.725f, 0.747f, 0.743f, 0.768f, 0.629f, 0.956f, 0.669f, 0.968f, 0.865f, 0.867f, 0.864f, 0.901f, 0.730f, 1.016f, 0.737f, 0.995f, 0.858f, 0.834f, 0.815f, 0.846f, 0.647f, 0.934f, 0.652f, 0.857f, 0.781f, 0.704f, 0.717f, 0.754f, 0.539f, 0.897f, 0.540f, 0.863f, 0.685f, 0.728f, 0.657f, 0.753f, 0.500f, 0.875f, 0.502f, 0.842f, 0.664f, 0.693f, 0.610f, 0.731f, 0.430f, 0.888f, 0.448f, 0.845f, 0.583f, 0.698f, 0.514f, 0.751f, 0.334f, 0.919f, 0.340f, 0.895f, 0.464f, 0.753f, 0.418f, 0.797f, 0.268f, 0.959f, 0.305f, 0.897f, 0.454f, 0.729f, 0.394f, 0.786f, 0.223f, 0.966f, 0.271f, 0.864f, 0.394f, 0.692f, 0.309f, 0.770f, 0.150f, 0.950f, 0.183f, 0.857f, 0.245f, 0.739f, 0.137f, 0.854f, 0.040f, 0.950f, 0.091f, 0.819f, 0.103f, 0.773f, 0.013f, 0.863f, 0.016f, 0.836f, 0.122f, 0.672f, 0.108f, 0.695f, 0.044f, 0.791f, 0.089f, 0.760f, 0.162f, 0.737f, 0.164f, 0.821f, 0.218f, 0.871f, 0.397f, 0.785f, 0.431f, 0.811f, 0.375f, 0.942f, 0.435f, 0.926f, 0.587f, 0.825f, 0.591f, 0.853f, 0.509f, 0.962f, 0.625f, 0.856f, 0.631f, 0.835f, 0.481f, 0.944f, 0.481f, 0.944f)
    private val sea = floatArrayOf(0.117f, 0.501f, 0.198f, 0.384f, 0.265f, 0.324f, 0.196f, 0.399f, 0.090f, 0.512f, 0.076f, 0.516f, 0.191f, 0.373f, 0.218f, 0.340f, 0.098f, 0.483f, 0.063f, 0.527f, 0.151f, 0.405f, 0.146f, 0.408f, 0.041f, 0.523f, 0.039f, 0.445f, 0.139f, 0.234f, 0.170f, 0.158f, 0.114f, 0.216f, 0.038f, 0.352f, 0.041f, 0.402f, 0.182f, 0.290f, 0.303f, 0.224f, 0.243f, 0.344f, 0.145f, 0.518f, 0.167f, 0.505f, 0.347f, 0.336f, 0.383f, 0.337f, 0.244f, 0.521f, 0.184f, 0.611f, 0.346f, 0.427f, 0.482f, 0.279f, 0.406f, 0.348f, 0.246f, 0.506f, 0.230f, 0.463f, 0.394f, 0.195f, 0.449f, 0.119f, 0.305f, 0.261f, 0.210f, 0.358f, 0.281f, 0.245f, 0.460f, 0.073f, 0.461f, 0.128f, 0.324f, 0.363f, 0.304f, 0.445f, 0.503f, 0.300f, 0.627f, 0.237f, 0.554f, 0.362f, 0.429f, 0.547f, 0.508f, 0.458f, 0.745f, 0.206f, 0.743f, 0.205f, 0.506f, 0.435f, 0.369f, 0.557f, 0.460f, 0.405f, 0.660f, 0.178f, 0.605f, 0.216f, 0.386f, 0.434f, 0.331f, 0.486f, 0.548f, 0.285f, 0.776f, 0.170f, 0.714f, 0.288f, 0.569f, 0.507f, 0.622f, 0.509f, 0.867f, 0.348f, 0.883f, 0.357f, 0.723f, 0.525f, 0.689f, 0.558f, 0.884f, 0.408f, 0.943f, 0.380f, 0.839f, 0.489f, 0.785f, 0.546f, 0.894f, 0.468f, 0.970f, 0.435f, 0.942f, 0.467f, 0.896f, 0.465f, 0.968f, 0.288f, 0.959f, 0.167f, 0.811f, 0.185f, 0.693f, 0.210f, 0.649f, 0.191f, 0.754f, 0.064f, 0.872f, 0.001f, 0.742f, 0.095f, 0.534f, 0.269f, 0.494f, 0.291f, 0.576f, 0.228f, 0.569f, 0.263f, 0.423f, 0.435f, 0.402f, 0.477f, 0.547f, 0.428f, 0.643f, 0.440f, 0.607f, 0.536f, 0.582f, 0.595f, 0.747f, 0.520f, 0.870f, 0.481f, 0.849f, 0.522f, 0.776f, 0.580f, 0.847f, 0.517f, 0.919f, 0.459f, 0.767f, 0.510f, 0.533f, 0.573f, 0.380f, 0.559f, 0.258f, 0.519f, 0.130f, 0.530f, 0.052f, 0.551f, 0.061f, 0.529f, 0.216f, 0.423f, 0.405f, 0.391f, 0.425f, 0.486f, 0.384f, 0.574f, 0.393f, 0.590f, 0.551f, 0.555f, 0.691f, 0.537f, 0.667f, 0.561f, 0.455f, 0.658f, 0.328f, 0.666f, 0.280f, 0.619f, 0.209f, 0.576f, 0.117f, 0.571f, 0.082f, 0.561f, 0.099f, 0.548f, 0.264f, 0.508f, 0.525f, 0.454f, 0.784f, 0.407f, 0.854f, 0.388f, 0.720f, 0.378f, 0.486f, 0.373f, 0.284f, 0.325f, 0.160f, 0.261f, 0.125f, 0.175f, 0.222f, 0.040f, 0.364f, 0.003f, 0.364f, 0.056f, 0.175f, 0.274f, 0.064f, 0.359f, 0.121f, 0.270f, 0.354f, 0.092f, 0.529f, 0.043f, 0.496f, 0.080f, 0.228f, 0.238f, 0.067f, 0.255f, 0.079f, 0.156f, 0.218f, 0.038f, 0.457f, -0.002f, 0.619f, 0.059f, 0.592f, 0.264f, 0.545f, 0.465f, 0.636f, 0.477f, 0.870f, 0.373f, 1.100f, 0.315f, 1.101f, 0.315f)

    private val yellow = Color(-801988)
    private val blue = Color(-12616743)

    @Test
    fun aSandAndASeaColouredAcrossTheBoardAreTheBackground() {
        val d = DemoState().newDiaryDay()
        val sandId = d.addStroke(stroke(yellow, sand))
        val seaId = d.addStroke(stroke(blue, sea))
        assertEquals("칠한 모래가 배경이 아니다", PieceRole.BACKGROUND, d.pieces.first { it.id == sandId }.role)
        assertEquals("칠한 바다가 배경이 아니다", PieceRole.BACKGROUND, d.pieces.first { it.id == seaId }.role)
        assertEquals("물체 조각이 생겼다: ${d.pieces.map { it.role }}", 0, d.pieces.count { it.role == PieceRole.OBJECT })
    }

    /** 판 반쯤 되는 큰 물건(고래)을 칠한 것은 물체 그대로 — 끝에서 끝까지 가로지르지 않는다 */
    @Test
    fun aBigThingColouredInIsStillAThing() {
        val zigzag = (0..20).flatMap { k -> listOf(if (k % 2 == 0) 0.25f else 0.75f, 0.30f + k * 0.02f) }.toFloatArray()
        val d = DemoState().newDiaryDay()
        val id = d.addStroke(stroke(blue, zigzag))
        assertEquals(PieceRole.OBJECT, d.pieces.first { it.id == id }.role)
    }

    /** 끝에서 끝까지 가도 한 번 쓱 그은 큰 언덕 선(오가지 않음)은 지금처럼 높이로 가린다 */
    @Test
    fun aTallHillLineDrawnOnceIsNotAFill() {
        val hill = floatArrayOf(0.02f, 0.90f, 0.25f, 0.55f, 0.50f, 0.40f, 0.75f, 0.55f, 0.98f, 0.90f)
        val d = DemoState().newDiaryDay()
        val id = d.addStroke(stroke(Color.Green, hill))
        assertEquals(PieceRole.OBJECT, d.pieces.first { it.id == id }.role)
    }
}
