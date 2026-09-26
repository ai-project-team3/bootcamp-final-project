package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.ui.COOP_TEMPLATES
import com.example.finalproject_demo.ui.ParentView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 협업 탭 템플릿 카드 — 카드를 탭하면 네 자리가 채워지고, 빈칸 칩 하나로 장소만 바뀌는가 (docs/주말_데모_뼈대.md 「B. 협업」).
 * 그림은 `screens/coop_template_*.png` 에 **기록만** 한다 (WorldSceneShotTest.snap 과 같은 이유).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class CoopTemplateShotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(path: String) = compose.onRoot().captureRoboImage(
        File(path).path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    )

    private fun parent(): Director {
        val d = Director(CoroutineScope(SupervisorJob()))
        compose.setContent { ParentView(d, "coop") }
        return d
    }

    @Test
    fun emptyTabShowsFourCards() {
        parent()
        COOP_TEMPLATES.forEach { compose.onNodeWithText(it.label()).assertExists() }
        snap("screens/coop_template_empty.png")
    }

    @Test
    fun tappingWeekendThenAChipFillsAllFourAndChangesOnlyThePlace() {
        val d = parent()
        val t = COOP_TEMPLATES.first { it.key == "weekend" }

        compose.onNodeWithText(t.label()).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(t.questions(), d.s.parentQuestions.toList())

        compose.onNodeWithText("외할머니 집").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(t.questions("외할머니 집"), d.s.parentQuestions.toList())

        snap("screens/coop_template_weekend.png")
    }
}
