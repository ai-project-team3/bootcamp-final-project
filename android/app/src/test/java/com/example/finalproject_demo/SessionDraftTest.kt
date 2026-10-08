package com.example.finalproject_demo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.CoopPick
import com.example.finalproject_demo.demo.CoopShelf
import com.example.finalproject_demo.demo.DemoBtn
import com.example.finalproject_demo.demo.DemoState
import com.example.finalproject_demo.demo.DiaryPiece
import com.example.finalproject_demo.demo.Director
import com.example.finalproject_demo.demo.GeneratedFriend
import com.example.finalproject_demo.demo.Hero
import com.example.finalproject_demo.demo.Level
import com.example.finalproject_demo.demo.LocalSessionDraftStore
import com.example.finalproject_demo.demo.PieceLook
import com.example.finalproject_demo.demo.Reply
import com.example.finalproject_demo.demo.Scene
import com.example.finalproject_demo.demo.SessionDraft
import com.example.finalproject_demo.demo.SessionDraftStore
import com.example.finalproject_demo.demo.StoryImageStore
import com.example.finalproject_demo.demo.StoryMode
import com.example.finalproject_demo.demo.Stroke
import com.example.finalproject_demo.demo.TalkLine
import com.example.finalproject_demo.demo.TurnNote
import com.example.finalproject_demo.demo.applyDraft
import com.example.finalproject_demo.demo.captureDraft
import com.example.finalproject_demo.demo.diaryDay
import com.example.finalproject_demo.demo.newDiaryDay
import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.ui.HeroAttr
import com.example.finalproject_demo.ui.shell.LocalWipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * #336 — a story survives a killed process: saved at scene boundaries and after answers, put back on launch,
 * resumed through the in-app 「이어서」 path without spending a new star or book, and gone once the book is done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDraftTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    // ── round trip ──────────────────────────────────────────────

    private fun DemoState.fillABook() {
        mode = StoryMode.STORY
        place = "바닷속"; problem = "문어가 울었어"; cause = "친구가 없어서"; newcomer = "문어"
        slots["place"] = "바닷속"; slots["problem"] = "문어가 울었어"; slots["cause"] = "친구가 없어서"; slots["try1"] = "노래했어"
        // one of each provenance — they must come back exactly (rule 5)
        slotBy["place"] = "child"; slotBy["problem"] = "card"; slotBy["cause"] = "mascot"; slotBy["try1"] = "child"
        storyUnneededSlots += "sound"; storyNextSlot = "solution"; endReason = null
        talk += TalkLine("otto", "어디로 갈까?"); talk += TalkLine("child", "바닷속", listOf("reason"))
        talk += TalkLine("card", "문어가 울었어"); talk += TalkLine("mascot", "친구가 없어서")
        turn = 3; reactions = 4; s1count = 1; s1streak = 1; modeVoice = 2; modeCard = 1; modeSilent = 1; mascotPicks = 1
        level = Level.REASON; levelAtStart = Level.CHAIN; templateKey = "C"
        notes += TurnNote("어디로?", "바닷속", "voice", true, setOf("place"), false, 1)
        signals += "S1 — \"바닷속\""; quotes += "바닷속"
        drawing += Stroke(Color(0xFFF25C4C), listOf(Offset(0.1f, 0.2f), Offset(0.3f, 0.4f)), 0.02f)
        drawingAspect = 1.5f
        storyBackground = "local:/x/bg.png"; storyHeroImage = "local:/x/hero.png"; storyHeroCall = "콩이"
        heroAttr = HeroAttr(hair = "long", shirt = Color(0xFFF9B233), glasses = "round")
        heroes += Hero("콩이", heroAttr!!, "local:/x/hero.png", "rig1", called = "콩이")
        generatedFriend = GeneratedFriend("문어", "local:/x/friend.png", null)
        m1Result = "star"; m2Result = "gift"
        heroTries += heroAttr!!
    }

    @Test fun draftRoundTripKeepsTheBookAndProvenanceExactly() {
        val a = DemoState().apply { fillABook() }
        val text = a.captureDraft(Scene.CAUSE).toJson().toString()

        val back = SessionDraft.fromJson(JSONObject(text))!!
        val b = DemoState().apply { applyDraft(back) }

        assertEquals(Scene.CAUSE, back.scene)
        assertEquals(a.slots.toMap(), b.slots.toMap())
        assertEquals("출처(child · card · mascot)가 바뀌었다", a.slotBy.toMap(), b.slotBy.toMap())
        assertEquals("리포트 대화 기록의 출처가 바뀌었다", a.talk.toList(), b.talk.toList())
        assertEquals(a.drawing.toList(), b.drawing.toList())
        assertEquals(a.heroes.toList(), b.heroes.toList())
        assertEquals(a.notes.toList(), b.notes.toList())
        assertEquals(listOf(3, 4, 2, 1, 1), listOf(b.turn, b.reactions, b.modeVoice, b.modeCard, b.modeSilent))
        assertEquals(Level.REASON, b.level)
        assertEquals("gift", b.m2Result)
        // everything in the field list comes back — a second capture is the same
        assertEquals(a.captureDraft(Scene.CAUSE, 1L).toJson().toString(), b.captureDraft(Scene.CAUSE, 1L).toJson().toString())
        assertEquals(setOf("local:/x/bg.png", "local:/x/hero.png", "local:/x/friend.png"), back.imageReferences())
    }

    @Test fun diaryPiecesAndCoopPlanRoundTrip() {
        val a = DemoState().apply {
            mode = StoryMode.DIARY
            newDiaryDay().pieces += DiaryPiece(0, listOf(Stroke(Color.Blue, listOf(Offset(0f, 0f), Offset(1f, 1f)))), "고양이", PieceLook.ORIGINAL)
            diaryDay.pieceStories[0] = "고양이가 잤어"
            coopPick = CoopPick("job", "소방관", "dream"); parentQuestions += "불은 어떻게 꺼?"; parentQIndex = 1
        }
        val b = DemoState().apply { applyDraft(SessionDraft.fromJson(JSONObject(a.captureDraft(Scene.DIARY).toJson().toString()))!!) }
        assertEquals(a.diaryDay.pieces.toList(), b.diaryDay.pieces.toList())
        assertEquals("고양이가 잤어", b.diaryDay.pieceStories[0])
        assertEquals(CoopPick("job", "소방관", "dream"), b.coopPick)
        assertEquals(listOf("불은 어떻게 꺼?"), b.parentQuestions.toList())
        assertEquals(1, b.parentQIndex)
    }

    // ── the file ────────────────────────────────────────────────

    @Test fun fileStoreIsAtomicDiscardsDamageAndIsWiped() {
        val store = LocalSessionDraftStore(ctx)
        store.save(DemoState().apply { fillABook() }.captureDraft(Scene.EVENT))
        assertEquals(Scene.EVENT, LocalSessionDraftStore(ctx).load()?.scene)

        // a damaged file is not half-restored — it is dropped
        File(ctx.filesDir, "session_draft/draft.json").writeText("{not json")
        assertNull(LocalSessionDraftStore(ctx).load())
        assertFalse(File(ctx.filesDir, "session_draft/draft.json").exists())

        store.save(DemoState().apply { fillABook() }.captureDraft(Scene.EVENT))
        assertTrue(LocalWipe.wipe(ctx))
        assertNull("「모두 지우기」 뒤에 만들던 이야기가 남았다", LocalSessionDraftStore(ctx).load())
    }

    @Test fun imageCleanupKeepsPicturesTheDraftStillNeeds() {
        val images = StoryImageStore(ctx)
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val bg = images.save(png)!!
        val orphan = images.save(png)!!
        val drafts = SessionDraftStore.Memory()
        drafts.save(DemoState().apply { mode = StoryMode.STORY; storyBackground = bg }.captureDraft(Scene.EVENT))
        val scope = CoroutineScope(SupervisorJob())
        try {
            // launch order before the draft is put back: the state is empty, only the file names the picture
            val d = Director(scope, storyImageStore = images, draftStore = drafts)
            CoopShelf.attach(ctx, d.s)
            d.recoverStoryImages()
            assertTrue("만들던 이야기의 배경을 지웠다", File(bg.removePrefix("local:")).exists())
            assertFalse(File(orphan.removePrefix("local:")).exists())
            d.discardDraft()
            d.recoverStoryImages()
            assertFalse("버린 이야기의 그림이 남았다", File(bg.removePrefix("local:")).exists())
        } finally { scope.cancel() }
    }

    // ── the flow ────────────────────────────────────────────────

    private suspend fun await(ms: Long = 5_000, cond: () -> Boolean): Boolean? =
        withTimeoutOrNull(ms) { while (!cond()) delay(3); true }

    private fun pick(bs: List<DemoBtn>): DemoBtn? =
        bs.firstOrNull { "🎲" in it.label } ?: bs.firstOrNull { "🖐" in it.label } ?: bs.firstOrNull { "✅" in it.label }
            ?: bs.firstOrNull { "▶" in it.label } ?: bs.firstOrNull { "🗣" in it.label }
            ?: bs.firstOrNull { "🤐" !in it.label && "😶" !in it.label }

    private suspend fun Director.step(): String? {
        if (await(4_000) { pick(s.buttons) != null } == null) return null
        repeat(12) {
            val b = pick(s.buttons) ?: return null
            val before = s.lineId
            b.onClick()
            if (await(600) { s.lineId != before || pick(s.buttons)?.label != b.label } != null) return b.label
        }
        return null
    }

    private suspend fun Director.walkTo(goal: Set<Scene>, max: Int = 120) = coroutineScope {
        val watch = launch { while (isActive) delay(1) }
        var n = 0
        while (s.scene !in goal && n++ < max) { if (step() == null) break; delay(20) }
        watch.cancel()
    }

    private suspend fun Director.startStory() {
        go(Scene.ADULT)
        assertNotNull(await { s.buttons.any { "이야기 만들기 탭" in it.label } })
        s.buttons.first { "이야기 만들기 탭" in it.label }.onClick()
        assertNotNull(await { s.scene == Scene.PARTNER })
    }

    /** A Director as the app makes one at launch — a new process with only the phone's storage */
    private fun launch(scope: CoroutineScope, drafts: SessionDraftStore) =
        Director(scope, draftStore = drafts).apply { s.speed = 0.01; restoreDraft() }

    @Test fun killedMidStoryComesBackAtTheSceneWithoutANewStarOrBook() = runBlocking {
        val drafts = SessionDraftStore.Memory()
        val first = CoroutineScope(coroutineContext + SupervisorJob())
        val d = Director(first, draftStore = drafts).apply { s.speed = 0.01 }
        d.startStory()
        d.walkTo(setOf(Scene.CAUSE))
        assertEquals(Scene.CAUSE, d.s.scene)
        assertNotNull("장면 경계에서 저장하지 않았다", await { drafts.load()?.scene == Scene.CAUSE })
        assertTrue("답마다 저장하지 않았다", drafts.writes > 3)
        val slots = d.s.slots.toMap(); val by = d.s.slotBy.toMap(); val talk = d.s.talk.toList()
        val turn = d.s.turn; val reactions = d.s.reactions
        assertTrue("검사가 아무 답도 안 했다", slots.isNotEmpty() && talk.isNotEmpty())   // the scripted story leaves slotBy to live judging · co-op
        first.cancel()                                     // the process is killed — memory is gone

        val second = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            CallLimits.enabled = true
            val e = launch(second, drafts)
            assertEquals("다시 켰는데 이어서 할 이야기를 모른다", Scene.CAUSE, e.s.paused)
            assertEquals(StoryMode.STORY, e.s.mode)
            assertEquals(slots, e.s.slots.toMap())
            assertEquals("출처가 바뀌었다", by, e.s.slotBy.toMap())
            assertEquals(talk, e.s.talk.toList())
            assertEquals(turn to reactions, e.s.turn to e.s.reactions)
            val booksLeft = CallLimits.booksLeftToday()
            val startEvents = e.s.events.count { it.startsWith("story_start") }
            e.go(Scene.ADULT)
            assertNotNull(await { e.s.scene == Scene.ADULT && e.s.buttons.isNotEmpty() })
            delay(30)
            e.send(Reply.Tapped("resume", "이어서"))
            assertNotNull("「이어서」로 멈춘 장면에 안 갔다", await { e.s.scene == Scene.CAUSE })
            assertEquals("이어 가는데 하루 별을 썼다", 0, e.s.usedToday)
            assertEquals("이어 가는데 새 책으로 셌다", booksLeft, CallLimits.booksLeftToday())
            assertEquals("이어 가는데 별이 날아갔다", 0, e.s.starFlights)
            assertEquals("이어 가는데 story_start 가 또 찍혔다", startEvents, e.s.events.count { it.startsWith("story_start") })
            assertEquals(slots, e.s.slots.filterKeys { it in slots }.toMap())
        } finally {
            second.cancel()
            CallLimits.reset()
        }
    }

    @Test fun finishingTheBookClearsTheDraftAndTheEndDoesNotWriteItBack() = runBlocking {
        val drafts = SessionDraftStore.Memory()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope, draftStore = drafts).apply { s.speed = 0.01 }
            d.startStory()
            d.walkTo(setOf(Scene.BOOK))
            assertEquals(Scene.BOOK, d.s.scene)
            assertNotNull("책이 펼쳐졌는데 만들던 이야기가 남았다", await { drafts.raw == null })
            d.holdSession()                                 // the app goes to the background while reading
            d.walkTo(setOf(Scene.ADULT))
            assertNull("책 뒤(친구 · 선물 · 방)에서 다 만든 책을 다시 저장했다", drafts.raw)
            assertNull(launch(scope, drafts).s.paused)
        } finally { scope.cancel() }
    }

    @Test fun aNewStoryReplacesTheDraftAndStartingOverClearsIt() = runBlocking {
        val drafts = SessionDraftStore.Memory()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope, draftStore = drafts).apply { s.speed = 0.01 }
            d.startStory()
            d.walkTo(setOf(Scene.EVENT))
            d.leaveToRoom()
            assertNotNull(await { d.s.scene == Scene.ADULT && d.s.buttons.isNotEmpty() })
            assertEquals(Scene.EVENT, drafts.load()?.scene)
            // 「새로」 — the room sends the mode itself: the old book is dropped, here and on the phone
            delay(30)
            d.send(Reply.Tapped("start", "이야기 만들기"))
            assertNotNull(await { d.s.scene == Scene.PARTNER })
            assertNotNull(await { drafts.load()?.scene == Scene.PARTNER })
            assertTrue("새 이야기에 지난 이야기 조각이 남았다", drafts.load()!!.state.getJSONObject("slots").length() == 0)
            d.restart()
            assertNotNull("「처음부터」 뒤에 만들던 이야기가 남았다", await { drafts.raw == null && d.s.scene == Scene.ADULT })
        } finally { scope.cancel() }
    }
}
