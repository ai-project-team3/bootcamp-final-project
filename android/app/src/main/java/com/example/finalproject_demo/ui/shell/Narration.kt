package com.example.finalproject_demo.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import com.example.finalproject_demo.R
import com.example.finalproject_demo.ui.Felt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.demo.Mood
import com.example.finalproject_demo.ui.Curtain
import com.example.finalproject_demo.ui.Expr
import com.example.finalproject_demo.ui.FeltSky
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.OttoFace
import com.example.finalproject_demo.ui.OttoState
import com.example.finalproject_demo.ui.ParentText

/**
 * 모드 — 나레이션 칸 테두리 색 (디자인 시스템 09-28 저녁판 「세 모드 모두 같은 틀」)
 */
enum class NarrationMode(val color: Color, val label: String, val icon: String) {
    STORY(Curtain, "이야기 만들기", "🎭"),
    DIARY(FeltSky, "오늘 이야기", "☀️"),
    COOP(FeltTeal, "같이 만들기", "👪"),
}

/**
 * **나레이션 칸** — 화면 아래 한 줄: **[오또 얼굴] [오또가 하는 말 띠]** · 오른쪽 끝에 따로 선 **[녹음 버튼]**.
 *
 *   얼굴 테두리 = 차례 — 분홍 말함 · 청록 들음 · 겨자 생각. 말할 때 · 기다릴 때 · 들을 때 테두리 색 파동이 퍼진다
 *   얼굴 그림 = 표정 — [Expr] (기쁨 · 깜짝 · 속상 · 궁금 · 뿌듯). 표정마다 움직임도 다르다
 *   띠 테두리 = 모드 — 빨강 이야기 만들기 · 파랑 오늘 이야기 · 청록 같이 만들기
 *
 * 10-01 — 얼굴을 감싸던 테두리만 없앴다 (자리는 그대로). 얼굴은 혼자 서고, 말 띠는 얼굴 밑선에 맞춰 얼굴 **뒤**에서 시작해
 * 오른쪽으로 뻗는 얇은 둥근 펠트 띠다
 * (크림 펠트 천 · 모드 색 펠트 테두리 · 바느질 점선 — [drawFeltPill]). 띠 길이는 **문장 길이에 맞춘다** — 한 자씩 써지는 동안
 * 띠가 따라 늘어나며 덜컹거리지 않게 [fullLine](문장 전체)으로 크기를 먼저 잡는다. 너무 길면 버튼 앞까지 가서 두 줄로 접힌다.
 * 높이는 위 여백 4 + 얼굴 100 + 아래 8 = 112dp — 무대 안쪽 아래 여백(`BottomChrome` 116dp)이 이것에 맞춘다.
 *
 * @param line 지금 보이는 글자 (한 자씩 써지는 중이면 앞부분만)
 * @param fullLine 다 써졌을 때의 문장 — 띠 길이를 이것으로 잡는다
 * @param trailing 오른쪽 끝에 따로 세울 버튼들(녹음 · 그리기)
 */
@Composable
fun Narration(
    mode: NarrationMode,
    state: OttoState,
    line: String,
    modifier: Modifier = Modifier,
    fullLine: String = line,
    speaker: String? = null,
    burst: Mood = Mood.NONE,
    burstId: Int = 0,
    expr: Expr = Expr.NONE,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val creamImg = ImageBitmap.imageResource(R.drawable.narration_felt)
    val fiberImg = ImageBitmap.imageResource(R.drawable.felt_texture)
    val cream = remember(creamImg) { ShaderBrush(ImageShader(creamImg, TileMode.Repeated, TileMode.Repeated)) }
    val fibers = remember(fiberImg) { ShaderBrush(ImageShader(fiberImg, TileMode.Repeated, TileMode.Repeated)) }
    // 얼굴이 뛰어오른 높이(px, 위가 음수) — 그리기 단계에서만 읽어 띠 왼쪽이 얼굴을 따라 휜다
    val lift = remember { mutableFloatStateOf(0f) }
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 얼굴 + 말 띠 — 띠는 얼굴 밑선에 맞춰 붙고 얼굴 뒤(얼굴 가운데)에서 시작한다. 길이는 글에 맞추되 버튼 앞까지만
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.BottomStart) {
            Row(
                Modifier
                    .padding(start = FaceSize / 2)
                    // 띠 전체가 오또 움직임의 절반만큼 같이 오르내리고, 나머지 절반은 얼굴 쪽에서 완만하게 이어받는다 —
                    // 끝만 휘면 끌려오는 것처럼 보였다 (10-01 사용자 제보). 글씨는 조금만 움직여 읽기에 지장이 없다
                    .graphicsLayer { translationY = lift.floatValue * BandShare }
                    .drawBehind { drawFeltPill(mode.color, cream, fibers, lift.floatValue * (1f - BandShare)) }
                    .padding(start = FaceSize / 2 + 10.dp, end = 22.dp, top = 10.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    if (speaker != null) ParentText { Text(speaker, fontSize = 12.sp, color = mode.color) }
                    // 보이지 않는 문장 전체가 띠 크기를 잡고, 그 위에 지금까지 써진 글자를 얹는다
                    Box {
                        Text(fullLine, fontSize = 22.sp, lineHeight = 29.sp, maxLines = 2, modifier = Modifier.alpha(0f))
                        Text(line, fontSize = 22.sp, color = InkBrown, lineHeight = 29.sp, maxLines = 2)
                    }
                }
            }
            // 오또 얼굴 — 혼자 선다 (감싸는 테두리 없음). 테두리 색 · 파동이 차례를 알린다.
            // 띠보다 위에 그려 띠 왼쪽 끝을 덮는다. 뛰면 그 높이를 띠에 알려 띠가 따라 휜다
            OttoFace(state, Modifier.size(FaceSize), burst = burst, burstId = burstId, expr = expr, pulse = true, onLift = { lift.floatValue = it })
        }
        // 녹음 · 그리기 버튼 — 오른쪽 끝에 따로 선다. 화면 아래 여백 = 화면 오른쪽 여백이 되게 밑에 붙이고,
        // 이 줄 밑에 부르는 쪽 여백(약 6dp)이 더 있어서 그만큼 내린다 — 실물폰(S10)에서 틈을 재어 맞췄다 (09-30)
        if (trailing != null) {
            Row(Modifier.padding(start = 12.dp).offset(y = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), content = trailing)
        }
    }
}

/**
 * 펠트 말 띠 — 모드 색 펠트 테두리(양모 결) 안에 크림 펠트 천(ComfyUI 그림 `narration_felt`)을 얹고,
 * 테두리 가운데로 흰 바느질 점선이 돈다. 띠 자기 크기로만 그려서 첫 프레임부터 모양이 맞다.
 *
 * 띠 왼쪽 둥근 끝의 중심은 얼굴 중심 아래(띠 왼쪽 가장자리)에 둔다 — 둥근 끝이 얼굴 원 안에 숨고 밑선이 얼굴 맨 아래에서
 * 접선으로 이어진다. 띠 전체는 오또 움직임의 [BandShare] 만큼 같이 움직이고, 남은 [lift] 는 왼쪽 끝에서 받아
 * 얼굴 너비 1.3 배쯤에 걸쳐 완만하게 0 이 된다 — 띠와 오또가 한 덩어리로 오르내리는 것처럼 (10-01 사용자 요청)
 */
private fun DrawScope.drawFeltPill(edge: Color, cream: ShaderBrush, fibers: ShaderBrush, lift: Float) {
    val bend = (FaceSize * 1.3f).toPx()        // 얼굴 중심부터 여기까지 완만하게 휜다
    fun follow(x: Float): Float {              // 얼굴 쪽 1 → bend 너머 0 (부드럽게)
        val t = (x / bend).coerceIn(0f, 1f)
        return lift * (1f - t * t * (3f - 2f * t))
    }
    fun pill(inset: Float) = Path().apply {
        val top = inset; val bottom = size.height - inset
        val r = (bottom - top) / 2
        val right = size.width - inset
        val steps = 16
        moveTo(0f, top + follow(0f))
        for (i in 1..steps) { val x = bend * i / steps; lineTo(x, top + follow(x)) }
        lineTo(right - r, top)
        arcTo(Rect(right - r * 2, top, right, bottom), 270f, 180f, false)
        lineTo(bend, bottom)
        for (i in steps - 1 downTo 0) { val x = bend * i / steps; lineTo(x, bottom + follow(x)) }
        val cy = (top + bottom) / 2 + lift
        arcTo(Rect(-r, cy - r, r, cy + r), 90f, 180f, false)
        close()
    }
    val band = EdgeBand.toPx()
    val outer = pill(0f)
    // 부드러운 그림자
    drawIntoCanvas { c ->
        val p = Paint().apply { color = Felt.ShadowColor }
        p.asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(8.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(0f, 4.dp.toPx()); c.drawPath(outer, p); c.restore()
    }
    drawPath(outer, edge)
    drawPath(outer, fibers, alpha = 0.55f)
    val inner = pill(band)
    drawPath(inner, cream)
    drawPath(inner, Color.Black.copy(alpha = 0.10f), style = Stroke(1.5.dp.toPx()))   // 천 가장자리가 테두리 밑으로 들어간 그늘
    val dash = Felt.StitchDash.toPx()
    drawPath(
        pill(band / 2), Color.White.copy(alpha = 0.8f),
        style = Stroke(Felt.StitchWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))),
    )
}

/** 오또 얼굴 — 전에 얼굴을 감싸던 둥근 자리(100dp)와 같은 크기 */
private val FaceSize = 100.dp

/** 띠 전체가 오또 움직임을 따라가는 몫 — 나머지는 얼굴 쪽 휨이 받는다 */
private const val BandShare = 0.5f

/** 모드 색 펠트 테두리 두께 */
private val EdgeBand = 7.dp
