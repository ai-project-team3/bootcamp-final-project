package com.example.finalproject_demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * 주인공 그림 — 프리셋 2명과 골라서 · 말로 만든 주인공 모두 같은 펠트 그림(머리 × 옷 × 안경 27장).
 * 눈(반달 · 별)은 "눈 스티커" — 실제 앱의 표정 스티커 오버레이와 같은 방식 (기획안 §4).
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
    Box(modifier) {
        Image(painterResource(id), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (attr.eyes != "round") {
            val eyes = HERO_EYES[name] ?: DEFAULT_EYES
            Canvas(Modifier.fillMaxSize()) {
                // Fit으로 그려진 정사각 그림의 실제 영역
                val k = min(size.width, size.height)
                val ox = (size.width - k) / 2f
                val oy = (size.height - k) / 2f
                val r = minOf(eyes.r, 0.024f) * k   // 안경테가 잡혀도 동자 크기로
                listOf(eyes.lx to eyes.ly, eyes.rx to eyes.ry).forEach { (ex, ey) ->
                    val c = Offset(ox + ex * k, oy + ey * k)
                    drawCircle(Color(0xFFFFDCC2), r * 1.35f, c)   // 살색 패치
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
    }
}

/** 눈 위치 (그림 폭 기준 0~1). tools/find_eyes.py가 만든 표. 없으면 기본값 */
data class Eyes(val lx: Float, val ly: Float, val rx: Float, val ry: Float, val r: Float)
val DEFAULT_EYES = Eyes(0.43f, 0.33f, 0.57f, 0.33f, 0.022f)
