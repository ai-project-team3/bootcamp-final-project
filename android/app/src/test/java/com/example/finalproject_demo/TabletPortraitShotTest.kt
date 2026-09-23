package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.WorldItem
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.StageView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 태블릿 **세로** 화면에서 무대가 깨지지 않는가 (09-23 박진웅 지적 6).
 *
 * `targetSdk 36` 부터 폭 600dp 이상 큰 화면에서는 `screenOrientation="sensorLandscape"` 가 **무시된다.**
 * 앱은 가로 전용으로 만들어졌는데 태블릿에서는 세로로 돌아갈 수 있다 — 그때 화면이 깨지는지 눈으로 본다.
 * 기준 대조 없이 기록만 한다(`screens/tablet_portrait_*.png`). 실기기 확인을 대신하지는 못한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-320dpi")
class TabletPortraitShotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(path: String) = compose.onRoot().captureRoboImage(
        File(path).path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    @Test
    fun worldSceneOnTabletPortrait() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.themeKey = "dino"
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        d.s.stage = Stage.World(
            listOf(
                WorldItem(d.s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
                WorldItem(Art.HeroArt(attr), 0.30f, 0.30f, 0.11f),
            ),
        )
        compose.setContent { StageView(d) }
        snap("screens/tablet_portrait_world.png")
    }

    @Test
    fun parentModeOnTabletPortrait() {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.stage = Stage.Parent("today")
        compose.setContent { StageView(d) }
        snap("screens/tablet_portrait_parent.png")
    }
}
