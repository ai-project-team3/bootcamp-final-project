package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.ui.shell.LocalWipe
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryHeroWipeTest {
    @Test fun wipingAllBooksRemovesReuseChoicesInTheExistingDirector() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalStoryBookStore(context)
        store.save(DemoState().apply { templateKey = "A"; storyHeroCall = "콩이" }.completedStoryBook()!!)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, store).apply { s.speed = 0.01; s.timerOn = false }
        try {
            assertTrue(LocalWipe.wipe(context))
            d.go(Scene.MAKEHERO)
            withTimeout(3000) { while (d.s.stage !is Stage.CardsRow) delay(5) }
            assertEquals(listOf("voice", "preset"), (d.s.stage as Stage.CardsRow).cards.map { it.value })
        } finally { scope.cancel() }
    }
}
