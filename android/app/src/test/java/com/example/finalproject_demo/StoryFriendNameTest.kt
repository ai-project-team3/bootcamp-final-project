package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StoryFriendNameTest {
    @Test fun drawingConfirmationUsesTheNewcomerTheChildNamed() =
        drawingLabel(newcomer = "염라대왕", givenName = null, expected = "염라대왕")

    @Test fun drawingConfirmationUsesTheFriendsGivenNameWhenAvailable() =
        drawingLabel(newcomer = "보라색 문어", givenName = "도리", expected = "도리")

    private fun drawingLabel(newcomer: String, givenName: String?, expected: String) = runBlocking {
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope)
        try {
            Server.liveModes = emptySet()
            d.s.slots["newcomer"] = newcomer
            givenName?.let { d.s.slots["name"] = it }
            d.s.syncStoryPresentation()
            val drawing = scope.launch { d.prepareStoryFriendDrawing() }
            withTimeout(3_000) { while (d.s.stage !is Stage.DrawPad) delay(5) }
            d.s.drawing += Stroke(Color.Blue, listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.7f)), 0.02f)
            d.send(Reply.Tapped("done", "완료"))
            withTimeout(3_000) { while (d.s.stage !is Stage.Show) delay(5) }
            assertEquals(expected, (d.s.stage as Stage.Show).caption)
            assertTrue("confirmation must retain the child's drawing", (d.s.stage as Stage.Show).art is Art.ChildDrawing)
            drawing.cancelAndJoin()
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.liveModes = previousModes
        }
    }

    @Test fun anUnnamedLiveStoryFriendNeverDisplaysTheMaskPlaceholder() = runBlocking {
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            Server.liveModes = emptySet()
            val d = Director(scope)
            d.s.speed = 0.001
            d.s.newcomer = "문어"
            d.s.newcomerKind = "문어"
            d.go(Scene.FRIENDS)
            withTimeout(3000) { while (d.s.stage !is Stage.FriendRate) delay(5) }
            val friends = (d.s.stage as Stage.FriendRate).friends
            assertFalse("An unnamed friend must use its descriptive label", friends.any { "{" in it.name })
            assertEquals(d.s.friendCallName, friends.first().name)
        } finally {
            scope.cancel()
            Server.liveModes = previousModes
        }
    }
}
