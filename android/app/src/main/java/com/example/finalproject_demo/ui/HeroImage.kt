package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.finalproject_demo.demo.heroImageName
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 주인공 그림 — **부품을 쌓아 그린다** (9/21).
 *
 * ```
 *   몸(body_{옷}_{하의}[_{머리}])  ← 27장. 얼굴 · 자세 · 비율이 모두 같은 아이
 *     └ 안경(gl_round · gl_square) ← 얹는다. "없음"이면 아무것도 안 얹는다
 *         └ 눈(반달 · 별)          ← 벡터로 그린다 (전부터 그랬다)
 * ```
 *
 * 왜 이렇게 바꿨나 — 전에는 완성본 81장을 갈아 끼웠는데, 81장이 각각 따로 생성된 그림이라
 * **토글 하나를 바꾸면 캐릭터가 통째로 다른 아이로 바뀌었다.** 머리를 길게 하면 얼굴이 바뀌고,
 * 옷 색을 바꾸면 하의가 바뀌고, 안경을 바꾸면 몸 비율이 달라졌다.
 * 아이가 고른 것은 "머리를 길게" 이지 "다른 아이" 가 아니다.
 *
 * 안경을 그림에서 빼고 얹는 것으로 바꾸면서 **안경 + 반달/별 눈 어긋남도 같이 사라졌다** —
 * 얼굴이 27장 모두 같으니 눈 자리가 하나로 고정된다.
 *
 * 그림이 없으면 벡터 인형으로 대신 그린다.
 */
@Composable
fun HeroImage(attr: HeroAttr, modifier: Modifier = Modifier) {
    val name = heroImageName(attr)
    val id = assetId(name)
    if (id == 0) {
        FigureView(hero(attr), modifier)
        return
    }
    val eyes = HERO_EYES[name] ?: DEFAULT_EYES
    Box(modifier) {
        Image(painterResource(id), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        // ⚠️ **눈을 먼저, 안경을 나중에** 그린다 (9/22). 전에는 거꾸로라 살색 패치가 안경테를 덮었다.
        //    실제로도 안경은 눈 위에 걸친다.
        if (attr.eyes != "round") {
            // 눈 스티커 — 원래 눈을 살색으로 덮고 그 위에 표정을 그린다.
            //
            // 살색을 코드에 박아 두지 않고 **그림에서 뽑아 쓴다** (9/22).
            // 박아 둔 값(255,220,194)이 실제 그림(247,213,184)보다 밝고 붉어서, 반달·별 눈을 고르면
            // 눈 자리만 색이 뜨고 펠트 질감이 끊겨 보였다. 27장이 조금씩 다르므로 그림마다 뽑는다.
            //
            // ⚠️ **반드시 remember 로 감싼다.** `toPixelMap()` 은 640×640 = 41만 화소를 통째로 읽는다.
            //    9/22에 이걸 그냥 두었더니 **도감(주인공 카드 네 장)에서 화면이 멎었다** —
            //    다시 그릴 때마다 네 번씩 41만 화소를 읽었기 때문이다. 그림 이름이 같으면 색도 같다.
            val bodyBitmap = ImageBitmap.imageResource(id)
            val skin = remember(name) { skinNear(bodyBitmap, eyes) }
            Canvas(Modifier.fillMaxSize()) {
                val k = min(size.width, size.height)
                val ox = (size.width - k) / 2f
                val oy = (size.height - k) / 2f
                val unit = maxOf(eyes.r, 0.016f) * k
                val patchR = unit * 1.9f
                val r = unit * 1.3f
                listOf(eyes.lx to eyes.ly, eyes.rx to eyes.ry).forEach { (ex, ey) ->
                    val c = Offset(ox + ex * k, oy + ey * k)
                    // 가장자리를 부드럽게 풀어 준다 — 딱 떨어지는 타원이면 붙인 자국이 보인다
                    drawOval(
                        Brush.radialGradient(
                            0.62f to skin,
                            1f to skin.copy(alpha = 0f),
                            center = c,
                            radius = patchR * 1.45f,
                        ),
                        topLeft = Offset(c.x - patchR * 1.2f, c.y - patchR * 1.5f),
                        size = Size(patchR * 2.4f, patchR * 2 * 1.5f),
                    )
                    when (attr.eyes) {
                        "smile" -> {
                            val p = Path().apply {
                                moveTo(c.x - r, c.y + r * 0.3f)
                                quadraticBezierTo(c.x, c.y - r * 1.2f, c.x + r, c.y + r * 0.3f)
                            }
                            drawPath(p, Color(0xFF3A2A1E), style = Stroke(r * 0.45f, cap = StrokeCap.Round))
                        }
                        "star" -> {
                            val p = Path()
                            for (i in 0 until 10) {
                                val rr = if (i % 2 == 0) r * 1.15f else r * 0.5f
                                val a = Math.toRadians((-90 + i * 36).toDouble())
                                val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
                                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                            }
                            p.close()
                            drawPath(p, Color(0xFF3A2A1E))
                        }
                    }
                }
            }
        }

        // 안경 — 눈 위에 얹는다.
        // ⚠️ **고정 좌표로 두면 안 된다.** 27장은 같은 아이지만 그림마다 몸이 화면을 채우는 정도가
        //    조금씩 달라서 눈 높이가 0.27 ~ 0.33 사이로 움직인다(바지 그림은 다리가 있어 얼굴이 위로 간다).
        //    그래서 `find_eyes.py` 가 잰 **그 그림의 눈 자리**를 따라간다.
        //
        // 그림 자체도 9/22에 다듬었다 — 렌즈 속 흰 칠과 접힌 안경다리를 지웠다(`tools/fix_glasses.py`).
        // 그 전에는 얼굴에 흰 원 두 개가 생기고 베이지색 안경다리가 머리카락 위로 삐져나왔다.
        val glassesId = if (attr.glasses == "none") 0 else assetId("gl_${attr.glasses}")
        if (glassesId != 0) {
            val gl = ImageBitmap.imageResource(glassesId)
            Canvas(Modifier.fillMaxSize()) {
                val k = min(size.width, size.height)
                val ox = (size.width - k) / 2f
                val oy = (size.height - k) / 2f
                val cx = ox + (eyes.lx + eyes.rx) / 2f * k
                val cy = oy + (eyes.ly + eyes.ry) / 2f * k
                val w = (eyes.rx - eyes.lx) * k * GLASSES_W
                val h = w * gl.height / gl.width
                drawImage(
                    gl,
                    dstOffset = IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                )
            }
        }
    }
}

/**
 * 눈 옆의 **피부색을 그림에서 직접 뽑는다** (9/22).
 *
 * 반달·별 눈은 원래 눈을 살색으로 덮고 그 위에 그리는데, 그 살색을 코드에 박아 두면
 * 그림과 미묘하게 어긋나 눈 자리만 색이 뜬다. 두 눈 사이(콧대)는 어느 그림에서나 살이라
 * 거기 한 점을 읽는다. 몸 그림은 640×640 정사각이라 비율을 그대로 화소로 바꾸면 된다.
 */
private fun skinNear(body: ImageBitmap, eyes: Eyes): Color {
    val px = body.toPixelMap()
    val x = (((eyes.lx + eyes.rx) / 2f) * body.width).toInt().coerceIn(0, body.width - 1)
    val y = (((eyes.ly + eyes.ry) / 2f) * body.height).toInt().coerceIn(0, body.height - 1)
    val c = px[x, y]
    // 투명하거나 어두운 점을 집었으면(머리카락에 걸린 경우) 예전 기본값으로 물러난다
    return if (c.alpha < 0.5f || c.red + c.green + c.blue < 1.2f) Color(0xFFFFDCC2) else c.copy(alpha = 1f)
}

/**
 * 안경 폭 — **두 눈 사이 거리의 몇 배인가.** 그림마다 얼굴 크기가 조금씩 달라도 이 비율은 같다.
 * `tools/check_glasses.py` 로 겹쳐 보며 잡는다.
 */
private const val GLASSES_W = 2.35f

/** 눈 위치 (그림 폭 기준 0~1). tools/find_eyes.py가 만든 표. 없으면 기본값 */
data class Eyes(val lx: Float, val ly: Float, val rx: Float, val ry: Float, val r: Float)
val DEFAULT_EYES = Eyes(0.43f, 0.33f, 0.57f, 0.33f, 0.022f)
