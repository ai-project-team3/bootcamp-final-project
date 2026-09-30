package com.example.finalproject_demo.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * **기기 크기 맞춤** (09-30 사용자 요청 — 연결된 폰 말고 모든 기기에).
 *
 * 화면은 가로 폰 한 대(800×411dp · 갤럭시 S10 5G 높이)를 기준으로 그렸다. 글자 · 버튼 · 여백이 dp 로 고정이라
 * 태블릿에서는 작고 휑하고, 작은 폰에서는 잘렸다. 그래서 앱 전체의 dp 를 **한 배율로** 키우거나 줄인다:
 *
 *   배율 = min(가로 / 800, 세로 / 411)
 *
 * 그러면 어느 기기에서든 앱이 보는 화면은 **가로 800dp 이상 · 세로 411dp 이상**이고, 넘는 쪽은 한 방향뿐이다
 * (태블릿 16:10 → 800×500, 4:3 → 800×600, 긴 폰 20:9 → 915×412). 기준 폰은 배율 1 이라 지금 모습 그대로다.
 * 남는 방향은 각 화면이 채운다 — 비율로 그리는 화면(방 · 무대 · 책)은 그대로 늘고, 나머지는 가운데 둔다.
 *
 * `LocalConfiguration` 의 화면 크기도 같이 바꾼다 — `screenWidthDp` 를 읽는 곳(책 넘김)이 새 dp 와 맞게.
 */
const val DESIGN_W = 800f
const val DESIGN_H = 411f

/** 너무 작은 기기에서 글자가 읽기 어려울 만큼 줄지 않게 · 아주 큰 화면에서 끝없이 커지지 않게 */
fun fitScale(wDp: Float, hDp: Float): Float = minOf(wDp / DESIGN_W, hDp / DESIGN_H).coerceIn(0.72f, 2.4f)

@Composable
fun FitScreen(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val k = fitScale(maxWidth.value, maxHeight.value)
        val conf = LocalConfiguration.current
        val scaled = remember(conf, k) {
            Configuration(conf).apply {
                screenWidthDp = (conf.screenWidthDp / k).roundToInt()
                screenHeightDp = (conf.screenHeightDp / k).roundToInt()
                smallestScreenWidthDp = (conf.smallestScreenWidthDp / k).roundToInt()
                densityDpi = (conf.densityDpi * k).roundToInt()
            }
        }
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * k, base.fontScale),
            LocalConfiguration provides scaled,
        ) { content() }
    }
}
