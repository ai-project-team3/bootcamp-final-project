package com.example.finalproject_demo

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 등장인물 인형 (10-06 조장) — 동화의 「토끼」가 외계인으로, 같이 만들기의 「강아지」가 남자아이로 책에 나왔다.
 * 맞는 그림이 없을 때만 오또가 인형을 만들고, 그 인형은 무대 · 책 · 다시 연 책에 남는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FriendArtTest {

    private fun story(newcomer: String) = DemoState().apply {
        mode = StoryMode.STORY
        slots["newcomer"] = newcomer
        newcomerKind = newcomer
        done += "draw"
    }

    /** Co-op after the drawing choice — no doll is asked for before it (#339 · CoopCompanionPresetTest) */
    private fun coop(companion: String) = DemoState().apply {
        mode = StoryMode.COOP
        companionKind = companion
        done += COOP_DRAW_DECIDED
    }

    @Test
    fun onlyACharacterWithNoPictureIsDrawn() {
        assertEquals("토끼", story("토끼").friendToDraw())
        assertNull("a felt preset has this name", story("외계인").friendToDraw())
        assertNull("the child's drawing is the default", story("토끼").apply {
            drawing.add(Stroke(Color.Red, listOf(Offset(0.1f, 0.1f), Offset(0.4f, 0.5f)), 4f))
        }.friendToDraw())
        assertEquals("강아지", coop("강아지").friendToDraw())
        assertNull("a person preset fits", coop("할머니").friendToDraw())
        assertNull("「친구」 alone keeps the generic friend", coop("친구").friendToDraw())
        assertNull(coop("").friendToDraw())
        assertNull("a real day in the diary keeps its presets", DemoState().apply {
            mode = StoryMode.DIARY; companionKind = "강아지"
        }.friendToDraw())
    }

    @Test
    fun theDollStandsInOnlyForTheWordsItWasMadeFor() {
        val s = story("토끼").apply { generatedFriend = GeneratedFriend("토끼", "local:/x/doll.png", "quad") }
        assertEquals(Art.Img("local:/x/doll.png", (s.friendArt as Art.Img).fallback, "quad"), s.friendArt)
        s.newcomerKind = "곰"
        assertTrue("the slot changed — the old doll is not this friend", s.friendArt is Art.ChildDrawing)

        val c = coop("강아지").apply { generatedFriend = GeneratedFriend("강아지", "local:/x/dog.png", "quad") }
        assertEquals("local:/x/dog.png", (c.companionArt as Art.Img).name)
        c.companionKind = "할머니"
        assertEquals("ic_p_grandma", (c.companionArt as Art.Img).name)
    }

    @Test
    fun aReopenedCoopBookKeepsItsDollAndTheImageIsNotCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("coop_books", Context.MODE_PRIVATE).edit().clear().commit()
        val made = DemoState().apply {
            mode = StoryMode.COOP
            coopPick = CoopPick("place", "동물원", "done")
            place = "맨 안쪽"; placeLabel = "동물원 맨 안쪽"; slots["place"] = "동물원 맨 안쪽까지 가 봤어요"
            companionKind = "강아지"; friend = "강아지"; slots["companion"] = "강아지와 함께 갔어요"
            problem = "길을 잃어버렸어"; slots["problem"] = "길을 잃어버렸어요"
            generatedFriend = GeneratedFriend("강아지", "local:/x/dog.png", "quad")
            generatedCharacters += GeneratedFriend("고양이", "local:/x/cat.png", "quad", "second")
            title = "강아지와 동물원"
        }
        made.storyCaptions = (1..made.pageCount).map { "${it}쪽 문장" }
        val store = LocalCoopBookStore(context)
        CoopShelf.attach(made, store)
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(made))

        val saved = store.load().single()
        assertEquals(GeneratedFriend("강아지", "local:/x/dog.png", "quad"), saved.book.visuals?.friend)
        assertTrue("the image cleanup must keep the doll", "local:/x/dog.png" in store.imageReferences()!!)
        assertTrue("the additional co-op character must also survive", "local:/x/cat.png" in store.imageReferences()!!)
        assertEquals(listOf("friend", "second"), saved.book.visuals!!.cast.map { it.role })
    }

    @Test
    fun aStoryBookWithoutADollStillReads() {
        val visuals = story("토끼").apply { templateKey = "A" }.captureStoryVisuals()
        assertNull(visuals.friend)
        assertEquals(visuals, storyVisualsFromJson(visuals.toJson()))
        val withDoll = visuals.copy(friend = GeneratedFriend("토끼", "local:/x/doll.png", null))
        assertEquals(withDoll, storyVisualsFromJson(withDoll.toJson()))
        assertTrue("local:/x/doll.png" in withDoll.images)
    }
}
