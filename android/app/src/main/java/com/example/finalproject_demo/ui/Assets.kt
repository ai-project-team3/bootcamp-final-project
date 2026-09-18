package com.example.finalproject_demo.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * ComfyUI로 만든 그림(res/drawable/<name>.png)을 이름으로 찾는다.
 * 없으면 0 — 호출한 쪽이 벡터 그림으로 대신 그린다. 그래서 그림이 아직 없어도 앱은 돈다.
 */
@Composable
fun assetId(name: String): Int {
    val ctx = LocalContext.current
    return remember(name) {
        @Suppress("DiscouragedApi")
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
    }
}

@Composable
fun AssetImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    fallback: @Composable () -> Unit = {},
) {
    val id = assetId(name)
    if (id != 0) {
        Image(painterResource(id), contentDescription = null, modifier = modifier, contentScale = contentScale, colorFilter = colorFilter)
    } else {
        fallback()
    }
}

/**
 * 색만 바꾸기 (⭐26 — 다시 생성하지 않고 색 영역만). 초록 원본을 색상환에서 돌린다.
 * 초록(120°) → 빨강(0°)이면 -120°.
 */
fun hueRotate(degrees: Float): ColorFilter {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    val m = floatArrayOf(
        lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
        lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
        lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** 공룡 색 → 색상환 회전 각도. 초록이 원본. */
fun dinoHue(color: Color): Float = when (color) {
    Color(0xFFF25C4C) -> -95f    // 빨강 (원본이 연두라 -95°가 빨강)
    Color(0xFF63B3ED) -> 100f    // 파랑
    Color(0xFFF9B233) -> -80f    // 노랑
    else -> 0f
}
