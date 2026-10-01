package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryLiveFriendsTest {
    @Test fun liveStoryRatesOnlyTheFriendChosenDuringTheConversation() = runBlocking {
        withDirector(live = true) { d ->
            d.s.slots["newcomer"] = "문어"
            d.s.newcomer = "문어"
            d.s.newcomerKind = "문어"
            d.s.friendName = "도리"
            d.go(Scene.FRIENDS)
            awaitFriendsOrEnd(d)

            val friends = (d.s.stage as Stage.FriendRate).friends
            assertEquals("A default dinosaur was never chosen in the live story", 1, friends.size)
            assertEquals("friend", friends.single().id)
            assertEquals("도리", friends.single().name)
            assertEquals(d.s.friendArt, friends.single().art)
        }
    }

    @Test fun liveStoryWithoutAChosenFriendSkipsRatingsInsteadOfInventingCharacters() = runBlocking {
        withDirector(live = true) { d ->
            d.go(Scene.FRIENDS)
            awaitFriendsOrEnd(d)

            assertEquals(Scene.END, d.s.scene)
            assertFalse(d.s.stage is Stage.FriendRate)
        }
    }

    @Test fun scriptedStoryStillRatesItsScriptedFriendAndDinosaur() = runBlocking {
        withDirector(live = false) { d ->
            d.go(Scene.FRIENDS)
            awaitFriendsOrEnd(d)

            val friends = (d.s.stage as Stage.FriendRate).friends
            assertEquals(listOf("friend", "dino"), friends.map { it.id })
            assertTrue(friends.last().art is Art.DinoArt)
        }
    }

    private suspend fun awaitFriendsOrEnd(d: Director) = withTimeout(3_000) {
        while (d.s.stage !is Stage.FriendRate && d.s.scene != Scene.END) delay(5)
    }

    private suspend fun withDirector(live: Boolean, check: suspend (Director) -> Unit) = coroutineScope {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val server = StoryTestServer { _, _ -> JSONObject() }
        try {
            Server.base = server.base
            Server.liveModes = if (live) setOf(StoryMode.STORY) else emptySet()
            val d = Director(scope)
            d.s.speed = 0.001
            check(d)
        } finally {
            scope.cancel()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
