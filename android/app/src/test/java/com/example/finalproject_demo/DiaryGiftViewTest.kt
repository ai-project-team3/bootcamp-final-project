package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.finalproject_demo.demo.DemoBtn
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.Stage
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.ui.StageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class DiaryGiftViewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun diaryWithoutDrawingCanStillPutItsBookOnTheShelf() {
        val director = Director(CoroutineScope(SupervisorJob()))
        director.s.mode = StoryMode.DIARY
        director.s.stage = Stage.Gifts(1)
        director.s.buttons += DemoBtn("📚 책장에 꽂기") {}

        compose.setContent { StageView(director) }

        compose.onNodeWithText("📚 책장에 꽂기").assertExists()
    }
}
