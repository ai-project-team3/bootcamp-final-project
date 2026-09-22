package com.example.finalproject_demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        // 크레용을 못 받은 날 — 선물은 하나지만 **끝났다**(done). 화면은 개수가 아니라 이것을 본다 (치영 d7f6754)
        director.s.stage = Stage.Gifts(1, done = true)

        compose.setContent { StageView(director) }

        compose.onNodeWithText("📚 책장에 꽂기").assertExists()
    }
}
