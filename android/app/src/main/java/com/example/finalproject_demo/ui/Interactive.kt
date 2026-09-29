package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 「이건 만질 수 있다」를 보여 주는 테두리 (09-27 · 멘토 요청).
 *
 * 멘토 지적 — *"상호작용할 수 있는 오브젝트는 테두리를 두껍게 해서 티가 나게."*
 * 전에는 누를 자리(핫스팟)가 **반짝일 때만** 보였고, 평소에는 배경과 구별이 안 됐다.
 * 책의 끌기 · 문지르기 · 퍼즐 물체도 그냥 그림이라, 아이가 무엇을 만질 수 있는지 몰랐다.
 *
 * 규칙 하나로 통일한다 — **만질 수 있는 것은 전부 같은 두꺼운 테두리**를 두른다.
 *   안쪽 흰색 [TOUCH_INNER] · 바깥 노랑 [TOUCH_OUTER] (앱 강조색 `Sun`)
 * 흰색만 두르면 밝은 배경(하늘 · 눈밭)에서 묻히고, 노랑만 두르면 노란 물체에서 묻힌다. 두 겹이면 어디서든 보인다.
 * 만질 수 없는 것(배경 · 장식 · 멀리 있는 인물)에는 두르지 않는다 — 전부 두르면 다시 구별이 안 된다.
 */

/**
 * 만질 수 있다는 표시를 **그릴 것인가** (09-29 사용자 요청 — 「그림책을 만들 때 상호작용 요소 표시를 없애 줘」).
 * 끄면 테두리 · 고리 · 반짝임을 그리지 않고, 누르는 것 · 누른 뒤 반응(통 튀기 · 글자)은 그대로다.
 */
const val SHOW_TOUCH_MARKS = false

/** 안쪽 흰 테두리 두께 */
val TOUCH_INNER: Dp = Border.TouchInner

/** 바깥 노란 테두리 두께 (흰 테두리 바깥으로 더 나오는 만큼) */
val TOUCH_OUTER: Dp = Border.TouchOuter

private val TouchWhite = Color.White
private val TouchYellow = Sun

/** 모양을 따라 두르는 테두리를 몇 방향으로 번지게 그리나 — 적으면 가장자리가 톱니처럼 보인다 */
private const val OUTLINE_STEPS = 16

/**
 * 그림의 **모양을 따라** 두꺼운 테두리를 두른다 (스티커처럼).
 *
 * 그림의 실루엣을 흰색 · 노랑으로 물들여 [OUTLINE_STEPS] 방향으로 조금씩 밀어 그린 뒤, 그 위에 원래 그림을 그린다.
 * `RenderEffect` 같은 안드로이드 12 이상 기능을 쓰지 않는다 — minSdk 24 폰에서도 똑같이 보인다.
 *
 * 투명한 곳이 있는 그림(인물 · 물건)은 모양을 따라가고, 네모난 그림(퍼즐 조각)은 네모 테두리가 된다.
 *
 * @param enabled 끄면 아무것도 하지 않는다 — 다 맞춘 퍼즐 조각처럼 **이제 만질 수 없는 것**은 끈다
 */
fun Modifier.touchOutline(enabled: Boolean = true): Modifier =
    if (!enabled || !SHOW_TOUCH_MARKS) this else this.drawWithContent {
        val inner = TOUCH_INNER.toPx()
        val outer = inner + TOUCH_OUTER.toPx()
        drawIntoCanvas { canvas ->
            // 바깥(노랑)부터 — 흰색이 그 위에 앉아 노랑이 가장자리에만 남는다
            for ((radius, color) in listOf(outer to TouchYellow, inner to TouchWhite)) {
                val tint = Paint().apply { colorFilter = ColorFilter.tint(color, BlendMode.SrcIn) }
                canvas.saveLayer(Rect(-radius, -radius, size.width + radius, size.height + radius), tint)
                for (k in 0 until OUTLINE_STEPS) {
                    val a = 2.0 * PI * k / OUTLINE_STEPS
                    translate(cos(a).toFloat() * radius, sin(a).toFloat() * radius) { this@drawWithContent.drawContent() }
                }
                canvas.restore()
            }
        }
        drawContent()
    }

/**
 * 동그란 누를 자리(핫스팟)에 두르는 **고리** — [touchOutline] 과 같은 두 겹 색.
 *
 * 핫스팟은 배경 그림 **안에** 그려진 것이라 모양을 따라갈 실루엣이 없다. 그래서 원으로 두른다.
 * ⚠️ 그림자(`shadow`)는 안 쓴다 — 속이 빈 원에 걸면 원 안쪽까지 어둡게 비친다.
 */
@Composable
fun TouchRing(modifier: Modifier = Modifier) {
    if (!SHOW_TOUCH_MARKS) return
    // `border()` 를 두 번 겹치면 **바깥 것이 안쪽 것 위에** 그려져 흰색이 사라진다 (09-27 첫 시도).
    // 순서를 손으로 정하려고 Canvas 에 직접 그린다 — 노랑(넓게) 먼저, 그 위에 흰색(안쪽)
    Canvas(modifier) {
        val inner = TOUCH_INNER.toPx()
        val outer = TOUCH_OUTER.toPx()
        val r = size.minDimension / 2
        drawCircle(TouchYellow.copy(alpha = 0.95f), radius = r - (inner + outer) / 2, style = Stroke(inner + outer))
        drawCircle(TouchWhite.copy(alpha = 0.95f), radius = r - outer - inner / 2, style = Stroke(inner))
    }
}
