package com.example.finalproject_demo

import android.content.Context
import android.os.Looper
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.core.app.ApplicationProvider
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Bgm
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.sound.ChildSound
import com.example.finalproject_demo.ui.BookPageView
import com.example.finalproject_demo.ui.SavedStoryView
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w807dp-h393dp-land-440dpi")
class StorySoundHolderTest {
    @get:Rule val compose = createComposeRule()
    private val oldBase = Server.base
    private val oldModes = Server.liveModes
    private val oldRoot = ChildSound.root
    private val oldFrozen = motionFrozen
    private lateinit var folder: File
    private val players = mutableListOf<ShadowMediaPlayer>()

    @Before fun setup() {
        Bgm.resetForTest()   // an earlier MainActivity test leaves Bgm attached; book music would add a second MediaPlayer
        folder = Files.createTempDirectory("story_sound_holder").toFile()
        ChildSound.root = folder
        motionFrozen = true
        setLive(true)
        ShadowMediaPlayer.setCreateListener { _, player -> players += player }
    }

    @After fun cleanup() {
        Bgm.resetForTest()
        ShadowMediaPlayer.setCreateListener(null)
        ChildSound.root = oldRoot
        Server.base = oldBase
        Server.liveModes = oldModes
        motionFrozen = oldFrozen
        folder.deleteRecursively()
    }

    private fun setLive(live: Boolean) {
        Server.base = if (live) "http://127.0.0.1:1" else null
        Server.liveModes = if (live) setOf(StoryMode.STORY) else emptySet()
    }

    private fun Director.prepareBook() {
        s.mode = StoryMode.STORY
        s.templateKey = "C"
        s.newcomerKind = "용"
        s.slots["newcomer"] = "용"   // a live story's friend is the newcomer the child told (StoryNoFriendTest)
        s.friendName = "도리"
        s.storySoundClip = clip()
        s.speed = 0.01
        s.timerOn = false
    }

    private fun clip(): ChildSound.SoundClip {
        val file = File(folder, "session/own-clip.wav").apply {
            parentFile!!.mkdirs()
            writeBytes(Voice.wav(ShortArray(1600) { 2000 }, Voice.RATE))
        }
        return ChildSound.SoundClip("own-clip", file)
    }

    private suspend fun reachLastPage(d: Director) {
        d.go(Scene.BOOK)
        withTimeout(3_000) {
            while (d.s.stage !is Stage.BookPage) delay(5)
            while (d.s.bookPage < d.s.pageCount) {
                d.send(Reply.Tapped("next", "다음"))
                delay(15)
            }
        }
    }

    @Test fun theLiveLastPageNamesTheVisibleFriendInsteadOfTheHiddenDinosaur() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope).apply { prepareBook() }
            reachLastPage(d)
            assertTrue("The note must identify the visible friend: ${d.s.bookNote}", d.s.bookNote.contains("도리"))
            assertFalse(d.s.bookNote.contains(d.s.dino.name))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun aNamelessLiveFriendUsesTheVisibleHeroForReplay() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val d = Director(scope).apply {
                prepareBook()
                s.friendName = "{친구}"
                s.newcomerKind = ""
            }
            reachLastPage(d)
            assertTrue("Do not direct the child to a nonexistent named figure: ${d.s.bookNote}", d.s.bookNote.contains(d.s.childName))
            assertFalse(d.s.bookNote.contains(d.s.dino.name))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun tappingTheVisibleLiveFriendEmitsTheRecordingAction() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val d = Director(scope).apply { prepareBook() }
            var reply: Reply? = null
            compose.setContent { BookPageView(d, Stage.BookPage(d.s.pageCount), onReply = { reply = it }) }
            tapFriend()
            compose.runOnIdle { assertEquals("sound", (reply as? Reply.Tapped)?.value) }
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @Test fun theFriendKeepsItsOrdinaryReactionWhenNoRecordingExists() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val d = Director(scope).apply { prepareBook(); s.storySoundClip = null }
            var reply: Reply? = null
            compose.setContent { BookPageView(d, Stage.BookPage(d.s.pageCount), onReply = { reply = it }) }
            tapFriend()
            compose.runOnIdle { assertEquals("tool:hand:friend", (reply as? Reply.Tapped)?.value) }
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @Test fun tappingTheHeroReplaysWhenTheLiveFriendHasNoName() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val d = Director(scope).apply { prepareBook(); s.friendName = "{친구}"; s.newcomerKind = "" }
            var reply: Reply? = null
            compose.setContent { BookPageView(d, Stage.BookPage(d.s.pageCount), onReply = { reply = it }) }
            compose.onRoot().performTouchInput { click(Offset(width * 0.17f, height * 0.49f)) }
            compose.runOnIdle { assertEquals("sound", (reply as? Reply.Tapped)?.value) }
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun aStoredLiveCoverDoesNotGrowADinosaurAfterDisconnecting() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val made = Director(scope).apply { prepareBook() }
            val book = mutableStateOf(made.s.completedStoryBook()!!)
            compose.setContent { SavedStoryView(made, Stage.SavedStory(book.value, 0)) }
            val before = File(folder, "cover-before.png")
            val after = File(folder, "cover-after.png")
            val options = RoborazziOptions(taskType = RoborazziTaskType.Record)
            compose.onRoot().captureRoboImage(before.path, roborazziOptions = options)
            compose.runOnIdle { setLive(false); book.value = book.value.copy(id = "reopened-cover") }
            compose.onRoot().captureRoboImage(after.path, roborazziOptions = options)
            assertTrue("A stored cover's figures must not depend on the current server connection",
                BitmapFactory.decodeFile(before.path).sameAs(BitmapFactory.decodeFile(after.path)))
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @Test fun aStoredLiveBooksFriendPlaysItsOwnClipAfterTheServerIsDisconnected() = storedReplay(live = true)

    @Test fun aStoredScriptedBooksDinosaurStillPlaysWhenTheServerIsEnabled() = storedReplay(live = false)

    @Test fun aStoredLiveRubPageKeepsItsFiguresAfterDisconnecting() = storedRubFigures(live = true)

    @Test fun aStoredScriptedRubPageKeepsItsFiguresAfterConnecting() = storedRubFigures(live = false)

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun storedRubFigures(live: Boolean) {
        setLive(live)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val made = Director(scope).apply { prepareBook() }
            val book = mutableStateOf(made.s.completedStoryBook()!!)
            val page = book.value.pages.indexOfFirst { it.kind == PageKind.RUB } + 1
            assertTrue(page > 0)
            compose.setContent { SavedStoryView(made, Stage.SavedStory(book.value, page)) }
            val before = File(folder, "rub-before.png")
            val after = File(folder, "rub-after.png")
            val options = RoborazziOptions(taskType = RoborazziTaskType.Record)
            compose.onRoot().captureRoboImage(before.path, roborazziOptions = options)
            compose.runOnIdle { setLive(!live); book.value = book.value.copy(id = "reopened-rub") }
            compose.onRoot().captureRoboImage(after.path, roborazziOptions = options)
            assertTrue("A stored mission's figures must not depend on the current server connection",
                BitmapFactory.decodeFile(before.path).sameAs(BitmapFactory.decodeFile(after.path)))
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @Test fun theLiveBookFriendActuallyStartsTheActiveRecording() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val d = Director(scope).apply { prepareBook() }
            val clip = d.s.storySoundClip!!
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(clip.file.path), ShadowMediaPlayer.MediaInfo(60_000, 0))
            compose.setContent { BookPageView(d, d.s.stage as? Stage.BookPage ?: Stage.BookPage(0)) }
            compose.runOnIdle { d.go(Scene.BOOK) }
            for (page in 1..d.s.pageCount) {
                compose.runOnIdle { d.send(Reply.Tapped("next", "다음")) }
                compose.waitUntil(3_000) { shadowOf(Looper.getMainLooper()).idle(); d.s.bookPage == page }
            }
            tapFriend()
            waitForPlayback()
            compose.runOnIdle {
                assertEquals(DataSource.toDataSource(clip.file.path), players.single().dataSource)
                assertTrue(players.single().isReallyPlaying)
                players.single().invokeCompletionListener()
            }
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    @Test fun anOlderBookWithoutLiveProvenanceStillKeepsItsFiguresAndCaptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("story_books", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val state = DemoState().apply { templateKey = "C"; friendName = "예전 친구" }
        val original = state.completedStoryBook()!!
        LocalStoryBookStore(context).save(original)
        val raw = JSONArray(prefs.getString("books", null))
        raw.getJSONObject(0).getJSONObject("visuals").remove("liveStory")
        prefs.edit().putString("books", raw.toString()).commit()

        val reopened = LocalStoryBookStore(context).load().single()
        assertNotNull(reopened.visuals)
        assertNull(reopened.visuals!!.liveStory)
        assertEquals(original.pages, reopened.pages)
        val reader = DemoState()
        assertTrue(reader.restoreStoryBook(reopened))
        assertEquals("예전 친구", reader.friendName)
    }

    private fun storedReplay(live: Boolean) {
        setLive(live)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("story_books", Context.MODE_PRIVATE).edit().clear().commit()
            val store = LocalStoryBookStore(context)
            val made = Director(scope).apply { prepareBook() }
            val book = made.s.completedStoryBook()!!
            val kept = ChildSound.keep(made.s.storySoundClip!!, book.id)!!
            store.save(book)
            val reloaded = LocalStoryBookStore(context).load().single()
            assertEquals(live, reloaded.visuals!!.liveStory)
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(kept.file.path), ShadowMediaPlayer.MediaInfo(60_000, 0))
            setLive(!live)
            val current = Director(scope).apply { s.friendName = "다른 이야기 친구"; s.storySoundClip = clip() }
            compose.setContent { SavedStoryView(current, Stage.SavedStory(reloaded, reloaded.pages.size)) }
            if (live) tapFriend() else compose.onRoot().performTouchInput { click(Offset(width * 0.72f, height * 0.49f)) }
            waitForPlayback()
            compose.runOnIdle {
                assertEquals(DataSource.toDataSource(kept.file.path), players.single().dataSource)
                assertTrue("The visible figure must actually start its book's recording", players.single().isReallyPlaying)
                players.single().invokeCompletionListener()
                assertEquals("다른 이야기 친구", current.s.friendName)
            }
        } finally { compose.runOnIdle { scope.cancel() } }
    }

    private fun tapFriend() = compose.onRoot().performTouchInput { click(Offset(width * 0.42f, height * 0.49f)) }

    private fun waitForPlayback() = compose.waitUntil(3_000) {
        shadowOf(Looper.getMainLooper()).idle()
        players.isNotEmpty()
    }
}
