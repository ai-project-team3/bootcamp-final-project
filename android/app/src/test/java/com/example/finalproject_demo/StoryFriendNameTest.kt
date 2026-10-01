package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StoryFriendNameTest {
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
