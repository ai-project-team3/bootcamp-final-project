package com.example.finalproject_demo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LetterColors = listOf(Coral, Sun, Color(0xFF6FC276), Color(0xFF63B3ED), Color(0xFFB07CE8))

/**
 * 앱을 켜면 처음 보이는 팀 이름 화면 — "CLAP".
 * 글자만 보인다 — 하나씩 통통 튀어 오르고, 잠시 뒤 화면 전체가 옅어지며 첫 화면이 드러난다. 누르면 바로 넘어간다.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val word = "CLAP"
    val letters = remember { word.map { Animatable(0f) } }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        letters.forEachIndexed { i, a ->
            launch {
                delay(150L + i * 110L)
                a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
        delay(150L + word.length * 110L + 1400L)
        fade.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF8EC), Bg, Color(0xFFFFE9C7))))
            .noRippleClickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            word.forEachIndexed { i, ch ->
                val p = letters[i].value
                Text(
                    ch.toString(),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = LetterColors[i % LetterColors.size],
                    modifier = Modifier.graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * 120f
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        rotationZ = (1f - p) * (if (i % 2 == 0) -18f else 18f)
                    },
                )
            }
        }
    }
}
