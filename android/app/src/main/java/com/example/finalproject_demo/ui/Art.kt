package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * 웹 데모(엔드픽처_데모.html)의 SVG 그림을 그대로 옮기기 위한 최소 경로 파서.
 * M L H V Q Z (대문자 절대 · 소문자 상대)만 지원 — 데모 그림은 이 범위 안에서 그려져 있다.
 */
fun svgPath(d: String): Path {
    val p = Path()
    var i = 0
    var cx = 0f; var cy = 0f
    var sx = 0f; var sy = 0f
    var cmd = ' '

    fun skipSep() {
        while (i < d.length && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++
    }

    fun num(): Float {
        skipSep()
        val st = i
        if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
        while (i < d.length && (d[i].isDigit() || d[i] == '.')) i++
        return d.substring(st, i).toFloatOrNull() ?: 0f
    }

    while (i < d.length) {
        skipSep()
        if (i >= d.length) break
        if (d[i].isLetter()) { cmd = d[i]; i++ }
        when (cmd) {
            'M' -> { cx = num(); cy = num(); p.moveTo(cx, cy); sx = cx; sy = cy; cmd = 'L' }
            'm' -> { cx += num(); cy += num(); p.moveTo(cx, cy); sx = cx; sy = cy; cmd = 'l' }
            'L' -> { cx = num(); cy = num(); p.lineTo(cx, cy) }
            'l' -> { cx += num(); cy += num(); p.lineTo(cx, cy) }
            'H' -> { cx = num(); p.lineTo(cx, cy) }
            'h' -> { cx += num(); p.lineTo(cx, cy) }
            'V' -> { cy = num(); p.lineTo(cx, cy) }
            'v' -> { cy += num(); p.lineTo(cx, cy) }
            'Q' -> { val x1 = num(); val y1 = num(); cx = num(); cy = num(); p.quadraticBezierTo(x1, y1, cx, cy) }
            'q' -> {
                val bx = cx; val by = cy
                val x1 = bx + num(); val y1 = by + num()
                cx = bx + num(); cy = by + num()
                p.quadraticBezierTo(x1, y1, cx, cy)
            }
            'Z', 'z' -> { p.close(); cx = sx; cy = sy }
            else -> i++
        }
    }
    return p
}

/** 그림 한 조각. SVG의 path · circle · ellipse · rect · text에 해당한다. */
sealed interface Bit {
    data class P(
        val d: String,
        val fill: Color? = null,
        val stroke: Color? = Ink,
        val w: Float = 3f,
        val alpha: Float = 1f
    ) : Bit

    data class C(
        val cx: Float, val cy: Float, val r: Float,
        val fill: Color? = null, val stroke: Color? = Ink, val w: Float = 3f, val alpha: Float = 1f
    ) : Bit

    data class E(
        val cx: Float, val cy: Float, val rx: Float, val ry: Float,
        val fill: Color? = null, val stroke: Color? = Ink, val w: Float = 3f, val alpha: Float = 1f
    ) : Bit

    data class R(
        val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 0f,
        val fill: Color? = null, val stroke: Color? = Ink, val sw: Float = 3f
    ) : Bit

    /** 이모지·글자. 실제 앱에서는 ComfyUI 그림이 들어갈 자리표시. */
    data class T(val cx: Float, val cy: Float, val size: Float, val text: String) : Bit
}

/** viewBox 좌표계 위에 그린 그림 하나. */
data class Figure(val vw: Float, val vh: Float, val bits: List<Bit>)

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawBits(fig: Figure, tm: TextMeasurer) {
    val k = min(size.width / fig.vw, size.height / fig.vh)
    val dx = (size.width - fig.vw * k) / 2f
    val dy = (size.height - fig.vh * k) / 2f
    translate(dx, dy) {
        scale(k, k, pivot = Offset.Zero) {
            fig.bits.forEach { b ->
                when (b) {
                    is Bit.P -> {
                        val path = svgPath(b.d)
                        b.fill?.let { drawPath(path, it, alpha = b.alpha) }
                        b.stroke?.let { drawPath(path, it, alpha = b.alpha, style = Stroke(b.w)) }
                    }
                    is Bit.C -> {
                        b.fill?.let { drawCircle(it, b.r, Offset(b.cx, b.cy), alpha = b.alpha) }
                        b.stroke?.let { drawCircle(it, b.r, Offset(b.cx, b.cy), alpha = b.alpha, style = Stroke(b.w)) }
                    }
                    is Bit.E -> {
                        val topLeft = Offset(b.cx - b.rx, b.cy - b.ry)
                        val sz = Size(b.rx * 2, b.ry * 2)
                        b.fill?.let { drawOval(it, topLeft, sz, alpha = b.alpha) }
                        b.stroke?.let { drawOval(it, topLeft, sz, alpha = b.alpha, style = Stroke(b.w)) }
                    }
                    is Bit.R -> {
                        val topLeft = Offset(b.x, b.y)
                        val sz = Size(b.w, b.h)
                        val corner = androidx.compose.ui.geometry.CornerRadius(b.r, b.r)
                        b.fill?.let { drawRoundRect(it, topLeft, sz, corner) }
                        b.stroke?.let { drawRoundRect(it, topLeft, sz, corner, style = Stroke(b.sw)) }
                    }
                    is Bit.T -> {
                        val layout = tm.measure(b.text, TextStyle(fontSize = b.size.toSp()))
                        drawText(
                            layout,
                            topLeft = Offset(
                                b.cx - layout.size.width / 2f,
                                b.cy - layout.size.height / 2f
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FigureView(fig: Figure, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) { drawBits(fig, tm) }
}

/** 이모지 한 글자를 그림처럼 크게 (웹 데모의 이모지 자리표시와 같은 역할). */
@Composable
fun EmojiView(emoji: String, modifier: Modifier = Modifier, size: Float = 70f) {
    FigureView(Figure(100f, 100f, listOf(Bit.T(50f, 50f, size, emoji))), modifier)
}

// ─────────────────────────────────────────────────────────────
// 그림들 — 웹 데모의 SVG.* 와 1:1
// ─────────────────────────────────────────────────────────────

val Mascot = Figure(
    100f, 100f, listOf(
        Bit.E(50f, 92f, 26f, 5f, fill = Color(0x14000000), stroke = null),
        Bit.P("M20 55 Q50 5 80 55 Q85 90 50 92 Q15 90 20 55Z", Sun, Color.White, 4f),
        Bit.C(38f, 50f, 8f, Color.White, null),
        Bit.C(62f, 50f, 8f, Color.White, null),
        Bit.C(40f, 52f, 4f, Ink, null),
        Bit.C(64f, 52f, 4f, Ink, null),
        Bit.P("M42 68 Q50 76 58 68", null, Ink, 3f),
        Bit.P("M45 66 L50 60 L55 66Z", Orange, null),
        Bit.C(28f, 62f, 5f, Color(0xFFFF9AA2), null, alpha = 0.8f),
        Bit.C(72f, 62f, 5f, Color(0xFFFF9AA2), null, alpha = 0.8f),
        Bit.P("M22 78 l-10 6", null, Orange, 5f),
        Bit.P("M78 78 l10 6", null, Orange, 5f),
        Bit.P("M30 25 l-6 -12", null, Orange, 4f),
        Bit.P("M70 25 l6 -12", null, Orange, 4f),
    )
)

data class HeroAttr(
    val hair: String = "short",
    // 실제로 있는 세 색 중 하나여야 한다 — 전에는 `0xFF5DADE2`(연한 파랑)였는데
    // 그 색의 그림이 없어서 그냥 파랑으로 떨어졌다 (9/21 그림 구조 변경)
    val shirt: Color = Color(0xFF3F7BD9),
    val eyes: String = "round",
    val glasses: String = "none",
    val likes: String = "dino",
    /**
     * 하의 — `pants`(긴바지) · `skirt`(치마) · `shorts`(반바지).
     *
     * 한때 성별(남 · 여)을 먼저 고르게 하고 여기 기본값을 정해 주었는데, **성별 선택은 뺐다** (9/21).
     * 아이는 바지든 치마든 그냥 고르면 된다 — 성별을 먼저 묻고 옷을 정해 주는 순서가 아니다.
     */
    val bottom: String = "pants",
)

fun hero(a: HeroAttr): Figure {
    val bits = mutableListOf<Bit>()
    val hairColor = Color(0xFF5A3A1E)
    when (a.hair) {
        "long" -> bits += Bit.P("M28 40 Q30 110 45 120 L75 120 Q90 110 92 40Z", hairColor)
        "tied" -> bits += Bit.C(60f, 18f, 10f, hairColor)
    }
    bits += Bit.C(60f, 48f, 30f, Color(0xFFFFD9B3))
    bits += Bit.P("M32 40 Q60 5 88 40 Q75 28 60 30 Q45 28 32 40Z", hairColor)
    when (a.glasses) {
        "square" -> {
            bits += Bit.R(39f, 42f, 18f, 16f, 3f, null)
            bits += Bit.R(63f, 42f, 18f, 16f, 3f, null)
            bits += Bit.P("M57 50 h6", null)
        }
        "round" -> {
            bits += Bit.C(48f, 50f, 9f, null)
            bits += Bit.C(72f, 50f, 9f, null)
            bits += Bit.P("M57 50 h6", null)
        }
    }
    when (a.eyes) {
        "smile" -> bits += Bit.P("M43 52 q5 -6 10 0 M67 52 q5 -6 10 0", null)
        "star" -> {
            bits += Bit.T(48f, 51f, 13f, "★")
            bits += Bit.T(72f, 51f, 13f, "★")
        }
        else -> {
            bits += Bit.C(48f, 51f, 3f, Ink, null)
            bits += Bit.C(72f, 51f, 3f, Ink, null)
        }
    }
    bits += Bit.P("M52 64 Q60 70 68 64", null)
    bits += Bit.C(40f, 60f, 4f, Color(0xFFFF9AA2), null)
    bits += Bit.C(80f, 60f, 4f, Color(0xFFFF9AA2), null)
    bits += Bit.R(35f, 80f, 50f, 55f, 12f, a.shirt)
    bits += Bit.T(60f, 110f, 22f, when (a.likes) { "dino" -> "🦕"; "car" -> "🚗"; else -> "⭐" })
    bits += Bit.C(35f, 86f, 4f, Color.White)
    bits += Bit.C(85f, 86f, 4f, Color.White)
    bits += Bit.P("M35 88 l-16 30", null, Color(0xFFFFD9B3), 10f)
    bits += Bit.P("M85 88 l16 30", null, Color(0xFFFFD9B3), 10f)
    bits += Bit.P("M45 135 l-4 28", null, Color(0xFF4A6FB5), 12f)
    bits += Bit.P("M75 135 l4 28", null, Color(0xFF4A6FB5), 12f)
    bits += Bit.C(45f, 135f, 4f, Color.White)
    bits += Bit.C(75f, 135f, 4f, Color.White)
    return Figure(120f, 170f, bits)
}

val Rocket = Figure(
    120f, 200f, listOf(
        Bit.P("M60 10 Q100 60 90 150 L30 150 Q20 60 60 10Z", Coral),
        Bit.C(60f, 80f, 18f, Color(0xFFBDEBFF)),
        Bit.P("M30 120 L5 165 L32 150Z", Sun),
        Bit.P("M90 120 L115 165 L88 150Z", Sun),
        Bit.P("M42 150 Q60 195 78 150Z", Orange),
    )
)

/** 공룡 3종 — 몸통은 같고 머리 · 목 · 등만 다르다 (프리셋 · 색은 생성 없이 바꾼다 ⭐26) */
fun dino(color: Color, kind: String = "horn"): Figure {
    val body = listOf(
        Bit.E(110f, 90f, 70f, 38f, color),
        Bit.P("M40 95 Q10 100 20 120 Q35 125 45 105Z", color),
        Bit.P("M70 120 v18", null, Ink, 10f),
        Bit.P("M100 125 v14", null, Ink, 10f),
        Bit.P("M130 125 v14", null, Ink, 10f),
        Bit.P("M155 118 v18", null, Ink, 10f),
    )
    val head = when (kind) {
        "long" -> listOf(
            Bit.P("M160 75 Q175 40 178 12", null, color, 22f),
            Bit.P("M160 75 Q175 40 178 12", null, Ink, 3f, alpha = 0f),
            Bit.E(182f, 12f, 16f, 11f, color),
            Bit.C(188f, 10f, 3f, Ink, null),
        )
        "trex" -> listOf(
            Bit.E(168f, 62f, 30f, 24f, color),
            Bit.P("M150 76 l6 8 l6 -8 l6 8 l6 -8 l6 8", Color.White, Ink, 2f),
            Bit.C(178f, 56f, 4f, Ink, null),
            Bit.P("M120 100 q-4 14 8 12", null, color, 8f),
        )
        else -> listOf(
            Bit.P("M160 70 Q200 50 195 90 Q190 110 160 100Z", color),
            Bit.P("M165 58 Q175 30 185 55", Color.White),
            Bit.P("M172 62 Q182 34 192 60", Color.White),
            Bit.P("M158 68 Q166 42 176 60", Color.White),
            Bit.P("M150 82 Q125 45 180 60 Q200 75 185 95Z", Color(0xFFFFE9A0), alpha = 0.6f),
            Bit.C(172f, 80f, 5f, Ink, null),
        )
    }
    return Figure(200f, 140f, body + head)
}

fun alienPreset(k: Int): Figure {
    val c = when (k) { 0 -> Green; 1 -> Purple; else -> Blue }
    val eyes = when (k) {
        0 -> listOf(Bit.C(38f, 45f, 5f), Bit.C(50f, 40f, 5f), Bit.C(62f, 45f, 5f))
        1 -> listOf(Bit.C(50f, 45f, 9f, Color.White), Bit.C(50f, 45f, 4f, Ink))
        else -> listOf(Bit.C(40f, 45f, 5f), Bit.C(60f, 45f, 5f))
    }
    return Figure(100f, 100f, listOf(Bit.C(50f, 50f, 32f, c)) + eyes + Bit.P("M40 65 q10 8 20 0", null))
}
