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
class StoryFriendChoiceTest {
    @Test fun choosingAFriendUpdatesTheCardBeforeTheReactionFinishes() = runBlocking {
        withFriends(live = false) { d ->
            d.send(Reply.Tapped("keep:friend", "콩이"))
            awaitChange("The chosen card stayed unanswered during the reaction") {
                (d.s.stage as? Stage.FriendRate)?.friends?.first()?.keep == true
            }
            assertEquals(Scene.FRIENDS, d.s.scene)
            assertEquals("Nothing is recorded before the arrow", 0, ratings(d).size)
        }
    }

    /** 10-05 device round: a mis-tap on 「안녕」 left at once. The last choice no longer leaves the scene. */
    @Test fun theLastChoiceWaitsForTheArrow() = runBlocking {
        withFriends(live = true) { d ->
            d.send(Reply.Tapped("bye:friend", "콩이"))
            awaitChange("The card did not show the choice") { (d.s.stage as? Stage.FriendRate)?.friends?.first()?.keep == false }
            delay(300)
            assertEquals(Scene.FRIENDS, d.s.scene)
            d.send(Reply.Tapped("done", "다음"))
            awaitChange("The arrow did not move on") { d.s.scene == Scene.END }
            assertTrue("keep=false" in ratings(d).single())
        }
    }

    @Test fun aMisTapCanBeChangedAndOnlyTheFinalChoiceIsRecorded() = runBlocking {
        withFriends(live = false) { d ->
            d.send(Reply.Tapped("bye:friend", "콩이"))
            d.send(Reply.Tapped("keep:friend", "콩이"))
            d.send(Reply.Tapped("done", "다음"))
            awaitChange("The next button was lost behind a changed choice") { d.s.scene == Scene.END }
            assertTrue("The last selection must win", "keep=true" in ratings(d).single())
            assertEquals(1, d.s.keptFriends.count { it == "콩이" })
        }
    }

    @Test fun choicesForTwoFriendsAreBothRecordedOnTheArrow() = runBlocking {
        withFriends(live = false) { d ->
            d.send(Reply.Tapped("keep:friend", "콩이"))
            d.send(Reply.Tapped("bye:dino", "공룡"))
            d.send(Reply.Tapped("done", "다음"))
            awaitChange("A friend's choice was lost") { ratings(d).size == 2 && d.s.scene == Scene.END }
            assertTrue(ratings(d).any { "friend_id=friend, keep=true" in it })
            assertTrue(ratings(d).any { "friend_id=dino, keep=false" in it })
        }
    }

    @Test fun theNextButtonCanLeaveDuringAReactionWithoutInventingMoreRatings() = runBlocking {
        withFriends(live = false) { d ->
            d.send(Reply.Tapped("keep:friend", "콩이"))
            d.send(Reply.Tapped("done", "다음"))
            awaitChange("The next button still waited for narration") { d.s.scene == Scene.END }
            assertEquals(1, ratings(d).size)
        }
    }

    private fun ratings(d: Director) = d.s.events.filter { it.startsWith("friend_rating ") }

    private suspend fun awaitChange(message: String, changed: () -> Boolean) {
        val applied = withTimeoutOrNull(1_500) {
            while (!changed()) delay(5)
            true
        } ?: false
        assertTrue(message, applied)
    }

    private suspend fun withFriends(live: Boolean, check: suspend (Director) -> Unit) = coroutineScope {
        val previousBase = Server.base
        val previousModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val server = StoryTestServer { _, _ -> JSONObject() }
        try {
            Server.base = server.base
            Server.liveModes = if (live) setOf(StoryMode.STORY) else emptySet()
            val d = Director(scope)
            d.s.speed = 1.0
            d.s.slots["newcomer"] = "강아지"
            d.s.newcomer = "강아지"
            d.s.newcomerKind = "강아지"
            d.s.friendName = "콩이"
            d.go(Scene.FRIENDS)
            withTimeout(3_000) { while (d.s.stage !is Stage.FriendRate) delay(5) }
            check(d)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            Server.base = previousBase
            Server.liveModes = previousModes
            server.close()
        }
    }
}
