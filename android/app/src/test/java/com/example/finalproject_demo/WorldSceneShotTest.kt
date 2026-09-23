package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.WorldItem
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
 * 무대 배치 — 배경 위에 인물·탈것이 **서 있어 보이는가**를 눈으로 본다 (09-23).
 *
 * 조장이 "캐릭터가 중앙에 붕 떠 있다"고 짚었다. 좌표는 `Scenes.kt`(치영) 것을 그대로 쓰고
 * 그리는 규칙(`WorldItemView`)만 바꾸므로, 같은 장면을 전·후로 찍어 비교한다.
 * `screens/world_*.png` 는 기준 그림이 아니라 **보는 용도**다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class WorldSceneShotTest {
    @get:Rule val compose = createComposeRule()

    /** 기준 대조 없이 기록만 — 보는 용도의 그림이다 (ScreenShotTest.snap 과 같은 이유) */
    private fun snap(path: String) = compose.onRoot().captureRoboImage(
        File(path).path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    private fun world(theme: String, diaryPlace: String? = null): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        d.s.themeKey = theme
        if (diaryPlace != null) { d.s.mode = StoryMode.DIARY; d.s.placeLabel = diaryPlace }
        val attr = HeroAttr(hair = "tied", glasses = "round", eyes = "star", bottom = "skirt")
        d.s.heroAttr = attr
        // Scenes.kt:685 의 장소 장면 그대로 — 탈것 + 주인공
        d.s.stage = Stage.World(
            listOf(
                WorldItem(d.s.th.vehicleArt, 0.46f, 0.22f, 0.13f),
                WorldItem(Art.HeroArt(attr), 0.30f, 0.30f, 0.11f),
            ),
        )
        return d
    }

    @Test
    fun dinoWorld() {
        val d = world("dino")
        compose.setContent { StageView(d) }
        snap("screens/world_dino.png")
    }

    @Test
    fun playgroundWorld() {
        val d = world("dino", diaryPlace = "놀이터")
        compose.setContent { StageView(d) }
        snap("screens/world_playground.png")
    }
}
