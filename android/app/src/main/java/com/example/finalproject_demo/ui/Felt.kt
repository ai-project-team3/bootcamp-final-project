package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path

/*
 * ── 펠트 조각 — 아이용 누르는 요소는 모두 같은 네 겹 (디자인 시스템 §4 · 09-29) ──────────
 *
 *   ① 단색         펠트 색 하나 (`Theme.kt` 의 Felt* 토큰)
 *   ② 양모 결      `felt_texture` 를 위에 겹친다 (밝고 어두운 섬유가 든 투명 그림, 타일로 깐다)
 *   ③ 바느질선     가장자리 안쪽 5dp 에 옅은 흰 **점선** (pen.dev 는 점선이 없어 실선으로 그렸다 — 앱은 점선)
 *   ④ 부드러운 그림자  아래로 5dp · 흐림 10dp · 잉크 20%
 *
 * 누르면 96% 로 꾹 눌리고 그림자가 2dp 로 얕아진다(120ms). 떼면 스프링으로 톡 돌아온다.
 * 부모 화면은 ① · ③ 만 쓴다(`texture = false`, `lift = 0.dp`).
 */

/** 양모 결 그림 — 한 번만 읽는다 */
@Composable
private fun feltTexture(): ImageBitmap = ImageBitmap.imageResource(com.example.finalproject_demo.R.drawable.felt_texture)

/**
 * 펠트 조각 겉모양 (① ~ ④). 누름은 [FeltButton] 이 맡는다.
 *
 * @param lift 그림자 깊이 — 누르면 [Felt.PressedShadowY]
 * @param texture 양모 결을 겹칠 것인가 (부모 화면은 false)
 * @param stitch 바느질 점선을 둘 것인가 (아주 작은 조각은 false)
 */
fun Modifier.felt(
    color: Color,
    shape: Shape = RoundedCornerShape(Radius.M),
    lift: Dp = Felt.ShadowY,
    texture: Boolean = true,
    stitch: Boolean = true,
): Modifier = composed {
    val tex = if (texture) feltTexture() else null
    val brush = remember(tex) { tex?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) } }
    this
        .then(if (lift.value > 0f) Modifier.shadow(lift * 1.4f, shape, ambientColor = Felt.ShadowColor, spotColor = Felt.ShadowColor) else Modifier)
        .clip(shape)
        .drawWithContent {
            // ① 단색
            val outline = shape.createOutline(size, layoutDirection, this)
            val body = Path().apply { addOutline(outline) }
            drawPath(body, color)
            // ② 양모 결 — 색 위에 얇게
            if (brush != null) drawPath(body, brush, alpha = 0.55f)
            // ③ 바느질선 — 가장자리 안쪽으로 줄인 같은 모양을 점선으로
            if (stitch) {
                val inset = Felt.StitchInset.toPx()
                if (size.width > inset * 4 && size.height > inset * 4) {
                    val inner = shape.createOutline(Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
                    val p = Path().apply { addOutline(inner) }
                    val dash = Felt.StitchDash.toPx()
                    translate(inset, inset) {
                        drawPath(p, Felt.StitchColor, style = Stroke(Felt.StitchWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))))
                    }
                }
            }
            drawContent()
        }
}

/**
 * 펠트 버튼 — 겉모양 [felt] + **꾹 눌림**. 아이 화면의 누르는 것은 모두 이것으로.
 * 크기는 부르는 쪽이 정한다 — 아이 화면 최소 [Touch.KidMin], 핵심 버튼 [Touch.Kid] 이상.
 */
@Composable
fun FeltButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.Round),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) Felt.PressedScale else 1f,
        if (pressed) tween(Felt.PressMillis) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "feltPress",
    )
    val lift by animateDpAsState(if (pressed) Felt.PressedShadowY else Felt.ShadowY, tween(Felt.PressMillis), label = "feltLift")
    Box(
        modifier
            .scale(scale)
            .felt(if (enabled) color else InkSoft.copy(alpha = 0.35f), shape, lift)
            .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 부모 화면 글자 — 긴 글을 읽으므로 본문용 고딕 [ParentFont] (디자인 시스템 §2).
 * 아이 화면은 앱 기본(Jua)이고, 부모 화면 · 부모 띠 · 비밀번호 화면만 이것으로 감싼다.
 */
@Composable
fun ParentText(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = ParentFont), content)
}
