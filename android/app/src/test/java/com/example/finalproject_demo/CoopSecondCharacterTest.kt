package com.example.finalproject_demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.Art
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.CoopShelved
import com.example.finalproject_demo.demo.LocalCoopBookStore
import com.example.finalproject_demo.demo.coopSecondDoll
import com.example.finalproject_demo.demo.restoreCoopBook
import com.example.finalproject_demo.demo.pageCount
import com.example.finalproject_demo.demo.COOP_SECOND_ROLE
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.GeneratedFriend
import com.example.finalproject_demo.demo.PageKind
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.charactersToDraw
import com.example.finalproject_demo.demo.coopActorIn
import com.example.finalproject_demo.demo.coopSecondArt
import com.example.finalproject_demo.demo.coopSecondCharacter
import com.example.finalproject_demo.demo.coopSecondOnPage
import com.example.finalproject_demo.demo.drawFriend
import com.example.finalproject_demo.demo.sceneActorCapacity
import com.example.finalproject_demo.net.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #339 ② — co-op's second character: an actor from the problem · cause the child gave, else from a parent question's
 * answer, standing in the middle spot (design §2 · §4). Presets need no request; a failed doll is tried twice at most.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoopSecondCharacterTest {

    private fun coop(reason: String = "done") = DemoState().apply {
        mode = StoryMode.COOP
        coopPick = CoopPick("place", "동물원", reason)
        companionKind = "엄마"
    }

    @Test
    fun coopAnswersNameTheirActors() {
        assertEquals("사자", coopActorIn("사자가 울타리를 넘었어"))
        assertEquals("기린", coopActorIn("기린한테 사과 줬어"))
        assertEquals("강아지", coopActorIn("강아지랑 놀았어"))
        assertEquals("펭귄", coopActorIn("펭귄"))
        assertNull("not an actor — 「사자 모양 과자」", coopActorIn("사자 모양 과자를 먹었어"))
        assertNull("negated", coopActorIn("기린이 없었어"))
        assertNull("「말도 안 돼」 is not a horse", coopActorIn("말도 안 돼"))
    }

    @Test
    fun theProblemActorComesBeforeTheParentAnswer() {
        val s = coop().apply {
            slots["problem"] = "사자가 울타리를 넘었어"; slotBy["problem"] = "child"
            slots["parent1"] = "기린한테 사과 줬어"
        }
        assertEquals("사자", s.coopSecondCharacter())
        s.slots["problem"] = "비가 왔어"
        assertEquals("기린", s.coopSecondCharacter())
    }

    @Test
    fun theCompanionTheHeroAndMascotWordsAreNotTheSecond() {
        val s = coop().apply {
            companionKind = "기린"
            slots["parent1"] = "기린한테 사과 줬어"
            slots["problem"] = "펭귄이 넘어졌어"; slotBy["problem"] = "mascot"
        }
        assertNull("the companion is not a second character; a mascot problem is not read", s.coopSecondCharacter())
        assertNull("diary · story: never", DemoState().apply {
            mode = StoryMode.DIARY; slots["problem"] = "사자가 울타리를 넘었어"; slotBy["problem"] = "child"
        }.coopSecondCharacter())
    }

    @Test
    fun aPresetCharacterIsNeverAskedFor() {
        val s = coop().apply { slots["parent1"] = "할머니랑 놀았어" }
        assertEquals("할머니", s.coopSecondCharacter())
        assertTrue(s.charactersToDraw().none { it.role == COOP_SECOND_ROLE })
        assertEquals("ic_p_grandma", (s.coopSecondArt() as? Art.Img)?.name)
        val giraffe = coop().apply { slots["parent1"] = "얼룩말한테 인사했어" }
        assertTrue(giraffe.charactersToDraw().any { it.role == COOP_SECOND_ROLE && it.words == "얼룩말" })
    }

    /** ⚖️2 — no doll yet: nothing on a real day, ✨ for 좋아해요 */
    @Test
    fun aMissingDollStandsOnlyInADream() {
        assertNull(coop("done").apply { slots["parent1"] = "얼룩말한테 인사했어" }.coopSecondArt())
        assertNull(coop("soon").apply { slots["parent1"] = "얼룩말한테 인사했어" }.coopSecondArt())
        assertEquals(Art.Emoji("✨"), coop("dream").apply { slots["parent1"] = "얼룩말한테 인사했어" }.coopSecondArt())
    }

    @Test
    fun theMiddleSpotIsKeptAndOnlyPagesNamingItShowIt() {
        val s = coop().apply {
            slots["parent1"] = "할머니랑 놀았어"
        }
        assertEquals(3, s.sceneActorCapacity)
        assertEquals(2, DemoState().apply { mode = StoryMode.DIARY }.sceneActorCapacity)
        assertNull(s.coopSecondOnPage(PageKind.COVER, "할머니랑 놀았어요"))
        assertNull(s.coopSecondOnPage(PageKind.RUB, "할머니랑 놀았어요"))
        assertNull("a page not naming it", s.coopSecondOnPage(PageKind.DEPART, "동물원에 갔어요"))
        assertEquals("ic_p_grandma", (s.coopSecondOnPage(PageKind.DEPART, "할머니랑 사자를 봤어요") as? Art.Img)?.name)
    }

    /** A reopened book keeps the doll it was made with */
    @Test
    fun aReopenedBookKeepsItsSecondDoll() {
        val s = coop().apply {
            slots["parent1"] = "얼룩말한테 인사했어"
            generatedCharacters.add(GeneratedFriend("얼룩말", "story_zebra.png", null, COOP_SECOND_ROLE))
            readingSavedCast = true
        }
        assertEquals("story_zebra.png", (s.coopSecondArt() as? Art.Img)?.name)
    }

    /** #378 review — a one-syllable animal inside another word is nobody: 모양 · 태양 · 인형 · 장소 */
    @Test
    fun aShortNounInsideAWordIsNotAnActor() {
        listOf("모양이 이상했어", "태양이 뜨거웠어", "인형이 망가졌어", "장소가 멀었어").forEach { said ->
            assertNull(said, coopActorIn(said))
            val s = coop().apply { slots["problem"] = said; slotBy["problem"] = "child" }
            assertNull(said, s.coopSecondCharacter())
            assertTrue("no doll asked for: $said", s.charactersToDraw().none { it.role == COOP_SECOND_ROLE })
        }
        assertEquals("a word of its own still counts", "양", coopActorIn("양이 풀을 먹었어"))
        val sheep = coop().apply { slots["parent1"] = "양한테 풀을 줬어" }
        assertNull("「양말」 is no 양", sheep.coopSecondOnPage(PageKind.DEPART, "양말을 신고 갔어요"))
        assertNull("「인형극」 is no 형", coop().apply { slots["parent1"] = "형이랑 놀았어" }
            .coopSecondOnPage(PageKind.DEPART, "인형극을 봤어요"))
    }

    /** #378 review — saved and reopened through the shelf: a doll made for an earlier second character is not kept */
    @Test
    fun aDollForAnEarlierSecondIsNotSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("coop_books", Context.MODE_PRIVATE).edit().clear().commit()
        val made = coop().apply {
            title = "동물원"; place = "동물원"; placeLabel = "동물원"; slots["place"] = "동물원"
            slots["parent1"] = "얼룩말한테 인사했어"
            generatedCharacters.add(GeneratedFriend("얼룩말", "local:/x/zebra.png", null, COOP_SECOND_ROLE))
            // the cause told later names the lion — the second character is now 사자
            slots["cause"] = "사자가 배고파서"; slotBy["cause"] = "child"
        }
        made.storyCaptions = (1..made.pageCount).map { "${it}쪽 사자 이야기" }
        val store = LocalCoopBookStore(context)
        CoopShelf.attach(made, store)
        assertEquals(CoopShelved.SAVED, CoopShelf.shelve(made))
        val saved = store.load().single()
        assertTrue(saved.book.visuals!!.cast.none { it.role == COOP_SECOND_ROLE })
        val again = DemoState().also { CoopShelf.attach(it, store) }
        assertTrue(again.restoreCoopBook(saved.book))
        assertNull("the zebra doll does not stand for the lion", again.coopSecondDoll())
    }

    /** §2-5 — a failed doll is asked for again on the next step, twice at most */
    @Test
    fun aFailedSecondDollIsTriedTwiceAtMost() = runBlocking {
        val sup = SupervisorJob()
        val d = Director(CoroutineScope(coroutineContext + sup))
        val server = StoryTestServer { _, _ -> JSONObject().put("preset", true) }
        try {
            Server.base = server.base
            Server.liveModes = setOf(StoryMode.COOP)
            d.s.apply { mode = StoryMode.COOP; coopPick = CoopPick("place", "동물원", "done"); slots["parent1"] = "얼룩말한테 인사했어" }
            repeat(4) { d.drawFriend(); delay(400) }
            assertEquals(2, server.requests.count { it.first == "/image" && it.second.optString("kind") == "character" })
        } finally { sup.cancel(); server.close(); Server.liveModes = emptySet(); Server.base = null }
    }
}
