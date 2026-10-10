package com.example.finalproject_demo

import com.example.finalproject_demo.demo.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryProblemCharacterTest {
    @Test fun clearSubjectsPreserveTheChildsModifiers() {
        mapOf("괴물이 길을 막았어" to "괴물", "사자가 나타났어" to "사자",
            "도둑 고양이가 사탕을 훔쳤어" to "도둑 고양이",
            "빨간 바늘괴물이 왔어" to "빨간 바늘괴물", "바늘괴물" to "바늘괴물",
            "도둑 고양이" to "도둑 고양이", "아기 공룡" to "아기 공룡",
            "엄마 친구 강아지가 길을 막았어" to "강아지").forEach { (said, expected) ->
            assertEquals(said, expected, problemActorIn(said))
        }
    }

    @Test fun objectsNegationAndUnclearMentionsDoNotInventCharacters() {
        listOf("문이 잠겼어", "사자 모양 과자를 먹었어", "괴물인형이 찢어졌어",
            "사자를 그렸어", "괴물이 없었어", "괴물은 사실 없었어", "괴물이 나오지 않았어",
            "어떤 게 길을 막았어").forEach {
            assertNull(it, problemActorIn(it))
        }
    }

    @Test fun onlyChildSourceInStoryAddsASeparateActor() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            slots["newcomer"] = "토끼"
            slots["problem"] = "괴물이 길을 막았어"
            slotBy["problem"] = "mascot"
        }
        assertNull(s.storyProblemCharacter())
        s.slotBy["problem"] = "card"
        assertNull(s.storyProblemCharacter())
        s.slotBy["problem"] = "child"
        assertEquals("괴물", s.storyProblemCharacter())
        s.slots["newcomer"] = "빨간 괴물"
        assertNull("The drawn friend is not a second actor", s.storyProblemCharacter())
        s.slots["newcomer"] = "토끼"
        for (mode in listOf(StoryMode.COOP, StoryMode.DIARY)) {
            s.mode = mode
            assertNull(s.storyProblemCharacter())
        }
    }

    @Test fun causeCanSupplyAnActorWithoutAProblemSubject() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            slots["problem"] = "문이 잠겼어"
            slots["cause"] = "마녀가 잠갔어"
            slotBy["problem"] = "child"; slotBy["cause"] = "child"
        }
        assertEquals("마녀", s.storyProblemCharacter())
    }

    @Test fun undoingTheProblemHidesTheOldPictureEvenWhenAllSlotsAreEmpty() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            generatedCharacters += GeneratedFriend("괴물", "local:/monster.png", "blob", "problem")
        }
        assertNull(s.storyProblemArt())
        s.templateKey = "A"
        assertTrue("An undone actor must not return when reopening the finished book", s.captureStoryVisuals().cast.isEmpty())
    }

    @Test fun stageKeepsHeroDrawingAndProblemActorInThreeSeparatePlaces() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            slots["newcomer"] = "토끼"
            slots["problem"] = "괴물이 길을 막았어"; slotBy["problem"] = "child"
            drawing.add(Stroke(androidx.compose.ui.graphics.Color.Red,
                listOf(androidx.compose.ui.geometry.Offset(.2f, .2f)), 4f))
            generatedCharacters += GeneratedFriend("괴물", "local:/monster.png", "blob", "problem")
        }
        val world = s.storyConversationWorld()
        assertEquals(3, world.items.size)
        assertEquals(3, world.items.map { it.xf }.distinct().size)
        assertTrue(world.items[1].art is Art.ChildDrawing)
        assertEquals("local:/monster.png", (world.items[2].art as Art.Img).name)
        assertNotNull(s.storyProblemOnPage(PageKind.TALK, "괴물이 길을 비켜 주었어요."))
        assertNull(s.storyProblemOnPage(PageKind.TALK, "토끼는 집에 갔어요."))
        for (kind in listOf(PageKind.RUB, PageKind.DRAG, PageKind.COVER)) {
            assertNull("Mission controls must remain clear", s.storyProblemOnPage(kind, "괴물이 왔어"))
        }
    }

    @Test fun presetProblemActorSurvivesBookReopeningWithoutAnImageRequest() {
        val s = DemoState().apply {
            mode = StoryMode.STORY; templateKey = "A"
            slots["problem"] = "외계인이 길을 막았어"; slotBy["problem"] = "child"
        }
        assertTrue(s.charactersToDraw().none { it.role == "problem" })
        val original = s.storyProblemArt()
        assertNotNull(original)
        val book = SavedStoryBook("preset", "이야기", "dino", "bg_park",
            listOf(SavedStoryPage(PageKind.TALK, "외계인이 길을 막았어")), s.captureStoryVisuals())
        val reopened = DemoState().apply { restoreStoryBook(book.copy(visuals = storyVisualsFromJson(book.visuals!!.toJson()))) }
        assertEquals(original, reopened.storyProblemArt())
    }

    @Test fun aNamedHeroIsNotGeneratedAgainAsAProblemActor() {
        val s = DemoState().apply {
            mode = StoryMode.STORY
            storyHeroCall = "괴물"
            slots["problem"] = "괴물이 넘어졌어"; slotBy["problem"] = "child"
        }
        assertNull(s.storyProblemCharacter())
    }

    @Test fun anUnavailablePictureKeepsANeutralFallbackWhenReopening() {
        val s = DemoState().apply {
            mode = StoryMode.STORY; templateKey = "A"
            slots["problem"] = "괴물이 길을 막았어"; slotBy["problem"] = "child"
        }
        assertEquals(Art.Emoji("✨"), s.storyProblemArt())
        val book = SavedStoryBook("fallback", "이야기", "dino", "bg_park",
            listOf(SavedStoryPage(PageKind.TALK, "괴물이 길을 막았어")), s.captureStoryVisuals())
        val reopened = DemoState().apply { restoreStoryBook(book) }
        assertEquals(s.storyProblemArt(), reopened.storyProblemArt())
    }
}
