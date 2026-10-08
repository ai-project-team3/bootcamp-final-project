package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StoryHeroReuseTest {
    @Test fun naturalSpokenChoicesKeepNamesAndNegationDistinct() {
        val heroes = listOf("콩이", "별이", "응원", "새로").map { Hero(it, com.example.finalproject_demo.ui.HeroAttr()) }
        val cases = mapOf(
            "또 할래요" to "reuse:0", "응 또 할래" to "reuse:0", "웅 좋아요" to "reuse:0",
            "콩이요" to "reuse:0", "응 별이랑 할래" to "reuse:1", "나는 별이요!" to "reuse:1",
            "응원이랑 할래" to "reuse:2", "새로랑 할래" to "reuse:3",
            "아니" to "new", "아니요" to "new", "새로 만들래요" to "new", "다른 친구요" to "new",
            "응 새로 만들래" to "new", "콩이랑 안 할래" to "new",
        )
        cases.forEach { (spoken, expected) -> assertEquals(spoken, expected, previousHeroChoice(spoken, heroes)) }
        listOf("새로 안 만들래", "또 하고 싶지 않아", "응가", "콩이랑 별이", "콩이랑 할까 말까", "몰라", "").forEach {
            assertNull(it, previousHeroChoice(it, heroes))
        }
    }

    @Test fun unclearSpeechAndSilenceAskAgainAndAllowAnotherAnswer() = runBlocking {
        for (reply in listOf(Reply.Spoke("모르겠어"), Reply.Silent)) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val d = Director(scope, object : StoryBookStore {
                override fun load() = listOf(book())
                override fun save(book: SavedStoryBook) = Unit
            }).apply { s.speed = 0.01; s.timerOn = false }
            try {
                d.go(Scene.MAKEHERO)
                waitFor { d.s.stage is Stage.CardsRow && d.s.micEnabled }
                val firstLine = d.s.lineId
                d.send(reply)
                waitFor { d.s.lineId > firstLine }
                assertEquals(Scene.MAKEHERO, d.s.scene)
                assertTrue(d.s.line.contains("콩이"))
                assertTrue(d.s.line.contains("새로"))
                assertTrue(d.s.micEnabled)
                assertTrue((d.s.stage as Stage.CardsRow).cards.any { it.value == "new" })
                d.send(Reply.Spoke("콩이요"))
                waitFor { d.s.scene == Scene.PLACE }
                assertEquals("콩이", d.s.storyHeroCall)
            } finally { scope.cancel() }
        }
    }

    private fun book(id: String = "one", name: String = "콩이", image: String = "local:hero.png") =
        DemoState().apply {
            templateKey = "A"
            storyHeroCall = name
            storyHeroImage = image
        }.completedStoryBook()!!.let { it.copy(id = id, visuals = it.visuals!!.copy(hero = it.visuals.hero.copy(name = name))) }

    @Test fun savedHeroUsesTheChosenNameInsteadOfTheChildCall() {
        val state = DemoState().apply { templateKey = "A"; storyHeroCall = "콩이" }
        assertEquals("콩이", state.completedStoryBook()!!.visuals!!.hero.name)
    }

    @Test fun aPreviousHeroCardSkipsCreationAndKeepsTheLocalImage() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val stored = book()
        val d = Director(scope, object : StoryBookStore {
            override fun load() = listOf(stored)
            override fun save(book: SavedStoryBook) = Unit
        }).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.go(Scene.MAKEHERO)
            withTimeout(3000) { while (d.s.stage !is Stage.CardsRow) delay(5) }
            val cards = (d.s.stage as Stage.CardsRow).cards
            assertTrue("saved hero must be offered before the builder", cards.any { it.label == "콩이" })
            delay(30)
            d.send(Reply.Tapped(cards.first { it.label == "콩이" }.value, "콩이"))
            withTimeout(3000) { while (d.s.scene != Scene.PLACE) delay(5) }
            assertEquals("콩이", d.s.storyHeroCall)
            assertEquals(stored.visuals!!.hero.image, d.s.storyHeroImage)
            assertTrue(d.s.log.any { "hero_setup" in it && "choice=reuse" in it })
        } finally { scope.cancel() }
    }

    @Test fun recentHeroesAreDistinctAndDeletingTheLastBookRemovesTheHero() = runBlocking {
        val books = mutableListOf(book("newest"), book("duplicate"), book("second", "별이", "local:star.png"),
            book("third", "달이", "local:moon.png"), book("fourth", "해님", "local:sun.png"))
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, object : StoryBookStore {
            override fun load() = books.toList()
            override fun save(book: SavedStoryBook) = Unit
            override fun delete(id: String) = books.removeAll { it.id == id }
        })
        try {
            assertEquals(listOf("콩이", "별이", "달이"), recentStoryHeroes(d.storyBooks()).map { it.name })
            d.s.scene = Scene.PARENT
            assertTrue(d.deleteStoryBook("newest"))
            assertEquals("콩이", recentStoryHeroes(d.storyBooks()).first().name)
            assertTrue(d.deleteStoryBook("duplicate"))
            assertEquals(listOf("별이", "달이", "해님"), recentStoryHeroes(d.storyBooks()).map { it.name })
        } finally { scope.cancel() }
    }

    @Test fun spokenReuseKeepsAppearanceAndMakesNoImageRequest() = runBlocking {
        val paths = java.util.concurrent.CopyOnWriteArrayList<String>()
        val oldBase = Server.base
        val oldModes = Server.liveModes
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        FakeHttp { path, _ -> paths += path; null }.use { server ->
            Server.base = server.base; Server.liveModes = setOf(StoryMode.STORY)
            val original = book().let { it.copy(visuals = it.visuals!!.copy(hero = it.visuals.hero.copy(description = "구름 머리; 노란 우비"))) }
            val d = Director(scope, object : StoryBookStore {
                override fun load() = listOf(original)
                override fun save(book: SavedStoryBook) = Unit
            }).apply { s.speed = 0.01; s.timerOn = false }
            try {
                d.go(Scene.MAKEHERO)
                waitFor { d.s.stage is Stage.CardsRow && d.s.micEnabled }
                d.send(Reply.Spoke("또 할래"))
                waitFor { d.s.scene == Scene.PLACE && d.s.micEnabled }
                assertEquals("콩이", d.s.storyHeroCall)
                assertEquals("local:hero.png", d.s.storyHeroImage)
                assertEquals("구름 머리; 노란 우비", d.s.storyHeroDescription)
                assertEquals(0, d.s.images)
                assertFalse(paths.any { it.contains("image") })
                assertTrue(d.s.log.any { "hero_setup choice=reuse eligible=true turns=1 elapsed_ms=" in it })
            } finally { scope.cancel(); Server.base = oldBase; Server.liveModes = oldModes }
        }
    }

    @Test fun spokenNewChoiceOpensTheExistingBuilderAndLogsCreation() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, object : StoryBookStore {
            override fun load() = listOf(book())
            override fun save(book: SavedStoryBook) = Unit
        }).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.s.heroes += listOf(Hero("세 번째", com.example.finalproject_demo.ui.HeroAttr()),
                Hero("네 번째", com.example.finalproject_demo.ui.HeroAttr()))
            d.go(Scene.MAKEHERO)
            waitFor { d.s.stage is Stage.CardsRow && d.s.micEnabled }
            d.send(Reply.Spoke("새로"))
            waitFor { (d.s.stage as? Stage.CardsRow)?.cards?.any { it.value == "preset" } == true }
            delay(30)
            d.send(Reply.Tapped("preset", "골라서 만들기"))
            waitFor { d.s.stage is Stage.HeroBuilder }
            delay(30)
            d.send(Reply.Tapped("ok", "좋아"))
            waitFor { d.s.stage is Stage.NameEntry }
            delay(30)
            d.send(Reply.Tapped(NAME_TYPED, "새콩이"))
            waitFor { d.s.scene == Scene.PLACE }
            assertEquals("새콩이", d.s.storyHeroCall)
            assertEquals(4, d.s.heroes.size)
            assertTrue(d.s.log.any { "hero_setup choice=new eligible=true" in it })
        } finally { scope.cancel() }
    }

    @Test fun noSavedBooksAndCoopUseTheExistingBuilder() = runBlocking {
        for (mode in listOf(StoryMode.STORY, StoryMode.COOP)) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val d = Director(scope, object : StoryBookStore {
                override fun load() = if (mode == StoryMode.COOP) listOf(book()) else emptyList()
                override fun save(book: SavedStoryBook) = Unit
            }).apply { s.mode = mode; s.speed = 0.01; s.timerOn = false }
            try {
                d.go(Scene.MAKEHERO)
                waitFor { d.s.stage is Stage.CardsRow }
                assertEquals(listOf("voice", "preset"), (d.s.stage as Stage.CardsRow).cards.map { it.value })
            } finally { scope.cancel() }
        }
    }

    private suspend fun waitFor(ready: () -> Boolean) {
        withTimeout(3000) { while (!ready()) delay(5) }
    }

    @Test fun theDemoMicrophoneAnswersThePreviousHeroQuestion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, object : StoryBookStore {
            override fun load() = listOf(book())
            override fun save(book: SavedStoryBook) = Unit
        }).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.go(Scene.MAKEHERO)
            waitFor { d.s.stage is Stage.CardsRow && d.s.micEnabled }
            d.toggleMic()
            d.toggleMic()
            waitFor { (d.s.stage as? Stage.CardsRow)?.cards?.none { it.value == "new" } != false }
        } finally { scope.cancel() }
    }

    @Test fun resumingNewCreationDoesNotAskForAPreviousHeroAgain() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(scope, object : StoryBookStore {
            override fun load() = listOf(book())
            override fun save(book: SavedStoryBook) = Unit
        }).apply { s.speed = 0.01; s.timerOn = false }
        try {
            d.go(Scene.MAKEHERO)
            waitFor { d.s.stage is Stage.CardsRow && d.s.micEnabled }
            d.send(Reply.Spoke("새로"))
            waitFor { (d.s.stage as? Stage.CardsRow)?.cards?.any { it.value == "preset" } == true }
            delay(30)
            d.send(Reply.Tapped("preset", "골라서 만들기"))
            waitFor { d.s.stage is Stage.HeroBuilder }
            delay(30)
            d.send(Reply.Tapped("set:hair:long", "긴 머리"))
            waitFor { (d.s.stage as? Stage.HeroBuilder)?.attr?.hair == "long" }
            d.leaveToRoom()
            waitFor { d.s.scene == Scene.ADULT && d.s.stage == Stage.Adult }
            delay(30)
            d.send(Reply.Tapped("resume", "이어서 하기"))
            waitFor { d.s.scene == Scene.MAKEHERO && d.s.stage is Stage.HeroBuilder }
            assertEquals("long", (d.s.stage as Stage.HeroBuilder).attr.hair)
            assertEquals(1, d.s.log.count { "hero_choice choice=new eligible=true" in it })
        } finally { scope.cancel() }
    }
}
