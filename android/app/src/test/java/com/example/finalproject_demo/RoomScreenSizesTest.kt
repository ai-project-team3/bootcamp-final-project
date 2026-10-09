package com.example.finalproject_demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.shell.Grid
import com.example.finalproject_demo.ui.shell.OttoRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 10-09 테스터 — 0.5 가 갤럭시 폴드7 바깥 화면에서 방에 들어가자마자 꺼졌다(Play Console · `Grid.<init>` 의
 * `coerceIn` IllegalArgumentException). 화면이 그림보다 가로로 길면 세로에 맞춘 배율에서 아래 · 위 한계가
 * 이론상 같아지고, float 반올림으로 아래가 위보다 커지면 빈 범위가 된다. 방이 그려지기만 하면 통과다.
 */
@RunWith(RobolectricTestRunner::class)
class RoomScreenSizesTest {
    @get:Rule val compose = createComposeRule()

    private val sup = SupervisorJob()
    @After fun stop() = sup.cancel()

    /** 방이 받는 자리를 [w]×[h] dp 로 정확히 준다 — 시스템 바 · 키보드를 뺀 실제 창 크기 */
    private fun drawRoom(w: Int, h: Int) {
        val d = Director(CoroutineScope(sup)).also { it.s.speed = 0.01 }
        compose.setContent { Box(Modifier.size(w.dp, h.dp)) { OttoRoom(d) } }
        compose.waitForIdle()
    }

    /** 갤럭시 폴드7 바깥 화면 가로 (1080×2520 · 420dpi) */
    @Test @Config(sdk = [34], qualifiers = "w960dp-h411dp-land-420dpi")
    fun fold7CoverScreen() = drawRoom(960, 411)

    /** 배치 계산만 — 가로 600~1200dp 를 3dp · 세로 200~560dp 를 0.25dp 마다. 어느 크기에서도 범위가 뒤집히지 않는다 */
    @Test @Config(sdk = [34]) fun everyWindowSizeKeepsAValidRange() {
        val bad = mutableListOf<String>()
        for (w in 600..1200 step 3) {
            var h = 200f
            while (h <= 560f) {
                runCatching { Grid(w.dp, h.dp) }.onFailure { bad += "${w}x$h" }
                h += 0.25f
            }
        }
        assertTrue("Grid threw at ${bad.size} sizes, e.g. ${bad.take(5)}", bad.isEmpty())
    }
}
