package com.example.finalproject_demo

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.ui.COOP_KINDS
import com.example.finalproject_demo.ui.CoopReason
import com.example.finalproject_demo.ui.coopKind
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
 * 협업 탭 템플릿 — 장소 · 직업 · 스포츠 → 요소(직접 쓰기) → 고른 이유를 누르면 네 자리가 채워지는가 (09-29).
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
    fun emptyTabShowsThreeKinds() {
        parent()
        COOP_KINDS.forEach { compose.onNodeWithText("${it.title} · ${it.arc}").assertExists() }
        snap("screens/coop_template_empty.png")
    }

    @Test
    fun pickingKindItemAndReasonFillsAllFour() {
        val d = parent()
        val job = coopKind("job")!!

        compose.onNodeWithText("직업 · 임무 이야기").performScrollTo().performClick()
        compose.onNodeWithText("소방관").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(job.questions("소방관"), d.s.parentQuestions.toList())
        assertEquals(CoopPick("job", "소방관", null), d.s.coopPick)

        compose.onNodeWithText("체험했어요").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(job.questions("소방관", CoopReason.DONE), d.s.parentQuestions.toList())
        assertEquals("done", d.s.coopPick?.reason)

        snap("screens/coop_template_job.png")
    }

    @Test
    fun typingANameUsesTheSameTemplate() {
        val d = parent()
        val place = coopKind("place")!!

        compose.onNodeWithText("장소 · 탐험 이야기").performScrollTo().performClick()
        compose.onNodeWithText("직접 쓰기", substring = true).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("  할머니 집 ")
        compose.onNodeWithText("넣기").performClick()
        compose.waitForIdle()
        assertEquals(place.questions("할머니 집"), d.s.parentQuestions.toList())
        assertEquals("할머니 집", d.s.coopPick?.name)

        snap("screens/coop_template_custom.png")
    }

    @Test
    fun aBadNameIsRefusedWithAReason() {
        val d = parent()
        compose.onNodeWithText("스포츠 · 도전 이야기").performScrollTo().performClick()
        compose.onNodeWithText("직접 쓰기", substring = true).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("축구!!")
        compose.onNodeWithText("넣기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("한글 · 영문 · 숫자로 10자까지 써 주세요").assertExists()
        assertEquals(null, d.s.coopPick)
        assertEquals(emptyList<String>(), d.s.parentQuestions.toList())
    }
}
