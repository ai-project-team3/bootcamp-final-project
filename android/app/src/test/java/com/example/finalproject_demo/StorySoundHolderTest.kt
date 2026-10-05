package com.example.finalproject_demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.example.finalproject_demo.demo.*
import com.example.finalproject_demo.net.Server
import com.example.finalproject_demo.net.Voice
import com.example.finalproject_demo.sound.ChildSound
import com.example.finalproject_demo.ui.BookPageView
import com.example.finalproject_demo.ui.motionFrozen
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    @Before fun setup() {
        folder = Files.createTempDirectory("story_sound_holder").toFile()
        ChildSound.root = folder
        motionFrozen = true
        setLive(true)
    }

    @After fun cleanup() {
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

    private fun tapFriend() = compose.onRoot().performTouchInput { click(Offset(width * 0.42f, height * 0.49f)) }
}
